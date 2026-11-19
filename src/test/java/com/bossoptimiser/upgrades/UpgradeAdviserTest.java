package com.bossoptimiser.upgrades;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bank.WithdrawList;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.inventory.InventoryPlanner;
import com.bossoptimiser.optimiser.GearOptimiser;
import com.bossoptimiser.optimiser.OptimiserResult;
import com.bossoptimiser.optimiser.PriceSource;
import com.bossoptimiser.optimiser.SlotAnalyser;
import com.bossoptimiser.upgrades.UpgradeAdviser.Report;
import com.bossoptimiser.upgrades.UpgradeAdviser.Upgrade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Upgrade adviser, slot analysis and the bank withdraw list. */
public class UpgradeAdviserTest
{
	private final GearOptimiser optimiser = Fixtures.optimiser();
	private final UpgradeAdviser adviser = new UpgradeAdviser(Fixtures.data(), optimiser, Fixtures.eligibility());

	private static final String[] BANK = {"Rune crossbow", "Broad bolts", "Black d'hide body", "Black d'hide chaps",
		"Amulet of fury", "Ava's accumulator", "Barrows gloves"};

	/** Only a handful of items are "tradeable" in this fake market. */
	private static PriceSource market(Map<String, Long> named)
	{
		Map<Integer, Long> byId = new HashMap<>();
		for (Map.Entry<String, Long> e : named.entrySet())
		{
			byId.put(TestData.item(e.getKey()).getId(), e.getValue());
		}
		return new PriceSource()
		{
			@Override
			public long price(int itemId)
			{
				return byId.getOrDefault(itemId, 0L);
			}

			@Override
			public String describe()
			{
				return "fake market";
			}
		};
	}

	private OptimiserResult baseline()
	{
		return optimiser.optimise(Fixtures.request("vorkath", "ranged", Fixtures.bank(BANK)).build(), () -> false);
	}

	private static Upgrade find(Report report, String name)
	{
		for (Upgrade u : report.getUpgrades())
		{
			if (u.getItem().getName().equals(name))
			{
				return u;
			}
		}
		return null;
	}

	@Test
	public void suggestsUpgradesMeasuredOnTheWholeResultingLoadout()
	{
		Map<String, Long> prices = new HashMap<>();
		prices.put("Dragon hunter crossbow", 60_000_000L);
		prices.put("Necklace of anguish", 12_000_000L);
		prices.put("Salve amulet(ei)", 1L);
		prices.put("Armadyl chestplate", 30_000_000L);
		prices.put("Abyssal whip", 1_500_000L);
		OptimiserResult baseline = baseline();
		Report report = adviser.advise(baseline, market(prices), 0, Fixtures.NOW, () -> false);

		assertEquals("fake market", report.getPriceSource());
		assertEquals(Fixtures.NOW, report.getPricedAtMillis());
		Upgrade dhcb = find(report, "Dragon hunter crossbow");
		assertNotNull(report.getUpgrades().toString(), dhcb);
		assertEquals("Rune crossbow", dhcb.getReplaces().getName());
		assertEquals(baseline.getTotalDps(), dhcb.getCurrentDps(), 1e-9);
		assertTrue(dhcb.getDpsGain() > 0);
		assertEquals(dhcb.getNewDps() - dhcb.getCurrentDps(), dhcb.getDpsGain(), 1e-9);
		assertEquals(dhcb.getDpsGain() / dhcb.getCurrentDps() * 100, dhcb.getPercentGain(), 1e-9);
		assertEquals(60_000_000L, dhcb.getPrice());
		assertEquals(dhcb.getPrice() / dhcb.getPercentGain(), dhcb.getCostPerPercent(), 1e-6);
		assertTrue("The owned broad bolts still fit, so nothing else must be bought", dhcb.getDependencies().isEmpty());

		// The gain is exactly what a full re-optimisation with the item added produces.
		OwnedItems withItem = Fixtures.bank(BANK).withVirtual(dhcb.getItem().getId(), 1);
		assertEquals(optimiser.optimise(Fixtures.request("vorkath", "ranged", withItem).build(), () -> false).getTotalDps(),
			dhcb.getNewDps(), 1e-9);

		assertNotNull("Vorkath is undead, so the salve amulet is a real upgrade", find(report, "Salve amulet(ei)"));
		assertTrue("A melee weapon is not an upgrade for a ranged setup", find(report, "Abyssal whip") == null);
		for (int i = 1; i < report.getUpgrades().size(); i++)
		{
			assertTrue("Sorted by cost per percentage point",
				report.getUpgrades().get(i - 1).getCostPerPercent() <= report.getUpgrades().get(i).getCostPerPercent());
		}
	}

	@Test
	public void budgetFilterAndUnavailablePrices()
	{
		Map<String, Long> prices = new HashMap<>();
		prices.put("Dragon hunter crossbow", 60_000_000L);
		prices.put("Salve amulet(ei)", 1L);
		Report limited = adviser.advise(baseline(), market(prices), 1_000_000, Fixtures.NOW, () -> false);
		assertTrue(find(limited, "Dragon hunter crossbow") == null);
		assertNotNull(find(limited, "Salve amulet(ei)"));

		Report noPrices = adviser.advise(baseline(), null, 0, Fixtures.NOW, () -> false);
		assertTrue(noPrices.getUpgrades().isEmpty());
		assertEquals("unavailable", noPrices.getPriceSource());
		assertTrue(noPrices.getNotes().get(0).contains("unavailable"));

		Report nothingPriced = adviser.advise(baseline(), market(new HashMap<>()), 0, Fixtures.NOW, () -> false);
		assertTrue("Items without a price are never suggested", nothingPriced.getUpgrades().isEmpty());
	}

	@Test
	public void weaponUpgradeIncludesTheAmmunitionItNeeds()
	{
		Map<String, Long> prices = new HashMap<>();
		prices.put("Twisted bow", 1_500_000_000L);
		prices.put("Dragon arrow", 2_000L);
		prices.put("Rune arrow", 60L);
		OptimiserResult baseline = optimiser.optimise(Fixtures.request("cerberus", "ranged", Fixtures.bank(BANK)).build(), () -> false);
		Report report = adviser.advise(baseline, market(prices), 0, Fixtures.NOW, () -> false);
		Upgrade tbow = find(report, "Twisted bow");
		assertNotNull(report.getUpgrades().toString(), tbow);
		assertEquals(1, tbow.getDependencies().size());
		assertEquals("Dragon arrow", tbow.getDependencies().get(0).getName());
		assertEquals(1_500_000_000L + 1000 * 2_000L, tbow.getPrice());
	}

	@Test
	public void slotAlternativesShowTheImpactOfEveryOwnedItem()
	{
		OwnedItems owned = Fixtures.bank("Rune crossbow", "Broad bolts", "Amulet of fury", "Necklace of anguish", "Salve amulet(ei)", "Amulet of glory");
		OptimiserResult result = optimiser.optimise(Fixtures.request("vorkath", "ranged", owned).build(), () -> false);
		SlotAnalyser analyser = new SlotAnalyser(Fixtures.data(), optimiser, Fixtures.eligibility());
		List<SlotAnalyser.Alternative> neck = analyser.alternatives(result.getRequest(), result.primary(), ItemSlot.NECK);
		assertEquals(4, neck.size());
		assertTrue(neck.get(0).isSelected());
		assertEquals("Salve amulet(ei)", neck.get(0).getItem().getName());
		assertEquals(0, neck.get(0).getDpsDelta(), 1e-12);
		for (int i = 1; i < neck.size(); i++)
		{
			assertFalse(neck.get(i).isSelected());
			assertTrue(neck.get(i).getDpsDelta() < 0);
			assertEquals(OwnedItems.Location.BANK, neck.get(i).getLocation());
			assertTrue(neck.get(i - 1).getDps() >= neck.get(i).getDps());
		}
		List<String> lines = analyser.explain(result.getRequest(), result.primary());
		assertTrue(lines.toString(), lines.stream().anyMatch(l -> l.startsWith("Neck: Salve amulet(ei)") && l.contains("Necklace of anguish")));
		assertTrue(lines.stream().anyMatch(l -> l.startsWith("Weapon: Rune crossbow") && l.contains("only usable option")));
	}

	@Test
	public void withdrawListTracksWhatIsCarriedBankedAndMissing()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		Map<Integer, Integer> inventory = new HashMap<>();
		Map<Integer, Integer> equipment = new HashMap<>();
		ItemDef rcb = TestData.item("Rune crossbow");
		ItemDef bolts = TestData.item("Broad bolts");
		int shark = Fixtures.supplies().family("shark").bestId();
		int prayer4 = Fixtures.supplies().family("prayer_potion").idForDose(4);
		equipment.put(rcb.getId(), 1);
		bank.put(bolts.getId(), 500);
		bank.put(shark, 10);
		inventory.put(shark, 3);
		OwnedItems owned = new OwnedItems("a", bank, inventory, equipment, Fixtures.NOW, Fixtures.NOW);

		OptimiserResult result = optimiser.optimise(Fixtures.request("cerberus", "ranged", owned).build(), () -> false);
		InventoryPlan plan = new InventoryPlanner(Fixtures.supplies()).plan(result.getRequest().getBoss(),
			result.getRequest().getBoss().strategy("ranged"), result, owned, result.getRequest().getAssumptions(),
			InventoryPlan.Preferences.builder().build(), null);
		WithdrawList list = WithdrawList.build(Fixtures.data(), result, plan, owned);

		WithdrawList.Entry weapon = list.get(rcb.getId());
		assertEquals(WithdrawList.Kind.EQUIPMENT, weapon.getKind());
		assertTrue("Already worn", weapon.isWithdrawn());
		WithdrawList.Entry ammo = list.get(bolts.getId());
		assertFalse(ammo.isWithdrawn());
		assertFalse(ammo.isMissing());
		assertEquals(1, ammo.stillToWithdraw());

		WithdrawList.Entry food = list.get(shark);
		assertEquals(WithdrawList.Kind.SUPPLY, food.getKind());
		assertEquals(3, food.getCarried());
		assertEquals(10, food.getInBank());
		assertTrue(food.getNeeded() > 13);
		assertTrue(food.isMissing());
		assertEquals(10, food.stillToWithdraw());

		WithdrawList.Entry prayer = list.get(prayer4);
		assertTrue(prayer.isMissing());
		assertEquals(0, prayer.stillToWithdraw());
		assertTrue(list.missing().contains(prayer));
		assertTrue(WithdrawList.EMPTY.isEmpty());
	}
}
