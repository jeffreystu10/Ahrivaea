# Ahrivaea Fast Minecarts

Minecarts top out at **30 blocks per second** instead of vanilla's 8: faster than flying with an elytra and rockets,
and a little slower than a top-bred horse (see [horses.md](horses.md)).

- In water the top speed halves (15). Furnace minecarts go half speed (15) too.
- The mod turns on Minecraft's newer minecart physics in every world. It's normally an experiment you choose when
  creating a world ("Minecart Improvements"). The old physics can't go faster than 8 without flying off the track at
  corners and slopes; the new physics follows the rails at any speed.
- Powered rails speed carts up a little at a time, so it takes a longer run of them to reach full speed. Unpowered
  rails slow carts down only slightly.
- The new physics changes some details redstone builders rely on (how carts push each other and how far they coast),
  so a few old minecart contraptions may behave differently.
- The top speed is a constant in `MinecartsMod.SPEED`; ask to make it a setting if you want to tune it in game.
