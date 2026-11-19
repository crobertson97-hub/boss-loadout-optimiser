#!/usr/bin/env node
/*
 * Regenerates the bundled combat datasets from the OSRS Wiki DPS calculator's published JSON
 * (https://github.com/weirdgloop/osrs-dps-calc, cdn/json), which is itself generated from the
 * Old School RuneScape Wiki (CC BY-NC-SA 3.0).
 *
 * Only data (game facts) is taken from that project; no code is copied into the plugin.
 *
 * Usage:
 *   node scripts/update-data.mjs                      # download from GitHub (default ref: main)
 *   node scripts/update-data.mjs --ref <commit>       # download a pinned commit
 *   node scripts/update-data.mjs --source <checkout>  # read a local osrs-dps-calc checkout
 *
 * Run this after an OSRS balance change, then run the test-suite: the reference validation
 * tests will tell you whether the combat engine still matches.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outDir = path.join(root, 'src', 'main', 'resources', 'com', 'bossoptimiser', 'items');

const args = process.argv.slice(2);
const arg = (name) => {
	const i = args.indexOf(name);
	return i >= 0 ? args[i + 1] : undefined;
};
const source = arg('--source');
let ref = arg('--ref') || 'main';

async function load(rel) {
	if (source) {
		return fs.readFileSync(path.join(source, rel), 'utf8');
	}
	const url = `https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/${ref}/${rel}`;
	const res = await fetch(url);
	if (!res.ok) {
		throw new Error(`GET ${url} -> ${res.status}`);
	}
	return res.text();
}

if (source) {
	try {
		ref = execFileSync('git', ['-C', source, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
	} catch (e) {
		ref = 'local-checkout';
	}
}

const SLOTS = ['head', 'cape', 'neck', 'ammo', 'weapon', 'body', 'shield', 'legs', 'hands', 'feet', 'ring'];

function compactItems(equipment) {
	const rows = [];
	for (const e of equipment) {
		if (!SLOTS.includes(e.slot)) {
			throw new Error(`Unknown slot ${e.slot} for ${e.name}`);
		}
		rows.push([
			e.id, e.name, e.version || '', e.slot, e.category || '', e.speed || 0, e.isTwoHanded ? 1 : 0,
			e.offensive.stab, e.offensive.slash, e.offensive.crush, e.offensive.magic, e.offensive.ranged,
			e.defensive.stab, e.defensive.slash, e.defensive.crush, e.defensive.magic, e.defensive.ranged,
			e.bonuses.str, e.bonuses.ranged_str, e.bonuses.magic_str, e.bonuses.prayer,
		]);
	}
	rows.sort((a, b) => a[0] - b[0]);
	return rows;
}

function compactMonsters(monsters) {
	const out = [];
	for (const m of monsters) {
		out.push({
			id: m.id,
			name: m.name,
			version: m.version || '',
			level: m.level,
			size: m.size,
			speed: m.speed,
			styles: m.style || [],
			maxHit: String(m.max_hit ?? ''),
			skills: [m.skills.atk, m.skills.str, m.skills.def, m.skills.magic, m.skills.ranged, m.skills.hp],
			offensive: [m.offensive.atk, m.offensive.str, m.offensive.magic, m.offensive.magic_str, m.offensive.ranged, m.offensive.ranged_str],
			defensive: [m.defensive.stab, m.defensive.slash, m.defensive.crush, m.defensive.magic,
				m.defensive.light, m.defensive.standard, m.defensive.heavy, m.defensive.flat_armour || 0],
			attributes: m.attributes || [],
			slayer: !!m.is_slayer_monster,
			weakness: m.weakness ? [m.weakness.element, m.weakness.severity] : null,
		});
	}
	out.sort((a, b) => a.id - b.id || a.version.localeCompare(b.version));
	return out;
}

/** Parses the weapon-category -> combat style table out of the calculator's utils.ts. */
function parseStyles(utilsTs, categoryEnumTs) {
	const enumNames = {};
	for (const m of categoryEnumTs.matchAll(/^\s*([A-Z0-9_]+)\s*=\s*'([^']*)'/gm)) {
		enumNames[m[1]] = m[2];
	}
	const start = utilsTs.indexOf('getCombatStylesForCategory');
	const body = utilsTs.slice(start, utilsTs.indexOf('export', start + 10));
	const result = {};
	let pending = [];
	let styles = [];
	for (const raw of body.split('\n')) {
		const line = raw.trim();
		const c = line.match(/^case EquipmentCategory\.([A-Z0-9_]+):/);
		if (c) {
			pending.push(c[1]);
			continue;
		}
		if (line.startsWith('default:')) {
			pending.push('__default__');
			continue;
		}
		if (line.startsWith('//')) {
			continue;
		}
		const s = line.match(/name:\s*'([^']+)'.*?type:\s*(?:'([^']+)'|null).*?stance:\s*(?:'([^']+)'|null)/);
		if (s) {
			styles.push({ name: s[1].replace(/\d+$/, ''), type: s[2] || null, stance: s[3] || null });
			continue;
		}
		if (line.startsWith('break;') || line.startsWith('return')) {
			for (const p of pending) {
				if (p !== '__default__' && enumNames[p] !== undefined) {
					result[enumNames[p]] = styles.filter((x) => x.type !== null);
				}
			}
			pending = [];
			styles = [];
		}
	}
	return result;
}

const equipment = JSON.parse(await load('cdn/json/equipment.json'));
const monsters = JSON.parse(await load('cdn/json/monsters.json'));
const spells = JSON.parse(await load('cdn/json/spells.json'));
const aliases = JSON.parse(await load('cdn/json/equipment_aliases.json'));
const utilsTs = await load('src/utils.ts');
const categoryTs = await load('src/enums/EquipmentCategory.ts');

/**
 * Extracts the weapon id -> usable ammunition ids table. The table is plain data embedded in the
 * calculator's Equipment.ts, so the two literal blocks are evaluated after stripping type annotations.
 */
function parseAmmo(equipmentTs) {
	const start = equipmentTs.indexOf('const commonAmmoCategories');
	const end = equipmentTs.indexOf('export enum AmmoApplicability');
	if (start < 0 || end < 0) {
		throw new Error('Could not locate the ammunition table');
	}
	const code = equipmentTs.slice(start, end)
		.replace(/:\s*\{\s*\[\w+:\s*\w+\]:\s*number\[\]\s*\}/g, '');
	// eslint-disable-next-line no-new-func
	const table = new Function(`${code}; return ammoForRangedWeapons;`)();
	const groups = [];
	const index = new Map();
	const weapons = {};
	for (const [weaponId, ammo] of Object.entries(table)) {
		const key = [...ammo].sort((a, b) => a - b).join(',');
		if (!index.has(key)) {
			index.set(key, groups.length);
			groups.push(key === '' ? [] : key.split(',').map(Number));
		}
		weapons[weaponId] = index.get(key);
	}
	return { groups, weapons };
}

const equipmentTs = await load('src/lib/Equipment.ts');
const ammo = parseAmmo(equipmentTs);
const styles = parseStyles(utilsTs, categoryTs);
const categoriesInData = [...new Set(equipment.filter((e) => e.slot === 'weapon').map((e) => e.category))];
const missing = categoriesInData.filter((c) => c && !styles[c] && !styles[c[0].toUpperCase() + c.slice(1)]);
if (missing.length) {
	console.warn('WARNING: weapon categories without a parsed style table:', missing);
}

const variantToCanonical = {};
// cdn/json/equipment_aliases.json is already variant id -> canonical id.
for (const [variant, canonical] of Object.entries(aliases)) {
	variantToCanonical[variant] = Number(canonical);
}

const meta = {
	schema: 1,
	generated: new Date().toISOString().slice(0, 10),
	source: 'https://github.com/weirdgloop/osrs-dps-calc (cdn/json), generated from the Old School RuneScape Wiki',
	sourceRef: ref,
	licence: 'Data derived from the OSRS Wiki, CC BY-NC-SA 3.0. See NOTICE.md.',
	counts: { items: equipment.length, monsters: monsters.length, spells: spells.length },
};

fs.mkdirSync(outDir, { recursive: true });
const write = (name, value) => {
	fs.writeFileSync(path.join(outDir, name), JSON.stringify(value) + '\n');
	console.log(`wrote ${name} (${(fs.statSync(path.join(outDir, name)).size / 1024).toFixed(0)} KB)`);
};
write('items.json', { meta, columns: ['id', 'name', 'version', 'slot', 'category', 'speed', 'twoHanded',
	'astab', 'aslash', 'acrush', 'amagic', 'aranged', 'dstab', 'dslash', 'dcrush', 'dmagic', 'dranged',
	'str', 'rstr', 'mdmg', 'prayer'], rows: compactItems(equipment), aliases: variantToCanonical });
write('monsters.json', { meta, monsters: compactMonsters(monsters) });
write('spells.json', { meta, spells: spells.map((s) => ({ name: s.name, maxHit: s.max_hit, spellbook: s.spellbook, element: s.element || null })) });
write('weapon-styles.json', { meta, styles });
write('ammo.json', { meta, groups: ammo.groups, weapons: ammo.weapons });
fs.writeFileSync(path.join(outDir, 'data-version.json'), JSON.stringify(meta, null, '\t') + '\n');
console.log('data version', meta);
