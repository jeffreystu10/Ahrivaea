# Ahrivaea Gateway: portals that send players to another server (the main world <-> Ahrivaea).
# Set it up with /function gateway:setup {...}; see datapacks/README.md.
scoreboard objectives add gateway.go trigger
scoreboard objectives add gateway.left minecraft.custom:minecraft.leave_game
scoreboard objectives add gateway.wait dummy
scoreboard objectives add gateway.leaving dummy
execute unless data storage gateway:config host run data merge storage gateway:config {name:"the other world",host:"",port:25565,note:"",label:{text:"the other world"},notetext:{text:""}}
