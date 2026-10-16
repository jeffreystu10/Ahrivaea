scoreboard players enable @a gateway.go
# no portal messages for the first 5 seconds after joining (players often arrive standing in a portal)
execute as @a unless score @s gateway.wait matches -2147483648.. run scoreboard players set @s gateway.wait 100
execute as @a[scores={gateway.left=1..}] run scoreboard players set @s gateway.wait 100
scoreboard players reset @a[scores={gateway.left=1..}] gateway.left
scoreboard players remove @a[scores={gateway.wait=1..}] gateway.wait 1
# who is standing in a portal
tag @a remove gateway.inside
execute as @e[type=marker,tag=gateway.portal] at @s run function gateway:portal
execute as @a[tag=gateway.inside,tag=!gateway.prompted,scores={gateway.wait=..0}] at @s run function gateway:prompt
tag @a[tag=gateway.prompted,tag=!gateway.inside] remove gateway.prompted
execute as @a[scores={gateway.go=1..}] at @s run function gateway:go
# still here 2 seconds after travelling: the transfer didn't go through
scoreboard players remove @a[scores={gateway.leaving=1..}] gateway.leaving 1
execute as @a[scores={gateway.leaving=0}] run tellraw @s {"text":"The portal couldn't send you. An operator should check that server.properties has function-permission-level=3 here and accepts-transfers=true on the other server.","color":"red"}
scoreboard players reset @a[scores={gateway.leaving=0}] gateway.leaving
