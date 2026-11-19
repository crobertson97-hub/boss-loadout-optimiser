package com.bossoptimiser.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** A recommended 28-slot inventory plus the checklist of what the bank cannot supply. */
@Value
@Builder
public class InventoryPlan
{
	public static final int SIZE = 28;

	public enum TripLength
	{
		SHORT("Short trip"), NORMAL("Normal trip"), LONG("Long trip");

		private final String label;

		TripLength(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Value
	@Builder(toBuilder = true)
	public static class Preferences
	{
		@Builder.Default
		TripLength tripLength = TripLength.NORMAL;
		/** Trim optional supplies to their minimum so more slots hold food. */
		boolean maxFood;
		/** Two extra prayer-restoration slots. */
		boolean morePrayer;
		/** Prefer the cheapest owned option in each category (needs price data). */
		boolean minCost;
		/** Item ids that must never be recommended. */
		@Builder.Default
		java.util.Set<Integer> excludedItemIds = Collections.emptySet();
		/** Slots pinned by the player: these are kept and the rest is planned around them. */
		@Builder.Default
		List<Locked> locked = Collections.emptyList();
	}

	@Value
	public static class Locked
	{
		int itemId;
		String name;
		int quantity;
	}

	@Value
	@Builder
	public static class Slot
	{
		int index;
		int itemId;
		String name;
		int quantity;
		/** SWITCH, LOCKED, FOOD, PRAYER, BOOST, ... */
		String category;
		/** True when the account owns what this slot asks for. */
		boolean owned;
		boolean locked;
		String reason;
	}

	@Value
	public static class Missing
	{
		int itemId;
		String name;
		int needed;
		int owned;
		boolean mandatory;
		String reason;
	}

	@Builder.Default
	List<Slot> slots = new ArrayList<>();
	@Builder.Default
	List<Missing> missing = new ArrayList<>();
	@Builder.Default
	List<String> notes = new ArrayList<>();

	public int freeSlots()
	{
		return SIZE - slots.size();
	}
}
