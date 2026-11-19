# Local setup and manual test guide

Primary instructions are for Windows PowerShell. macOS/Linux equivalents are given where they differ.

## 1. Install the tools

**JDK (required).** Any JDK 11 or newer; the build targets Java 11 regardless. A JRE is not enough.

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
java -version
```
macOS: `brew install --cask temurin@17` · Debian/Ubuntu: `sudo apt install openjdk-17-jdk`

> This machine already has JDK 21 at `C:\Program Files\Java\jdk-21.0.11`, but `JAVA_HOME` points at a Java 17
> **JRE**. The scripts in `scripts\` detect that and pick the JDK automatically; nothing needs changing.

**Git** (only needed to publish the project): `winget install Git.Git` · macOS: `xcode-select --install`

**Node.js** is *not* needed to build or run. It is only used by the optional data-regeneration scripts.

## 2. Open the project

```powershell
cd C:\Users\Callum.Robertson\Projects\boss-loadout-optimiser
```

**IntelliJ IDEA:** *File → Open…* → select the folder → *Open as Project* → trust it. IntelliJ imports the
Gradle build. Set *Settings → Build Tools → Gradle → Gradle JVM* to your JDK, and enable
*Settings → Build → Compiler → Annotation Processors* (Lombok). Install the Lombok plugin if prompted.

## 3. Build and test

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build.ps1     # compile + all tests
powershell -ExecutionPolicy Bypass -File scripts\test.ps1      # tests only
powershell -ExecutionPolicy Bypass -File scripts\test.ps1 --tests "*ReferenceValidationTest"
```
macOS/Linux: `./gradlew build` · `./gradlew test`

The first run downloads Gradle 8.10 and the RuneLite client (a few minutes). The HTML test report is at
`build\reports\tests\test\index.html`.

## 4. Launch a RuneLite development client

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
```
macOS/Linux: `./gradlew run`

This starts a real RuneLite client (developer mode) with the plugin side-loaded through
`BossOptimiserPluginTest`. **This is the supported way to run an unpublished plugin.** You cannot install it
by copying a JAR into your normal RuneLite; a normal RuneLite only loads hub plugins that have been reviewed.

In IntelliJ you can instead run the `main` method of `src/test/java/com/bossoptimiser/BossOptimiserPluginTest.java`
(add VM option `-ea` and program arguments `--developer-mode --debug`).

**Logging in.** Development clients cannot use the Jagex Launcher directly. Follow
<https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts> once (it has you run the launcher with a flag
that writes `credentials.properties` into `~/.runelite`); the development client then logs in with those.
Delete that file when you are done testing. The development client shares `~/.runelite` (settings, ground
markers) with your normal client — a reason the plugin is careful with ground markers.

## 5. Manual test script

The automated tests cover the logic; **none of the steps below has been executed by the author of the code**,
because driving the game client with automation is against Jagex's rules. Please run them and note what you see.

1. **Enable.** Open the plugin list (wrench icon), search "Boss Loadout Optimiser", make sure it is on. A
   shield-with-tick icon appears in the sidebar. Open it: the *Home* tab should say "Not logged in".
2. **Log in.** Home should show your name and combat levels within a second or two, and a warning that the
   bank has not been read.
3. **Open your bank.** Home should switch to "N distinct items" with the current time. Close the bank,
   withdraw/deposit something, reopen: the count/time updates.
4. **Select a boss.** *Boss* tab → search "vork" → Vorkath → strategy *Ranged* → priority *Maximum DPS*.
   Check the requirement line for Dragon Slayer II shows your real quest state. Review *Assumptions*
   (potion, prayer; set your blowpipe dart if you use one).
5. **Optimise.** Click *Optimise My Setup*. The status line should report DPS, combinations checked and time.
6. **Gear.** *Gear* tab: equipment grid with icons. Compare the DPS / max hit / accuracy with
   <https://tools.runescape.wiki/osrs-dps/> using the same gear, style, prayer and potion — they should match.
   Click a slot to see alternatives with DPS deltas; try *Lock* and *Exclude* and confirm the setup changes.
   Items marked "unverified" are ones the plugin has no requirement data for: check you can really wear them.
7. **Inventory.** *Inventory* tab: 28 slots. Change trip length and the checkboxes; lock a slot; exclude an
   item. Check the *Missing items* list against your bank.
8. **Bank highlights.** Open the bank with a setup optimised: needed items are outlined green with "xN",
   items you already carry blue with "ok", items you have too few of red with "have/need". A small list in
   the top-left shows what the bank cannot supply. Confirm **nothing is withdrawn or moved automatically**.
   Toggle the options in the plugin's settings.
9. **Tile packs.** Select General Graardor → *Tiles* tab → pick a pack → check the preview and marker list.
   - *Copy JSON*, then enable RuneLite's **Ground Markers** plugin and its *Show Import/Export/Clear options*
     setting, right-click the world-map button under the minimap → *Import* → confirm.
   - Or *Add to Ground Markers…*: read the confirmation (it states how many markers will be added and that
     existing ones are untouched). Afterwards the pack shows "Installed…"; markers appear when the area next
     loads. *Undo install* removes only those markers. **Back up first if your markers matter to you:**
     in Ground Markers use *Export* and keep the text.
   - *Import…*: paste a Ground Markers export of your own (e.g. your Vorkath tiles), name it, save, export it again.
10. **Save a Boss Profile.** *Profiles* → *Save current as…*. Close RuneLite, launch again, log in: the
    profile is listed, *Home* shows it as last used, the bank snapshot shows its saved time, and *Load*
    restores the saved gear, inventory, locks and tile pack. Log into a different account: its list is separate.
11. **Upgrades.** *Upgrades* → *Find upgrades* (optionally with a budget like `25m`). Check a suggested
    upgrade's DPS in the wiki calculator.

## 6. Logs

- Client log: `%USERPROFILE%\.runelite\logs\client.log` (macOS/Linux: `~/.runelite/logs/client.log`).
- The development client also prints to the console it was started from.
- The plugin logs under `com.bossoptimiser` at debug level; the development launcher passes `--debug`, which turns debug logging on.
  Search the log for `bossoptimiser` or `Boss Loadout`.

## 7. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `java.io.IOException: Unable to establish loopback connection` from Gradle | Java cannot create its internal socket files in `%TEMP%` (seen on this machine, whose temp path is the short form `C:\Users\CALLUM~1.ROB\…`). **Use the scripts in `scripts\`**: they set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<project>\.tmp\sock`. To run `gradlew` by hand: `. .\scripts\_env.ps1` first. In IntelliJ add that `-D…` option to *Gradle → VM options*. |
| Client window never appears; log shows `error fetching runtime config`, `Failed to get jav_config`, HTTP 403 | The network blocks RuneLite / Jagex servers (seen on the work Wi-Fi on 2026-11-19: `api.runelite.net`, `static.runelite.net` and `oldschool.runescape.com` all returned 403). Nothing in the plugin can change that. Launch on a network that allows the game. Building and testing do **not** need those servers once dependencies are cached. |
| `No JDK found` / `javac` missing | `JAVA_HOME` points at a JRE. Install a JDK or set `JAVA_HOME` to one; the scripts search `C:\Program Files\Java` and `Eclipse Adoptium`. |
| `Could not resolve net.runelite:client` | No access to `repo.runelite.net` (proxy/VPN). On a TLS-inspecting corporate network Java must trust the inspection root CA; `~/.gradle/gradle.properties` on this machine already sets `javax.net.ssl.trustStoreType=Windows-ROOT`. |
| Client starts but the plugin is missing | It was started some other way than `scripts\run-local.ps1` / `BossOptimiserPluginTest`. |
| "Bank not read yet" | Open the bank once per session; RuneLite cannot see bank contents before that. |
| A setup is labelled *Estimate* | Read the notes under it: an unverified requirement, an unmodelled weapon, or an unknown blowpipe dart. |
| Ground markers do not appear after *Add to Ground Markers…* | They load with the scene: leave and re-enter the area, or mark/unmark any tile. Ground Markers must be enabled. |
| An item I own is not used | Check *Owned but unusable* in the Gear tab, your exclusions, and whether its requirement is known. |
| Gradle or IntelliJ complains about Lombok | Enable annotation processing. |
| Want to reset the plugin's data | RuneLite → Configuration → Boss Loadout Optimiser → *Reset*. This clears settings, custom tile packs, profiles and the saved bank snapshot. It does not touch your ground markers. |

## 8. Updating the bundled data (optional)

```powershell
node scripts\update-data.mjs                 # latest wiki-calculator data from GitHub
node scripts\update-data.mjs --ref <commit>  # a pinned commit
powershell -ExecutionPolicy Bypass -File scripts\test.ps1
```
`scripts\gen-supplies.mjs <runelite checkout>` and `scripts\import-tile-packs.mjs <tile-packs checkout>`
regenerate the other two generated files. See docs/testing.md for refreshing the reference fixture.
