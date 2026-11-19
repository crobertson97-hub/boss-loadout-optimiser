package com.bossoptimiser.ui;

import com.bossoptimiser.inventory.InventoryPlan;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

/** The recommended 28-slot inventory, and what the bank cannot supply for it. */
final class InventorySection extends Section
{
	private static final int COLUMNS = 4;
	private static final int TILE_WIDTH = 44;
	private static final int TILE_HEIGHT = 40;

	private final JComboBox<InventoryPlan.TripLength> tripLength = UiKit.combo();
	private final JCheckBox maxFood = UiKit.check("Max food");
	private final JCheckBox morePrayer = UiKit.check("More prayer");
	private final JCheckBox minCost = UiKit.check("Min cost");
	private final WrapArea exclusions = UiKit.wrap("");
	private final JButton clearExclusions;
	private final JPanel planArea = UiKit.stack(6);

	private InventoryPlan shownPlan;
	private int selectedIndex = -1;
	private int viewVersion;

	InventorySection(UiController controller)
	{
		super("Inventory", controller);

		JPanel prefs = UiKit.card();
		prefs.add(UiKit.heading("Preferences"));
		UiKit.setItems(tripLength, Arrays.asList(InventoryPlan.TripLength.values()), InventoryPlan.TripLength.NORMAL);
		tripLength.addActionListener(e ->
		{
			Object value = tripLength.getSelectedItem();
			if (value instanceof InventoryPlan.TripLength)
			{
				change(b -> b.tripLength((InventoryPlan.TripLength) value));
			}
		});
		prefs.add(tripLength);
		maxFood.setToolTipText("Trim optional supplies to their minimum so more slots hold food");
		maxFood.addActionListener(e -> change(b -> b.maxFood(maxFood.isSelected())));
		morePrayer.setToolTipText("Two extra prayer-restoration slots");
		morePrayer.addActionListener(e -> change(b -> b.morePrayer(morePrayer.isSelected())));
		minCost.setToolTipText("Prefer the cheapest owned option in each category (needs price data)");
		minCost.addActionListener(e -> change(b -> b.minCost(minCost.isSelected())));
		prefs.add(UiKit.row(maxFood, morePrayer));
		prefs.add(minCost);
		prefs.add(exclusions);
		clearExclusions = UiKit.button("Allow excluded supplies again", () -> change(b -> b.excludedItemIds(Collections.emptySet())));
		prefs.add(clearExclusions);
		content.add(prefs);
		content.add(planArea);
	}

	private static InventoryPlan.Preferences prefsOf(UiState state)
	{
		Selection selection = state == null ? null : state.getSelection();
		InventoryPlan.Preferences prefs = selection == null ? null : selection.getInventoryPrefs();
		return prefs == null ? InventoryPlan.Preferences.builder().build() : prefs;
	}

	private void change(UnaryOperator<InventoryPlan.Preferences.PreferencesBuilder> edit)
	{
		if (updating)
		{
			return;
		}
		InventoryPlan.Preferences current = prefsOf(controller.state());
		InventoryPlan.Preferences next = edit.apply(current.toBuilder()).build();
		if (!next.equals(current))
		{
			controller.setInventoryPreferences(next);
		}
	}

	private void select(int index)
	{
		selectedIndex = index == selectedIndex ? -1 : index;
		viewVersion++;
		refresh();
	}

	@Override
	protected void update(UiState state)
	{
		InventoryPlan.Preferences prefs = prefsOf(state);
		InventoryPlan.TripLength trip = prefs.getTripLength() == null ? InventoryPlan.TripLength.NORMAL : prefs.getTripLength();
		if (tripLength.getSelectedItem() != trip)
		{
			tripLength.setSelectedItem(trip);
		}
		maxFood.setSelected(prefs.isMaxFood());
		morePrayer.setSelected(prefs.isMorePrayer());
		minCost.setSelected(prefs.isMinCost());
		int excluded = prefs.getExcludedItemIds() == null ? 0 : prefs.getExcludedItemIds().size();
		exclusions.setTextIfChanged(excluded == 0 ? "" : excluded + " supply item(s) are excluded from the plan.");
		exclusions.setVisible(excluded > 0);
		clearExclusions.setVisible(excluded > 0);

		InventoryPlan plan = state.getPlan();
		if (plan != shownPlan)
		{
			shownPlan = plan;
			// Keep the slot the user was working on selected across a re-plan, when it still exists.
			if (plan == null || plan.getSlots() == null || selectedIndex >= plan.getSlots().size())
			{
				selectedIndex = -1;
			}
			viewVersion++;
		}
		if (!changed(plan, viewVersion, state.getResult() == null))
		{
			return;
		}
		planArea.removeAll();
		if (plan == null)
		{
			planArea.add(new WrapArea(state.getResult() == null
				? "No inventory yet. Optimise a setup first; the inventory is planned around it."
				: "No inventory plan is available for this setup.", UiKit.TEXT, UiKit.CONTENT_WIDTH));
			return;
		}
		List<InventoryPlan.Slot> slots = plan.getSlots() == null ? Collections.emptyList() : plan.getSlots();
		addGrid(slots);
		addSelected(slots);
		addMissing(plan);
		if (plan.getNotes() != null)
		{
			for (String note : plan.getNotes())
			{
				if (note != null && !note.isEmpty())
				{
					planArea.add(new WrapArea(note, UiKit.TEXT, UiKit.CONTENT_WIDTH));
				}
			}
		}
	}

	private void addGrid(List<InventoryPlan.Slot> slots)
	{
		int rows = InventoryPlan.SIZE / COLUMNS;
		JPanel grid = new JPanel(new GridLayout(rows, COLUMNS, 3, 3));
		grid.setOpaque(false);
		for (int i = 0; i < InventoryPlan.SIZE; i++)
		{
			final int index = i;
			ItemTile tile = new ItemTile(TILE_WIDTH, TILE_HEIGHT);
			InventoryPlan.Slot slot = i < slots.size() ? slots.get(i) : null;
			if (slot == null)
			{
				tile.setItem(controller, 0, 0, null, "");
				grid.add(tile);
				continue;
			}
			// The quantity is drawn here, so the plain single-item icon is requested.
			tile.setItem(controller, slot.getItemId(), 1, slot.getName(), slot.getName());
			if (slot.getQuantity() > 1)
			{
				tile.setQuantityText(QuantityFormatter.quantityToStackSize(slot.getQuantity()));
			}
			tile.setToolTipText(UiKit.plain(slot.getName()) + " — " + category(slot) + " — " + nullToEmpty(slot.getReason())
				+ (slot.isOwned() ? "" : " (not owned)") + (slot.isLocked() ? " (locked)" : ""));
			if (!slot.isOwned())
			{
				tile.setOutline(UiKit.BAD, 2);
				tile.setMarker("!", UiKit.BAD);
			}
			else if (slot.isLocked())
			{
				tile.setOutline(UiKit.ACCENT, 2);
			}
			if (index == selectedIndex)
			{
				tile.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
			tile.onClick(() -> select(index));
			grid.add(tile);
		}
		JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holder.setOpaque(false);
		holder.add(grid);
		planArea.add(holder);
		planArea.add(new WrapArea("Click a slot to lock it or to exclude its item. Red: you do not own it. Orange: locked.",
			UiKit.TEXT, UiKit.CONTENT_WIDTH));
	}

	private void addSelected(List<InventoryPlan.Slot> slots)
	{
		if (selectedIndex < 0 || selectedIndex >= slots.size() || slots.get(selectedIndex) == null)
		{
			return;
		}
		final int index = selectedIndex;
		final InventoryPlan.Slot slot = slots.get(index);
		JPanel card = UiKit.card();
		card.add(UiKit.wrap(slot.getName() + (slot.getQuantity() > 1 ? " x " + QuantityFormatter.formatNumber(slot.getQuantity()) : ""),
			UiKit.BRIGHT));
		card.add(UiKit.wrap("Slot " + (index + 1) + " - " + category(slot)));
		if (slot.getReason() != null && !slot.getReason().isEmpty())
		{
			card.add(UiKit.wrap(slot.getReason()));
		}
		if (!slot.isOwned())
		{
			card.add(UiKit.wrap("You do not own what this slot asks for.", UiKit.BAD));
		}
		JButton lock = slot.isLocked()
			? UiKit.button("Unlock", () -> controller.lockInventorySlot(index, false))
			: UiKit.button("Lock", () -> controller.lockInventorySlot(index, true));
		JButton exclude = UiKit.button("Exclude this item", () -> controller.excludeInventoryItem(slot.getItemId()));
		exclude.setEnabled(slot.getItemId() > 0);
		card.add(lock);
		card.add(exclude);
		planArea.add(card);
	}

	private void addMissing(InventoryPlan plan)
	{
		List<InventoryPlan.Missing> missing = plan.getMissing() == null ? Collections.emptyList() : plan.getMissing();
		JPanel card = UiKit.card();
		card.add(UiKit.heading("Missing items"));
		if (missing.isEmpty())
		{
			card.add(UiKit.wrap("Nothing is missing: you own everything in this plan.", UiKit.GOOD));
		}
		boolean first = true;
		for (InventoryPlan.Missing m : missing)
		{
			if (m == null)
			{
				continue;
			}
			if (!first)
			{
				card.add(new JSeparator());
			}
			first = false;
			JPanel text = UiKit.stack(1, UiKit.CARD_WIDTH - UiKit.ICON_WIDTH - 4);
			text.add(new WrapArea(m.getName(), UiKit.BRIGHT, UiKit.CARD_WIDTH - UiKit.ICON_WIDTH - 4));
			text.add(new WrapArea("You have " + m.getOwned() + " of " + m.getNeeded(), UiKit.TEXT, UiKit.CARD_WIDTH - UiKit.ICON_WIDTH - 4));
			card.add(UiKit.iconRow(UiKit.itemIcon(controller, m.getItemId(), 1, m.getName()), text));
			if (m.isMandatory())
			{
				card.add(UiKit.wrap("MANDATORY", UiKit.BAD));
			}
			else
			{
				card.add(UiKit.wrap("Optional"));
			}
			if (m.getReason() != null && !m.getReason().isEmpty())
			{
				card.add(UiKit.wrap(m.getReason()));
			}
			int shortfall = Math.max(1, m.getNeeded() - m.getOwned());
			long each = m.getItemId() > 0 ? controller.price(m.getItemId()) : 0;
			card.add(UiKit.wrap("To buy " + shortfall + ": " + (each <= 0 ? "price unavailable" : "about " + UiKit.gp(each * shortfall))));
		}
		planArea.add(card);
	}

	private static String category(InventoryPlan.Slot slot)
	{
		String c = slot.getCategory();
		if (c == null || c.isEmpty())
		{
			return "item";
		}
		String lower = c.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static String nullToEmpty(String s)
	{
		return s == null ? "" : s;
	}
}
