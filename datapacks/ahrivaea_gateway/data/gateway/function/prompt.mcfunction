# a player just stepped into a portal: say where it goes and offer to go
tag @s add gateway.prompted
playsound minecraft:block.portal.trigger master @s ~ ~ ~ 0.35 1.6
execute if data storage gateway:config {host:""} run return run tellraw @s {"text":"This portal isn't connected yet. An operator sets it up with /function gateway:setup.","color":"gray","italic":true}
tellraw @s ["",{"text":"✦ This portal leads to ","color":"gray"},{"nbt":"label","storage":"gateway:config","interpret":true,"color":"light_purple","bold":true},{"text":". ","color":"gray"},{"nbt":"notetext","storage":"gateway:config","interpret":true,"color":"gray"}]
tellraw @s ["",{"text":"   [ Travel there ]","color":"green","bold":true,"click_event":{"action":"run_command","command":"/trigger gateway.go"},"hover_event":{"action":"show_text","value":"Leave this server and join the other one. Your inventory stays here and will be waiting when you come back."}}]
