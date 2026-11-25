package com.bossoptimiser.ui;

import com.bossoptimiser.account.Usability;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.DpsResult;
import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.optimiser.OptimisedSetup;
import com.bossoptimiser.optimiser.OptimiserRequest;
import com.bossoptimiser.optimiser.OptimiserResult;
import com.bossoptimiser.optimiser.SlotAnalyser;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/** The recommended equipment, why it was chosen, and what else could go in each slot. */
final class GearSection extends Section
{
	/** Same arrangement as the in-game equipment tab. */
	private static final ItemSlot[][] GRID = {
		{null, ItemSlot.HEAD, null},
		{ItemSlot.CAPE, ItemSlot.NECK, ItemSlot.AMMO},
		{ItemSlot.WEAPON, ItemSlot.BODY, ItemSlot.SHIELD},
		{null, ItemSlot.LEGS, null},
		{ItemSlot.HANDS, ItemSlot.FEET, ItemSlot.RING},
	};
	private static final int TILE = 44;
	private static final int ALTERNATIVES_SHOWN = 6;
	private static final double EPSILON = 0.0005;

	private final Consumer<String> navigate;

	// What the user is looking at; reset when a new result arrives.
	private OptimiserResult shownResult;
	private CombatClass shownClass;
	/** Index into the result's alternative setups, or -1 for the recommended setup. */
	private int comparing = -1;
	private ItemSlot openSlot;
	/** Null while the answer for {@link #openSlot} is being computed. */
	private List<SlotAnalyser.Alternative> slotAlternatives;
	private boolean allAlternatives;
	private boolean explaining;
	private List<String> explanation;
	private boolean unusableOpen;
	private int alternativesRequest;
	private int explainRequest;
	private int viewVersion;

	private final BankTagCard bankCard;

	GearSection(UiController controller, Consumer<String> navigate)
	{
		super("Gear", controller);
		bankCard = new BankTagCard(controller);
		this.navigate = navigate;
	}

	// ------------------------------------------------------------------ view state

	private void viewChanged()
	{
		viewVersion++;
		if (!updating)
		{
			refresh();
		}
	}

	private void openSlot(ItemSlot slot)
	{
		allAlternatives = false;
		if (slot == null || slot == openSlot)
		{
			openSlot = null;
			slotAlternatives = null;
			alternativesRequest++;
		}
		else
		{
			openSlot = slot;
			requestAlternatives();
		}
		viewChanged();
	}

	private void requestAlternatives()
	{
		final int request = ++alternativesRequest;
		slotAlternatives = null;
		if (openSlot == null || shownClass == null)
		{
			return;
		}
		controller.slotAlternatives(shownClass, openSlot, list ->
		{
			if (request != alternativesRequest)
			{
				return;
			}
			slotAlternatives = list == null ? Collections.emptyList() : list;
			viewChanged();
		});
	}

	private void requestExplanation()
	{
		final int request = ++explainRequest;
		if (shownClass == null)
		{
			return;
		}
		explaining = true;
		explanation = null;
		controller.explain(shownClass, lines ->
		{
			if (request != explainRequest)
			{
				return;
			}
			explaining = false;
			explanation = lines == null ? Collections.emptyList() : lines;
			viewChanged();
		});
		viewChanged();
	}

	private void showClass(CombatClass cc)
	{
		shownClass = cc;
		comparing = -1;
		openSlot = null;
		slotAlternatives = null;
		explanation = null;
		explaining = false;
		alternativesRequest++;
		explainRequest++;
		viewChanged();
	}

	private void compare(int index)
	{
		comparing = index;
		if (index >= 0 && shownResult != null && shownResult.primary() != null)
		{
			// Alternative setups belong to the primary combat class.
			shownClass = shownResult.primary().getCombatClass();
		}
		openSlot = null;
		slotAlternatives = null;
		alternativesRequest++;
		viewChanged();
	}

	// ------------------------------------------------------------------ rendering

	@Override
	protected void update(UiState state)
	{
		bankCard.update(state);
		OptimiserResult result = state.getResult();
		Selection selection = state.getSelection() == null ? Selection.builder().build() : state.getSelection();
		if (result != shownResult)
		{
			shownResult = result;
			comparing = -1;
			explanation = null;
			explaining = false;
			allAlternatives = false;
			slotAlternatives = null;
			alternativesRequest++;
			explainRequest++;
			viewVersion++;
			Map<CombatClass, OptimisedSetup> setups = setupsOf(result);
			if (setups.isEmpty())
			{
				shownClass = null;
				openSlot = null;
			}
			else
			{
				if (shownClass == null || !setups.containsKey(shownClass))
				{
					shownClass = setups.keySet().iterator().next();
					openSlot = null;
				}
				if (openSlot != null)
				{
					// Keep the slot the user was working on open across a re-optimisation.
					requestAlternatives();
				}
			}
		}
		if (!changed(result, selection.getLockedSlots(), selection.getExcludedItemIds(), selection.getBossId(),
			state.isOptimising(), state.getStatus(), viewVersion))
		{
			return;
		}
		content.removeAll();
		build(state, result, selection);
	}

	private static Map<CombatClass, OptimisedSetup> setupsOf(OptimiserResult result)
	{
		if (result == null || result.getSetups() == null)
		{
			return Collections.emptyMap();
		}
		Map<CombatClass, OptimisedSetup> out = new LinkedHashMap<>();
		for (Map.Entry<CombatClass, OptimisedSetup> e : result.getSetups().entrySet())
		{
			if (e.getKey() != null && e.getValue() != null)
			{
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	private static <T> List<T> orEmpty(List<T> list)
	{
		return list == null ? Collections.emptyList() : list;
	}

	private void build(UiState state, OptimiserResult result, Selection selection)
	{
		if (state.isOptimising())
		{
			content.add(UiKit.wrap("Optimising... the setup below is the previous result until this finishes.", UiKit.ACCENT));
			content.add(UiKit.button("Cancel", controller::cancelOptimise));
		}
		Map<CombatClass, OptimisedSetup> setups = setupsOf(result);
		if (setups.isEmpty())
		{
			if (result != null)
			{
				addWarnings(result, state.getStatus());
			}
			content.add(UiKit.wrap(state.isOptimising() ? "Working on your setup..."
				: "No setup yet. Choose a boss, then press Optimise My Setup."));
			if (!state.isOptimising())
			{
				JButton optimise = UiKit.primaryButton("Optimise My Setup", controller::optimise);
				optimise.setEnabled(selection.getBossId() != null);
				content.add(optimise);
				content.add(UiKit.button("Choose boss", () -> navigate.accept(BossOptimiserPanel.BOSS)));
			}
			addExcluded(selection);
			return;
		}

		List<OptimisedSetup> others = orEmpty(result.getAlternatives());
		boolean readOnly = comparing >= 0 && comparing < others.size() && others.get(comparing) != null;
		OptimisedSetup recommended = setups.get(shownClass);
		OptimisedSetup setup = readOnly ? others.get(comparing) : recommended;

		addHeadline(result, setups);
		addWarnings(result, state.getStatus());

		if (setups.size() > 1)
		{
			List<JToggleButton> toggles = new ArrayList<>();
			for (CombatClass cc : setups.keySet())
			{
				JToggleButton b = new JToggleButton(cc.getDisplayName(), !readOnly && cc == shownClass);
				b.setFont(FontManager.getRunescapeSmallFont());
				b.setFocusPainted(false);
				b.addActionListener(e -> UiKit.safely("show setup", () -> showClass(cc)));
				toggles.add(b);
			}
			content.add(UiKit.row(toggles.toArray(new JToggleButton[0])));
		}
		if (readOnly)
		{
			JPanel banner = UiKit.card();
			banner.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiKit.ACCENT), banner.getBorder()));
			banner.add(UiKit.wrap("Comparing alternative " + (comparing + 1), UiKit.ACCENT));
			banner.add(UiKit.wrap("This is not the recommended setup. It is shown for comparison only and cannot be edited."));
			banner.add(UiKit.button("Back to recommended", () -> compare(-1)));
			content.add(banner);
		}
		if (setup == null)
		{
			content.add(UiKit.wrap("There is no setup to show for this combat style."));
		}
		else
		{
			addGrid(setup, selection, readOnly);
			if (!readOnly && openSlot != null)
			{
				addSlotAlternatives(setup, selection);
			}
			addDetails(setup, result);
			if (!readOnly)
			{
				addExplanation();
			}
		}

		List<ItemDef> switches = orEmpty(result.getSwitchItems());
		if (!switches.isEmpty())
		{
			StringBuilder sb = new StringBuilder("Carried switch items: ");
			for (int i = 0; i < switches.size(); i++)
			{
				sb.append(i == 0 ? "" : ", ").append(switches.get(i) == null ? "?" : switches.get(i).displayName());
			}
			content.add(UiKit.wrap(sb.toString()));
		}

		if (!readOnly)
		{
			content.add(bankCard);
		}
		addExcluded(selection);
		addOtherSetups(result, others, readOnly);
		addUnusable(result);
	}

	private void addHeadline(OptimiserResult result, Map<CombatClass, OptimisedSetup> setups)
	{
		JPanel head = UiKit.card();
		OptimiserRequest request = result.getRequest();
		BossDefinition boss = request == null ? null : request.getBoss();
		if (boss != null)
		{
			BossDefinition.Strategy strategy = boss.strategy(request.getStrategyId());
			head.add(UiKit.wrap(boss.getName() + (strategy == null ? "" : " - " + strategy.getName())));
		}
		head.add(UiKit.label("Total DPS"));
		JLabel dps = UiKit.heading(UiKit.dp(result.getTotalDps(), 2));
		dps.setFont(FontManager.getRunescapeBoldFont().deriveFont(32f));
		head.add(dps);
		boolean exact = true;
		for (OptimisedSetup s : setups.values())
		{
			exact &= s.isExact();
		}
		if (!exact)
		{
			head.add(UiKit.wrap("Estimate — see notes", UiKit.ACCENT));
		}
		if (setups.size() > 1)
		{
			head.add(UiKit.wrap("Averaged over the phases of this strategy, each fought with its own setup."));
		}
		content.add(head);
	}

	private void addWarnings(OptimiserResult result, String status)
	{
		for (String w : orEmpty(result.getWarnings()))
		{
			// The status line above the sections may already say exactly this.
			if (w != null && !w.isEmpty() && !w.equals(status))
			{
				content.add(new WrapArea(w, UiKit.ACCENT, UiKit.CONTENT_WIDTH));
			}
		}
		if (result.isSearchBounded())
		{
			content.add(new WrapArea("The search was limited to the strongest candidates in each slot.", UiKit.TEXT, UiKit.CONTENT_WIDTH));
		}
	}

	private void addGrid(OptimisedSetup setup, Selection selection, boolean readOnly)
	{
		Loadout loadout = setup.getLoadout() == null ? Loadout.EMPTY : setup.getLoadout();
		Map<ItemSlot, Integer> locks = selection.getLockedSlots() == null ? Collections.emptyMap() : selection.getLockedSlots();
		List<Integer> unverified = orEmpty(setup.getUnverifiedItemIds());

		JPanel grid = new JPanel(new GridLayout(GRID.length, 3, 4, 4));
		grid.setOpaque(false);
		for (ItemSlot[] row : GRID)
		{
			for (ItemSlot slot : row)
			{
				if (slot == null)
				{
					grid.add(new JLabel());
					continue;
				}
				ItemDef item = loadout.get(slot);
				ItemTile tile = new ItemTile(TILE, TILE);
				tile.setItem(controller, item == null ? 0 : item.getId(), 1, item == null ? null : item.displayName(),
					slot.getDisplayName());
				boolean locked = locks.containsKey(slot);
				boolean unknown = item != null && unverified.contains(item.getId());
				if (locked)
				{
					tile.setOutline(UiKit.ACCENT, 2);
				}
				else if (!readOnly && slot == openSlot)
				{
					tile.setOutline(Color.WHITE, 2);
				}
				else if (unknown)
				{
					tile.setOutline(Color.YELLOW, 1);
				}
				if (unknown)
				{
					tile.setMarker("?", Color.YELLOW);
				}
				String tip = slot.getDisplayName() + ": " + (item == null ? "empty" : UiKit.plain(item.displayName()))
					+ (locked ? " (locked)" : "") + (unknown ? " - equip requirement unverified" : "");
				tile.setToolTipText(tip);
				if (!readOnly)
				{
					tile.onClick(() -> openSlot(slot));
				}
				grid.add(tile);
			}
		}
		JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holder.setOpaque(false);
		holder.add(grid);
		content.add(holder);

		StringBuilder legend = new StringBuilder();
		if (!readOnly)
		{
			legend.append("Click a slot to see what else you own for it.");
		}
		if (!locks.isEmpty())
		{
			legend.append(legend.length() == 0 ? "" : " ").append("Orange border: locked by you.");
		}
		if (!unverified.isEmpty())
		{
			legend.append(legend.length() == 0 ? "" : " ").append("\"?\": equip requirement unverified.");
		}
		if (legend.length() > 0)
		{
			content.add(new WrapArea(legend.toString(), UiKit.TEXT, UiKit.CONTENT_WIDTH));
		}
	}

	private void addDetails(OptimisedSetup setup, OptimiserResult result)
	{
		JPanel card = UiKit.card();
		if (setup.getCombatClass() != null)
		{
			card.add(UiKit.heading(setup.getCombatClass().getDisplayName() + " setup"));
		}
		if (setup.getStyle() != null)
		{
			card.add(UiKit.wrap("Attack style: " + setup.getStyle().describe()));
		}
		if (setup.getSpell() != null)
		{
			card.add(UiKit.wrap("Spell: " + setup.getSpell().getName()));
		}
		if (setup.getBlowpipeDart() != null)
		{
			card.add(UiKit.wrap("Blowpipe darts: " + setup.getBlowpipeDart().displayName()));
		}

		Map<String, DpsResult> phases = setup.getPhaseResults() == null ? Collections.emptyMap() : setup.getPhaseResults();
		if (!phases.isEmpty())
		{
			card.add(tableRow(UiKit.TEXT, "DPS", "Max hit", "Accuracy", "Speed"));
			Map<String, String> names = phaseNames(result);
			for (Map.Entry<String, DpsResult> e : phases.entrySet())
			{
				DpsResult r = e.getValue();
				if (r == null)
				{
					continue;
				}
				String name = names.getOrDefault(e.getKey(), e.getKey());
				card.add(UiKit.wrap(name + (r.isExact() ? "" : " (estimate)"), r.isExact() ? UiKit.BRIGHT : UiKit.ACCENT));
				card.add(tableRow(UiKit.BRIGHT, UiKit.dp(r.getDps(), 3), String.valueOf(r.getMaxHit()),
					UiKit.dp(r.getAccuracy() * 100.0, 1) + "%", r.getAttackSpeed() + " ticks"));
			}
		}
		card.add(UiKit.wrap("Setup DPS: " + UiKit.dp(setup.getDps(), 3) + (setup.isExact() ? "" : " (estimate)")));
		card.add(UiKit.wrap("Defence score (heuristic): " + UiKit.dp(setup.getDefenceScore(), 1)));
		card.add(UiKit.wrap("Prayer bonus: " + UiKit.signed(setup.getPrayerBonus())));
		if (setup.getCostPerHour() >= 0)
		{
			card.add(UiKit.wrap("Running cost: about " + QuantityFormatter.formatNumber(Math.round(setup.getCostPerHour()))
				+ " gp per hour" + (setup.isCostComplete() ? "" : " (partial)")));
		}
		else
		{
			card.add(UiKit.wrap("Running cost: price unavailable"));
		}
		if (setup.isExact())
		{
			card.add(UiKit.wrap("Exact", UiKit.GOOD));
		}
		else
		{
			card.add(UiKit.wrap("Estimate — see notes", UiKit.ACCENT));
		}
		for (String note : orEmpty(setup.getNotes()))
		{
			if (note != null && !note.isEmpty())
			{
				card.add(UiKit.wrap(note));
			}
		}
		content.add(card);
	}

	private static JPanel tableRow(Color colour, String... cells)
	{
		JPanel row = new JPanel(new GridLayout(1, cells.length, 2, 0));
		row.setOpaque(false);
		for (String cell : cells)
		{
			JLabel l = UiKit.label(cell, colour);
			l.setToolTipText(cell);
			row.add(l);
		}
		return row;
	}

	private static Map<String, String> phaseNames(OptimiserResult result)
	{
		Map<String, String> names = new LinkedHashMap<>();
		OptimiserRequest request = result.getRequest();
		BossDefinition boss = request == null ? null : request.getBoss();
		BossDefinition.Variant variant = boss == null ? null : boss.variant(request.getVariantId());
		if (variant != null && variant.getPhases() != null)
		{
			for (BossDefinition.Phase p : variant.getPhases())
			{
				if (p != null && p.getId() != null && p.getName() != null)
				{
					names.put(p.getId(), p.getName());
				}
			}
		}
		return names;
	}

	private void addExplanation()
	{
		JButton why = UiKit.button("Why these items?", this::requestExplanation);
		why.setEnabled(!explaining);
		content.add(why);
		if (explaining)
		{
			content.add(UiKit.wrap("Working out the reasons..."));
		}
		else if (explanation != null)
		{
			JPanel card = UiKit.card();
			if (explanation.isEmpty())
			{
				card.add(UiKit.wrap("No explanation is available for this setup."));
			}
			for (String line : explanation)
			{
				if (line != null && !line.isEmpty())
				{
					card.add(UiKit.wrap(line));
				}
			}
			card.add(UiKit.button("Hide", () ->
			{
				explanation = null;
				viewChanged();
			}));
			content.add(card);
		}
	}

	private void addSlotAlternatives(OptimisedSetup setup, Selection selection)
	{
		final ItemSlot slot = openSlot;
		Map<ItemSlot, Integer> locks = selection.getLockedSlots() == null ? Collections.emptyMap() : selection.getLockedSlots();
		Set<Integer> excluded = selection.getExcludedItemIds() == null ? Collections.emptySet() : selection.getExcludedItemIds();

		JPanel header = UiKit.card();
		header.add(UiKit.heading(slot.getDisplayName() + " slot"));
		if (locks.containsKey(slot))
		{
			header.add(UiKit.wrap("This slot is locked by you.", UiKit.ACCENT));
			header.add(UiKit.row(UiKit.button("Unlock slot", () -> controller.lockSlot(slot, null)),
				UiKit.button("Close", () -> openSlot(null))));
		}
		else
		{
			header.add(UiKit.button("Close", () -> openSlot(null)));
		}
		content.add(header);

		if (slotAlternatives == null)
		{
			content.add(UiKit.wrap("Comparing the items you own for this slot..."));
			return;
		}
		if (slotAlternatives.isEmpty())
		{
			content.add(UiKit.wrap(slot == ItemSlot.WEAPON
				? "No other owned weapon uses the same attack style. Different weapons are compared under Other setups."
				: "You do not own anything else that fits this slot."));
			return;
		}
		int limit = allAlternatives ? slotAlternatives.size() : Math.min(ALTERNATIVES_SHOWN, slotAlternatives.size());
		for (int i = 0; i < limit; i++)
		{
			SlotAnalyser.Alternative alt = slotAlternatives.get(i);
			if (alt != null && alt.getItem() != null)
			{
				content.add(alternativeCard(alt, slot, setup.getCombatClass(), locks, excluded));
			}
		}
		if (limit < slotAlternatives.size())
		{
			content.add(UiKit.button("Show all " + slotAlternatives.size(), () ->
			{
				allAlternatives = true;
				viewChanged();
			}));
		}
	}

	private JPanel alternativeCard(SlotAnalyser.Alternative alt, ItemSlot slot, CombatClass cc,
		Map<ItemSlot, Integer> locks, Set<Integer> excluded)
	{
		final ItemDef item = alt.getItem();
		final int id = item.getId();
		JPanel card = UiKit.card();
		if (alt.isSelected())
		{
			card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiKit.GOOD), card.getBorder()));
		}
		JLabel icon = UiKit.itemIcon(controller, id, 1, item.displayName());
		card.add(UiKit.iconRow(icon, UiKit.wrapBesideIcon(item.displayName() + (alt.isSelected() ? " (chosen)" : ""), UiKit.BRIGHT)));
		if (alt.getLocation() != null)
		{
			card.add(UiKit.wrap("Where: " + alt.getLocation().getLabel()));
		}

		List<JLabel> dps = new ArrayList<>();
		dps.add(UiKit.label("DPS " + UiKit.dp(alt.getDps(), 3), UiKit.BRIGHT));
		if (!alt.isSelected())
		{
			dps.add(UiKit.label(UiKit.signed(alt.getDpsDelta(), 3), UiKit.deltaColour(alt.getDpsDelta(), EPSILON)));
		}
		if (!alt.isExact())
		{
			dps.add(UiKit.label("estimate", UiKit.ACCENT));
		}
		card.add(UiKit.line(dps));
		if (!alt.isSelected())
		{
			card.add(UiKit.wrap("Defence score (heuristic) " + UiKit.signed(alt.getDefenceDelta(), 1)
				+ ", prayer bonus " + UiKit.signed(alt.getPrayerDelta())));
		}

		Usability usability = alt.getUsability();
		boolean unknown = false;
		if (usability != null && usability.getState() != null)
		{
			String reason = usability.getReason() == null || usability.getReason().isEmpty() ? "" : ": " + usability.getReason();
			switch (usability.getState())
			{
				case UNUSABLE:
					card.add(UiKit.wrap("Cannot be equipped" + reason, UiKit.BAD));
					break;
				case UNKNOWN:
					unknown = true;
					card.add(UiKit.wrap("Requirement unverified" + reason, UiKit.ACCENT));
					break;
				default:
					card.add(UiKit.wrap("Usable" + reason));
					break;
			}
		}
		card.add(UiKit.wrap(bonuses(item, cc)));
		if (alt.getReason() != null && !alt.getReason().isEmpty())
		{
			card.add(UiKit.wrap(alt.getReason()));
		}

		Integer lockedId = locks.get(slot);
		JButton lock = lockedId != null && lockedId == id
			? UiKit.button("Unlock", () -> controller.lockSlot(slot, null))
			: UiKit.button("Lock", () -> controller.lockSlot(slot, id));
		JButton exclude = excluded.contains(id)
			? UiKit.button("Un-exclude", () -> controller.setExcluded(id, false))
			: UiKit.button("Exclude", () -> controller.setExcluded(id, true));
		card.add(UiKit.row(lock, exclude));
		if (unknown)
		{
			card.add(UiKit.button("I can equip this", () -> controller.setConfirmedUsable(id, true)));
		}
		return card;
	}

	/** The equipment bonuses that matter for the combat class, plus defences. */
	private static String bonuses(ItemDef d, CombatClass cc)
	{
		StringBuilder sb = new StringBuilder();
		if (cc == CombatClass.RANGED)
		{
			sb.append("Ranged attack ").append(UiKit.signed(d.getAranged()))
				.append(", ranged strength ").append(UiKit.signed(d.getRstr()));
		}
		else if (cc == CombatClass.MAGIC)
		{
			sb.append("Magic attack ").append(UiKit.signed(d.getAmagic()))
				.append(", magic damage ").append(UiKit.signed(d.getMdmg() / 10.0, 1)).append("%");
		}
		else
		{
			sb.append("Attack: stab ").append(UiKit.signed(d.getAstab()))
				.append(", slash ").append(UiKit.signed(d.getAslash()))
				.append(", crush ").append(UiKit.signed(d.getAcrush()))
				.append("; strength ").append(UiKit.signed(d.getStr()));
		}
		if (d.getSlot() == ItemSlot.WEAPON && d.getSpeed() > 0)
		{
			sb.append("\nAttack interval: ").append(d.getSpeed()).append(" ticks");
		}
		sb.append("\nDefence: stab ").append(UiKit.signed(d.getDstab()))
			.append(", slash ").append(UiKit.signed(d.getDslash()))
			.append(", crush ").append(UiKit.signed(d.getDcrush()))
			.append(", magic ").append(UiKit.signed(d.getDmagic()))
			.append(", ranged ").append(UiKit.signed(d.getDranged()));
		sb.append("\nPrayer ").append(UiKit.signed(d.getPrayer()));
		return sb.toString();
	}

	private String itemName(int itemId)
	{
		GameData data = controller.gameData();
		ItemDef def = data == null ? null : data.item(itemId);
		return def == null ? "Item " + itemId : def.displayName();
	}

	private void addExcluded(Selection selection)
	{
		Set<Integer> excluded = selection.getExcludedItemIds();
		if (excluded == null || excluded.isEmpty())
		{
			return;
		}
		JPanel card = UiKit.card();
		card.add(UiKit.heading("Excluded items"));
		card.add(UiKit.wrap("These are never recommended."));
		for (Integer id : new TreeSet<>(excluded))
		{
			if (id == null)
			{
				continue;
			}
			String name = itemName(id);
			JLabel label = UiKit.label(UiKit.plain(name), UiKit.BRIGHT);
			label.setToolTipText(UiKit.plain(name));
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(false);
			row.add(label, BorderLayout.CENTER);
			row.add(UiKit.button("Un-exclude", () -> controller.setExcluded(id, false)), BorderLayout.EAST);
			card.add(row);
		}
		content.add(card);
	}

	private void addOtherSetups(OptimiserResult result, List<OptimisedSetup> others, boolean readOnly)
	{
		if (others.isEmpty())
		{
			return;
		}
		OptimisedSetup primary = result.primary();
		content.add(UiKit.heading("Other setups"));
		content.add(new WrapArea("Click one to compare it with the recommended setup.", UiKit.TEXT, UiKit.CONTENT_WIDTH));
		for (int i = 0; i < others.size(); i++)
		{
			final int index = i;
			OptimisedSetup alt = others.get(i);
			if (alt == null)
			{
				continue;
			}
			ItemDef weapon = alt.getLoadout() == null ? null : alt.getLoadout().weapon();
			JPanel card = UiKit.card();
			boolean shown = readOnly && comparing == i;
			if (shown)
			{
				card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiKit.ACCENT), card.getBorder()));
			}
			card.add(UiKit.wrap((i + 1) + ". " + (weapon == null ? "Unarmed" : weapon.displayName())
				+ (alt.getStyle() == null ? "" : " - " + alt.getStyle().getName()), UiKit.BRIGHT));
			List<JLabel> dps = new ArrayList<>();
			dps.add(UiKit.label("DPS " + UiKit.dp(alt.getDps(), 3), UiKit.TEXT));
			if (primary != null)
			{
				double delta = alt.getDps() - primary.getDps();
				dps.add(UiKit.label(UiKit.signed(delta, 3), UiKit.deltaColour(delta, EPSILON)));
			}
			if (!alt.isExact())
			{
				dps.add(UiKit.label("estimate", UiKit.ACCENT));
			}
			card.add(UiKit.line(dps));
			card.add(UiKit.wrap("Defence score (heuristic): " + UiKit.dp(alt.getDefenceScore(), 1)));
			UiKit.clickable(card, () -> compare(shown ? -1 : index));
			card.setToolTipText(shown ? "Click to go back to the recommended setup" : "Click to compare");
			content.add(card);
		}
	}

	private void addUnusable(OptimiserResult result)
	{
		List<String> unusable = orEmpty(result.getUnusableOwned());
		if (unusable.isEmpty())
		{
			return;
		}
		JButton toggle = UiKit.button((unusableOpen ? "Hide" : "Show") + " owned but unusable (" + unusable.size() + ")", () ->
		{
			unusableOpen = !unusableOpen;
			viewChanged();
		});
		toggle.setHorizontalAlignment(SwingConstants.CENTER);
		content.add(toggle);
		if (unusableOpen)
		{
			JPanel card = UiKit.card();
			card.add(UiKit.heading("Owned but unusable"));
			for (String line : unusable)
			{
				if (line != null && !line.isEmpty())
				{
					card.add(UiKit.wrap(line));
				}
			}
			content.add(card);
		}
	}
}
