package com.bossoptimiser.optimiser;

import com.bossoptimiser.account.AccountSnapshot;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.data.ItemSlot;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/** Everything one optimisation run depends on. Identical requests give identical results. */
@Value
@Builder(toBuilder = true)
public class OptimiserRequest
{
	public static final int DEFAULT_MAX_SWITCHES = 8;
	public static final int LOW_SWITCH_MAX_SWITCHES = 4;

	BossDefinition boss;
	String variantId;
	String strategyId;
	@Builder.Default
	Priority priority = Priority.MAX_DPS;
	OwnedItems owned;
	AccountSnapshot account;
	CombatAssumptions assumptions;

	/** Canonical item ids the player never wants recommended. */
	@Builder.Default
	Set<Integer> excludedItemIds = Collections.emptySet();
	/** Slots pinned to a specific item id. */
	@Builder.Default
	Map<ItemSlot, Integer> lockedSlots = Collections.emptyMap();
	/** Items the player confirmed they can equip although the plugin has no requirement data. */
	@Builder.Default
	Set<Integer> confirmedItemIds = Collections.emptySet();
	/** When false, items with unknown requirements are left out entirely. */
	@Builder.Default
	boolean allowUnknownRequirements = true;
	@Builder.Default
	int maxSwitches = DEFAULT_MAX_SWITCHES;
	/** Optional: needed only for the Budget priority. */
	PriceSource prices;

	public int effectiveMaxSwitches()
	{
		return priority == Priority.LOW_SWITCH ? Math.min(maxSwitches, LOW_SWITCH_MAX_SWITCHES) : maxSwitches;
	}
}
