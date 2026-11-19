package com.bossoptimiser.combat;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Outcome of one DPS calculation, with enough intermediate values to explain and verify it. */
@Value
@Builder
public class DpsResult
{
	public static final double SECONDS_PER_TICK = 0.6;

	int maxAttackRoll;
	int defenceRoll;
	/** Probability that an attack passes the accuracy roll (0..1). */
	double accuracy;
	/** Largest total damage one attack can deal (all hitsplats). */
	int maxHit;
	/** Expected damage of one attack. */
	double expectedHit;
	/** Attack interval in game ticks. */
	int attackSpeed;
	double dps;

	/** False when a mechanic that affects this result is not modelled; see {@link #notes}. */
	boolean exact;
	@Builder.Default
	List<String> notes = Collections.emptyList();

	public double secondsPerAttack()
	{
		return attackSpeed * SECONDS_PER_TICK;
	}

	public static DpsResult zero(String note)
	{
		return builder().exact(true).notes(Collections.singletonList(note)).attackSpeed(4).build();
	}
}
