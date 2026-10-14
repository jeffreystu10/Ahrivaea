# Playing a battle

The rules are in [design.md](design.md#battles-fire-emblem-style). This is how it plays.

**Starting.** Hit a wild creature. The camera rises to look straight down on the 21×21 battlefield around you:
you, the first 4 creatures in your party, the creature you hit and 1–4 more wild ones. Blue frames are your
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

Operator commands for testing: `/battle start` (with the nearest wild creature), `/battle end`,
`/character givexp <amount>`.
