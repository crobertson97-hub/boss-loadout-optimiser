package com.bossoptimiser.ui;

import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.tiles.GroundMarkerInstaller;
import com.bossoptimiser.tiles.TilePack;
import com.bossoptimiser.ui.UiKit.Option;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.DefaultCaret;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Tile packs for the selected boss: inspect, copy, install into Ground Markers, import and edit. */
final class TilesSection extends Section
{
	private static final String INSTRUCTIONS = "To import manually: enable RuneLite's Ground Markers plugin and its"
		+ " 'Show Import/Export/Clear options' setting, copy the JSON here, then right-click the world map button under"
		+ " the minimap and choose Import. Markers appear when the area next loads.";
	private static final String NO_PACKS = "No verified tile pack is bundled for this boss. Import your own Ground Markers export below.";
	private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"']+");
	private static final int DIALOG_WIDTH = 380;

	private final JComboBox<Option<String>> packCombo = UiKit.combo();
	private final WrapArea none = new WrapArea(NO_PACKS, UiKit.TEXT, UiKit.CONTENT_WIDTH);
	private final JPanel details = UiKit.stack(6);
	private final JButton copy;
	private final JButton install;
	private final JButton undo;
	private final JButton importButton;
	private final JButton edit;
	private final JButton delete;
	private final JPanel customRow;

	private TilePack shown;

	TilesSection(UiController controller)
	{
		super("Tiles", controller);
		packCombo.addActionListener(e ->
		{
			String id = UiKit.selected(packCombo);
			if (!updating && id != null && !id.equals(selectionOf(controller.state()).getTilePackId()))
			{
				controller.selectTilePack(id);
			}
		});
		content.add(packCombo);
		content.add(none);
		content.add(details);

		copy = UiKit.button("Copy JSON", this::copyJson);
		importButton = UiKit.button("Import…", this::importPack);
		content.add(UiKit.row(copy, importButton));
		install = UiKit.button("Add to Ground Markers…", this::installPack);
		content.add(install);
		undo = UiKit.button("Undo install", this::undoInstall);
		content.add(undo);
		edit = UiKit.button("Edit…", this::editPack);
		delete = UiKit.button("Delete", this::deletePack);
		customRow = UiKit.row(edit, delete);
		content.add(customRow);

		content.add(new WrapArea(INSTRUCTIONS, UiKit.TEXT, UiKit.CONTENT_WIDTH));
	}

	private static Selection selectionOf(UiState state)
	{
		return state == null || state.getSelection() == null ? Selection.builder().build() : state.getSelection();
	}

	// ------------------------------------------------------------------ rendering

	@Override
	protected void update(UiState state)
	{
		Selection selection = selectionOf(state);
		String bossId = selection.getBossId();
		BossDefinition boss = DashboardSection.findBoss(controller.bosses(), bossId);
		List<TilePack> packs = new ArrayList<>();
		if (bossId != null)
		{
			List<TilePack> found = controller.tilePacks(bossId);
			if (found != null)
			{
				for (TilePack p : found)
				{
					if (p != null && p.getId() != null)
					{
						packs.add(p);
					}
				}
			}
		}

		TilePack chosen = null;
		for (TilePack p : packs)
		{
			if (p.getId().equals(selection.getTilePackId()))
			{
				chosen = p;
			}
		}
		if (chosen == null)
		{
			for (TilePack p : packs)
			{
				if (chosen == null && p.fitsStrategy(selection.getStrategyId()))
				{
					chosen = p;
				}
			}
		}
		if (chosen == null && !packs.isEmpty())
		{
			chosen = packs.get(0);
		}

		List<Option<String>> options = new ArrayList<>();
		for (TilePack p : packs)
		{
			options.add(new Option<>(p.getId(), (p.getName() == null ? "Unnamed pack" : p.getName())
				+ (p.fitsStrategy(selection.getStrategyId()) ? "" : " (other strategy)")));
		}
		UiKit.setItems(packCombo, options, chosen == null ? null : UiKit.find(options, chosen.getId()));
		packCombo.setVisible(!packs.isEmpty());
		none.setTextIfChanged(bossId == null ? "Select a boss to see its tile packs." : NO_PACKS);
		none.setVisible(packs.isEmpty());

		boolean installed = chosen != null && controller.isTilePackInstalled(chosen.getId());
		boolean canInstall = controller.groundMarkerInstallEnabled();
		shown = chosen;
		copy.setEnabled(chosen != null);
		importButton.setEnabled(bossId != null);
		install.setEnabled(chosen != null && canInstall);
		install.setToolTipText(canInstall ? "Shows what would be added and asks before changing anything"
			: "Direct installation is switched off in this plugin's settings. Use Copy JSON instead.");
		undo.setEnabled(installed);
		undo.setToolTipText(installed ? "Removes only the markers this plugin added" : "This pack is not installed by this plugin");
		customRow.setVisible(chosen != null && !chosen.isBuiltIn());

		if (changed(chosen, selection.getStrategyId(), installed, boss == null ? null : boss.getId()))
		{
			details.removeAll();
			if (chosen != null)
			{
				buildDetails(chosen, boss, selection.getStrategyId(), installed);
			}
		}
	}

	private static String strategyName(BossDefinition boss, String strategyId)
	{
		if (strategyId == null || strategyId.isEmpty())
		{
			return "Any strategy";
		}
		if (boss != null && boss.getStrategies() != null)
		{
			for (BossDefinition.Strategy s : boss.getStrategies())
			{
				if (s != null && strategyId.equals(s.getId()))
				{
					return s.getName();
				}
			}
		}
		return strategyId;
	}

	private static String firstLink(String... texts)
	{
		for (String text : texts)
		{
			if (text == null)
			{
				continue;
			}
			Matcher m = LINK.matcher(text);
			if (m.find())
			{
				return m.group().replaceAll("[.,;:)\\]]+$", "");
			}
		}
		return null;
	}

	private void buildDetails(TilePack pack, BossDefinition boss, String strategyId, boolean installed)
	{
		List<TilePack.Marker> markers = new ArrayList<>();
		if (pack.getMarkers() != null)
		{
			for (TilePack.Marker m : pack.getMarkers())
			{
				if (m != null)
				{
					markers.add(m);
				}
			}
		}

		JPanel card = UiKit.card();
		card.add(UiKit.wrap(pack.getName() == null ? "Unnamed pack" : pack.getName(), UiKit.BRIGHT));
		if (pack.getDescription() != null && !pack.getDescription().isEmpty())
		{
			card.add(UiKit.wrap(pack.getDescription()));
		}
		final String link = firstLink(pack.getDescription(), pack.getSourceRef(), pack.getSource());
		if (link != null)
		{
			JButton open = UiKit.button("Open reference", () -> LinkBrowser.browse(link));
			open.setToolTipText(UiKit.plain(link));
			card.add(open);
		}
		boolean fits = pack.fitsStrategy(strategyId);
		card.add(UiKit.wrap("Strategy: " + strategyName(boss, pack.getStrategyId())));
		if (!fits)
		{
			card.add(UiKit.wrap("Made for a different strategy than the one you selected.", UiKit.ACCENT));
		}
		card.add(UiKit.wrap("Markers: " + markers.size() + ", with a label: " + pack.labelCount()));
		Set<Integer> regions = pack.regionIds();
		card.add(UiKit.wrap("Regions: " + (regions.isEmpty() ? "none" : join(regions))));

		Set<String> colours = pack.colours();
		if (!colours.isEmpty())
		{
			JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			swatches.setOpaque(false);
			swatches.add(UiKit.label("Colours: "));
			int shownSwatches = 0;
			for (String c : colours)
			{
				if (shownSwatches++ >= 9)
				{
					break;
				}
				Color colour = TilePreview.parseColour("default".equals(c) ? null : c);
				JLabel swatch = new JLabel();
				swatch.setOpaque(true);
				swatch.setBackground(new Color(colour.getRed(), colour.getGreen(), colour.getBlue()));
				swatch.setPreferredSize(new Dimension(14, 14));
				swatch.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1));
				swatch.setToolTipText("default".equals(c) ? "Ground Markers default colour" : UiKit.plain(c));
				swatches.add(swatch);
			}
			card.add(swatches);
		}

		addIfPresent(card, "Source: ", pack.getSource());
		addIfPresent(card, "Reference: ", pack.getSourceRef());
		addIfPresent(card, "Author: ", pack.getAuthor());
		addIfPresent(card, "Licence: ", pack.getLicence());
		if (TilePack.VERIFIED_SOURCE.equals(pack.getValidationStatus()))
		{
			card.add(UiKit.wrap("Verified source", UiKit.GOOD));
		}
		else if (TilePack.USER.equals(pack.getValidationStatus()))
		{
			card.add(UiKit.wrap("Your pack — not checked by the plugin", UiKit.ACCENT));
		}
		else
		{
			card.add(UiKit.wrap("Not checked by the plugin", UiKit.ACCENT));
		}
		card.add(installed
			? UiKit.wrap("Installed in Ground Markers by this plugin", UiKit.GOOD)
			: UiKit.wrap("Not installed"));
		details.add(card);

		TilePreview preview = new TilePreview();
		preview.setMarkers(markers);
		details.add(preview);
		details.add(new WrapArea("Relative tile positions (not a map)", UiKit.TEXT, UiKit.CONTENT_WIDTH));

		StringBuilder list = new StringBuilder();
		for (TilePack.Marker m : markers)
		{
			if (list.length() > 0)
			{
				list.append('\n');
			}
			list.append("region ").append(m.getRegionId())
				.append("  ").append(m.getRegionX()).append(',').append(m.getRegionY())
				.append("  plane ").append(m.getZ())
				.append("  ").append(m.getColor() == null ? "default colour" : m.getColor());
			if (m.getLabel() != null && !m.getLabel().isEmpty())
			{
				list.append("  ").append(m.getLabel());
			}
		}
		JTextArea text = new JTextArea();
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.setForeground(UiKit.TEXT);
		if (text.getCaret() instanceof DefaultCaret)
		{
			((DefaultCaret) text.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		}
		text.setText(list.length() == 0 ? "This pack has no markers." : list.toString());
		JScrollPane scroll = new JScrollPane(text);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 4));
		scroll.setPreferredSize(new Dimension(UiKit.CONTENT_WIDTH, 120));
		UiKit.passWheelToSidebar(scroll);
		details.add(scroll);
	}

	private static void addIfPresent(JPanel card, String caption, String value)
	{
		if (value != null && !value.isEmpty())
		{
			card.add(UiKit.wrap(caption + value));
		}
	}

	private static String join(Set<Integer> values)
	{
		StringBuilder sb = new StringBuilder();
		for (Integer v : values)
		{
			sb.append(sb.length() == 0 ? "" : ", ").append(v);
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ actions

	private void copyJson()
	{
		TilePack pack = shown;
		if (pack == null)
		{
			return;
		}
		String json = controller.tilePackJson(pack);
		if (json != null && UiKit.copyToClipboard(json))
		{
			UiKit.info(this, "Copy JSON", "The markers of '" + pack.getName() + "' are on the clipboard.\n\n" + INSTRUCTIONS);
		}
		else
		{
			UiKit.info(this, "Copy JSON", "The JSON could not be copied to the clipboard.");
		}
	}

	private void installPack()
	{
		TilePack pack = shown;
		if (pack == null || !controller.groundMarkerInstallEnabled())
		{
			return;
		}
		GroundMarkerInstaller.Preview preview = controller.previewTileInstall(pack);
		if (preview == null)
		{
			UiKit.info(this, "Add to Ground Markers", "The install could not be previewed, so nothing was changed.");
			return;
		}
		if (preview.getBlocker() != null)
		{
			UiKit.info(this, "Add to Ground Markers", preview.getBlocker());
			return;
		}
		String question = "This adds " + preview.getToAdd() + " markers in " + preview.getRegions()
			+ " region(s) to your Ground Markers. " + preview.getAlreadyMarked()
			+ " tiles are already marked and will be left unchanged. Your existing markers are not modified.";
		if (!UiKit.confirm(this, "Add to Ground Markers", question))
		{
			return;
		}
		GroundMarkerInstaller.Outcome outcome = controller.installTilePack(pack);
		refresh();
		UiKit.info(this, "Add to Ground Markers",
			outcome == null || outcome.getMessage() == null ? "The install did not report a result." : outcome.getMessage());
	}

	private void undoInstall()
	{
		TilePack pack = shown;
		if (pack == null || !controller.isTilePackInstalled(pack.getId()))
		{
			return;
		}
		GroundMarkerInstaller.Outcome outcome = controller.rollbackTilePack(pack);
		refresh();
		UiKit.info(this, "Undo install",
			outcome == null || outcome.getMessage() == null ? "The undo did not report a result." : outcome.getMessage());
	}

	private JComboBox<Option<String>> strategyChoice(BossDefinition boss, String selectedId)
	{
		List<Option<String>> options = new ArrayList<>();
		options.add(new Option<>(null, "Any strategy"));
		if (boss != null && boss.getStrategies() != null)
		{
			for (BossDefinition.Strategy s : boss.getStrategies())
			{
				if (s != null && s.getId() != null)
				{
					options.add(new Option<>(s.getId(), s.getName()));
				}
			}
		}
		JComboBox<Option<String>> combo = new JComboBox<>();
		UiKit.setItems(combo, options, UiKit.find(options, selectedId == null || selectedId.isEmpty() ? null : selectedId));
		return combo;
	}

	private void importPack()
	{
		Selection selection = selectionOf(controller.state());
		final String bossId = selection.getBossId();
		if (bossId == null)
		{
			return;
		}
		BossDefinition boss = DashboardSection.findBoss(controller.bosses(), bossId);
		JTextField name = new JTextField();
		JComboBox<Option<String>> strategy = strategyChoice(boss, null);
		JTextField description = new JTextField();
		JTextArea json = new JTextArea(9, 30);
		json.setLineWrap(true);
		JScrollPane scroll = new JScrollPane(json);
		scroll.setPreferredSize(new Dimension(DIALOG_WIDTH, 170));
		JLabel pasteResult = new JLabel(" ");
		JButton paste = new JButton("Paste from clipboard");
		paste.addActionListener(e ->
		{
			String text = UiKit.readClipboard();
			if (text == null || text.trim().isEmpty())
			{
				pasteResult.setText("The clipboard holds no text.");
			}
			else
			{
				json.setText(text);
				json.setCaretPosition(0);
				pasteResult.setText("Pasted " + text.length() + " characters.");
			}
		});

		JPanel form = UiKit.stack(4, DIALOG_WIDTH);
		form.add(new JLabel("Tile pack for " + (boss == null ? bossId : boss.getName())));
		form.add(new JLabel("Name"));
		form.add(name);
		form.add(new JLabel("Strategy"));
		form.add(strategy);
		form.add(new JLabel("Description (optional)"));
		form.add(description);
		form.add(new JLabel("Ground Markers export (JSON)"));
		form.add(scroll);
		form.add(paste);
		form.add(pasteResult);

		while (UiKit.form(this, "Import tile pack", form))
		{
			String packName = name.getText() == null ? "" : name.getText().trim();
			if (packName.isEmpty())
			{
				UiKit.info(this, "Import tile pack", "Give the tile pack a name.");
				continue;
			}
			String error = controller.importTilePack(packName, bossId, UiKit.selected(strategy),
				description.getText() == null ? "" : description.getText().trim(), json.getText());
			if (error == null)
			{
				refresh();
				return;
			}
			UiKit.info(this, "Import tile pack", error);
		}
	}

	private void editPack()
	{
		TilePack pack = shown;
		if (pack == null || pack.isBuiltIn())
		{
			return;
		}
		BossDefinition boss = DashboardSection.findBoss(controller.bosses(), pack.getBossId());
		JTextField name = new JTextField(pack.getName() == null ? "" : pack.getName());
		JComboBox<Option<String>> strategy = strategyChoice(boss, pack.getStrategyId());
		JTextField description = new JTextField(pack.getDescription() == null ? "" : pack.getDescription());
		JPanel form = UiKit.stack(4, DIALOG_WIDTH);
		form.add(new JLabel("Name"));
		form.add(name);
		form.add(new JLabel("Strategy"));
		form.add(strategy);
		form.add(new JLabel("Description"));
		form.add(description);

		while (UiKit.form(this, "Edit tile pack", form))
		{
			String packName = name.getText() == null ? "" : name.getText().trim();
			if (packName.isEmpty())
			{
				UiKit.info(this, "Edit tile pack", "Give the tile pack a name.");
				continue;
			}
			boolean ok = controller.updateTilePack(pack.getId(), packName, pack.getBossId(), UiKit.selected(strategy),
				description.getText() == null ? "" : description.getText().trim());
			refresh();
			if (!ok)
			{
				UiKit.info(this, "Edit tile pack", "The tile pack could not be updated.");
			}
			return;
		}
	}

	private void deletePack()
	{
		TilePack pack = shown;
		if (pack == null || pack.isBuiltIn())
		{
			return;
		}
		boolean installed = controller.isTilePackInstalled(pack.getId());
		String question = "Delete the tile pack '" + pack.getName() + "' from this plugin?"
			+ (installed ? " Its markers are still in Ground Markers: use Undo install first if you want them removed,"
			+ " because that is not possible after deleting the pack." : "");
		if (!UiKit.confirm(this, "Delete tile pack", question))
		{
			return;
		}
		boolean ok = controller.deleteTilePack(pack.getId());
		refresh();
		if (!ok)
		{
			UiKit.info(this, "Delete tile pack", "The tile pack could not be deleted.");
		}
	}
}
