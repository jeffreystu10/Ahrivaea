# Playing a battle

The rules are in [design.md](design.md#battles-fire-emblem-style). This is how it plays.

**Starting.** Hit a wild creature with a sword (any sword; nothing else starts a battle). You and it are taken to a **battle arena** built for the biome you were in, and the
camera looks straight down on its 21×21 battlefield: you and up to 4 of your creatures along the south edge
(first any you had **out in the world** with you, within 64 blocks; then the first ones in your party), the creature
you hit and its reinforcements along the north edge. When you leave the results screen you're back exactly where you
were, with the creatures that were out beside you again. Unless you won, the wild creature is back too, with the
damage you did (at full health if you knocked it out); it's only gone for good when you win. Blue frames are your
units, red are enemies; the bar above each is its health.

**Reinforcements** depend on the level of the creature you hit: up to 1 at levels 1-10, 2 at 11-20, 3 at 21-30, 4
at 31-40 and 5 at 41-50, each turning up 50%, 60%, 70%, 80% or 90% of the time (`Battles.reinforcements`). So a
level 5 creature is alone half the time, and a level 45 one usually brings four or five.

**Winning and losing.** You win when every enemy is beaten. You lose only when you are knocked out yourself: your
creatures fainting doesn't end the battle, and you can fight on (or flee).

**Your phase.** For each of your units:
1. Click it. Blue squares show where it can move this turn.
2. Click where it should go (or the unit itself to stay put).
3. Pick what it does at the bottom: one of its moves (then click a target shown in red), **Item** or **Rally**
   (you only; then click a unit in green), or **Wait**. Shortcut: after step 2, click an enemy in red to attack
   it with the first move that reaches.

Right-click or Escape goes back a step. **End turn** ends your phase early; **Flee** gets you out straight away, at any time, even during the enemy phase.

**The view.** Click and drag the battlefield to look around, scroll to zoom in (towards the mouse) and out, WASD or
the arrow keys also move the view, Space looks at the selected unit and R goes back to the whole field. A click
only counts as a click if the mouse didn't move; otherwise it's a drag.

**The screen.** A coloured banner says whose turn it is (blue: yours, red: the enemy's), with a hint under it.
Your team's cards are on the left and the wild side's on the right: name, level, health, and "done" once a unit has
acted; click a card to look at that unit (and pick it, if it's yours and can still act). Units on the field stand
on a tinted square with a thick frame and a health bar (the selected one flashes yellow). When a unit has moved, its
actions (moves with their range, Item, Rally, Wait) pop up beside it. Damage and healing float up as numbers. The
selected or hovered unit's card is at the bottom left; the last three things that happened are at the bottom.

**Enemy phase.** The wild creatures move and attack one at a time. The log at the top left says what happened.

**After.** Win and you get XP for yourself (`/character`: 5 for the win plus about 5 per creature beaten, up to
double for ones above your level and as little as 1 for ones far below) and the same amount for your creatures
(`/party xp`). Creatures and your character level on the same curve (100 + 25 x level XP to the next level), so
your creatures grow at your pace. Lose or flee and you get nothing; your creatures keep their damage either way
(fainted ones come back at 1 HP).

**Recruiting** (`dev.ahrivaea.party.RecruitRules`). You can recruit any defeated creature if you have the creature
XP: 100 per level, times 1 (Common), 1.5 (Uncommon), 2 (Rare), 3 (Super Rare) or 5 (Legendary), so a Lv.4 Common
is 400 XP and a Lv.20 Legendary 10,000. Catching with a Vessel has no limits (the Vessel is its cost).

**Reinforcements.** The wild creatures that join the one you challenged are never rarer or higher level than it:
a Common Lv.4 brings only Commons of Lv.2 to 4 (if nothing from the area fits, more of its own kind).

**Wild levels.** Wild creatures appear within 5 levels of the player's character level: only creatures whose biome
level range reaches that band, at a level inside it (so high-level creatures don't appear until you're close to
their level, and an area where nothing fits stays empty for you).
Vanilla mobs that turn into creatures near a player follow the same band.

**Your character.** `/character` shows your level, stats and points; `/character spend <stat> <points>` spends them.

Operator commands for testing: `/battle start` (with the nearest wild creature), `/battle end`, `/battle arena <theme>`
(build an arena and look at it; `/battle home` to go back),
`/character givexp <amount>`.

## Arenas

Arenas are built fresh for each battle in their own dimension (`ahrivaea:battle_arena`), so trees, mobs and
awkward terrain in the real world don't get in the way. Each has a square field (gentle terraced hills, pools,
obstacles that block squares, harmless decoration) and a 6-block border of the biome's scenery. Its grass, leaves
and water take the colours of the biome the encounter happened in.

- **Time of day** is the world's: the arena dimension runs on the overworld clock, so a battle at dusk is fought
  at dusk. (Nether and End arenas get the overworld's sky too.)
- **Size** depends on the biome and the battle: open country 25×25, woods and most others 21×21, the Nether
  19×19, caves 17×17; one size up for a crowded battle (9+ units), one down for a small one (4 or fewer). Between
  15×15 and 27×27. The camera rises to fit.
- **Everyone gets their own arena.** Each battle going on at once is built in its own slot, 1024 blocks apart
  (further than anyone can see); a slot is reused, rebuilt from scratch, once its battle is over.
- **Only for battles.** Nobody can get in otherwise: anyone in the arena dimension who isn't in a battle (or an
  operator previewing with `/battle arena`) is sent back within a second. Blocks there can't be broken, placed
  or used.
- **Safe return.** If the spot you left has changed (built over, flooded, lava), you're put on the nearest safe
  ground within 8 blocks sideways and 12 up or down; failing that on top of the ground there, and failing that at
  the world spawn. A wild creature going back uses the same search.

| Theme | Biomes | Field | Water / lava | Obstacles | Border |
|---|---|---|---|---|---|
| Plains | plains and anything else | grass | ponds | boulders | oak woods |
| Meadow | meadow, flower forest, sunflower plains | grass, flowers | — | boulders | grassy hills |
| Forest | forest | grass | ponds | oak stumps | oak woods |
| Birch forest | birch forests | grass, flowers | — | birch stumps | birch woods |
| Dark forest | dark forest, pale garden | podzol, mushrooms | — | dark oak stumps | dark oak woods |
| Cherry | cherry grove | grass, petals | ponds | cherry stumps | cherry woods |
| Taiga | taigas | podzol, ferns | ponds | spruce stumps | spruce woods |
| Snowy | snowy, frozen, ice, grove, jagged peaks | snow | ice | ice spikes | snow cliffs |
| Desert | desert | sand | oasis with sugar cane | cactus | dunes |
| Badlands | badlands | red sand | — | terracotta pillars | mesa cliffs |
| Savanna | savannas | grass | — | acacia stumps | acacia woods |
| Jungle | jungles | grass, ferns | ponds | jungle stumps | jungle woods |
| Swamp | swamp, mangrove swamp | mud | many shallow pools | mangrove roots | mangrove woods |
| Mountain | stony and windswept biomes, peaks, slopes | stone | — | boulders | stone cliffs |
| Ocean | oceans, beaches, rivers | sand | wide shallows | sandstone | open sea |
| Mushroom | mushroom fields | mycelium | — | huge mushrooms | mushroom grove |
| Cave | dripstone caves, anywhere deep underground | stone | lava | dripstone | cave walls |
| Lush cave | lush caves | moss | ponds | azalea | cave walls |
| Deep dark | deep dark | deepslate | — | deepslate pillars | deepslate walls |
| Nether | nether wastes | netherrack | lava | nether brick pillars | netherrack walls |
| Soul valley | soul sand valley | soul sand | lava | bone | basalt walls |
| Crimson | crimson forest | crimson nylium | lava | crimson stems | netherrack walls |
| Warped | warped forest | warped nylium | lava | warped stems | netherrack walls |
| Basalt | basalt deltas | basalt | lava | basalt pillars | basalt walls |
| End | the End | end stone | — | obsidian | obsidian pillars |

If you log out mid-battle, the battle counts as fled and you're sent back when you log in again.

**Starter.** A new player (empty party) picks a starter when they first join: any of the Common creatures in their
None form, shown with their stats and a turning model. It joins the party at level 1. `/starter` reopens the choice
until one is picked (`dev.ahrivaea.party.Starters`, `client/StarterScreen`). Players who already had creatures skip it.

**The road to the End.** A battle won at night also has a 10% chance of an ender pearl, and every Fire creature
beaten in the Nether a 25% chance of a blaze rod (`BattleSettings.NIGHT_PEARL_CHANCE`, `NETHER_BLAZE_ROD_CHANCE`).
They make Eyes of Ender (vanilla recipe), which lead to the strongholds and the towers over them; a tower's End
portal room opens when the tower is rebuilt (see challenges.md).
