# Bases (Palworld-style work)

Creatures you've recruited can work for you at a base.

## Setting up

1. Get a **Base Core**. It can't be crafted: when a tower (Ahrivaea Tower Challenges) is fully rebuilt, its offering
   chest turns into a Base Core owned by the tower's top donor, who also gets a second one to place anywhere (see [challenges](challenges.md)). The base is the
   area around it, in whole chunks: the core's chunk and every chunk around it (3x3 chunks at level 1), from 8
   blocks below the core to 16 above. (Creative mode still has Base Cores to place.)
2. Right-click the core. On the right are your party and box; **Work here** puts one to work. On the left are the
   workers, what each is doing right now, and **Send away** (it goes back to your party, or the box if that's full).
3. Workers stay in the base and find work on their own. Each creature has up to two jobs (from the
   creature list) and does the first one it has something to do for. How fast it works depends on its level (see Work speed below).
4. Upgrade the core by right-clicking it with: 4 iron blocks (level 2), 4 gold blocks (3), 2 diamond blocks (4),
   4 emerald blocks (5).

| Level | Area (chunks) | Workers |
|---|---|---|
| 1 | 3 x 3 (48 x 48 blocks) | 5 |
| 2 | 5 x 5 (80 x 80) | 7 |
| 3 | 7 x 7 (112 x 112) | 9 |
| 4 | 9 x 9 (144 x 144) | 12 |
| 5 | 11 x 11 (176 x 176) | 15 |

**Keeping a base loaded.** While its owner is online, a base stays loaded and running (workers work, crops grow,
furnaces smelt) wherever the owner is, even in another dimension. Each base has a **Keep loaded** switch at the
bottom of its screen (on by default); turn it off for bases you don't need running. When the owner logs off, their
bases unload like any other land. (`Bases.LOADERS` remembers every owned base; a ticket of the base's size plus two
chunks makes every chunk of the base run entities. Raiders still only come while the owner is nearby.)

Only the owner can manage a base or break the core, and only once all its workers are sent away. No wild
creatures spawn inside a base. Workers can only be hurt by raiders.

## Jobs

The creature list's ~190 job descriptions are sorted into these (the base screen shows each creature's jobs):

| Job | What the worker does | Forms with it |
|---|---|---|
| **Farming** | Harvests ripe crops (wheat, carrots, potatoes, beetroot, nether wart, berries, cocoa, pumpkins, melons, sugar cane, cactus, bamboo) and replants them | 43 |
| **Growth** | Tends crops, saplings and other plants: half the time like a dose of bone meal, otherwise a few growth ticks | 37 |
| **Logging** | Fells grown natural trees (ones with natural leaves; log buildings are safe) and plants a sapling where each stood | 29 |
| **Mining** | Digs out the chunk under a **Mining Post** (crafted: stone pickaxe on top, cobblestone and logs below) down to bedrock, into the chests beside the post: see Quarries below | 43 |
| **Smelting** | Runs furnaces, smokers and blast furnaces that have something in them: no fuel needed, at the worker's speed | 28 |
| **Transport** | Carries loose items and finished smelting to the base's chests and barrels | 58 |
| **Ranching** | Every 2–3 minutes, produces its own drops (from the creature list) | 51 |
| **Fishing** | Fishes from water in the base (the normal fishing loot, with luck from its level) | 33 |
| **Defense** | Guards the base and fights off raids (below) | 82 |
| **Brewing** | Runs brewing stands that have something to brew: no blaze powder needed, at the worker's speed | 22 |
| **Enchanting** | Studies at an enchanting table: 1–2 bottles o' enchanting a minute, and 1 time in 20 an enchanted book | 8 |
| **Redstone** | Powers a **Creature Dynamo** (crafted: copper ingots in the corners, redstone on the sides, a block of redstone in the middle): a full redstone signal on every side while it works, plus a redstone dust every minute | 24 |
| **Crafting** | Works **Crafters** in the base: crafts whatever each is set up for, every few seconds, as a redstone pulse would | 16 |
| **Ice** | Makes ice and snowballs from water in the base, now and then packed or blue ice | 14 |
| **Lighting** | Puts torches from the base's chests on dark spots | 2 |
| Riding | Comes with rideable creatures | 1 |

Every one of the 277 forms has at least one job it can do. Items from harvesting, felling, fishing, ice-making,
studying and ranching land on the ground; a Transport worker brings them to the chests (put a chest or barrel in
the base). Mining fills the chest beside its post directly.

### Replanting, felling and silk touch

- **Farmers** replant with one of the seeds (or carrots, potatoes, nether wart, cocoa beans) the crop drops, like a
  player; the rest go to the chests. If none dropped, the spot is remembered and a farmer fetches one from a base chest.
- **Loggers** are tree fellers: they work from the ground and the whole tree comes down at once, its logs and its
  own leaves, so saplings, sticks and apples drop straight away. The time it takes is the time to chop every log with
  a stone axe, plus 2 seconds, paid before the tree falls. Then they replant with one of the saplings that dropped,
  or fetch one from a base chest if none did.
- **Miners** at level 20 and up mine with **silk touch** (stone, ore blocks, glowstone ...). It can be switched off
  per miner on its jobs panel.

### Quarries

A Mining Post in a base turns the chunk it stands in into a quarry, dug by the base's miners:

1. **The staircase.** They cut a staircase that spirals down inside the chunk's edge, one block down per step, with
   room to walk, all the way to the bottom. Gaps under a step are filled with stone.
2. **The layers.** Then they clear the chunk a layer at a time, from just below the post down to the layer above
   bedrock, leaving a **one-block wall** around the chunk's edge and the staircase. Every gap in the wall is filled
   with stone (cobbled deepslate below y=0) and any water or lava in the way is drained, so nothing flows or falls in.
3. **Done:** an empty chunk-sized pit with a wall and a staircase down to bedrock.

Everything they dig goes straight into the **chests and barrels touching the post** (dug as with a diamond pickaxe;
silk touch from level 20; what a container held comes too). If there's no chest, or the chests are full, the miners
stop, say so on the base screen, and the owner gets a message every couple of minutes: put another chest next to the
post and they carry on. Several miners share a quarry, each taking its own blocks; a base can have several posts.

Never dug: bedrock and unbreakable blocks, spawners, trial spawners and vaults, portals and end portal frames, base
cores and mining posts, **anything in a stronghold**, and **blocks players placed** in a base's chunks (noted from
this version on; blocks placed before can't be told apart). Other structures (ancient cities, mineshafts, trial
chambers ...) are kept too, unless you set the quarry to dig them.

Right-click the Mining Post to see its quarry (stage, layer, % done, blocks dug, chests) with buttons: **Pause /
Resume**, **Structures: kept / dug**, **Restart** (`/quarry <x y z> pause|structures|restart`). Digging a block takes
about 0.4 s for dirt and 1.2 s for stone at level 20 (plus walking), more for harder blocks, faster at higher levels.
A miner that can't find its way down climbs straight to its next block. (`Quarry`, `PlacedBlocks`.)

### Work speed

A level 20 worker works as fast as a player would, or as a furnace or brewing stand does on its own. Every level
above 20 makes it 2% faster and every level below 2% slower: 62% at level 1, 120% at level 30, 160% at level 50.
The jobs panel shows each worker's speed. The times for a level 20 worker:

| Job | Level 20 time |
|---|---|
| Farming | 1 s a crop (harvest and replant) |
| Growth | 2 s a plant |
| Logging | 0.75 s a log (like a stone axe), so about 4 s for a small oak |
| Mining | 0.4 s for dirt, 1.2 s for stone, longer for harder blocks (plus walking) |
| Smelting | 10 s an item in a furnace, 5 s in a smoker or blast furnace (their own speed; no fuel) |
| Brewing | 20 s a batch (the stand's own speed; no blaze powder) |
| Fishing | 20 s a catch (about a player's average without Lure) |
| Crafting | 1 s a craft |
| Transport | 0.25 s to pick up, 0.5 s to put away, sort or empty a furnace (plus walking) |
| Lighting | 0.5 s a torch |
| Enchanting | 60 s a study session |
| Ice | 15 s a batch |
| Ranching | its drops every 2-3 minutes |
| Redstone | a redstone dust every minute of powering |

Walking speed doesn't change with level. (`WorkerBrain.speedAt`, `PAR_LEVEL`, `PER_LEVEL`.)

### Choosing what each worker does

The **Jobs** button by a worker on the base screen lists its jobs in the order it does them: it works on the first
one that has something to do. Use ▲/▼ to change the order and **On/Off** to switch a job off, so a creature that
can farm and fish only fishes, or fishes first. A worker with every job off stands idle ("its jobs are switched
off"). Switching off Ranching or Defense stops those too. The settings stay with the worker (`Worker.order`,
`Worker.off`; `Bases.changeJob`).

### Sorting the chests

Put an **item frame** on a chest or barrel and put an item in it: that chest is now labelled for that item and
everything in its category. Categories are item tags: logs, planks, saplings, leaves, wool, fish, coal, arrows,
sand, dirt, meat, eggs, mushrooms, each tool type, boats, candles, terracotta, beds, signs, rails, banners, and the
common `c:` tags (ores, raw materials, ingots, gems, nuggets, dusts, seeds, crops, foods, potions, dyes, stones,
cobblestone, music discs, armor, enchanted books, glass). So a frame with an oak log takes every log, one with raw
iron every raw ore. Several frames on one chest label it for all of them; a frame on either half of a double chest
labels both. Transport workers put what they carry in, in order of preference:

1. a chest labelled for that exact item,
2. a chest labelled for its category,
3. an unlabelled chest that already holds some of it,
4. the nearest unlabelled chest with room.

They never put anything in a chest labelled for something else. On every third trip, and whenever there's nothing
to carry, they also **tidy**: anything sitting in an unlabelled chest, or the wrong labelled one, that has a
labelled chest with room is moved there. Unlabelled chests work as the overflow and the "inbox": put things in one
and they're sorted for you. (`Sorting`.)

## Raids

At nightfall, a base may be raided: 25% chance plus 5% per base level, at most once a night, and only while its
owner is online and within 128 blocks (bases aren't raided while you're away). In the Nether and the End, where
there's no night, it's once a day.

- 2 + the base level + 0–2 aggressive creatures of the area turn up at the edge of the base, glowing, at about the
  level of your workers. They head for the core, fighting any worker in their way.
- **Defense** workers drop what they're doing and fight them, with the battle damage formula (each uses Strike or
  its influence Bolt, whichever hits harder; Bolt reaches 6 blocks).
- Raiders at the core steal: every 10 seconds each takes up to 16 items from a random chest in the base. Those
  items are gone.
- You can join in: hit a raider with a sword to fight it in a battle.
- Workers that lose a fight aren't killed: they're knocked out and rest for a minute.
- Defeated raiders drop their loot (from the creature list) for your Transport workers to collect.
- The raid ends when every raider is defeated, at daybreak, or after 5 minutes; you're told how it went and how
  much was stolen.

`/gamerule ahrivaea:base_raids false` turns raids off; operators can start one with `/base raid` while standing in
a base.

## Still to come

Sorting ideas not built yet: exact-only labels (a renamed frame), keeping a set amount of something in a chest,
supply runs (fuel and ore from a labelled chest to the furnaces, ingredients to the brewing stands, torches to the
lighting workers), a trash chest that destroys what's put in it, and a sorting terminal that shows every chest's
contents.

Riding (with rideable creatures).
