# a player clicked [ Travel there ]
scoreboard players reset @s gateway.go
execute unless entity @s[tag=gateway.inside] run return run tellraw @s {"text":"Step into the portal first.","color":"red"}
execute if data storage gateway:config {host:""} run return run tellraw @s {"text":"This portal isn't connected yet. An operator sets it up with /function gateway:setup.","color":"red"}
title @s times 5 40 10
title @s title {"text":"✦","color":"light_purple"}
title @s subtitle [{"text":"Travelling to ","color":"gray"},{"nbt":"label","storage":"gateway:config","interpret":true,"color":"light_purple"}]
playsound minecraft:block.portal.travel master @s ~ ~ ~ 0.3 1.2
scoreboard players set @s gateway.leaving 40
function gateway:transfer with storage gateway:config
