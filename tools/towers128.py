"""The 128 stronghold tower challenges (one per stronghold, nearest first), with rewards that grow with
distance from spawn. Writes Tower_Challenges.xlsx (the editable master list). tools/sheet_to_data.py turns the
sheet into the mod's data files."""
# Ring sizes of the 128 strongholds, nearest first
RINGS = [3, 6, 10, 15, 21, 28, 36, 9]

# (id, name, unit, {item: worth}, goal)  -- nearest towers first, roughly easiest to hardest
T = [
 # ring 1
 ("coal", "The Ember Spire", "coal", {"coal": 1, "coal_block": 9}, 1000),
 ("logs", "The Timber Watch", "logs", {"#logs": 1}, 1500),
 ("cobblestone", "The Quarry Keep", "cobblestone", {"cobblestone": 1, "cobbled_deepslate": 1}, 5000),
 # ring 2
 ("wheat", "The Harvest Keep", "wheat", {"wheat": 1, "hay_block": 9}, 2000),
 ("iron", "The Iron Bastion", "iron ingots", {"iron_ingot": 1, "iron_block": 9}, 500),
 ("copper", "The Verdigris Tower", "copper ingots", {"copper_ingot": 1, "copper_block": 9}, 2000),
 ("bones", "The Ossuary", "bones", {"bone": 1, "bone_block": 3}, 800),
 ("string", "The Weaver's Spire", "string", {"string": 1}, 600),
 ("flesh", "The Hollow Spire", "rotten flesh", {"rotten_flesh": 1}, 1000),
 # ring 3
 ("sand", "The Sandglass Tower", "sand", {"#sand": 1}, 4000),
 ("clay", "The Clayworks", "clay", {"clay_ball": 1, "clay": 4}, 1500),
 ("potato", "The Root Cellar", "potatoes", {"potato": 1, "baked_potato": 1}, 1500),
 ("carrot", "The Orange Lantern", "carrots", {"carrot": 1}, 1500),
 ("beetroot", "The Crimson Garden", "beetroots", {"beetroot": 1}, 1200),
 ("pumpkin", "The Pumpkin Watch", "pumpkins", {"pumpkin": 1, "carved_pumpkin": 1}, 400),
 ("melon", "The Melon Keep", "melon slices", {"melon_slice": 1, "melon": 9}, 2000),
 ("flint", "The Flint Needle", "flint", {"flint": 1}, 600),
 ("gravel", "The Gravel Heap", "gravel", {"gravel": 1}, 3000),
 ("torch", "The Torchbearer's Tower", "torches", {"torch": 1}, 1500),
 # ring 4
 ("redstone", "The Redstone Beacon", "redstone", {"redstone": 1, "redstone_block": 9}, 1500),
 ("lapis", "The Azure Sanctum", "lapis lazuli", {"lapis_lazuli": 1, "lapis_block": 9}, 1000),
 ("gold", "The Gilded Crown", "gold ingots", {"gold_ingot": 1, "gold_block": 9}, 400),
 ("planks", "The Carpenter's Stair", "planks", {"#planks": 1}, 4000),
 ("feather", "The Featherfall Spire", "feathers", {"feather": 1}, 500),
 ("leather", "The Tannery", "leather", {"leather": 1}, 400),
 ("eggs", "The Hen House", "eggs", {"#eggs": 1}, 600),
 ("sugarcane", "The Sugar Spire", "sugar cane", {"sugar_cane": 1}, 1500),
 ("paper", "The Paper Archive", "paper", {"paper": 1, "book": 3}, 1500),
 ("glass", "The Glasswright's Tower", "glass", {"glass": 1}, 2000),
 ("bricks", "The Brickyard", "bricks", {"brick": 1, "bricks": 4}, 3000),
 ("kelp", "The Kelp Tower", "kelp", {"kelp": 1, "dried_kelp": 1, "dried_kelp_block": 9}, 3000),
 ("spidereye", "The Spider's Den", "spider eyes", {"spider_eye": 1}, 300),
 ("gunpowder", "The Powder Keep", "gunpowder", {"gunpowder": 1, "tnt": 5}, 600),
 ("arrows", "The Archer's Spire", "arrows", {"#arrows": 1}, 1500),
 # ring 5
 ("berries", "The Bramble Tower", "sweet berries", {"sweet_berries": 1}, 1500),
 ("apples", "The Orchard Spire", "apples", {"apple": 1}, 300),
 ("bread", "The Bakery", "bread", {"bread": 1}, 800),
 ("cocoa", "The Cocoa Tower", "cocoa beans", {"cocoa_beans": 1}, 1000),
 ("bamboo", "The Bamboo Pagoda", "bamboo", {"bamboo": 1, "bamboo_block": 9}, 4000),
 ("cactus", "The Cactus Needle", "cactus", {"cactus": 1}, 1000),
 ("mushrooms", "The Toadstool Spire", "mushrooms", {"#mushrooms": 1}, 600),
 ("saplings", "The Sapling Grove", "saplings", {"#saplings": 1}, 800),
 ("flowers", "The Blossom Tower", "flowers", {"#small_flowers": 1}, 1000),
 ("leaves", "The Leafwatch", "leaves", {"#leaves": 1}, 3000),
 ("wool", "The Woolspire", "wool", {"#wool": 1}, 800),
 ("ink", "The Ink Tower", "ink sacs", {"ink_sac": 1, "glow_ink_sac": 2}, 400),
 ("slime", "The Slime Spire", "slimeballs", {"slime_ball": 1, "slime_block": 9}, 300),
 ("honey", "The Honey Spire", "honey bottles", {"honey_bottle": 1, "honey_block": 4}, 200),
 ("honeycomb", "The Comb Tower", "honeycomb", {"honeycomb": 1, "honeycomb_block": 4}, 400),
 ("fish", "The Fisher's Lighthouse", "fish", {"#fishes": 1}, 800),
 ("prismarine", "The Tide Tower", "prismarine shards", {"prismarine_shard": 1, "prismarine": 4}, 800),
 ("ice", "The Ice Spire", "ice", {"ice": 1, "packed_ice": 9, "blue_ice": 81}, 2000),
 ("snow", "The Snowcrest", "snowballs", {"snowball": 1, "snow_block": 4}, 4000),
 ("granite", "The Granite Spire", "granite", {"granite": 1, "polished_granite": 1}, 3000),
 ("diorite", "The Pale Spire", "diorite", {"diorite": 1, "polished_diorite": 1}, 3000),
 # ring 6
 ("andesite", "The Grey Spire", "andesite", {"andesite": 1, "polished_andesite": 1}, 3000),
 ("deepslate", "The Deepslate Delve", "deepslate", {"cobbled_deepslate": 1, "deepslate": 1}, 6000),
 ("tuff", "The Tuff Tower", "tuff", {"tuff": 1}, 3000),
 ("calcite", "The Calcite Spire", "calcite", {"calcite": 1}, 1500),
 ("dripstone", "The Dripstone Spire", "dripstone", {"pointed_dripstone": 1, "dripstone_block": 4}, 800),
 ("amethyst", "The Amethyst Spire", "amethyst shards", {"amethyst_shard": 1, "amethyst_block": 4}, 800),
 ("mud", "The Mudworks", "mud", {"#mud": 1, "packed_mud": 1, "mud_bricks": 1}, 2000),
 ("moss", "The Mossy Keep", "moss", {"#moss_blocks": 1}, 1000),
 ("glowberries", "The Glow Tower", "glow berries", {"glow_berries": 1}, 600),
 ("vines", "The Vine Tower", "vines", {"vine": 1}, 800),
 ("lilypads", "The Lily Keep", "lily pads", {"lily_pad": 1}, 400),
 ("seeds", "The Seed Vault", "seeds", {"wheat_seeds": 1, "beetroot_seeds": 1, "melon_seeds": 1, "pumpkin_seeds": 1}, 2000),
 ("stew", "The Stew Kitchen", "bowls of stew", {"mushroom_stew": 1, "beetroot_soup": 1, "rabbit_stew": 2}, 100),
 ("beef", "The Butcher's Spire", "cooked beef", {"cooked_beef": 1}, 500),
 ("pork", "The Smokehouse", "cooked porkchops", {"cooked_porkchop": 1}, 500),
 ("chicken", "The Roost", "cooked chicken", {"cooked_chicken": 1}, 500),
 ("mutton", "The Shepherd's Hearth", "cooked mutton", {"cooked_mutton": 1}, 500),
 ("rabbit", "The Rabbit Warren", "rabbit hide", {"rabbit_hide": 1, "rabbit_foot": 5}, 300),
 ("pie", "The Pie Tower", "pumpkin pies", {"pumpkin_pie": 1}, 200),
 ("cookies", "The Cookie Jar", "cookies", {"cookie": 1}, 1000),
 ("cake", "The Cake Keep", "cakes", {"cake": 1}, 50),
 ("goldencarrot", "The Golden Orchard", "golden carrots", {"golden_carrot": 1}, 300),
 ("lanterns", "The Lantern Spire", "lanterns", {"#lanterns": 1}, 300),
 ("bookshelves", "The Librarian's Tower", "bookshelves", {"bookshelf": 1}, 150),
 ("rails", "The Rail Spire", "rails", {"#rails": 1}, 1000),
 ("ladders", "The Ladder Tower", "ladders", {"ladder": 1}, 1000),
 ("chests", "The Chest Vault", "chests", {"chest": 1, "barrel": 1, "trapped_chest": 1}, 300),
 ("candles", "The Candlelight Spire", "candles", {"#candles": 1}, 300),
 # ring 7
 ("dyes", "The Dyer's Tower", "dyes", {"#dyes": 1}, 800),
 ("terracotta", "The Terracotta Spire", "terracotta", {"#terracotta": 1}, 2000),
 ("glazed", "The Glazed Gallery", "glazed terracotta", {"#glazed_terracotta": 1}, 300),
 ("concrete", "The Concrete Citadel", "concrete", {"#concrete": 1}, 2000),
 ("beds", "The Dreamer's Tower", "beds", {"#beds": 1}, 64),
 ("boats", "The Boatwright's Spire", "boats", {"#boats": 1, "#chest_boats": 2}, 100),
 ("carpets", "The Carpet Hall", "carpets", {"#wool_carpets": 1}, 500),
 ("banners", "The Banner Hall", "banners", {"#banners": 1}, 64),
 ("frames", "The Frame Gallery", "item frames", {"item_frame": 1, "glow_item_frame": 2}, 200),
 ("paintings", "The Painter's Tower", "paintings", {"painting": 1}, 100),
 ("pots", "The Flower Pot Tower", "flower pots", {"flower_pot": 1}, 300),
 ("campfires", "The Campfire Spire", "campfires", {"campfire": 1, "soul_campfire": 1}, 100),
 ("anvils", "The Anvil Forge", "anvils", {"anvil": 1}, 20),
 ("bars", "The Iron Cage", "iron bars", {"#bars": 1}, 640),
 ("hoppers", "The Hopper Works", "hoppers", {"hopper": 1}, 64),
 ("pistons", "The Piston Spire", "pistons", {"piston": 1, "sticky_piston": 2}, 200),
 ("observers", "The Observer's Tower", "observers", {"observer": 1}, 100),
 ("clocks", "The Clockwork Spire", "clocks and compasses", {"clock": 1, "compass": 1}, 50),
 ("maps", "The Map Room", "maps", {"map": 1, "filled_map": 1}, 64),
 ("pearls", "The Ender Watch", "ender pearls", {"ender_pearl": 1, "ender_eye": 2}, 200),
 ("blaze", "The Blaze Spire", "blaze powder", {"blaze_rod": 2, "blaze_powder": 1}, 400),
 ("ghast", "The Weeping Spire", "ghast tears", {"ghast_tear": 1}, 32),
 ("magmacream", "The Magma Tower", "magma cream", {"magma_cream": 1}, 200),
 ("phantom", "The Phantom Spire", "phantom membranes", {"phantom_membrane": 1}, 100),
 ("netherrack", "The Netherrack Spire", "netherrack", {"netherrack": 1}, 6000),
 ("basalt", "The Basalt Spire", "basalt", {"basalt": 1, "polished_basalt": 1, "smooth_basalt": 1}, 3000),
 ("blackstone", "The Blackstone Keep", "blackstone", {"blackstone": 1, "polished_blackstone": 1, "polished_blackstone_bricks": 1}, 3000),
 ("soulsand", "The Soul Spire", "soul sand", {"soul_sand": 1, "soul_soil": 1}, 1500),
 ("glowstone", "The Glowstone Spire", "glowstone dust", {"glowstone_dust": 1, "glowstone": 4}, 1000),
 ("quartz", "The Quartz Spire", "nether quartz", {"quartz": 1, "quartz_block": 4}, 1000),
 ("wart", "The Wart Tower", "nether wart", {"nether_wart": 1, "nether_wart_block": 9}, 1000),
 ("crimson", "The Crimson Spire", "crimson stems", {"#crimson_stems": 1}, 1000),
 ("warped", "The Warped Spire", "warped stems", {"#warped_stems": 1}, 1000),
 ("shroomlight", "The Shroomlight Tower", "shroomlights", {"shroomlight": 1}, 200),
 ("netherbrick", "The Nether Brick Keep", "nether bricks", {"nether_brick": 1, "nether_bricks": 4}, 3000),
 ("obsidian", "The Obsidian Spire", "obsidian", {"obsidian": 1, "crying_obsidian": 2}, 500),
 # ring 8 (farthest)
 ("emerald", "The Emerald Exchange", "emeralds", {"emerald": 1, "emerald_block": 9}, 500),
 ("diamond", "The Diamond Throne", "diamonds", {"diamond": 1, "diamond_block": 9}, 100),
 ("chorus", "The Chorus Spire", "chorus fruit", {"chorus_fruit": 1, "popped_chorus_fruit": 1}, 1000),
 ("endstone", "The End Stone Spire", "end stone", {"end_stone": 1, "end_stone_bricks": 1}, 5000),
 ("purpur", "The Purpur Spire", "purpur", {"purpur_block": 1, "purpur_pillar": 1}, 2000),
 ("shulker", "The Shulker Vault", "shulker shells", {"shulker_shell": 1}, 64),
 ("wither", "The Wither Crown", "wither skeleton skulls", {"wither_skeleton_skull": 1}, 12),
 ("echo", "The Echo Spire", "echo shards", {"echo_shard": 1}, 24),
 ("netherite", "The Ancient Forge", "netherite scrap", {"netherite_scrap": 1, "ancient_debris": 1, "netherite_ingot": 4}, 64),
]

# First-place prizes, nearest towers first: each different, and better farther out
FIRST = [
 # ring 1
 [("diamond", 8), ("golden_apple", 2)],
 [("diamond_axe", 1), ("emerald", 16)],
 [("diamond_pickaxe", 1), ("experience_bottle", 16)],
 # ring 2
 [("golden_carrot", 64), ("enchanted_golden_apple", 1)],
 [("anvil", 1), ("diamond", 12)],
 [("spyglass", 1), ("lightning_rod", 4), ("diamond", 10)],
 [("saddle", 1), ("name_tag", 2)],
 [("bundle", 2), ("diamond", 10)],
 [("totem_of_undying", 1)],
 # ring 3
 [("brush", 1), ("diamond", 12)],
 [("decorated_pot", 4), ("diamond", 12)],
 [("diamond_hoe", 1), ("bone_meal", 64)],
 [("golden_carrot", 48), ("diamond_horse_armor", 1)],
 [("diamond", 14), ("emerald", 12)],
 [("jack_o_lantern", 16), ("diamond", 12)],
 [("glistering_melon_slice", 16), ("diamond", 12)],
 [("crossbow", 1), ("spectral_arrow", 64)],
 [("diamond_shovel", 1), ("emerald", 24)],
 [("lantern", 16), ("diamond", 14)],
 # ring 4
 [("beacon", 1)],
 [("enchanting_table", 1), ("experience_bottle", 64)],
 [("enchanted_golden_apple", 3)],
 [("crafter", 2), ("diamond", 16)],
 [("elytra", 1)],
 [("saddle", 2), ("lead", 8), ("diamond", 12)],
 [("sniffer_egg", 1), ("diamond", 10)],
 [("emerald_block", 4)],
 [("lectern", 1), ("experience_bottle", 48)],
 [("tinted_glass", 16), ("spyglass", 1), ("diamond", 12)],
 [("diamond", 20)],
 [("conduit", 1)],
 [("fermented_spider_eye", 32), ("diamond", 14)],
 [("tnt", 32), ("diamond", 16)],
 [("crossbow", 1), ("arrow", 64), ("diamond", 16)],
 # ring 5
 [("diamond_block", 2)],
 [("enchanted_golden_apple", 4)],
 [("cake", 4), ("diamond", 18)],
 [("diamond", 16), ("emerald", 32)],
 [("scaffolding", 64), ("diamond", 20)],
 [("diamond", 18), ("experience_bottle", 32)],
 [("red_mushroom_block", 32), ("diamond", 20)],
 [("diamond_axe", 1), ("diamond", 18)],
 [("music_disc_pigstep", 1), ("diamond", 16)],
 [("shears", 1), ("diamond", 20)],
 [("loom", 1), ("diamond", 20)],
 [("glow_item_frame", 16), ("diamond", 18)],
 [("sticky_piston", 16), ("diamond", 18)],
 [("beehive", 1), ("diamond", 20)],
 [("bee_nest", 1), ("diamond", 18)],
 [("fishing_rod", 1), ("nautilus_shell", 4), ("diamond", 16)],
 [("heart_of_the_sea", 1), ("diamond", 12)],
 [("blue_ice", 64), ("diamond", 16)],
 [("powder_snow_bucket", 2), ("diamond", 20)],
 [("diamond", 24)],
 [("emerald_block", 6)],
 # ring 6
 [("netherite_ingot", 1), ("diamond", 8)],
 [("netherite_upgrade_smithing_template", 1), ("diamond", 16)],
 [("trial_key", 4), ("diamond", 20)],
 [("diamond_block", 3)],
 [("music_disc_otherside", 1), ("diamond", 20)],
 [("amethyst_block", 32), ("spyglass", 2), ("diamond", 16)],
 [("totem_of_undying", 2)],
 [("axolotl_bucket", 1), ("diamond", 20)],
 [("glow_ink_sac", 32), ("diamond", 20)],
 [("diamond", 26)],
 [("lily_pad", 32), ("diamond", 22)],
 [("sniffer_egg", 2)],
 [("golden_apple", 16)],
 [("diamond_sword", 1), ("diamond", 20)],
 [("diamond_chestplate", 1), ("diamond", 12)],
 [("diamond_helmet", 1), ("diamond", 18)],
 [("diamond_leggings", 1), ("diamond", 14)],
 [("diamond_boots", 1), ("diamond", 20)],
 [("cake", 8), ("emerald_block", 6)],
 [("cookie", 64), ("diamond_block", 3)],
 [("enchanted_golden_apple", 5)],
 [("golden_carrot", 64), ("diamond_block", 3)],
 [("respawn_anchor", 1), ("glowstone", 16), ("diamond", 16)],
 [("enchanting_table", 1), ("bookshelf", 15), ("experience_bottle", 64)],
 [("powered_rail", 64), ("diamond", 20)],
 [("ender_chest", 2), ("diamond", 16)],
 [("shulker_box", 1), ("diamond", 16)],
 [("experience_bottle", 64), ("diamond", 24)],
 # ring 7
 [("netherite_ingot", 2)],
 [("sentry_armor_trim_smithing_template", 1), ("diamond_block", 3)],
 [("wild_armor_trim_smithing_template", 1), ("diamond_block", 3)],
 [("coast_armor_trim_smithing_template", 1), ("diamond_block", 3)],
 [("music_disc_relic", 1), ("diamond_block", 3)],
 [("trident", 1), ("diamond", 16)],
 [("shulker_box", 2), ("diamond", 20)],
 [("dune_armor_trim_smithing_template", 1), ("diamond_block", 4)],
 [("recovery_compass", 1), ("diamond_block", 3)],
 [("music_disc_5", 1), ("diamond_block", 3)],
 [("lodestone", 2), ("diamond_block", 3)],
 [("enchanted_golden_apple", 6)],
 [("netherite_upgrade_smithing_template", 2), ("diamond", 20)],
 [("ominous_trial_key", 2), ("diamond_block", 3)],
 [("crafter", 8), ("diamond_block", 4)],
 [("heavy_core", 1), ("breeze_rod", 8)],
 [("observer", 32), ("diamond_block", 4)],
 [("diamond_block", 6)],
 [("netherite_scrap", 4), ("diamond_block", 3)],
 [("mace", 1)],
 [("elytra", 1), ("firework_rocket", 64)],
 [("dragon_breath", 16), ("diamond_block", 4)],
 [("totem_of_undying", 3)],
 [("phantom_membrane", 32), ("elytra", 1)],
 [("netherite_ingot", 3)],
 [("rib_armor_trim_smithing_template", 1), ("diamond_block", 4)],
 [("snout_armor_trim_smithing_template", 1), ("diamond_block", 4)],
 [("music_disc_pigstep", 1), ("netherite_ingot", 2)],
 [("beacon", 2)],
 [("nether_star", 1), ("diamond_block", 2)],
 [("respawn_anchor", 2), ("diamond_block", 4)],
 [("enchanted_golden_apple", 8)],
 [("silence_armor_trim_smithing_template", 1), ("netherite_ingot", 1)],
 [("eye_armor_trim_smithing_template", 1), ("diamond_block", 5)],
 [("netherite_ingot", 4)],
 [("conduit", 1), ("heart_of_the_sea", 2), ("diamond_block", 4)],
 # ring 8
 [("nether_star", 2), ("emerald_block", 16)],
 [("netherite_ingot", 6), ("elytra", 1)],
 [("elytra", 2), ("shulker_box", 2)],
 [("dragon_head", 1), ("netherite_ingot", 4)],
 [("shulker_box", 4), ("netherite_ingot", 4)],
 [("shulker_box", 8), ("totem_of_undying", 4)],
 [("beacon", 4), ("netherite_ingot", 4)],
 [("music_disc_otherside", 1), ("ward_armor_trim_smithing_template", 1), ("netherite_ingot", 6)],
 [("netherite_block", 2), ("nether_star", 2)],
]

# second/third place and everyone-who-helped, by ring
SECOND = ["diamond 6", "diamond 8", "diamond 10", "diamond 12", "diamond 16", "diamond_block 2", "netherite_ingot 1", "netherite_ingot 2"]
THIRD = ["diamond 3", "diamond 4", "diamond 5", "diamond 6", "diamond 8", "diamond 12", "diamond_block 2", "netherite_ingot 1"]
EVERYONE = ["emerald 4", "emerald 6", "emerald 8", "emerald 10", "emerald 12", "diamond 2", "diamond 3", "diamond 5"]


if __name__ == "__main__":
    import json, os, sys
    from openpyxl import Workbook
    from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
    assert len(T) == 128 and len(FIRST) == 128 and sum(RINGS) == 128
    ring_of = [r + 1 for r, n in enumerate(RINGS) for _ in range(n)]
    F = 'Arial'; f = lambda **k: Font(name=F, **k)
    thin = Side(style='thin', color='BFBFBF'); B = Border(left=thin, right=thin, top=thin, bottom=thin)
    hdr = PatternFill('solid', fgColor='262626')
    ring_fill = ['FFFFFF', 'F2F2F2']
    wb = Workbook(); ws = wb.active; ws.title = 'Towers'
    ws['A1'] = 'Ahrivaea Tower Challenges (all 128 strongholds)'; ws['A1'].font = f(bold=True, size=14)
    ws['A2'] = ('Tower 1 is the stronghold nearest the centre of the world; rings go outward (ring 1 = 3 towers about 1,300-2,800 blocks out, '
                'ring 8 = 9 towers about 21,000 blocks out). Farther towers ask for rarer things and give more. Edit anything in blue, then send it back.')
    ws['A2'].font = f(italic=True, size=9, color='595959')
    cols = ['Tower #', 'Ring', 'ID', 'Challenge name', 'What to bring (item = how much it counts)', 'Unit word', 'Goal', 'Stages',
            '1st place reward', '2nd place reward', '3rd place reward', 'Everyone who helped']
    for j, h in enumerate(cols):
        c = ws.cell(4, 1 + j, h); c.font = f(bold=True, color='FFFFFF'); c.fill = hdr; c.border = B
        c.alignment = Alignment(wrap_text=True, vertical='center')
    stack = lambda lst: ', '.join(f"{n} {i}" for i, n in lst)
    for k, (cid, name, unit, items, goal) in enumerate(T):
        ring = ring_of[k]
        row = [k + 1, ring, cid, name, ', '.join(f"{i} = {w}" for i, w in items.items()), unit, goal, 4,
               stack(FIRST[k]), SECOND[ring - 1].replace(' ', ' x', 0) , THIRD[ring - 1], EVERYONE[ring - 1]]
        for j, v in enumerate(row):
            if j >= 9 and isinstance(v, str):
                i, n = v.rsplit(' ', 1); v = f"{n} {i}"
            c = ws.cell(5 + k, 1 + j, v)
            c.font = f(color='0000FF' if j >= 3 else '000000'); c.border = B
            c.alignment = Alignment(wrap_text=True, vertical='top')
            c.fill = PatternFill('solid', fgColor=ring_fill[ring % 2])
    for col, w in zip('ABCDEFGHIJKL', [7, 5, 12, 24, 40, 18, 7, 7, 44, 18, 18, 18]):
        ws.column_dimensions[col].width = w
    ws.row_dimensions[4].height = 32; ws.freeze_panes = 'E5'; ws.auto_filter.ref = f"A4:L{4 + len(T)}"
    h = wb.create_sheet('How to fill it in')
    notes = ['How to fill it in', '',
             'What to bring: item ids from the game, each with how much it counts. Example: coal = 1, coal_block = 9',
             '   #name means a whole group: #logs = 1 accepts every kind of log, #wool every colour of wool, #fishes every fish.',
             'Unit word: what the progress bar says, e.g. "coal" shows 520/1000 coal.',
             'Goal: total needed to fully rebuild the tower. Stages: how many steps it rebuilds in (4 = every quarter).',
             'Rewards: amount and item id, separated by commas. Example: 16 diamond, 1 enchanted_golden_apple. Blank = no reward.',
             'ID: a short name for the challenge (letters, no spaces). Keep it unique; it names the file in the mod.',
             '', 'Rings: strongholds sit in 8 rings around the centre of the world: 3, 6, 10, 15, 21, 28, 36 and 9 towers.',
             'Towers in the same ring are about the same distance from spawn, so they get similar rewards.',
             '', 'Every item name was checked against Minecraft 26.3. If you add one and aren\'t sure of its id, just write it in words.']
    for i, t_ in enumerate(notes):
        c = h.cell(1 + i, 1, t_); c.font = f(bold=(i == 0), size=14 if i == 0 else 10)
    h.column_dimensions['A'].width = 120
    out = sys.argv[1] if len(sys.argv) > 1 else 'Tower_Challenges.xlsx'
    wb.save(out); print('wrote', out)
