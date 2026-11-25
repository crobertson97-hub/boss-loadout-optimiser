package com.bossoptimiser.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.TestData;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.MonsterDef;
import java.util.Map;
import org.junit.Test;

/** Behaviour of the engine around its edges: assumptions, unsupported mechanics, immunity. */
public class MechanicsTest
{
	private final GameData data = TestData.data();
	private final DpsCalculator calc = new DpsCalculator(data);
	private final MonsterDef vorkath = data.monster(8059);
	private final MonsterDef graardor = data.monster(2215);
	private final MonsterDef zulrah = data.monster(2042);

	private DpsResult run(Loadout loadout, String style, MonsterDef monster, CombatAssumptions assumptions)
	{
		CombatStyle s = TestData.style(loadout.weapon(), style);
		return calc.calculate(CombatInput.of(loadout, s, null, PlayerLevels.maxed(), assumptions, null, monster));
	}

	@Test
	public void potionBoostFormulas()
	{
		assertEquals(19, Potion.SUPER_COMBAT.attackBoost(99));
		assertEquals(19, Potion.SUPER_COMBAT.strengthBoost(99));
		assertEquals(13, Potion.RANGING.rangedBoost(99));
		assertEquals(11, Potion.RANGING.rangedBoost(75));
		assertEquals(13, Potion.SATURATED_HEART.magicBoost(99));
		assertEquals(10, Potion.IMBUED_HEART.magicBoost(99));
		assertEquals(4, Potion.MAGIC.magicBoost(80));
		assertEquals(0, Potion.RANGING.attackBoost(99));
		assertEquals(0, Potion.NONE.strengthBoost(99));
		assertEquals(12, Potion.ATTACK_STRENGTH.attackBoost(99));
	}

	@Test
	public void bestPrayersRespectPrayerLevelAndUnlocks()
	{
		Map<CombatClass, Prayer> max = CombatAssumptions.bestPrayers(99, true);
		assertEquals(Prayer.PIETY, max.get(CombatClass.MELEE));
		assertEquals(Prayer.RIGOUR, max.get(CombatClass.RANGED));
		assertEquals(Prayer.AUGURY, max.get(CombatClass.MAGIC));
		Map<CombatClass, Prayer> mid = CombatAssumptions.bestPrayers(50, true);
		assertNull("No combined melee prayer below 60 Prayer", mid.get(CombatClass.MELEE));
		assertEquals(Prayer.EAGLE_EYE, mid.get(CombatClass.RANGED));
		assertEquals(Prayer.MYSTIC_MIGHT, mid.get(CombatClass.MAGIC));
		assertEquals(Prayer.EAGLE_EYE, CombatAssumptions.bestPrayers(99, false).get(CombatClass.RANGED));
	}

	@Test
	public void assumptionsChangeTheResult()
	{
		Loadout whip = Loadout.of(TestData.item("Abyssal whip"));
		CombatAssumptions none = CombatAssumptions.builder().meleePotion(Potion.NONE).build();
		CombatAssumptions boosted = CombatAssumptions.builder().meleePotion(Potion.SUPER_COMBAT).meleePrayer(Prayer.PIETY).build();
		assertTrue(run(whip, "Lash", graardor, boosted).getDps() > run(whip, "Lash", graardor, none).getDps());
		assertTrue(run(whip, "Lash", graardor, boosted).getMaxHit() > run(whip, "Lash", graardor, none).getMaxHit());
	}

	@Test
	public void slayerTaskOnlyMattersWithAHelmetAndAFlag()
	{
		Loadout helm = Loadout.of(TestData.item("Abyssal whip"), TestData.item("Slayer helmet (i)"));
		CombatAssumptions off = CombatAssumptions.builder().build();
		CombatAssumptions on = CombatAssumptions.builder().onSlayerTask(true).build();
		assertTrue(run(helm, "Lash", graardor, on).getDps() > run(helm, "Lash", graardor, off).getDps());
		Loadout plain = Loadout.of(TestData.item("Abyssal whip"));
		assertEquals(run(plain, "Lash", graardor, off).getDps(), run(plain, "Lash", graardor, on).getDps(), 0);
	}

	@Test
	public void meleeCannotDamageZulrahUnlessItIsAHalberd()
	{
		Loadout whip = Loadout.of(TestData.item("Abyssal whip"));
		assertEquals(0, run(whip, "Lash", zulrah, CombatAssumptions.builder().build()).getDps(), 0);
		assertTrue(DpsCalculator.isImmune(zulrah, TestData.style(whip.weapon(), "Lash").getType(), whip.weapon()));
		ItemDef halberd = TestData.item("Noxious halberd");
		assertFalse(DpsCalculator.isImmune(zulrah, TestData.style(halberd, "Swipe").getType(), halberd));
		assertTrue(run(Loadout.of(halberd), "Swipe", zulrah, CombatAssumptions.builder().build()).getDps() > 0);
	}

	@Test
	public void rangedWeaponWithoutUsableAmmunitionDealsNothing()
	{
		Loadout noAmmo = Loadout.of(TestData.item("Twisted bow"));
		DpsResult r = run(noAmmo, "Rapid", graardor, CombatAssumptions.builder().build());
		assertEquals(0, r.getDps(), 0);
		assertTrue(r.getNotes().toString(), r.getNotes().get(0).contains("ammunition"));
		Loadout wrongAmmo = Loadout.of(TestData.item("Twisted bow"), TestData.item("Diamond bolts (e)"));
		assertEquals(0, run(wrongAmmo, "Rapid", graardor, CombatAssumptions.builder().build()).getDps(), 0);
		Loadout ok = Loadout.of(TestData.item("Twisted bow"), TestData.item("Dragon arrow"));
		assertTrue(run(ok, "Rapid", graardor, CombatAssumptions.builder().build()).getDps() > 0);
	}

	@Test
	public void blowpipeWithoutKnownDartIsNotPresentedAsExact()
	{
		Loadout pipe = Loadout.of(TestData.item("Toxic blowpipe"));
		DpsResult r = run(pipe, "Rapid", graardor, CombatAssumptions.builder().build());
		assertFalse(r.isExact());
		assertTrue(r.getNotes().toString().contains("dart"));
		CombatStyle rapid = TestData.style(pipe.weapon(), "Rapid");
		DpsResult withDart = calc.calculate(CombatInput.of(pipe, rapid, null, PlayerLevels.maxed(),
			CombatAssumptions.builder().build(), TestData.item("Dragon dart"), graardor));
		assertTrue(withDart.isExact());
		assertTrue(withDart.getDps() > r.getDps());
	}

	@Test
	public void unmodelledMechanicsAreFlaggedInsteadOfSilentlyApproximated()
	{
		Loadout dharok = Loadout.of(TestData.item("Dharok's greataxe"), TestData.item("Dharok's helm"),
			TestData.item("Dharok's platebody"), TestData.item("Dharok's platelegs"));
		DpsResult r = run(dharok, "Hack", graardor, CombatAssumptions.builder().build());
		assertFalse(r.isExact());
		assertTrue(r.getNotes().toString().contains("Barrows"));

		Loadout ralos = Loadout.of(TestData.item("Tonalztics of Ralos"));
		assertFalse(calc.calculate(CombatInput.of(ralos, data.stylesFor(ralos.weapon()).get(0), null, PlayerLevels.maxed(),
			CombatAssumptions.builder().build(), null, graardor)).isExact());

		Loadout whip = Loadout.of(TestData.item("Abyssal whip"));
		DpsResult demon = run(whip, "Lash", data.monster("Tormented Demon", null), CombatAssumptions.builder().build());
		assertFalse("Tormented Demon's shield is not modelled", demon.isExact());
		// The Corporeal Beast's damage halving is modelled (reference scenarios x-corp-*), so its figures are exact.
		assertTrue(run(whip, "Lash", data.monster("Corporeal Beast", null), CombatAssumptions.builder().build()).isExact());
		assertNull(Mechanics.monsterUnsupportedReason(vorkath));
	}

	@Test
	public void twoHandedWeaponTotalsIgnoreNothingButAmmoRules()
	{
		Loadout l = Loadout.of(TestData.item("Twisted bow"), TestData.item("Dragon arrow"), TestData.item("Amulet of fury"));
		EquipmentTotals t = EquipmentTotals.compute(data, l, TestData.style(l.weapon(), "Rapid"), null, null);
		assertEquals(EquipmentTotals.AmmoState.VALID, t.ammoState);
		assertEquals(70 + 10, t.aranged);
		assertEquals(20 + 60, t.rstr);
		// Ammunition in the slot of a weapon that does not fire it contributes no ranged stats.
		Loadout pipe = Loadout.of(TestData.item("Toxic blowpipe"), TestData.item("Dragon arrow"));
		EquipmentTotals p = EquipmentTotals.compute(data, pipe, TestData.style(pipe.weapon(), "Rapid"), null, null);
		assertEquals(EquipmentTotals.AmmoState.NOT_NEEDED, p.ammoState);
		assertEquals(TestData.item("Toxic blowpipe").getRstr(), p.rstr);
	}

	@Test
	public void hitDistributionsAreProperProbabilityDistributions()
	{
		HitDist d = HitDist.linear(0.75, 0, 40);
		assertEquals(1.0, d.totalProbability(), 1e-12);
		assertEquals(0.75 * 20, d.expected(), 1e-12);
		assertEquals(40, d.max());
		HitDist capped = d.transform((dmg, acc, p, out) -> out.add(Math.min(dmg, 10), acc, p));
		assertEquals(1.0, capped.totalProbability(), 1e-12);
		assertEquals(10, capped.max());
		assertEquals(0, HitDist.miss().expected(), 0);
	}

	@Test
	public void loadoutHelpers()
	{
		Loadout a = Loadout.of(TestData.item("Abyssal whip"), TestData.item("Dragon defender"));
		Loadout b = a.with(com.bossoptimiser.data.ItemSlot.SHIELD, null);
		assertEquals(2, a.size());
		assertEquals(1, b.size());
		assertTrue(a.wearing("Dragon defender"));
		assertFalse(b.wearing("Dragon defender"));
		assertEquals(1, a.differingSlots(b));
		assertEquals(0, b.differingSlots(a));
	}
}
