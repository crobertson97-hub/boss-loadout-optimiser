# Combat formulas

What `com.bossoptimiser.combat.DpsCalculator` computes, in the order it computes it. All divisions are
integer divisions that truncate, exactly where the game truncates; the order of operations matters.
Sources: OSRS Wiki (*Damage per second/Melee*, */Ranged*, */Magic*, *Maximum hit*, and the individual item
pages). Verified against the wiki DPS calculator: see docs/testing.md.

## Inputs
Loadout, attack style (type + stance), spell (when autocasting), real levels, potion boosts, prayers,
"on slayer task", Kandarin hard diary, blowpipe dart, monster, monster current hitpoints.

## Equipment totals (`EquipmentTotals`)
Sum of all pieces, then:
- Ammunition-slot ranged bonuses count only if the weapon can fire that ammunition (`ammo.json`).
  A weapon that needs ammunition and has none/wrong ammunition has max hit 0.
- Toxic/blazing blowpipe: add the loaded dart's ranged strength.
- Tumeken's shadow: magic attack ×3, magic damage ×3 (capped at 100%).
- Virtus pieces: +3% magic damage each when casting an ancient spell.
- Elite void mage set: +5% magic damage.
- Charged Dizana's quiver with a bow/crossbow firing valid ammunition: +10 ranged attack, +1 ranged strength.

## Effective levels
| | Accuracy | Damage |
|---|---|---|
| Melee | `(Attack+boost) × prayer` `+8` `+3 accurate / +1 controlled`, `×11/10` void | `(Strength+boost) × prayer` `+8` `+3 aggressive / +1 controlled`, `×11/10` void |
| Ranged | `(Ranged+boost) × prayer` `+3 accurate` `+8`, `×11/10` void | same with strength prayer, `×9/8` elite void or `×11/10` void |
| Magic | `(Magic+boost) × prayer` `+2 accurate` `+9`, `×29/20` void | n/a |

Prayers multiply as `level × percent / 100`.

## Attack roll
`effective level × (equipment attack bonus + 64)`, then in this order:

**Melee:** salve (e)/(ei) ×6/5 or salve/(i) ×7/6 vs undead, *else* black mask / slayer helmet on task ×7/6 →
Arclight/Emberlight +70% vs demons → Silverlight/Darklight +60% → burning claws +5% → dragon hunter lance ×6/5
(wand ×7/4) vs dragons → Inquisitor's `×(200+b)/200` on crush (helm 1, hauberk 2, plateskirt 2).

**Ranged:** crystal bow `×(20+p)/20` (helm 1, legs 2, body 3) → salve (ei) ×6/5 or (i) ×7/6, *else* imbued
slayer helmet on task ×23/20 → twisted bow scaling → dragon hunter crossbow ×13/10 → scorching bow +30% vs demons.

**Magic:** additive percentage (salve (ei) 20, (i) 15, smoke staff with a standard spell 10) → dragon hunter
weapon factor → imbued slayer helmet ×23/20 → tome of water with a water spell ×6/5 → elemental weakness:
`+ baseRoll × severity / 100`.

Twisted bow: with `m = min(250, max(monster Magic level, monster magic attack))` (350 in the Chambers),
accuracy % `= 140 + (3m−10)/100 − (3m/10 − 100)²/100` capped at 140; damage % `= 250 + (3m−14)/100 − (3m/10 − 140)²/100`
capped at 250.

## Max hit
**Melee / ranged base:** `(effective strength × (strength bonus + 64) + 320) / 640`.

**Melee modifiers:** salve / slayer helmet (as above) → Arclight/Emberlight +70% → burning claws +5% → dragon
hunter lance ×6/5 (wand ×7/5) → Silverlight/Darklight +60% → colossal blade `+ min(2×size, 10)` → Inquisitor's →
Osmumten's fang: `shrink = max×3/20`; damage is uniform on `[shrink, max−shrink]`.

**Ranged modifiers:** crystal bow `×(40+p)/40` → salve, *else* imbued slayer helmet `×(23 [+5 dragon hunter crossbow]
[+6 scorching bow])/20` (additive with the helmet) → twisted bow scaling → dragon hunter crossbow ×5/4 and
scorching bow +30% when not already folded into the helmet.

**Magic base:** powered staves from the visible Magic level — trident of the seas `⌊m/3 − 5⌋`, swamp `⌊m/3 − 2⌋`,
sanguinesti `⌊m/3⌋`, Tumeken's shadow `⌊m/3⌋ + 1`, Eye of Ayak `⌊m/3⌋ − 6`, warped sceptre `(8m+96)/37`.
Spells: the listed max hit; elemental spells use the max hit of the highest tier of their class that the
*real* Magic level unlocks. Then `+ base × (magic damage bonus [+10% smoke staff] [+20%/15% salve] [+prayer]) / 1000`
→ slayer helmet ×23/20 → dragon hunter factor → `+ base × weakness%` → charged tome matching the spell ×11/10.

## Defence roll and accuracy
`(level + 9) × (defence bonus + 64)`, using the monster's Magic level against magic and its Defence level
otherwise. Ranged defence depends on the weapon: thrown = light, bows = standard, crossbows = heavy.

`accuracy = 1 − (def+2)/(2(atk+1))` if `atk > def`, else `atk/(2(def+1))`.
Osmumten's fang on stab rolls twice: `1 − (def+2)(2def+3)/(6(atk+1)²)` or `atk(4atk+5)/(6(atk+1)(def+1))`.
Confliction gauntlets (one-handed magic): `double/(1 + double − single)`.

## Hit distribution (`HitDist`)
A hit is uniform on `[min, max]` with probability `accuracy`, otherwise a miss. Then, in order:
- Dark bow: two hits. Scythe of Vitur: up to three hits (monster size), max halved each time.
  Torag's hammers / sulphur blades / glacial temotli / earthbound tecpatl: two hits splitting the max.
- Sanguinesti staff: 20% of successful hits deal +8.
- Enchanted bolts (crossbows): opal 5% +⌊level/10⌋; pearl 6% +⌊level/20⌋ (15 vs fiery); diamond 10% →
  uniform `[0, max×115%]` ignoring accuracy; dragonstone 6% +⌊2·level/10⌋ (not vs dragons/fiery); onyx 11% →
  `[0, max×120%]` (not vs undead). Kandarin hard diary ×1.1 chance. Zaryte crossbow improves each effect.
- A successful hit of 0 becomes 1.
- Ruby bolts: 6% → `min(100, 20% of the monster's current hitpoints)`.
- Brimstone ring (magic): 25% of attacks are rolled against 90% of the defence roll.
- Monster rules: immunity (Zulrah and flying monsters vs melee without a halberd), Zulrah's cap (hits above
  50 become uniform 45–50), flat armour.

`DPS = expected damage of one attack ÷ (attack interval in ticks × 0.6 s)`.
Interval: weapon speed; −1 on Rapid; 5 when casting (4 with a harmonised nightmare staff and a standard spell).

## Assumptions the optimiser makes
- Ruby bolts are evaluated at **full hitpoints** (the same convention as the wiki calculator), which
  overstates their average over a kill. Compare with diamond bolts yourself.
- Zulrah forms are weighted equally; Muspah phases 40/35/5/20. These are stated in the UI.
- Sergeants, minions, specials, thralls, defence-lowering specials and overkill are not included.
- Potion boosts are assumed to be at their freshly-sipped value.

## Not modelled (never shown as exact)
Special attacks; ancient/Arceuus/god spells in the optimiser (the engine supports ancient spell max hits, the
optimiser does not choose them); Barrows set effects; obsidian set; Tonalztics of Ralos, dual macuahuitl,
eclipse atlatl, soulreaper axe, Dinh's bulwark, keris, chinchompas, salamanders, wilderness weapons,
Gauntlet gear, twinflame staff, and the others listed in `Mechanics.UNSUPPORTED_WEAPONS`; monsters with special
damage rules listed in `Mechanics.SPECIAL_RULE_MONSTERS`. A loadout that uses any of these is still evaluated
with the basic formula but is flagged *Estimate* with the reason.
