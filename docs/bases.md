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
   creature list) and does the first one it has something to do for. Higher-level creatures work a little faster.
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
| **Mining** | Works a **Mining Post** (crafted: stone pickaxe on top, cobblestone and logs below): stone and ores, into a chest next to the post. Deeper than y=0: deepslate, more diamonds; in the Nether: netherrack, quartz, gold, ancient debris; in the End: end stone, chorus, pearls | 43 |
| **Smelting** | Runs furnaces, smokers and blast furnaces that have something in them: no fuel needed, twice as fast | 28 |
| **Transport** | Carries loose items and finished smelting to the base's chests and barrels | 58 |
| **Ranching** | Every 2–3 minutes, produces its own drops (from the creature list) | 51 |
| **Fishing** | Fishes from water in the base (the normal fishing loot, with luck from its level) | 33 |
| **Defense** | Guards the base and fights off raids (below) | 82 |
| **Brewing** | Runs brewing stands that have something to brew: no blaze powder needed, twice as fast | 22 |
| **Enchanting** | Studies at an enchanting table: 1–2 bottles o' enchanting a minute, and 1 time in 20 an enchanted book | 8 |
| **Redstone** | Powers a **Creature Dynamo** (crafted: copper ingots in the corners, redstone on the sides, a block of redstone in the middle): a full redstone signal on every side while it works, plus a redstone dust every minute | 24 |
| **Crafting** | Works **Crafters** in the base: crafts whatever each is set up for, every few seconds, as a redstone pulse would | 16 |
| **Ice** | Makes ice and snowballs from water in the base, now and then packed or blue ice | 14 |
| **Lighting** | Puts torches from the base's chests on dark spots | 2 |
| Riding | Comes with rideable creatures | 1 |

Every one of the 277 forms has at least one job it can do. Items from harvesting, felling, fishing, ice-making,
studying and ranching land on the ground; a Transport worker brings them to the chests (put a chest or barrel in
the base). Mining fills the chest beside its post directly.

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

Riding (with rideable creatures).
