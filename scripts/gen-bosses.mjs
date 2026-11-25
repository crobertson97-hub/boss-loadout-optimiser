#!/usr/bin/env node
// Generates boss definitions (src/main/resources/com/bossoptimiser/bosses/*.json) from the curated table below
// and the bundled monster data. The five original bosses (Vorkath, Zulrah, Cerberus, General Graardor,
// Phantom Muspah) are hand-written and are only listed in index.json, never overwritten.
//
// What is curated here, by hand:  which bosses, their phases (monster id + version), the styles offered,
//   categories, slayer data, access requirements we are sure of, and warnings.
// What is derived from data:     antifire (monster attack styles include "Dragonfire"), damage-taken weights
//   (the monster's listed attack styles, equally weighted), and the validation tier.
// Never invented: quest/skill requirements we are not sure of become a NOTE pointing at the wiki page.
//
// Usage: node scripts/gen-bosses.mjs            (writes files)
//        node scripts/gen-bosses.mjs --check    (exits 1 if the generated files differ from what is on disk)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const RES = path.join(ROOT, 'src/main/resources/com/bossoptimiser');
const OUT = path.join(RES, 'bosses');
const DATA_VERSION = '2026-11-25';
const HAND_WRITTEN = ['vorkath.json', 'zulrah.json', 'cerberus.json', 'general-graardor.json', 'phantom-muspah.json'];

const monsters = JSON.parse(fs.readFileSync(path.join(RES, 'items/monsters.json'), 'utf8')).monsters;

// Mirrors Mechanics.SPECIAL_RULE_MONSTERS / monsterUnsupportedReason: monsters whose DPS is only an estimate.
const SPECIAL = new Set(['Verzik Vitur', 'Tekton', 'Great Olm', 'Ice demon', 'Tormented Demon', 'The Hueycoatl', 'Abyssal Sire',
	'Duke Sucellus', 'Yama', 'Dusk', 'Dawn', 'Scurrius', 'Doom of Mokhaiotl', 'Araxxor', 'Eclipse Moon', 'Blood Moon', 'Blue Moon',
	'Branda the Fire Queen', 'Eldric the Ice King']);
const SPECIAL_ATTRS = ['vampyre', 'leafy', 'rat', 'kalphite', 'shade', 'golem', 'xerician'];

// ---------------------------------------------------------------- shared text
const WILDY = [
	'This boss is in the Wilderness: other players can attack you there and items are at risk on death.',
	"Wilderness weapons (Craw's bow, Viggora's chainmace, Thammaron's sceptre and their upgrades) are not modelled; setups using them are shown as estimates.",
];
const GWD_REQS = [
	{ type: 'QUEST', quest: 'TROLL_STRONGHOLD', minState: 'IN_PROGRESS', text: 'Troll Stronghold started (access to Trollheim)' },
	{ type: 'NOTE', text: '40 kill count for this god (less with the relevant diary) is needed to enter the room' },
];
const GWD_WARN = 'God items worn for protection from the dungeon\'s followers are not scored by the optimiser.';
const SEE_WIKI = { type: 'NOTE', text: 'Access requirements are not checked by the plugin: see the wiki page' };
const DT2 = { type: 'QUEST', quest: 'DESERT_TREASURE_II__THE_FALLEN_EMPIRE', minState: 'FINISHED', text: 'Desert Treasure II - The Fallen Empire completed' };
const AWAKENED_NOTE = { type: 'NOTE', text: "The awakened version needs an awakener's orb" };
const MORYTANIA = { type: 'QUEST', quest: 'PRIEST_IN_PERIL', minState: 'FINISHED', text: 'Priest in Peril completed (access to Morytania)' };
const slayerReq = (lvl) => ({ type: 'SKILL', skill: 'slayer', level: lvl, text: lvl + ' Slayer' });
const TASK_NOTE = (what) => ({ type: 'NOTE', text: 'Requires an active ' + what + ' slayer task' });

// ---------------------------------------------------------------- curated table
// phases: [phaseId, display name, monster name, monster version (exact, '' for none)]
// styles: strategies offered, best-known first. A string like 'MELEE' means every phase uses that style;
//         an object gives per-phase styles for hybrid strategies.
// weights: phase weights for multi-phase bosses (always heuristic). Single-phase bosses weigh 1.
const B = [
	// ----- Slayer
	{ id: 'abyssal-sire', name: 'Abyssal Sire', category: 'SLAYER', wiki: 'Abyssal_Sire', slayer: [true, true],
		requirements: [slayerReq(85), TASK_NOTE('abyssal demon')],
		phases: [['phase-1', 'Phase 1', 'Abyssal Sire', 'Phase 1'], ['phase-2', 'Phase 2', 'Abyssal Sire', 'Phase 2'],
			['phase-3a', 'Phase 3 (stage 1)', 'Abyssal Sire', 'Phase 3 (stage 1)'], ['phase-3b', 'Phase 3 (stage 2)', 'Abyssal Sire', 'Phase 3 (stage 2)']],
		weights: [0.25, 0.25, 0.25, 0.25], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Respiratory systems, spawns and the Sire\'s phase transitions are not part of the DPS figures.'] },
	{ id: 'alchemical-hydra', name: 'Alchemical Hydra', category: 'SLAYER', wiki: 'Alchemical_Hydra', slayer: [true, true],
		requirements: [slayerReq(95), TASK_NOTE('hydra'), SEE_WIKI],
		phases: [['serpentine', 'Serpentine (green)', 'Alchemical Hydra', 'Serpentine'], ['electric', 'Electric (blue)', 'Alchemical Hydra', 'Electric'],
			['fire', 'Fire (red)', 'Alchemical Hydra', 'Fire'], ['extinguished', 'Extinguished (enraged)', 'Alchemical Hydra', 'Extinguished']],
		weights: [0.25, 0.25, 0.25, 0.25], styles: ['RANGED', 'MELEE', 'MAGIC'], antivenom: 'Poison pools',
		warnings: ['The DPS figures assume the Hydra is not protected by its damage-reduction effect (see the wiki strategy page); that effect is not modelled.'] },
	{ id: 'kraken', name: 'Kraken', category: 'SLAYER', wiki: 'Kraken', slayer: [true, true],
		requirements: [slayerReq(87), TASK_NOTE('cave kraken')],
		phases: [['main', 'Kraken', 'Kraken', 'Kraken']], styles: ['MAGIC'],
		warnings: ['The Kraken cannot be damaged with melee and ranged damage against it is divided by 7, so only a magic strategy is offered.'] },
	{ id: 'thermonuclear-smoke-devil', name: 'Thermonuclear smoke devil', category: 'SLAYER', wiki: 'Thermonuclear_smoke_devil', slayer: [true, true],
		requirements: [slayerReq(93), TASK_NOTE('smoke devil')],
		phases: [['main', 'Thermonuclear smoke devil', 'Thermonuclear smoke devil', '']], styles: ['RANGED', 'MELEE', 'MAGIC'],
		warnings: ['A facemask or slayer helmet is needed in the Smoke Devil Dungeon; the optimiser does not force one into the head slot.'] },
	{ id: 'grotesque-guardians', name: 'Grotesque Guardians', category: 'SLAYER', wiki: 'Grotesque_Guardians', slayer: [true, true],
		requirements: [slayerReq(75), TASK_NOTE('gargoyle'), SEE_WIKI],
		phases: [['dawn', 'Dawn', 'Dawn', ''], ['dusk-1', 'Dusk (first form)', 'Dusk', 'First form'], ['dusk-2', 'Dusk (second form)', 'Dusk', 'Second form']],
		weights: [0.3, 0.35, 0.35],
		styles: [{ id: 'ranged-melee', name: 'Ranged on Dawn, melee on Dusk', description: 'Dawn cannot be hit with melee (except halberds) and Dusk only takes melee damage. Switch items are carried in the inventory.', styles: { dawn: 'RANGED', 'dusk-1': 'MELEE', 'dusk-2': 'MELEE' } },
			{ id: 'magic-melee', name: 'Magic on Dawn, melee on Dusk', description: 'Magic against Dawn, melee against Dusk.', styles: { dawn: 'MAGIC', 'dusk-1': 'MELEE', 'dusk-2': 'MELEE' } }],
		warnings: ['Dusk only takes melee damage; Dawn cannot be hit with melee except by halberds.'] },
	{ id: 'araxxor', name: 'Araxxor', category: 'SLAYER', wiki: 'Araxxor', slayer: [true, true],
		requirements: [slayerReq(92), TASK_NOTE('araxyte')],
		phases: [['main', 'Araxxor', 'Araxxor', '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Araxyte spawns and enrage are not part of the DPS figures.'] },

	// ----- God Wars Dungeon
	{ id: 'kreearra', name: "Kree'arra", category: 'GWD', wiki: "Kree'arra", groups: ['SOLO', 'GROUP'],
		requirements: [{ type: 'SKILL', skill: 'ranged', level: 70, text: "70 Ranged to reach Armadyl's Eyrie" }, ...GWD_REQS],
		phases: [['main', "Kree'arra", "Kree'arra", '']], styles: ['RANGED', 'MAGIC'],
		warnings: ["Kree'arra cannot be hit with melee (except halberds), so melee is not offered.", 'Bodyguard kills are not included in the DPS estimate.', GWD_WARN] },
	{ id: 'commander-zilyana', name: 'Commander Zilyana', category: 'GWD', wiki: 'Commander_Zilyana', groups: ['SOLO', 'GROUP'],
		requirements: [{ type: 'SKILL', skill: 'agility', level: 70, text: '70 Agility to enter Saradomin\'s Encampment' }, ...GWD_REQS],
		phases: [['main', 'Commander Zilyana', 'Commander Zilyana', '']], styles: ['RANGED', 'MELEE', 'MAGIC'],
		warnings: ['Bodyguard kills are not included in the DPS estimate.', GWD_WARN] },
	{ id: 'kril-tsutsaroth', name: "K'ril Tsutsaroth", category: 'GWD', wiki: "K'ril_Tsutsaroth", groups: ['SOLO', 'GROUP'],
		requirements: [{ type: 'SKILL', skill: 'hitpoints', level: 70, text: "70 Hitpoints to enter Zamorak's Fortress" }, ...GWD_REQS],
		phases: [['main', "K'ril Tsutsaroth", "K'ril Tsutsaroth", '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Bodyguard kills are not included in the DPS estimate.', GWD_WARN] },
	{ id: 'nex', name: 'Nex', category: 'GWD', wiki: 'Nex', groups: ['GROUP', 'SOLO'],
		requirements: [SEE_WIKI],
		phases: [['main', 'Nex', 'Nex', '']], styles: ['RANGED', 'MELEE', 'MAGIC'],
		partial: 'Nex\'s phases, minions and group damage sharing are not modelled; the figures are for her base statistics.',
		warnings: ['Nex is normally fought in a group; the DPS figures are per player.'] },

	// ----- Wilderness
	...[
		['artio', 'Artio', 'Artio', ['MELEE', 'RANGED', 'MAGIC']],
		['callisto', 'Callisto', 'Callisto', ['RANGED', 'MELEE', 'MAGIC']],
		['venenatis', 'Venenatis', 'Venenatis', ['RANGED', 'MELEE', 'MAGIC'], 'Poison'],
		['spindel', 'Spindel', 'Spindel', ['RANGED', 'MELEE', 'MAGIC'], 'Poison'],
		['scorpia', 'Scorpia', 'Scorpia', ['RANGED', 'MELEE', 'MAGIC'], 'Poison'],
		['chaos-elemental', 'Chaos Elemental', 'Chaos Elemental', ['RANGED', 'MELEE', 'MAGIC']],
		['chaos-fanatic', 'Chaos Fanatic', 'Chaos Fanatic', ['MAGIC', 'MELEE', 'RANGED']],
		['crazy-archaeologist', 'Crazy archaeologist', 'Crazy archaeologist', ['MELEE', 'RANGED', 'MAGIC']],
	].map(([id, name, monster, styles, venom]) => ({ id, name, category: 'WILDERNESS', wiki: name.replace(/ /g, '_'),
		requirements: [SEE_WIKI], phases: [['main', name, monster, '']], styles, antivenom: venom, warnings: WILDY })),
	{ id: 'vetion', name: "Vet'ion", category: 'WILDERNESS', wiki: "Vet'ion", requirements: [SEE_WIKI],
		phases: [['main', "Vet'ion", "Vet'ion", 'Normal']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		partial: "Vet'ion's skeleton hellhounds and second form are not modelled (both forms share the same statistics).", warnings: WILDY },
	{ id: 'calvarion', name: "Calvar'ion", category: 'WILDERNESS', wiki: "Calvar'ion", requirements: [SEE_WIKI],
		phases: [['main', "Calvar'ion", "Calvar'ion", 'Normal']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		partial: "Calvar'ion's skeletons and second form are not modelled (both forms share the same statistics).", warnings: WILDY },

	// ----- Standard
	{ id: 'king-black-dragon', name: 'King Black Dragon', category: 'STANDARD', wiki: 'King_Black_Dragon', requirements: [SEE_WIKI],
		phases: [['main', 'King Black Dragon', 'King Black Dragon', '']], styles: ['MELEE', 'RANGED', 'MAGIC'], antivenom: 'Poison breath', warnings: [] },
	{ id: 'kalphite-queen', name: 'Kalphite Queen', category: 'STANDARD', wiki: 'Kalphite_Queen', requirements: [SEE_WIKI], groups: ['SOLO', 'GROUP'],
		phases: [['crawling', 'First form (crawling)', 'Kalphite Queen', 'Crawling'], ['airborne', 'Second form (airborne)', 'Kalphite Queen', 'Airborne']],
		weights: [0.5, 0.5], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Rules specific to kalphites (see the wiki strategy page) are not modelled; DPS against the Kalphite Queen is an estimate.'] },
	{ id: 'giant-mole', name: 'Giant Mole', category: 'STANDARD', wiki: 'Giant_Mole', requirements: [SEE_WIKI],
		phases: [['main', 'Giant Mole', 'Giant Mole', '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['A light source is needed in the Mole lair; the planner does not add one.'] },
	...[
		['dagannoth-prime', 'Dagannoth Prime', ['RANGED', 'MELEE']],
		['dagannoth-rex', 'Dagannoth Rex', ['MAGIC', 'RANGED']],
		['dagannoth-supreme', 'Dagannoth Supreme', ['MELEE', 'MAGIC']],
	].map(([id, name, styles]) => ({ id, name, category: 'STANDARD', wiki: name.replace(/ /g, '_'), requirements: [SEE_WIKI],
		phases: [['main', name, name, '']], styles,
		warnings: ['Only this king is included; the other two Dagannoth Kings are separate entries.'] })),
	{ id: 'corporeal-beast', name: 'Corporeal Beast', category: 'STANDARD', wiki: 'Corporeal_Beast', requirements: [SEE_WIKI], groups: ['SOLO', 'GROUP'],
		phases: [['main', 'Corporeal Beast', 'Corporeal Beast', '']], styles: ['MELEE', 'MAGIC', 'RANGED'],
		partial: 'The dark energy core is not modelled. Damage is halved except from stab spears, halberds, Osmumten\'s fang and magic, which is included in the figures.',
		warnings: ['Damage is halved unless you stab with a spear, halberd or Osmumten\'s fang, or use magic; this is included in the DPS figures.'] },
	{ id: 'sarachnis', name: 'Sarachnis', category: 'STANDARD', wiki: 'Sarachnis', requirements: [SEE_WIKI],
		phases: [['main', 'Sarachnis', 'Sarachnis', '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Spawns are not included in the DPS estimate.'] },
	{ id: 'skotizo', name: 'Skotizo', category: 'STANDARD', wiki: 'Skotizo', requirements: [{ type: 'NOTE', text: 'A dark totem is needed for each kill' }],
		phases: [['main', 'Skotizo', 'Skotizo', '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Skotizo\'s altars raise its defence while active; that is not modelled.'] },
	{ id: 'obor', name: 'Obor', category: 'STANDARD', wiki: 'Obor', requirements: [{ type: 'NOTE', text: 'A giant key is needed for each kill' }],
		phases: [['main', 'Obor', 'Obor', '']], styles: ['MELEE', 'RANGED', 'MAGIC'], warnings: [] },
	{ id: 'bryophyta', name: 'Bryophyta', category: 'STANDARD', wiki: 'Bryophyta', requirements: [{ type: 'NOTE', text: 'A mossy key is needed for each kill' }],
		phases: [['main', 'Bryophyta', 'Bryophyta', '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Growthlings are not included in the DPS estimate.'] },
	{ id: 'deranged-archaeologist', name: 'Deranged archaeologist', category: 'STANDARD', wiki: 'Deranged_archaeologist',
		requirements: [{ type: 'QUEST', quest: 'BONE_VOYAGE', minState: 'FINISHED', text: 'Bone Voyage completed (access to Fossil Island)' }],
		phases: [['main', 'Deranged archaeologist', 'Deranged archaeologist', '']], styles: ['MELEE', 'RANGED', 'MAGIC'], warnings: [] },
	{ id: 'the-nightmare', name: 'The Nightmare', category: 'STANDARD', wiki: 'The_Nightmare', groups: ['GROUP', 'SOLO'], requirements: [MORYTANIA],
		phases: [['main', 'The Nightmare', 'The Nightmare', '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		partial: 'Shield phases, totems and group scaling are not modelled; the figures are for the Nightmare\'s base statistics.',
		warnings: ['Totems and sleepwalkers are not included in the DPS estimate.'] },
	{ id: 'phosanis-nightmare', name: "Phosani's Nightmare", category: 'STANDARD', wiki: "Phosani's_Nightmare", requirements: [MORYTANIA, SEE_WIKI],
		phases: [['main', "Phosani's Nightmare", "Phosani's Nightmare", '']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		partial: 'Shield phases, totems and sleepwalkers are not modelled; the figures are for the base statistics.',
		warnings: ['Totems and sleepwalkers are not included in the DPS estimate.'] },
	...[
		['duke-sucellus', 'Duke Sucellus', 'Duke Sucellus', 'Post-quest, Awake', 'Awakened, Awake', ['MELEE', 'RANGED', 'MAGIC']],
		['the-leviathan', 'The Leviathan', 'The Leviathan', 'Post-quest', 'Awakened', ['RANGED', 'MAGIC']],
		['vardorvis', 'Vardorvis', 'Vardorvis', 'Post-quest', 'Awakened', ['MELEE', 'RANGED', 'MAGIC']],
		['the-whisperer', 'The Whisperer', 'The Whisperer', 'Post-quest', 'Awakened', ['MAGIC', 'MELEE', 'RANGED']],
	].map(([id, name, monster, post, awake, styles]) => ({ id, name, category: 'STANDARD', wiki: name.replace(/ /g, '_'),
		requirements: [DT2, AWAKENED_NOTE],
		variants: [['post-quest', 'Post-quest', [['main', name, monster, post]]], ['awakened', 'Awakened', [['main', name, monster, awake]]]],
		styles,
		warnings: id === 'the-leviathan' ? ['The Leviathan cannot be damaged with melee, so melee is not offered.'] : [] })),
	{ id: 'moons-of-peril', name: 'Moons of Peril', category: 'STANDARD', wiki: 'Moons_of_Peril',
		requirements: [{ type: 'QUEST', quest: 'PERILOUS_MOONS', minState: 'FINISHED', text: 'Perilous Moons completed' }],
		phases: [['blood', 'Blood Moon', 'Blood Moon', ''], ['blue', 'Blue Moon', 'Blue Moon', ''], ['eclipse', 'Eclipse Moon', 'Eclipse Moon', 'Regular']],
		weights: [0.3334, 0.3333, 0.3333], styles: ['MELEE'],
		warnings: ['One setup for all three Moons. Each Moon\'s special phase is not modelled.', 'Only melee is offered: the Moons have very high ranged and magic defence.'] },
	{ id: 'royal-titans', name: 'Royal Titans', category: 'STANDARD', wiki: 'Royal_Titans', groups: ['SOLO', 'GROUP'], requirements: [SEE_WIKI],
		phases: [['branda', 'Branda the Fire Queen', 'Branda the Fire Queen', ''], ['eldric', 'Eldric the Ice King', 'Eldric the Ice King', '']],
		weights: [0.5, 0.5], styles: ['MELEE'],
		warnings: ['Only melee is offered: the Titans have very high ranged and magic defence. Elemental and pillar mechanics are not modelled.'] },
	{ id: 'scurrius', name: 'Scurrius', category: 'STANDARD', wiki: 'Scurrius', requirements: [SEE_WIKI],
		variants: [['solo', 'Solo', [['main', 'Scurrius', 'Scurrius', 'Solo']]], ['group', 'Group', [['main', 'Scurrius', 'Scurrius', 'Group']]]],
		styles: ['MELEE', 'RANGED', 'MAGIC'], warnings: ['Giant rats are not included in the DPS estimate.'] },
	{ id: 'the-hueycoatl', name: 'The Hueycoatl', category: 'STANDARD', wiki: 'The_Hueycoatl', groups: ['GROUP', 'SOLO'], requirements: [SEE_WIKI],
		phases: [['main', 'The Hueycoatl', 'The Hueycoatl', 'Normal']], styles: ['MELEE', 'RANGED', 'MAGIC'],
		warnings: ['Only the head is modelled; the tail and body phases are not included.'] },
	{ id: 'yama', name: 'Yama', category: 'STANDARD', wiki: 'Yama', groups: ['SOLO', 'GROUP'], requirements: [SEE_WIKI],
		phases: [['main', 'Phases 1-2', 'Yama', 'Normal'], ['phase-3', 'Phase 3', 'Yama', 'Phase 3']],
		weights: [0.67, 0.33], styles: ['MAGIC', 'MELEE', 'RANGED'],
		warnings: ['Yama\'s shields, voice of Yama and duo mechanics are not modelled.'] },
	{ id: 'amoxliatl', name: 'Amoxliatl', category: 'STANDARD', wiki: 'Amoxliatl', requirements: [SEE_WIKI],
		phases: [['main', 'Amoxliatl', 'Amoxliatl', 'Post-quest']], styles: ['MELEE', 'RANGED', 'MAGIC'], warnings: [] },

	// ----- Minigame / other
	{ id: 'barrows', name: 'Barrows brothers', category: 'MINIGAME', wiki: 'Barrows', requirements: [MORYTANIA],
		phases: [['ahrim', 'Ahrim', 'Ahrim the Blighted', ''], ['dharok', 'Dharok', 'Dharok the Wretched', ''], ['guthan', 'Guthan', 'Guthan the Infested', ''],
			['karil', 'Karil', 'Karil the Tainted', ''], ['torag', 'Torag', 'Torag the Corrupted', ''], ['verac', 'Verac', 'Verac the Defiled', '']],
		weights: [0.1667, 0.1667, 0.1667, 0.1667, 0.1666, 0.1666],
		styles: [{ id: 'magic-melee', name: 'Magic on melee brothers, melee on Ahrim and Karil', description: 'Magic against Dharok, Guthan, Torag and Verac; melee against Ahrim and Karil.',
			styles: { ahrim: 'MELEE', dharok: 'MAGIC', guthan: 'MAGIC', karil: 'MELEE', torag: 'MAGIC', verac: 'MAGIC' } }, 'MAGIC', 'MELEE', 'RANGED'],
		warnings: ['Tunnel monsters are not included. Each brother weighs the same, which is an assumption.'] },
	{ id: 'the-mimic', name: 'The Mimic', category: 'OTHER', wiki: 'The_Mimic', requirements: [{ type: 'NOTE', text: 'Needs a mimic casket from a treasure trail' }],
		phases: [['main', 'The Mimic', 'The Mimic', '']], styles: ['MELEE', 'RANGED', 'MAGIC'], warnings: [] },
];

// Not offered, with the reason (written to docs by hand; listed here so the generator documents it too).
export const NOT_OFFERED = {
	'Raids (Chambers of Xeric, Theatre of Blood, Tombs of Amascut)': 'room-by-room encounters with scaling and team roles',
	'TzTok-Jad, TzKal-Zuk, Sol Heredit': 'final waves of wave-based minigames',
	'Crystalline / Corrupted Hunllef': 'gear is made inside the Gauntlet, not taken from the bank',
	'Wintertodt, Tempoross, Zalcano': 'skilling bosses',
	'Doom of Mokhaiotl': 'stats scale with each delve',
	'Hespori, Revenant maledictus, quest-only bosses': 'not repeatable PvM bosses',
};

// ---------------------------------------------------------------- builders
function findMonster(name, version) {
	const all = monsters.filter((m) => m.name === name);
	const m = version === '' ? all[0] : all.find((x) => x.version === version);
	if (!m) {
		throw new Error(`monster not found: ${name} [${version}] (versions: ${all.map((x) => JSON.stringify(x.version)).join(', ')})`);
	}
	return m;
}

function special(m) {
	if (SPECIAL.has(m.name)) return true;
	return (m.attributes || []).some((a) => SPECIAL_ATTRS.some((s) => a.startsWith(s)));
}

function damageTaken(ms) {
	const counts = { stab: 0, slash: 0, crush: 0, magic: 0, ranged: 0 };
	for (const m of ms) {
		for (const raw of m.styles) {
			const s = raw.toLowerCase();
			let hit = false;
			for (const k of ['stab', 'slash', 'crush', 'magic', 'ranged']) {
				if (s.includes(k)) { counts[k]++; hit = true; }
			}
			if (!hit && s.includes('melee')) { counts.slash++; hit = true; }
			if (!hit && (s === 'all' || s === 'various')) { for (const k in counts) counts[k]++; }
		}
	}
	let total = Object.values(counts).reduce((a, b) => a + b, 0);
	if (total === 0) { for (const k in counts) counts[k] = 1; total = 5; }
	const out = {};
	const keys = Object.keys(counts).filter((k) => counts[k] > 0);
	let acc = 0;
	keys.forEach((k, i) => {
		const v = i === keys.length - 1 ? Math.round((1 - acc) * 100) / 100 : Math.round((counts[k] / total) * 100) / 100;
		acc += v;
		out[k] = v;
	});
	return out;
}

const STYLE_NAME = { MELEE: 'Melee', RANGED: 'Ranged', MAGIC: 'Magic' };
const STYLE_BOOST = { MELEE: 'Melee boost', RANGED: 'Ranged boost', MAGIC: 'Magic boost' };

function inventory(classes, needsAntifire, antivenom, hybrid) {
	const inv = [];
	for (const c of classes) {
		inv.push(hybrid ? { category: 'BOOST', slots: 1, combatClass: c, reason: STYLE_BOOST[c] } : { category: 'BOOST', slots: 1, reason: STYLE_BOOST[c] });
	}
	if (needsAntifire) inv.push({ category: 'ANTIFIRE', slots: 1, mandatory: true, reason: 'Dragonfire protection' });
	if (antivenom) inv.push({ category: 'ANTIVENOM', slots: 1, reason: antivenom });
	inv.push({ category: 'PRAYER', slots: { SHORT: 2, NORMAL: 4, LONG: 6 }, reason: 'Prayer upkeep (generic estimate)' });
	inv.push({ category: 'TELEPORT', slots: 1, reason: 'Bank teleport' });
	inv.push({ category: 'FOOD', fill: true, reason: 'Remaining slots' });
	return inv;
}

function build(b) {
	const variants = b.variants
		? b.variants.map(([id, name, phases]) => ({ id, name, phases }))
		: [{ id: 'standard', name: 'Standard', phases: b.phases }];
	const phaseIds = variants[0].phases.map((p) => p[0]);
	for (const v of variants) {
		if (v.phases.map((p) => p[0]).join() !== phaseIds.join()) throw new Error(b.id + ': variants must share phase ids');
	}
	const resolved = variants.map((v) => v.phases.map(([pid, pname, mname, mver]) => ({ pid, pname, m: findMonster(mname, mver), mver })));
	const allMonsters = resolved.flat().map((r) => r.m);
	const multi = phaseIds.length > 1;
	const weights = multi ? b.weights : [1];
	if (!weights || weights.length !== phaseIds.length) throw new Error(b.id + ': weights');
	const wsum = weights.reduce((a, c) => a + c, 0);
	if (Math.abs(wsum - 1) > 0.001) throw new Error(b.id + ': weights sum ' + wsum);
	const needsAntifire = allMonsters.some((m) => m.styles.some((s) => s.toLowerCase().includes('dragonfire')));
	const specialMonsters = [...new Set(allMonsters.filter(special).map((m) => m.name))];

	const strategies = b.styles.map((s) => {
		const def = typeof s === 'string'
			? { id: s.toLowerCase(), name: STYLE_NAME[s], description: STYLE_NAME[s] + ' setup for the whole fight.', styles: Object.fromEntries(phaseIds.map((p) => [p, s])) }
			: s;
		const phaseWeights = Object.fromEntries(phaseIds.map((p, i) => [p, weights[i]]));
		const byWeight = {};
		phaseIds.forEach((p, i) => { byWeight[def.styles[p]] = (byWeight[def.styles[p]] || 0) + weights[i]; });
		const classes = Object.keys(byWeight).sort((x, y) => byWeight[y] - byWeight[x]);
		const out = { id: def.id, name: def.name, description: def.description, phaseStyles: def.styles, phaseWeights };
		if (multi) out.weightsAreHeuristic = true;
		out.inventory = inventory(classes, needsAntifire, b.antivenom, classes.length > 1);
		return out;
	});

	const reasons = [];
	if (multi) reasons.push('The share of the kill spent on each phase is an assumption; compare the per-phase DPS figures.');
	if (specialMonsters.length) reasons.push(`${specialMonsters.join(', ')} ${specialMonsters.length > 1 ? 'have' : 'has'} special damage rules that are not modelled, so DPS is an estimate.`);
	if (b.partial) reasons.push(b.partial);
	const status = reasons.length ? 'PARTIAL' : 'VALIDATED';
	const notes = [
		status === 'VALIDATED'
			? 'Single-phase fight with no unmodelled damage rules; DPS is covered by the reference validation tests.'
			: 'DPS against each phase is covered by the reference validation tests where the rules are modelled.',
		...reasons,
		'Strategies are the attack styles that can damage this boss, best-known first; they are not a strategy guide.',
		'Damage-taken weights for Survivability are derived from the attack styles listed for the boss, equally weighted.',
	];

	const warnings = [...(b.warnings || [])];
	if (needsAntifire) warnings.unshift('Dragonfire protection is mandatory: the planner adds an antifire potion.');
	if (multi) warnings.push('Phase weights are assumptions; compare the per-phase DPS figures rather than only the weighted total.');

	const def = {
		schema: 1,
		id: b.id,
		name: b.name,
		category: b.category,
		wikiUrl: 'https://oldschool.runescape.wiki/w/' + b.wiki,
		dataSource: 'Monster statistics: OSRS Wiki via bundled monsters.json. Strategies: attack styles that can damage the boss (generated by scripts/gen-bosses.mjs).',
		dataVersion: DATA_VERSION,
		validationStatus: status,
		validationNotes: notes.join(' '),
		groupSizes: b.groups || ['SOLO'],
		slayerTaskRelevant: b.slayer ? b.slayer[0] : allMonsters.some((m) => m.slayer),
		...(b.slayer && b.slayer[1] ? { slayerTaskOnly: true } : {}),
		requirements: b.requirements || [],
		warnings,
		damageTaken: damageTaken(allMonsters),
		variants: variants.map((v, vi) => ({
			id: v.id,
			name: v.name,
			phases: resolved[vi].map((r) => {
				const p = { id: r.pid, name: r.pname, monsterId: r.m.id };
				// Some ids are shared by several versions (quest / post-quest / awakened): pin the version.
				if (monsters.filter((m) => m.id === r.m.id).length > 1) p.monsterVersion = r.m.version;
				return p;
			}),
		})),
		strategies,
	};
	return def;
}

// ---------------------------------------------------------------- write
const check = process.argv.includes('--check');
const files = [];
let changed = 0;
for (const b of B) {
	const def = build(b);
	const file = b.id + '.json';
	files.push(file);
	const text = JSON.stringify(def, null, '\t') + '\n';
	const target = path.join(OUT, file);
	const old = fs.existsSync(target) ? fs.readFileSync(target, 'utf8') : null;
	if (old !== text) {
		changed++;
		if (!check) fs.writeFileSync(target, text);
	}
}
const index = JSON.stringify({ schema: 1, bosses: [...HAND_WRITTEN, ...files] }, null, '\t') + '\n';
const indexPath = path.join(OUT, 'index.json');
if (fs.readFileSync(indexPath, 'utf8') !== index) {
	changed++;
	if (!check) fs.writeFileSync(indexPath, index);
}
const tiers = B.map(build).reduce((a, d) => { a[d.validationStatus] = (a[d.validationStatus] || 0) + 1; return a; }, {});
console.log(`${B.length} generated bosses (${JSON.stringify(tiers)}), ${HAND_WRITTEN.length} hand-written; ${changed} file(s) ${check ? 'out of date' : 'written'}`);
if (check && changed) process.exit(1);
