# Makes the spot you stand on a portal, for an opening you built yourself (3 wide, 4 tall, you standing in the
# bottom middle). Face through the opening, the way players will walk.
execute if entity @s[y_rotation=-45..44.9] align xyz positioned ~0.5 ~ ~0.5 run summon minecraft:marker ~ ~ ~ {Tags:["gateway.portal","gateway.x"]}
execute if entity @s[y_rotation=135..180] align xyz positioned ~0.5 ~ ~0.5 run summon minecraft:marker ~ ~ ~ {Tags:["gateway.portal","gateway.x"]}
execute if entity @s[y_rotation=-180..-135.1] align xyz positioned ~0.5 ~ ~0.5 run summon minecraft:marker ~ ~ ~ {Tags:["gateway.portal","gateway.x"]}
execute if entity @s[y_rotation=45..134.9] align xyz positioned ~0.5 ~ ~0.5 run summon minecraft:marker ~ ~ ~ {Tags:["gateway.portal","gateway.z"]}
execute if entity @s[y_rotation=-135..-45.1] align xyz positioned ~0.5 ~ ~0.5 run summon minecraft:marker ~ ~ ~ {Tags:["gateway.portal","gateway.z"]}
tellraw @s {"text":"This spot is now a portal.","color":"green"}
