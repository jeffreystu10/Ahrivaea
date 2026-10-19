# Ahrivaea Map

A minimap and a full-screen map of everywhere you've been, showing the creatures around you and every player on the
server. Players and the server both need the mod; on a server without it the map stays off.

- **Explored map:** everything your game loads around you is drawn, coloured like a vanilla map (shaded by slope,
  water darker the deeper it is). Places you haven't been stay dark. Each dimension has its own map; in the Nether
  it's the ground under the roof. Changes are redrawn as you go: the chunks around you every second, everything in view
  every 5 seconds.
- **Saved on your computer:** the map is kept in `ahrivaea_map/` in your game folder, as 256x256-block PNG tiles, one
  folder per single-player world or server and per dimension. It's saved every 30 seconds and when you leave.
- **Minimap:** top right, one pixel per block (100 blocks across), north up, your coordinates underneath. It moves down
  when effect icons are showing. **N** shows or hides it (remembered in `config/ahrivaea_map.json`).
- **Full map:** **M**. Scroll to zoom (8 px per block down to 8 blocks per px), drag to move around, "Centre on me" to
  follow you again. The bottom line shows the block under the mouse and anyone who's in another dimension.
- **Creatures:** every creature (or mob) within 128 blocks of you, as a dot in its name-tag colour: Ahrivaea
  creatures in their influence colour, unnamed hostile mobs red, others white. The rim says whose it is: dark for wild,
  white for yours, gold for another player's. Hover on the full map for its name and level.
- **Players:** everyone in your dimension, wherever they are, as their face (on the minimap, players further than the
  edge sit on the edge, greyed). Names show on the full map; hover for their coordinates. You're the red arrow.
  Spectators and invisible players aren't shown (unless you're a spectator).
- **Server switches:** `/gamerule ahrivaea_map:show_players false` and `/gamerule ahrivaea_map:show_creatures false`
  hide them (you always see yourself).

How it works: the explored map is drawn by each player's own game (`MapTiles`), so the server does no extra work
for it. Twice a second the server sends each player with the mod where the players are and the creatures near them
(`Markers`); the map just reads their name tags, so it works for any mob, and needs nothing from the other Ahrivaea mods.
