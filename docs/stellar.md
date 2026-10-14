# Stellar gear

Netherite tools and armor upgraded with a **nether star** at a smithing table: put the nether star in the
template slot and the netherite piece in the base slot (the third slot stays empty). Enchantments carry over.
Stellar gear is tougher than netherite (tools 3046 uses, armor durability 45×), fireproof, and glows.

| Item | Power |
|---|---|
| Stellar Pickaxe, Shovel | Mine 3×3, one block deep, across the face you're looking at (look steeply up/down for a flat 3×3). Only blocks that tool is meant for; never bedrock or much harder blocks (no instant obsidian). Fortune/Silk Touch apply to every block. |
| Stellar Axe | Fells a whole tree from one log (up to 256 logs). Never touches logs a player placed, logs inside villages or other structures, or wood that doesn't have natural leaves on it. |
| Stellar Hoe | Tills 3×3. On fully grown crops, harvests 3×3 and replants them. |
| Stellar Sword | Heals you for 20% of the damage it deals. |
| Full Stellar armor | Creative-style flight (double-tap jump). Take a piece off and flight ends; if you were flying you get Slow Falling to land safely. |

**Sneak** to use the pickaxe, shovel, axe or hoe normally (one block at a time).

Until there's art, the items use the netherite look with an enchantment shimmer and purple names. To give them
their own look, add textures and point `assets/ahrivaea/items/stellar_*.json` at new models.

Where it lives in the code: `src/main/java/dev/ahrivaea/stellar/`. Placed logs are remembered per chunk
(`PlacedLogs`), recorded when a player places any log.
