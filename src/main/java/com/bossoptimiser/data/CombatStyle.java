package com.bossoptimiser.data;

import lombok.Value;

/**
 * One selectable attack style of a weapon category, e.g. {@code Lunge / stab / Controlled}.
 * Stances are kept as the strings used by the game data: Accurate, Aggressive, Controlled,
 * Defensive, Rapid, Longrange, Autocast, Defensive Autocast.
 */
@Value
public class CombatStyle
{
	public static final String ACCURATE = "Accurate";
	public static final String AGGRESSIVE = "Aggressive";
	public static final String CONTROLLED = "Controlled";
	public static final String DEFENSIVE = "Defensive";
	public static final String RAPID = "Rapid";
	public static final String LONGRANGE = "Longrange";
	public static final String AUTOCAST = "Autocast";
	public static final String DEFENSIVE_AUTOCAST = "Defensive Autocast";

	String name;
	AttackType type;
	String stance;

	public boolean isCast()
	{
		return AUTOCAST.equals(stance) || DEFENSIVE_AUTOCAST.equals(stance);
	}

	/** Styles that only train Defence / add range are never the highest-damage choice. */
	public boolean isDefensive()
	{
		return DEFENSIVE.equals(stance) || LONGRANGE.equals(stance) || DEFENSIVE_AUTOCAST.equals(stance);
	}

	public String describe()
	{
		return name + " (" + type.name().toLowerCase() + ", " + stance.toLowerCase() + ")";
	}
}
