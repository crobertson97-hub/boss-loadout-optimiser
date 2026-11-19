package com.bossoptimiser.bank;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of everything the account holds, split by container. Item ids are real
 * (un-noted, non-placeholder) ids. The three containers are disjoint at any moment in the game,
 * so the total for an id is simply the sum; nothing is counted twice.
 */
public final class OwnedItems
{
	public enum Location
	{
		EQUIPPED("Equipped"), INVENTORY("Inventory"), BANK("Bank"), NOT_OWNED("Not owned");

		private final String label;

		Location(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public static final OwnedItems EMPTY = new OwnedItems(null, Collections.emptyMap(), Collections.emptyMap(),
		Collections.emptyMap(), 0, 0);

	private final String accountKey;
	private final Map<Integer, Integer> bank;
	private final Map<Integer, Integer> inventory;
	private final Map<Integer, Integer> equipment;
	/** Epoch millis of the last time the bank container was read; 0 when it never has been. */
	private final long bankCapturedAt;
	private final long updatedAt;

	public OwnedItems(String accountKey, Map<Integer, Integer> bank, Map<Integer, Integer> inventory,
		Map<Integer, Integer> equipment, long bankCapturedAt, long updatedAt)
	{
		this.accountKey = accountKey;
		this.bank = Collections.unmodifiableMap(new HashMap<>(bank));
		this.inventory = Collections.unmodifiableMap(new HashMap<>(inventory));
		this.equipment = Collections.unmodifiableMap(new HashMap<>(equipment));
		this.bankCapturedAt = bankCapturedAt;
		this.updatedAt = updatedAt;
	}

	public String getAccountKey()
	{
		return accountKey;
	}

	public Map<Integer, Integer> getBank()
	{
		return bank;
	}

	public Map<Integer, Integer> getInventory()
	{
		return inventory;
	}

	public Map<Integer, Integer> getEquipment()
	{
		return equipment;
	}

	public long getBankCapturedAt()
	{
		return bankCapturedAt;
	}

	public long getUpdatedAt()
	{
		return updatedAt;
	}

	public boolean hasBankData()
	{
		return bankCapturedAt > 0;
	}

	/** True when the bank snapshot is older than {@code maxAgeMillis}. */
	public boolean isBankStale(long now, long maxAgeMillis)
	{
		return hasBankData() && now - bankCapturedAt > maxAgeMillis;
	}

	public int count(int itemId)
	{
		return bank.getOrDefault(itemId, 0) + inventory.getOrDefault(itemId, 0) + equipment.getOrDefault(itemId, 0);
	}

	public int carried(int itemId)
	{
		return inventory.getOrDefault(itemId, 0) + equipment.getOrDefault(itemId, 0);
	}

	public boolean owns(int itemId)
	{
		return count(itemId) > 0;
	}

	public Location locate(int itemId)
	{
		if (equipment.getOrDefault(itemId, 0) > 0)
		{
			return Location.EQUIPPED;
		}
		if (inventory.getOrDefault(itemId, 0) > 0)
		{
			return Location.INVENTORY;
		}
		if (bank.getOrDefault(itemId, 0) > 0)
		{
			return Location.BANK;
		}
		return Location.NOT_OWNED;
	}

	/** Every id with a positive quantity in any container. */
	public Set<Integer> allIds()
	{
		Set<Integer> ids = new HashSet<>(bank.keySet());
		ids.addAll(inventory.keySet());
		ids.addAll(equipment.keySet());
		return ids;
	}

	public int distinctCount()
	{
		return allIds().size();
	}

	/** Copy with one extra item, used by the upgrade adviser to evaluate "what if I owned this". */
	public OwnedItems withVirtual(int itemId, int quantity)
	{
		Map<Integer, Integer> b = new HashMap<>(bank);
		b.merge(itemId, quantity, Integer::sum);
		return new OwnedItems(accountKey, b, inventory, equipment, bankCapturedAt, updatedAt);
	}
}
