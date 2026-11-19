package com.bossoptimiser.data;

/** The three combat classes of the combat triangle. */
public enum CombatClass
{
	MELEE("Melee"), RANGED("Ranged"), MAGIC("Magic");

	private final String displayName;

	CombatClass(String displayName)
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
