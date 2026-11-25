package com.bossoptimiser.ui;

import com.bossoptimiser.account.AccountSnapshot;
import com.bossoptimiser.bosses.BossCategory;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.combat.Potion;
import com.bossoptimiser.combat.Prayer;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.optimiser.Priority;
import com.bossoptimiser.ui.UiKit.Option;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

/** Boss, variant, strategy and every assumption the numbers depend on. */
final class BossSection extends Section
{
	private static String sortKey(BossDefinition b)
	{
		String n = b.getName().toLowerCase(Locale.ROOT);
		return n.startsWith("the ") ? n.substring(4) : n;
	}

	private static final int LIST_ROWS = 7;

	private final IconTextField search = new IconTextField();
	private final JComboBox<Option<BossCategory>> category = UiKit.combo();
	private final DefaultListModel<BossDefinition> bossModel = new DefaultListModel<>();
	private final JList<BossDefinition> bossList = new JList<>(bossModel);
	private final WrapArea noMatches = UiKit.wrap("No boss matches this search.");

	private final JComboBox<Option<String>> variant = UiKit.combo();
	private final JComboBox<Option<String>> strategy = UiKit.combo();
	private final WrapArea strategyText = UiKit.wrap("");
	private final JComboBox<Option<String>> groupSize = UiKit.combo();
	private final JComboBox<Priority> priority = UiKit.combo();
	private final WrapArea priorityText = UiKit.wrap("");

	private final JPanel requirements = UiKit.stack(3, UiKit.CARD_WIDTH);

	private final Map<CombatClass, JComboBox<Potion>> potions = new EnumMap<>(CombatClass.class);
	private final Map<CombatClass, JPanel> potionFields = new EnumMap<>(CombatClass.class);
	private final Map<CombatClass, JComboBox<Option<Prayer>>> prayers = new EnumMap<>(CombatClass.class);
	private final Map<CombatClass, JPanel> prayerFields = new EnumMap<>(CombatClass.class);
	private final JCheckBox slayerTask = UiKit.check("On slayer task");
	private final WrapArea slayerNote = UiKit.wrap("This boss can only be fought on a slayer task, so the task bonus always applies.");
	private final JCheckBox kandarin = UiKit.check("Kandarin hard diary");
	private final JCheckBox allowUnknown = UiKit.check("");
	private final JComboBox<Option<Integer>> dart = UiKit.combo();

	private final Consumer<String> navigate;
	private final List<JButton> optimiseButtons = new ArrayList<>();
	private final JButton cancel;

	private List<BossDefinition> shownBosses = new ArrayList<>();
	private List<Option<Integer>> dartOptions;

	BossSection(UiController controller, Consumer<String> navigate)
	{
		super("Boss", controller);
		this.navigate = navigate;

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(UiKit.CONTENT_WIDTH, 28));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				filterChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				filterChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				filterChanged();
			}
		});
		content.add(search);

		category.addActionListener(e -> filterChanged());
		content.add(category);

		bossList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		bossList.setVisibleRowCount(LIST_ROWS);
		bossList.setFixedCellHeight(18);
		bossList.setFont(FontManager.getRunescapeSmallFont());
		bossList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bossList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				putClientProperty("html.disable", Boolean.TRUE);
				// Explicit colours: readable whatever look-and-feel defaults are in force.
				setBackground(selected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
				setForeground(selected ? java.awt.Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
				if (value instanceof BossDefinition)
				{
					BossDefinition b = (BossDefinition) value;
					boolean partial = b.getValidationStatus() == BossDefinition.ValidationStatus.PARTIAL;
					setText(b.getName() + (partial ? " (partly validated)" : ""));
					setToolTipText(UiKit.plain(b.getName()) + (b.getCategory() == null ? "" : " - " + b.getCategory().getDisplayName()));
				}
				return this;
			}
		});
		bossList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting())
			{
				bossPicked();
			}
		});
		JScrollPane listScroll = new JScrollPane(bossList);
		listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScroll.setBorder(null);
		UiKit.passWheelToSidebar(listScroll);
		content.add(listScroll);
		noMatches.setVisible(false);
		content.add(noMatches);

		JPanel setup = UiKit.card();
		variant.addActionListener(e ->
		{
			String id = UiKit.selected(variant);
			if (id != null)
			{
				change(b -> b.variantId(id));
			}
		});
		setup.add(UiKit.field("Variant", variant));
		strategy.addActionListener(e ->
		{
			String id = UiKit.selected(strategy);
			if (id != null)
			{
				change(b -> b.strategyId(id));
			}
		});
		setup.add(UiKit.field("Strategy", strategy));
		setup.add(strategyText);
		groupSize.addActionListener(e ->
		{
			String size = UiKit.selected(groupSize);
			if (size != null)
			{
				change(b -> b.groupSize(size));
			}
		});
		setup.add(UiKit.field("Group size", groupSize));
		UiKit.setItems(priority, Arrays.asList(Priority.values()), Priority.MAX_DPS);
		priority.addActionListener(e ->
		{
			Object p = priority.getSelectedItem();
			if (p instanceof Priority)
			{
				change(b -> b.priority((Priority) p));
			}
		});
		setup.add(UiKit.field("Optimise for", priority));
		setup.add(priorityText);
		content.add(setup);

		content.add(optimiseButton());
		cancel = UiKit.button("Cancel", controller::cancelOptimise);
		cancel.setVisible(false);
		content.add(cancel);

		JPanel reqCard = UiKit.card();
		reqCard.add(UiKit.heading("Requirements & warnings"));
		reqCard.add(requirements);
		content.add(reqCard);

		JPanel assume = UiKit.card();
		assume.add(UiKit.heading("Assumptions"));
		for (CombatClass cc : CombatClass.values())
		{
			JComboBox<Potion> potion = UiKit.combo();
			List<Potion> choices = new ArrayList<>();
			choices.add(Potion.NONE);
			for (Potion p : Potion.values())
			{
				if (p.getCombatClass() == cc)
				{
					choices.add(p);
				}
			}
			UiKit.setItems(potion, choices, Potion.NONE);
			potion.addActionListener(e ->
			{
				Object p = potion.getSelectedItem();
				if (p instanceof Potion)
				{
					changeAssumptions(b -> withPotion(b, cc, (Potion) p));
				}
			});
			potions.put(cc, potion);
			JPanel potionField = UiKit.field(cc.getDisplayName() + " boost", potion);
			potionFields.put(cc, potionField);
			assume.add(potionField);

			JComboBox<Option<Prayer>> prayer = UiKit.combo();
			List<Option<Prayer>> prayerChoices = new ArrayList<>();
			prayerChoices.add(new Option<>(null, "None"));
			for (Prayer p : Prayer.values())
			{
				if (p.getCombatClass() == cc)
				{
					prayerChoices.add(new Option<>(p, p.getDisplayName() + " (" + p.getLevel() + ")"));
				}
			}
			UiKit.setItems(prayer, prayerChoices, prayerChoices.get(0));
			prayer.addActionListener(e -> changeAssumptions(b -> withPrayer(b, cc, UiKit.selected(prayer))));
			prayers.put(cc, prayer);
			JPanel prayerField = UiKit.field(cc.getDisplayName() + " prayer", prayer);
			prayerFields.put(cc, prayerField);
			assume.add(prayerField);
		}
		slayerTask.addActionListener(e -> changeAssumptions(b -> b.onSlayerTask(slayerTask.isSelected())));
		assume.add(slayerTask);
		slayerNote.setVisible(false);
		assume.add(slayerNote);
		kandarin.addActionListener(e -> changeAssumptions(b -> b.kandarinHardDiary(kandarin.isSelected())));
		assume.add(kandarin);
		allowUnknown.setToolTipText("Items whose equip requirements the plugin cannot check are still considered, and marked as unverified");
		allowUnknown.addActionListener(e -> change(b -> b.allowUnknownRequirements(allowUnknown.isSelected())));
		assume.add(UiKit.wrappedCheck(allowUnknown, "Use items with unverified requirements"));
		dart.addActionListener(e ->
		{
			Integer id = UiKit.selected(dart);
			changeAssumptions(b -> b.blowpipeDartId(id == null ? 0 : id));
		});
		assume.add(UiKit.field("Blowpipe darts", dart));
		content.add(assume);

		// Repeated here so it is within reach after changing the assumptions.
		content.add(optimiseButton());
	}

	private JButton optimiseButton()
	{
		JButton b = UiKit.primaryButton("Optimise My Setup", () ->
		{
			controller.optimise();
			navigate.accept(BossOptimiserPanel.GEAR);
		});
		optimiseButtons.add(b);
		return b;
	}

	// ------------------------------------------------------------------ user changes

	private static CombatAssumptions.CombatAssumptionsBuilder withPotion(CombatAssumptions.CombatAssumptionsBuilder b,
		CombatClass cc, Potion potion)
	{
		switch (cc)
		{
			case MELEE:
				return b.meleePotion(potion);
			case RANGED:
				return b.rangedPotion(potion);
			default:
				return b.magicPotion(potion);
		}
	}

	private static CombatAssumptions.CombatAssumptionsBuilder withPrayer(CombatAssumptions.CombatAssumptionsBuilder b,
		CombatClass cc, Prayer prayer)
	{
		switch (cc)
		{
			case MELEE:
				return b.meleePrayer(prayer);
			case RANGED:
				return b.rangedPrayer(prayer);
			default:
				return b.magicPrayer(prayer);
		}
	}

	private static Selection selectionOf(UiState state)
	{
		return state == null || state.getSelection() == null ? Selection.builder().build() : state.getSelection();
	}

	private static PlayerLevels levelsOf(UiState state)
	{
		AccountSnapshot account = state == null ? null : state.getAccount();
		return account == null || account.getLevels() == null ? PlayerLevels.builder().build() : account.getLevels();
	}

	private static CombatAssumptions assumptionsOf(UiState state)
	{
		Selection selection = selectionOf(state);
		return selection.getAssumptions() != null ? selection.getAssumptions() : CombatAssumptions.defaultsFor(levelsOf(state));
	}

	private void change(UnaryOperator<Selection.SelectionBuilder> edit)
	{
		if (updating)
		{
			return;
		}
		Selection current = selectionOf(controller.state());
		Selection next = edit.apply(current.toBuilder()).build();
		if (!next.equals(current))
		{
			controller.select(next);
		}
	}

	private void changeAssumptions(UnaryOperator<CombatAssumptions.CombatAssumptionsBuilder> edit)
	{
		if (updating)
		{
			return;
		}
		UiState state = controller.state();
		Selection current = selectionOf(state);
		CombatAssumptions before = assumptionsOf(state);
		CombatAssumptions after = edit.apply(before.toBuilder()).build();
		if (!after.equals(current.getAssumptions()))
		{
			controller.select(current.toBuilder().assumptions(after).build());
		}
	}

	private void bossPicked()
	{
		if (updating)
		{
			return;
		}
		BossDefinition picked = bossList.getSelectedValue();
		Selection current = selectionOf(controller.state());
		if (picked != null && !Objects.equals(picked.getId(), current.getBossId()))
		{
			// The controller fills in the new boss's first variant, strategy and group size.
			controller.select(current.toBuilder().bossId(picked.getId()).variantId(null).strategyId(null).build());
		}
	}

	private void filterChanged()
	{
		if (updating)
		{
			return;
		}
		boolean was = updating;
		updating = true;
		try
		{
			fillBossList(selectionOf(controller.state()).getBossId());
		}
		finally
		{
			updating = was;
		}
		revalidate();
		repaint();
	}

	// ------------------------------------------------------------------ rendering

	private void fillBossList(String selectedId)
	{
		List<BossDefinition> all = controller.bosses();
		if (all == null)
		{
			all = new ArrayList<>();
		}
		String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		BossCategory wanted = UiKit.selected(category);
		List<BossDefinition> matches = new ArrayList<>();
		for (BossDefinition b : all)
		{
			if (b == null || b.getName() == null)
			{
				continue;
			}
			if (wanted != null && b.getCategory() != wanted)
			{
				continue;
			}
			if (!query.isEmpty() && !b.getName().toLowerCase(Locale.ROOT).contains(query))
			{
				continue;
			}
			matches.add(b);
		}
		// Alphabetical, ignoring a leading "The " (so The Leviathan sits under L).
		matches.sort(java.util.Comparator.comparing(BossSection::sortKey));
		if (!matches.equals(shownBosses))
		{
			shownBosses = matches;
			bossModel.clear();
			for (BossDefinition b : matches)
			{
				bossModel.addElement(b);
			}
		}
		int index = -1;
		for (int i = 0; i < matches.size(); i++)
		{
			if (Objects.equals(matches.get(i).getId(), selectedId))
			{
				index = i;
			}
		}
		if (index != bossList.getSelectedIndex())
		{
			if (index < 0)
			{
				bossList.clearSelection();
			}
			else
			{
				bossList.setSelectedIndex(index);
				bossList.ensureIndexIsVisible(index);
			}
		}
		noMatches.setVisible(matches.isEmpty());
	}

	@Override
	protected void update(UiState state)
	{
		Selection selection = selectionOf(state);
		List<BossDefinition> all = controller.bosses();
		BossDefinition boss = DashboardSection.findBoss(all, selection.getBossId());

		// Category filter: "All" plus the categories that actually have bosses.
		Set<BossCategory> present = EnumSet.noneOf(BossCategory.class);
		if (all != null)
		{
			for (BossDefinition b : all)
			{
				if (b != null && b.getCategory() != null)
				{
					present.add(b.getCategory());
				}
			}
		}
		List<Option<BossCategory>> categories = new ArrayList<>();
		categories.add(new Option<>(null, "All categories"));
		for (BossCategory c : present)
		{
			categories.add(new Option<>(c, c.getDisplayName()));
		}
		UiKit.setItems(category, categories, UiKit.find(categories, UiKit.selected(category)));
		fillBossList(selection.getBossId());

		// Variant, strategy, group size, priority.
		List<Option<String>> variants = new ArrayList<>();
		List<Option<String>> strategies = new ArrayList<>();
		List<Option<String>> sizes = new ArrayList<>();
		BossDefinition.Strategy chosenStrategy = null;
		if (boss != null)
		{
			for (BossDefinition.Variant v : boss.getVariants())
			{
				variants.add(new Option<>(v.getId(), v.getName()));
			}
			for (BossDefinition.Strategy s : boss.getStrategies())
			{
				strategies.add(new Option<>(s.getId(), s.getName()));
			}
			for (String size : boss.getGroupSizes())
			{
				sizes.add(new Option<>(size, groupLabel(size)));
			}
			chosenStrategy = boss.strategy(selection.getStrategyId());
		}
		if (sizes.isEmpty())
		{
			String size = selection.getGroupSize() == null ? "SOLO" : selection.getGroupSize();
			sizes.add(new Option<>(size, groupLabel(size)));
		}
		UiKit.setItems(variant, variants, variants.isEmpty() ? null : UiKit.find(variants, selection.getVariantId()));
		variant.setEnabled(variants.size() > 1);
		UiKit.setItems(strategy, strategies, strategies.isEmpty() ? null : UiKit.find(strategies, selection.getStrategyId()));
		strategy.setEnabled(strategies.size() > 1);
		String description = chosenStrategy == null || chosenStrategy.getDescription() == null ? "" : chosenStrategy.getDescription();
		if (chosenStrategy != null && chosenStrategy.isWeightsAreHeuristic())
		{
			description = (description.isEmpty() ? "" : description + " ") + "The share of the kill spent in each phase is an assumption.";
		}
		strategyText.setTextIfChanged(description);
		strategyText.setVisible(!description.isEmpty());
		UiKit.setItems(groupSize, sizes, UiKit.find(sizes, selection.getGroupSize()));
		groupSize.setEnabled(boss != null && boss.getGroupSizes().size() > 1);
		Priority chosenPriority = selection.getPriority() == null ? Priority.MAX_DPS : selection.getPriority();
		if (priority.getSelectedItem() != chosenPriority)
		{
			priority.setSelectedItem(chosenPriority);
		}
		priorityText.setTextIfChanged(chosenPriority.getDescription());

		for (JButton b : optimiseButtons)
		{
			b.setEnabled(boss != null && !state.isOptimising());
			b.setText(state.isOptimising() ? "Optimising..." : "Optimise My Setup");
		}
		cancel.setVisible(state.isOptimising());

		// Assumptions.
		CombatAssumptions assumptions = assumptionsOf(state);
		List<CombatClass> used = chosenStrategy == null ? Arrays.asList(CombatClass.values()) : chosenStrategy.combatClasses();
		if (used == null || used.isEmpty())
		{
			used = Arrays.asList(CombatClass.values());
		}
		for (CombatClass cc : CombatClass.values())
		{
			boolean show = used.contains(cc);
			potionFields.get(cc).setVisible(show);
			prayerFields.get(cc).setVisible(show);
			JComboBox<Potion> potion = potions.get(cc);
			Potion wantedPotion = assumptions.potionFor(cc);
			if (potion.getSelectedItem() != wantedPotion)
			{
				potion.setSelectedItem(wantedPotion);
			}
			JComboBox<Option<Prayer>> prayer = prayers.get(cc);
			Prayer wantedPrayer = assumptions.prayerFor(cc);
			if (UiKit.selected(prayer) != wantedPrayer)
			{
				for (int i = 0; i < prayer.getItemCount(); i++)
				{
					if (prayer.getItemAt(i).value == wantedPrayer)
					{
						prayer.setSelectedIndex(i);
					}
				}
			}
		}
		boolean taskOnly = boss != null && boss.isSlayerTaskOnly();
		slayerTask.setSelected(taskOnly || assumptions.isOnSlayerTask());
		slayerTask.setEnabled(!taskOnly);
		slayerNote.setVisible(taskOnly);
		kandarin.setSelected(assumptions.isKandarinHardDiary());
		allowUnknown.setSelected(selection.isAllowUnknownRequirements());

		List<Option<Integer>> darts = dartOptions();
		UiKit.setItems(dart, darts, UiKit.find(darts, assumptions.getBlowpipeDartId()));

		AccountSnapshot account = state.getAccount();
		if (changed(boss, account))
		{
			fillRequirements(boss, account);
		}
	}

	private static String groupLabel(String size)
	{
		if (size == null || size.isEmpty())
		{
			return "Solo";
		}
		String lower = size.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private List<Option<Integer>> dartOptions()
	{
		if (dartOptions != null)
		{
			return dartOptions;
		}
		List<Option<Integer>> out = new ArrayList<>();
		out.add(new Option<>(0, "Not set"));
		GameData data = controller.gameData();
		if (data == null)
		{
			return out;
		}
		List<ItemDef> weapons = data.itemsInSlot(ItemSlot.WEAPON);
		if (weapons != null)
		{
			for (ItemDef def : weapons)
			{
				if (def == null || def.getName() == null || !def.getName().endsWith(" dart"))
				{
					continue;
				}
				String version = def.getVersion();
				if (version == null || version.isEmpty() || "Unpoisoned".equals(version))
				{
					out.add(new Option<>(def.getId(), def.getName()));
				}
			}
		}
		dartOptions = out;
		return out;
	}

	private void fillRequirements(BossDefinition boss, AccountSnapshot account)
	{
		requirements.removeAll();
		if (boss == null)
		{
			requirements.add(UiKit.wrap("Select a boss to see what it needs."));
			return;
		}
		boolean loggedIn = account != null && account.isLoggedIn();
		boolean any = false;
		if (boss.getRequirements() != null)
		{
			for (BossDefinition.Requirement r : boss.getRequirements())
			{
				if (r == null)
				{
					continue;
				}
				any = true;
				addRequirement(r, account, loggedIn);
			}
		}
		if (!any)
		{
			requirements.add(UiKit.wrap("No quest or skill requirements are recorded for this boss."));
		}
		if (boss.getWarnings() != null)
		{
			for (String w : boss.getWarnings())
			{
				if (w != null && !w.isEmpty())
				{
					requirements.add(UiKit.wrap("Warning: " + w, UiKit.ACCENT));
				}
			}
		}
		BossDefinition.ValidationStatus status = boss.getValidationStatus();
		if (status != null)
		{
			requirements.add(UiKit.wrap("Data check: " + status.getLabel() + ".",
				status == BossDefinition.ValidationStatus.VALIDATED ? UiKit.GOOD : UiKit.ACCENT));
		}
		if (boss.getValidationNotes() != null && !boss.getValidationNotes().isEmpty())
		{
			requirements.add(UiKit.wrap(boss.getValidationNotes()));
		}
	}

	private void addRequirement(BossDefinition.Requirement r, AccountSnapshot account, boolean loggedIn)
	{
		String type = r.getType() == null ? "" : r.getType();
		String text = r.getText();
		String verdict = null;
		Color colour = UiKit.TEXT;
		if ("SKILL".equals(type))
		{
			if (text == null || text.isEmpty())
			{
				text = r.getLevel() + " " + r.getSkill();
			}
			int have = loggedIn && account.getLevels() != null && r.getSkill() != null
				? account.getLevels().get(r.getSkill().toLowerCase(Locale.ROOT)) : -1;
			if (!loggedIn)
			{
				verdict = "Not checked: log in first";
				colour = UiKit.ACCENT;
			}
			else if (have < 0)
			{
				verdict = "Unknown: this skill is not read by the plugin";
				colour = UiKit.ACCENT;
			}
			else if (have >= r.getLevel())
			{
				verdict = "Met (your level: " + have + ")";
				colour = UiKit.GOOD;
			}
			else
			{
				verdict = "Not met (your level: " + have + ", needs " + r.getLevel() + ")";
				colour = UiKit.BAD;
			}
		}
		else if ("QUEST".equals(type))
		{
			if (text == null || text.isEmpty())
			{
				text = String.valueOf(r.getQuest());
			}
			String questState = account == null || r.getQuest() == null ? null : account.questState(r.getQuest());
			boolean needsFinished = !AccountSnapshot.IN_PROGRESS.equals(r.getMinState());
			if (questState == null)
			{
				verdict = "Unknown: quest progress has not been read" + (loggedIn ? "" : " (log in first)");
				colour = UiKit.ACCENT;
			}
			else if (AccountSnapshot.FINISHED.equals(questState)
				|| (!needsFinished && AccountSnapshot.IN_PROGRESS.equals(questState)))
			{
				verdict = "Met (" + questLabel(questState) + ")";
				colour = UiKit.GOOD;
			}
			else
			{
				verdict = "Not met (" + questLabel(questState) + ", needs " + (needsFinished ? "finished" : "started") + ")";
				colour = UiKit.BAD;
			}
		}
		if (text != null && !text.isEmpty())
		{
			requirements.add(UiKit.wrap(text, UiKit.BRIGHT));
		}
		if (verdict != null)
		{
			requirements.add(UiKit.wrap(verdict, colour));
		}
	}

	private static String questLabel(String state)
	{
		if (AccountSnapshot.FINISHED.equals(state))
		{
			return "finished";
		}
		return AccountSnapshot.IN_PROGRESS.equals(state) ? "in progress" : "not started";
	}
}
