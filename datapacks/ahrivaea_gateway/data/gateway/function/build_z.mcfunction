# a portal across z (you walk through it along x), centred here
fill ~ ~-1 ~-2 ~ ~4 ~2 minecraft:crying_obsidian
fill ~ ~ ~-1 ~ ~3 ~1 minecraft:light[level=13]
setblock ~ ~-1 ~-2 minecraft:chiseled_polished_blackstone
setblock ~ ~-1 ~2 minecraft:chiseled_polished_blackstone
setblock ~ ~4 ~-2 minecraft:chiseled_polished_blackstone
setblock ~ ~4 ~2 minecraft:chiseled_polished_blackstone
setblock ~ ~4 ~ minecraft:amethyst_block
summon minecraft:marker ~ ~ ~ {Tags:["gateway.portal","gateway.z"]}
