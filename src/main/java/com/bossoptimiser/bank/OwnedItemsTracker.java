package com.bossoptimiser.bank;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the latest contents of the bank, inventory and worn-equipment containers for one account
 * and produces immutable {@link OwnedItems} snapshots. Pure state; the RuneLite event handling
 * lives in the plugin class, which makes this class fully unit-testable.
 */
public final class OwnedItemsTracker
{
	public enum Container
	{
		BANK, INVENTORY, EQUIPMENT
	}

	private String accountKey;
	private Map<Integer, Integer> bank = Collections.emptyMap();
	private Map<Integer, Integer> inventory = Collections.emptyMap();
	private Map<Integer, Integer> equipment = Collections.emptyMap();
	private long bankCapturedAt;
	private long updatedAt;

	/**
	 * Switches to another account (or to "logged out" with a null key). Data belonging to the
	 * previous account is discarded so snapshots are never mixed.
	 *
	 * @return true when the account actually changed
	 */
	public synchronized boolean switchAccount(String newKey)
	{
		if (Objects.equals(accountKey, newKey))
		{
			return false;
		}
		accountKey = newKey;
		bank = Collections.emptyMap();
		inventory = Collections.emptyMap();
		equipment = Collections.emptyMap();
		bankCapturedAt = 0;
		updatedAt = 0;
		return true;
	}

	/** Restores a persisted bank snapshot for the current account. */
	public synchronized void restoreBank(Map<Integer, Integer> items, long capturedAt)
	{
		bank = clean(items);
		bankCapturedAt = capturedAt;
	}

	/**
	 * Replaces the contents of one container.
	 *
	 * @return true when the contents differ from what was stored before
	 */
	public synchronized boolean update(Container container, Map<Integer, Integer> items, long now)
	{
		Map<Integer, Integer> cleaned = clean(items);
		boolean changed;
		switch (container)
		{
			case BANK:
				changed = !cleaned.equals(bank) || bankCapturedAt == 0;
				bank = cleaned;
				bankCapturedAt = now;
				break;
			case INVENTORY:
				changed = !cleaned.equals(inventory);
				inventory = cleaned;
				break;
			default:
				changed = !cleaned.equals(equipment);
				equipment = cleaned;
				break;
		}
		if (changed)
		{
			updatedAt = now;
		}
		return changed;
	}

	private static Map<Integer, Integer> clean(Map<Integer, Integer> items)
	{
		Map<Integer, Integer> out = new HashMap<>();
		if (items != null)
		{
			for (Map.Entry<Integer, Integer> e : items.entrySet())
			{
				if (e.getKey() != null && e.getKey() > 0 && e.getValue() != null && e.getValue() > 0)
				{
					out.put(e.getKey(), e.getValue());
				}
			}
		}
		return out;
	}

	public synchronized OwnedItems snapshot()
	{
		return new OwnedItems(accountKey, bank, inventory, equipment, bankCapturedAt, updatedAt);
	}

	public synchronized String getAccountKey()
	{
		return accountKey;
	}

	// ------------------------------------------------------------ persistence codec

	/** Encodes a container as {@code id:qty,id:qty} for storage in the RuneLite config. */
	public static String encode(Map<Integer, Integer> items)
	{
		StringBuilder sb = new StringBuilder(items.size() * 8);
		items.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(e.getKey()).append(':').append(e.getValue());
		});
		return sb.toString();
	}

	/** Lenient inverse of {@link #encode}: malformed entries are skipped, never thrown. */
	public static Map<Integer, Integer> decode(String encoded)
	{
		Map<Integer, Integer> out = new HashMap<>();
		if (encoded == null || encoded.isEmpty())
		{
			return out;
		}
		for (String part : encoded.split(","))
		{
			int colon = part.indexOf(':');
			if (colon <= 0)
			{
				continue;
			}
			try
			{
				int id = Integer.parseInt(part.substring(0, colon).trim());
				int qty = Integer.parseInt(part.substring(colon + 1).trim());
				if (id > 0 && qty > 0)
				{
					out.put(id, qty);
				}
			}
			catch (NumberFormatException ignored)
			{
				// skip the malformed entry
			}
		}
		return out;
	}
}
