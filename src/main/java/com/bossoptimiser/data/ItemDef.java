package com.bossoptimiser.data;

import lombok.Builder;
import lombok.Value;

/**
 * Static combat statistics of one equipable item, as published by the OSRS Wiki.
 * Magic damage ({@link #mdmg}) is in tenths of a percent (an occult necklace is 50).
 */
@Value
@Builder(toBuilder = true)
public class ItemDef
{
	int id;
	String name;
	/** Wiki "version" of the item, e.g. Charged / Uncharged / Empty. Empty string when there is only one. */
	String version;
	ItemSlot slot;
	/** Weapon category (attack style set). Empty for non-weapons. */
	String category;
	/** Attack speed in game ticks; 0 for non-weapons. */
	int speed;
	boolean twoHanded;

	int astab;
	int aslash;
	int acrush;
	int amagic;
	int aranged;

	int dstab;
	int dslash;
	int dcrush;
	int dmagic;
	int dranged;

	int str;
	int rstr;
	int mdmg;
	int prayer;

	public int attackBonus(AttackType type)
	{
		switch (type)
		{
			case STAB:
				return astab;
			case SLASH:
				return aslash;
			case CRUSH:
				return acrush;
			case MAGIC:
				return amagic;
			default:
				return aranged;
		}
	}

	public boolean named(String other)
	{
		return name.equals(other);
	}

	public boolean nameContains(String fragment)
	{
		return name.contains(fragment);
	}

	/**
	 * True for versions of chargeable items that cannot be used in combat until recharged
	 * (an empty blowpipe, an uncharged trident, an inactive crystal bow...).
	 */
	public boolean isDepleted()
	{
		switch (version)
		{
			case "Uncharged":
			case "Empty":
			case "Inactive":
			case "Broken":
			case "Locked + Uncharged":
				return true;
			default:
				return false;
		}
	}

	public String displayName()
	{
		return version.isEmpty() || "Charged".equals(version) || "Normal".equals(version) || "Undamaged".equals(version)
			? name
			: name + " (" + version + ")";
	}

	@Override
	public String toString()
	{
		return displayName() + "#" + id;
	}
}
