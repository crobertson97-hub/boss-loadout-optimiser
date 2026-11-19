package com.bossoptimiser.data;

import lombok.Value;

/** A combat spell. {@link #element} is air/water/earth/fire or null. */
@Value
public class SpellDef
{
	String name;
	int maxHit;
	/** standard, ancient or arceuus. */
	String spellbook;
	String element;

	/** Second word of an elemental spell name: Strike, Bolt, Blast, Wave or Surge. */
	public String spellClass()
	{
		int i = name.indexOf(' ');
		return i < 0 ? "" : name.substring(i + 1);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
