package com.bossoptimiser.combat;

import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.data.MonsterDef;
import com.bossoptimiser.data.SpellDef;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Item-set detection and the registry of mechanics the engine does <em>not</em> model.
 * Anything listed as unsupported is reported back to the caller so it can be excluded from
 * exact ranking instead of being silently approximated.
 */
public final class Mechanics
{
	private Mechanics()
	{
	}

	/** Weapons whose passive effects, hit patterns or situational scaling are not modelled. */
	private static final Set<String> UNSUPPORTED_WEAPONS = new HashSet<>(Arrays.asList(
		"Tonalztics of Ralos", "Dual macuahuitl", "Eclipse atlatl", "Hunter's spear", "Dinh's bulwark",
		"Dinh's blazing bulwark", "Obligator", "Soulreaper axe", "Gadderhammer", "Holy water",
		"Dawnbringer", "Twinflame staff", "Bone mace", "Bone shortbow", "Bone staff", "Crimson kisten",
		"Sunspear", "Blisterwood flail", "Blisterwood sickle", "Hallowed flail", "Ivandis flail", "Rod of Ivandis",
		"Magic secateurs", "Tzhaar-ket-em", "Tzhaar-ket-om", "Tzhaar-ket-om (t)", "Toktz-xil-ak", "Toktz-xil-ek",
		"Toktz-mej-tal", "Zorya's tome", "Starter staff", "Starter sword", "Starter bow",
		"Keris", "Keris partisan", "Keris partisan of breaching", "Keris partisan of corruption",
		"Keris partisan of the sun", "Keris partisan of amascut", "Comp ogre bow", "Ogre bow",
		"Swamp lizard", "Orange salamander", "Red salamander", "Black salamander", "Tecu salamander",
		"Thammaron's sceptre", "Accursed sceptre", "Thammaron's sceptre (a)", "Accursed sceptre (a)",
		"Craw's bow", "Webweaver bow", "Viggora's chainmace", "Ursine chainmace", "Ascension crossbows",
		"Barronite mace", "Granite hammer", "Leaf-bladed battleaxe", "Arkan blade"));

	/** Weapon categories whose damage depends on inputs the plugin does not have. */
	private static final Set<String> UNSUPPORTED_CATEGORIES = new HashSet<>(Arrays.asList(
		"Chinchompas", "Salamander", "Blaster", "Gun", "Bulwark", "Powered Book"));

	/** Monsters with special damage rules that are not modelled (other than the supported bosses). */
	private static final Set<String> SPECIAL_RULE_MONSTERS = new HashSet<>(Arrays.asList(
		"Corporeal Beast", "Kraken", "Cave kraken", "Verzik Vitur", "Tekton", "Tekton (enraged)", "Great Olm",
		"Ice demon", "Tormented Demon", "Fragment of Seren", "Slagilith", "Zogre", "Skogre", "Slash Bash",
		"The Hueycoatl", "Abyssal Sire", "Duke Sucellus", "Yama", "Dusk", "Dawn", "Respiratory system",
		"Fire Warrior of Lesarkus", "Fareed", "Scurrius", "Giant rat (Scurrius)", "Tanglefoot",
		"Doom of Mokhaiotl", "Araxxor", "Acidic Araxyte", "Mirrorback Araxyte", "Glowing crystal",
		"Guardian (Chambers of Xeric)", "Nightmare", "Totem", "Maggot King", "Eclipse Moon", "Blood Moon",
		"Blue Moon", "Branda the Fire Queen", "Eldric the Ice King", "Royal Titans"));

	public static boolean isBlowpipe(ItemDef weapon)
	{
		return weapon.named("Toxic blowpipe") || weapon.named("Blazing blowpipe");
	}

	static boolean voidRobes(Loadout l)
	{
		return l.wearingAny("Void knight top", "Void knight top (or)", "Elite void top", "Elite void top (or)")
			&& l.wearingAny("Void knight robe", "Void knight robe (or)", "Elite void robe", "Elite void robe (or)")
			&& l.wearing("Void knight gloves");
	}

	static boolean eliteVoidRobes(Loadout l)
	{
		return l.wearingAny("Elite void top", "Elite void top (or)")
			&& l.wearingAny("Elite void robe", "Elite void robe (or)")
			&& l.wearing("Void knight gloves");
	}

	static boolean meleeVoid(Loadout l)
	{
		return voidRobes(l) && l.wearingAny("Void melee helm", "Void melee helm (or)");
	}

	static boolean rangedVoid(Loadout l)
	{
		return voidRobes(l) && l.wearingAny("Void ranger helm", "Void ranger helm (or)");
	}

	static boolean eliteRangedVoid(Loadout l)
	{
		return eliteVoidRobes(l) && l.wearingAny("Void ranger helm", "Void ranger helm (or)");
	}

	static boolean magicVoid(Loadout l)
	{
		return voidRobes(l) && l.wearingAny("Void mage helm", "Void mage helm (or)");
	}

	static boolean imbuedBlackMask(Loadout l)
	{
		return l.wearingAny("Black mask (i)", "Slayer helmet (i)", "V's helm");
	}

	static boolean blackMask(Loadout l)
	{
		return imbuedBlackMask(l) || l.wearingAny("Black mask", "Slayer helmet");
	}

	static boolean smokeStaff(Loadout l)
	{
		return l.wearingAny("Smoke battlestaff", "Mystic smoke staff", "Twinflame staff");
	}

	static boolean crystalBow(Loadout l)
	{
		ItemDef w = l.weapon();
		return w != null && (w.named("Crystal bow") || w.nameContains("Bow of Faerdhinen"));
	}

	static int crystalPieces(Loadout l)
	{
		return (l.wearing("Crystal helm") ? 1 : 0) + (l.wearing("Crystal legs") ? 2 : 0) + (l.wearing("Crystal body") ? 3 : 0);
	}

	static boolean fang(Loadout l)
	{
		return l.wearingAny("Osmumten's fang", "Osmumten's fang (or)");
	}

	static boolean scythe(Loadout l)
	{
		ItemDef w = l.weapon();
		return w != null && (w.nameContains("of vitur") || w.nameContains("of Vitur"));
	}

	static boolean twoHitWeapon(Loadout l)
	{
		return l.wearingAny("Torag's hammers", "Sulphur blades", "Glacial temotli", "Earthbound tecpatl");
	}

	static int inquisitorBonus(Loadout l)
	{
		return (l.wearing("Inquisitor's great helm") ? 1 : 0)
			+ (l.wearing("Inquisitor's hauberk") ? 2 : 0)
			+ (l.wearing("Inquisitor's plateskirt") ? 2 : 0);
	}

	private static boolean barrowsSetWithEffect(Loadout l)
	{
		return l.wearingAll("Dharok's helm", "Dharok's platebody", "Dharok's platelegs", "Dharok's greataxe")
			|| l.wearingAll("Verac's helm", "Verac's brassard", "Verac's plateskirt", "Verac's flail")
			|| l.wearingAll("Karil's coif", "Karil's leathertop", "Karil's leatherskirt", "Karil's crossbow", "Amulet of the Damned")
			|| l.wearingAll("Ahrim's staff", "Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt", "Amulet of the Damned");
	}

	/**
	 * @return a human-readable reason when the loadout uses a mechanic the engine does not model,
	 * otherwise null. A non-null answer means the DPS figure must not be presented as exact.
	 */
	public static String unsupportedReason(Loadout loadout, SpellDef spell, MonsterDef monster)
	{
		ItemDef weapon = loadout.weapon();
		if (weapon != null)
		{
			if (UNSUPPORTED_WEAPONS.contains(weapon.getName()))
			{
				return weapon.getName() + " has effects that are not modelled";
			}
			if (UNSUPPORTED_CATEGORIES.contains(weapon.getCategory()))
			{
				return weapon.getCategory() + " weapons are not modelled";
			}
			if (weapon.nameContains("(basic)") || weapon.nameContains("(attuned)") || weapon.nameContains("(perfected)"))
			{
				return "Gauntlet equipment is not modelled";
			}
		}
		if (barrowsSetWithEffect(loadout))
		{
			return "Barrows set effects are not modelled";
		}
		if (loadout.wearingAll("Obsidian helmet", "Obsidian platelegs", "Obsidian platebody"))
		{
			return "The obsidian armour set effect is not modelled";
		}
		if (loadout.wearing("Crystal blessing") || loadout.wearing("Amulet of avarice") || loadout.wearing("Efaritay's aid"))
		{
			ItemDef ammo = loadout.get(ItemSlot.AMMO);
			ItemDef neck = loadout.get(ItemSlot.NECK);
			ItemDef ring = loadout.get(ItemSlot.RING);
			ItemDef culprit = ammo != null && ammo.named("Crystal blessing") ? ammo
				: neck != null && neck.named("Amulet of avarice") ? neck : ring;
			return (culprit == null ? "An equipped item" : culprit.getName()) + " has effects that are not modelled";
		}
		if (spell != null)
		{
			if ("arceuus".equals(spell.getSpellbook()))
			{
				return "Arceuus spells are not modelled";
			}
			switch (spell.getName())
			{
				case "Saradomin Strike":
				case "Claws of Guthix":
				case "Flames of Zamorak":
				case "Magic Dart":
				case "Iban Blast":
				case "Crumble Undead":
					return spell.getName() + " is not modelled";
				default:
					break;
			}
		}
		return monsterUnsupportedReason(monster);
	}

	public static String monsterUnsupportedReason(MonsterDef monster)
	{
		if (monster == null)
		{
			return null;
		}
		if (SPECIAL_RULE_MONSTERS.contains(monster.getName()))
		{
			return monster.getName() + " has special damage rules that are not modelled";
		}
		for (String attr : monster.getAttributes())
		{
			if (attr.startsWith("vampyre") || "leafy".equals(attr) || "rat".equals(attr) || "kalphite".equals(attr)
				|| "shade".equals(attr) || "golem".equals(attr) || "xerician".equals(attr))
			{
				return "Monsters with the '" + attr + "' attribute have rules that are not modelled";
			}
		}
		return null;
	}
}
