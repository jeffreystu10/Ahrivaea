# Ahrivaea — Game Design

The decisions agreed so far. Numbers marked *(tunable)* are starting values; the spreadsheet
`Ahrivaea_Influence_Chart.xlsx` has the same formulas for trying out balance changes.

## Creatures

- Every creature has a **species** (e.g. Dragon) and an **elemental influence**. The influence
  comes from the biome and time of day where it spawns, and changes its look (model and texture),
  its name (e.g. Ashen Dragon = Fire Dragon) and some of its moves.
- **Levels** run 0–50.
- **Stats** (7): HP, Attack, Defense, Speed, Magic, Luck, Charisma.
  - Each is 0–100 **at level 50**; the seven add up to between 500 and 600.
  - Each influence form of a species can have its own stats.
  - At level L a creature has `base × (0.25 + 0.75 × L / 50)` of each stat *(tunable)*.
  - Max HP = 20 + 2 × effective HP *(tunable)*.
- **What the stats do**
  - HP — hit points.
  - Attack — physical damage dealt. Defense — resists physical damage.
  - Magic — magic damage dealt *and* resisted.
  - Speed — movement on the battle grid: 3 + ⌊Speed ÷ 25⌋ squares per turn (3–7), from the
    level-50 value, so it does not grow with level *(tunable)*.
  - Luck — dodging and critical hits.
  - Charisma — supporting allies (rules to come; placeholder: +0.1% damage per point of
    adjacent allies' Charisma).
- **Moves**: each creature knows 6. Some are melee, some ranged. No PP or uses. Learned by level
  and by influence. Move list to come. Status effects come later.

## Influences (12)

None, Fire, Frost, Storm, Earth, Water, Nature, Void, Fae, Darkness, Radiant, Arcane.

Favor chart (2× / ½×): two closed loops plus None.

| Influence | Strong against | Weak to |
|---|---|---|
| Fire | Nature, Frost | Water, Earth |
| Frost | Storm, Nature | Fire, Storm |
| Storm | Water, Frost | Earth, Frost |
| Earth | Fire, Storm | Water, Nature |
| Water | Fire, Earth | Storm, Nature |
| Nature | Water, Earth | Fire, Frost |
| Radiant | Darkness, Fae | Arcane, Void |
| Darkness | Arcane, Void | Radiant, Fae |
| Arcane | Fae, Radiant | Darkness, Void |
| Fae | Void, Darkness | Radiant, Arcane |
| Void | Radiant, Arcane | Darkness, Fae |
| None | — | — |

If A hits B for 2×, B's attacks on A are ½×. Each influence resists its own element (except
None). Storm and Frost hit each other for 2×.

Which biome and time of day gives which influence: list to come (a draft is in
`data/ahrivaea/influence_rules/draft.json`).

## Battles (Fire Emblem style)

### Starting
- Only the player starts a battle, by hitting a wild creature. **Wild creatures are passive**: they wander, look
  at you and never attack or start a battle. Trainers are NPCs you talk to (later).
- You're taken to a **battle arena** themed after the biome the encounter happened in (25 themes, see
  [battles.md](battles.md#arenas)): a 15×15 to 27×27 battlefield seen from above, at the world's time of day. Your side starts
  on the south edge, the wild creatures on the north edge. Afterwards you go back to where you were; a wild
  creature you didn't defeat goes back too.
- Wild encounters: the creature you hit plus 1–4 more wild creatures of the area.

### Units
- **Your side: you plus 4 creatures** (the first 4 in your party).
- **You are a unit.** Your stats are the same 7 as a creature's and come from your character level (below).
  Your attack uses the weapon in your hand; armor adds to your Defense.
- **If you're knocked out, the battle is lost** and you retreat (like a Fire Emblem lord).

### Turns
- Player phase: each of your units may **move, then do one thing**: attack (with one of its moves), use an
  item, rally (you only), or wait. Then enemy phase. Repeat.
- Movement: squares per turn = 3 + ⌊Speed ÷ 25⌋. Stepping up 1 block costs a square like any other;
  2+ blocks up is a wall and drops of 4+ are too far, unless the unit flies. Lava can't be crossed except by
  Fire creatures. Units can pass through allies but not enemies, and can't stop on an occupied square.
- No swapping in bench creatures during a battle.

### Combat
- Damage formula as below. Moves have a range: melee (1 square) or ranged (e.g. 1–2 or 2–3).
- **Counterattack:** a unit that is attacked and survives hits back once if the attacker is within one of its
  moves' range.
- **High ground:** attacking from 2+ blocks higher gives +10% hit chance.
- **Water:** a unit standing in water deals half damage, except magic from a Water-influence move.
- **Items** (your action): use a healing potion (heals 40%) or a golden apple (heals 50%) on yourself or a
  unit next to you.
- **Rally** (your action): a creature next to you gets +(your Charisma ÷ 2)% damage on its next attack.
- **Support:** each attack also gets +0.1% damage per point of Charisma of the attacker's neighbouring allies.
- **Flee** (instead of acting): chance = 50% + (your side's average Speed − theirs) ÷ 2 %, between 10% and 95%.
  Failing ends your phase.
- Until there is a move list, every creature has *Strike* (melee, physical, power 40) and *<Influence> Bolt*
  (range 1–2, magic, power 35, its own influence).

### Ending
- Win: every enemy knocked out. Lose: you are knocked out, or all your creatures have fainted.
- Creatures keep their damage after a battle; fainted ones come back at 1 HP.

### Other players
- Can watch but can't interfere: battle units can't be hurt from outside and the battlefield's blocks can't
  be broken by others. Co-op battles later.

## Your character (MMO-style levels)
- Levels 1–50. XP for now comes from battles: 15 per enemy knocked out, +25 for winning. XP to the next
  level = 100 + 25 × level.
- Start with 30 in each stat. **Each level gives 6 points to spend on any stat** (`/character spend <stat> <n>`),
  up to 100 each. (At level 50 that is about 500, like a creature.)
- Speed sets your squares per turn the same way as for creatures. Armor adds 1.5 Defense per armor point.
- Classes or skill trees (e.g. the Seven Schools of the Dread Circle) can come later.

## After a battle

- **XP** goes to the player's pool, not the creatures: 10 XP per creature defeated. The player chooses
  which creatures to spend it on. Levelling is linear: 100 XP per level.
- **Recruiting**: instead of catching mid-battle, pay XP to recruit one of the creatures you
  defeated. Cost: 10 XP × its level *(placeholder)*.
