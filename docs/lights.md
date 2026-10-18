# Ahrivaea Handheld Lights

Hold something that gives off light and the space around you is lit as you move, without placing it. Server only:
players don't need the mod.

- **What lights up:** any held block item that gives off light, at its own brightness (torch 14, lantern 15, soul
  torch and soul lantern 10, glowstone, sea lantern, froglights, shroomlight, jack o'lantern and campfire 15,
  redstone torch 7, end rod 14 ...), modded blocks included. Plus glowing items: lava bucket 15, glow berries 14,
  nether star and end crystal 12, blaze rod and fire charge 10, blaze powder and glowstone dust 8, glow ink sac,
  magma cream, spectral arrow and bottle o' enchanting 6 (`HeldLights.GLOWING`). Either hand; the brighter wins.
- **How:** an invisible light block (Minecraft's own `light` block) at that brightness sits in the air at your head
  (at your feet if your head's in a block, or just above). It moves when you move to another block and is taken
  away when you put the item down, log off, die, change dimension or go into spectator mode. In still water it's a
  waterlogged light, so it lights underwater too; it never replaces anything but air and water.
- **Everyone sees it:** it's real light, so other players see you glow, with or without the mod.
- **Leftovers:** where the lights are is saved with each dimension; any left after a crash are cleared the next time
  their chunk loads, and all are taken down when the server stops.
- **Off switch:** `/gamerule ahrivaea:held_lights false`.

Quarry miners and other workers treat these lights like air (they're never dug and never block anything).
