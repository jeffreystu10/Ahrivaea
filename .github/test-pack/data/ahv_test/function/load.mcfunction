forceload add 0 0
scoreboard objectives add ahv_test dummy
execute unless score #done ahv_test matches 1 run summon ahrivaea:creature 8 200 8 {NoAI:1b}
execute unless score #done ahv_test matches 1 run say AHRIVAEA_TEST summoned a creature
scoreboard players set #done ahv_test 1
schedule function ahv_test:check 60t
