#!/usr/bin/env node
/*
 * Generates src/main/resources/com/bossoptimiser/items/supplies.json.
 *
 * Supply item ids are NOT typed in by hand: every entry below names a constant of RuneLite's
 * net.runelite.api.ItemID and this script resolves the numeric id from the RuneLite source, so a
 * misspelt or non-existent item fails the generation instead of shipping a wrong id.
 * SuppliesDataTest re-checks every id against the RuneLite API jar at test time.
 *
 * Usage: node scripts/gen-supplies.mjs <path-to-runelite-checkout>
 * Heal / restore figures are the standard published values (OSRS Wiki item pages).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runelite = process.argv[2];
if (!runelite) {
	console.error('usage: node scripts/gen-supplies.mjs <path-to-runelite-checkout>');
	process.exit(1);
}
const src = fs.readFileSync(path.join(runelite, 'runelite-api/src/main/java/net/runelite/api/ItemID.java'), 'utf8');
const ids = {};
for (const m of src.matchAll(/public static final int ([A-Z0-9_]+) = (\d+);/g)) {
	ids[m[1]] = Number(m[2]);
}
const id = (constant) => {
	if (!(constant in ids)) {
		throw new Error(`ItemID.${constant} does not exist`);
	}
	return ids[constant];
};

const families = [];
/** Potion with 1-4 dose variants; constants are given as a prefix + dose digit (+ optional suffix). */
function dosed(key, name, category, prefix, extra = {}, suffixes = ['', '', '', '']) {
	const constants = [1, 2, 3, 4].map((d, i) => `${prefix}${d}${suffixes[i]}`);
	families.push({ key, name, category, constants, ids: constants.map(id), doses: true, ...extra });
}
function single(key, name, category, constant, extra = {}) {
	families.push({ key, name, category, constants: [constant], ids: [id(constant)], doses: false, ...extra });
}

// ---- food (heals = hitpoints restored by one bite)
single('anglerfish', 'Anglerfish', 'FOOD', 'ANGLERFISH', { heals: 22 });
single('manta_ray', 'Manta ray', 'FOOD', 'MANTA_RAY', { heals: 22 });
single('dark_crab', 'Dark crab', 'FOOD', 'DARK_CRAB', { heals: 22 });
single('tuna_potato', 'Tuna potato', 'FOOD', 'TUNA_POTATO', { heals: 22 });
single('sea_turtle', 'Sea turtle', 'FOOD', 'SEA_TURTLE', { heals: 21 });
single('shark', 'Shark', 'FOOD', 'SHARK', { heals: 20 });
single('cooked_karambwan', 'Cooked karambwan', 'FOOD', 'COOKED_KARAMBWAN', { heals: 18 });
single('monkfish', 'Monkfish', 'FOOD', 'MONKFISH', { heals: 16 });
single('swordfish', 'Swordfish', 'FOOD', 'SWORDFISH', { heals: 14 });
single('lobster', 'Lobster', 'FOOD', 'LOBSTER', { heals: 12 });
dosed('saradomin_brew', 'Saradomin brew', 'BREW', 'SARADOMIN_BREW');

// ---- prayer restoration
dosed('prayer_potion', 'Prayer potion', 'PRAYER', 'PRAYER_POTION');
dosed('super_restore', 'Super restore', 'PRAYER', 'SUPER_RESTORE');
dosed('sanfew_serum', 'Sanfew serum', 'PRAYER', 'SANFEW_SERUM');

// ---- combat boosts. "potion" links the supply to the combat engine's Potion assumption.
dosed('divine_super_combat', 'Divine super combat potion', 'BOOST', 'DIVINE_SUPER_COMBAT_POTION', { combatClass: 'MELEE', potion: 'SUPER_COMBAT' });
dosed('super_combat', 'Super combat potion', 'BOOST', 'SUPER_COMBAT_POTION', { combatClass: 'MELEE', potion: 'SUPER_COMBAT' });
dosed('super_attack', 'Super attack', 'BOOST', 'SUPER_ATTACK', { combatClass: 'MELEE', potion: 'SUPER_ATTACK_STRENGTH', pairedWith: 'super_strength' });
dosed('super_strength', 'Super strength', 'BOOST_PAIR', 'SUPER_STRENGTH', { combatClass: 'MELEE', potion: 'SUPER_ATTACK_STRENGTH' });
dosed('divine_ranging', 'Divine ranging potion', 'BOOST', 'DIVINE_RANGING_POTION', { combatClass: 'RANGED', potion: 'RANGING' });
dosed('ranging', 'Ranging potion', 'BOOST', 'RANGING_POTION', { combatClass: 'RANGED', potion: 'RANGING' });
dosed('divine_bastion', 'Divine bastion potion', 'BOOST', 'DIVINE_BASTION_POTION', { combatClass: 'RANGED', potion: 'BASTION' });
dosed('bastion', 'Bastion potion', 'BOOST', 'BASTION_POTION', { combatClass: 'RANGED', potion: 'BASTION' });
single('saturated_heart', 'Saturated heart', 'BOOST', 'SATURATED_HEART', { combatClass: 'MAGIC', potion: 'SATURATED_HEART' });
single('imbued_heart', 'Imbued heart', 'BOOST', 'IMBUED_HEART', { combatClass: 'MAGIC', potion: 'IMBUED_HEART' });
dosed('divine_magic', 'Divine magic potion', 'BOOST', 'DIVINE_MAGIC_POTION', { combatClass: 'MAGIC', potion: 'MAGIC' });
dosed('magic_potion', 'Magic potion', 'BOOST', 'MAGIC_POTION', { combatClass: 'MAGIC', potion: 'MAGIC' });
dosed('divine_battlemage', 'Divine battlemage potion', 'BOOST', 'DIVINE_BATTLEMAGE_POTION', { combatClass: 'MAGIC', potion: 'BATTLEMAGE' });
dosed('battlemage', 'Battlemage potion', 'BOOST', 'BATTLEMAGE_POTION', { combatClass: 'MAGIC', potion: 'BATTLEMAGE' });

// ---- protection
dosed('extended_super_antifire', 'Extended super antifire', 'ANTIFIRE', 'EXTENDED_SUPER_ANTIFIRE', { superAntifire: true });
dosed('super_antifire', 'Super antifire potion', 'ANTIFIRE', 'SUPER_ANTIFIRE_POTION', { superAntifire: true });
dosed('extended_antifire', 'Extended antifire', 'ANTIFIRE', 'EXTENDED_ANTIFIRE');
dosed('antifire', 'Antifire potion', 'ANTIFIRE', 'ANTIFIRE_POTION');
dosed('antivenom_plus', 'Anti-venom+', 'ANTIVENOM', 'ANTIVENOM', {}, ['_12919', '_12917', '_12915', '_12913']);
dosed('antivenom', 'Anti-venom', 'ANTIVENOM', 'ANTIVENOM');
dosed('stamina', 'Stamina potion', 'STAMINA', 'STAMINA_POTION');

// ---- teleports (stackable or single-slot)
// Order = preference. A tradeable tablet comes first so that the suggestion for an account that owns no
// teleport is something it can actually buy.
single('teleport_to_house', 'Teleport to house', 'TELEPORT', 'TELEPORT_TO_HOUSE', { stackable: true });
single('royal_seed_pod', 'Royal seed pod', 'TELEPORT', 'ROYAL_SEED_POD');
single('ectophial', 'Ectophial', 'TELEPORT', 'ECTOPHIAL');
single('varrock_teleport', 'Varrock teleport', 'TELEPORT', 'VARROCK_TELEPORT', { stackable: true });
single('zulandra_teleport', 'Zul-andra teleport', 'BOSS_TELEPORT', 'ZULANDRA_TELEPORT', { stackable: true });
single('key_master_teleport', 'Key master teleport', 'BOSS_TELEPORT', 'KEY_MASTER_TELEPORT', { stackable: true });

// ---- runes and containers
single('divine_rune_pouch', 'Divine rune pouch', 'RUNE_POUCH', 'DIVINE_RUNE_POUCH');
single('rune_pouch', 'Rune pouch', 'RUNE_POUCH', 'RUNE_POUCH');
for (const [key, constant] of Object.entries({
	air: 'AIR_RUNE', water: 'WATER_RUNE', earth: 'EARTH_RUNE', fire: 'FIRE_RUNE', mind: 'MIND_RUNE',
	chaos: 'CHAOS_RUNE', death: 'DEATH_RUNE', blood: 'BLOOD_RUNE', soul: 'SOUL_RUNE', wrath: 'WRATH_RUNE',
	law: 'LAW_RUNE', cosmic: 'COSMIC_RUNE', nature: 'NATURE_RUNE', astral: 'ASTRAL_RUNE',
})) {
	single(`${key}_rune`, `${key[0].toUpperCase()}${key.slice(1)} rune`, 'RUNE', constant, { stackable: true });
}

/**
 * Rune cost per cast of the spells the plugin can recommend (OSRS Wiki spell pages).
 * A spell that is missing here is still usable by the DPS engine; the inventory planner then says
 * that its runes are not in the dataset instead of guessing.
 */
const spellRunes = {
	'Wind Strike': { air: 1, mind: 1 }, 'Water Strike': { water: 1, air: 1, mind: 1 },
	'Earth Strike': { earth: 2, air: 1, mind: 1 }, 'Fire Strike': { fire: 3, air: 2, mind: 1 },
	'Wind Bolt': { air: 2, chaos: 1 }, 'Water Bolt': { water: 2, air: 2, chaos: 1 },
	'Earth Bolt': { earth: 3, air: 2, chaos: 1 }, 'Fire Bolt': { fire: 4, air: 3, chaos: 1 },
	'Wind Blast': { air: 3, death: 1 }, 'Water Blast': { water: 3, air: 3, death: 1 },
	'Earth Blast': { earth: 4, air: 3, death: 1 }, 'Fire Blast': { fire: 5, air: 4, death: 1 },
	'Wind Wave': { air: 5, blood: 1 }, 'Water Wave': { water: 7, air: 5, blood: 1 },
	'Earth Wave': { earth: 7, air: 5, blood: 1 }, 'Fire Wave': { fire: 7, air: 5, blood: 1 },
	'Wind Surge': { air: 7, wrath: 1 }, 'Water Surge': { water: 10, air: 7, wrath: 1 },
	'Earth Surge': { earth: 10, air: 7, wrath: 1 }, 'Fire Surge': { fire: 10, air: 7, wrath: 1 },
	'Ice Barrage': { water: 6, blood: 2, death: 4 }, 'Blood Barrage': { blood: 4, death: 4, soul: 1 },
	'Ice Blitz': { water: 3, blood: 2, death: 2 }, 'Blood Blitz': { blood: 4, death: 2 },
	'Crumble Undead': { earth: 2, air: 2, chaos: 1 },
};

/**
 * Consumables used per attack by charged weapons (OSRS Wiki item pages). Used only for the
 * "Budget" priority's running-cost estimate. qty is per attack; coins is a flat gp cost per attack.
 */
const charge = (constant, qty) => ({ id: id(constant), name: constant, qty });
const chargeCosts = {
	'Toxic blowpipe': { items: [charge('ZULRAHS_SCALES', 2 / 3)], usesDart: true },
	'Trident of the Seas': { items: [charge('DEATH_RUNE', 1), charge('CHAOS_RUNE', 1), charge('FIRE_RUNE', 5)], coins: 10 },
	'Trident of the Swamp': { items: [charge('DEATH_RUNE', 1), charge('CHAOS_RUNE', 1), charge('FIRE_RUNE', 5), charge('ZULRAHS_SCALES', 1)] },
	'Sanguinesti staff': { items: [charge('BLOOD_RUNE', 3)] },
	"Tumeken's shadow": { items: [charge('SOUL_RUNE', 2), charge('CHAOS_RUNE', 5)] },
	'Scythe of Vitur': { items: [charge('BLOOD_RUNE', 3), charge('VIAL_OF_BLOOD_22446', 0.01)] },
	'Bow of Faerdhinen': { items: [charge('CRYSTAL_SHARD', 0.01)] },
};

const out = {
	meta: {
		schema: 1,
		generated: new Date().toISOString().slice(0, 10),
		source: 'Item ids resolved from net.runelite.api.ItemID; heal values and rune costs from the OSRS Wiki item and spell pages.',
	},
	families,
	spellRunes,
	chargeCosts,
};
const file = path.join(root, 'src/main/resources/com/bossoptimiser/items/supplies.json');
fs.writeFileSync(file, JSON.stringify(out, null, '\t') + '\n');
console.log(`wrote ${families.length} supply families to ${file}`);
