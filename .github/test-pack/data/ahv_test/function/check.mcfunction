execute as @e[type=ahrivaea:creature] run say AHRIVAEA_TEST creature present
execute unless entity @e[type=ahrivaea:creature] run say AHRIVAEA_TEST no creature found
ahrivaea selftest
challenge top tower
execute if block 45 101 45 minecraft:chest if block 45 126 40 minecraft:stone_brick_wall run say AHRIVAEA_TEST tower complete
execute if block 45 101 45 minecraft:chest run say AHRIVAEA_TEST chest ok
execute if block 45 126 40 minecraft:stone_brick_wall run say AHRIVAEA_TEST wall ok
execute if block 40 101 45 minecraft:stone_bricks run say AHRIVAEA_TEST west wall ok
execute if block 45 100 45 minecraft:polished_andesite run say AHRIVAEA_TEST floor ok
execute if block 45 107 45 minecraft:spruce_planks run say AHRIVAEA_TEST first floor ok
say AHRIVAEA_TEST check done
