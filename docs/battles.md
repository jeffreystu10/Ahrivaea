# Playing a battle

The rules are in [design.md](design.md#battles-fire-emblem-style). This is how it plays.

**Starting.** Hit a wild creature. You and it are taken to a **battle arena** built for the biome you were in, and the
camera looks straight down on its 21×21 battlefield: you and the first 4 creatures in your party along the south
edge, the creature you hit and 1–4 more wild ones along the north edge. When you leave the results screen you're
back exactly where you were; if you fled or lost, the wild creature is back too (with the damage you did). Blue frames are your
units, red are enemies; the bar above each is its health.

**Your phase.** For each of your units:
1. Click it. Blue squares show where it can move this turn.
2. Click where it should go (or the unit itself to stay put).
3. Pick what it does at the bottom: one of its moves (then click a target shown in red), **Item** or **Rally**
   (you only; then click a unit in green), or **Wait**. Shortcut: after step 2, click an enemy in red to attack
   it with the first move that reaches.

Right-click or Escape goes back a step. **End turn** ends your phase early; **Flee** tries to escape.
Scroll to zoom in and out, WASD or the arrow keys to look around.

**Enemy phase.** The wild creatures move and attack one at a time. The log at the top left says what happened.

**After.** Win and you get XP for your creatures (`/party xp`) and for yourself (`/character`), and can
recruit any of the defeated creatures for XP. Lose or flee and you get nothing; your creatures keep their
damage either way (fainted ones come back at 1 HP).

**Your character.** `/character` shows your level, stats and points; `/character spend <stat> <points>` spends them.

Operator commands for testing: `/battle start` (with the nearest wild creature), `/battle end`, `/battle arena <theme>`
(build an arena and look at it; `/battle home` to go back),
`/character givexp <amount>`.

## Arenas

Arenas are built fresh for each battle in their own dimension (`ahrivaea:battle_arena`), always in daylight, so
trees, mobs, night and awkward terrain in the real world don't get in the way. Each has a 21×21 field (gentle
terraced hills, pools, obstacles that block squares, harmless decoration) and a 6-block border of the biome's
scenery. Its grass, leaves and water take the colours of the biome the encounter happened in.

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
