package com.bossoptimiser;

import com.bossoptimiser.optimiser.PriceSource;
import com.bossoptimiser.util.KeyValueStore;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Stand-in for the RuneLite client: in-memory config (one account store per account key),
 * synchronous "UI thread", a controllable clock and optional fake prices.
 */
public class FakeEnvironment implements BossOptimiserService.Environment
{
	public final KeyValueStore.InMemory shared = new KeyValueStore.InMemory();
	public final KeyValueStore.InMemory groundMarkers = new KeyValueStore.InMemory();
	public final Map<String, KeyValueStore.InMemory> accounts = new HashMap<>();
	public String currentAccount;
	public long now = Fixtures.NOW;
	public PriceSource prices;
	public boolean groundMarkerInstall = true;
	/** What the last "Show in bank" asked the client to show. */
	public String bankViewTitle;
	public java.util.Set<Integer> bankViewItems;

	@Override
	public String showInBank(String title, java.util.Set<Integer> canonicalItemIds)
	{
		bankViewTitle = title;
		bankViewItems = canonicalItemIds;
		return null;
	}

	private final KeyValueStore account = new KeyValueStore()
	{
		private KeyValueStore.InMemory store()
		{
			return currentAccount == null ? null : accounts.computeIfAbsent(currentAccount, k -> new KeyValueStore.InMemory());
		}

		@Override
		public String get(String key)
		{
			return store() == null ? null : store().get(key);
		}

		@Override
		public void put(String key, String value)
		{
			if (store() != null)
			{
				store().put(key, value);
			}
		}

		@Override
		public void remove(String key)
		{
			if (store() != null)
			{
				store().remove(key);
			}
		}
	};

	@Override
	public KeyValueStore sharedStore()
	{
		return shared;
	}

	@Override
	public KeyValueStore accountStore()
	{
		return account;
	}

	@Override
	public KeyValueStore groundMarkerStore()
	{
		return groundMarkers;
	}

	@Override
	public PriceSource prices()
	{
		return prices;
	}

	@Override
	public BufferedImage itemImage(int itemId, int quantity)
	{
		return null;
	}

	@Override
	public boolean groundMarkerInstallEnabled()
	{
		return groundMarkerInstall;
	}

	@Override
	public void runOnUi(Runnable task)
	{
		task.run();
	}

	@Override
	public long now()
	{
		return now;
	}
}
