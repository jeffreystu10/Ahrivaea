# Ahrivaea Swift Horses

Faster horses, and a way to make them faster still. Covers horses, donkeys, mules, skeleton horses and zombie
horses (not llamas or camels). Meant for worlds other than Ahrivaea, where rideable creatures take the horses' place.

## Speed tiers

Every horse has a speed tier, 0 to 5. The tier multiplies the horse's own speed (the one it inherited the vanilla
way), so a well-bred horse stays faster than a poorly bred one at the same tier.

| Tier | Multiplier | Slowest vanilla horse | Average | Best-bred | How |
|---|---|---|---|---|---|
| 0 | ×1.5 | 7.1 | 14.2 | 21.3 | every horse, untrained |
| 1 | ×1.76 | 8.3 | 16.7 | 25.0 | 8 Swift Feed |
| 2 | ×2.02 | 9.6 | 19.2 | 28.8 | 8 Golden Feed |
| 3 | ×2.28 | 10.8 | 21.6 | 32.5 | 4 Racing Feed |
| 4 | ×2.54 | 12.0 | 24.1 | 36.1 | breeding only |
| 5 | ×2.8 | 13.3 | 26.6 | **39.9** | breeding only |

Speeds in blocks per second. For comparison: walking 4.3, sprinting 5.6, vanilla horses 4.7–14.2, a Fast Minecart 30.
Only well-bred horses at tier 4–5 beat the minecart. Donkeys and mules are slower to begin with (a vanilla donkey
is 7.4), so they top out around 21. **Skeleton horses are always at top speed**: the best possible bloodline at
tier 5, about 40 blocks/sec, with no training. Zombie horses can't breed, so they stop at tier 3.

## Feeding (tiers 1–3)

Right-click a **tamed** horse with the feed for its next tier. Each one counts towards the tier; the last one levels
it up (sound and sparkles). Feeding the wrong feed tells you which one it needs.

| Feed | Recipe (shapeless) | Makes |
|---|---|---|
| Swift Feed | 3 wheat, 2 sugar, 1 apple | 4 |
| Golden Feed | 2 golden carrots, 1 Swift Feed, 1 sugar | 2 |
| Racing Feed | 1 golden apple, 2 Golden Feed, 1 rabbit's foot, 1 sugar | 1 |

## Breeding (tiers 4–5)

Breed horses the vanilla way. The foal takes the better parent's tier. If both parents are on the same tier, there's
a 30% chance the foal is born one tier higher. So: feed two horses to tier 3, breed them for a tier 4 foal, raise
two tier 4 horses and breed them for tier 5. Vanilla speed inheritance still applies, so breed fast horses together.

## Seeing a horse's stats

- **Look at a horse** (up to 16 blocks away, not through walls): a panel beside the crosshair shows its top speed,
  speed tier, jump height, health and what its next tier needs. Works on wild horses too, so you can pick a good one
  to tame.
- **Riding:** the same panel sits in the top-left corner, with the speed you're going right now.
- **Inventory:** the panel is beside the horse's inventory screen.

Jump height is at full charge, in blocks (vanilla horses jump about 1.1 to 5.3).

## Commands

- `/horse` while riding: its tier, top speed, and what it needs next.
- `/horse settier <0-5>` (operators) while riding.
