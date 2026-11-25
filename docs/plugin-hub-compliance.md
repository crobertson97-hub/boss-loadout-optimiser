# Plugin Hub compliance

Status: **reviewed by the author against the published rules; not submitted, not approved.**
Rules checked: the `AGENTS.md` shipped in [runelite/example-plugin](https://github.com/runelite/example-plugin)
(commit `46e14f2c44`), which summarises Jagex's third-party client guidelines and RuneLite's rejected features.

## Checklist

| Rule | Status | Evidence |
|---|---|---|
| Java 11 compatible | ✅ | `options.release.set(11)` in `build.gradle`; builds with JDK 21 targeting 11 |
| `build.gradle` matches the template | ✅ | Template copied; only additions: version, test config, and test-only dependencies (Lombok processing for tests, Mockito) |
| No reflection / JNI / native / processes / classloading / serialisation | ✅ | `grep -rE "java.lang.reflect|ProcessBuilder|Runtime.getRuntime|ObjectInputStream|ClassLoader|Unsafe" src/main` → no matches. (One test uses reflection to verify supply ids; tests are not shipped.) |
| No `META-INF/services` plugin file, no build artefacts committed | ✅ | `.gitignore` |
| Package / names renamed from the template | ✅ | `com.bossoptimiser`, config group `bossloadoutoptimiser` |
| Permissive licence | ✅ | BSD 2-Clause; third-party data in `NOTICE.md` |
| File I/O only through `Filepath` | ✅ | No file I/O at all; storage is `ConfigManager`, import/export is the clipboard |
| OkHttp for HTTP; opt-in for third-party servers | ✅ n/a | **The plugin makes no network requests.** Prices come from `ItemManager` |
| Injected `Gson` | ✅ | `@Inject Gson` is passed everywhere; no `new Gson()` in `src/main` |
| `gameval` constants | ✅ | `InventoryID.BANK/INV/WORN`, `InterfaceID.BANKMAIN` |
| `LinkBrowser` for URLs | ✅ | Sidebar only |
| No `Thread.sleep`, no blocking in `startUp`/`shutDown`, `shutdownNow()` | ✅ | `BossOptimiserPlugin` |
| No heavy work on the client thread / in overlays | ✅ | Parsing and optimisation on a worker; overlays do a map lookup per item |
| No per-tick scene scans | ✅ | Only item containers, on change |
| Logging at debug | ✅ | `log.debug` except data-load failures (`warn`, once) |
| Clean-up in `shutDown` | ✅ | Overlays, navigation button, service, executor |
| No menu entries sending actions, no input injection, no autotyping | ✅ | None exist |
| No interface manipulation | ✅ | Draw-only `WidgetItemOverlay` on the bank |
| No player data over HTTP, no crowdsourcing, no credential storage | ✅ | No network; no credentials or session tokens are read |

## Boss and combat restrictions

| Restriction | This plugin |
|---|---|
| Next-attack prediction, projectile indicators, attack counters, flinch timers | None. The plugin does nothing during combat. |
| Prayer switching indicators / combat prayer recommendations | None. Offensive prayers are an *input the player chooses* for the DPS maths (the default is simply the highest one the account's Prayer level allows). Boss notes and inventory reasons do not name protection prayers. |
| Automatic indicators showing where to stand | None. Tile packs are static lists chosen by the player; nothing is derived from boss state. Manual tile marking is explicitly allowed, and the existing hub plugin *Tile Packs* is the same idea. |
| Additional indicators of boss mechanics, advance hazard warnings | None. |
| Content simulation | The DPS engine is a closed-form expected-damage formula, not a fight simulator. |
| "New high-end PvM boss plugins are not accepted as a blanket policy" | **Needs a maintainer's view.** The plugin is a bank/gear planner in the same family as existing hub plugins (`bis-gear`, `best-dps`, `bossing-bis`, `inventory-setups`, `tile-packs`), and it has no in-fight behaviour, but it is organised around bosses. |

## Items that need clarification from RuneLite maintainers

1. **Writing to the `groundMarker` config group.** "Add to Ground Markers…" uses the public
   `ConfigManager.setConfiguration` on the built-in plugin's group, after explicit confirmation, appending
   only (see `GroundMarkerInstaller`). If maintainers do not want hub plugins touching another plugin's config,
   set the default of `allowGroundMarkerInstall` to off or delete the installer; *Copy JSON* covers the feature.
2. **Bundled wiki-derived data.** `items.json`/`monsters.json` are CC BY-NC-SA (via osrs-dps-calc's JSON).
   Other hub plugins bundle wiki data, but confirm the attribution in `NOTICE.md` is sufficient.
3. **Boss-plugin policy**, as above.
4. **Jar size.** The data resources total about 1.2 MB uncompressed; check against current hub limits.
5. **Storing the bank snapshot in config.** About 10 bytes per bank item per account (synced with the
   RuneLite profile if the user syncs profiles). *Bank Memory* does the same; confirm it is acceptable.

## Network usage disclosure

None. No endpoint is contacted by plugin code. Item prices and icons are served by the RuneLite client's own
services through `ItemManager`, exactly as for built-in plugins. Bank contents, account name/key, profiles and
tile packs never leave the machine (other than RuneLite's own optional profile sync of its config).

## Security review

- **Untrusted input:** clipboard imports (ground-marker JSON, Boss Profiles) are parsed as JSON data only,
  size-capped (2 MB / 200 kB), range-checked field by field, and rejected with a message on any violation
  (`TilePackTest.malformedInputIsRejectedWithAUserMessage`, `ProfileStoreTest.malformedOrHostileImportsAreRejectedWithoutSideEffects`).
  Nothing imported is executed, loaded as a class, used as a path or interpolated into HTML: imported text
  (names, labels, descriptions) is shown through plain-text components, and profile names are stripped of
  control characters and angle brackets.
- **No Java deserialisation**, no dynamic code, no reflection.
- **No secrets:** no credentials, tokens or session data are read or stored.
- **Data safety:** existing ground markers are never rewritten (append-only, verified, undoable); custom
  packs and profiles are stored one per key; unreadable stored entries are skipped, not deleted.

## Submission steps (not performed)

1. Create a **public GitHub repository** containing this project (it currently has no commits and no remote).
2. Add an `icon.png` (optional, ≤ 48×72) and screenshots to the README once you have tested in game.
3. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) and add `plugins/boss-loadout-optimiser`:
   ```
   repository=https://github.com/<you>/boss-loadout-optimiser.git
   commit=<full 40-character commit hash>
   ```
4. Open a pull request. Describe the features, state that the plugin makes no network requests, and raise
   the clarification items above. Per the template's rules, PRs produced with AI assistance should end
   their description with `Generated-by: Claude Code`.
5. Respond to review; update the `commit=` line for every change.
