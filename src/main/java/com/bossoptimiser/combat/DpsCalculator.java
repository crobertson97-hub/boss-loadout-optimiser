package com.bossoptimiser.combat;

import com.bossoptimiser.combat.EquipmentTotals.AmmoState;
import com.bossoptimiser.data.AttackType;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.data.MonsterDef;
import com.bossoptimiser.data.SpellDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Player-versus-monster damage calculation.
 *
 * <p>The formulas are the publicly documented OSRS combat formulas (OSRS Wiki: "Damage per second",
 * "Maximum hit", "Accuracy" and the individual item pages). The implementation is independent, and is
 * regression-tested against figures produced by the OSRS Wiki DPS calculator
 * (see {@code docs/combat-formulas.md} and {@code docs/testing.md}).
 *
 * <p>All integer arithmetic deliberately truncates at the same points the game does; do not
 * "simplify" the order of operations in this class.
 *
 * <p>Instances are stateless and thread-safe.
 */
public final class DpsCalculator
{
	private static final int[] ZULRAH_IDS = {2042, 2043, 2044};

	private final GameData data;

	public DpsCalculator(GameData data)
	{
		this.data = data;
	}

	public DpsResult calculate(CombatInput in)
	{
		final Loadout loadout = in.getLoadout();
		final CombatStyle style = in.getStyle();
		final MonsterDef monster = in.getMonster();
		final ItemDef weapon = loadout.weapon();
		final List<String> notes = new ArrayList<>();
		boolean exact = true;

		String unsupported = Mechanics.unsupportedReason(loadout, in.getSpell(), monster);
		if (unsupported != null)
		{
			exact = false;
			notes.add(unsupported);
		}

		final EquipmentTotals totals = EquipmentTotals.compute(data, loadout, style, in.getSpell(), in.getBlowpipeDart());
		final int speed = attackSpeed(weapon, style, in.getSpell());
		final AttackType type = style.getType();

		if (totals.ammoState == AmmoState.INVALID && type == AttackType.RANGED)
		{
			return DpsResult.builder().attackSpeed(speed).exact(true)
				.notes(Collections.singletonList("No usable ammunition for " + weapon.getName())).build();
		}
		if (weapon != null && weapon.isDepleted() && (type == AttackType.RANGED || type == AttackType.MAGIC || Mechanics.scythe(loadout)))
		{
			notes.add(weapon.displayName() + " must be charged before use");
		}
		if (weapon != null && Mechanics.isBlowpipe(weapon) && in.getBlowpipeDart() == null)
		{
			exact = false;
			notes.add("Blowpipe dart type is unknown; dart strength is not included");
		}

		final int attackRoll;
		final int[] minMax;
		switch (type.combatClass())
		{
			case MELEE:
				attackRoll = meleeAttackRoll(in, totals);
				minMax = meleeMaxHit(in, totals);
				break;
			case RANGED:
				attackRoll = rangedAttackRoll(in, totals);
				minMax = rangedMaxHit(in, totals);
				break;
			default:
				if (!style.isCast() && powerStaffBase(weapon, 99) < 0)
				{
					return DpsResult.builder().attackSpeed(speed).exact(false)
						.notes(Collections.singletonList("No magic attack is modelled for " + (weapon == null ? "unarmed" : weapon.getName()))).build();
				}
				attackRoll = magicAttackRoll(in, totals);
				minMax = magicMaxHit(in, totals);
				break;
		}
		int min = Math.max(0, minMax[0]);
		int max = Math.max(0, minMax[1]);
		if (min > max)
		{
			max = min;
		}

		final int defenceRoll;
		try
		{
			defenceRoll = defenceRoll(type, weapon, monster);
		}
		catch (IllegalArgumentException ex)
		{
			return DpsResult.builder().attackSpeed(speed).exact(false)
				.notes(Collections.singletonList(ex.getMessage())).build();
		}

		final double accuracy = hitChance(in, attackRoll, defenceRoll);

		List<HitDist> dists = attackerDists(in, totals, accuracy, min, max);

		if (type == AttackType.MAGIC && loadout.wearing("Brimstone ring") && max > 0)
		{
			// 25% of the time the target's magic defence roll is reduced by 10%.
			int reduced = defenceRoll * 9 / 10;
			double effectAccuracy = hitChance(in, attackRoll, reduced);
			List<HitDist> effect = attackerDists(in, totals, effectAccuracy, min, max);
			List<HitDist> mixed = new ArrayList<>();
			for (int i = 0; i < dists.size(); i++)
			{
				HitDist d = dists.get(i).scaleProbability(0.75);
				d.addAll(effect.get(i).scaleProbability(0.25));
				mixed.add(d);
			}
			dists = mixed;
		}

		dists = applyMonsterRules(in, dists);

		double expected = 0;
		int maxHit = 0;
		for (HitDist d : dists)
		{
			expected += d.expected();
			maxHit += d.max();
		}

		return DpsResult.builder()
			.maxAttackRoll(attackRoll)
			.defenceRoll(defenceRoll)
			.accuracy(accuracy)
			.maxHit(maxHit)
			.expectedHit(expected)
			.attackSpeed(speed)
			.dps(expected / speed / DpsResult.SECONDS_PER_TICK)
			.exact(exact)
			.notes(notes)
			.build();
	}

	// ------------------------------------------------------------------ speed

	static int attackSpeed(ItemDef weapon, CombatStyle style, SpellDef spell)
	{
		int speed = weapon == null || weapon.getSpeed() <= 0 ? 4 : weapon.getSpeed();
		if (style.getType() == AttackType.RANGED && CombatStyle.RAPID.equals(style.getStance()))
		{
			speed -= 1;
		}
		else if (style.isCast())
		{
			if (weapon != null && weapon.named("Harmonised Nightmare staff") && spell != null && "standard".equals(spell.getSpellbook()))
			{
				speed = 4;
			}
			else if (weapon != null && weapon.named("Twinflame staff"))
			{
				speed = 6;
			}
			else
			{
				speed = 5;
			}
		}
		return Math.max(speed, 1);
	}

	// ------------------------------------------------------------------ prayers

	private static int applyPrayerAccuracy(CombatInput in, CombatClass cc, int level)
	{
		for (Prayer p : in.getPrayers())
		{
			if (p.getCombatClass() == cc && p.getAccuracy() > 0)
			{
				level = level * p.getAccuracy() / 100;
			}
		}
		return level;
	}

	private static int applyPrayerStrength(CombatInput in, CombatClass cc, int level)
	{
		for (Prayer p : in.getPrayers())
		{
			if (p.getCombatClass() == cc && p.getStrength() > 0)
			{
				if ((p == Prayer.BURST_OF_STRENGTH || p == Prayer.SHARP_EYE) && level <= 20)
				{
					level += 1;
				}
				else
				{
					level = level * p.getStrength() / 100;
				}
			}
		}
		return level;
	}

	// ------------------------------------------------------------------ melee

	private int meleeAttackRoll(CombatInput in, EquipmentTotals totals)
	{
		final Loadout l = in.getLoadout();
		final CombatStyle style = in.getStyle();
		final MonsterDef m = in.getMonster();

		int eff = in.getLevels().getAttack() + in.getAttackBoost();
		eff = applyPrayerAccuracy(in, CombatClass.MELEE, eff);
		eff += 8;
		if (CombatStyle.ACCURATE.equals(style.getStance()))
		{
			eff += 3;
		}
		else if (CombatStyle.CONTROLLED.equals(style.getStance()))
		{
			eff += 1;
		}
		if (Mechanics.meleeVoid(l))
		{
			eff = eff * 11 / 10;
		}

		int roll = eff * (totals.attackBonus(style.getType()) + 64);

		if (l.wearingAny("Salve amulet (e)", "Salve amulet(ei)") && m.isUndead())
		{
			roll = roll * 6 / 5;
		}
		else if (l.wearingAny("Salve amulet", "Salve amulet(i)") && m.isUndead())
		{
			roll = roll * 7 / 6;
		}
		else if (Mechanics.blackMask(l) && m.isSlayerMonster() && in.isOnSlayerTask())
		{
			roll = roll * 7 / 6;
		}

		if (m.isDemon())
		{
			if (l.wearingAny("Arclight", "Emberlight"))
			{
				roll += roll * 70 / 100;
			}
			if (l.wearingAny("Silverlight", "Darklight"))
			{
				roll += roll * 60 / 100;
			}
			if (l.wearingAny("Bone claws", "Burning claws"))
			{
				roll += roll * 5 / 100;
			}
		}
		if (m.isDragon())
		{
			if (l.wearing("Dragon hunter lance"))
			{
				roll = roll * 6 / 5;
			}
			else if (l.wearing("Dragon hunter wand"))
			{
				roll = roll * 7 / 4;
			}
		}
		if (style.getType() == AttackType.CRUSH)
		{
			int inq = Mechanics.inquisitorBonus(l);
			if (inq > 0)
			{
				roll = roll * (200 + inq) / 200;
			}
		}
		return roll;
	}

	private int[] meleeMaxHit(CombatInput in, EquipmentTotals totals)
	{
		final Loadout l = in.getLoadout();
		final CombatStyle style = in.getStyle();
		final MonsterDef m = in.getMonster();

		int eff = in.getLevels().getStrength() + in.getStrengthBoost();
		eff = applyPrayerStrength(in, CombatClass.MELEE, eff);
		eff += 8;
		if (CombatStyle.AGGRESSIVE.equals(style.getStance()))
		{
			eff += 3;
		}
		else if (CombatStyle.CONTROLLED.equals(style.getStance()))
		{
			eff += 1;
		}
		if (Mechanics.meleeVoid(l))
		{
			eff = eff * 11 / 10;
		}

		int max = (eff * (totals.str + 64) + 320) / 640;
		int min = 0;

		if (l.wearingAny("Salve amulet (e)", "Salve amulet(ei)") && m.isUndead())
		{
			max = max * 6 / 5;
		}
		else if (l.wearingAny("Salve amulet", "Salve amulet(i)") && m.isUndead())
		{
			max = max * 7 / 6;
		}
		else if (Mechanics.blackMask(l) && m.isSlayerMonster() && in.isOnSlayerTask())
		{
			max = max * 7 / 6;
		}

		if (m.isDemon() && l.wearingAny("Arclight", "Emberlight"))
		{
			max += max * 70 / 100;
		}
		if (m.isDemon() && l.wearingAny("Bone claws", "Burning claws"))
		{
			max += max * 5 / 100;
		}
		if (m.isDragon() && l.wearing("Dragon hunter lance"))
		{
			max = max * 6 / 5;
		}
		if (m.isDragon() && l.wearing("Dragon hunter wand"))
		{
			max = max * 7 / 5;
		}
		if (m.isDemon() && l.wearingAny("Silverlight", "Darklight", "Silverlight (dyed)"))
		{
			max += max * 60 / 100;
		}
		if (l.wearing("Colossal blade"))
		{
			max += Math.min(m.getSize() * 2, 10);
		}
		if (style.getType() == AttackType.CRUSH)
		{
			int inq = Mechanics.inquisitorBonus(l);
			if (inq > 0)
			{
				max = max * (200 + inq) / 200;
			}
		}
		if (Mechanics.fang(l))
		{
			int shrink = max * 3 / 20;
			min = shrink;
			max -= shrink;
		}
		return new int[]{min, max};
	}

	// ------------------------------------------------------------------ ranged

	private int rangedAttackRoll(CombatInput in, EquipmentTotals totals)
	{
		final Loadout l = in.getLoadout();
		final MonsterDef m = in.getMonster();

		int eff = in.getLevels().getRanged() + in.getRangedBoost();
		eff = applyPrayerAccuracy(in, CombatClass.RANGED, eff);
		if (CombatStyle.ACCURATE.equals(in.getStyle().getStance()))
		{
			eff += 3;
		}
		eff += 8;
		if (Mechanics.rangedVoid(l))
		{
			eff = eff * 11 / 10;
		}

		int roll = eff * (totals.aranged + 64);

		if (Mechanics.crystalBow(l))
		{
			roll = roll * (20 + Mechanics.crystalPieces(l)) / 20;
		}

		if (l.wearing("Salve amulet(ei)") && m.isUndead())
		{
			roll = roll * 6 / 5;
		}
		else if (l.wearing("Salve amulet(i)") && m.isUndead())
		{
			roll = roll * 7 / 6;
		}
		else if (Mechanics.imbuedBlackMask(l) && m.isSlayerMonster() && in.isOnSlayerTask())
		{
			roll = roll * 23 / 20;
		}

		if (l.wearing("Twisted bow"))
		{
			roll = twistedBowScaling(roll, twistedBowMagic(m), true);
		}
		if (l.wearing("Dragon hunter crossbow") && m.isDragon())
		{
			roll = roll * 13 / 10;
		}
		if (l.wearing("Scorching bow") && m.isDemon())
		{
			roll += roll * 30 / 100;
		}
		return roll;
	}

	private int[] rangedMaxHit(CombatInput in, EquipmentTotals totals)
	{
		final Loadout l = in.getLoadout();
		final MonsterDef m = in.getMonster();

		int eff = in.getLevels().getRanged() + in.getRangedBoost();
		eff = applyPrayerStrength(in, CombatClass.RANGED, eff);
		if (CombatStyle.ACCURATE.equals(in.getStyle().getStance()))
		{
			eff += 3;
		}
		eff += 8;
		if (Mechanics.eliteRangedVoid(l))
		{
			eff = eff * 9 / 8;
		}
		else if (Mechanics.rangedVoid(l))
		{
			eff = eff * 11 / 10;
		}

		int max = (eff * (64 + totals.rstr) + 320) / 640;

		if (Mechanics.crystalBow(l))
		{
			max = max * (40 + Mechanics.crystalPieces(l)) / 40;
		}

		boolean needDragonbane = l.wearing("Dragon hunter crossbow") && m.isDragon();
		boolean needDemonbane = l.wearing("Scorching bow") && m.isDemon();

		if (l.wearing("Salve amulet(ei)") && m.isUndead())
		{
			max = max * 6 / 5;
		}
		else if (l.wearing("Salve amulet(i)") && m.isUndead())
		{
			max = max * 7 / 6;
		}
		else if (Mechanics.imbuedBlackMask(l) && m.isSlayerMonster() && in.isOnSlayerTask())
		{
			// Dragonbane and demonbane are additive with the slayer helmet rather than multiplicative.
			int numerator = 23;
			if (needDragonbane)
			{
				needDragonbane = false;
				numerator += 5;
			}
			if (needDemonbane)
			{
				needDemonbane = false;
				numerator += 6;
			}
			max = max * numerator / 20;
		}

		if (l.wearing("Twisted bow"))
		{
			max = twistedBowScaling(max, twistedBowMagic(m), false);
		}
		if (needDragonbane)
		{
			max = max * 5 / 4;
		}
		if (needDemonbane)
		{
			max += max * 30 / 100;
		}
		return new int[]{0, max};
	}

	private static int twistedBowMagic(MonsterDef m)
	{
		int cap = m.hasAttribute("xerician") ? 350 : 250;
		return Math.min(cap, Math.max(m.getMagicLevel(), m.getOffMagic()));
	}

	static int twistedBowScaling(int current, int magic, boolean accuracyMode)
	{
		int factor = accuracyMode ? 10 : 14;
		int base = accuracyMode ? 140 : 250;
		int t2 = (3 * magic - factor) / 100;
		int inner = (3 * magic / 10) - (10 * factor);
		int t3 = inner * inner / 100;
		int bonus = Math.max(0, Math.min(base, base + t2 - t3));
		return current * bonus / 100;
	}

	// ------------------------------------------------------------------ magic

	private int magicAttackRoll(CombatInput in, EquipmentTotals totals)
	{
		final Loadout l = in.getLoadout();
		final MonsterDef m = in.getMonster();
		final SpellDef spell = in.getSpell();

		int eff = in.getLevels().getMagic() + in.getMagicBoost();
		eff = applyPrayerAccuracy(in, CombatClass.MAGIC, eff);
		if (CombatStyle.ACCURATE.equals(in.getStyle().getStance()))
		{
			eff += 2;
		}
		eff += 9;
		if (Mechanics.magicVoid(l))
		{
			eff = eff * 29 / 20;
		}

		final int baseRoll = eff * (totals.amagic + 64);
		int roll = baseRoll;

		int additive = 0;
		boolean blackMask = false;
		if (l.wearing("Salve amulet(ei)") && m.isUndead())
		{
			additive += 20;
		}
		else if (l.wearing("Salve amulet(i)") && m.isUndead())
		{
			additive += 15;
		}
		else if (Mechanics.imbuedBlackMask(l) && m.isSlayerMonster() && in.isOnSlayerTask())
		{
			blackMask = true;
		}
		if (Mechanics.smokeStaff(l) && spell != null && "standard".equals(spell.getSpellbook()))
		{
			additive += 10;
		}
		if (additive != 0)
		{
			roll = roll * (100 + additive) / 100;
		}

		if (m.isDragon())
		{
			if (l.wearing("Dragon hunter crossbow"))
			{
				roll = roll * 13 / 10;
			}
			else if (l.wearing("Dragon hunter lance"))
			{
				roll = roll * 6 / 5;
			}
			else if (l.wearing("Dragon hunter wand"))
			{
				roll = roll * 7 / 4;
			}
		}
		if (blackMask)
		{
			roll = roll * 23 / 20;
		}
		String element = spell == null ? null : spell.getElement();
		if (l.wearing("Tome of Water") && "water".equals(element))
		{
			roll = roll * 6 / 5;
		}
		if (element != null && element.equals(m.getWeaknessElement()))
		{
			roll += baseRoll * m.getWeaknessSeverity() / 100;
		}
		return roll;
	}

	/**
	 * Base max hit of a powered staff's built-in spell for the given visible Magic level,
	 * or -1 when the weapon is not a modelled powered staff.
	 */
	static int powerStaffBase(ItemDef weapon, int magicLevel)
	{
		if (weapon == null)
		{
			return -1;
		}
		switch (weapon.getName())
		{
			case "Trident of the Seas":
			case "Trident of the Seas (e)":
				return Math.max(1, (int) (magicLevel / 3.0 - 5));
			case "Trident of the Swamp":
			case "Trident of the Swamp (e)":
				return Math.max(1, (int) (magicLevel / 3.0 - 2));
			case "Sanguinesti staff":
			case "Holy sanguinesti staff":
				return Math.max(1, magicLevel / 3);
			case "Tumeken's shadow":
				return Math.max(1, magicLevel / 3 + 1);
			case "Eye of Ayak":
				return Math.max(1, magicLevel / 3 - 6);
			case "Warped sceptre":
				return Math.max(1, (8 * magicLevel + 96) / 37);
			default:
				return -1;
		}
	}

	/** Max hit of a spell before any damage bonus; elemental spells scale with the real Magic level. */
	int spellBaseMax(SpellDef spell, int realMagicLevel)
	{
		if (spell.getElement() == null)
		{
			return spell.getMaxHit();
		}
		String cls = spell.spellClass();
		int[] thresholds;
		switch (cls)
		{
			case "Strike":
				thresholds = new int[]{13, 9, 5};
				break;
			case "Bolt":
				thresholds = new int[]{35, 29, 23};
				break;
			case "Blast":
				thresholds = new int[]{59, 53, 47};
				break;
			case "Wave":
				thresholds = new int[]{75, 70, 65};
				break;
			case "Surge":
				thresholds = new int[]{95, 90, 85};
				break;
			default:
				return spell.getMaxHit();
		}
		String tier = realMagicLevel >= thresholds[0] ? "Fire"
			: realMagicLevel >= thresholds[1] ? "Earth"
			: realMagicLevel >= thresholds[2] ? "Water" : "Wind";
		SpellDef scaled = data.spell(tier + " " + cls);
		return scaled == null ? spell.getMaxHit() : scaled.getMaxHit();
	}

	private int[] magicMaxHit(CombatInput in, EquipmentTotals totals)
	{
		final Loadout l = in.getLoadout();
		final MonsterDef m = in.getMonster();
		final SpellDef spell = in.getSpell();
		final ItemDef weapon = l.weapon();
		final int magicLevel = in.getLevels().getMagic() + in.getMagicBoost();

		int max;
		if (in.getStyle().isCast())
		{
			max = spell == null ? 0 : spellBaseMax(spell, in.getLevels().getMagic());
		}
		else
		{
			max = powerStaffBase(weapon, magicLevel);
		}
		if (max <= 0)
		{
			return new int[]{0, 0};
		}

		if (spell != null && l.wearing("Chaos gauntlets") && spell.getName().toLowerCase().contains("bolt"))
		{
			max += 3;
		}
		String element = spell == null ? null : spell.getElement();
		if (element != null && (l.wearing("Elemental amulet") || l.wearing("Amulet of " + element)))
		{
			max += 2;
		}

		final int baseMax = max;
		int dmgBonus = totals.mdmg;
		if (Mechanics.smokeStaff(l) && spell != null && "standard".equals(spell.getSpellbook()))
		{
			dmgBonus += 100;
		}
		boolean blackMask = false;
		if (l.wearing("Salve amulet(ei)") && m.isUndead())
		{
			dmgBonus += 200;
		}
		else if (l.wearing("Salve amulet(i)") && m.isUndead())
		{
			dmgBonus += 150;
		}
		else if (Mechanics.imbuedBlackMask(l) && m.isSlayerMonster() && in.isOnSlayerTask())
		{
			blackMask = true;
		}
		for (Prayer p : in.getPrayers())
		{
			if (p.getCombatClass() == CombatClass.MAGIC)
			{
				dmgBonus += p.getMagicDamage();
			}
		}

		max += max * dmgBonus / 1000;

		if (blackMask)
		{
			max = max * 23 / 20;
		}
		if (m.isDragon())
		{
			if (l.wearing("Dragon hunter lance"))
			{
				max = max * 6 / 5;
			}
			else if (l.wearing("Dragon hunter wand"))
			{
				max = max * 7 / 5;
			}
			else if (l.wearing("Dragon hunter crossbow"))
			{
				max = max * 5 / 4;
			}
		}
		if (element != null && element.equals(m.getWeaknessElement()))
		{
			max += (int) (baseMax * (m.getWeaknessSeverity() / 100.0));
		}
		ItemDef shield = l.get(ItemSlot.SHIELD);
		if (shield != null && element != null && "Charged".equals(shield.getVersion())
			&& ((shield.named("Tome of Fire") && "fire".equals(element))
			|| (shield.named("Tome of Water") && "water".equals(element))
			|| (shield.named("Tome of Earth") && "earth".equals(element))))
		{
			max = max * 11 / 10;
		}
		return new int[]{0, max};
	}

	// ------------------------------------------------------------------ defence / accuracy

	static int defenceRoll(AttackType type, ItemDef weapon, MonsterDef m)
	{
		int level = type == AttackType.MAGIC ? m.getMagicLevel() : m.getDefenceLevel();
		int bonus;
		switch (type)
		{
			case STAB:
				bonus = m.getDefStab();
				break;
			case SLASH:
				bonus = m.getDefSlash();
				break;
			case CRUSH:
				bonus = m.getDefCrush();
				break;
			case MAGIC:
				bonus = m.getDefMagic();
				break;
			default:
				String category = weapon == null ? "" : weapon.getCategory();
				switch (category)
				{
					case "Thrown":
						bonus = m.getDefLight();
						break;
					case "Bow":
						bonus = m.getDefStandard();
						break;
					case "Crossbow":
					case "Chinchompas":
						bonus = m.getDefHeavy();
						break;
					case "Salamander":
						bonus = (m.getDefLight() + m.getDefStandard() + m.getDefHeavy()) / 3;
						break;
					default:
						throw new IllegalArgumentException("Not a modelled ranged weapon category: " + category);
				}
				break;
		}
		return (level + 9) * (bonus + 64);
	}

	static double normalAccuracy(int atk, int def)
	{
		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}
		if (atk >= 0 && def >= 0)
		{
			return stdRoll(atk, def);
		}
		if (atk >= 0)
		{
			return 1 - 1.0 / (-def + 1) / (atk + 1);
		}
		if (def >= 0)
		{
			return 0;
		}
		return stdRoll(-def, -atk);
	}

	private static double stdRoll(double attack, double defence)
	{
		return attack > defence
			? 1 - ((defence + 2) / (2 * (attack + 1)))
			: attack / (2 * (defence + 1));
	}

	/** Accuracy when the attack roll is made twice and the better result is kept (Osmumten's fang). */
	static double doubleRollAccuracy(int atkRoll, int defRoll)
	{
		if (atkRoll < 0 || defRoll < 0)
		{
			double single = normalAccuracy(atkRoll, defRoll);
			return 1 - (1 - single) * (1 - single);
		}
		double a = atkRoll;
		double d = defRoll;
		return a > d
			? 1 - (d + 2) * (2 * d + 3) / (a + 1) / (a + 1) / 6
			: a * (4 * a + 5) / 6 / (a + 1) / (d + 1);
	}

	private static double hitChance(CombatInput in, int attackRoll, int defenceRoll)
	{
		final Loadout l = in.getLoadout();
		final AttackType type = in.getStyle().getType();
		double chance = normalAccuracy(attackRoll, defenceRoll);
		if (Mechanics.fang(l) && type == AttackType.STAB)
		{
			chance = doubleRollAccuracy(attackRoll, defenceRoll);
		}
		ItemDef weapon = l.weapon();
		if (l.wearing("Confliction gauntlets") && type == AttackType.MAGIC && (weapon == null || !weapon.isTwoHanded()))
		{
			double single = normalAccuracy(attackRoll, defenceRoll);
			double twice = doubleRollAccuracy(attackRoll, defenceRoll);
			chance = twice / (1 + twice - single);
		}
		return chance;
	}

	// ------------------------------------------------------------------ hit distributions

	private List<HitDist> attackerDists(CombatInput in, EquipmentTotals totals, double acc, int min, int max)
	{
		final Loadout l = in.getLoadout();
		final MonsterDef m = in.getMonster();
		final AttackType type = in.getStyle().getType();
		final ItemDef weapon = l.weapon();
		List<HitDist> dists = new ArrayList<>();

		if (max == 0)
		{
			dists.add(HitDist.miss());
			return dists;
		}

		final HitDist standard = HitDist.linear(acc, min, max);
		dists.add(standard);

		if (type == AttackType.RANGED && l.wearing("Dark bow"))
		{
			dists.add(HitDist.linear(acc, min, max));
		}

		if (type.isMelee() && Mechanics.scythe(l))
		{
			dists.clear();
			int hits = Math.min(Math.max(m.getSize(), 1), 3);
			for (int i = 0; i < hits; i++)
			{
				int splatMax = max >> i;
				dists.add(HitDist.linear(acc, min, Math.max(min, splatMax)));
			}
		}

		if (type.isMelee() && Mechanics.twoHitWeapon(l))
		{
			dists.clear();
			int first = max / 2;
			int second = max - first;
			dists.add(HitDist.linear(acc, min, Math.max(min, first)));
			dists.add(HitDist.linear(acc, min, Math.max(min, second)));
		}

		if (l.wearingAny("Sanguinesti staff", "Holy sanguinesti staff") && type == AttackType.MAGIC)
		{
			transformAll(dists, (d, accurate, p, out) -> {
				out.add(d, accurate, p * 0.8);
				out.add(d + 8, accurate, p * 0.2);
			}, false);
		}

		final boolean crossbow = type == AttackType.RANGED && weapon != null && "Crossbow".equals(weapon.getCategory());
		final boolean zcb = l.wearing("Zaryte crossbow");
		final double kandarin = in.isKandarinHardDiary() ? 1.1 : 1.0;
		final int rangedLevel = in.getLevels().getRanged() + in.getRangedBoost();
		ItemDef ammo = l.get(ItemSlot.AMMO);
		String ammoName = ammo == null ? "" : ammo.getName();

		if (crossbow)
		{
			final int finalMax = max;
			switch (ammoName)
			{
				case "Opal bolts (e)":
				case "Opal dragon bolts (e)":
				{
					double chance = 0.05 * kandarin;
					int bonus = rangedLevel / (zcb ? 9 : 10);
					transformAll(dists, bonusDamage(chance, bonus, false), true);
					break;
				}
				case "Pearl bolts (e)":
				case "Pearl dragon bolts (e)":
				{
					double chance = 0.06 * kandarin;
					int divisor = m.hasAttribute("fiery") ? 15 : 20;
					int bonus = rangedLevel / (zcb ? divisor - 2 : divisor);
					transformAll(dists, bonusDamage(chance, bonus, false), true);
					break;
				}
				case "Diamond bolts (e)":
				case "Diamond dragon bolts (e)":
				{
					double chance = 0.1 * kandarin;
					int effectMax = finalMax * (zcb ? 126 : 115) / 100;
					transformAll(dists, (d, accurate, p, out) -> {
						double each = p * chance / (effectMax + 1);
						for (int i = 0; i <= effectMax; i++)
						{
							out.add(i, true, each);
						}
						out.add(d, accurate, p * (1 - chance));
					}, true);
					break;
				}
				case "Dragonstone bolts (e)":
				case "Dragonstone dragon bolts (e)":
				{
					if (!m.hasAttribute("fiery") && !m.isDragon())
					{
						double chance = 0.06 * kandarin;
						int bonus = rangedLevel * 2 / (zcb ? 9 : 10);
						transformAll(dists, bonusDamage(chance, bonus, true), true);
					}
					break;
				}
				case "Onyx bolts (e)":
				case "Onyx dragon bolts (e)":
				{
					if (!m.isUndead())
					{
						double chance = 0.11 * kandarin;
						int effectMax = finalMax * (zcb ? 132 : 120) / 100;
						transformAll(dists, (d, accurate, p, out) -> {
							double each = p * chance / (effectMax + 1);
							for (int i = 0; i <= effectMax; i++)
							{
								out.add(i, true, each);
							}
							out.add(d, true, p * (1 - chance));
						}, false);
					}
					break;
				}
				default:
					break;
			}
		}

		// A hit that passes the accuracy roll always deals at least 1.
		for (int i = 0; i < dists.size(); i++)
		{
			dists.set(i, dists.get(i).accurateZeroToOne());
		}

		if (crossbow && ("Ruby bolts (e)".equals(ammoName) || "Ruby dragon bolts (e)".equals(ammoName))
			&& in.getLevels().getHitpoints() >= 10)
		{
			double chance = 0.06 * kandarin;
			int cap = zcb ? 110 : 100;
			int currentHp = in.getMonsterCurrentHp() > 0 ? Math.min(in.getMonsterCurrentHp(), m.getHitpoints()) : m.getHitpoints();
			int effect = Math.min(cap, currentHp * (zcb ? 22 : 20) / 100);
			transformAll(dists, (d, accurate, p, out) -> {
				out.add(effect, true, p * chance);
				out.add(d, accurate, p * (1 - chance));
			}, true);
		}
		return dists;
	}

	private static HitDist.Transform bonusDamage(double chance, int bonus, boolean accurateOnly)
	{
		return (d, accurate, p, out) -> {
			if (!accurate && accurateOnly)
			{
				out.add(d, false, p);
				return;
			}
			out.add(d + bonus, accurate, p * chance);
			out.add(d, accurate, p * (1 - chance));
		};
	}

	private static void transformAll(List<HitDist> dists, HitDist.Transform t, boolean transformInaccurate)
	{
		for (int i = 0; i < dists.size(); i++)
		{
			dists.set(i, dists.get(i).transform(t, transformInaccurate));
		}
	}

	// ------------------------------------------------------------------ monster rules

	private static boolean isZulrah(MonsterDef m)
	{
		for (int id : ZULRAH_IDS)
		{
			if (m.getId() == id)
			{
				return true;
			}
		}
		return false;
	}

	/** True when the monster cannot be damaged by this kind of attack at all. */
	public static boolean isImmune(MonsterDef m, AttackType type, ItemDef weapon)
	{
		String category = weapon == null ? "" : weapon.getCategory();
		if (type.isMelee())
		{
			if (isZulrah(m))
			{
				return !"Polearm".equals(category);
			}
			if (m.hasAttribute("flying"))
			{
				return !"Polearm".equals(category) && !"Salamander".equals(category);
			}
		}
		return false;
	}

	private List<HitDist> applyMonsterRules(CombatInput in, List<HitDist> dists)
	{
		final MonsterDef m = in.getMonster();
		final AttackType type = in.getStyle().getType();
		if (isImmune(m, type, in.getLoadout().weapon()))
		{
			List<HitDist> out = new ArrayList<>();
			for (int i = 0; i < dists.size(); i++)
			{
				out.add(HitDist.miss());
			}
			return out;
		}
		if ("Zulrah".equals(m.getName()))
		{
			// Hits above 50 are re-rolled to a uniform 45-50.
			transformAll(dists, (d, accurate, p, out) -> {
				if (d <= 50)
				{
					out.add(d, accurate, p);
					return;
				}
				for (int i = 0; i <= 5; i++)
				{
					out.add(45 + i, accurate, p / 6);
				}
			}, true);
		}
		if (m.getFlatArmour() != 0 && type != AttackType.MAGIC)
		{
			final int armour = m.getFlatArmour();
			transformAll(dists, (d, accurate, p, out) -> out.add(Math.max(0, d - armour), accurate, p), false);
		}
		return dists;
	}
}
