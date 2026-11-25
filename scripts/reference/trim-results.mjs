#!/usr/bin/env node
/*
 * Converts the raw output of the reference-calculator harness into the compact fixture used by
 * ReferenceValidationTest. Usage: node scripts/reference/trim-results.mjs <results.json>
 * See docs/testing.md for how the raw results are produced.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const raw = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const scenarios = raw.map((r) => {
	if (r.error) {
		throw new Error(`${r.id}: ${r.error}`);
	}
	const eq = {};
	let dartId = 0;
	for (const [slot, piece] of Object.entries(r.resolved.equipment)) {
		if (piece) {
			eq[slot] = piece.id;
			if (piece.itemVars && piece.itemVars.blowpipeDartId) {
				dartId = piece.itemVars.blowpipeDartId;
			}
		}
	}
	const s = r.resolved.skills;
	const b = r.resolved.boosts;
	return {
		id: r.id,
		monsterId: r.resolved.monster.id,
		monsterVersion: r.resolved.monster.version || '',
		monsterName: `${r.resolved.monster.name}${r.resolved.monster.version ? ` (${r.resolved.monster.version})` : ''}`,
		monsterCurrentHp: r.resolved.monsterCurrentHp,
		equipment: eq,
		dartId,
		style: r.resolved.style,
		spell: r.resolved.spell || null,
		skills: { atk: s.atk, str: s.str, def: s.def, ranged: s.ranged, magic: s.magic, prayer: s.prayer, hp: s.hp },
		boosts: { atk: b.atk, str: b.str, ranged: b.ranged, magic: b.magic },
		prayers: r.resolved.prayers,
		onSlayerTask: !!r.resolved.buffs.onSlayerTask,
		kandarinDiary: !!r.resolved.buffs.kandarinDiary,
		expected: {
			maxAttackRoll: r.results.maxAttackRoll,
			npcDefRoll: r.results.npcDefRoll,
			accuracy: r.results.accuracy,
			maxHit: r.results.maxHit,
			expectedHit: r.results.expectedHit,
			attackSpeed: r.results.attackSpeed,
			dps: r.results.dps,
		},
	};
});
const out = {
	meta: {
		generated: new Date().toISOString().slice(0, 10),
		reference: 'OSRS Wiki DPS calculator (https://github.com/weirdgloop/osrs-dps-calc)',
		referenceCommit: raw[0].calcCommit || 'unknown',
		note: 'Reference figures only. Produced by running the calculator on the scenarios in scripts/reference/gen-scenarios.mjs.',
	},
	scenarios,
};
const file = path.join(root, 'src/test/resources/reference/reference-scenarios.json');
fs.writeFileSync(file, JSON.stringify(out, null, '\t') + '\n');
console.log(`wrote ${scenarios.length} reference scenarios to ${file}`);
