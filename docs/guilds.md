# Guilds (Ahrivaea Guilds)

Groups of players who look after each other. Server only: players don't need the mod.

## Playing

- `/guild create <name>` founds a guild (2-16 letters, digits or _) with you as its leader. Everyone is told.
- The leader invites players who are online with `/guild invite <player>`. They get a chat message with a
  **[Join]** button, or type `/guild join <name>`. Invitations last 5 minutes. You can be in one guild at a time.
- `/guild` shows your guild and its members (who's online, who leads); `/guild list` shows every guild.
- `/guild leave` leaves. If the leader leaves, the next member (alphabetically) leads; a guild with nobody left ends.
- Leaders: `/guild kick <member>`, `/guild leader <member>` (hand over), `/guild color <color>`, `/guild disband`.
- `/teammsg <message>` (or `/tm`) is vanilla's team chat, and talks to your guild.

## What a guild does

- **No hurting each other.** Friendly fire is off, so guild members can't hurt each other, with weapons, arrows or
  anything else. Tame creatures and pets are covered too: a member's creatures can't hurt guild mates or their
  creatures, and members can't hurt each other's creatures (`Guilds.init`, on Fabric's damage event).
- **Shared towers.** Members can build, break and use blocks around every tower a member owns (Ahrivaea Tower
  Challenges checks for the owner's team; see [challenges](challenges.md)). The tower's base itself is still managed
  only by its owner.
- Names show a coloured `[Guild]` tag (in chat, the player list and over heads).

## How it works

Each guild is a vanilla scoreboard team named `guild_<name in lower case>`, with friendly fire off, a prefix and a
colour, so it's saved with the world and works for players without the mod. Who leads each guild is saved with the
overworld (`Guilds.GUILDS`). Teams are by player name. Operators can still use `/team` (a team made that way isn't a
guild unless its name starts with `guild_`).
