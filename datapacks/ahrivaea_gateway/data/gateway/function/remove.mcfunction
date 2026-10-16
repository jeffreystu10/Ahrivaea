# Stops the nearest portal (within 6 blocks) from working. The blocks stay; break them yourself if you want.
execute unless entity @e[type=marker,tag=gateway.portal,distance=..6] run return run tellraw @s {"text":"No portal within 6 blocks.","color":"red"}
kill @e[type=marker,tag=gateway.portal,distance=..6,limit=1,sort=nearest]
tellraw @s {"text":"Portal removed.","color":"green"}
