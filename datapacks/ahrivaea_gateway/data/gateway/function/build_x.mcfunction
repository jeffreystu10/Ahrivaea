# a portal across x (you walk through it along z), centred here
fill ~-2 ~-1 ~ ~2 ~4 ~ minecraft:crying_obsidian
fill ~-1 ~ ~ ~1 ~3 ~ minecraft:light[level=13]
setblock ~-2 ~-1 ~ minecraft:chiseled_polished_blackstone
setblock ~2 ~-1 ~ minecraft:chiseled_polished_blackstone
setblock ~-2 ~4 ~ minecraft:chiseled_polished_blackstone
setblock ~2 ~4 ~ minecraft:chiseled_polished_blackstone
setblock ~ ~4 ~ minecraft:amethyst_block
summon minecraft:marker ~ ~ ~ {Tags:["gateway.portal","gateway.x"]}
