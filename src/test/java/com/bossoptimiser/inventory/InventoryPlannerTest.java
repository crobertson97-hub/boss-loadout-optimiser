package com.bossoptimiser.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.combat.Potion;
import com.bossoptimiser.inventory.InventoryPlan.Preferences;
import com.bossoptimiser.inventory.InventoryPlan.Slot;
import com.bossoptimiser.inventory.InventoryPlan.TripLength;
import com.bossoptimiser.optimiser.OptimiserResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class InventoryPlannerTest
{
	private final SupplyCatalogue supplies = Fixtures.supplies();
	private final InventoryPlanner planner = new InventoryPlanner(supplies);
	private final CombatAssumptions assumptions = CombatAssumptions.defaultsFor(PlayerLevels.maxed());

	private int id(String family, int dose)
	{
		return supplies.family(family).idForDose(dose);
	}

	private Map<Integer, Integer> wellStocked()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(id("shark", 1), 500);
		bank.put(id("prayer_potion", 4), 40);
		bank.put(id("ranging", 4), 10);
		bank.put(id("extended_super_antifire", 4), 5);
		bank.put(id("antivenom_plus", 4), 5);
		bank.put(id("teleport_to_house", 1), 50);
		bank.put(id("rune_pouch", 1), 1);
		bank.put(id("air_rune", 1), 5000);
		bank.put(id("earth_rune", 1), 5000);
		bank.put(id("chaos_rune", 1), 5000);
		return bank;
	}

	private InventoryPlan plan(String bossId, String strategyId, Map<Integer, Integer> bank, Preferences prefs)
	{
		BossDefinition boss = Fixtures.bosses().get(bossId);
		return planner.plan(boss, boss.strategy(strategyId), null, Fixtures.bank(bank), assumptions, prefs, null);
	}

	private static int count(InventoryPlan plan, String category)
	{
		int n = 0;
		for (Slot s : plan.getSlots())
		{
			if (category.equals(s.getCategory()))
			{
				n++;
			}
		}
		return n;
	}

	@Test
	public void fillsExactlyTwentyEightSlotsFromOwnedSupplies()
	{
		InventoryPlan plan = plan("vorkath", "ranged", wellStocked(), Preferences.builder().build());
		assertEquals(28, plan.getSlots().size());
		assertEquals(0, plan.freeSlots());
		assertTrue(plan.getMissing().toString(), plan.getMissing().isEmpty());
		assertEquals(1, count(plan, "BOOST"));
		assertEquals(1, count(plan, "ANTIFIRE"));
		assertEquals(1, count(plan, "ANTIVENOM"));
		assertEquals(2, count(plan, "PRAYER"));
		assertEquals(1, count(plan, "RUNE_POUCH"));
		assertEquals(1, count(plan, "TELEPORT"));
		assertEquals(21, count(plan, "FOOD"));
		for (Slot s : plan.getSlots())
		{
			assertTrue(s.getName() + " should be owned", s.isOwned());
		}
		assertTrue(plan.getNotes().toString().contains("Crumble Undead"));
	}

	@Test
	public void boostMatchesTheAssumedPotion()
	{
		InventoryPlan plan = plan("vorkath", "ranged", wellStocked(), Preferences.builder().build());
		Slot boost = plan.getSlots().stream().filter(s -> "BOOST".equals(s.getCategory())).findFirst().get();
		assertEquals("Ranging potion(4)", boost.getName());
		assertEquals(Potion.RANGING, assumptions.getRangedPotion());
	}

	@Test
	public void highestDoseIsUsedFirstAndQuantitiesAreNeverOverstated()
	{
		Map<Integer, Integer> bank = wellStocked();
		bank.remove(id("prayer_potion", 4));
		bank.put(id("prayer_potion", 4), 1);
		bank.put(id("prayer_potion", 2), 1);
		InventoryPlan plan = plan("cerberus", "melee", withMeleeBoost(bank), Preferences.builder().build());
		// Cerberus asks for 6 prayer slots on a normal trip; only 2 potions exist.
		assertEquals(6, count(plan, "PRAYER"));
		int owned = 0;
		for (Slot s : plan.getSlots())
		{
			if ("PRAYER".equals(s.getCategory()) && s.isOwned())
			{
				owned++;
			}
		}
		assertEquals(2, owned);
		Slot first = plan.getSlots().stream().filter(s -> "PRAYER".equals(s.getCategory())).findFirst().get();
		assertEquals("Prayer potion(4)", first.getName());
		InventoryPlan.Missing missing = plan.getMissing().stream().filter(m -> m.getName().startsWith("Prayer potion")).findFirst().get();
		assertEquals(6, missing.getNeeded());
		assertEquals(2, missing.getOwned());
		assertTrue(missing.isMandatory());
	}

	private Map<Integer, Integer> withMeleeBoost(Map<Integer, Integer> bank)
	{
		bank.put(id("super_combat", 4), 3);
		return bank;
	}

	@Test
	public void missingMandatorySuppliesAppearInTheChecklist()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(id("shark", 1), 5);
		InventoryPlan plan = plan("vorkath", "ranged", bank, Preferences.builder().build());
		assertEquals(28, plan.getSlots().size());
		assertTrue(plan.getMissing().stream().anyMatch(m -> m.isMandatory() && m.getName().contains("antifire")));
		assertTrue(plan.getMissing().stream().anyMatch(m -> m.isMandatory() && m.getName().toLowerCase().contains("venom")));
		assertTrue(plan.getMissing().stream().anyMatch(m -> m.getName().contains("rune")));
		assertTrue(plan.getMissing().stream().anyMatch(m -> m.getName().contains("food")));
		int ownedFood = 0;
		for (Slot s : plan.getSlots())
		{
			if ("FOOD".equals(s.getCategory()) && s.isOwned())
			{
				ownedFood++;
			}
		}
		assertEquals("Only the 5 sharks that exist may be shown as owned", 5, ownedFood);
	}

	@Test
	public void tripLengthAndPreferencesChangeTheMix()
	{
		InventoryPlan shortTrip = plan("cerberus", "melee", withMeleeBoost(wellStocked()), Preferences.builder().tripLength(TripLength.SHORT).build());
		InventoryPlan longTrip = plan("cerberus", "melee", withMeleeBoost(wellStocked()), Preferences.builder().tripLength(TripLength.LONG).build());
		assertEquals(4, count(shortTrip, "PRAYER"));
		assertEquals(8, count(longTrip, "PRAYER"));
		assertTrue(count(shortTrip, "FOOD") > count(longTrip, "FOOD"));

		InventoryPlan morePrayer = plan("vorkath", "ranged", wellStocked(), Preferences.builder().morePrayer(true).build());
		assertEquals(4, count(morePrayer, "PRAYER"));
		InventoryPlan maxFood = plan("vorkath", "ranged", wellStocked(), Preferences.builder().maxFood(true).build());
		assertTrue(count(maxFood, "FOOD") > count(plan("vorkath", "ranged", wellStocked(), Preferences.builder().build()), "FOOD"));
		assertEquals("Mandatory supplies survive the max-food preference", 1, count(maxFood, "ANTIFIRE"));
	}

	@Test
	public void lockedSlotsAreKeptAndExcludedItemsAreAvoided()
	{
		Map<Integer, Integer> bank = wellStocked();
		bank.put(id("manta_ray", 1), 100);
		bank.put(id("stamina", 4), 2);
		Preferences prefs = Preferences.builder()
			.locked(Collections.singletonList(new InventoryPlan.Locked(id("stamina", 4), "Stamina potion(4)", 1)))
			.excludedItemIds(Collections.singleton(id("manta_ray", 1)))
			.build();
		InventoryPlan plan = plan("vorkath", "ranged", bank, prefs);
		assertEquals(28, plan.getSlots().size());
		assertEquals("LOCKED", plan.getSlots().get(0).getCategory());
		assertTrue(plan.getSlots().get(0).isLocked());
		assertFalse(plan.getSlots().stream().anyMatch(s -> s.getName().equals("Manta ray")));
		assertTrue(plan.getSlots().stream().anyMatch(s -> s.getName().equals("Shark")));
	}

	@Test
	public void foodPreferenceOrderAndMixedFoodWhenTheBestRunsOut()
	{
		Map<Integer, Integer> bank = wellStocked();
		bank.put(id("shark", 1), 4);
		bank.put(id("manta_ray", 1), 6);
		bank.put(id("monkfish", 1), 200);
		InventoryPlan plan = plan("vorkath", "ranged", bank, Preferences.builder().build());
		assertEquals(6, plan.getSlots().stream().filter(s -> s.getName().equals("Manta ray")).count());
		assertEquals(4, plan.getSlots().stream().filter(s -> s.getName().equals("Shark")).count());
		assertEquals(11, plan.getSlots().stream().filter(s -> s.getName().equals("Monkfish")).count());
		assertTrue(plan.getMissing().isEmpty());
	}

	@Test
	public void minimumCostPreferencePicksTheCheapestHealingPerCoin()
	{
		Map<Integer, Integer> bank = wellStocked();
		bank.put(id("manta_ray", 1), 100);
		Map<Integer, Long> prices = new HashMap<>();
		prices.put(id("manta_ray", 1), 2200L);
		prices.put(id("shark", 1), 800L);
		BossDefinition boss = Fixtures.bosses().get("vorkath");
		InventoryPlan cheap = planner.plan(boss, boss.strategy("ranged"), null, Fixtures.bank(bank), assumptions,
			Preferences.builder().minCost(true).build(), Fixtures.prices(prices));
		assertTrue(cheap.getSlots().stream().anyMatch(s -> s.getName().equals("Shark")));
		assertFalse(cheap.getSlots().stream().anyMatch(s -> s.getName().equals("Manta ray")));
	}

	@Test
	public void switchItemsComeFirstAndEquipmentIsNotDuplicated()
	{
		OwnedItems owned = Fixtures.bank("Trident of the Swamp", "Occult necklace", "Toxic blowpipe", "Necklace of anguish", "Ava's assembler");
		CombatAssumptions a = assumptions.toBuilder().blowpipeDartId(TestData.item("Dragon dart").getId()).build();
		OptimiserResult result = Fixtures.optimiser().optimise(Fixtures.request("zulrah", "hybrid", owned).assumptions(a).build(), () -> false);
		BossDefinition boss = Fixtures.bosses().get("zulrah");
		InventoryPlan plan = planner.plan(boss, boss.strategy("hybrid"), result, owned, a, Preferences.builder().build(), null);
		assertEquals(28, plan.getSlots().size());
		int switches = count(plan, "SWITCH");
		assertEquals(result.getSwitchItems().size(), switches);
		assertTrue(switches >= 1);
		for (int i = 0; i < switches; i++)
		{
			assertEquals("SWITCH", plan.getSlots().get(i).getCategory());
		}
		assertTrue(plan.getSlots().stream().noneMatch(s -> s.getName().contains("Trident")));
		// A magic boost is assumed off by default, so no magic potion is recommended.
		assertEquals(1, count(plan, "BOOST"));
	}

	@Test
	public void runesGoLooseWhenThereIsNoPouch()
	{
		Map<Integer, Integer> bank = wellStocked();
		bank.remove(id("rune_pouch", 1));
		InventoryPlan plan = plan("vorkath", "ranged", bank, Preferences.builder().build());
		assertEquals(0, count(plan, "RUNE_POUCH"));
		assertEquals(3, count(plan, "RUNE"));
		Slot chaos = plan.getSlots().stream().filter(s -> s.getName().equals("Chaos rune")).findFirst().get();
		assertEquals(30, chaos.getQuantity());
		assertEquals(28, plan.getSlots().size());
	}

	@Test
	public void bankNotReadIsSaidPlainly()
	{
		BossDefinition boss = Fixtures.bosses().get("vorkath");
		OwnedItems none = new OwnedItems("a", new HashMap<>(), new HashMap<>(), new HashMap<>(), 0, 0);
		InventoryPlan plan = planner.plan(boss, boss.strategy("ranged"), null, none, assumptions, Preferences.builder().build(), null);
		assertTrue(plan.getNotes().get(0).contains("Bank not read"));
		assertTrue(plan.getSlots().stream().noneMatch(Slot::isOwned));
	}
}
