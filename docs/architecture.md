# Architecture

```
RuneLite client ──events──▶ BossOptimiserPlugin ──▶ BossOptimiserService ◀── UiController ── sidebar (ui/*)
 (client thread)            (thin adapter)           (application core)                       (Swing thread)
                                 │                        │  single background worker
                                 ▼                        ▼
                         client/ overlays        optimiser / inventory / upgrades / tiles / profiles
                                                         │
                                                  combat (DPS engine)  ◀── data (bundled JSON catalogues)
```

## Packages

| Package | Responsibility | Depends on RuneLite? |
|---|---|---|
| `data` | `GameData`: items, monsters, spells, weapon styles, ammunition table, loaded from bundled JSON | no (Gson only) |
| `combat` | `DpsCalculator`, hit distributions, potions, prayers, `Mechanics` (set detection + list of unmodelled mechanics) | no |
| `bosses` | `BossDefinition` (JSON), `BossRegistry` (load + validate) | no |
| `account` | `AccountSnapshot`, curated `RequirementData`, three-valued `EligibilityChecker` | no |
| `bank` | `OwnedItemsTracker` / `OwnedItems` snapshots, `WithdrawList` for highlights | no |
| `optimiser` | `GearOptimiser`, `SlotAnalyser`, `OperatingCostModel`, request/result types | no |
| `inventory` | `SupplyCatalogue`, `InventoryPlanner`, `InventoryPlan` | no |
| `tiles` | `TilePack`, `GroundMarkerCodec`, `TilePackLibrary`, `GroundMarkerInstaller` | no |
| `profiles` | `BossProfile`, `ProfileStore` (persist, migrate, validate) | no |
| `upgrades` | `UpgradeAdviser` | no |
| `util` | `KeyValueStore` abstraction over config storage | no |
| `ui` | Sidebar. Talks only to the `UiController` interface | Swing + RuneLite UI classes |
| `client` | Bank overlays | yes |
| root | `BossOptimiserPlugin` (events, environment), `BossOptimiserService`, `BossOptimiserConfig` | plugin only |

Everything except `ui`, `client` and the plugin class is plain Java and is unit-tested without a client.
`BossOptimiserService` receives an `Environment` (storage, prices, icons, clock, UI executor); the plugin
supplies the real one and `FakeEnvironment` supplies the test one. That is why the whole user journey can be
tested end to end (`ServiceIntegrationTest`).

**Change from the suggested layout:** resources live under `src/main/resources/com/bossoptimiser/{items,bosses,tiles}`
rather than top-level `items/`, `bosses/`, `tiles/`, because every Plugin Hub plugin is loaded into the same
client and un-namespaced resource paths can collide. There is no `icons/` folder: the sidebar icon is drawn
in code and item icons come from the client.

## Threading

| Work | Thread |
|---|---|
| Reading item containers, levels, quest states; overlay rendering | client thread |
| Parsing datasets, optimisation, slot analysis, upgrade adviser | one daemon worker (`boss-loadout-optimiser`) |
| Rendering the sidebar | Swing thread |

- The service publishes immutable `UiState` snapshots; the sidebar never reads mutable state.
- Each optimise/upgrade request takes a generation number. Starting a new request, changing the boss or
  switching account bumps it: queued work is skipped and a running search stops at its next poll (every
  1,024 evaluations). This is both the debounce and the cancel button.
- `shutDown()` calls `service.shutdown()` and `worker.shutdownNow()`; nothing is awaited.

## Performance

Measured on a real 996-item bank (572 equipable items), every boss, strategy and priority
(`PerformanceBenchmarkTest`, opt-in; see docs/testing.md):

| Priority | Typical | Worst case |
|---|---|---|
| Maximum DPS, Budget, Low switch complexity | 5–30 ms | about 100 ms |
| Balanced | about 0.6 s | about 1.2 s |
| Survivability | about 0.5 s | about 1.2 s |
| Slot alternatives / explanations | 1–30 ms | |
| Upgrade adviser | 0.2–0.5 s | |

Before the 2026-11-25 optimisation pass, Balanced and Survivability averaged 1.2 s with a worst case of
1.9 s, and some of them hit the search limit. The pass (exact pruning on the weighted defence value,
branch-and-bound, constant-time "is this item worn" checks) gave the same or a better setup in every
case: 27 identical, 6 better because the old search had been trimmed.

While playing, the bank overlay reads its settings once and refreshes them only when a setting changes,
instead of reading the config for every bank item on every frame.

## Bank snapshot

`OwnedItemsTracker` keeps the last contents of the bank, inventory and worn equipment for the current account.
The three containers are disjoint in the game, so the owned quantity of an id is their sum. The bank is only
known after it has been opened; `OwnedItems.hasBankData()` is false until then and the UI says so. The bank
snapshot (ids + quantities + timestamp) is stored per RuneScape account in RuneLite's config and restored at
login, with its age shown. Switching account discards the in-memory snapshot before anything else happens.

## Requirements: usable / unusable / unknown

`EligibilityChecker` answers `USABLE` only with evidence: a curated rule that is met, the item being worn
right now, the item being ordinary ammunition, or the player's explicit "I can equip this". A rule that is
not met gives `UNUSABLE` (and cannot be overridden). Everything else is `UNKNOWN`: by default such items are
still considered but the setup is labelled an estimate and the items are marked; a checkbox excludes them.

## Gear optimisation

For each combat class the strategy uses:

1. **Candidate pool** – owned, equippable items by slot (cosmetic variants folded onto the base item).
2. **Weapon options** – every (weapon, attack style, ammunition | spell) the class can use; defensive styles
   are skipped; weapons the boss is immune to are skipped; ranged weapons without owned compatible
   ammunition are skipped with a warning. Autocast candidates are the standard elemental spells of the
   highest class the Magic level allows.
3. **Shortlist** – each option is scored quickly with a greedy fill, grouped by weapon+ammunition+spell
   (best style per group), and the best 6 groups (max 3 per weapon) go to the full search.
4. **Pruning** – per slot, an item is dropped if another owned item is at least as good on every relevant
   statistic: accuracy for the attack type and the damage statistic, plus, for Balanced/Survivability, the
   item's contribution to the score's defence part (one weighted sum of its defence bonuses and prayer
   bonus; exact because both scores are linear in total defence and total prayer). Items whose offence is
   zero or negative and that add nothing else leave the slot empty. Items that can trigger an effect are never dropped: salve amulets
   vs undead, slayer helmets on task, crystal armour with crystal bows, Inquisitor's with crush, Dizana's
   quiver, brimstone ring, confliction gauntlets, tomes matching the spell.
5. **Enumeration** – a depth-first search evaluates combinations of the remaining candidates with the real
   DPS engine, capped at 20,000 combinations per option (lowest-ranked candidates are trimmed and the result
   says the search was bounded). For Balanced/Survivability it is a branch-and-bound: a branch is skipped
   when even this weapon option's best DPS (known exactly from the DPS pass) plus the most defence the
   remaining slots could add cannot beat the third-best setup already found. A second enumeration forces
   the complete Void set when it is owned.
6. **Hybrid strategies** – the heaviest-weighted class is worn; for the others, items not in the worn
   setup are switches. If there are more than the limit (8, or 4 for *Low switch complexity*), slots are
   reverted to the worn item one at a time, always choosing the reversion that costs the least DPS.

### Ranking model

| Priority | Score |
|---|---|
| Maximum DPS | phase-weighted DPS; defence only breaks ties |
| Balanced | `0.75·DPS/bestDPS + 0.20·defence/defenceBound + 0.05·prayer/prayerBound` |
| Survivability | among setups with DPS ≥ 85% of the best: `0.8·defence/bound + 0.2·prayer/bound` |
| Budget | `DPS/bestDPS − 0.10 × (running cost in millions of gp per hour)` |
| Low switch complexity | Maximum DPS with at most 4 switch items |

*Defence* is the sum of equipment defence bonuses weighted by the boss definition's `damageTaken` shares.
It is a **heuristic for comparing gear**, not a survival probability, and the UI labels it as such.
*Running cost* (`OperatingCostModel`) counts ammunition lost (20% with an assembler or quiver, 28% with an
accumulator, 100% otherwise), runes per cast and weapon charges, priced from the client's price cache. It is
an estimate and excludes armour degradation, potions and food.

Results are deterministic: candidates are processed in id order and ties are broken by a fixed comparator.

## Inventory planning

Strategy JSON lists rules (`BOOST`, `ANTIFIRE`, `PRAYER` with slots per trip length, `RUNE_POUCH` with a spell
and cast count, `FOOD` fill…). The planner places locked slots, then switches, then each rule using the
best-preferred supply family the bank can actually fill (highest dose first), reserving quantities so the
same items are never promised twice. Anything short becomes a missing-item entry. No kills-per-trip figure
is produced.

## Persistence and migration

All storage is RuneLite config under group `bossloadoutoptimiser`:

| Key | Scope | Content |
|---|---|---|
| `bank.items`, `bank.capturedAt` | account | bank snapshot: written on the first bank read of a session, when the bank is closed, and at most once a minute while banking (never on every withdrawal), and only into the account it belongs to |
| `profiles.index`, `profile.<id>`, `profiles.last` | account | Boss Profiles (JSON, schema 2) |
| `tilepacks.index`, `tilepack.<id>` | RuneLite profile | custom tile packs |
| `tileinstall.<packId>` | RuneLite profile | which markers an install added (for undo) |
| settings (`highlightBank`, …) | RuneLite profile | `BossOptimiserConfig` |

One key per profile/pack means a failed or partial write cannot damage other entries; unreadable entries are
skipped and left in place. `ProfileStore.migrate` upgrades older schemas step by step; a profile from a newer
plugin version is refused with a message.

## Extending

- **New boss:** add `bosses/<id>.json`, list it in `bosses/index.json`. `BossRegistry.validate` rejects
  unknown monsters, missing phase styles and weights that do not sum to 1. Set `validationStatus` honestly;
  `UNVALIDATED` bosses are never offered. Add reference scenarios for the boss before marking it validated.
- **Game update:** `node scripts/update-data.mjs`, then run the tests; `ReferenceValidationTest` shows what moved.
- **New mechanic:** implement it in `combat`, remove it from `Mechanics.UNSUPPORTED_*`, add scenarios.
