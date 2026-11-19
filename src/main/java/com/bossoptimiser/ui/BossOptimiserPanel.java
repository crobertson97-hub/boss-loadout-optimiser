package com.bossoptimiser.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JToggleButton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The plugin's sidebar. It only draws {@link UiState} snapshots and forwards user actions to the
 * {@link UiController}; it does no work of its own.
 */
@Slf4j
public class BossOptimiserPanel extends PluginPanel
{
	static final String HOME = "Home";
	static final String BOSS = "Boss";
	static final String GEAR = "Gear";
	static final String INVENTORY = "Inventory";
	static final String TILES = "Tiles";
	static final String UPGRADES = "Upgrades";
	static final String PROFILES = "Profiles";

	/** A CardLayout that is only as tall as the card being shown, so short sections do not scroll. */
	private static final class VisibleCardLayout extends CardLayout
	{
		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets in = parent.getInsets();
				for (Component c : parent.getComponents())
				{
					if (c.isVisible())
					{
						Dimension d = c.getPreferredSize();
						return new Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
					}
				}
				return new Dimension(in.left + in.right, in.top + in.bottom);
			}
		}
	}

	private final UiController controller;
	private final Map<String, JToggleButton> navButtons = new LinkedHashMap<>();
	private final ButtonGroup navGroup = new ButtonGroup();
	private final Map<String, Section> sections = new LinkedHashMap<>();
	private final CardLayout cards = new VisibleCardLayout();
	private final JPanel cardHolder = new JPanel(cards);
	private final WrapArea status = new WrapArea("", UiKit.TEXT, UiKit.CONTENT_WIDTH);
	private final JProgressBar progress = new JProgressBar();
	private final WrapArea loading = new WrapArea("Loading data…", UiKit.TEXT, UiKit.CONTENT_WIDTH);
	private String current = HOME;
	private int lastWidth;

	public BossOptimiserPanel(UiController controller)
	{
		super();
		this.controller = controller;
		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = UiKit.stack(3);
		header.add(navRow(HOME, BOSS, GEAR, INVENTORY));
		header.add(navRow(TILES, UPGRADES, PROFILES));
		header.add(status);
		progress.setIndeterminate(false);
		progress.setPreferredSize(new Dimension(UiKit.CONTENT_WIDTH, 5));
		progress.setBorderPainted(false);
		progress.setForeground(ColorScheme.BRAND_ORANGE);
		progress.setVisible(false);
		header.add(progress);
		header.add(loading);
		add(header, BorderLayout.NORTH);

		cardHolder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sections.put(HOME, new DashboardSection(controller, this::showSection));
		sections.put(BOSS, new BossSection(controller, this::showSection));
		sections.put(GEAR, new GearSection(controller, this::showSection));
		sections.put(INVENTORY, new InventorySection(controller));
		sections.put(TILES, new TilesSection(controller));
		sections.put(UPGRADES, new UpgradesSection(controller));
		sections.put(PROFILES, new ProfilesSection(controller, this::showSection));
		for (Map.Entry<String, Section> e : sections.entrySet())
		{
			cardHolder.add(e.getValue(), e.getKey());
		}
		add(cardHolder, BorderLayout.CENTER);
		navButtons.get(HOME).setSelected(true);

		// Wrapped text is measured at the width it was last given; measure again when that width changes.
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				if (getWidth() != lastWidth)
				{
					lastWidth = getWidth();
					revalidate();
				}
			}
		});

		controller.addListener(this::render);
		render(controller.state());
	}

	private JPanel navRow(String... names)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.gridy = 0;
		for (int i = 0; i < names.length; i++)
		{
			final String name = names[i];
			JToggleButton b = new JToggleButton(name);
			b.setFont(FontManager.getRunescapeSmallFont());
			b.setFocusPainted(false);
			b.setMargin(new Insets(4, 0, 4, 0));
			b.setToolTipText(name);
			b.addActionListener(e -> showSection(name));
			navGroup.add(b);
			navButtons.put(name, b);
			// Longer labels get proportionally more of the row, so every label fits.
			c.gridx = i;
			c.weightx = name.length() + 3;
			c.insets = new Insets(0, i == 0 ? 0 : 2, 0, 0);
			b.setPreferredSize(new Dimension(10, 24));
			b.setMinimumSize(new Dimension(10, 24));
			row.add(b, c);
		}
		return row;
	}

	/** Switches to a section and draws it from the latest state. */
	void showSection(String name)
	{
		Section section = sections.get(name);
		if (section == null)
		{
			return;
		}
		current = name;
		JToggleButton button = navButtons.get(name);
		if (button != null && !button.isSelected())
		{
			button.setSelected(true);
		}
		UiState state = controller.state();
		if (state != null && state.isDataReady())
		{
			section.render(state);
		}
		cards.show(cardHolder, name);
		if (getScrollPane() != null)
		{
			getScrollPane().getVerticalScrollBar().setValue(0);
		}
		revalidate();
		repaint();
	}

	private void render(UiState state)
	{
		try
		{
			String text = state == null ? null : state.getStatus();
			boolean hasStatus = text != null && !text.isEmpty();
			// Wrapped rather than cut off: an error message must be readable in full.
			status.setTextIfChanged(hasStatus ? text : "");
			status.setForeground(state != null && state.isStatusIsError() ? UiKit.BAD : UiKit.TEXT);

			boolean busy = state != null && (state.isOptimising() || state.isAdvising());
			if (progress.isVisible() != busy)
			{
				progress.setIndeterminate(busy);
				progress.setVisible(busy);
			}

			// No state yet is treated like data that is still loading.
			boolean ready = state != null && state.isDataReady();
			if (ready)
			{
				loading.setVisible(false);
			}
			else
			{
				boolean failed = state != null && state.getLoadError() != null;
				loading.setTextIfChanged(failed ? state.getLoadError() : "Loading data…");
				loading.setForeground(failed ? UiKit.BAD : UiKit.TEXT);
				loading.setVisible(true);
			}
			cardHolder.setVisible(ready);
			for (JToggleButton b : navButtons.values())
			{
				b.setEnabled(ready);
			}
			if (ready)
			{
				// Only the visible section is drawn; the others are drawn when they are opened.
				sections.get(current).render(state);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Boss Loadout Optimiser: the sidebar could not be refreshed", e);
		}
		revalidate();
		repaint();
	}
}
