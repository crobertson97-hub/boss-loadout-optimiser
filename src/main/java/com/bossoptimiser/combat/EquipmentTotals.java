package com.bossoptimiser.combat;

import com.bossoptimiser.data.AttackType;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.data.SpellDef;
import java.util.Set;

/**
 * Summed equipment bonuses for a loadout, including the adjustments the game applies to the
 * visible totals (ammunition that does not fit the weapon, blowpipe darts, Tumeken's shadow,
 * Virtus robes with ancient spells, elite void mage, Dizana's quiver).
 */
public final class EquipmentTotals
{
	public enum AmmoState
	{
		/** Weapon does not use the ammunition slot. */
		NOT_NEEDED,
		/** Ammunition slot item fits the weapon and contributes its bonuses. */
		VALID,
		/** Weapon needs ammunition and the slot is empty or holds the wrong type. */
		INVALID
	}

	public int astab;
	public int aslash;
	public int acrush;
	public int amagic;
	public int aranged;
	public int dstab;
	public int dslash;
	public int dcrush;
	public int dmagic;
	public int dranged;
	public int str;
	public int rstr;
	public int mdmg;
	public int prayer;
	public AmmoState ammoState = AmmoState.NOT_NEEDED;

	public static AmmoState ammoState(GameData data, Loadout loadout)
	{
		ItemDef weapon = loadout.weapon();
		Set<Integer> allowed = data.ammoFor(weapon);
		if (allowed == null)
		{
			return AmmoState.NOT_NEEDED;
		}
		ItemDef ammo = loadout.get(ItemSlot.AMMO);
		return ammo != null && allowed.contains(ammo.getId()) ? AmmoState.VALID : AmmoState.INVALID;
	}

	public static EquipmentTotals compute(GameData data, Loadout loadout, CombatStyle style, SpellDef spell, ItemDef blowpipeDart)
	{
		EquipmentTotals t = new EquipmentTotals();
		t.ammoState = ammoState(data, loadout);
		ItemDef weapon = loadout.weapon();

		for (ItemDef piece : loadout.asMap().values())
		{
			boolean applyRanged = piece.getSlot() != ItemSlot.AMMO || t.ammoState == AmmoState.VALID;
			t.astab += piece.getAstab();
			t.aslash += piece.getAslash();
			t.acrush += piece.getAcrush();
			t.amagic += piece.getAmagic();
			t.dstab += piece.getDstab();
			t.dslash += piece.getDslash();
			t.dcrush += piece.getDcrush();
			t.dmagic += piece.getDmagic();
			t.dranged += piece.getDranged();
			t.str += piece.getStr();
			t.mdmg += piece.getMdmg();
			t.prayer += piece.getPrayer();
			if (applyRanged)
			{
				t.aranged += piece.getAranged();
				t.rstr += piece.getRstr();
			}
		}

		if (weapon != null && Mechanics.isBlowpipe(weapon) && blowpipeDart != null)
		{
			t.rstr += blowpipeDart.getRstr();
		}

		if (weapon != null && weapon.named("Tumeken's shadow"))
		{
			t.mdmg = Math.min(1000, t.mdmg * 3);
			t.amagic *= 3;
		}

		boolean casting = style != null && style.isCast();
		if (spell != null && "ancient".equals(spell.getSpellbook()) && casting)
		{
			for (ItemSlot s : new ItemSlot[]{ItemSlot.HEAD, ItemSlot.BODY, ItemSlot.LEGS})
			{
				ItemDef d = loadout.get(s);
				if (d != null && d.nameContains("Virtus"))
				{
					t.mdmg += 30;
				}
			}
		}

		if (named(loadout, ItemSlot.HEAD, "Void mage helm") && named(loadout, ItemSlot.BODY, "Elite void top")
			&& named(loadout, ItemSlot.LEGS, "Elite void robe") && named(loadout, ItemSlot.HANDS, "Void knight gloves"))
		{
			t.mdmg += 50;
		}

		ItemDef cape = loadout.get(ItemSlot.CAPE);
		boolean quiverCharged = cape != null && (cape.named("Dizana's max cape") || cape.named("Blessed Dizana's quiver")
			|| (cape.named("Dizana's quiver") && "Charged".equals(cape.getVersion())));
		boolean firesQuiverAmmo = weapon != null
			&& ("Bow".equals(weapon.getCategory()) || "Crossbow".equals(weapon.getCategory()))
			&& !weapon.named("Heavy ballista") && !weapon.named("Light ballista") && !weapon.named("Eclipse atlatl");
		if (quiverCharged && firesQuiverAmmo && t.ammoState == AmmoState.VALID)
		{
			t.aranged += 10;
			t.rstr += 1;
		}
		return t;
	}

	private static boolean named(Loadout loadout, ItemSlot slot, String name)
	{
		ItemDef d = loadout.get(slot);
		return d != null && d.named(name);
	}

	public int attackBonus(AttackType type)
	{
		switch (type)
		{
			case STAB:
				return astab;
			case SLASH:
				return aslash;
			case CRUSH:
				return acrush;
			case MAGIC:
				return amagic;
			default:
				return aranged;
		}
	}
}
