# /function gateway:setup {name:"Ahrivaea",host:"play.example.net",port:25566,note:"You need the Ahrivaea modpack."}
$data modify storage gateway:config name set value "$(name)"
$data modify storage gateway:config host set value "$(host)"
$data modify storage gateway:config port set value $(port)
$data modify storage gateway:config note set value "$(note)"
tellraw @s ["",{"text":"Portals on this server now lead to ","color":"green"},{"nbt":"name","storage":"gateway:config","color":"light_purple"},{"text":" (","color":"gray"},{"nbt":"host","storage":"gateway:config","color":"gray"},{"text":":","color":"gray"},{"nbt":"port","storage":"gateway:config","color":"gray"},{"text":").","color":"gray"}]
tellraw @s {"text":"Both servers need accepts-transfers=true and function-permission-level=3 in server.properties.","color":"gray","italic":true}
