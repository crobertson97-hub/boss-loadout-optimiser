package com.bossoptimiser.optimiser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class GearOptimiserTest
{
	private final GearOptimiser optimiser = Fixtures.optimiser();

	private static final String[] RANGED_BANK = {
		"Rune crossbow", "Broad bolts", "Dragon hunter crossbow", "Diamond dragon bolts (e)", "Ruby dragon bolts (e)",
		"Magic shortbow (i)", "Amethyst arrow", "Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt",
		"Black d'hide body", "Black d'hide chaps", "Black d'hide vambraces", "Barrows gloves", "Ava's assembler",
		"Amulet of fury", "Necklace of anguish", "Salve amulet(ei)", "Pegasian boots", "Archers ring (i)",
		"Dragon defender", "Twisted buckler", "Abyssal whip", "Bandos chestplate", "Slayer helmet (i)"};

	private OptimiserResult run(OptimiserRequest request)
	{
		return optimiser.optimise(request, () -> false);
	}

	private static String name(OptimisedSetup setup, ItemSlot slot)
	{
		ItemDef d = setup.getLoadout().get(slot);
		return d == null ? null : d.getName();
	}

	@Test
	public void choosesTheWholeBestSetupNotTheBestStatPerSlot()
	{
		OptimiserResult result = run(Fixtures.request("vorkath", "ranged", Fixtures.bank(RANGED_BANK)).build());
		OptimisedSetup best = result.primary();
		assertNotNull(result.getWarnings().toString(), best);
		assertEquals(CombatClass.RANGED, best.getCombatClass());
		// Against an undead dragon the dragon hunter crossbow beats everything else in this bank,
		// and the salve amulet (ei) beats the necklace of anguish despite much lower ranged stats.
		assertEquals("Dragon hunter crossbow", name(best, ItemSlot.WEAPON));
		assertEquals("Salve amulet(ei)", name(best, ItemSlot.NECK));
		assertEquals("Twisted buckler", name(best, ItemSlot.SHIELD));
		assertTrue(name(best, ItemSlot.AMMO).endsWith("dragon bolts (e)"));
		assertEquals("Armadyl chestplate", name(best, ItemSlot.BODY));
		assertTrue(best.isExact());
		assertTrue(result.getTotalDps() > 0);
		assertEquals(best.getDps(), result.getTotalDps(), 1e-9);
		assertTrue(result.getEvaluatedCombinations() > 0);
	}

	@Test
	public void bestSetupIsNeverBeatenByAnAlternativeOrASingleSwap()
	{
		OptimiserRequest request = Fixtures.request("vorkath", "ranged", Fixtures.bank(RANGED_BANK)).build();
		OptimiserResult result = run(request);
		OptimisedSetup best = result.primary();
		assertFalse(result.getAlternatives().isEmpty());
		for (OptimisedSetup alt : result.getAlternatives())
		{
			assertTrue(alt.getDps() <= best.getDps() + 1e-9);
		}
		// Exhaustive single-slot check against every owned item.
		for (int id : request.getOwned().allIds())
		{
			ItemDef def = Fixtures.data().item(id);
			if (def.getSlot() == ItemSlot.WEAPON || def.getSlot() == ItemSlot.AMMO)
			{
				continue;
			}
			Loadout swapped = best.getLoadout().with(def.getSlot(), def);
			double dps = optimiser.evaluateLoadout(request, CombatClass.RANGED, swapped, best.getStyle(), null).getDps();
			assertTrue("Swapping in " + def + " should not improve on the optimum", dps <= best.getDps() + 1e-9);
		}
	}

	@Test
	public void twoHandedWeaponsNeverGetAShield()
	{
		OwnedItems owned = Fixtures.bank("Twisted bow", "Dragon arrow", "Twisted buckler", "Armadyl chestplate", "Rune crossbow", "Broad bolts");
		// Cerberus has a high Magic level, which is what the twisted bow scales with.
		OptimisedSetup best = run(Fixtures.request("cerberus", "ranged", owned).build()).primary();
		assertEquals("Twisted bow", name(best, ItemSlot.WEAPON));
		assertNull(best.getLoadout().get(ItemSlot.SHIELD));
		assertEquals("Dragon arrow", name(best, ItemSlot.AMMO));
	}

	@Test
	public void weaponWithoutCompatibleAmmunitionIsSkippedWithAWarning()
	{
		OwnedItems owned = Fixtures.bank("Twisted bow", "Diamond bolts (e)", "Rune crossbow");
		OptimiserResult result = run(Fixtures.request("general-graardor", "ranged", owned).build());
		assertEquals("Rune crossbow", name(result.primary(), ItemSlot.WEAPON));
		assertEquals("Diamond bolts (e)", name(result.primary(), ItemSlot.AMMO));
		assertTrue(result.getWarnings().toString(), result.getWarnings().toString().contains("Twisted bow"));
	}

	@Test
	public void noUsableWeaponGivesAnEmptyResultWithAnExplanation()
	{
		OptimiserResult result = run(Fixtures.request("vorkath", "ranged", Fixtures.bank("Abyssal whip", "Amulet of fury")).build());
		assertTrue(result.isEmpty());
		assertFalse(result.getWarnings().isEmpty());
	}

	@Test
	public void itemsTheAccountCannotEquipAreLeftOutAndReported()
	{
		PlayerLevels levels = PlayerLevels.maxed().toBuilder().ranged(64).build();
		OptimiserRequest request = Fixtures.request("vorkath", "ranged", Fixtures.bank(RANGED_BANK)).account(Fixtures.account(levels))
			.assumptions(CombatAssumptions.defaultsFor(levels)).build();
		OptimiserResult result = run(request);
		assertFalse("A 65 Ranged weapon must not be recommended at 64 Ranged", "Dragon hunter crossbow".equals(name(result.primary(), ItemSlot.WEAPON)));
		assertFalse("Armadyl needs 70 Ranged", "Armadyl chestplate".equals(name(result.primary(), ItemSlot.BODY)));
		assertTrue(result.getUnusableOwned().toString(), result.getUnusableOwned().toString().contains("Dragon hunter crossbow"));
		assertTrue(result.getUnusableOwned().toString(), result.getUnusableOwned().toString().contains("65 Ranged"));
	}

	@Test
	public void unknownRequirementItemsAreFlaggedOrExcludedOnRequest()
	{
		// Leaf-bladed battleaxe has no entry in the curated requirement data.
		OwnedItems owned = Fixtures.bank("Leaf-bladed sword", "Rune scimitar");
		OptimiserResult allowed = run(Fixtures.request("cerberus", "melee", owned).build());
		assertEquals("Leaf-bladed sword", name(allowed.primary(), ItemSlot.WEAPON));
		assertFalse("A setup with an unverified item is not presented as exact", allowed.primary().isExact());
		assertEquals(1, allowed.primary().getUnverifiedItemIds().size());

		OptimiserResult strict = run(Fixtures.request("cerberus", "melee", owned).allowUnknownRequirements(false).build());
		assertEquals("Rune scimitar", name(strict.primary(), ItemSlot.WEAPON));
		assertTrue(strict.primary().isExact());

		int id = TestData.item("Leaf-bladed sword").getId();
		OptimiserResult confirmed = run(Fixtures.request("cerberus", "melee", owned).allowUnknownRequirements(false)
			.confirmedItemIds(Collections.singleton(id)).build());
		assertEquals("Leaf-bladed sword", name(confirmed.primary(), ItemSlot.WEAPON));
	}

	@Test
	public void exclusionsAndLocksAreRespected()
	{
		OwnedItems owned = Fixtures.bank(RANGED_BANK);
		int dhcb = TestData.item("Dragon hunter crossbow").getId();
		OptimiserResult excluded = run(Fixtures.request("vorkath", "ranged", owned).excludedItemIds(Collections.singleton(dhcb)).build());
		assertFalse("Dragon hunter crossbow".equals(name(excluded.primary(), ItemSlot.WEAPON)));

		Map<ItemSlot, Integer> locks = new HashMap<>();
		locks.put(ItemSlot.NECK, TestData.item("Amulet of fury").getId());
		OptimiserResult locked = run(Fixtures.request("vorkath", "ranged", owned).lockedSlots(locks).build());
		assertEquals("Amulet of fury", name(locked.primary(), ItemSlot.NECK));
		assertTrue(locked.primary().getDps() < run(Fixtures.request("vorkath", "ranged", owned).build()).primary().getDps());
	}

	@Test
	public void setEffectsAreFoundEvenWhenEachPieceLooksWorse()
	{
		// Void pieces have no ranged bonuses at all, so each one is dominated by even a leather item;
		// only evaluating the complete set reveals that it is better.
		OwnedItems owned = Fixtures.bank("Toxic blowpipe", "Void ranger helm", "Elite void top", "Elite void robe", "Void knight gloves",
			"Leather body", "Leather chaps", "Leather vambraces", "Leather cowl");
		CombatAssumptions assumptions = CombatAssumptions.defaultsFor(PlayerLevels.maxed()).toBuilder()
			.blowpipeDartId(TestData.item("Dragon dart").getId()).build();
		OptimisedSetup best = run(Fixtures.request("general-graardor", "ranged", owned).assumptions(assumptions).build()).primary();
		assertEquals("Void ranger helm", name(best, ItemSlot.HEAD));
		assertEquals("Elite void top", name(best, ItemSlot.BODY));
		assertEquals("Elite void robe", name(best, ItemSlot.LEGS));
		assertEquals("Void knight gloves", name(best, ItemSlot.HANDS));
	}

	@Test
	public void slayerHelmetIsChosenOnlyOnTask()
	{
		OwnedItems owned = Fixtures.bank("Abyssal whip", "Slayer helmet (i)", "Helm of Neitiznot", "Dragon defender");
		// Cerberus is task-only, so the helmet always applies there.
		assertEquals("Slayer helmet (i)", name(run(Fixtures.request("cerberus", "melee", owned).build()).primary(), ItemSlot.HEAD));
		// Graardor off task: the helmet's bonus does not apply and the neitiznot helm's strength wins.
		assertEquals("Helm of Neitiznot", name(run(Fixtures.request("general-graardor", "melee", owned).build()).primary(), ItemSlot.HEAD));
	}

	@Test
	public void prioritiesTradeDamageForDefence()
	{
		OwnedItems owned = Fixtures.bank("Abyssal whip", "Dragon defender", "Dragonfire shield", "Bandos chestplate", "Torva platebody",
			"Fighter torso", "Amulet of torture", "Amulet of fury", "Berserker ring (i)", "Ring of suffering (i)", "Dragon boots", "Primordial boots");
		OptimisedSetup dps = run(Fixtures.request("general-graardor", "melee", owned).priority(Priority.MAX_DPS).build()).primary();
		OptimisedSetup tank = run(Fixtures.request("general-graardor", "melee", owned).priority(Priority.SURVIVABILITY).build()).primary();
		OptimisedSetup balanced = run(Fixtures.request("general-graardor", "melee", owned).priority(Priority.BALANCED).build()).primary();
		assertTrue(tank.getDefenceScore() > dps.getDefenceScore());
		assertTrue(tank.getDps() <= dps.getDps());
		assertTrue("Survivability keeps at least 85% of the best DPS", tank.getDps() >= 0.85 * dps.getDps() - 1e-9);
		assertTrue(balanced.getDefenceScore() >= dps.getDefenceScore());
		assertTrue(balanced.getDps() >= tank.getDps() - 1e-9);
	}

	@Test
	public void budgetPriorityPenalisesExpensiveAmmunition()
	{
		OwnedItems owned = Fixtures.bank("Rune crossbow", "Broad bolts", "Diamond bolts (e)", "Ava's assembler");
		Map<Integer, Long> prices = new HashMap<>();
		prices.put(TestData.item("Diamond bolts (e)").getId(), 50_000L);
		prices.put(TestData.item("Broad bolts").getId(), 50L);
		OptimiserResult budget = run(Fixtures.request("general-graardor", "ranged", owned).priority(Priority.BUDGET)
			.prices(Fixtures.prices(prices)).build());
		OptimiserResult dps = run(Fixtures.request("general-graardor", "ranged", owned).priority(Priority.MAX_DPS)
			.prices(Fixtures.prices(prices)).build());
		assertEquals("Diamond bolts (e)", name(dps.primary(), ItemSlot.AMMO));
		assertEquals("Broad bolts", name(budget.primary(), ItemSlot.AMMO));
		assertTrue(budget.primary().getCostPerHour() < dps.primary().getCostPerHour());

		OptimiserResult noPrices = run(Fixtures.request("general-graardor", "ranged", owned).priority(Priority.BUDGET).build());
		assertTrue(noPrices.getWarnings().toString().contains("No price data"));
		assertEquals(-1, noPrices.primary().getCostPerHour(), 0);
	}

	@Test
	public void hybridStrategyProducesBothSetupsAndCarriedSwitches()
	{
		OwnedItems owned = Fixtures.bank("Trident of the Swamp", "Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt", "Occult necklace",
			"Tormented bracelet", "Eternal boots", "Seers ring (i)", "Imbued Saradomin cape", "Book of Darkness",
			"Toxic blowpipe", "Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt", "Necklace of anguish",
			"Barrows gloves", "Pegasian boots", "Archers ring (i)", "Ava's assembler");
		CombatAssumptions assumptions = CombatAssumptions.defaultsFor(PlayerLevels.maxed()).toBuilder()
			.blowpipeDartId(TestData.item("Dragon dart").getId()).build();
		OptimiserResult result = run(Fixtures.request("zulrah", "hybrid", owned).assumptions(assumptions).build());
		assertEquals(2, result.getSetups().size());
		OptimisedSetup magic = result.getSetups().get(CombatClass.MAGIC);
		OptimisedSetup ranged = result.getSetups().get(CombatClass.RANGED);
		assertEquals("Magic covers two of three forms, so it is the worn setup", magic, result.primary());
		assertEquals("Trident of the Swamp", name(magic, ItemSlot.WEAPON));
		assertEquals("Toxic blowpipe", name(ranged, ItemSlot.WEAPON));
		assertEquals(2, magic.getPhaseResults().size());
		assertEquals(1, ranged.getPhaseResults().size());
		assertFalse(result.getSwitchItems().isEmpty());
		assertTrue(result.getSwitchItems().size() <= OptimiserRequest.DEFAULT_MAX_SWITCHES);
		assertEquals(GearOptimiser.switchItems(magic.getLoadout(), ranged.getLoadout()), result.getSwitchItems());
		assertTrue(result.getWarnings().toString().contains("assumptions"));

		// Total DPS is the phase-weighted mix of the two setups.
		double expected = 0.3334 * magic.getPhaseResults().get("serpentine").getDps()
			+ 0.3333 * magic.getPhaseResults().get("magma").getDps()
			+ 0.3333 * ranged.getPhaseResults().get("tanzanite").getDps();
		assertEquals(expected, result.getTotalDps(), 1e-9);

		OptimiserResult low = run(Fixtures.request("zulrah", "hybrid", owned).assumptions(assumptions).priority(Priority.LOW_SWITCH).build());
		assertTrue(low.getSwitchItems().size() <= OptimiserRequest.LOW_SWITCH_MAX_SWITCHES);
		assertTrue(low.getTotalDps() <= result.getTotalDps() + 1e-9);
	}

	@Test
	public void autocastSpellMatchesTheBossWeaknessAndMagicLevel()
	{
		OwnedItems owned = Fixtures.bank("Kodai wand", "Occult necklace", "Ahrim's robetop");
		OptimisedSetup best = run(Fixtures.request("zulrah", "magic", owned).build()).primary();
		assertEquals("Zulrah is weak to fire", "Fire Surge", best.getSpell().getName());
		PlayerLevels levels = PlayerLevels.maxed().toBuilder().magic(80).build();
		OptimisedSetup mid = run(Fixtures.request("zulrah", "magic", owned).account(Fixtures.account(levels))
			.assumptions(CombatAssumptions.defaultsFor(levels)).build()).primary();
		assertEquals("Fire Wave", mid.getSpell().getName());
	}

	@Test
	public void uselessOrHarmfulItemsLeaveTheSlotEmptyAndDefenceBreaksTies()
	{
		OwnedItems owned = Fixtures.bank("Trident of the Swamp", "Broad bolts", "Rune full helm", "Ring of suffering (i)", "Berserker ring (i)");
		OptimisedSetup best = run(Fixtures.request("zulrah", "magic", owned).build()).primary();
		assertNull("Bolts do nothing for a magic setup", best.getLoadout().get(ItemSlot.AMMO));
		assertNull("A helm with negative magic attack lowers DPS, so the slot stays empty", best.getLoadout().get(ItemSlot.HEAD));
		assertEquals("Neither ring adds magic damage; the one that defends against Zulrah wins the tie",
			"Ring of suffering (i)", name(best, ItemSlot.RING));

		// When defence is an objective the helm is worth considering again.
		OptimisedSetup tank = run(Fixtures.request("zulrah", "magic", owned).priority(Priority.SURVIVABILITY).build()).primary();
		assertTrue(tank.getDefenceScore() >= best.getDefenceScore());
	}

	@Test
	public void resultsAreReproducible()
	{
		OptimiserRequest request = Fixtures.request("vorkath", "ranged", Fixtures.bank(RANGED_BANK)).build();
		OptimiserResult a = run(request);
		OptimiserResult b = run(request);
		assertEquals(a.primary().getLoadout(), b.primary().getLoadout());
		assertEquals(a.getTotalDps(), b.getTotalDps(), 0);
		assertEquals(a.getEvaluatedCombinations(), b.getEvaluatedCombinations());
		assertEquals(a.getAlternatives().size(), b.getAlternatives().size());
	}

	@Test
	public void bankNotReadIsCalledOut()
	{
		OwnedItems carriedOnly = new OwnedItems("test-account", new HashMap<>(), new HashMap<>(),
			Collections.singletonMap(TestData.item("Abyssal whip").getId(), 1), 0, Fixtures.NOW);
		OptimiserResult result = run(Fixtures.request("general-graardor", "melee", carriedOnly).build());
		assertEquals("Abyssal whip", name(result.primary(), ItemSlot.WEAPON));
		assertTrue(result.getWarnings().get(0).contains("bank has not been read"));
	}

	@Test
	public void cancellationStopsTheSearch()
	{
		OptimiserRequest request = Fixtures.request("vorkath", "ranged", Fixtures.bank(RANGED_BANK)).build();
		OptimiserResult cancelled = optimiser.optimise(request, () -> true);
		assertTrue(cancelled.isEmpty());
	}

	@Test(timeout = 60_000)
	public void largeBankStaysResponsive()
	{
		// Every equipable item in the game, owned at once: far larger than any real bank.
		Map<Integer, Integer> everything = new HashMap<>();
		for (ItemDef d : Fixtures.data().items())
		{
			everything.put(d.getId(), 1000);
		}
		CombatAssumptions assumptions = CombatAssumptions.defaultsFor(PlayerLevels.maxed()).toBuilder()
			.blowpipeDartId(TestData.item("Dragon dart").getId()).build();
		AtomicInteger polls = new AtomicInteger();
		long start = System.nanoTime();
		OptimiserResult result = optimiser.optimise(
			Fixtures.request("zulrah", "hybrid", Fixtures.bank(everything)).assumptions(assumptions).build(),
			() -> polls.incrementAndGet() < 0);
		long millis = (System.nanoTime() - start) / 1_000_000;
		assertEquals(2, result.getSetups().size());
		assertTrue("Cancellation must be polled during long searches", polls.get() > 0);
		assertTrue("Full-game bank took " + millis + " ms", millis < 30_000);
		assertTrue(result.getEvaluatedCombinations() <= 2L * 2 * GearOptimiser.MAX_WEAPON_OPTIONS * GearOptimiser.MAX_COMBINATIONS_PER_OPTION * 2);
	}
}
