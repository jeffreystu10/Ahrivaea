# Bases (Palworld-style work)

Creatures you've recruited can work for you at a base.

## Setting up

1. Craft a **Base Core** (4 iron ingots in the corners, 4 amethyst shards on the sides, a Vessel in the middle) and
   place it. The base is the area around it: 24 blocks out each way (level 1), 8 down and 16 up.
2. Right-click the core. On the right are your party and box; **Work here** puts one to work. On the left are the
   workers, what each is doing right now, and **Send away** (it goes back to your party, or the box if that's full).
3. Workers stay in the base and find work on their own. Each creature has up to two jobs (from the
   creature list) and does the first one it has something to do for. Higher-level creatures work a little faster.
4. Upgrade the core by right-clicking it with: 4 iron blocks (level 2), 4 gold blocks (3), 2 diamond blocks (4),
   4 emerald blocks (5).

| Level | Radius | Workers |
|---|---|---|
| 1 | 24 | 5 |
| 2 | 28 | 7 |
| 3 | 32 | 9 |
| 4 | 36 | 12 |
| 5 | 40 | 15 |

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
