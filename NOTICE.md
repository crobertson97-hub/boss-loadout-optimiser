# Third-party notices

The plugin's source code is licensed under the BSD 2-Clause licence (see `LICENSE`).
The bundled **data files** below come from other projects and keep their own terms.

## Old School RuneScape Wiki data

Files: `src/main/resources/com/bossoptimiser/items/items.json`, `monsters.json`, `spells.json`,
`weapon-styles.json`, `ammo.json` (and the test fixture `src/test/resources/reference/reference-scenarios.json`).

- Origin: the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/), via the JSON published by the
  wiki's DPS calculator project, [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) (`cdn/json`).
- Wiki content is licensed [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).
- The exact source commit is recorded in `items/data-version.json` and inside every generated file.
- Only data (item, monster and spell statistics, attack style tables, ammunition compatibility) is used.
  **No source code from osrs-dps-calc (GPL-3.0) is included in this plugin.** The combat engine in
  `com.bossoptimiser.combat` is an independent implementation of the publicly documented game formulas.
- The reference fixture contains numbers produced by running that calculator; the calculator itself is
  not distributed with this project.

`requirements.json` and the rune costs / heal values / charge costs in `supplies.json` were compiled
by hand from OSRS Wiki item and spell pages.

## Tile Packs plugin

File: `src/main/resources/com/bossoptimiser/tiles/packs.json`.

- Origin: [TrevorMDev/tile-packs](https://github.com/TrevorMDev/tile-packs), `tilePacks.jsonc`. The source
  commit is recorded in the file. Marker coordinates, colours and labels are copied unchanged.
- Licence: BSD 2-Clause.

```
BSD 2-Clause License

Copyright (c) 2022, TrevorMDev
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## RuneLite

Built against the [RuneLite](https://github.com/runelite/runelite) client API (BSD 2-Clause). The Gradle
build files and wrapper follow the official [example-plugin](https://github.com/runelite/example-plugin)
template. Supply item ids in `supplies.json` are resolved from RuneLite's `net.runelite.api.ItemID`.

Old School RuneScape is a trademark of Jagex Ltd. This project is not affiliated with Jagex or RuneLite.
