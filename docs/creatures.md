# Creature files

Each creature is one JSON file in `src/main/resources/data/ahrivaea/creature/`. The file name is its id
(`emberkit.json` → `emberkit`), and its texture is `assets/ahrivaea/textures/entity/creature/<id>.png`.
Creatures can also be added from any data pack at `data/<namespace>/creature/<id>.json`; `/reload` picks up changes.

```json
{
  "name": "Emberkit",
  "types": ["fire"],
  "base_stats": {"hp": 42, "attack": 50, "defense": 40, "special_attack": 58, "special_defense": 46, "speed": 64},
  "catch_rate": 0.45,
  "scale": 0.9,
  "spawn": {"biomes": ["#minecraft:is_savanna", "minecraft:desert"], "min_level": 2, "max_level": 9, "weight": 10},
  "evolution": {"into": "emberfox", "level": 16}
}
```

| Field | Meaning |
|---|---|
| `name` | Name shown in game. |
| `types` | One or two of: normal, fire, water, grass, electric, ice, fighting, poison, ground, flying, psychic, bug, rock, ghost, dragon, dark, steel, fairy. |
| `base_stats` | Base values (roughly 20–150). Stats at a level follow the usual curve: HP = 2·base·level/100 + level + 10, others = 2·base·level/100 + 5. Speed also sets how fast it walks. |
| `catch_rate` | 0–1. Chance to bind at full health; a weakened creature is up to 60% easier. Optional, default 0.45. |
| `scale` | Size multiplier. Optional, default 1. |
| `spawn` | Optional. Biome ids or `#tags`, level range and how common it is compared with others in the same biome. Without it, the creature never appears in the wild. |
| `evolution` | Optional. The creature it becomes, and at what level (evolving arrives with leveling). |
