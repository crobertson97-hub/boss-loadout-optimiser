package com.bossoptimiser.data;

import java.util.Locale;

/**
 * Equipment slots, in the order of the standard OSRS equipment interface.
 * {@link #containerIndex} is the index of the slot in the worn-items container
 * (it matches {@code net.runelite.api.EquipmentInventorySlot#getSlotIdx()}).
 */
public enum ItemSlot
{
	HEAD("Head", 0),
	CAPE("Cape", 1),
	NECK("Neck", 2),
	AMMO("Ammo", 13),
	WEAPON("Weapon", 3),
	BODY("Body", 4),
	SHIELD("Shield", 5),
	LEGS("Legs", 7),
	HANDS("Hands", 9),
	FEET("Feet", 10),
	RING("Ring", 12);

	private final String displayName;
	private final int containerIndex;

	ItemSlot(String displayName, int containerIndex)
	{
		this.displayName = displayName;
		this.containerIndex = containerIndex;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int getContainerIndex()
	{
		return containerIndex;
	}

	public String key()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	public static ItemSlot fromKey(String key)
	{
		return valueOf(key.toUpperCase(Locale.ROOT));
	}

	public static ItemSlot fromContainerIndex(int idx)
	{
		for (ItemSlot s : values())
		{
			if (s.containerIndex == idx)
			{
				return s;
			}
		}
		return null;
	}
}
