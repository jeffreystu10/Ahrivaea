# Creature files

The 239 species (312 forms) from the creature list are made by `tools/creatures_from_sheet.py` from
`tools/data/Monster_List_v.1.xlsx`. The list comes in waves, each on two tabs: "MOB LIST n" (biomes, rarity, drops)
and "MOB RECEIPT n" (work); each form records its `wave` (1: 205 species, 2: 33 more, plus the Iron Golem, which took
the second Arcane Djinni's place in wave 1). To change the list, edit the spreadsheet and run
`python3 tools/creatures_from_sheet.py` again; it keeps each form's existing name, stats, size and movement and
rewrites its spawn, drops and jobs. `tools/data/creature_import_report.txt` lists anything it couldn't read.

Typos in the list are fixed on import (`NAME_FIXES`, `INFLUENCE_FIXES` and `BIOMES` in the tool): the Fae "Ghome" is the Gnome's Fae form,
"Helebrog (Saber-toothed brown bear)" is the Helebrog, "Nater" is Nature and "Bich Forest" the Birch Forest. The Iron Golem has no
row on the work tab, so it gets Base defense and Item transport (`WORK_FALLBACK`). "Namean Lion" keeps its id
(`namean_lion`) but is shown as Nemean Lion, and "Zmey Gorymych" (`zmey_gorymych`) as Zmey Gorynych.

Each creature species is one JSON file in `src/main/resources/data/ahrivaea/creature/`. The file name is
its id (`dragon.json` → `dragon`). Creatures can also be added from any data pack at
`data/<namespace>/creature/<id>.json`; `/reload` picks up changes.

```json
{
  "name": "Dragon",
  "stats": {"hp": 92, "attack": 82, "defense": 98, "speed": 54, "magic": 63, "luck": 37, "charisma": 98},
  "size": 1.6,
  "movement": "fly",
  "forms": {
    "none": {"name": "Old Dragon"},
    "fire": {"name": "Ashen Dragon", "stats": {"hp": 57, "attack": 68, "defense": 61, "speed": 97, "magic": 61, "luck": 80, "charisma": 82}}
  },
  "spawn": {"biomes": ["#minecraft:is_overworld"], "min_level": 3, "max_level": 15, "weight": 10}
}
```

| Field | Meaning |
|---|---|
| `name` | The species name. A form without its own name is called "<Influence> <name>", e.g. "Frost Dragon". |
| `stats` | The seven stats at level 50, each 0–100, adding up to 500–600 (the log warns if not). |
| `forms` | One entry per influence the species can have: none, fire, frost, storm, earth, water, nature, void, fae, darkness, radiant, arcane. Each can have its own `name` and `stats`. A creature whose spawn influence isn't listed becomes its None form. Optional; default is just None. |
| `size` | Size multiplier for the creature's hitbox and model. Optional, default 1. A form can have its own `size` (set by the model import). |
| `movement` | `walk`, `fly` or `swim` (for the battle grid). Optional, default walk. |
| `spawn` | Optional, species-wide. Biome ids or `#tags`, level range (0–50) and how common it is compared with others in the same biome; the influence then comes from the influence rules. Without any spawn, the creature never appears in the wild. |

Each **form** can also have (the creature list fills these in):

| Form field | Meaning |
|---|---|
| `rarity` | Common, Uncommon, Rare, Super Rare or Legendary (shown, and sets the spawn weight and levels below). |
| `spawn` | Where this form appears (biomes), its levels and weight. Weight = rarity weight × 10 × the influence's overall rarity: Common 40, Uncommon 18, Rare 8, Super Rare 3, Legendary 1; overall Common ×1.5, Rare ×1, Super Rare ×0.6, Legendary ×0.3. Levels: Common 2–12, Uncommon 6–18, Rare 12–26, Super Rare 20–35, Legendary 30–45. |
| `drops` | `[{"item": "minecraft:flint", "chance": 0.65, "min": 1, "max": 2}]`, rolled for each one you defeat in a battle you win. `"enchant": true` enchants it (enchanted books). |
| `jobs` | `[{"job": "smelting", "text": "Glass and brick production"}]`: what it does at a base, first job first (see [bases.md](bases.md)). |

## Spawning

Wild creatures are rare and tied to exploring. Ahrivaea's own spawner (not Minecraft's) gives each player one
creature for every 50 blocks they travel (game rule `ahrivaea:wild_spawn_distance`), so about 20 on a 1000 block
journey, plus one every 90 seconds while they stay put. Each appears 24–56 blocks away, and none do while 5 wild
creatures are already within 80 blocks (they wander off once you've left them behind). Teleports and portals don't
count as travel. On land it picks walking and flying creatures, in water swimmers, underground (or in the Nether)
it looks for a cave floor near the player's height. Nothing spawns inside a base or in the battle arenas. Wild
creatures despawn like animals do when no player is near; tamed ones and workers stay.

**Getting unstuck.** Big creatures can wedge themselves into blocks or between trees and walls. Once a second each
creature checks: inside blocks for 2 seconds, or trying to walk somewhere for 6 seconds without moving, and it
hops to the nearest open spot within 12 blocks where it fits, with solid ground under it (or in water, for
swimmers). Not while ridden, sitting, frozen (NoAI) or in a battle (`CreatureEntity.checkStuck`).

**Which creature.** First its influence tier, then a creature of that tier from the forms whose `spawn` biomes
include the spot (weighted as above, and within 5 levels of the player):

| Tier | Influences | Share of spawns |
|---|---|---|
| 1 (most common) | None | 4 in 9 (44%) |
| 2 | Fire, Water, Nature, Storm, Earth, Frost | 3 in 9 (33%) |
| 3 (rarest) | Darkness, Void, Radiant, Fae, Arcane | 2 in 9 (22%) |

Where a biome has no creatures of a tier, the other tiers share its part (`Influence.spawnTier`, `Species.pickTier`).
Battle reinforcements and creatures replacing vanilla mobs are picked the same way.

**Enraged and Mutated.** 1 in 10,000 wild creatures (game rule `ahrivaea:variant_odds`; 0 turns them off) is
Enraged or Mutated, equally likely: the same creature in its model's other colours (`<influence>_enraged.png`,
`<influence>_mutated.png`), with the word in front of its name. It keeps it when caught, recruited, sent out or
put to work. `/ahrivaea spawn <species> <influence> <level> enraged|mutated` makes one (operators).

## No vanilla mobs

Minecraft's natural mob spawning is off in every biome. Any vanilla mob that appears anyway (spawners, structures,
villages, spawn eggs, eggs, old chunks) is replaced by a creature: the matching one where the list has it (cow,
pig, sheep, chicken, horse, camel, cat, fox, goat, llama, rabbit, squid, dolphin, wolf, polar bear → bear,
golems → golem, zombies → zombie, skeletons → skeleton), else a creature of that biome, unless 3 wild creatures
are already within 8 blocks. The Ender Dragon counts as defeated the moment it appears, so the exit portal and
gateways open. The game rule `/gamerule ahrivaea:vanilla_mobs true` turns all this off for a world.

What goes with them: villager trading, bees and honey from hives, raids, and the Wither and Ender Dragon fights.
Their drops come from creatures instead (nether stars: a few Legendary creatures drop them, 2%).

## Models and textures

Models are Blockbench **Bedrock entity** models (`.geo.json`), one per form:

- `assets/ahrivaea/geo/creature/<species>/<influence>.geo.json`, with box UVs or per-face UVs
- `assets/ahrivaea/geo/creature/<species>/<influence>.animation.json` (optional): Bedrock animations. `idle`
  plays all the time, `fly` while the creature is off the ground. Values can be Molang (numbers, `+ - * /`,
  brackets, `query.life_time`, `math.sin`/`cos`/`abs`/`min`/`max`/`clamp`/`sqrt`).
- `assets/ahrivaea/textures/entity/creature/<species>/<influence>.png`
- optional `assets/ahrivaea/geo/creature/<species>/settings.json`: `{"model_scale": 0.5}` shrinks or grows a species'
  models that aren't real size (no species uses it now).

A missing form falls back to the None model and texture, or else any form's that the species has; a creature with no model at all is the placeholder,
tinted its influence's colour. Legs (`front_left_leg` …, or a biped's `leg_left`/`arm_right` …) swing as it
walks and `head`/`neck` follow where it looks; models without animations also get gently moving `wing_left`,
`tail`, `tail_2` ….

### Importing models from Blockbench

`python3 tools/import_models.py <zips, .bbmodel files or folders>` takes Blockbench projects (`.bbmodel`) from
the Mythic Mob Library, reads which creature and influence each is from its `mythic_mob` block, and writes the
model, animations and textures above. The `_enraged`, `_mutated` and `_enraged_mutated` versions only differ in
colours, so they become extra textures (`<influence>_enraged.png` …) for later. The originals are kept in
`art/models/<influence>/`, and `tools/data/model_import_report.txt` lists what was used. Names that differ from
the roster (Dancing Axe → Enchanted Axe, Djinn → Djinni, Haunted Lantern → Enchanted Lantern, Rug of
Smothering → Enchanted Rug, Spellbook → Living Spellbook, Tarasque → Terrasque, Changeling → Doppleganger, Giant Slug → Giant Snail, Wisp → Will-o-the-wisp, Ifrit → Efreeti, Nemean Lion → Namean Lion, Balor → Drathkuhl, Sabertooth → Saber Toothed Cat, Adarna → Ibong Adarna, Wild Hunt → Wild Hunt Rider, Ape → Giant Ape, Griffin → Griffon, Gelcube → Gelatinous Cube, Dinosaur → T-Rex, Chimera → Chimaera, Rhino → Rhinocerous, Tropical Fish → Fish, Zmey → Zmey Gorymych, Serpent Demon → Six Armed Serpent Demon, Angler → Giant Angler Fish, Lung → Long) are in the tool's `ALIASES`.

These models are made at their real size (one Blockbench pixel = 1/16 block): a Haunted Lantern is about a block
tall, a Gilded Sentinel 8 blocks and a Clockwork Dragon 16 blocks long. The tool sets the form's `size` from the
model's height so the hitbox matches, up to 4× the standard hitbox (2.8 × 3.2 blocks) so big creatures can still
get around; the model is always drawn full size. Base workers and raiders get a hitbox under a block wide (1.25×)
so they fit through doors and between farm rows. In a battle every creature is shrunk to fit its square.

All of wave 2 has its library models (imported like the rest). Until theirs arrive, the Iron Golem and the Whale
have stand-in models built by `tools/build_models.py` from the
descriptions in `tools/model_defs.py`, in the library's style (blocky parts, a 64x16 palette texture, idle and fly
animations, Enraged and Mutated textures made by recolouring the palette). Run `python3 tools/build_models.py
[species ...]` after changing one; a real library model imported later replaces its files (then take
that creature out of `model_defs.py` so a rebuild doesn't put the stand-in back).

Models so far: 310 of the 312 forms. A form without its own model borrows its species' None model, or else any model
the species has: the None Orca uses the Water Orca's and the Arcane Dragon the None Dragon's.

`python3 tools/audit_creatures.py` checks every form (stats, biomes, drop items, jobs, model and textures) and writes
`tools/data/creature_audit.txt`.

## Where influences come from

Rules in `data/ahrivaea/influence_rules/*.json` map biomes and time of day (`day`, `night`, `dawn`, `dusk`
or `any`) to an influence. `draft.json` is a placeholder until the real list is written.

```json
[{"biomes": ["minecraft:desert", "#minecraft:is_badlands"], "influence": "fire"},
 {"biomes": ["minecraft:meadow"], "time": "night", "influence": "arcane", "weight": 2}]
```

## Commands

- `/party` — your creatures, their stats and your unspent XP. `/party send|release <slot>`.
- `/party xp <slot> <amount>` — spend XP on a creature (100 XP per level, up to 50).
- `/party box` — creatures that didn't fit in the party (recruits go here when it's full). `/party box take <n>`,
  `/party box store <slot>`.
- `/ahrivaea spawn <species> <influence> <level>` — (operators) put a creature here.
- `/ahrivaea givexp <amount>`, `/ahrivaea selftest` — (operators) testing tools.

## Riding

Every creature of medium size or bigger can be ridden (form size 1.9 and up: everything human-sized and larger,
239 of the 312 forms). The rules are Swift Horses' (`docs/horses.md`), which stays the mod for vanilla horses.

- **Saddle**: right-click your own creature (sent out with `/party send`, not a base worker) with a vanilla saddle.
  Right-click it with an empty hand to get on; sneak to get off. Shears take the saddle off. Small creatures and
  creatures that aren't yours can't be saddled. Starting a battle takes you off your mount; it waits where it was.
- **Flying** creatures (`movement: fly`): jump to take off, then forward flies where you look, jump rises. Look
  down into the ground to land. No fall damage for them or their rider.
- **Swimming** creatures (`movement: swim`): in water, forward swims where you look and jump rises; the rider gets
  Water Breathing. On land they walk.
- **Walkers** climb full blocks and jump about 2 blocks.
- **Speed**: top speed (blocks/sec) = (5 + 0.09 x Speed) x the tier's multiplier (x1.5, x1.76, x2.02, x2.28, x2.54,
  x2.8), where Speed is the creature's Speed stat at its level (25% at level 0, full at 50).
- **Tiers**: 1-3 from treats fed by hand: 8 Swift Treats, then 8 Golden Treats, then 4 Racing Treats
  (`dev.ahrivaea.creature.Mounts`). At tier 3 a creature with Speed 60 or more rides at tier 4, and 80 or more at
  tier 5, so levelling it up can raise it further.
- Its treat tier, progress and saddle are kept when it's called back into the party (and its XP now is too).
- While riding, its name, top speed, current speed, tier and next step show in the top-left corner.

| Treat | Recipe (shapeless) | Makes |
|---|---|---|
| Swift Treat | 2 wheat, sugar, apple, sweet berries | 4 |
| Golden Treat | 2 golden carrots, Swift Treat, honey bottle | 2 |
| Racing Treat | golden apple, 2 Golden Treats, rabbit's foot (Rabbits, Foxes, Jackalopes and others drop it), glow berries | 1 |

The client test `CreatureRideTest` saddles and feeds a Horse creature by hand, rides it at tier 4 and measures its
speed, flies a Pegasus up and back down, and dives an Orca. Screenshots: `creature_ride_1_horse_full_speed.png`,
`creature_ride_2_pegasus_flying.png`, `creature_ride_3_orca_diving.png`.

## Party summary and party screen

The server sends each player a view of their party (`dev.ahrivaea.party.PartySync`, a JSON payload, re-sent
whenever it changes, checked twice a second): the party, the creatures they've sent out that are within 64 blocks,
the box and their XP to give out. It's worked out on the server because only the server has the species data.

- **Summary** (`client/PartyClient`): in the top-left corner (below the mount stats while riding), each party creature's name (in its
  influence colour), level and an HP bar, then the ones out in the world ("out" or "riding"). Hidden with F1, and
  can be turned off with the Summary button on the party screen (saved in `config/ahrivaea_party.json`).
- **Party screen** (`client/PartyScreen`): on **K** by default (P is Minecraft's Social Interactions key; change it
  in Options > Controls > Ahrivaea Creatures). Tabs for Party, Out and Box; click a creature for its HP, XP, all
  seven stats (now / at level 50), battle movement, jobs and riding (tier, top speed, saddle, next tier). Buttons:
  Send out, +1 Lv (spends the XP it needs from your pool), To box, Release (asks twice) for party creatures; Call
  back for ones that are out; To party for the box. The buttons run the `/party` commands.
- `/party recall` calls back every creature you've sent out nearby; `/party recall <entity>` one of them.

Test: `PartyScreenTest` (screenshots `party_1_summary`, `party_2_screen`, `party_3_summary_one_out`).

## Healing

Outside battle your creatures eat food, like you do (`dev.ahrivaea.creature.Healing`). Right-click one you have
out with food, or press Feed on the party screen (Party or Out tab) with food in your main hand, or
`/party feed <slot>`. It heals a share of its max HP: 2.5% per hunger point the food fills plus 1.25% per point of
saturation, between 3% and 60%. Bowls and bottles come back.

| Food | Heals |
|---|---|
| Cookie, dried kelp, sweet berries | 3-6% |
| Raw beef, carrot, apple | 10-13% |
| Bread, baked potato, cooked cod | 20% |
| Cooked chicken, mutton, salmon | 24-27% |
| Steak, cooked porkchop | 36% |
| Golden carrot | 33% |
| Rabbit stew | 40% |

In battle food does nothing; only brewed Healing and Regeneration potions heal (see design.md, Items).

## Creature dex

Press **J** (change it in Controls) or **Dex** on the party screen (`dev.ahrivaea.party.Dex`, `client/DexScreen`).
Every creature form is numbered (#001 onwards, by name). For each player a form is:

- **Unknown**: a "?" and a clue, the first place it lives ("Lives in Snowy Plains and elsewhere").
- **Seen** once a wild one has been within 20 blocks in sight, or fought you: its name, model, influence, rarity,
  levels, where it lives and how it moves.
- **Caught** once one has been in your party or box (caught, recruited, a starter): everything, adding its seven
  stats, battle movement, whether it can be ridden, its base jobs and its drops.

A message tells you when something new is seen or caught. Filter by All / Seen / Caught, or search by name or
number. Kept with the player (and on death).
