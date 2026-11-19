package com.bossoptimiser.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.bank.OwnedItemsTracker.Container;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class OwnedItemsTrackerTest
{
	private static Map<Integer, Integer> items(int... idQty)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			m.put(idQty[i], idQty[i + 1]);
		}
		return m;
	}

	@Test
	public void bankIsUnknownUntilItHasBeenRead()
	{
		OwnedItemsTracker t = new OwnedItemsTracker();
		t.switchAccount("a");
		t.update(Container.INVENTORY, items(385, 5), 1000);
		OwnedItems owned = t.snapshot();
		assertFalse(owned.hasBankData());
		assertEquals(5, owned.count(385));
		assertEquals(OwnedItems.Location.INVENTORY, owned.locate(385));
		assertEquals(OwnedItems.Location.NOT_OWNED, owned.locate(4151));
	}

	@Test
	public void quantitiesAreSummedAcrossContainersWithoutDoubleCounting()
	{
		OwnedItemsTracker t = new OwnedItemsTracker();
		t.switchAccount("a");
		t.update(Container.BANK, items(385, 100, 4151, 1), 1000);
		t.update(Container.INVENTORY, items(385, 5), 1000);
		assertEquals(105, t.snapshot().count(385));

		// Withdraw 10 sharks: the bank and inventory containers both change in the same tick.
		t.update(Container.BANK, items(385, 90, 4151, 1), 2000);
		t.update(Container.INVENTORY, items(385, 15), 2000);
		assertEquals(105, t.snapshot().count(385));

		// Equip the whip straight from the bank.
		t.update(Container.BANK, items(385, 90), 3000);
		t.update(Container.EQUIPMENT, items(4151, 1), 3000);
		OwnedItems owned = t.snapshot();
		assertEquals(1, owned.count(4151));
		assertEquals(OwnedItems.Location.EQUIPPED, owned.locate(4151));
		assertEquals(1, owned.carried(4151));
		assertEquals(3000, owned.getBankCapturedAt());
	}

	@Test
	public void itemsThatAreNoLongerOwnedDisappear()
	{
		OwnedItemsTracker t = new OwnedItemsTracker();
		t.switchAccount("a");
		t.update(Container.BANK, items(4151, 1, 385, 3), 1000);
		assertTrue(t.snapshot().owns(4151));
		assertTrue(t.update(Container.BANK, items(385, 3), 2000));
		assertFalse(t.snapshot().owns(4151));
		assertFalse("identical contents are not a change", t.update(Container.INVENTORY, items(), 2500));
	}

	@Test
	public void zeroQuantitiesAndInvalidIdsAreIgnored()
	{
		OwnedItemsTracker t = new OwnedItemsTracker();
		t.switchAccount("a");
		t.update(Container.BANK, items(385, 0, -1, 5, 0, 2, 4151, 1), 1000);
		assertEquals(1, t.snapshot().distinctCount());
	}

	@Test
	public void switchingAccountDropsThePreviousAccountsItems()
	{
		OwnedItemsTracker t = new OwnedItemsTracker();
		assertTrue(t.switchAccount("first"));
		t.update(Container.BANK, items(4151, 1), 1000);
		assertFalse(t.switchAccount("first"));
		assertTrue(t.switchAccount("second"));
		OwnedItems owned = t.snapshot();
		assertEquals("second", owned.getAccountKey());
		assertFalse(owned.owns(4151));
		assertFalse(owned.hasBankData());
		assertTrue(t.switchAccount(null));
		assertNull(t.snapshot().getAccountKey());
	}

	@Test
	public void staleness()
	{
		OwnedItemsTracker t = new OwnedItemsTracker();
		t.switchAccount("a");
		t.restoreBank(items(4151, 1), 1000);
		OwnedItems owned = t.snapshot();
		assertTrue(owned.hasBankData());
		assertFalse(owned.isBankStale(1000 + 60_000, 3_600_000));
		assertTrue(owned.isBankStale(1000 + 7_200_000, 3_600_000));
	}

	@Test
	public void persistenceCodecRoundTripsAndToleratesGarbage()
	{
		Map<Integer, Integer> bank = items(4151, 1, 385, 1200, 2434, 37);
		assertEquals(bank, OwnedItemsTracker.decode(OwnedItemsTracker.encode(bank)));
		assertEquals("385:1200,2434:37,4151:1", OwnedItemsTracker.encode(bank));
		assertTrue(OwnedItemsTracker.decode(null).isEmpty());
		assertTrue(OwnedItemsTracker.decode("").isEmpty());
		assertEquals(items(385, 2), OwnedItemsTracker.decode("abc,385:2,:,7:x,-4:1,9:0,<script>"));
	}

	@Test
	public void virtualItemsDoNotMutateTheSnapshot()
	{
		OwnedItems owned = new OwnedItems("a", items(385, 1), items(), items(), 1, 1);
		OwnedItems more = owned.withVirtual(4151, 1);
		assertFalse(owned.owns(4151));
		assertTrue(more.owns(4151));
	}
}
