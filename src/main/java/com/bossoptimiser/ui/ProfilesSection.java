package com.bossoptimiser.ui;

import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.profiles.BossProfile;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/** Saved Boss Profiles of the logged-in account. */
@Slf4j
final class ProfilesSection extends Section
{
	private static final int ROW_HEIGHT = 34;
	private static final int ROWS = 5;

	/** Two-line list row: name, then boss and date. */
	private final class Row extends JPanel implements ListCellRenderer<BossProfile>
	{
		private final JLabel name = UiKit.label("", UiKit.BRIGHT);
		private final JLabel detail = UiKit.label("");

		Row()
		{
			super(null);
			add(name);
			add(detail);
		}

		/** The two lines are placed by hand, so the row never depends on a layout pass while it is being painted. */
		@Override
		public void setBounds(int x, int y, int width, int height)
		{
			super.setBounds(x, y, width, height);
			int line = Math.max(0, (height - 2) / 2);
			name.setBounds(4, 1, Math.max(0, width - 8), line);
			detail.setBounds(4, 1 + line, Math.max(0, width - 8), line);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends BossProfile> list, BossProfile p, int index,
			boolean selected, boolean focus)
		{
			boolean last = p != null && p.getId() != null && p.getId().equals(lastUsedId);
			name.setText(p == null ? "" : UiKit.plain(p.getName()) + (last ? " (last used)" : ""));
			name.setForeground(last ? UiKit.ACCENT : UiKit.BRIGHT);
			detail.setText(p == null ? "" : bossName(p.getBossId()) + ", " + UiKit.dateTime(p.getUpdatedAt()));
			setBackground(selected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			setToolTipText(p == null ? null : UiKit.plain(p.getName()) + " - " + bossName(p.getBossId())
				+ " - updated " + UiKit.dateTime(p.getUpdatedAt()));
			return this;
		}
	}

	private final Consumer<String> navigate;
	private final DefaultListModel<BossProfile> model = new DefaultListModel<>();
	private final JList<BossProfile> list = new JList<>(model);
	private final WrapArea empty = new WrapArea("", UiKit.TEXT, UiKit.CONTENT_WIDTH);
	private final List<JButton> needSelection = new ArrayList<>();
	private final JButton saveAs;
	private final JButton importButton;

	private final Map<String, String> bossNames = new HashMap<>();
	private List<BossProfile> shown = new ArrayList<>();
	private String lastUsedId;

	ProfilesSection(UiController controller, Consumer<String> navigate)
	{
		super("Profiles", controller);
		this.navigate = navigate;

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(ROW_HEIGHT);
		list.setVisibleRowCount(ROWS);
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.setCellRenderer(new Row());
		list.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting())
			{
				enableButtons();
			}
		});
		JScrollPane scroll = new JScrollPane(list);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		UiKit.passWheelToSidebar(scroll);
		content.add(scroll);
		content.add(empty);

		JButton load = selectionButton("Load", this::load);
		JButton update = selectionButton("Update selected", this::updateSelected);
		content.add(UiKit.row(load, update));
		saveAs = UiKit.button("Save current as…", this::saveAs);
		content.add(saveAs);
		content.add(UiKit.row(selectionButton("Rename…", this::rename), selectionButton("Duplicate", this::duplicate)));
		content.add(selectionButton("Delete", this::delete));
		content.add(selectionButton("Export to clipboard", this::exportSelected));
		importButton = UiKit.button("Import from clipboard", this::importFromClipboard);
		content.add(importButton);
		content.add(new WrapArea("Profiles are stored per RuneScape account in your RuneLite profile.", UiKit.TEXT, UiKit.CONTENT_WIDTH));
		enableButtons();
	}

	private JButton selectionButton(String text, Runnable action)
	{
		JButton b = UiKit.button(text, () -> guarded(text, action));
		needSelection.add(b);
		return b;
	}

	private void enableButtons()
	{
		boolean selected = list.getSelectedValue() != null;
		for (JButton b : needSelection)
		{
			b.setEnabled(selected);
		}
	}

	private String bossName(String bossId)
	{
		String name = bossId == null ? null : bossNames.get(bossId);
		return name == null ? "Unknown boss" : name;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	protected void update(UiState state)
	{
		List<BossProfile> profiles = new ArrayList<>();
		if (state.getProfiles() != null)
		{
			for (BossProfile p : state.getProfiles())
			{
				if (p != null)
				{
					profiles.add(p);
				}
			}
		}
		List<BossDefinition> bosses = controller.bosses();
		if (bosses != null && bossNames.size() != bosses.size())
		{
			bossNames.clear();
			for (BossDefinition b : bosses)
			{
				if (b != null && b.getId() != null)
				{
					bossNames.put(b.getId(), b.getName());
				}
			}
		}
		boolean lastChanged = !Objects.equals(lastUsedId, state.getLastProfileId());
		lastUsedId = state.getLastProfileId();
		if (!profiles.equals(shown))
		{
			BossProfile selected = list.getSelectedValue();
			String selectedId = selected == null ? null : selected.getId();
			shown = profiles;
			model.clear();
			for (BossProfile p : profiles)
			{
				model.addElement(p);
			}
			for (int i = 0; i < profiles.size(); i++)
			{
				if (selectedId != null && selectedId.equals(profiles.get(i).getId()))
				{
					list.setSelectedIndex(i);
				}
			}
		}
		else if (lastChanged)
		{
			list.repaint();
		}
		boolean loggedIn = state.getAccount() != null && state.getAccount().isLoggedIn();
		empty.setTextIfChanged(!profiles.isEmpty() ? ""
			: loggedIn ? "No Boss Profiles yet. Optimise a setup, then press Save current as…"
			: "Log in to see the Boss Profiles of your account.");
		empty.setVisible(profiles.isEmpty());
		Selection selection = state.getSelection();
		saveAs.setEnabled(selection != null && selection.getBossId() != null);
		enableButtons();
	}

	// ------------------------------------------------------------------ actions

	/** Runs an action and tells the user when the controller could not carry it out. */
	private void guarded(String what, Runnable action)
	{
		try
		{
			action.run();
		}
		catch (RuntimeException e)
		{
			log.warn("Boss Loadout Optimiser: '{}' failed", what, e);
			UiKit.info(this, what, "That did not work. Check that you are logged in, then try again.");
		}
	}

	private void report(String title, String error)
	{
		if (error != null)
		{
			UiKit.info(this, title, error);
		}
	}

	private void saveAs()
	{
		guarded("Save current as…", () ->
		{
			Selection selection = controller.state() == null ? null : controller.state().getSelection();
			String suggestion = selection == null || selection.getBossId() == null ? "" : bossName(selection.getBossId());
			String name = UiKit.prompt(this, "Save Boss Profile", "Name for this Boss Profile:", suggestion);
			if (name != null)
			{
				report("Save Boss Profile", controller.saveProfile(name, null));
			}
		});
	}

	private void updateSelected()
	{
		BossProfile p = list.getSelectedValue();
		if (p != null && UiKit.confirm(this, "Update Boss Profile",
			"Replace what is saved in '" + p.getName() + "' with the current boss, setup and assumptions?"))
		{
			report("Update Boss Profile", controller.saveProfile(p.getName(), p.getId()));
		}
	}

	private void load()
	{
		BossProfile p = list.getSelectedValue();
		if (p != null)
		{
			controller.loadProfile(p.getId());
			navigate.accept(BossOptimiserPanel.GEAR);
		}
	}

	private void rename()
	{
		BossProfile p = list.getSelectedValue();
		if (p == null)
		{
			return;
		}
		String name = UiKit.prompt(this, "Rename Boss Profile", "New name:", p.getName());
		if (name != null && !name.equals(p.getName()))
		{
			report("Rename Boss Profile", controller.renameProfile(p.getId(), name));
		}
	}

	private void duplicate()
	{
		BossProfile p = list.getSelectedValue();
		if (p != null)
		{
			report("Duplicate Boss Profile", controller.duplicateProfile(p.getId()));
		}
	}

	private void delete()
	{
		BossProfile p = list.getSelectedValue();
		if (p != null && UiKit.confirm(this, "Delete Boss Profile", "Delete the Boss Profile '" + p.getName() + "'? This cannot be undone."))
		{
			controller.deleteProfile(p.getId());
		}
	}

	private void exportSelected()
	{
		BossProfile p = list.getSelectedValue();
		if (p == null)
		{
			return;
		}
		String json = controller.exportProfile(p.getId());
		if (json == null)
		{
			UiKit.info(this, "Export Boss Profile", "This Boss Profile could not be exported.");
		}
		else if (UiKit.copyToClipboard(json))
		{
			UiKit.info(this, "Export Boss Profile", "'" + p.getName() + "' was copied to the clipboard.");
		}
		else
		{
			UiKit.info(this, "Export Boss Profile", "The clipboard could not be used.");
		}
	}

	private void importFromClipboard()
	{
		guarded("Import from clipboard", () ->
		{
			String text = UiKit.readClipboard();
			if (text == null || text.trim().isEmpty())
			{
				UiKit.info(this, "Import Boss Profile", "The clipboard holds no text. Copy an exported Boss Profile first.");
				return;
			}
			report("Import Boss Profile", controller.importProfile(text));
		});
	}
}
