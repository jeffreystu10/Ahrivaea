# Server challenges

Every stronghold has a ruined tower standing on the surface above its End portal room. Each tower runs its
own challenge — what to bring, how much, and what the top donors win — and the whole server rebuilds it
together by putting items into its offering chest. Eyes of Ender lead players to the towers.

## Which tower gets which challenge

Strongholds are numbered from the centre of the world outward, in the order the game lays out its
stronghold rings (3 in the first ring, 6 in the second, … 128 in all). `data/ahrivaea/challenge_order.json`
lists challenge ids; tower 1 gets the first, tower 2 the second, and so on. When the list runs out it starts
over, so with 12 challenges tower 13 repeats the first one (as its own separate tower and leaderboard).

```json
{"towers": ["coal", "iron", "wheat", "logs", "copper", "bones", "redstone", "lapis", "string", "gold", "flesh", "diamond"]}
```

A tower is built the first time its stronghold's chunk loads, so towers appear as the world is explored —
in new worlds and in existing ones.

## Biome blocks

Each tower takes on the blocks of the biome it stands in: sandstone in deserts, red sandstone in badlands,
prismarine at sea, granite in savannas, deepslate in dark forests, mud bricks in swamps, quartz and cherry
in blossom biomes, tuff in the mountains, diorite and ice in snowy biomes, mushroom blocks on mushroom
islands, mossy stone in jungles, plain stone bricks elsewhere. The choice is made once when the tower is
built and used for every stage. Towers stand on a foundation down to solid ground (or the sea floor).

Palettes are `data/ahrivaea/tower_palette/<name>.json`:

```json
{
  "priority": 10,
  "biomes": ["minecraft:desert", "#minecraft:is_beach"],
  "blocks": {"minecraft:stone_bricks": "minecraft:cut_sandstone", "minecraft:spruce_planks": "minecraft:birch_planks"},
  "foundation": "minecraft:sandstone"
}
```

`blocks` maps a block used in the tower's structure files to its replacement (stairs, slabs and walls keep
their facing). The highest-priority palette whose biomes match wins; one with no `biomes` is the fallback.

## How it plays

- Right-click the tower's chest: it opens like a normal chest titled "Offer coal: 520/1000". Put coal in
  and close it. Coal counts 1, coal blocks count 9; anything else goes straight back to your inventory.
- Every 250 coal, the next quarter of the finished tower is rebuilt from the bottom up, with a dust cloud
  and an announcement. Everyone sees "<player> offered 64 coal to The Old Tower (520/1000)".
- At 1000 the tower is complete: title on every screen, fireworks, and rewards for the top donors.
  Offline winners get theirs the next time they log in.
- **Everything comes back.** Every item a player puts in the chest is recorded (`ChallengeState.deposits`), and when
  the tower is complete it all goes back to whoever gave it: straight into the inventory of players who are online
  (what doesn't fit drops at their feet), the next time they join for the rest. Offerings made before this existed
  weren't recorded, so they don't come back. Operator `/challenge credit` donations have no items.
- **The tower becomes a base.** When it's complete the offering chest turns into a **Base Core** (from Ahrivaea
  Creatures) owned by the top donor. That's the only way to get a base: the Base Core has no recipe. The core can't
  be broken or moved (except in creative mode); it works like any other base (right-click to assign workers,
  upgrade with blocks), and only its owner can manage it. Towers completed before this get their core the next time
  the chest's chunk is loaded. Without Ahrivaea Creatures installed the chest just stays a chest.
- **The way to the End.** The End portal room under every tower is sealed until the tower is complete: each
  opening in its walls, floor and ceiling (the doorway, any cave holes) is filled with reinforced deepslate, and
  nothing in or around the room can be broken, placed or used, the portal frames included, by anyone outside
  creative mode. When the tower is finished the openings clear and everyone is told the way is open. Towers built
  before this existed get their room sealed the next time their stronghold's chunk loads (if not yet complete).
  `Challenges.sealPortalRoom`, `StrongholdTowers.portalRoom`.
- **Protection, then a claim.** Like spawn protection, nobody can break, place or use blocks (buckets, fire,
  doors ...) within 64 blocks of the tower, from 8 blocks below its base up to the sky, until it's rebuilt; the
  stronghold under it stays open so the End can still be reached. When it's complete its top donor claims it
  (announced to everyone), and from then on only they **and their guild** can build there. A guild is any
  scoreboard team, so Ahrivaea Guilds' guilds work (and so do vanilla `/team` teams); it's checked by name, so it
  works while the owner is offline. Towers finished before claims existed belong to their top donor. The offering
  chest works for everyone until the tower is complete, and players in creative mode (operators) aren't affected.
  `dev.ahrivaea.challenge.mixin.ServerLevelMixin` answers Minecraft's "may this player change this block" check.

- `/challenge` shows the leaderboard of the nearest tower; `/challenge list` lists every tower found so far
  with its location and progress; `/challenge top <tower>` shows a particular one.

## Commands for operators

| Command | What it does |
|---|---|
| `/challenge generate nearest` | Builds the tower over the nearest stronghold now. |
| `/challenge generate <n>` | Builds tower n+1 (0 = the first stronghold). |
| `/challenge place <challenge> [tower id]` | Places an extra tower for a challenge where you stand (corner), anywhere. |
| `/challenge credit <tower> <player> <amount>` | Adds to someone's total by hand. |
| `/challenge remove <tower>` | Stops tracking a tower (the blocks stay). |

## Files

One file per challenge, `data/ahrivaea/challenge/<id>.json` (12 starters: coal, iron, wheat, logs, copper,
bones, redstone, lapis, string, gold, flesh, diamond):

```json
{
  "name": "The Ember Spire",
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

- `items`: item ids, or `#tags` (e.g. `"#minecraft:logs": 1` accepts every kind of log), and what each counts for.
- `ruined` / `finished`: structure files (`data/ahrivaea/structure/tower/ruined.nbt` and `finished.nbt`). Both
  should be the same size; the finished one is revealed in horizontal slices from the bottom.
- `chest`: where the offering chest is, counted from the structure's corner.
- `rewards`: `rank` 1, 2, 3 … for the top donors; `rank` 0 for everyone who gave anything.

The structures in the mod now are placeholders made by `tools/placeholder_tower.py`. To use a real build,
save it with a structure block in Minecraft 26.3 (Save mode) — once ruined, once finished, same corner and
size — and replace the two .nbt files.
