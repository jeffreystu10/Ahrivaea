# Ahrivaea Guide

An in-game book about the pack. Press **G** (change it in Controls) or type `/guide` (`/guide <mod>` opens
that mod's page).

- **Mods** on the left, with **NEW** on any you haven't opened yet. When you join a world, chat lists the new ones.
- **About**: what the mod does, numbered *Getting started* steps, its items, everything it does, its commands and
  whether players need to install it.
- **Recipes**: every recipe from that mod (or from all of them) as crafting-table, smithing-table and stonecutter
  cards, with a search box. Slots that take "any log" and the like cycle through the options.
- **Click any item** to see every recipe that makes it, then every recipe that uses it.

## Where its content comes from

The build writes `assets/ahrivaea_guide/guide.json` (task `guideData` in `build.gradle`) from `mods.json`:
`name`, `summary`, `guide` (the Getting started steps, written for players), `features`, `commands`,
`players_install`, plus every recipe and item file that falls under the mod's `include` patterns. So a new mod or
recipe shows up in the guide without touching the guide's code: give the mod's entry a `guide` list and you're done.

Which mods a player has read is kept in `config/ahrivaea_guide.json` on their computer.
