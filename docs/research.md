# Research report

Research carried out on 2026-11-19 before and during implementation. Everything here was confirmed by
reading the source at the commits listed, not from memory.

## Sources inspected

| Source | Commit / version | Licence | Used for |
|---|---|---|---|
| [runelite/runelite](https://github.com/runelite/runelite) | `0fa3b8489e` (client 1.13.7) | BSD 2-Clause | API signatures, Ground Markers plugin, overlays, config, item manager |
| [runelite/example-plugin](https://github.com/runelite/example-plugin) | `46e14f2c44` | BSD 2-Clause | Build template, development launcher, the `AGENTS.md` Plugin Hub rules |
| [runelite/plugin-hub](https://github.com/runelite/plugin-hub) | current `master` | BSD 2-Clause | Submission format, existing gear / tile plugins (precedent) |
| [runelite/plugin-hub-tooling](https://github.com/runelite/plugin-hub-tooling) | current | BSD 2-Clause | How submissions are built and verified |
| [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) | `3da927f428` | **GPL-3.0** | Data files (wiki-derived) and as a *reference oracle* for tests. No code copied. |
| [Diogo-G-Dias/bis-gear-plugin](https://github.com/Diogo-G-Dias/bis-gear-plugin) | current | BSD 2-Clause | Prior art for a gear sidebar on the hub |
| [buderw/osrs-dps-optimize](https://github.com/buderw/osrs-dps-optimize) | current | **GPL-3.0** | Prior art for optimisation; not used |
| [TrevorMDev/tile-packs](https://github.com/TrevorMDev/tile-packs) | `3f3f87c7d7` | BSD 2-Clause | Verified tile coordinates for bundled packs |
| [OSRS Wiki](https://oldschool.runescape.wiki/) | n/a | CC BY-NC-SA 3.0 | Game facts: requirements, boss access, supplies |

## Confirmed findings and the decisions they led to

### Build and launch
- Plugin Hub plugins must **target Java 11** and keep `build.gradle` in the shape of the example template
  (`compileOnly net.runelite:client`, Lombok, JUnit 4, `options.release = 11`). The template ships Gradle 8.10.
- The supported local workflow is a `run` task that starts `RuneLite.main` after
  `ExternalPluginManager.loadBuiltin(Plugin.class)` from the test source set. A plugin JAR cannot be dropped
  into a release client. This project uses exactly that (`BossOptimiserPluginTest`, `gradlew run`).
- Any JDK 11+ can build it (`release 11` cross-compiles). A JRE is not enough.

### APIs used (all public, no reflection)
| Need | API | Notes |
|---|---|---|
| Bank / inventory / equipment contents | `ItemContainerChanged`, `Client.getItemContainer(int)` with `gameval.InventoryID.BANK / INV / WORN` | The bank container only exists after the bank has been opened in the session. |
| Real item ids | `ItemManager.canonicalize(int)`, `ItemComposition.getPlaceholderTemplateId()` | Un-notes items; placeholders are skipped. |
| Levels | `Client.getRealSkillLevel(Skill)`, `StatChanged` | |
| Quests | `Quest.getState(Client)` | Runs a client script, so it is called on the client thread only. |
| Per-account storage | `ConfigManager.getRSProfileKey()`, `get/set/unsetRSProfileConfiguration`, `RuneScapeProfileChanged` | Account key never leaves the client. |
| Sidebar | `PluginPanel`, `NavigationButton`, `ClientToolbar` | |
| Bank highlighting | `WidgetItemOverlay.showOnBank()` | Draw-only. |
| Bank open/closed | `WidgetLoaded` / `WidgetClosed` with `gameval.InterfaceID.BANKMAIN` | |
| Item icons and prices | `ItemManager.getImage`, `ItemManager.getItemPrice` | Prices come from the client's own cache; the plugin makes no request. |
| Equipment statistics | `ItemManager.getItemStats` exists but has **no weapon category / attack styles and no monster data**, so a bundled dataset is required. |

### Ground Markers
- `GroundMarkerPoint` is `{regionId, regionX, regionY, z, color, label}`; equality ignores colour and label.
- Stored in config group `groundMarker`, key `region_<regionId>`, value = JSON array of points.
- Import (`GroundMarkerSharingManager`) reads a JSON array from the clipboard, groups by region, appends
  points that are not already present, saves, reloads. Colours serialise as `#AARRGGBB` with the client's Gson
  (verified in `TilePackTest.exportRoundTripsAndIsReadableByRuneLitesOwnGson`).
- Instances: markers are stored against the template region and translated with
  `WorldPoint.toLocalInstance`, so the same JSON works for instanced bosses. No special handling is needed.
- The plugin does **not** listen for config changes to region keys: points written from outside appear when
  the scene next loads. The sidebar says so.
- **Decision:** both paths are implemented. (1) *Copy JSON* + written import instructions: always available,
  uses Ground Markers' own importer. (2) *Add to Ground Markers…*: a user-confirmed write through the public
  `ConfigManager` API that applies the same merge rule as the built-in importer, verifies the write,
  records what it added and can undo exactly that. It can be disabled in settings. Whether a hub plugin
  may write another plugin's config group is a **maintainer question** (see plugin-hub-compliance.md).

### Plugin Hub rules that shaped the design
From the template's `AGENTS.md` (Jagex third-party client guidelines + RuneLite rejected features):
- No reflection, JNI, external processes, runtime code loading, Java serialisation.
- No file I/O outside the `Filepath` utility → the plugin does **no file I/O at all**; everything is in
  `ConfigManager`, and import/export uses the clipboard.
- Boss restrictions: no attack prediction, no prayer advice, no automatic "where to stand" indicators —
  *manual tile marking is allowed*. Tile packs here are static, user-chosen lists of tiles; nothing is
  computed from game state.
- "New high-end PvM boss plugins are not accepted as a blanket policy" and "no content simulation".
  This plugin is a preparation tool (gear, inventory, static markers) and does nothing during a fight, and
  a DPS formula is not a fight simulator; but this is a judgement call for reviewers and is flagged.
- No menu entries that send actions, no input injection → none exist.
- No player data over HTTP → the plugin makes no network requests.
- Third-party-server features must be opt-in → not applicable (no network).
- Use `gameval` constants, injected `Gson`, `LinkBrowser`.

### Combat formulas
- The engine follows the publicly documented formulas (wiki pages *Damage per second/Melee|Ranged|Magic*,
  *Maximum hit*, item pages) and was cross-checked against the wiki calculator's behaviour: 86 scenarios
  match to nine significant figures (docs/testing.md).
- osrs-dps-calc is GPL-3.0, which is incompatible with shipping its code in a BSD-2 hub plugin, so **no
  code was taken**. Game formulas and statistics are facts; the implementation, structure and tests are new.

### Data
- osrs-dps-calc publishes wiki-derived JSON for 5,100 equipment entries, 2,927 monster versions and 61 spells.
  It is bundled (trimmed) with its commit hash; `scripts/update-data.mjs` regenerates it.
- **No trustworthy machine-readable source of equipment requirements was found.** osrsreboxed-db
  (GPL-3.0, last updated January 2025) was checked and rejected: it reports Twisted bow as 75 Ranged
  (now 85) and has no requirements at all for Torva or Osmumten's fang. Requirements are therefore a small
  hand-curated file, and everything else is reported as *unknown* rather than assumed.
- Tile coordinates: only packs with a published, licensed source are shipped (General Graardor ×6, Zulrah,
  Phantom Muspah). **No verified Vorkath or Cerberus pack was found, so none is shipped**; import your own.

## Unresolved limitations
See README "Known limitations". The main ones: requirement coverage is partial by design; phase weights for
Zulrah and the Muspah are assumptions; ancient/Arceuus spells, special attacks and several niche weapons
are not optimised; blowpipe darts must be chosen by the player; in-game behaviour has not been tested by
the author of this code (it cannot be: see docs/testing.md).
