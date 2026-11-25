# Boss Loadout Optimiser

A RuneLite plugin for Old School RuneScape. Pick a boss, and it works out the best equipment and inventory
**from the items you actually own**, shows the DPS maths behind it, highlights what to withdraw, suggests
upgrades, and manages static ground-marker tile packs for the fight.

It is a preparation tool. It never clicks, withdraws, equips, moves, predicts boss attacks or gives
in-fight advice, and it makes no network requests.

> **Status:** builds and passes its automated tests; **not yet tested in the live game and not submitted to
> the Plugin Hub.** See [docs/verification-report.md](docs/verification-report.md) for exactly what has and
> has not been verified.

## Features

| | |
|---|---|
| **Bank analysis** | Reads bank, inventory and worn items through RuneLite's item-container events. Keeps a timestamped snapshot per account; tells you when the bank has not been read or is old. |
| **Requirements** | Each item is *usable*, *unusable* (with the missing level/quest) or *unknown*. Unknown items are flagged, never silently assumed. |
| **DPS engine** | Melee, ranged and magic: effective levels, attack/defence rolls, hit chance, max hit, hit distributions, prayers, potions, set effects, bolt effects, boss weaknesses and immunities. Validated against the OSRS Wiki DPS calculator on 86 loadouts. |
| **Gear optimiser** | Evaluates complete setups (weapon + style + ammunition/spell + every slot), with five priorities: Maximum DPS, Balanced, Survivability, Budget, Low switch complexity. Locks, exclusions, alternatives and per-slot explanations. |
| **Inventory planner** | A 28-slot plan from the boss template and your supplies: switches, boosts, antifire, anti-venom, prayer, runes, teleport, food. Trip length and preferences, locks, a missing-items checklist. |
| **Bank highlighting** | Outlines setup items in the bank: still to withdraw, already carried, not enough. Draw-only. |
| **Tile packs** | Verified static packs for General Graardor, Zulrah and Phantom Muspah; import your own Ground Markers export for any boss; preview; copy JSON; optional confirmed, append-only, undoable install into Ground Markers. |
| **Upgrade adviser** | Items you do not own, ranked by gp per percentage point of DPS, measured on the complete re-optimised loadout, with price source and time. |
| **Boss Profiles** | Save, load, rename, duplicate, delete, export and import complete setups per account. |

**Supported bosses:** Vorkath, Zulrah, Cerberus, General Graardor, Phantom Muspah. Bosses are JSON
definitions; a boss is only offered once its data is validated (see docs/architecture.md â†’ Extending).

## Quick start (Windows)

```powershell
cd C:\Users\Callum.Robertson\Projects\boss-loadout-optimiser
powershell -ExecutionPolicy Bypass -File scripts\test.ps1        # run the tests
powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1   # start RuneLite with the plugin
```

Requires a JDK 11+ (not a JRE). Full instructions, the manual test script and troubleshooting:
[docs/local-setup.md](docs/local-setup.md). macOS/Linux: `./gradlew test`, `./gradlew run`.

Then in the client: log in â†’ **open your bank** â†’ sidebar shield icon â†’ *Boss* â†’ choose a boss, strategy and
priority â†’ **Optimise My Setup** â†’ *Gear*, *Inventory*, *Tiles*, *Upgrades*, *Profiles*.

## Settings

RuneLite â†’ Configuration â†’ Boss Loadout Optimiser: bank highlighting on/off, equipment / supplies
highlighting, quantities, missing-items list, three highlight colours, and *Allow adding packs to Ground
Markers*.

## Documentation

| | |
|---|---|
| [docs/local-setup.md](docs/local-setup.md) | Install, build, launch, manual test script, logs, troubleshooting |
| [docs/architecture.md](docs/architecture.md) | Design, threading, optimisation algorithm, ranking model, persistence |
| [docs/combat-formulas.md](docs/combat-formulas.md) | Every formula and modifier, assumptions, unsupported mechanics |
| [docs/testing.md](docs/testing.md) | Test inventory and the reference validation method |
| [docs/research.md](docs/research.md) | Sources, licences, API decisions |
| [docs/plugin-hub-compliance.md](docs/plugin-hub-compliance.md) | Rule checklist, network and security review, submission steps |
| [docs/verification-report.md](docs/verification-report.md) | Feature-by-feature status with evidence |
| [NOTICE.md](NOTICE.md) | Third-party data and licences |

## Known limitations

- **Not tested in game.** Everything that touches the live client needs your manual test pass.
- **Requirement data is partial by design.** About 290 commonly used items have verified requirements;
  everything else is "unknown". Setups containing unknown items are labelled estimates. Confirm items
  individually ("I can equip this") or switch unknown items off.
- **No verified tile packs for Vorkath or Cerberus** are bundled, because no licensed source was found.
  Import your own.
- **Phase weights are assumptions** for Zulrah (equal thirds) and the Phantom Muspah (40/35/5/20); per-phase
  DPS is always shown. General Graardor's sergeants and group damage sharing are not modelled.
- **Not modelled:** special attacks; ancient, Arceuus and god spells in the optimiser; Barrows and obsidian
  set effects; chinchompas, salamanders, wilderness weapons and a list of niche weapons (see
  docs/combat-formulas.md). Setups using them are shown as estimates, not hidden.
- **Blowpipe darts** cannot be read from the client; choose the dart under *Assumptions* or the blowpipe is
  evaluated without dart strength and labelled an estimate.
- **Ruby bolts** are evaluated at full boss hitpoints (wiki-calculator convention), which flatters them.
- **Survivability** uses weighted defence bonuses and prayer bonus. It is a comparison heuristic, not a
  probability of surviving.
- **Budget** costs cover ammunition, runes and weapon charges only, at the client's cached prices. Prices
  become available a few seconds after you log in; until then Budget ranks by DPS and says so.
- **Upgrades** only consider tradeable items with a price; untradeable upgrades are not suggested.
- **Prayers:** Zeal is not included; prayer unlocks (Piety, Rigour, Auguryâ€¦) cannot be read and are assumed
  when your Prayer level allows them â€” change the prayer under *Assumptions* if you have not unlocked it.
- Installed ground markers appear when the area next loads, because Ground Markers does not watch for
  outside changes.

## Licence

BSD 2-Clause (see [LICENSE](LICENSE)). Bundled game data is derived from the Old School RuneScape Wiki
(CC BY-NC-SA 3.0) and tile coordinates from the Tile Packs plugin (BSD 2-Clause): see [NOTICE.md](NOTICE.md).
Not affiliated with Jagex or RuneLite.
