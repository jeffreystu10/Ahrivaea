# run as a portal's marker, at the bottom middle of the portal's 3 x 4 opening
particle minecraft:portal ~ ~1.5 ~ 0.7 1.1 0.7 0.4 6
particle minecraft:reverse_portal ~ ~1.5 ~ 0.6 1 0.6 0.02 2
execute if entity @s[tag=gateway.x] positioned ~-1.5 ~ ~-0.5 run tag @a[dx=2,dy=3,dz=0] add gateway.inside
execute if entity @s[tag=gateway.z] positioned ~-0.5 ~ ~-1.5 run tag @a[dx=0,dy=3,dz=2] add gateway.inside
