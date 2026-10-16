# Creature files

The 206 species (278 forms) from the creature list are made by `tools/creatures_from_sheet.py` from
`tools/data/Monster_List_v.1.xlsx`. To change the list, edit the spreadsheet and run
`python3 tools/creatures_from_sheet.py` again; it keeps each form's existing name, stats, size and movement and
rewrites its spawn, drops and jobs. `tools/data/creature_import_report.txt` lists anything it couldn't read.

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

Wild creatures are placed around each player by Ahrivaea's own spawner (not Minecraft's): one try a second per
player, 24–56 blocks away, until there are 10 wild creatures within 80 blocks. On land it picks walking and flying
creatures, in water swimmers, underground (or in the Nether) it looks for a cave floor near the player's height.
Which creature: any form whose `spawn` biomes include the spot's biome, weighted. Nothing spawns inside a base or
in the battle arenas. Wild creatures despawn like animals do when no player is near; tamed ones and workers stay.

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
- optional `assets/ahrivaea/geo/creature/<species>/settings.json`: `{"model_scale": 0.15}` shrinks or grows the model
  (the dragons are modelled about 17 blocks long, so they're drawn at 0.15 × size 1.6).

A missing form falls back to the None model and texture; a creature with no model at all is the placeholder,
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
Smothering → Enchanted Rug, Spellbook → Living Spellbook, Tarasque → Terrasque) are in the tool's `ALIASES`.

These models are made at their real size (one Blockbench pixel = 1/16 block): a Haunted Lantern is about a block
tall, a Gilded Sentinel 8 blocks and a Clockwork Dragon 16 blocks long. The tool sets the form's `size` from the
model's height so the hitbox matches, up to 4× the standard hitbox (2.8 × 3.2 blocks) so big creatures can still
get around; the model is always drawn full size. In a battle every creature is shrunk to fit its square.

Models so far: every Arcane (19), Darkness (20) and Earth (20) creature, and the other 9 dragons (the Darkness and
Earth Dragons now use the library's models; the older dragon set is drawn at `model_scale` 0.15, which real-size models ignore).

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
