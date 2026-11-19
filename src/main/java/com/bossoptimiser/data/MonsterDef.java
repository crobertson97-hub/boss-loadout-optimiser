package com.bossoptimiser.data;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Combat statistics of one monster version, as published by the OSRS Wiki. */
@Value
@Builder(toBuilder = true)
public class MonsterDef
{
	int id;
	String name;
	String version;
	int combatLevel;
	int size;
	/** Monster attack speed in ticks. */
	int speed;
	/** Attack styles the monster uses, e.g. Slash, Magic, Ranged, Dragonfire. */
	List<String> attackStyles;
	/** Free-text max hit as published (may list several styles). */
	String maxHitText;

	int attackLevel;
	int strengthLevel;
	int defenceLevel;
	int magicLevel;
	int rangedLevel;
	int hitpoints;

	int offAttack;
	int offStrength;
	int offMagic;
	int offMagicStrength;
	int offRanged;
	int offRangedStrength;

	int defStab;
	int defSlash;
	int defCrush;
	int defMagic;
	int defLight;
	int defStandard;
	int defHeavy;
	int flatArmour;

	List<String> attributes;
	boolean slayerMonster;
	/** Elemental weakness element (air/water/earth/fire) or null. */
	String weaknessElement;
	int weaknessSeverity;

	public boolean hasAttribute(String attribute)
	{
		return attributes.contains(attribute);
	}

	public boolean isDragon()
	{
		return hasAttribute("dragon");
	}

	public boolean isUndead()
	{
		return hasAttribute("undead");
	}

	public boolean isDemon()
	{
		return hasAttribute("demon");
	}

	public String displayName()
	{
		return version.isEmpty() ? name : name + " (" + version + ")";
	}
}
