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

- **Turns**: player phase (all your creatures act), then enemy phase.
- **Field**: the real Minecraft terrain around the encounter, seen from above. The player's
  character stands still while the battle runs.
- **Your side**: 4 creatures from your party of 6.
- **Wild encounters**: you meet one creature in the world; when the battle starts, 1–4 more
  wild creatures join it.
- **Trainer battles**: against people met in the overworld.
- **Fainting**: no permadeath. A creature at 0 HP faints and recovers after the battle.
- **Terrain**
  - Lava cannot be crossed, except by Fire creatures.
  - Water halves all damage, except magic from a Water-influence move.
    *(Assumed: applies to attacks made by a creature standing in water.)*
- **Damage** = Power × level scale × stat ratio × influence × same-influence bonus × crit ×
  support × roll *(all tunable)*
  - Stat ratio: (Attack + 10) ÷ (Defense + 10) for physical, (Magic + 10) ÷ (Magic + 10) for magic.
  - Same-influence bonus ×1.2; crit ×1.5; roll 0.9–1.1.
  - Hit chance = move accuracy + 0.25% per point of (attacker Luck − defender Luck).
  - Crit chance = 5% + 0.15% × attacker Luck − 0.05% × defender Luck.

## After a battle

- **XP** goes to the player, not the creatures: 10 XP per creature defeated. The player chooses
  which creatures to spend it on. Levelling is linear: 100 XP per level.
- **Recruiting**: instead of catching mid-battle, pay XP to recruit one of the creatures you
  defeated. Cost: 10 XP × its level *(placeholder)*.
