package com.bossoptimiser.inventory;

import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.Potion;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.SpellDef;
import com.bossoptimiser.inventory.InventoryPlan.Locked;
import com.bossoptimiser.inventory.InventoryPlan.Missing;
import com.bossoptimiser.inventory.InventoryPlan.Preferences;
import com.bossoptimiser.inventory.InventoryPlan.Slot;
import com.bossoptimiser.inventory.InventoryPlan.TripLength;
import com.bossoptimiser.inventory.SupplyCatalogue.Family;
import com.bossoptimiser.optimiser.OptimisedSetup;
import com.bossoptimiser.optimiser.OptimiserResult;
import com.bossoptimiser.optimiser.PriceSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a 28-slot inventory from the boss strategy's template, the optimiser's switch items and
 * what the account actually owns. It never reports an item as available unless the snapshot has it,
 * and it makes no kills-per-trip prediction.
 */
public final class InventoryPlanner
{
	/** Spell casts to carry runes for, by trip length, when a strategy autocasts. */
	private static final int[] AUTOCAST_CASTS = {150, 300, 500};

	private final SupplyCatalogue supplies;

	public InventoryPlanner(SupplyCatalogue supplies)
	{
		this.supplies = supplies;
	}

	private static final class Builder
	{
		final List<Slot> slots = new ArrayList<>();
		final List<Missing> missing = new ArrayList<>();
		final List<String> notes = new ArrayList<>();
		/** Quantities already promised to earlier slots, so the same item is not handed out twice. */
		final Map<Integer, Integer> reserved = new LinkedHashMap<>();

		int free()
		{
			return InventoryPlan.SIZE - slots.size();
		}

		void add(int itemId, String name, int qty, String category, boolean owned, boolean locked, String reason)
		{
			if (free() <= 0)
			{
				return;
			}
			slots.add(Slot.builder().index(slots.size()).itemId(itemId).name(name).quantity(qty).category(category)
				.owned(owned).locked(locked).reason(reason).build());
		}

		int available(OwnedItems owned, int itemId)
		{
			return owned.count(itemId) - reserved.getOrDefault(itemId, 0);
		}

		void reserve(int itemId, int qty)
		{
			reserved.merge(itemId, qty, Integer::sum);
		}
	}

	public InventoryPlan plan(BossDefinition boss, BossDefinition.Strategy strategy, OptimiserResult result,
		OwnedItems owned, CombatAssumptions assumptions, Preferences prefs, PriceSource prices)
	{
		Builder b = new Builder();
		final TripLength trip = prefs.getTripLength();

		for (Locked lock : prefs.getLocked())
		{
			boolean has = owned.count(lock.getItemId()) >= lock.getQuantity();
			b.add(lock.getItemId(), lock.getName(), lock.getQuantity(), "LOCKED", has, true, "Locked by you");
			b.reserve(lock.getItemId(), lock.getQuantity());
			if (!has)
			{
				b.missing.add(new Missing(lock.getItemId(), lock.getName(), lock.getQuantity(), owned.count(lock.getItemId()), false, "Locked by you"));
			}
		}

		if (result != null)
		{
			for (ItemDef item : result.getSwitchItems())
			{
				b.add(item.getId(), item.displayName(), 1, "SWITCH", true, false, "Equipment switch");
			}
		}

		List<BossDefinition.InventoryRule> rules = strategy == null ? new ArrayList<>() : strategy.getInventory();
		BossDefinition.InventoryRule fill = null;
		for (BossDefinition.InventoryRule rule : rules)
		{
			if (rule.isFill())
			{
				fill = rule;
				continue;
			}
			applyRule(b, rule, strategy, owned, assumptions, prefs, prices, trip);
		}

		// Runes for an autocast spell chosen by the optimiser.
		if (result != null)
		{
			for (OptimisedSetup setup : result.getSetups().values())
			{
				SpellDef spell = setup.getSpell();
				if (spell != null)
				{
					addRunes(b, spell.getName(), AUTOCAST_CASTS[trip.ordinal()], owned, prefs, false,
						"Runes to autocast " + spell.getName());
				}
			}
		}

		if (fill != null || rules.isEmpty())
		{
			fillFood(b, owned, prefs, prices, fill == null ? "Remaining slots" : fill.getReason());
		}

		b.notes.add("Quantities are checked against your last bank snapshot plus what you are carrying and wearing.");
		b.notes.add("The plugin does not estimate kills per trip; adjust food and prayer to taste and lock the slots you change.");
		if (!owned.hasBankData())
		{
			b.notes.add(0, "Bank not read yet: everything is checked against carried items only. Open your bank to refresh.");
		}
		return InventoryPlan.builder().slots(b.slots).missing(b.missing).notes(b.notes).build();
	}

	// ------------------------------------------------------------------ rules

	private void applyRule(Builder b, BossDefinition.InventoryRule rule, BossDefinition.Strategy strategy, OwnedItems owned,
		CombatAssumptions assumptions, Preferences prefs, PriceSource prices, TripLength trip)
	{
		int slots = rule.slotsFor(trip.name());
		String category = rule.getCategory();
		if ("PRAYER".equals(category) && prefs.isMorePrayer())
		{
			slots += 2;
		}
		if (prefs.isMaxFood() && !rule.isMandatory())
		{
			if ("BOSS_TELEPORT".equals(category) || "STAMINA".equals(category))
			{
				return;
			}
			slots = Math.min(slots, "PRAYER".equals(category) ? Math.max(1, slots - 1) : 1);
		}
		if (slots <= 0)
		{
			return;
		}

		if ("RUNE_POUCH".equals(category))
		{
			addRunes(b, rule.getSpell(), Math.max(1, rule.getCasts()), owned, prefs, rule.isMandatory(), rule.getReason());
			return;
		}

		List<Family> options = new ArrayList<>();
		if (rule.getFamily() != null)
		{
			Family f = supplies.family(rule.getFamily());
			if (f != null)
			{
				options.add(f);
			}
		}
		else if ("BOOST".equals(category))
		{
			CombatClass cc = rule.getCombatClass() != null ? rule.getCombatClass() : strategy.primaryClass();
			Potion assumed = assumptions.potionFor(cc);
			if (assumed == Potion.NONE)
			{
				return;
			}
			// Supplies that give exactly the boost the DPS figures assume come first.
			for (Family f : supplies.byCategory("BOOST"))
			{
				if (f.getCombatClass() == cc && assumed.name().equals(f.getPotion()))
				{
					options.add(f);
				}
			}
			if (options.isEmpty())
			{
				b.notes.add("No supply is defined for the assumed boost '" + assumed.getDisplayName() + "'.");
				return;
			}
		}
		else
		{
			options.addAll(supplies.byCategory(category));
		}
		options.removeIf(f -> excluded(f, prefs));
		if (options.isEmpty())
		{
			return;
		}

		Family chosen = choose(b, options, slots, owned, prefs, prices);
		allocate(b, chosen, slots, owned, category, rule.isMandatory(), rule.getReason());
		if (chosen.getPairedWith() != null)
		{
			Family pair = supplies.family(chosen.getPairedWith());
			if (pair != null)
			{
				allocate(b, pair, slots, owned, category, rule.isMandatory(), rule.getReason());
			}
		}
	}

	private static boolean excluded(Family f, Preferences prefs)
	{
		for (int id : f.getIds())
		{
			if (prefs.getExcludedItemIds().contains(id))
			{
				return true;
			}
		}
		return false;
	}

	/** Number of inventory slots' worth of a family the account can still supply. */
	private static int units(Builder b, Family f, OwnedItems owned)
	{
		int total = 0;
		for (int id : f.getIds())
		{
			total += Math.max(0, b.available(owned, id));
		}
		return total;
	}

	private Family choose(Builder b, List<Family> options, int slots, OwnedItems owned, Preferences prefs, PriceSource prices)
	{
		List<Family> enough = new ArrayList<>();
		Family most = options.get(0);
		for (Family f : options)
		{
			int have = units(b, f, owned);
			if (have >= slots || (f.isStackable() && have > 0))
			{
				enough.add(f);
			}
			if (have > units(b, most, owned))
			{
				most = f;
			}
		}
		if (enough.isEmpty())
		{
			return most;
		}
		if (prefs.isMinCost() && prices != null)
		{
			Family cheapest = null;
			double cheapestCost = Double.MAX_VALUE;
			for (Family f : enough)
			{
				long price = prices.price(f.bestId());
				if (price <= 0)
				{
					continue;
				}
				double cost = f.getHeals() > 0 ? (double) price / f.getHeals() : price;
				if (cost < cheapestCost)
				{
					cheapestCost = cost;
					cheapest = f;
				}
			}
			if (cheapest != null)
			{
				return cheapest;
			}
		}
		return enough.get(0);
	}

	private void allocate(Builder b, Family f, int slots, OwnedItems owned, String category, boolean mandatory, String reason)
	{
		if (f.isStackable())
		{
			int id = f.bestId();
			int have = Math.max(0, b.available(owned, id));
			b.add(id, f.getName(), Math.max(1, Math.min(have, 1)), category, have >= 1, false, reason);
			b.reserve(id, 1);
			if (have < 1)
			{
				b.missing.add(new Missing(id, f.getName(), 1, 0, mandatory, reason));
			}
			return;
		}
		int placed = 0;
		// Highest dose first.
		for (int i = f.getIds().length - 1; i >= 0 && placed < slots; i--)
		{
			int id = f.getIds()[i];
			int have = Math.max(0, b.available(owned, id));
			int take = Math.min(have, slots - placed);
			for (int n = 0; n < take; n++)
			{
				b.add(id, f.isDosed() ? f.nameForDose(i + 1) : f.getName(), 1, category, true, false, reason);
			}
			b.reserve(id, take);
			placed += take;
		}
		int shortfall = slots - placed;
		if (shortfall > 0)
		{
			int id = f.bestId();
			String name = f.isDosed() ? f.nameForDose(f.getIds().length) : f.getName();
			for (int n = 0; n < shortfall; n++)
			{
				b.add(id, name, 1, category, false, false, reason);
			}
			b.missing.add(new Missing(id, name, slots, placed, mandatory, reason));
		}
	}

	// ------------------------------------------------------------------ runes

	private void addRunes(Builder b, String spellName, int casts, OwnedItems owned, Preferences prefs, boolean mandatory, String reason)
	{
		Map<String, Integer> runes = spellName == null ? null : supplies.runesFor(spellName);
		if (runes == null)
		{
			b.notes.add("Rune costs for " + spellName + " are not in the plugin's dataset; add the runes yourself.");
			return;
		}
		Family pouch = null;
		for (Family f : supplies.byCategory("RUNE_POUCH"))
		{
			if (b.available(owned, f.bestId()) > 0 && !excluded(f, prefs))
			{
				pouch = f;
				break;
			}
		}
		int pouchCapacity = pouch == null ? 0 : "divine_rune_pouch".equals(pouch.getKey()) ? 4 : 3;
		StringBuilder contents = new StringBuilder();
		List<String> loose = new ArrayList<>();
		int inPouch = 0;
		for (Map.Entry<String, Integer> e : runes.entrySet())
		{
			Family rune = supplies.family(e.getKey());
			if (rune == null)
			{
				continue;
			}
			int needed = e.getValue() * casts;
			int id = rune.bestId();
			int have = Math.max(0, b.available(owned, id));
			b.reserve(id, Math.min(have, needed));
			if (have < needed)
			{
				b.missing.add(new Missing(id, rune.getName(), needed, have, mandatory, reason));
			}
			if (inPouch < pouchCapacity)
			{
				inPouch++;
				contents.append(contents.length() == 0 ? "" : ", ").append(needed).append(" x ").append(rune.getName());
			}
			else
			{
				loose.add(e.getKey());
				b.add(id, rune.getName(), needed, "RUNE", have >= needed, false, reason);
			}
		}
		if (pouch != null)
		{
			b.add(pouch.bestId(), pouch.getName(), 1, "RUNE_POUCH", true, false, reason + " (" + contents + ")");
			b.reserve(pouch.bestId(), 1);
			b.notes.add(pouch.getName() + " should hold: " + contents + " (" + casts + " casts of " + spellName + ").");
		}
		else
		{
			b.notes.add("No rune pouch found, so runes for " + spellName + " take one slot per rune type.");
		}
	}

	// ------------------------------------------------------------------ food

	private void fillFood(Builder b, OwnedItems owned, Preferences prefs, PriceSource prices, String reason)
	{
		List<Family> foods = new ArrayList<>(supplies.byCategory("FOOD"));
		foods.removeIf(f -> excluded(f, prefs));
		if (foods.isEmpty())
		{
			return;
		}
		if (prefs.isMinCost() && prices != null)
		{
			foods.sort((x, y) -> Double.compare(costPerHeal(x, prices), costPerHeal(y, prices)));
		}
		int wanted = b.free();
		// If the bank runs out, ask for more of the best food the player already uses rather than a different one.
		Family preferred = foods.get(0);
		for (Family f : foods)
		{
			if (b.available(owned, f.bestId()) > 0)
			{
				preferred = f;
				break;
			}
		}
		for (Family f : foods)
		{
			if (b.free() <= 0)
			{
				break;
			}
			int id = f.bestId();
			int take = Math.min(Math.max(0, b.available(owned, id)), b.free());
			for (int n = 0; n < take; n++)
			{
				b.add(id, f.getName(), 1, "FOOD", true, false, reason);
			}
			b.reserve(id, take);
		}
		int shortfall = b.free();
		if (shortfall > 0)
		{
			for (int n = 0; n < shortfall; n++)
			{
				b.add(preferred.bestId(), preferred.getName(), 1, "FOOD", false, false, reason);
			}
			b.missing.add(new Missing(preferred.bestId(), preferred.getName() + " (or other food)", wanted, wanted - shortfall, false,
				"Not enough food in the bank to fill the inventory"));
		}
	}

	private static double costPerHeal(Family f, PriceSource prices)
	{
		long price = prices.price(f.bestId());
		return price <= 0 || f.getHeals() <= 0 ? Double.MAX_VALUE : (double) price / f.getHeals();
	}
}
