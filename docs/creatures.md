# Creature files

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
| `size` | Size multiplier for the creature's hitbox and model. Optional, default 1. |
| `movement` | `walk`, `fly` or `swim` (for the battle grid). Optional, default walk. |
| `spawn` | Optional. Biome ids or `#tags`, level range (0–50) and how common it is compared with others in the same biome. Without it, the creature never appears in the wild. |

## Models and textures

Models are Blockbench **Bedrock entity** models (`.geo.json`), one per form:

- `assets/ahrivaea/geo/creature/<species>/<influence>.geo.json`
- `assets/ahrivaea/textures/entity/creature/<species>/<influence>.png`
- optional `assets/ahrivaea/geo/creature/<species>/settings.json`: `{"model_scale": 0.15}` shrinks or grows the model
  (the dragons are modelled about 17 blocks long, so they're drawn at 0.15 × size 1.6).

A missing form falls back to the None model and texture. Bones named `front_left_leg`, `head`, `neck`,
`wing_left`, `tail`, `tail_2` … are animated automatically.

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
- `/ahrivaea spawn <species> <influence> <level>` — (operators) put a creature here.
- `/ahrivaea givexp <amount>`, `/ahrivaea selftest` — (operators) testing tools.
