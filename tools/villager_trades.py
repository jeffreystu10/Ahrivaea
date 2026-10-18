"""Villager professions and what they sell: every item in the game (tools/data/items.txt, listed by the server),
except creative-only ones, sorted into 17 professions and 5 ranks, with a price in creature XP.

    python3 tools/villager_trades.py [Villager_Trades.xlsx]

Writes src/main/resources/data/ahrivaea/villager_profession/<id>.json, and (if a path is given) a spreadsheet of
every profession, rank, deed and offer for reviewing the balance.

How it decides:
  * profession: the first rule in RULES whose pattern matches the item id (top to bottom, so specific rules first)
  * value: XP for one item, from VALUES (first match), else from the item's rarity
  * rank: its place in the profession's list, cheapest and plainest first: the first fifth Novice ... the last fifth
    Master; but anything worth more than 20 XP is at least Expert and more than 80 XP always Master (RANK_LIMITS)
  * offer: a stack's worth for cheap things (64, 16 or 1, whatever the item stacks to), one of anything worth 1 XP or
    more; the cost is value x count, rounded up, at least 1
Deeds (what earns each rank) are in DEEDS.
"""
import json
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(ROOT, 'tools/data/items.txt')
OUT = os.path.join(ROOT, 'src/main/resources/data/ahrivaea/villager_profession')

RANKS = ['Novice', 'Apprentice', 'Journeyman', 'Expert', 'Master']
# an item worth up to this many XP is sold from this rank on (Master: anything more)
RANK_LIMITS = [0.15, 1.0, 20.0, 80.0]

# ---- what isn't sold ----
CREATIVE_ONLY = re.compile(r'^(air|bedrock|barrier|light|command_block|chain_command_block|repeating_command_block|command_block_minecart|'
                           r'structure_block|structure_void|jigsaw|test_block|test_instance_block|debug_stick|knowledge_book|spawner|'
                           r'trial_spawner|vault|end_portal_frame|budding_amethyst|reinforced_deepslate|infested_.*|petrified_oak_slab|'
                           r'player_head|farmland|dirt_path|frogspawn|chorus_plant|.*_spawn_egg|written_book|filled_map|.*_map|'
                           r'suspicious_stew)$')
# map items need a structure to point at, written books need writing and suspicious stew an effect: none of them
# makes sense as a plain item, so they stay what you find or make. (empty "map" is sold: see the explicit rule)
AHRIVAEA_NOT_SOLD = {'base_core'}  # bases only come from completing towers

# ---- professions: id, name, vanilla outfit, icon, description ----
PROFESSIONS = [
    ('mason', 'Mason', 'mason', 'minecraft:stone_bricks', 'Stone, earth, bricks, terracotta, concrete, glass and ice.'),
    ('carpenter', 'Carpenter', 'toolsmith', 'minecraft:oak_planks', 'Logs, planks and everything made of wood.'),
    ('gardener', 'Gardener', 'shepherd', 'minecraft:poppy', 'Saplings, leaves, flowers, grass, moss and other plants.'),
    ('farmer', 'Farmer', 'farmer', 'minecraft:wheat', 'Crops, seeds, produce, eggs, milk and honey.'),
    ('cook', 'Cook', 'butcher', 'minecraft:cooked_beef', 'Meat and cooked food, cakes, pies and stews.'),
    ('weaver', 'Weaver', 'shepherd', 'minecraft:white_wool', 'Wool, carpets, beds, banners, dyes and string.'),
    ('leatherworker', 'Leatherworker', 'leatherworker', 'minecraft:leather', 'Leather, leather armor, saddles, harnesses and leads.'),
    ('fisherman', 'Fisherman', 'fisherman', 'minecraft:cod', 'Fish, coral, prismarine and treasures of the sea.'),
    ('smith', 'Smith', 'armorer', 'minecraft:iron_ingot', 'Ores, metals, copper, tools, weapons and armor.'),
    ('hunter', 'Hunter', 'fletcher', 'minecraft:bow', 'Bows, arrows and what creatures drop.'),
    ('librarian', 'Librarian', 'librarian', 'minecraft:book', 'Books, paper, bookshelves and enchanted books.'),
    ('alchemist', 'Alchemist', 'cleric', 'minecraft:brewing_stand', 'Potions and everything for brewing them.'),
    ('engineer', 'Engineer', 'toolsmith', 'minecraft:redstone', 'Redstone, rails, minecarts and machines.'),
    ('explorer', 'Explorer', 'cartographer', 'minecraft:compass', 'Maps, compasses, music discs, trims, sherds and keepsakes.'),
    ('netherwright', 'Netherwright', 'weaponsmith', 'minecraft:netherrack', 'Everything from the Nether, netherite included.'),
    ('voidwalker', 'Voidwalker', 'cleric', 'minecraft:ender_pearl', 'Everything from the End: pearls, purpur, shulkers, elytra.'),
    ('jeweler', 'Jeweler', 'nitwit', 'minecraft:diamond', 'Gems, amethyst and the rarest treasures: beacons, totems, nether stars.'),
]

COLORS = r'(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)'
WOODS = r'(oak|spruce|birch|jungle|acacia|cherry|dark_oak|pale_oak|mangrove|poplar|bamboo|crimson|warped)'

# first match wins
RULES = [
    ('gardener', r'^golden_dandelion$'),
    # the End
    ('voidwalker', r'^(end_stone.*|purpur_.*|chorus_.*|popped_chorus_fruit|ender_pearl|ender_eye|ender_chest|shulker_shell|shulker_box|'
                   + COLORS + r'_shulker_box|end_rod|end_crystal|elytra|dragon_.*|purpur_shard)$'),
    # the Nether
    ('netherwright', r'^(netherrack|crimson_nylium|warped_nylium|nether_.*|red_nether_.*|cracked_nether_bricks|chiseled_nether_bricks|'
                     r'basalt|polished_basalt|smooth_basalt|blackstone.*|polished_blackstone.*|chiseled_polished_blackstone|'
                     r'cracked_polished_blackstone_bricks|gilded_blackstone|soul_sand|soul_soil|soul_torch|soul_lantern|soul_campfire|'
                     r'magma_block|shroomlight|crimson_fungus|warped_fungus|crimson_roots|warped_roots|nether_sprouts|weeping_vines|'
                     r'twisting_vines|nether_wart_block|warped_wart_block|quartz|quartz_.*|smooth_quartz.*|chiseled_quartz_block|'
                     r'ancient_debris|netherite_.*|blaze_rod|blaze_powder|ghast_tear|dried_ghast|respawn_anchor|crying_obsidian|obsidian|'
                     r'wither_skeleton_skull|wither_rose|fire_charge|piglin_head|piglin_banner_pattern|warped_fungus_on_a_stick|'
                     r'lodestone|magma_cream)$'),
    # rare treasures
    ('jeweler', r'^(diamond|emerald|diamond_block|emerald_block|amethyst_.*|small_amethyst_bud|medium_amethyst_bud|large_amethyst_bud|'
                r'beacon|nether_star|totem_of_undying|echo_shard|golden_apple|enchanted_golden_apple|trial_key|ominous_trial_key|heavy_core|'
                r'mace|sculk|sculk_.*|calibrated_sculk_sensor|lapis_lazuli|lapis_block|ivory)$'),
    # brewing
    ('alchemist', r'^(potion|splash_potion|lingering_potion|glass_bottle|brewing_stand|cauldron|fermented_spider_eye|glistering_melon_slice|'
                  r'golden_carrot|glowstone|glowstone_dust|experience_bottle|ominous_bottle|rabbit_foot|honey_bottle|turtle_scute|'
                  r'turtle_helmet|breeze_rod|wind_charge|sulfur|potent_sulfur)$'),
    # the sea
    ('fisherman', r'^(cod|salmon|tropical_fish|pufferfish|cooked_cod|cooked_salmon|.*_fish_bucket|cod_bucket|salmon_bucket|pufferfish_bucket|'
                  r'tadpole_bucket|axolotl_bucket|sulfur_cube_bucket|fishing_rod|prismarine.*|dark_prismarine.*|sea_lantern|kelp|dried_kelp|'
                  r'dried_kelp_block|seagrass|sea_pickle|.*coral.*|nautilus_shell|heart_of_the_sea|conduit|sponge|wet_sponge|ink_sac|'
                  r'glow_ink_sac|turtle_egg|trident|.*_nautilus_armor|lily_pad|water_bucket)$'),
    # redstone and rails
    ('engineer', r'^(redstone|redstone_torch|redstone_block|redstone_lamp|repeater|comparator|piston|sticky_piston|observer|hopper|dispenser|'
                 r'dropper|lever|daylight_detector|sculk_sensor|tripwire_hook|trapped_chest|tnt|note_block|target|crafter|'
                 r'.*rail|minecart|.*_minecart|stone_button|polished_blackstone_button|stone_pressure_plate|polished_blackstone_pressure_plate|'
                 r'light_weighted_pressure_plate|heavy_weighted_pressure_plate|iron_door|iron_trapdoor|.*lightning_rod|.*copper_bulb|'
                 r'slime_block|honey_block|bell|jukebox|carrot_on_a_stick)$'),
    # exploring and keepsakes
    ('explorer', r'^(map|compass|recovery_compass|spyglass|clock|.*_banner_pattern|.*_armor_trim_smithing_template|.*_pottery_sherd|'
                 r'decorated_pot|brush|music_disc_.*|disc_fragment_5|suspicious_sand|suspicious_gravel|painting|item_frame|glow_item_frame|'
                 r'armor_stand|goat_horn|cartography_table|sniffer_egg|creaking_heart|resin_.*|chiseled_resin_bricks|firework_rocket|'
                 r'firework_star|bundle|' + COLORS + r'_bundle)$'),
    # metals, tools, weapons, armor
    ('smith', r'^(.*_ore|raw_iron|raw_copper|raw_gold|raw_.*_block|coal|charcoal|coal_block|iron_.*|gold_.*|golden_.*|copper_.*|.*_copper.*|'
              r'.*copper|chiseled_copper|cut_copper.*|chainmail_.*|wooden_(sword|shovel|pickaxe|axe|hoe|spear)|stone_(sword|shovel|pickaxe|axe|hoe|spear)|'
              r'diamond_(sword|shovel|pickaxe|axe|hoe|spear|helmet|chestplate|leggings|boots|horse_armor)|anvil|chipped_anvil|damaged_anvil|'
              r'shield|shears|flint_and_steel|bucket|lava_bucket|powder_snow_bucket|lantern|smithing_table|blast_furnace|grindstone|'
              r'stonecutter|furnace|stellar_.*|.*_horse_armor)$'),
    # hunting and creature drops
    ('hunter', r'^(bow|crossbow|arrow|spectral_arrow|tipped_arrow|flint|feather|bone|bone_block|gunpowder|slime_ball|spider_eye|rotten_flesh|'
               r'phantom_membrane|skeleton_skull|zombie_head|creeper_head|fletching_table|armadillo_scute|wolf_armor|cobweb|raw_rat)$'),
    # leather
    ('leatherworker', r'^(leather|leather_.*|rabbit_hide|saddle|.*harness|lead|name_tag)$'),
    # books
    ('librarian', r'^(book|paper|writable_book|enchanted_book|bookshelf|chiseled_bookshelf|.*_shelf|lectern|enchanting_table|candle|'
                  + COLORS + r'_candle)$'),
    # wool and dyes
    ('weaver', r'^(' + COLORS + r'_(wool|carpet|bed|banner|dye|cushion|wool_slab|wool_stairs)|string|loom|straw_bed)$'),
    # cooking
    ('cook', r'^(beef|cooked_beef|porkchop|cooked_porkchop|chicken|cooked_chicken|mutton|cooked_mutton|rabbit|cooked_rabbit|bread|cookie|'
             r'cake|pumpkin_pie|mushroom_stew|rabbit_stew|beetroot_soup|baked_potato|poisonous_potato|smoker|campfire|bowl|banana)$'),
    # farming
    ('farmer', r'^(wheat|wheat_seeds|pumpkin|carved_pumpkin|jack_o_lantern|pumpkin_seeds|melon|melon_slice|melon_seeds|carrot|potato|'
               r'beetroot|beetroot_seeds|sugar_cane|sugar|cocoa_beans|sweet_berries|glow_berries|egg|blue_egg|brown_egg|milk_bucket|'
               r'honeycomb|honeycomb_block|bee_nest|beehive|hay_block|composter|bone_meal|torchflower_seeds|pitcher_pod|apple|'
               r'.*_feed|.*_treat|cactus|cactus_flower)$'),
    # wood
    ('carpenter', r'^(' + WOODS + r'_(log|wood|planks|stairs|slab|fence|fence_gate|door|trapdoor|button|pressure_plate|sign|hanging_sign|'
                  r'boat|chest_boat|stem|hyphae)|stripped_.*|bamboo_block|bamboo_mosaic.*|bamboo_raft|bamboo_chest_raft|crimson_stem|warped_stem|'
                  r'crimson_hyphae|warped_hyphae|stick|chest|barrel|crafting_table|ladder|scaffolding|mangrove_roots|muddy_mangrove_roots|'
                  r'torch|copper_torch|.*_shelf|vessel|mining_post|creature_dynamo)$'),
    # plants
    ('gardener', r'^(.*_sapling|mangrove_propagule|.*_leaves|azalea|flowering_azalea|short_grass|tall_grass|fern|large_fern|bush|red_shrub|'
                 r'dead_bush|firefly_bush|.*dry.*grass|dandelion|golden_dandelion|open_eyeblossom|closed_eyeblossom|poppy|blue_orchid|allium|'
                 r'azure_bluet|.*_tulip|oxeye_daisy|cornflower|lily_of_the_valley|torchflower|pitcher_plant|spore_blossom|sunflower|lilac|'
                 r'rose_bush|peony|brown_mushroom|red_mushroom|shelf_mushroom|.*mushroom_block|mushroom_stem|vine|glow_lichen|pink_petals|'
                 r'wildflowers|leaf_litter|moss_block|moss_carpet|pale_moss_block|pale_moss_carpet|pale_hanging_moss|hanging_roots|big_dripleaf|small_dripleaf|bamboo|flower_pot|grass_block|podzol|mycelium|'
                 r'rooted_dirt)$'),
    # everything else that's a block of stone, earth, glass or ice
    ('mason', r'.*'),
]

# ---- value of one item in XP (first match), else from rarity ----
VALUES = [
    (0.04, r'^(dirt|coarse_dirt|cobblestone|cobbled_deepslate|gravel|sand|red_sand|netherrack|snow_block|snow|stone|deepslate|tuff|'
           r'andesite|diorite|granite|end_stone|calcite|mud|clay|blackstone|basalt|soul_sand|soul_soil|dripstone_block|cinnabar|sulfur)$'),
    (0.08, r'^(.*_log|.*_wood|.*_stem|.*_hyphae|stripped_.*|.*_planks|stick|.*_leaves|short_grass|tall_grass|fern|large_fern|kelp|seagrass|'
           r'bamboo|sugar_cane|wheat_seeds|pumpkin_seeds|melon_seeds|beetroot_seeds|vine|glow_lichen|.*_sapling|mangrove_propagule|'
           r'rotten_flesh|bone|string|ice|leaf_litter|bush|dead_bush|.*dry.*grass|cactus|lily_pad|wheat|carrot|potato|beetroot|'
           r'melon_slice|apple|feather|smooth_stone|polished_.*|.*_slab|.*_stairs|.*_wall|cobweb|torch|.*_carpet)$'),
    (0.15, r'^(.*_wool|.*_terracotta|terracotta|.*_concrete|.*_concrete_powder|glass|.*_stained_glass|.*glass_pane|.*bricks|bricks|'
           r'.*_tile.*|.*_fence|.*_fence_gate|.*_button|.*_pressure_plate|ladder|bread|cod|salmon|egg|.*_egg|sugar|flint|leather|paper|'
           r'clay_ball|brick|.*_dye|dandelion|poppy|.*_tulip|.*flower.*|.*_petals|.*orchid|allium|azure_bluet|oxeye_daisy|cornflower|'
           r'lily_of_the_valley|.*mushroom.*|coal|charcoal|sweet_berries|glow_berries|cocoa_beans|bowl|slime_ball|gunpowder|spider_eye|'
           r'arrow|chest|barrel|crafting_table|packed_mud|.*sandstone.*|prismarine_shard|ink_sac|cooked_.*|baked_potato|.*_moss.*|moss.*)$'),
    (0.5, r'^(raw_copper|copper_ingot|copper_nugget|iron_nugget|gold_nugget|redstone|lapis_lazuli|quartz|glowstone_dust|amethyst_shard|'
          r'prismarine_crystals|honeycomb|nether_wart|hay_block|pumpkin|melon|.*_door|.*_trapdoor|.*_sign|.*_hanging_sign|.*_bed|cake|'
          r'pumpkin_pie|cookie|.*_stew|beetroot_soup|book|.*_candle|candle|.*_banner|bone_meal|glass_bottle|bucket|.*_shelf|'
          r'packed_ice|magma_block|glowstone|obsidian|honey_bottle|feather|phantom_membrane|rabbit_hide|rabbit_foot|leather_.*|'
          r'copper_.*|.*_copper.*|cut_copper.*|chiseled_copper|lantern|soul_lantern|campfire|soul_campfire|wooden_.*|stone_(sword|shovel|pickaxe|axe|hoe|spear))$'),
    (1.5, r'^(raw_iron|iron_ingot|raw_gold|gold_ingot|.*_boat|.*_chest_boat|.*_raft|blue_ice|ghast_tear|blaze_powder|magma_cream|'
          r'fermented_spider_eye|glistering_melon_slice|golden_carrot|bookshelf|redstone_torch|repeater|lever|tripwire_hook|'
          r'rail|minecart|.*_bundle|bundle|item_frame|painting|flower_pot|armor_stand|lead|fishing_rod|bow|shears|flint_and_steel|'
          r'compass|clock|map|crossbow|spyglass|.*_horse_armor|saddle|.*harness|name_tag|shield|potion|splash_potion|lingering_potion|'
          r'experience_bottle|blaze_rod|nether_brick|.*_froglight|sea_lantern|sponge|wet_sponge|tnt|.*_bucket|iron_bars|iron_chain|'
          r'ender_pearl|ender_eye|shulker_shell|.*_shulker_box|shulker_box|end_rod|chorus_fruit|popped_chorus_fruit|chorus_flower|'
          r'turtle_scute|armadillo_scute|breeze_rod|wind_charge|spectral_arrow|tipped_arrow|firework_rocket|firework_star|goat_horn|'
          r'.*_head|.*_skull|sniffer_egg|creaking_heart|lodestone|crying_obsidian|respawn_anchor|.*_banner_pattern)$'),
    # rarities and specific treasures (XP each)
    (6, r'^(ender_pearl|blaze_rod|ghast_tear|ender_eye)$'),
    (12, r'^(emerald|shulker_shell|nautilus_shell|echo_shard|phantom_membrane|.*_pottery_sherd|golden_apple|trial_key|disc_fragment_5)$'),
    (25, r'^(diamond|ender_chest|enchanting_table|anvil|brewing_stand|jukebox|end_crystal|wolf_armor|turtle_helmet|ominous_bottle|'
         r'recovery_compass|music_disc_.*|.*_armor_trim_smithing_template|wither_skeleton_skull|piglin_head|zombie_head|skeleton_skull|'
         r'creeper_head|ancient_debris|netherite_scrap|.*_nautilus_armor|mace|ominous_trial_key|sculk_catalyst|sculk_shrieker|'
         r'calibrated_sculk_sensor|sculk_sensor)$'),
    (300, r'^(netherite_ingot|netherite_.*|totem_of_undying|heart_of_the_sea|trident|conduit|heavy_core|netherite_upgrade_smithing_template|'
          r'enchanted_golden_apple|dragon_head|stellar_.*)$'),
    (1500, r'^(elytra|beacon|nether_star|dragon_egg)$'),
]
# exact overrides (XP each), after the rules above
EXACT = {
    'ender_pearl': 6, 'ender_eye': 9, 'blaze_rod': 5, 'blaze_powder': 2.5, 'ghast_tear': 6, 'diamond': 25, 'emerald': 12,
    'diamond_block': 225, 'emerald_block': 108, 'iron_block': 13.5, 'gold_block': 18, 'netherite_block': 2700, 'coal_block': 1.35,
    'redstone_block': 4.5, 'lapis_block': 4.5, 'copper_block': 4.5, 'raw_iron_block': 13.5, 'raw_gold_block': 18, 'raw_copper_block': 4.5,
    'amethyst_block': 2, 'netherite_ingot': 300, 'netherite_scrap': 70, 'ancient_debris': 70, 'elytra': 2000, 'beacon': 2500,
    'nether_star': 2400, 'dragon_egg': 5000, 'dragon_head': 500, 'dragon_breath': 20, 'end_crystal': 40, 'totem_of_undying': 400,
    'enchanted_golden_apple': 600, 'heart_of_the_sea': 300, 'conduit': 600, 'trident': 500, 'heavy_core': 500, 'mace': 1200,
    'trial_key': 15, 'ominous_trial_key': 60, 'netherite_upgrade_smithing_template': 250, 'golden_apple': 20, 'shulker_box': 30,
    'echo_shard': 15, 'nautilus_shell': 12, 'ender_chest': 20, 'enchanting_table': 60, 'anvil': 45, 'chipped_anvil': 30,
    'damaged_anvil': 15, 'experience_bottle': 3, 'enchanted_book': 40, 'saddle': 8, 'name_tag': 10, 'lead': 2,
    'diamond_horse_armor': 120, 'iron_horse_armor': 10, 'golden_horse_armor': 15, 'leather_horse_armor': 4,
    'turtle_helmet': 30, 'sponge': 6, 'wet_sponge': 6, 'sea_lantern': 3, 'shulker_shell': 15, 'respawn_anchor': 40, 'lodestone': 30,
    'crying_obsidian': 3, 'obsidian': 1, 'tnt': 3, 'cake': 2, 'golden_carrot': 3, 'glistering_melon_slice': 3, 'base_core': 0,
    'vessel': 3, 'mining_post': 6, 'creature_dynamo': 10, 'banana': 0.3, 'ivory': 4, 'purpur_shard': 1, 'raw_rat': 0.2,
    'swift_treat': 2, 'golden_treat': 8, 'racing_treat': 25, 'swift_feed': 2, 'golden_feed': 8, 'racing_feed': 25,
    'brewing_stand': 12, 'cauldron': 10, 'hopper': 8, 'observer': 3, 'piston': 3, 'sticky_piston': 4, 'comparator': 3,
    'dispenser': 2.5, 'dropper': 1.5, 'daylight_detector': 2, 'crafter': 10, 'powered_rail': 3, 'detector_rail': 2,
    'activator_rail': 2, 'chest_minecart': 10, 'furnace_minecart': 10, 'tnt_minecart': 12, 'hopper_minecart': 16, 'minecart': 8,
    'bell': 12, 'jukebox': 25, 'note_block': 1.5, 'target': 2, 'lightning_rod': 2, 'redstone_lamp': 3, 'bow': 3, 'crossbow': 5,
    'shield': 6, 'spyglass': 5, 'recovery_compass': 40, 'compass': 5, 'clock': 8, 'map': 4, 'brush': 3, 'conduit': 600,
    'sniffer_egg': 50, 'creaking_heart': 30, 'goat_horn': 20, 'wither_rose': 15, 'wither_skeleton_skull': 60,
    'skeleton_skull': 20, 'zombie_head': 20, 'creeper_head': 30, 'piglin_head': 30, 'stellar_sword': 400,
}
# tools and armor by material: XP each
MATERIAL = {'wooden': 0.6, 'stone': 0.8, 'leather': 3, 'copper': 3, 'golden': 8, 'iron': 8, 'chainmail': 12, 'diamond': 60, 'netherite': 400}
PIECES = {'sword': 1.0, 'shovel': 0.6, 'pickaxe': 1.2, 'axe': 1.2, 'hoe': 0.8, 'spear': 1.0, 'helmet': 2.0, 'chestplate': 3.0,
          'leggings': 2.6, 'boots': 1.6}

# ---- the deeds for each rank (Novice ... Master) ----
def d(type_, of, amount, text):
    return {'type': type_, 'of': of, 'amount': amount, 'text': text}

DEEDS = {
    'mason': [d('mined', '#minecraft:base_stone_overworld', 64, 'Mine 64 stone'),
              d('mined', '#minecraft:base_stone_overworld', 640, 'Mine 640 stone'),
              d('crafted', '#minecraft:stone_bricks', 256, 'Make 256 stone bricks'),
              d('mined', '#minecraft:base_stone_overworld', 4000, 'Mine 4,000 stone'),
              d('mined', '#minecraft:base_stone_overworld', 15000, 'Mine 15,000 stone')],
    'carpenter': [d('mined', '#minecraft:logs', 32, 'Chop 32 logs'),
                  d('crafted', '#minecraft:planks', 256, 'Make 256 planks'),
                  d('mined', '#minecraft:logs', 640, 'Chop 640 logs'),
                  d('crafted', 'minecraft:chest', 64, 'Make 64 chests'),
                  d('mined', '#minecraft:logs', 5000, 'Chop 5,000 logs')],
    'gardener': [d('picked_up', '#minecraft:saplings', 16, 'Gather 16 saplings'),
                 d('used', 'minecraft:bone_meal', 64, 'Use 64 bone meal'),
                 d('picked_up', '#minecraft:flowers', 200, 'Gather 200 flowers'),
                 d('picked_up', '#minecraft:saplings', 800, 'Gather 800 saplings'),
                 d('dex_seen', '', 150, 'See 150 creature forms in your dex')],
    'farmer': [d('mined', 'minecraft:wheat', 48, 'Harvest 48 wheat'),
               d('crafted', 'minecraft:bread', 64, 'Bake 64 bread'),
               d('mined', '#minecraft:crops', 1000, 'Harvest 1,000 crops'),
               d('used', 'minecraft:bone_meal', 500, 'Use 500 bone meal'),
               d('mined', '#minecraft:crops', 8000, 'Harvest 8,000 crops')],
    'cook': [d('crafted', 'minecraft:bread', 16, 'Bake 16 bread'),
             d('used', '#c:foods', 100, 'Eat 100 meals'),
             d('crafted', 'minecraft:cake', 8, 'Bake 8 cakes'),
             d('used', '#c:foods', 1000, 'Eat 1,000 meals'),
             d('custom', 'minecraft:interact_with_smoker', 500, 'Use a smoker 500 times')],
    'weaver': [d('picked_up', '#minecraft:wool', 32, 'Gather 32 wool'),
               d('crafted', '#c:dyes', 64, 'Make 64 dyes'),
               d('crafted', '#minecraft:beds', 16, 'Make 16 beds'),
               d('crafted', '#minecraft:banners', 32, 'Make 32 banners'),
               d('picked_up', '#minecraft:wool', 2000, 'Gather 2,000 wool')],
    'leatherworker': [d('picked_up', 'minecraft:leather', 16, 'Gather 16 leather'),
                      d('crafted', 'minecraft:leather_chestplate', 4, 'Make 4 leather tunics'),
                      d('crafted', 'minecraft:item_frame', 32, 'Make 32 item frames'),
                      d('custom', 'ahrivaea:battles_won', 300, 'Win 300 battles'),
                      d('picked_up', 'minecraft:leather', 1000, 'Gather 1,000 leather')],
    'fisherman': [d('custom', 'minecraft:fish_caught', 10, 'Catch 10 fish'),
                  d('custom', 'minecraft:fish_caught', 60, 'Catch 60 fish'),
                  d('custom', 'minecraft:fish_caught', 250, 'Catch 250 fish'),
                  d('custom', 'minecraft:fish_caught', 750, 'Catch 750 fish'),
                  d('custom', 'minecraft:fish_caught', 2000, 'Catch 2,000 fish')],
    'smith': [d('mined', '#minecraft:iron_ores', 16, 'Mine 16 iron ore'),
              d('crafted', 'minecraft:iron_ingot', 192, 'Smelt 192 iron ingots'),
              d('crafted', 'minecraft:anvil', 4, 'Make 4 anvils'),
              d('mined', '#minecraft:diamond_ores', 40, 'Mine 40 diamond ore'),
              d('crafted', 'minecraft:netherite_ingot', 4, 'Make 4 netherite ingots')],
    'hunter': [d('custom', 'ahrivaea:battles_won', 10, 'Win 10 battles'),
               d('crafted', '#minecraft:arrows', 128, 'Make 128 arrows'),
               d('custom', 'ahrivaea:battles_won', 150, 'Win 150 battles'),
               d('custom', 'ahrivaea:creatures_recruited', 15, 'Recruit 15 creatures'),
               d('custom', 'ahrivaea:battles_won', 1500, 'Win 1,500 battles')],
    'librarian': [d('crafted', 'minecraft:book', 16, 'Make 16 books'),
                  d('custom', 'minecraft:enchant_item', 5, 'Enchant 5 items'),
                  d('crafted', 'minecraft:bookshelf', 32, 'Make 32 bookshelves'),
                  d('custom', 'minecraft:enchant_item', 60, 'Enchant 60 items'),
                  d('custom', 'minecraft:enchant_item', 250, 'Enchant 250 items')],
    'alchemist': [d('advancement', 'minecraft:nether/brew_potion', 1, 'Brew a potion'),
                  d('crafted', 'minecraft:blaze_powder', 32, 'Make 32 blaze powder'),
                  d('used', 'minecraft:potion', 50, 'Drink 50 potions'),
                  d('used', 'minecraft:splash_potion', 100, 'Throw 100 splash potions'),
                  d('used', 'minecraft:potion', 600, 'Drink 600 potions')],
    'engineer': [d('crafted', 'minecraft:redstone_torch', 16, 'Make 16 redstone torches'),
                 d('crafted', 'minecraft:repeater', 32, 'Make 32 repeaters'),
                 d('crafted', 'minecraft:piston', 48, 'Make 48 pistons'),
                 d('crafted', 'minecraft:hopper', 64, 'Make 64 hoppers'),
                 d('crafted', 'minecraft:observer', 200, 'Make 200 observers')],
    'explorer': [d('custom', 'minecraft:walk_one_cm', 200000, 'Walk 2 km'),
                 d('dex_seen', '', 40, 'See 40 creature forms in your dex'),
                 d('custom', 'minecraft:walk_one_cm', 5000000, 'Walk 50 km'),
                 d('dex_seen', '', 120, 'See 120 creature forms in your dex'),
                 d('dex_seen', '', 220, 'See 220 creature forms in your dex')],
    'netherwright': [d('advancement', 'minecraft:story/enter_the_nether', 1, 'Enter the Nether'),
                     d('custom', 'ahrivaea:nether_battles_won', 25, 'Win 25 battles in the Nether'),
                     d('mined', 'minecraft:netherrack', 2000, 'Mine 2,000 netherrack'),
                     d('custom', 'ahrivaea:nether_battles_won', 300, 'Win 300 battles in the Nether'),
                     d('advancement', 'minecraft:nether/obtain_ancient_debris', 1, 'Find ancient debris')],
    'voidwalker': [d('advancement', 'minecraft:story/enter_the_end', 1, 'Enter the End'),
                   d('custom', 'ahrivaea:end_battles_won', 25, 'Win 25 battles in the End'),
                   d('advancement', 'minecraft:end/find_end_city', 1, 'Find an End city'),
                   d('custom', 'ahrivaea:end_battles_won', 300, 'Win 300 battles in the End'),
                   d('custom', 'ahrivaea:end_battles_won', 1000, 'Win 1,000 battles in the End')],
    'jeweler': [d('mined', '#c:ores', 64, 'Mine 64 ores'),
                d('mined', '#minecraft:diamond_ores', 10, 'Mine 10 diamond ore'),
                d('dex_caught', '', 40, 'Catch 40 creature forms'),
                d('dex_caught', '', 100, 'Catch 100 creature forms'),
                d('dex_caught', '', 180, 'Catch 180 creature forms')],
}

# potions and enchanted books are sold as specific ones
POTIONS = ['night_vision', 'long_night_vision', 'invisibility', 'long_invisibility', 'leaping', 'strong_leaping', 'fire_resistance',
           'long_fire_resistance', 'swiftness', 'strong_swiftness', 'slowness', 'water_breathing', 'long_water_breathing', 'healing',
           'strong_healing', 'harming', 'poison', 'regeneration', 'strong_regeneration', 'strength', 'strong_strength', 'weakness',
           'slow_falling', 'turtle_master', 'wind_charged', 'weaving', 'oozing', 'infested']
ENCHANTS = [('protection', 4), ('fire_protection', 4), ('feather_falling', 4), ('blast_protection', 4), ('projectile_protection', 4),
            ('respiration', 3), ('aqua_affinity', 1), ('thorns', 3), ('depth_strider', 3), ('frost_walker', 2), ('soul_speed', 3),
            ('swift_sneak', 3), ('sharpness', 5), ('smite', 5), ('bane_of_arthropods', 5), ('knockback', 2), ('fire_aspect', 2),
            ('looting', 3), ('sweeping_edge', 3), ('efficiency', 5), ('silk_touch', 1), ('unbreaking', 3), ('fortune', 3),
            ('power', 5), ('punch', 2), ('flame', 1), ('infinity', 1), ('luck_of_the_sea', 3), ('lure', 3), ('loyalty', 3),
            ('impaling', 5), ('riptide', 3), ('channeling', 1), ('multishot', 1), ('quick_charge', 3), ('piercing', 4),
            ('density', 5), ('breach', 4), ('wind_burst', 3), ('mending', 1), ('lunge', 3)]


def load_items():
    out = []
    for token in open(ITEMS).read().split():
        idf, rarity, stack, flags = token.split('|')
        ns, name = idf.split(':')
        out.append({'id': idf, 'ns': ns, 'name': name, 'rarity': rarity, 'stack': int(stack), 'block': flags[0] == 'B', 'food': flags[1] == 'F'})
    return out


def sold(item):
    if item['ns'] == 'ahrivaea':
        return item['name'] not in AHRIVAEA_NOT_SOLD
    return item['name'] == 'map' or not CREATIVE_ONLY.match(item['name'])


def profession(item):
    for prof, pattern in RULES:
        if re.match(pattern, item['name']):
            return prof
    return 'mason'


def value(item):
    n = item['name']
    if n in EXACT:
        return EXACT[n]
    m = re.match(r'^(wooden|stone|leather|copper|golden|iron|chainmail|diamond|netherite)_(sword|shovel|pickaxe|axe|hoe|spear|helmet|chestplate|leggings|boots)$', n)
    if m:
        return MATERIAL[m.group(1)] * PIECES[m.group(2)]
    for v, pattern in VALUES:
        if re.match(pattern, n):
            return v
    return {'C': 0.3, 'U': 4, 'R': 20, 'E': 60}[item['rarity']]


def rank_of(v):
    for i, limit in enumerate(RANK_LIMITS):
        if v <= limit:
            return i
    return 4


FANCY = ('polished', 'smooth', 'cut', 'chiseled', 'bricks', 'brick', 'tiles', 'tile', 'cracked', 'mossy', 'slab', 'stairs', 'wall',
         'glazed', 'stained', 'waxed', 'exposed', 'weathered', 'oxidized', 'stripped', 'pane', 'fence', 'gate', 'door', 'trapdoor',
         'button', 'plate', 'sign', 'hanging', 'carpet', 'bed', 'banner', 'concrete', 'powder', 'pillar')


def fanciness(name):
    words = name.split('_')
    score = sum(1 for w in words if w in FANCY)
    if re.match(COLORS + '_', name) and not name.startswith('white_'):
        score += 1
    return score


def offer(item, v, extra=None):
    stack = item['stack']
    if v >= 1 or stack == 1:
        count = 1
    elif v >= 0.3:
        count = min(stack, 16)
    else:
        count = min(stack, 64)
    o = {'item': item['id'], 'count': count, 'cost': max(1, math.ceil(v * count))}
    if extra:
        o.update(extra)
    return o


def build():
    items = [i for i in load_items() if sold(i)]
    profs = {p[0]: {'name': p[1], 'outfit': p[2], 'icon': p[3], 'description': p[4], 'order': n, 'ranks': [[] for _ in RANKS]}
             for n, p in enumerate(PROFESSIONS)}
    rows = []
    by_prof = {}
    for it in items:
        if it['name'] in ('potion', 'splash_potion', 'lingering_potion', 'tipped_arrow', 'enchanted_book'):
            continue  # sold as specific potions and books below
        by_prof.setdefault(profession(it), []).append((value(it), it))
    for prof, entries in by_prof.items():
        # cheapest first; among equal prices, plain things before fancier versions of them
        entries.sort(key=lambda e: (e[0], fanciness(e[1]['name']), e[1]['id']))
        for n, (v, it) in enumerate(entries):
            spread = min(4, n * 5 // len(entries))
            r = max(spread, 4 if v > RANK_LIMITS[3] else 3 if v > RANK_LIMITS[2] else 0)
            o = offer(it, v)
            profs[prof]['ranks'][r].append(o)
            rows.append((prof, r, it['id'], o['count'], o['cost'], v, ''))
    # potions: the Alchemist (splash and lingering a rank later, tipped arrows from the Hunter)
    for p in POTIONS:
        strong = p.startswith('strong_') or p.startswith('long_') or p in ('turtle_master', 'wind_charged', 'weaving', 'oozing', 'infested')
        for kind, prof, base_rank, v in (('potion', 'alchemist', 2, 3), ('splash_potion', 'alchemist', 3, 5), ('lingering_potion', 'alchemist', 3, 8),
                                         ('tipped_arrow', 'hunter', 3, 1)):
            r = min(4, base_rank + (1 if strong else 0))
            cost_v = v * (1.5 if strong else 1)
            count = 8 if kind == 'tipped_arrow' else 1
            o = {'item': 'minecraft:' + kind, 'count': count, 'cost': max(1, math.ceil(cost_v * count)), 'potion': 'minecraft:' + p}
            profs[prof]['ranks'][r].append(o)
            rows.append((prof, r, 'minecraft:' + kind, count, o['cost'], cost_v, 'potion: ' + p))
    # enchanted books, each enchantment at its highest level: the Librarian (treasure ones at Master)
    for e, lvl in ENCHANTS:
        treasure = e in ('mending', 'frost_walker', 'soul_speed', 'swift_sneak', 'wind_burst')
        r = 4 if treasure else (3 if lvl >= 4 else 2)
        v = (150 if treasure else 20 + 12 * lvl)
        o = {'item': 'minecraft:enchanted_book', 'count': 1, 'cost': v, 'enchantment': 'minecraft:' + e, 'level': lvl}
        profs['librarian']['ranks'][r].append(o)
        rows.append(('librarian', r, 'minecraft:enchanted_book', 1, v, v, '%s %d' % (e, lvl)))
    return profs, rows, items


def write(profs):
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith('.json'):
            os.remove(os.path.join(OUT, f))
    for pid, p in profs.items():
        data = {'name': p['name'], 'description': p['description'], 'outfit': p['outfit'], 'icon': p['icon'], 'order': p['order'], 'ranks': []}
        for r, name in enumerate(RANKS):
            sells = sorted(p['ranks'][r], key=lambda o: (o['cost'] / o['count'], o['item']))
            data['ranks'].append({'name': name, 'deed': DEEDS[pid][r], 'sells': sells})
        with open(os.path.join(OUT, pid + '.json'), 'w') as fh:
            json.dump(data, fh, indent=1)


def sheet(path, profs, rows):
    from openpyxl import Workbook
    from openpyxl.styles import Font, PatternFill
    wb = Workbook()
    ws = wb.active
    ws.title = 'Professions'
    ws.append(['Profession', 'Outfit', 'What it sells', 'Rank', 'Deed to earn the rank', 'Items unlocked', 'Cheapest (XP)', 'Dearest (XP)'])
    for pid, p in profs.items():
        for r, name in enumerate(RANKS):
            costs = [o['cost'] for o in p['ranks'][r]]
            ws.append([p['name'], p['outfit'], p['description'] if r == 0 else '', name, DEEDS[pid][r]['text'], len(costs),
                       min(costs) if costs else '', max(costs) if costs else ''])
    trades = wb.create_sheet('Trades')
    trades.append(['Profession', 'Rank', 'Item', 'Count', 'Cost (XP)', 'XP per item', 'Notes'])
    for prof, r, item, count, cost, v, note in sorted(rows, key=lambda x: (profs[x[0]]['order'], x[1], x[4] / x[3], x[2])):
        trades.append([profs[prof]['name'], RANKS[r], item.replace('minecraft:', ''), count, cost, round(v, 3), note])
    for s in (ws, trades):
        for c in s[1]:
            c.font = Font(bold=True, color='FFFFFF')
            c.fill = PatternFill('solid', fgColor='4B3B8F')
        s.freeze_panes = 'A2'
        for col in s.columns:
            s.column_dimensions[col[0].column_letter].width = min(60, max(10, max(len(str(c.value or '')) for c in col) + 2))
    trades.auto_filter.ref = trades.dimensions
    wb.save(path)


if __name__ == '__main__':
    profs, rows, items = build()
    write(profs)
    total = sum(len(r) for p in profs.values() for r in p['ranks'])
    print('%d items sold (+ potions and books): %d offers in %d professions' % (len(items), total, len(profs)))
    for pid, p in profs.items():
        print('  %-14s %s' % (p['name'], ' / '.join(str(len(r)) for r in p['ranks'])))
    if len(sys.argv) > 1:
        sheet(sys.argv[1], profs, rows)
        print('wrote', sys.argv[1])
