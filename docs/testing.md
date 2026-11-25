# Testing

Run everything with `powershell -ExecutionPolicy Bypass -File scripts\test.ps1` (or `./gradlew test`).
Current results are recorded in [verification-report.md](verification-report.md).

## What is automated

| Test class | Kind | Covers |
|---|---|---|
| `combat.ReferenceValidationTest` | reference validation | 364 loadouts recomputed and compared with the OSRS Wiki DPS calculator; every phase and style of every offered boss must have one |
| `combat.BasicRollsTest` | unit | Hand-checkable attack rolls, max hits, accuracy branches, DPS definition |
| `combat.MechanicsTest` | unit | Potion and prayer assumptions, slayer task flag, immunity, ammunition validity, unsupported mechanics are flagged, equipment totals, hit distributions |
| `data.BundledDataTest` | data integrity | Item/monster/spell catalogues, variants, charge states, two-handed flag, ammunition table, boss definitions (and rejection of broken ones), requirement rules name real items and quests, **every supply id re-checked against RuneLite's `ItemID`** |
| `bank.OwnedItemsTrackerTest` | unit | Ownership, quantities, no double counting across containers, items no longer owned, account switch, staleness, persistence codec incl. garbage input |
| `account.EligibilityCheckerTest` | unit | Usable / unusable / unknown, multi-skill requirements, quests, worn and confirmed items, variants |
| `optimiser.GearOptimiserTest` | unit | Whole-setup optimality (checked against every single-slot swap), two-handed weapons, ammunition compatibility, unusable and unknown items, exclusions and locks, Void set effect, slayer helmet on/off task, all priorities, budget costs, hybrid strategies and switch limits, autocast spell choice, reproducibility, cancellation, **performance with every item in the game owned** |
| `inventory.InventoryPlannerTest` | unit | 28-slot limit, potion doses, quantities never overstated, mandatory supplies, missing-item checklist, trip length and preferences, locks and exclusions, food order, minimum cost, switches, runes with and without a pouch |
| `tiles.TilePackTest` | unit | Ground Markers JSON parse/export round trip, **compatibility with RuneLite's own Gson**, unknown fields preserved, legacy colours, malformed input, built-in pack provenance, custom pack CRUD and persistence, install (append-only, dedupe), undo, unreadable existing markers, failed writes |
| `profiles.ProfileStoreTest` | unit | Persistence across a restart, rename / duplicate / delete, export / import, per-account separation, schema migration, hostile imports, name sanitising |
| `upgrades.UpgradeAdviserTest` | unit | Upgrades measured on the complete re-optimised loadout, budget filter, unavailable prices, ammunition dependencies, slot alternatives and explanations, bank withdraw list |
| `ServiceIntegrationTest` | integration | Start-up, bank updates, boss selection, optimisation, inventory, bank highlight state, tile pack export / import / install / undo, profile save → restart → load, account switching, upgrades, shutdown |
| `PluginLifecycleTest` | integration (mocked client) | The real `BossOptimiserPlugin`: start-up registers the sidebar button and both overlays, login reads levels, the bank container is read (noted items folded, placeholders skipped) and stored per account, shutdown removes everything, late events are harmless |
| `ui.PanelSmokeTest` | integration (headless) | The real sidebar is constructed and rendered in every state of the journey without exceptions or a section falling back to its error placeholder |
| `PerformanceBenchmarkTest` | benchmark (opt-in, skipped by default) | Times every boss, strategy and priority, slot alternatives, explanations and the upgrade adviser against a real bank snapshot, and prints the chosen setups so two versions can be compared. Set `BOSS_BENCH_BANK` to a file holding a `bank.items` value (`id:qty,id:qty,...`), then run `gradlew test --tests "*PerformanceBenchmarkTest" -i`. Bank snapshots are personal data: keep them out of the repository. |
| `ui.PanelRenderTest` | integration (headless) | Renders every sidebar page at the real 225 px width to `build/ui-preview/*.png` for a visual layout check |

## Reference validation

**Reference:** the OSRS Wiki DPS calculator, <https://tools.runescape.wiki/osrs-dps/>, source
[weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) at commit
`3da927f4280c73154fa59e4e90eb374af5123d32` (the same commit the bundled data comes from).

**Method.** The calculator's own classes were executed locally (through a small Jest harness that builds the
player and monster with the calculator's own `PlayerStore` and `MonsterStore`, as the website does) on the scenario list in `scripts/reference/gen-scenarios.mjs`. The harness
resolves item and monster names to ids, runs `PlayerVsNPCCalc`, and writes the resolved inputs and results.
`scripts/reference/trim-results.mjs` turns that into `src/test/resources/reference/reference-scenarios.json`.
`ReferenceValidationTest` rebuilds each scenario from the ids and compares:

| Value | Tolerance |
|---|---|
| max attack roll, NPC defence roll, max hit, attack interval | exact |
| accuracy, expected damage per attack, DPS | relative 1e-9 |

The harness itself is not part of this repository: it links against GPL-3.0 code. The scenario generator and
the resulting numbers are. To refresh the fixture after a game update: clone osrs-dps-calc, install with the
repository's vendored yarn (`node .yarn/releases/yarn-*.cjs install --immutable --mode=skip-build`; plain `npm install`
fails on its patched dependency), add a Jest test that reads the scenarios, builds each player and monster through
`PlayerStore` / `MonsterStore`, runs `PlayerVsNPCCalc` and writes `{id, resolved, results}` per scenario; then run
`node scripts/reference/trim-results.mjs <results.json>`. Do not build monsters with the calculator's `getTestMonster`
test helper: it leaves the current hitpoints at 150, which gives wrong ruby-bolt figures; default to full hitpoints.
The calculator's own defaults also have *on slayer task* and *Kandarin diary* switched **on**, so every scenario here sets both explicitly.

**Inputs.** 364 scenarios. 89 hand-written ones (27 melee, 37 ranged, 25 magic) against Vorkath, all three Zulrah
forms, Cerberus, General Graardor and four Phantom Muspah forms (plus an Abyssal demon baseline); 257 generated
ones, one per phase and style of every generated boss with a top-end setup (Torva + fang/whip/bludgeon by the
weakest melee defence, Masori + twisted bow, Ancestral + Tumeken's shadow) plus Arclight against demons, dragon
hunter lance/crossbow against dragons, salve (ei) against undead and the slayer helmet on task; and 18 targeted
special-rule checks (`x-*`: Kraken, Leviathan, Dusk, Dawn, Kree'arra and Corporeal Beast). Unless stated, levels are 99.

| Area | Scenarios exercise |
|---|---|
| Melee | whip baseline; dragon hunter lance (stab/slash/crush); salve (e)/(ei); slayer helmet on task; Osmumten's fang (aggressive + accurate); scythe (3 hits, size 4 and 5); Arclight / Emberlight / burning claws demonbane; Inquisitor's set; bludgeon, rapier, saeldor, tentacle, godsword, zombie axe, colossal blade, sulphur blades (two hits); melee void; Piety, Chivalry, separate attack/strength prayers; mid-level accounts; halberd vs Zulrah and melee immunity |
| Ranged | twisted bow vs every boss; dragon hunter crossbow with diamond / ruby (full and partial HP, Kandarin diary) / opal / plain bolts, with salve and with slayer helmet (additive rule); Armadyl and Zaryte crossbows; pearl, dragonstone and onyx bolts; bow of Faerdhinen with and without crystal armour; blowpipe with three dart types; void and elite void; scorching bow; dark bow; venator bow; magic shortbow; rune crossbow at 75 Ranged; quiver on and off; Rigour, Deadeye, Eagle Eye; wrong ammunition |
| Magic | Tumeken's shadow vs every boss; tridents; sanguinesti; Kodai with fire / earth / water / wind surge and matching tomes and weaknesses; harmonised staff speed; smoke battlestaff; brimstone ring; confliction gauntlets; elite void mage; slayer helmet; salve (ei); Eye of Ayak; warped sceptre; Virtus with Ice Barrage; Augury, Mystic Might, Mystic Vigour; mid-level accounts; fire bolt at 50 Magic |

**Expected vs actual.** All 364 scenarios match within the tolerances above; there are **no known differences**.
Scenarios against monsters with unmodelled rules (for example Abyssal Sire, Araxxor) still match the reference
figures, and the test also checks that the engine labels them as estimates.
Five examples (full list in the fixture):

| Scenario | Attack roll | Defence roll | Max hit | Accuracy | DPS |
|---|---|---|---|---|---|
| Dragon hunter lance, Torva, torture, Piety, super combat vs Vorkath (`m02`) | 38,880 | 20,070 | 60 | 74.188% | 9.2786 |
| Twisted bow, Masori (f), quiver, Rigour, ranging potion vs Zulrah (blue) (`r01`) | 59,441 | 19,776 | 50 | 83.364% | 9.3140 |
| Dragon hunter crossbow, diamond dragon bolts (e), Rigour vs Vorkath (`r08`) | 63,133 | 20,070 | 70 | 84.104% | 8.8667 |
| Tumeken's shadow, Ancestral, Augury, +13 Magic vs Muspah (melee form) (`g03`) | 83,956 | 15,582 | 66 | 90.719% | 9.9836 |
| Kodai wand, Fire Surge, tome of fire vs Zulrah (green) (`g10`) | 51,181 | 5,871 | 50 | 94.263% | 7.8614 |

The engine produces exactly these figures (same integers; decimals equal to nine significant figures).

**Formula assumptions shared with the reference:** ruby bolts use the monster's current hitpoints (full by
default); potion boosts are at full value; no special attacks; single target.

**Known unsupported mechanics** are listed at the end of [combat-formulas.md](combat-formulas.md). They are not
part of the fixture; `MechanicsTest` checks that using them marks the result as an estimate.

## What cannot be automated here

A RuneLite plugin's in-game behaviour can only be confirmed by a person playing: automating input to the
game violates Jagex's third-party client rules. The following therefore have **logic** tests but no
in-client test, and are listed as *Untested (manual)* in the verification report:

- Item container events actually firing as assumed (bank open/close, withdraw, equip).
- Quest state and level reads on the client thread.
- Sidebar appearance at real size, icons loading, dialogs and clipboard.
- Bank overlay position and colours; the missing-items list.
- Ground Markers picking up installed markers after a scene load; undo.
- Config persistence across a real client restart and account switch.
- Prices from `ItemManager`.

Use the script in [local-setup.md](local-setup.md#5-manual-test-script).
