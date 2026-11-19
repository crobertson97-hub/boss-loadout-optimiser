package com.bossoptimiser.combat;

import lombok.Builder;
import lombok.Value;

/** Real (unboosted) skill levels relevant to combat and equipment requirements. */
@Value
@Builder(toBuilder = true)
public class PlayerLevels
{
	@Builder.Default
	int attack = 1;
	@Builder.Default
	int strength = 1;
	@Builder.Default
	int defence = 1;
	@Builder.Default
	int ranged = 1;
	@Builder.Default
	int magic = 1;
	@Builder.Default
	int prayer = 1;
	@Builder.Default
	int hitpoints = 10;
	@Builder.Default
	int slayer = 1;
	@Builder.Default
	int agility = 1;
	@Builder.Default
	int mining = 1;

	public static PlayerLevels maxed()
	{
		return builder().attack(99).strength(99).defence(99).ranged(99).magic(99).prayer(99)
			.hitpoints(99).slayer(99).agility(99).mining(99).build();
	}

	public int get(String skill)
	{
		switch (skill)
		{
			case "attack":
				return attack;
			case "strength":
				return strength;
			case "defence":
				return defence;
			case "ranged":
				return ranged;
			case "magic":
				return magic;
			case "prayer":
				return prayer;
			case "hitpoints":
				return hitpoints;
			case "slayer":
				return slayer;
			case "agility":
				return agility;
			case "mining":
				return mining;
			default:
				return -1;
		}
	}

	public int combatLevel()
	{
		double base = 0.25 * (defence + hitpoints + prayer / 2);
		double melee = 0.325 * (attack + strength);
		double range = 0.325 * (ranged / 2 + ranged);
		double mage = 0.325 * (magic / 2 + magic);
		return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
	}
}
