execute as @e[type=ahrivaea:creature] run say AHRIVAEA_TEST creature present
execute unless entity @e[type=ahrivaea:creature] run say AHRIVAEA_TEST no creature found
ahrivaea selftest
challenge top coal
execute unless score #gen ahv_test matches 1 run challenge generate 0
execute unless score #gen ahv_test matches 1 run challenge generate 1
scoreboard players set #gen ahv_test 1
challenge list
execute if block 45 101 45 minecraft:chest unless block 45 126 40 minecraft:air run say AHRIVAEA_TEST tower complete
execute if block 45 101 45 minecraft:chest run say AHRIVAEA_TEST chest ok
say AHRIVAEA_TEST check done
