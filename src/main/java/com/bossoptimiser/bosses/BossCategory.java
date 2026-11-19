package com.bossoptimiser.bosses;

public enum BossCategory
{
	SLAYER("Slayer bosses"),
	WILDERNESS("Wilderness bosses"),
	GWD("God Wars Dungeon"),
	STANDARD("Standard PvM bosses"),
	RAID("Raid encounters"),
	MINIGAME("Minigame bosses"),
	OTHER("Other encounters");

	private final String displayName;

	BossCategory(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
