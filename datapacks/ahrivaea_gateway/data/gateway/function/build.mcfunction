# Builds a portal 3 blocks in front of you, facing you: a crying obsidian frame with a 3 x 4 glowing opening.
execute if entity @s[y_rotation=-45..44.9] positioned ^ ^ ^3 align xyz positioned ~0.5 ~ ~0.5 run function gateway:build_x
execute if entity @s[y_rotation=135..180] positioned ^ ^ ^3 align xyz positioned ~0.5 ~ ~0.5 run function gateway:build_x
execute if entity @s[y_rotation=-180..-135.1] positioned ^ ^ ^3 align xyz positioned ~0.5 ~ ~0.5 run function gateway:build_x
execute if entity @s[y_rotation=45..134.9] positioned ^ ^ ^3 align xyz positioned ~0.5 ~ ~0.5 run function gateway:build_z
execute if entity @s[y_rotation=-135..-45.1] positioned ^ ^ ^3 align xyz positioned ~0.5 ~ ~0.5 run function gateway:build_z
tellraw @s {"text":"Portal built. Walk into it to try it.","color":"green"}
