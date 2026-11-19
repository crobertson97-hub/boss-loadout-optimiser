package com.bossoptimiser.combat;

import com.bossoptimiser.data.CombatClass;

/**
 * Offensive prayers. Accuracy and strength are percentages applied to the visible level;
 * {@link #magicDamage} is added to the magic damage bonus in tenths of a percent.
 * {@link #drainRate} is the prayer drain rate used for the prayer-point estimate.
 */
public enum Prayer
{
	BURST_OF_STRENGTH("Burst of Strength", CombatClass.MELEE, 4, 0, 105, 0, 1),
	CLARITY_OF_THOUGHT("Clarity of Thought", CombatClass.MELEE, 7, 105, 0, 0, 1),
	SUPERHUMAN_STRENGTH("Superhuman Strength", CombatClass.MELEE, 13, 0, 110, 0, 6),
	IMPROVED_REFLEXES("Improved Reflexes", CombatClass.MELEE, 16, 110, 0, 0, 6),
	ULTIMATE_STRENGTH("Ultimate Strength", CombatClass.MELEE, 31, 0, 115, 0, 12),
	INCREDIBLE_REFLEXES("Incredible Reflexes", CombatClass.MELEE, 34, 115, 0, 0, 12),
	CHIVALRY("Chivalry", CombatClass.MELEE, 60, 115, 118, 0, 24),
	PIETY("Piety", CombatClass.MELEE, 70, 120, 123, 0, 24),

	SHARP_EYE("Sharp Eye", CombatClass.RANGED, 8, 105, 105, 0, 1),
	HAWK_EYE("Hawk Eye", CombatClass.RANGED, 26, 110, 110, 0, 6),
	EAGLE_EYE("Eagle Eye", CombatClass.RANGED, 44, 115, 115, 0, 12),
	DEADEYE("Deadeye", CombatClass.RANGED, 62, 118, 118, 0, 12),
	RIGOUR("Rigour", CombatClass.RANGED, 74, 120, 123, 0, 24),

	MYSTIC_WILL("Mystic Will", CombatClass.MAGIC, 9, 105, 0, 0, 1),
	MYSTIC_LORE("Mystic Lore", CombatClass.MAGIC, 27, 110, 0, 10, 6),
	MYSTIC_MIGHT("Mystic Might", CombatClass.MAGIC, 45, 115, 0, 20, 12),
	MYSTIC_VIGOUR("Mystic Vigour", CombatClass.MAGIC, 63, 118, 0, 30, 12),
	AUGURY("Augury", CombatClass.MAGIC, 77, 125, 0, 40, 24);

	private final String displayName;
	private final CombatClass combatClass;
	private final int level;
	private final int accuracy;
	private final int strength;
	private final int magicDamage;
	private final int drainRate;

	Prayer(String displayName, CombatClass combatClass, int level, int accuracy, int strength, int magicDamage, int drainRate)
	{
		this.displayName = displayName;
		this.combatClass = combatClass;
		this.level = level;
		this.accuracy = accuracy;
		this.strength = strength;
		this.magicDamage = magicDamage;
		this.drainRate = drainRate;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public CombatClass getCombatClass()
	{
		return combatClass;
	}

	/** Prayer level needed to activate. Unlock requirements (scrolls, Knight Waves) are not known to the plugin. */
	public int getLevel()
	{
		return level;
	}

	public int getAccuracy()
	{
		return accuracy;
	}

	public int getStrength()
	{
		return strength;
	}

	public int getMagicDamage()
	{
		return magicDamage;
	}

	public int getDrainRate()
	{
		return drainRate;
	}

	/** Prayers gated behind an unlock the client cannot always see (scrolls / Camelot training room). */
	public boolean needsUnlock()
	{
		return this == CHIVALRY || this == PIETY || this == RIGOUR || this == AUGURY
			|| this == DEADEYE || this == MYSTIC_VIGOUR;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
