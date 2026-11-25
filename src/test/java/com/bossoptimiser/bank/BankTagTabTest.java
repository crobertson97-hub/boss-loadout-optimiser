package com.bossoptimiser.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.data.ItemSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class BankTagTabTest
{
	/** What RuneLite 1.13's TabInterface.importTag reads, re-implemented to check the text round-trips. */
	private static final class Imported
	{
		String name;
		int icon;
		final Set<Integer> items = new LinkedHashSet<>();
		final Map<Integer, Integer> layout = new LinkedHashMap<>();
	}

	private static Imported parse(String text)
	{
		Iterator<String> it = Arrays.asList(text.split(",")).iterator();
		Imported out = new Imported();
		assertEquals("banktags", it.next());
		assertEquals("1", it.next());
		out.name = it.next();
		out.icon = Integer.parseInt(it.next());
		while (it.hasNext())
		{
			String token = it.next();
			if ("layout".equals(token))
			{
				break;
			}
			out.items.add(Integer.parseInt(token));
		}
		while (it.hasNext())
		{
			int pos = Integer.parseInt(it.next());
			int item = Integer.parseInt(it.next());
			out.layout.put(pos, item);
		}
		return out;
	}

	@Test
	public void exportRoundTripsThroughTheBankTagsImportFormat()
	{
		Map<ItemSlot, Integer> worn = new EnumMap<>(ItemSlot.class);
		worn.put(ItemSlot.HEAD, 100);
		worn.put(ItemSlot.WEAPON, 200);
		worn.put(ItemSlot.RING, 300);
		List<Integer> inv = new ArrayList<>(Collections.nCopies(28, -1));
		inv.set(0, 400);
		inv.set(5, 500);
		inv.set(27, 400);

		String text = BankTagTab.export("Vorkath - Ranged", 200, worn, inv, new HashSet<>(Arrays.asList(401, 402)));
		Imported tab = parse(text);

		assertEquals("vorkath ranged", tab.name);
		assertEquals(200, tab.icon);
		assertEquals(new LinkedHashSet<>(Arrays.asList(100, 200, 300, 400, 500, 401, 402)), tab.items);
		// Equipment in the right-hand columns, shaped like the equipment screen.
		assertEquals(Integer.valueOf(100), tab.layout.get(6));
		assertEquals(Integer.valueOf(200), tab.layout.get(2 * 8 + 5));
		assertEquals(Integer.valueOf(300), tab.layout.get(4 * 8 + 7));
		// Inventory in the left four columns, row by row.
		assertEquals(Integer.valueOf(400), tab.layout.get(0));
		assertEquals(Integer.valueOf(500), tab.layout.get(8 + 1));
		assertEquals(Integer.valueOf(400), tab.layout.get(6 * 8 + 3));
		assertEquals(6, tab.layout.size());
		// Extra doses are tagged but not placed.
		assertTrue(!tab.layout.containsValue(401));
	}

	@Test
	public void equipmentAndInventoryNeverShareABankPosition()
	{
		Set<Integer> used = new HashSet<>();
		for (int i = 0; i < 28; i++)
		{
			assertTrue(used.add(BankTagTab.inventoryPosition(i)));
			assertTrue(BankTagTab.inventoryPosition(i) % 8 < 4);
		}
		for (ItemSlot slot : ItemSlot.values())
		{
			Integer pos = BankTagTab.equipmentPosition(slot);
			assertTrue(slot + " has a position", pos != null);
			assertTrue(slot + " overlaps", used.add(pos));
		}
	}

	@Test
	public void tagNamesAreSafeForBankTags()
	{
		assertEquals("kree arra ranged", BankTagTab.tagName("Kree'arra", "Ranged"));
		assertEquals("grotesque guardians ranged on dawn melee", BankTagTab.tagName("Grotesque Guardians", "Ranged on Dawn, melee on Dusk"));
		assertEquals("boss loadout", BankTagTab.tagName("!!!", null));
		assertTrue(BankTagTab.tagName("x".repeat(100)).length() <= 40);
	}

	@Test
	public void nothingToExportGivesNull()
	{
		assertNull(BankTagTab.export("empty", 0, new EnumMap<>(ItemSlot.class), Collections.nCopies(28, -1), null));
	}
}
