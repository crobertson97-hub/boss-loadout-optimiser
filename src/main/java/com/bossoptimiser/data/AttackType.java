package com.bossoptimiser.data;

import java.util.Locale;

/** The damage type an attack style rolls against. */
public enum AttackType
{
	STAB, SLASH, CRUSH, RANGED, MAGIC;

	public boolean isMelee()
	{
		return this == STAB || this == SLASH || this == CRUSH;
	}

	public CombatClass combatClass()
	{
		if (isMelee())
		{
			return CombatClass.MELEE;
		}
		return this == RANGED ? CombatClass.RANGED : CombatClass.MAGIC;
	}

	public static AttackType fromKey(String key)
	{
		return valueOf(key.toUpperCase(Locale.ROOT));
	}
}
