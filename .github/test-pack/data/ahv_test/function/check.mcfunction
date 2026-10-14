execute as @e[type=ahrivaea:creature] run say AHRIVAEA_TEST creature present
execute unless entity @e[type=ahrivaea:creature] run say AHRIVAEA_TEST no creature found
ahrivaea selftest
challenge top tower
execute if block 45 101 45 minecraft:chest if block 45 126 40 minecraft:stone_brick_wall run say AHRIVAEA_TEST tower complete
say AHRIVAEA_TEST check done
