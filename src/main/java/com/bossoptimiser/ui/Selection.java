package com.bossoptimiser.ui;

import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.optimiser.Priority;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/** Everything the player has chosen in the sidebar. Immutable; changed through {@code toBuilder()}. */
@Value
@Builder(toBuilder = true)
public class Selection
{
	String bossId;
	String variantId;
	String strategyId;
	@Builder.Default
	String groupSize = "SOLO";
	@Builder.Default
	Priority priority = Priority.MAX_DPS;
	/** Null until the account's levels are known; defaults are then derived from them. */
	CombatAssumptions assumptions;
	@Builder.Default
	boolean allowUnknownRequirements = true;
	@Builder.Default
	Set<Integer> excludedItemIds = Collections.emptySet();
	@Builder.Default
	Map<ItemSlot, Integer> lockedSlots = Collections.emptyMap();
	@Builder.Default
	Set<Integer> confirmedItemIds = Collections.emptySet();
	@Builder.Default
	InventoryPlan.Preferences inventoryPrefs = InventoryPlan.Preferences.builder().build();
	String tilePackId;
}
