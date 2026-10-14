# Server challenges

A challenge is a ruined structure that the whole server rebuilds by putting items into its offering chest.
The first one is **The Old Tower**: 1000 coal, rebuilt in 4 stages.

## How it plays

- Right-click the tower's chest: it opens like a normal chest titled "Offer coal: 520/1000". Put coal in
  and close it. Coal counts 1, coal blocks count 9; anything else goes straight back to your inventory.
- Every 250 coal, the next quarter of the finished tower is rebuilt from the bottom up, with a dust cloud
  and an announcement. Everyone sees "<player> offered 64 coal to The Old Tower (520/1000)".
- At 1000 the tower is complete: title on every screen, fireworks, and rewards for the top donors.
  Offline winners get theirs the next time they log in. After that the chest just shows the final results.
- Players outside creative mode can't break the tower's blocks.
- `/challenge` shows the leaderboard: progress bar, top 10 and your own total and rank.

## Commands for operators

| Command | What it does |
|---|---|
| `/challenge place tower` | Places the ruined tower with its north-west bottom corner where you stand and starts it from 0. |
| `/challenge credit tower <player> <amount>` | Adds coal to someone's total by hand (e.g. if a chest offering got lost). |
| `/challenge remove tower` | Stops tracking it (the blocks stay). |

## Files

`data/ahrivaea/challenge/tower.json`:

```json
{
  "name": "The Old Tower",
  "unit": "coal",
  "items": {"minecraft:coal": 1, "minecraft:coal_block": 9},
  "goal": 1000,
  "stages": 4,
  "ruined": "ahrivaea:tower/ruined",
  "finished": "ahrivaea:tower/finished",
  "chest": [5, 1, 5],
  "rewards": [
    {"rank": 1, "items": [{"item": "minecraft:diamond", "count": 16}]},
    {"rank": 0, "items": [{"item": "minecraft:emerald", "count": 8}]}
  ]
}
```

- `ruined` / `finished`: structure files (`data/ahrivaea/structure/tower/ruined.nbt` and `finished.nbt`). Both
  should be the same size; the finished one is revealed in horizontal slices from the bottom.
- `chest`: where the offering chest is, counted from the structure's corner.
- `rewards`: `rank` 1, 2, 3 … for the top donors; `rank` 0 for everyone who gave anything.

The structures in the mod now are placeholders made by `tools/placeholder_tower.py`. To use a real build,
save it with a structure block in Minecraft 26.3 (Save mode) — once ruined, once finished, same corner and
size — and replace the two .nbt files.
