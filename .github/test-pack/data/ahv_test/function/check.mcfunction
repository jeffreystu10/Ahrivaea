execute as @e[type=ahrivaea:creature] run say AHRIVAEA_TEST creature present
execute unless entity @e[type=ahrivaea:creature] run say AHRIVAEA_TEST no creature found
ahrivaea selftest
challenge top coal
execute unless score #gen ahv_test matches 1 run challenge generate 0
execute unless score #gen ahv_test matches 1 run challenge generate 1
scoreboard players set #gen ahv_test 1
challenge list
execute if block 45 101 45 ahrivaea:base_core unless block 45 126 40 minecraft:air run say AHRIVAEA_TEST tower complete
execute if block 45 101 45 minecraft:chest run say AHRIVAEA_TEST chest ok
execute if data storage gateway:config {host:"localhost",port:25566} run say AHRIVAEA_TEST gateway config ok
execute if block 48 139 50 minecraft:chiseled_polished_blackstone if block 50 141 50 minecraft:light if block 50 144 50 minecraft:amethyst_block if entity @e[type=marker,tag=gateway.portal,tag=gateway.x,x=50,y=140,z=50,distance=..1] run say AHRIVAEA_TEST gateway portal ok
execute if score #gw ahv_test matches 1.. run say AHRIVAEA_TEST gateway objectives ok
say AHRIVAEA_TEST check done
