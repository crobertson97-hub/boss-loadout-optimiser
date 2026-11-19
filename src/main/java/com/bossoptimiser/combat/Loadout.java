package com.bossoptimiser.combat;

import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** An immutable set of worn equipment, at most one item per slot. */
public final class Loadout
{
	public static final Loadout EMPTY = new Loadout(new EnumMap<>(ItemSlot.class));

	private final EnumMap<ItemSlot, ItemDef> items;

	private Loadout(EnumMap<ItemSlot, ItemDef> items)
	{
		this.items = items;
	}

	public static Loadout of(ItemDef... defs)
	{
		EnumMap<ItemSlot, ItemDef> map = new EnumMap<>(ItemSlot.class);
		for (ItemDef d : defs)
		{
			if (d != null)
			{
				map.put(d.getSlot(), d);
			}
		}
		return new Loadout(map);
	}

	public static Loadout of(Map<ItemSlot, ItemDef> source)
	{
		EnumMap<ItemSlot, ItemDef> map = new EnumMap<>(ItemSlot.class);
		for (Map.Entry<ItemSlot, ItemDef> e : source.entrySet())
		{
			if (e.getValue() != null)
			{
				map.put(e.getKey(), e.getValue());
			}
		}
		return new Loadout(map);
	}

	public ItemDef get(ItemSlot slot)
	{
		return items.get(slot);
	}

	public ItemDef weapon()
	{
		return items.get(ItemSlot.WEAPON);
	}

	public Map<ItemSlot, ItemDef> asMap()
	{
		return Collections.unmodifiableMap(items);
	}

	public Loadout with(ItemSlot slot, ItemDef item)
	{
		EnumMap<ItemSlot, ItemDef> map = new EnumMap<>(items);
		if (item == null)
		{
			map.remove(slot);
		}
		else
		{
			map.put(slot, item);
		}
		return new Loadout(map);
	}

	public boolean wearing(String name)
	{
		for (ItemDef d : items.values())
		{
			if (d.getName().equals(name))
			{
				return true;
			}
		}
		return false;
	}

	public boolean wearingAny(String... names)
	{
		for (String n : names)
		{
			if (wearing(n))
			{
				return true;
			}
		}
		return false;
	}

	public boolean wearingAll(String... names)
	{
		for (String n : names)
		{
			if (!wearing(n))
			{
				return false;
			}
		}
		return true;
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}

	public int size()
	{
		return items.size();
	}

	/** Number of slots whose item differs between the two loadouts (an empty slot counts as different). */
	public int differingSlots(Loadout other)
	{
		int n = 0;
		for (ItemSlot s : ItemSlot.values())
		{
			ItemDef a = items.get(s);
			ItemDef b = other.items.get(s);
			if (a != null && (b == null || a.getId() != b.getId()))
			{
				n++;
			}
		}
		return n;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Loadout && items.equals(((Loadout) o).items);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(items);
	}

	@Override
	public String toString()
	{
		return items.values().toString();
	}
}
