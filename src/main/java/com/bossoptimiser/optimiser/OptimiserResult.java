package com.bossoptimiser.optimiser;

import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.ItemDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/** Result of one optimisation run. */
@Value
@Builder(toBuilder = true)
public class OptimiserResult
{
	OptimiserRequest request;
	/** Best setup per combat class the strategy uses; the first entry is the primary (worn) setup. */
	@Builder.Default
	Map<CombatClass, OptimisedSetup> setups = new LinkedHashMap<>();
	/** Other complete setups for the primary class, best first (different weapons / trade-offs). */
	@Builder.Default
	List<OptimisedSetup> alternatives = new ArrayList<>();
	/** Phase-weighted DPS over the whole strategy. */
	double totalDps;
	/** Items carried and swapped to for secondary combat classes. */
	@Builder.Default
	List<ItemDef> switchItems = new ArrayList<>();
	@Builder.Default
	List<String> warnings = new ArrayList<>();
	/** Owned equipment that was left out because a requirement is not met: "Item - reason". */
	@Builder.Default
	List<String> unusableOwned = new ArrayList<>();
	long evaluatedCombinations;
	/** True when candidate lists had to be trimmed to keep the search bounded. */
	boolean searchBounded;
	long elapsedMillis;

	public OptimisedSetup primary()
	{
		return setups.isEmpty() ? null : setups.values().iterator().next();
	}

	public boolean isEmpty()
	{
		return setups.isEmpty();
	}

	public static OptimiserResult empty(OptimiserRequest request, String warning)
	{
		return builder().request(request).warnings(new ArrayList<>(Collections.singletonList(warning))).build();
	}
}
