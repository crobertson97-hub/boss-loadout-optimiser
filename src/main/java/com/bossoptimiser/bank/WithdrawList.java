package com.bossoptimiser.bank;

import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.optimiser.OptimisedSetup;
import com.bossoptimiser.optimiser.OptimiserResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Everything the selected setup needs, with how much of it is already worn or carried. The bank
 * overlay uses this to highlight items; it never moves, withdraws or re-orders anything.
 */
public final class WithdrawList
{
	public enum Kind
	{
		EQUIPMENT, SWITCH, SUPPLY
	}

	@Value
	public static class Entry
	{
		/** Canonical item id (cosmetic variants are folded into their base item). */
		int itemId;
		String name;
		Kind kind;
		int needed;
		/** Worn + in inventory. */
		int carried;
		int inBank;

		public boolean isWithdrawn()
		{
			return carried >= needed;
		}

		public boolean isMissing()
		{
			return carried + inBank < needed;
		}

		public int stillToWithdraw()
		{
			return Math.max(0, Math.min(needed - carried, inBank));
		}
	}

	public static final WithdrawList EMPTY = new WithdrawList(Collections.emptyMap());

	private final Map<Integer, Entry> entries;

	private WithdrawList(Map<Integer, Entry> entries)
	{
		this.entries = entries;
	}

	public static WithdrawList build(GameData data, OptimiserResult result, InventoryPlan plan, OwnedItems owned)
	{
		// Requirement totals first, so an item needed twice (e.g. several food slots) is summed.
		Map<Integer, Integer> needed = new LinkedHashMap<>();
		Map<Integer, String> names = new LinkedHashMap<>();
		Map<Integer, Kind> kinds = new LinkedHashMap<>();
		if (result != null)
		{
			boolean primary = true;
			for (OptimisedSetup setup : result.getSetups().values())
			{
				for (ItemDef item : setup.getLoadout().asMap().values())
				{
					int id = data.canonicalId(item.getId());
					if (!needed.containsKey(id))
					{
						needed.put(id, 1);
						names.put(id, item.displayName());
						kinds.put(id, primary ? Kind.EQUIPMENT : Kind.SWITCH);
					}
				}
				primary = false;
			}
		}
		if (plan != null)
		{
			for (InventoryPlan.Slot slot : plan.getSlots())
			{
				if ("SWITCH".equals(slot.getCategory()))
				{
					continue;
				}
				int id = data.canonicalId(slot.getItemId());
				needed.merge(id, Math.max(1, slot.getQuantity()), Integer::sum);
				names.putIfAbsent(id, slot.getName());
				kinds.putIfAbsent(id, Kind.SUPPLY);
			}
		}

		// Owned quantities, folded onto canonical ids.
		Map<Integer, Integer> carried = new LinkedHashMap<>();
		Map<Integer, Integer> banked = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : owned.getInventory().entrySet())
		{
			carried.merge(data.canonicalId(e.getKey()), e.getValue(), Integer::sum);
		}
		for (Map.Entry<Integer, Integer> e : owned.getEquipment().entrySet())
		{
			carried.merge(data.canonicalId(e.getKey()), e.getValue(), Integer::sum);
		}
		for (Map.Entry<Integer, Integer> e : owned.getBank().entrySet())
		{
			banked.merge(data.canonicalId(e.getKey()), e.getValue(), Integer::sum);
		}

		Map<Integer, Entry> entries = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : needed.entrySet())
		{
			int id = e.getKey();
			entries.put(id, new Entry(id, names.get(id), kinds.get(id), e.getValue(),
				carried.getOrDefault(id, 0), banked.getOrDefault(id, 0)));
		}
		return new WithdrawList(entries);
	}

	/** @param canonicalItemId un-noted, non-placeholder id folded through {@link GameData#canonicalId} */
	public Entry get(int canonicalItemId)
	{
		return entries.get(canonicalItemId);
	}

	public List<Entry> entries()
	{
		return new ArrayList<>(entries.values());
	}

	public List<Entry> missing()
	{
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries.values())
		{
			if (e.isMissing())
			{
				out.add(e);
			}
		}
		return out;
	}

	public boolean isEmpty()
	{
		return entries.isEmpty();
	}
}
