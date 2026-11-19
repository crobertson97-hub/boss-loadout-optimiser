package com.bossoptimiser.optimiser;

/**
 * Item prices. In the client this is backed by RuneLite's own price cache (ItemManager); no
 * request is made by the plugin and no account data leaves the client.
 */
public interface PriceSource
{
	PriceSource NONE = itemId -> 0;

	/** @return the approximate Grand Exchange price in gp, or 0 when no price is available. */
	long price(int itemId);

	default String describe()
	{
		return "unavailable";
	}
}
