package com.bossoptimiser.combat;

import com.bossoptimiser.data.CombatClass;

/**
 * Stat-boosting consumables. Boost = floor(flat + level * percent / 100).
 * The figures are the standard published boost formulas.
 */
public enum Potion
{
	NONE("None", null, 0, 0, false, false, false, false, false),
	ATTACK_STRENGTH("Attack + strength potions", CombatClass.MELEE, 3, 10, true, true, false, false, false),
	SUPER_ATTACK_STRENGTH("Super attack + super strength", CombatClass.MELEE, 5, 15, true, true, false, false, false),
	SUPER_COMBAT("Super combat", CombatClass.MELEE, 5, 15, true, true, true, false, false),
	RANGING("Ranging potion", CombatClass.RANGED, 4, 10, false, false, false, true, false),
	BASTION("Bastion potion", CombatClass.RANGED, 4, 10, false, false, false, true, false),
	MAGIC("Magic potion", CombatClass.MAGIC, 4, 0, false, false, false, false, true),
	BATTLEMAGE("Battlemage potion", CombatClass.MAGIC, 4, 0, false, false, false, false, true),
	IMBUED_HEART("Imbued heart", CombatClass.MAGIC, 1, 10, false, false, false, false, true),
	SATURATED_HEART("Saturated heart", CombatClass.MAGIC, 4, 10, false, false, false, false, true);

	private final String displayName;
	private final CombatClass combatClass;
	private final int flat;
	private final int percent;
	private final boolean attack;
	private final boolean strength;
	private final boolean defence;
	private final boolean ranged;
	private final boolean magic;

	Potion(String displayName, CombatClass combatClass, int flat, int percent,
		boolean attack, boolean strength, boolean defence, boolean ranged, boolean magic)
	{
		this.displayName = displayName;
		this.combatClass = combatClass;
		this.flat = flat;
		this.percent = percent;
		this.attack = attack;
		this.strength = strength;
		this.defence = defence;
		this.ranged = ranged;
		this.magic = magic;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public CombatClass getCombatClass()
	{
		return combatClass;
	}

	private int boost(int level)
	{
		return flat + level * percent / 100;
	}

	public int attackBoost(int level)
	{
		return attack ? boost(level) : 0;
	}

	public int strengthBoost(int level)
	{
		return strength ? boost(level) : 0;
	}

	public int defenceBoost(int level)
	{
		if (this == BASTION || this == BATTLEMAGE)
		{
			return 5 + level * 15 / 100;
		}
		return defence ? boost(level) : 0;
	}

	public int rangedBoost(int level)
	{
		return ranged ? boost(level) : 0;
	}

	public int magicBoost(int level)
	{
		return magic ? boost(level) : 0;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
