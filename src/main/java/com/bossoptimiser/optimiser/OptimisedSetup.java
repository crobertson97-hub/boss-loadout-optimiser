package com.bossoptimiser.optimiser;

import com.bossoptimiser.combat.DpsResult;
import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.SpellDef;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/** One complete, evaluated equipment setup for one combat class. */
@Value
@Builder(toBuilder = true)
public class OptimisedSetup
{
	CombatClass combatClass;
	Loadout loadout;
	CombatStyle style;
	SpellDef spell;
	ItemDef blowpipeDart;

	/** phase id -> result, for the phases this combat class is used against. */
	Map<String, DpsResult> phaseResults;
	/** DPS averaged over this class's phases, weighted by phase share. */
	double dps;
	/** Heuristic: equipment defence bonuses weighted by the boss's attack styles. Not a survival probability. */
	double defenceScore;
	int prayerBonus;
	/** Estimated running cost in gp/hour; negative when no price data was available. */
	double costPerHour;
	boolean costComplete;
	double score;
	/** False when any phase result relies on an unmodelled mechanic or unverified requirement. */
	boolean exact;
	@Builder.Default
	List<String> notes = Collections.emptyList();
	/** Item ids in this loadout whose equip requirements are unverified. */
	@Builder.Default
	List<Integer> unverifiedItemIds = Collections.emptyList();

	public DpsResult headlineResult()
	{
		DpsResult best = null;
		for (DpsResult r : phaseResults.values())
		{
			if (best == null || r.getDps() > best.getDps())
			{
				best = r;
			}
		}
		return best;
	}
}
