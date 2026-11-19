package com.bossoptimiser.combat;

import com.bossoptimiser.data.CombatClass;
import java.util.EnumMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Everything the DPS engine cannot read from equipment: boosts, prayers and situational flags.
 * These are user-visible, saved with every Boss Profile and make results reproducible.
 */
@Value
@Builder(toBuilder = true)
public class CombatAssumptions
{
	@Builder.Default
	Potion meleePotion = Potion.SUPER_COMBAT;
	@Builder.Default
	Potion rangedPotion = Potion.RANGING;
	@Builder.Default
	Potion magicPotion = Potion.NONE;

	/** Null means "no offensive prayer". */
	Prayer meleePrayer;
	Prayer rangedPrayer;
	Prayer magicPrayer;

	/** Enables the Slayer helmet / black mask bonus against slayer monsters. */
	boolean onSlayerTask;
	/** Kandarin hard diary: +10% enchanted bolt proc chance. */
	boolean kandarinHardDiary;
	/** Item id of the dart loaded in the blowpipe, or 0 when unknown. */
	int blowpipeDartId;

	public Potion potionFor(CombatClass combatClass)
	{
		switch (combatClass)
		{
			case MELEE:
				return meleePotion == null ? Potion.NONE : meleePotion;
			case RANGED:
				return rangedPotion == null ? Potion.NONE : rangedPotion;
			default:
				return magicPotion == null ? Potion.NONE : magicPotion;
		}
	}

	public Prayer prayerFor(CombatClass combatClass)
	{
		switch (combatClass)
		{
			case MELEE:
				return meleePrayer;
			case RANGED:
				return rangedPrayer;
			default:
				return magicPrayer;
		}
	}

	/** Highest-level offensive prayer of each class the account's Prayer level allows. */
	public static Map<CombatClass, Prayer> bestPrayers(int prayerLevel, boolean assumeUnlocks)
	{
		Map<CombatClass, Prayer> best = new EnumMap<>(CombatClass.class);
		for (Prayer p : Prayer.values())
		{
			if (p.getLevel() > prayerLevel || (p.needsUnlock() && !assumeUnlocks))
			{
				continue;
			}
			// Single-stat melee prayers are skipped: they need pairing and the combined prayers dominate them.
			if (p.getCombatClass() == CombatClass.MELEE && (p.getAccuracy() == 0 || p.getStrength() == 0))
			{
				continue;
			}
			Prayer current = best.get(p.getCombatClass());
			if (current == null || p.getLevel() > current.getLevel())
			{
				best.put(p.getCombatClass(), p);
			}
		}
		return best;
	}

	public static CombatAssumptions defaultsFor(PlayerLevels levels)
	{
		Map<CombatClass, Prayer> best = bestPrayers(levels.getPrayer(), true);
		return builder()
			.meleePrayer(best.get(CombatClass.MELEE))
			.rangedPrayer(best.get(CombatClass.RANGED))
			.magicPrayer(best.get(CombatClass.MAGIC))
			.build();
	}
}
