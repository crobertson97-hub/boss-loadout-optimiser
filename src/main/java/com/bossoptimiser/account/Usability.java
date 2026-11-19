package com.bossoptimiser.account;

import lombok.Value;

/** Whether the current account can equip an item, and why. */
@Value
public class Usability
{
	public enum State
	{
		/** Requirements are known and met, the item is currently worn, or the player confirmed it. */
		USABLE,
		/** A known requirement is not met. */
		UNUSABLE,
		/** The plugin has no verified requirement data for this item. */
		UNKNOWN
	}

	State state;
	String reason;

	public static Usability usable(String reason)
	{
		return new Usability(State.USABLE, reason);
	}

	public static Usability unusable(String reason)
	{
		return new Usability(State.UNUSABLE, reason);
	}

	public static Usability unknown(String reason)
	{
		return new Usability(State.UNKNOWN, reason);
	}

	public boolean isUsable()
	{
		return state == State.USABLE;
	}

	public boolean isUnknown()
	{
		return state == State.UNKNOWN;
	}
}
