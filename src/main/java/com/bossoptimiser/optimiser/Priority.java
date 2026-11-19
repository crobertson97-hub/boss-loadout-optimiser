package com.bossoptimiser.optimiser;

/** What the optimiser ranks complete setups by. The scoring model is documented in docs/architecture.md. */
public enum Priority
{
	MAX_DPS("Maximum DPS", "Highest expected damage per second. Defence only breaks ties."),
	BALANCED("Balanced", "75% damage, 20% defence against this boss's attack styles, 5% prayer bonus."),
	SURVIVABILITY("Survivability", "Best defence and prayer bonus among setups within 15% of your best DPS."),
	BUDGET("Budget", "Damage minus a penalty for ammunition, rune and charge costs per hour."),
	LOW_SWITCH("Low switch complexity", "Maximum DPS with at most 4 carried switch items for multi-style strategies.");

	private final String displayName;
	private final String description;

	Priority(String displayName, String description)
	{
		this.displayName = displayName;
		this.description = description;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getDescription()
	{
		return description;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
