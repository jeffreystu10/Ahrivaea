# Villagers

Villagers are creatures (`data/ahrivaea/creature/villager.json`) that live in villages: every vanilla villager (and
wandering trader) that turns up becomes one (`VanillaMobs.VILLAGERS`), at level 2-10. They never spawn in the wild
and can't be picked as a starter. Recruit one in a battle like any other creature.

## Training and trading

Open a villager's screen by right-clicking your villager when it's out, with **Villager** on the party screen, or
with `/villager` (the first villager in your party; `/villager party <slot>`, `/villager out <entity id>`).

- **Untrained:** pick one of 17 professions. Training starts as a Novice.
- **Ranks:** Novice, Apprentice, Journeyman, Expert, Master. Each rank is earned with a **deed**, counted from when
  that rank's training began (so a Mason's 64 stone are 64 *more* stone). When the deed is done, press
  **Become \<rank\>**. Advancements and the dex count everything ever done.
- **Shop:** every item the profession sells, by rank; locked ranks are dimmed. Click to buy, shift-click for 10.
  Prices are in **creature XP** (the pool from battles that recruiting and levelling also use).
- **Change profession:** starts over as a Novice in the new one (click twice to confirm).
- The villager keeps its training in the party, the box and out in the world, and wears the clothes of a vanilla
  profession: Mason mason, Carpenter and Engineer toolsmith, Gardener and Weaver shepherd, Farmer farmer, Cook
  butcher, Leatherworker leatherworker, Fisherman fisherman, Smith armorer, Hunter fletcher, Librarian librarian,
  Alchemist and Voidwalker cleric, Explorer cartographer, Netherwright weaponsmith, Jeweler nitwit.

## Professions

| Profession | Sells | Novice | Apprentice | Journeyman | Expert | Master |
|---|---|---|---|---|---|---|
| Mason | stone, earth, bricks, terracotta, concrete, glass, ice | mine 64 stone | mine 640 stone | make 256 stone bricks | mine 4,000 stone | mine 15,000 stone |
| Carpenter | logs, planks, wooden things | chop 32 logs | make 256 planks | chop 640 logs | make 64 chests | chop 5,000 logs |
| Gardener | saplings, leaves, flowers, grass, moss | gather 16 saplings | use 64 bone meal | gather 200 flowers | gather 800 saplings | see 150 forms in the dex |
| Farmer | crops, seeds, produce, eggs, milk, honey | harvest 48 wheat | bake 64 bread | harvest 1,000 crops | use 500 bone meal | harvest 8,000 crops |
| Cook | meat, cooked food, cakes, pies, stews | bake 16 bread | eat 100 meals | bake 8 cakes | eat 1,000 meals | use a smoker 500 times |
| Weaver | wool, carpets, beds, banners, dyes, string | gather 32 wool | make 64 dyes | make 16 beds | make 32 banners | gather 2,000 wool |
| Leatherworker | leather, leather armor, saddles, harnesses, leads | gather 16 leather | make 4 leather tunics | make 32 item frames | win 300 battles | gather 1,000 leather |
| Fisherman | fish, coral, prismarine, sea treasures | catch 10 fish | 60 fish | 250 fish | 750 fish | 2,000 fish |
| Smith | ores, metals, copper, tools, weapons, armor | mine 16 iron ore | smelt 192 iron | make 4 anvils | mine 40 diamond ore | make 4 netherite ingots |
| Hunter | bows, arrows, creature drops | win 10 battles | make 128 arrows | win 150 battles | recruit 15 creatures | win 1,500 battles |
| Librarian | books, paper, bookshelves, enchanted books | make 16 books | enchant 5 items | make 32 bookshelves | enchant 60 items | enchant 250 items |
| Alchemist | potions, brewing ingredients | brew a potion | make 32 blaze powder | drink 50 potions | throw 100 splash potions | drink 600 potions |
| Engineer | redstone, rails, minecarts, machines | make 16 redstone torches | 32 repeaters | 48 pistons | 64 hoppers | 200 observers |
| Explorer | maps, compasses, music discs, trims, sherds | walk 2 km | see 40 forms | walk 50 km | see 120 forms | see 220 forms |
| Netherwright | Nether blocks and treasures, netherite | enter the Nether | win 25 Nether battles | mine 2,000 netherrack | win 300 Nether battles | find ancient debris |
| Voidwalker | End blocks, pearls, shulkers, elytra | enter the End | win 25 End battles | find an End city | win 300 End battles | win 1,000 End battles |
| Jeweler | gems, amethyst, beacons, totems, nether stars | mine 64 ores | mine 10 diamond ore | catch 40 forms | catch 100 forms | catch 180 forms |

Potions are sold as specific potions (Alchemist; splash and lingering a rank later, stronger and longer ones a rank
after that; tipped arrows from the Hunter), enchanted books as each enchantment at its highest level (Librarian;
Mending and the other treasure enchantments at Master).

## What isn't sold

Creative-only things (command blocks, barriers, spawners, spawn eggs, bedrock, infested blocks, end portal frames,
reinforced deepslate ...), maps that point somewhere (explorer maps, filled maps), written books and suspicious stew
(each needs content), and Base Cores (they only come from completing towers). Everything else is: 1,539 items plus
28 potions in 4 forms and 41 enchanted books.

## Balance and editing

`tools/villager_trades.py` makes the profession files (`data/ahrivaea/villager_profession/*.json`) and
`Villager_Trades.xlsx` from the server's list of items (`tools/data/items.txt`):

- each item goes to the first profession whose pattern matches it (`RULES`);
- its value in XP comes from `EXACT`, the tool and armor tables, `VALUES`, or its rarity;
- an offer is a stack of cheap things (64 or 16) or one of anything worth 1 XP or more, cost = value x count;
- ranks: the profession's items cheapest and plainest first, split in fifths; anything worth more than 20 XP is at
  least Expert and more than 80 XP always Master. Examples: 64 cobblestone 3 XP, an iron ingot 2, a diamond 25,
  an elytra 2,000, a beacon 2,500.
- deeds per rank are in `DEEDS`. Deed types: `mined` / `crafted` / `used` / `picked_up` (block or item id or #tag),
  `custom` (a statistic: `minecraft:fish_caught`, `ahrivaea:battles_won`, `ahrivaea:nether_battles_won`,
  `ahrivaea:end_battles_won`, `ahrivaea:creatures_recruited` ...), `advancement`, `dex_seen`, `dex_caught`.

Run `python3 tools/villager_trades.py Villager_Trades.xlsx` after changing it.
