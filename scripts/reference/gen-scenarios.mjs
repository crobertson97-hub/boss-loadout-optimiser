// Generates the reference scenario set for the Boss Loadout Optimiser DPS engine validation.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const out = [];
const OFF = { onSlayerTask: false, kandarinDiary: false, soulreaperStacks: 0 };
const TASK = { ...OFF, onSlayerTask: true };
const KAND = { ...OFF, kandarinDiary: true };

const M = { head: 'Torva full helm', body: 'Torva platebody', legs: 'Torva platelegs', cape: { name: 'Infernal cape', version: 'Normal' },
	neck: 'Amulet of torture', hands: 'Ferocious gloves', feet: 'Primordial boots', ring: 'Ultor ring' };
const DEF = { shield: { name: 'Avernic defender', version: 'Normal' } };
const R = { head: 'Masori mask (f)', body: 'Masori body (f)', legs: 'Masori chaps (f)', neck: 'Necklace of anguish',
	hands: 'Zaryte vambraces', feet: 'Pegasian boots', ring: 'Venator ring', cape: { name: "Dizana's quiver", version: 'Charged' } };
const G = { head: 'Ancestral hat', body: 'Ancestral robe top', legs: 'Ancestral robe bottom', neck: 'Occult necklace',
	hands: 'Tormented bracelet', feet: 'Eternal boots', ring: 'Magus ring', cape: 'Imbued saradomin cape' };
const CRYSTAL = { head: 'Crystal helm', body: 'Crystal body', legs: 'Crystal legs' };
const INQ = { head: "Inquisitor's great helm", body: "Inquisitor's hauberk", legs: "Inquisitor's plateskirt" };
const VOID = { body: 'Void knight top', legs: 'Void knight robe', hands: 'Void knight gloves' };
const EVOID = { body: 'Elite void top', legs: 'Elite void robe', hands: 'Void knight gloves' };
const VIRTUS = { head: 'Virtus mask', body: 'Virtus robe top', legs: 'Virtus robe bottom' };
const DARROW = { ammo: { name: 'Dragon arrow', version: 'Unpoisoned' } };

const VORK = { name: 'Vorkath', version: 'Post-quest' };
const ZG = { name: 'Zulrah', version: 'Serpentine' };
const ZR = { name: 'Zulrah', version: 'Magma' };
const ZB = { name: 'Zulrah', version: 'Tanzanite' };
const CERB = { name: 'Cerberus' };
const GRAA = { name: 'General Graardor' };
const MR = { name: 'Phantom Muspah', version: 'Ranged' };
const MM = { name: 'Phantom Muspah', version: 'Melee' };
const MS = { name: 'Phantom Muspah', version: 'Shielded' };

const MELEE = { boosts: { atk: 19, str: 19, def: 19 }, prayers: ['PIETY'] };
const RANGED = { boosts: { ranged: 13 }, prayers: ['RIGOUR'] };
const MAGIC = { boosts: { magic: 13 }, prayers: ['AUGURY'] };

function add(id, monster, equipment, style, extra = {}) {
	out.push({ id, monster, equipment, style: typeof style === 'string' ? { name: style } : style, buffs: OFF, ...extra });
}
const w = (name, version) => (version ? { name, version } : name);

// ---------------------------------------------------------------- melee
add('m01-whip-only-abyssal-demon', { name: 'Abyssal demon', version: 'Standard' }, { weapon: 'Abyssal whip' }, 'Flick');
add('m02-dhl-torva-vorkath', VORK, { ...M, ...DEF, weapon: 'Dragon hunter lance' }, 'Lunge', MELEE);
add('m03-dhl-salve-ei-vorkath', VORK, { ...M, ...DEF, weapon: 'Dragon hunter lance', neck: 'Salve amulet(ei)' }, 'Lunge', MELEE);
add('m04-dhl-slayer-helm-task-vorkath', VORK, { ...M, ...DEF, weapon: 'Dragon hunter lance', head: 'Slayer helmet (i)' }, 'Swipe', { ...MELEE, buffs: TASK });
add('m05-fang-graardor', GRAA, { ...M, ...DEF, weapon: "Osmumten's fang" }, 'Lunge', MELEE);
add('m06-fang-accurate-vorkath', VORK, { ...M, ...DEF, weapon: "Osmumten's fang" }, 'Stab', MELEE);
add('m07-scythe-graardor', GRAA, { ...M, weapon: w('Scythe of Vitur', 'Charged') }, 'Chop', MELEE);
add('m08-scythe-cerberus', CERB, { ...M, weapon: w('Scythe of Vitur', 'Charged') }, 'Reap', MELEE);
add('m09-arclight-cerberus', CERB, { ...M, ...DEF, weapon: 'Arclight' }, 'Slash', MELEE);
add('m10-emberlight-slayer-helm-cerberus', CERB, { ...M, ...DEF, weapon: 'Emberlight', head: 'Slayer helmet (i)' }, 'Slash', { ...MELEE, buffs: TASK });
add('m11-inquisitor-mace-cerberus', CERB, { ...M, ...INQ, ...DEF, weapon: "Inquisitor's mace" }, 'Pummel', MELEE);
add('m12-bludgeon-cerberus', CERB, { ...M, weapon: 'Abyssal bludgeon' }, 'Pound', MELEE);
add('m13-rapier-muspah-melee', MM, { ...M, ...DEF, weapon: 'Ghrazi rapier' }, 'Lunge', MELEE);
add('m14-void-melee-whip-graardor', GRAA, { ...M, ...VOID, head: 'Void melee helm', ...DEF, weapon: 'Abyssal whip' }, 'Lash', MELEE);
add('m15-bgs-graardor', GRAA, { ...M, weapon: 'Bandos godsword' }, 'Slash', MELEE);
add('m16-dscim-midlevel-graardor', GRAA, { weapon: 'Dragon scimitar', shield: { name: 'Rune defender', version: 'Normal' }, neck: 'Amulet of fury' }, 'Slash',
	{ skills: { atk: 70, str: 70, def: 70 } });
add('m17-zombie-axe-vorkath', VORK, { ...M, ...DEF, weapon: 'Zombie axe' }, 'Hack', MELEE);
add('m18-noxious-halberd-zulrah', ZG, { ...M, weapon: 'Noxious halberd' }, 'Swipe', MELEE);
add('m19-whip-zulrah-immune', ZG, { ...M, ...DEF, weapon: 'Abyssal whip' }, 'Flick', MELEE);
add('m20-colossal-blade-graardor', GRAA, { ...M, weapon: 'Colossal blade' }, 'Slash', MELEE);
add('m21-sulphur-blades-cerberus', CERB, { ...M, weapon: 'Sulphur blades' }, { type: 'slash', stance: 'Aggressive' }, MELEE);
add('m22-burning-claws-cerberus', CERB, { ...M, weapon: 'Burning claws' }, 'Slash', MELEE);
add('m23-dhl-crush-graardor', GRAA, { ...M, ...DEF, weapon: 'Dragon hunter lance' }, 'Pound', MELEE);
add('m24-dscim-prayers-potions-lowlevel', GRAA, { weapon: 'Dragon scimitar', neck: 'Amulet of glory' }, 'Slash',
	{ skills: { atk: 60, str: 65 }, boosts: { atk: 14, str: 14 }, prayers: ['ULTIMATE_STRENGTH', 'INCREDIBLE_REFLEXES'] });
add('m25-salve-e-whip-vorkath', VORK, { ...M, ...DEF, weapon: 'Abyssal whip', neck: 'Salve amulet (e)' }, 'Lash', MELEE);
add('m26-tentacle-chivalry-cerberus', CERB, { ...M, ...DEF, weapon: 'Abyssal tentacle' }, 'Lash', { boosts: { atk: 19, str: 19 }, prayers: ['CHIVALRY'] });
add('m27-saeldor-muspah-ranged', MR, { ...M, ...DEF, weapon: w('Blade of saeldor', 'Charged') }, 'Slash', MELEE);

// ---------------------------------------------------------------- ranged
add('r01-tbow-zulrah-blue', ZB, { ...R, ...DARROW, weapon: 'Twisted bow' }, 'Rapid', RANGED);
add('r02-tbow-zulrah-green', ZG, { ...R, ...DARROW, weapon: 'Twisted bow' }, 'Rapid', RANGED);
add('r03-tbow-zulrah-red', ZR, { ...R, ...DARROW, weapon: 'Twisted bow' }, 'Rapid', RANGED);
add('r04-tbow-vorkath', VORK, { ...R, ...DARROW, weapon: 'Twisted bow' }, 'Rapid', RANGED);
add('r05-tbow-slayer-helm-cerberus', CERB, { ...R, ...DARROW, weapon: 'Twisted bow', head: 'Slayer helmet (i)' }, 'Rapid', { ...RANGED, buffs: TASK });
add('r06-tbow-graardor', GRAA, { ...R, ...DARROW, weapon: 'Twisted bow' }, 'Rapid', RANGED);
add('r07-tbow-muspah-ranged', MR, { ...R, ...DARROW, weapon: 'Twisted bow' }, 'Accurate', RANGED);
add('r08-dhcb-diamond-vorkath', VORK, { ...R, weapon: 'Dragon hunter crossbow', shield: 'Twisted buckler', ammo: 'Diamond dragon bolts (e)' }, 'Rapid', RANGED);
add('r09-dhcb-ruby-kandarin-vorkath', VORK, { ...R, weapon: 'Dragon hunter crossbow', shield: 'Twisted buckler', ammo: 'Ruby dragon bolts (e)' }, 'Rapid', { ...RANGED, buffs: KAND });
add('r10-dhcb-ruby-vorkath-300hp', VORK, { ...R, weapon: 'Dragon hunter crossbow', ammo: 'Ruby dragon bolts (e)' }, 'Rapid', { ...RANGED, monsterCurrentHp: 300 });
add('r11-dhcb-salve-ei-diamond-vorkath', VORK, { ...R, weapon: 'Dragon hunter crossbow', neck: 'Salve amulet(ei)', ammo: 'Diamond dragon bolts (e)' }, 'Rapid', RANGED);
add('r12-dhcb-slayer-helm-task-vorkath', VORK, { ...R, weapon: 'Dragon hunter crossbow', head: 'Slayer helmet (i)', ammo: { name: 'Dragon bolts', version: 'Unpoisoned' } }, 'Rapid', { ...RANGED, buffs: TASK });
add('r13-acb-diamond-graardor', GRAA, { ...R, weapon: 'Armadyl crossbow', ammo: 'Diamond bolts (e)' }, 'Accurate', RANGED);
add('r14-zcb-diamond-graardor', GRAA, { ...R, weapon: 'Zaryte crossbow', ammo: 'Diamond dragon bolts (e)' }, 'Rapid', RANGED);
add('r15-zcb-ruby-muspah-ranged', MR, { ...R, weapon: 'Zaryte crossbow', ammo: 'Ruby dragon bolts (e)' }, 'Rapid', RANGED);
add('r16-bowfa-crystal-zulrah-green', ZG, { ...R, ...CRYSTAL, weapon: w('Bow of Faerdhinen', 'Charged') }, 'Rapid', RANGED);
add('r17-bowfa-crystal-muspah-ranged', MR, { ...R, ...CRYSTAL, weapon: w('Bow of Faerdhinen', 'Charged') }, 'Rapid', RANGED);
add('r18-bowfa-masori-muspah-shield', MS, { ...R, weapon: w('Bow of Faerdhinen', 'Charged') }, 'Rapid', RANGED);
add('r19-blowpipe-dragon-dart-zulrah-red', ZR, { ...R, weapon: { name: 'Toxic blowpipe', version: 'Charged', dart: 'Dragon dart' } }, 'Rapid', RANGED);
add('r20-blowpipe-amethyst-dart-graardor', GRAA, { ...R, weapon: { name: 'Toxic blowpipe', version: 'Charged', dart: 'Amethyst dart' } }, 'Accurate', RANGED);
add('r21-elite-void-tbow-zulrah-blue', ZB, { ...R, ...EVOID, head: 'Void ranger helm', ...DARROW, weapon: 'Twisted bow' }, 'Rapid', RANGED);
add('r22-void-blowpipe-zulrah-green', ZG, { ...R, ...VOID, head: 'Void ranger helm', weapon: { name: 'Toxic blowpipe', version: 'Charged', dart: 'Rune dart' } }, 'Rapid', RANGED);
add('r23-rcb-broad-midlevel-vorkath', VORK, { weapon: 'Rune crossbow', ammo: 'Broad bolts', neck: 'Amulet of fury', cape: "Ava's accumulator" }, 'Accurate',
	{ skills: { ranged: 75 }, boosts: { ranged: 11 }, prayers: ['EAGLE_EYE'] });
add('r24-msbi-amethyst-graardor', GRAA, { ...R, weapon: 'Magic shortbow (i)', ammo: { name: 'Amethyst arrow', version: 'Unpoisoned' } }, 'Rapid', RANGED);
add('r25-scorching-bow-cerberus', CERB, { ...R, ...DARROW, weapon: 'Scorching bow' }, 'Rapid', RANGED);
add('r26-scorching-bow-slayer-helm-cerberus', CERB, { ...R, ...DARROW, weapon: 'Scorching bow', head: 'Slayer helmet (i)' }, 'Rapid', { ...RANGED, buffs: TASK });
add('r27-dark-bow-graardor', GRAA, { ...R, ...DARROW, weapon: w('Dark bow', 'Regular') }, 'Rapid', RANGED);
add('r28-acb-onyx-graardor', GRAA, { ...R, weapon: 'Armadyl crossbow', ammo: 'Onyx dragon bolts (e)' }, 'Rapid', RANGED);
add('r29-dhcb-opal-vorkath', VORK, { ...R, weapon: 'Dragon hunter crossbow', ammo: 'Opal dragon bolts (e)' }, 'Rapid', RANGED);
add('r30-acb-pearl-vorkath', VORK, { ...R, weapon: 'Armadyl crossbow', ammo: 'Pearl dragon bolts (e)' }, 'Rapid', RANGED);
add('r31-acb-dragonstone-graardor', GRAA, { ...R, weapon: 'Armadyl crossbow', ammo: 'Dragonstone dragon bolts (e)' }, 'Rapid', { ...RANGED, buffs: KAND });
add('r32-venator-bow-muspah-ranged', MR, { ...R, ...DARROW, weapon: w('Venator bow', 'Charged') }, 'Rapid', RANGED);
add('r33-tbow-wrong-ammo', GRAA, { ...R, weapon: 'Twisted bow', ammo: 'Diamond bolts (e)' }, 'Rapid', RANGED);
add('r34-tbow-deadeye-no-quiver-zulrah-blue', ZB, { ...R, cape: "Ava's assembler", ...DARROW, weapon: 'Twisted bow' }, 'Rapid', { boosts: { ranged: 13 }, prayers: ['DEADEYE'] });
add('r35-dhcb-ruby-zulrah-green', ZG, { ...R, weapon: 'Dragon hunter crossbow', ammo: 'Ruby dragon bolts (e)' }, 'Rapid', RANGED);

// ---------------------------------------------------------------- magic
const SHADOW = w("Tumeken's shadow", 'Charged');
add('g01-shadow-zulrah-green', ZG, { ...G, weapon: SHADOW }, 'Accurate', MAGIC);
add('g02-shadow-zulrah-red', ZR, { ...G, weapon: SHADOW }, 'Accurate', MAGIC);
add('g03-shadow-muspah-melee', MM, { ...G, weapon: SHADOW }, 'Accurate', MAGIC);
add('g04-shadow-slayer-helm-cerberus', CERB, { ...G, weapon: SHADOW, head: 'Slayer helmet (i)' }, 'Accurate', { ...MAGIC, buffs: TASK });
add('g05-shadow-graardor', GRAA, { ...G, weapon: SHADOW }, 'Accurate', MAGIC);
add('g06-swamp-trident-zulrah-green', ZG, { ...G, weapon: w('Trident of the Swamp', 'Charged') }, 'Accurate', MAGIC);
add('g07-sang-zulrah-red', ZR, { ...G, weapon: w('Sanguinesti staff', 'Charged') }, 'Accurate', MAGIC);
add('g08-sang-muspah-melee', MM, { ...G, weapon: w('Sanguinesti staff', 'Charged') }, 'Accurate', MAGIC);
add('g09-seas-trident-salve-ei-vorkath', VORK, { ...G, weapon: w('Trident of the Seas', 'Charged'), neck: 'Salve amulet(ei)' }, 'Accurate', MAGIC);
add('g10-kodai-fire-surge-tome-zulrah-green', ZG, { ...G, weapon: 'Kodai wand', shield: w('Tome of Fire', 'Charged') }, { type: 'magic', stance: 'Autocast' }, { ...MAGIC, spell: 'Fire Surge' });
add('g11-kodai-earth-surge-tome-graardor', GRAA, { ...G, weapon: 'Kodai wand', shield: w('Tome of Earth', 'Charged') }, { type: 'magic', stance: 'Autocast' }, { ...MAGIC, spell: 'Earth Surge' });
add('g12-kodai-water-surge-tome-cerberus', CERB, { ...G, weapon: 'Kodai wand', shield: w('Tome of Water', 'Charged') }, { type: 'magic', stance: 'Autocast' }, { ...MAGIC, spell: 'Water Surge' });
add('g13-harmonised-fire-surge-zulrah-green', ZG, { ...G, weapon: 'Harmonised Nightmare staff' }, { type: 'magic', stance: 'Autocast' }, { ...MAGIC, spell: 'Fire Surge' });
add('g14-smoke-staff-fire-wave-zulrah-red', ZR, { ...G, weapon: 'Smoke battlestaff' }, { type: 'magic', stance: 'Autocast' }, { skills: { magic: 80 }, prayers: ['MYSTIC_MIGHT'], spell: 'Fire Wave' });
add('g15-shadow-brimstone-muspah-melee', MM, { ...G, weapon: SHADOW, ring: 'Brimstone ring' }, 'Accurate', MAGIC);
add('g16-kodai-wind-surge-confliction-muspah-melee', MM, { ...G, weapon: 'Kodai wand', hands: 'Confliction gauntlets' }, { type: 'magic', stance: 'Autocast' }, { ...MAGIC, spell: 'Wind Surge' });
add('g17-elite-void-mage-swamp-trident-zulrah-green', ZG, { ...G, ...EVOID, head: 'Void mage helm', weapon: w('Trident of the Swamp', 'Charged') }, 'Accurate', MAGIC);
add('g18-seas-trident-midlevel-zulrah-green', ZG, { weapon: w('Trident of the Seas', 'Charged'), neck: 'Occult necklace' }, 'Accurate',
	{ skills: { magic: 80 }, boosts: { magic: 4 }, prayers: ['MYSTIC_MIGHT'] });
add('g19-eye-of-ayak-muspah-melee', MM, { ...G, weapon: w('Eye of Ayak', 'Charged') }, 'Accurate', MAGIC);
add('g20-warped-sceptre-graardor', GRAA, { ...G, weapon: w('Warped sceptre', 'Charged') }, 'Accurate', MAGIC);
add('g21-kodai-ice-barrage-virtus-graardor', GRAA, { ...G, ...VIRTUS, weapon: 'Kodai wand' }, { type: 'magic', stance: 'Autocast' }, { ...MAGIC, spell: 'Ice Barrage' });
add('g22-kodai-fire-surge-no-weakness-muspah', MM, { ...G, weapon: 'Kodai wand' }, { type: 'magic', stance: 'Autocast' }, { ...MAGIC, spell: 'Fire Surge' });
add('g23-shadow-vigour-muspah-post-shield', { name: 'Phantom Muspah', version: 'Post-shield' }, { ...G, weapon: SHADOW }, 'Accurate', { boosts: { magic: 10 }, prayers: ['MYSTIC_VIGOUR'] });
add('g24-fire-bolt-midlevel-vorkath', VORK, { weapon: 'Staff of fire', neck: 'Amulet of magic' }, { type: 'magic', stance: 'Autocast' }, { skills: { magic: 50 }, spell: 'Fire Bolt' });

fs.writeFileSync(path.join(here, 'scenarios.json'), JSON.stringify(out, null, 1));
console.log(`wrote ${out.length} scenarios`);
