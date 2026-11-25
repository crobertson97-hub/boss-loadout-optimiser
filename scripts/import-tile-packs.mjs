#!/usr/bin/env node
/*
 * Imports the boss tile packs this plugin ships from the open-source Tile Packs plugin
 * (https://github.com/TrevorMDev/tile-packs, BSD 2-Clause). Coordinates are copied verbatim; nothing
 * is generated or adjusted. Only bosses that this plugin supports are imported.
 *
 * Usage: node scripts/import-tile-packs.mjs <path-to-tile-packs-checkout>
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const checkout = process.argv[2];
if (!checkout) {
	console.error('usage: node scripts/import-tile-packs.mjs <path-to-tile-packs-checkout>');
	process.exit(1);
}
let commit = 'unknown';
try {
	commit = execFileSync('git', ['-C', checkout, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
} catch (e) { /* not a git checkout */ }

const raw = fs.readFileSync(path.join(checkout, 'src/main/resources/com/tilepacks/tilePacks.jsonc'), 'utf8');
const source = JSON.parse(raw.split('\n').filter((l) => !l.trim().startsWith('//')).join('\n'));

/** Source pack name -> how it is filed in this plugin. strategy null = any strategy. */
const wanted = {
	'General Graardor 5:0': { boss: 'general-graardor', strategy: 'ranged', group: false },
	'General Graardor 6:0 5 tick': { boss: 'general-graardor', strategy: 'ranged', group: false },
	'General Graardor 6:0 4 tick': { boss: 'general-graardor', strategy: 'ranged', group: false },
	'General Graardor 7:0': { boss: 'general-graardor', strategy: 'ranged', group: false },
	'General Graardor 9:0': { boss: 'general-graardor', strategy: 'ranged', group: false },
	'General Graador Altar Door': { boss: 'general-graardor', strategy: null, group: true },
	'Muspah safespot': { boss: 'phantom-muspah', strategy: null, group: false },
	Zulrah: { boss: 'zulrah', strategy: null, group: false },
	'Abyssal Sire': { boss: 'abyssal-sire', strategy: null, group: false },
	'Alchemical Hydra (melee)': { boss: 'alchemical-hydra', strategy: 'melee', group: false },
	'Alchemical Hydra (range)': { boss: 'alchemical-hydra', strategy: 'ranged', group: false },
	Duke: { boss: 'duke-sucellus', strategy: null, group: false },
	'Awakened Duke': { boss: 'duke-sucellus', strategy: null, group: false },
	Leviathan: { boss: 'the-leviathan', strategy: null, group: false },
	'Awakened Leviathan': { boss: 'the-leviathan', strategy: null, group: false },
	'Awakened Vardorvis': { boss: 'vardorvis', strategy: null, group: false },
	'Awakened Whisperer': { boss: 'the-whisperer', strategy: null, group: false },
	Barrows: { boss: 'barrows', strategy: null, group: false },
	'Chaos Elemental': { boss: 'chaos-elemental', strategy: null, group: false },
	'Grotesque Guardians': { boss: 'grotesque-guardians', strategy: null, group: false },
	Huey: { boss: 'the-hueycoatl', strategy: null, group: true },
	"K'ril Tsutsaroth 5:0": { boss: 'kril-tsutsaroth', strategy: null, group: false },
	"K'ril Tsutsaroth 6:0": { boss: 'kril-tsutsaroth', strategy: null, group: false },
	"K'ril Tsutsaroth 8:0": { boss: 'kril-tsutsaroth', strategy: null, group: false },
	"K'ril Tsutsaroth Altar Door": { boss: 'kril-tsutsaroth', strategy: null, group: true },
	'Moons of Peril': { boss: 'moons-of-peril', strategy: null, group: false },
	'Yama No Fly': { boss: 'yama', strategy: null, group: false },
	'Zilyana Bowfa': { boss: 'commander-zilyana', strategy: 'ranged', group: false },
	'Zilyana Tbow': { boss: 'commander-zilyana', strategy: 'ranged', group: false },
};

const packs = [];
for (const entry of Object.values(source)) {
	const cfg = wanted[entry.packName];
	if (!cfg) {
		continue;
	}
	const markers = JSON.parse(entry.packTiles).map((t) => {
		for (const k of ['regionId', 'regionX', 'regionY', 'z']) {
			if (!Number.isInteger(t[k])) {
				throw new Error(`${entry.packName}: bad ${k}`);
			}
		}
		const m = { regionId: t.regionId, regionX: t.regionX, regionY: t.regionY, z: t.z };
		if (t.color) m.color = t.color;
		if (t.label) m.label = t.label;
		return m;
	});
	packs.push({
		schema: 1,
		id: `tilepacks-${entry.id}`,
		bossId: cfg.boss,
		strategyId: cfg.strategy,
		name: entry.packName,
		description: `Static ground markers from the Tile Packs plugin pack "${entry.packName}".`
			+ (entry.link ? ` Method reference: ${entry.link}` : ''),
		author: 'TrevorMDev and Tile Packs contributors',
		source: 'https://github.com/TrevorMDev/tile-packs',
		sourceRef: commit,
		licence: 'BSD 2-Clause',
		validationStatus: 'VERIFIED_SOURCE',
		version: 1,
		builtIn: true,
		markers,
	});
}
const missing = Object.keys(wanted).filter((n) => !packs.some((p) => p.name === n));
if (missing.length) {
	throw new Error(`Source packs not found: ${missing.join(', ')}`);
}
packs.sort((a, b) => a.bossId.localeCompare(b.bossId) || a.name.localeCompare(b.name));
const out = {
	meta: {
		schema: 1,
		generated: new Date().toISOString().slice(0, 10),
		source: 'https://github.com/TrevorMDev/tile-packs',
		sourceRef: commit,
		licence: 'BSD 2-Clause, Copyright (c) 2022 TrevorMDev. See NOTICE.md.',
	},
	packs,
};
const file = path.join(root, 'src/main/resources/com/bossoptimiser/tiles/packs.json');
fs.mkdirSync(path.dirname(file), { recursive: true });
fs.writeFileSync(file, JSON.stringify(out, null, '\t') + '\n');
console.log(`wrote ${packs.length} packs (${packs.reduce((n, p) => n + p.markers.length, 0)} markers) to ${file}`);
