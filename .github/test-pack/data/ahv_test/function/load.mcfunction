forceload add 0 0
forceload add 40 40 60 60
execute if block 45 101 45 minecraft:chest run say AHRIVAEA_TEST chest present at load
scoreboard objectives add ahv_test dummy
execute unless score #done ahv_test matches 1 positioned 8 120 8 run ahrivaea spawn dragon fire 30
execute unless score #done ahv_test matches 1 positioned 10 120 8 run ahrivaea spawn dragon arcane 12
execute unless score #done ahv_test matches 1 positioned 40 100 40 run challenge place tower
execute unless score #done ahv_test matches 1 run challenge credit tower Alice 300
execute unless score #done ahv_test matches 1 run challenge credit tower Bob 450
execute unless score #done ahv_test matches 1 run challenge credit tower Alice 300
execute unless score #done ahv_test matches 1 run say AHRIVAEA_TEST summoned creatures
scoreboard players set #done ahv_test 1
schedule function ahv_test:check 60t
