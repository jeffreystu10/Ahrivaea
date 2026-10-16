forceload add 0 0
forceload add 40 40 60 60
execute if block 45 101 45 minecraft:chest run say AHRIVAEA_TEST chest present at load
scoreboard objectives add ahv_test dummy
execute store result score #gw ahv_test run scoreboard objectives list
execute unless score #done ahv_test matches 1 positioned 8 0 8 positioned over motion_blocking run ahrivaea spawn dragon fire 30
execute unless score #done ahv_test matches 1 positioned 10 0 8 positioned over motion_blocking run ahrivaea spawn dragon arcane 12
execute unless score #done ahv_test matches 1 positioned 40 100 40 run challenge place coal
execute unless score #done ahv_test matches 1 run challenge credit coal Alice 300
execute unless score #done ahv_test matches 1 run challenge credit coal Bob 450
execute unless score #done ahv_test matches 1 run challenge credit coal Alice 300
execute unless score #done ahv_test matches 1 run say AHRIVAEA_TEST summoned creatures
execute unless score #done ahv_test matches 1 run function gateway:setup {name:"Test world",host:"localhost",port:25566,note:"Test note."}
execute unless score #done ahv_test matches 1 positioned 50 140 50 run function gateway:build_x
scoreboard players set #done ahv_test 1
schedule function ahv_test:check 60t
