package com.bossoptimiser.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.TestData;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.MonsterDef;
import org.junit.Test;

/**
 * Hand-checkable rolls. The expected figures are the published basic-roll values used by the
 * OSRS Wiki DPS calculator's own test-suite (weapon only, no boosts, first attack style).
 */
public class BasicRollsTest
{
	private final DpsCalculator calc = new DpsCalculator(TestData.data());
	private final MonsterDef abyssalDemon = TestData.data().monster("Abyssal demon", "Standard");

	private DpsResult run(String weaponName, String styleName, PlayerLevels levels)
	{
		ItemDef weapon = TestData.item(weaponName);
		CombatStyle style = TestData.style(weapon, styleName);
		return calc.calculate(CombatInput.builder()
			.loadout(Loadout.of(weapon)).style(style).levels(levels).monster(abyssalDemon).build());
	}

	@Test
	public void meleeWhip()
	{
		assertEquals(1752, run("Abyssal whip", "Flick", PlayerLevels.builder().attack(1).build()).getMaxAttackRoll());
		assertEquals(2, run("Abyssal whip", "Flick", PlayerLevels.builder().strength(1).build()).getMaxHit());
		assertEquals(16060, run("Abyssal whip", "Flick", PlayerLevels.builder().attack(99).build()).getMaxAttackRoll());
		assertEquals(24, run("Abyssal whip", "Flick", PlayerLevels.builder().strength(99).build()).getMaxHit());
	}

	@Test
	public void rangedBowOfFaerdhinen()
	{
		assertEquals(2304, run("Bow of Faerdhinen", "Accurate", PlayerLevels.builder().ranged(1).build()).getMaxAttackRoll());
		assertEquals(3, run("Bow of Faerdhinen", "Accurate", PlayerLevels.builder().ranged(1).build()).getMaxHit());
		assertEquals(21120, run("Bow of Faerdhinen", "Accurate", PlayerLevels.builder().ranged(99).build()).getMaxAttackRoll());
		assertEquals(29, run("Bow of Faerdhinen", "Accurate", PlayerLevels.builder().ranged(99).build()).getMaxHit());
	}

	@Test
	public void magicTridentOfTheSeas()
	{
		assertEquals(948, run("Trident of the Seas", "Accurate", PlayerLevels.builder().magic(1).build()).getMaxAttackRoll());
		assertEquals(1, run("Trident of the Seas", "Accurate", PlayerLevels.builder().magic(1).build()).getMaxHit());
		assertEquals(8690, run("Trident of the Seas", "Accurate", PlayerLevels.builder().magic(99).build()).getMaxAttackRoll());
		assertEquals(28, run("Trident of the Seas", "Accurate", PlayerLevels.builder().magic(99).build()).getMaxHit());
	}

	@Test
	public void accuracyFormulaBranches()
	{
		// attack roll above defence roll
		assertEquals(1 - (102.0 / (2 * 201)), DpsCalculator.normalAccuracy(200, 100), 1e-12);
		// attack roll below defence roll
		assertEquals(100.0 / (2 * 201), DpsCalculator.normalAccuracy(100, 200), 1e-12);
		assertEquals(0.0, DpsCalculator.normalAccuracy(0, 500), 1e-12);
		// rolling twice can never be worse than rolling once
		assertTrue(DpsCalculator.doubleRollAccuracy(5000, 20000) > DpsCalculator.normalAccuracy(5000, 20000));
		assertTrue(DpsCalculator.doubleRollAccuracy(30000, 20000) > DpsCalculator.normalAccuracy(30000, 20000));
	}

	@Test
	public void dpsIsExpectedHitOverAttackInterval()
	{
		DpsResult r = run("Abyssal whip", "Flick", PlayerLevels.maxed());
		assertEquals(4, r.getAttackSpeed());
		assertEquals(r.getExpectedHit() / (4 * 0.6), r.getDps(), 1e-12);
		assertTrue(r.isExact());
	}
}
