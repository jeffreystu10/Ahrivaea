"""Writes the starter challenge files, tower order and biome palettes. Edit the JSON files directly afterwards;
this script is only the starting point."""
import json
D='src/main/resources/data/ahrivaea/'
def R(item,count=1): return {"item":"minecraft:"+item,"count":count}
C=[
 ("coal","The Ember Spire","coal",{"minecraft:coal":1,"minecraft:coal_block":9},1000,
  [[R("diamond",16),R("enchanted_golden_apple")],[R("diamond",8)],[R("diamond",4)],[R("emerald",8)]]),
 ("iron","The Iron Bastion","iron ingots",{"minecraft:iron_ingot":1,"minecraft:iron_block":9},500,
  [[R("netherite_ingot",2),R("anvil")],[R("diamond",10)],[R("diamond",5)],[R("iron_block",2)]]),
 ("wheat","The Harvest Keep","wheat",{"minecraft:wheat":1,"minecraft:hay_block":9},2000,
  [[R("golden_carrot",64),R("enchanted_golden_apple")],[R("golden_carrot",32)],[R("golden_carrot",16)],[R("cake")]]),
 ("logs","The Timber Watch","logs",{"#minecraft:logs":1},1500,
  [[R("diamond_axe"),R("emerald",32)],[R("emerald",16)],[R("emerald",8)],[R("oak_sapling",8)]]),
 ("copper","The Verdigris Tower","copper ingots",{"minecraft:copper_ingot":1,"minecraft:copper_block":9},2000,
  [[R("trident")],[R("diamond",8)],[R("diamond",4)],[R("lightning_rod",2)]]),
 ("bones","The Ossuary","bones",{"minecraft:bone":1,"minecraft:bone_block":3},800,
  [[R("totem_of_undying")],[R("diamond",8)],[R("diamond",4)],[R("bone_meal",32)]]),
 ("redstone","The Redstone Beacon","redstone",{"minecraft:redstone":1,"minecraft:redstone_block":9},1500,
  [[R("beacon")],[R("diamond",8)],[R("diamond",4)],[R("redstone_block",4)]]),
 ("lapis","The Azure Sanctum","lapis lazuli",{"minecraft:lapis_lazuli":1,"minecraft:lapis_block":9},1000,
  [[R("enchanting_table"),R("experience_bottle",64)],[R("experience_bottle",32)],[R("experience_bottle",16)],[R("bookshelf",4)]]),
 ("string","The Weaver's Spire","string",{"minecraft:string":1,"#minecraft:wool":4},600,
  [[R("elytra")],[R("diamond",8)],[R("diamond",4)],[R("white_wool",16)]]),
 ("gold","The Gilded Crown","gold ingots",{"minecraft:gold_ingot":1,"minecraft:gold_block":9},400,
  [[R("enchanted_golden_apple",3)],[R("enchanted_golden_apple")],[R("golden_apple",4)],[R("golden_carrot",16)]]),
 ("flesh","The Hollow Spire","rotten flesh",{"minecraft:rotten_flesh":1},1000,
  [[R("netherite_ingot")],[R("diamond",6)],[R("diamond",3)],[R("emerald",4)]]),
 ("diamond","The Diamond Throne","diamonds",{"minecraft:diamond":1,"minecraft:diamond_block":9},100,
  [[R("netherite_ingot",4),R("elytra")],[R("netherite_ingot",2)],[R("netherite_ingot")],[R("diamond",2)]]),
]
for cid,name,unit,items,goal,rw in C:
  rewards=[{"rank":i+1,"items":r} for i,r in enumerate(rw[:3])]+[{"rank":0,"items":rw[3]}]
  json.dump({"name":name,"unit":unit,"items":items,"goal":goal,"stages":4,"ruined":"ahrivaea:tower/ruined","finished":"ahrivaea:tower/finished","chest":[5,1,5],"rewards":rewards},
            open(D+f'challenge/{cid}.json','w'),indent=2)
json.dump({"towers":[c[0] for c in C]},open(D+'challenge_order.json','w'),indent=2)
M=lambda d:{("minecraft:"+k):("minecraft:"+v) for k,v in d.items()}
P={
 "default":(0,[],{},"stone_bricks"),
 "desert":(10,["minecraft:desert","#minecraft:is_beach"],{"stone_bricks":"cut_sandstone","cracked_stone_bricks":"sandstone","mossy_stone_bricks":"smooth_sandstone","chiseled_stone_bricks":"chiseled_sandstone","cobblestone":"sandstone","mossy_cobblestone":"smooth_sandstone","cobblestone_slab":"sandstone_slab","stone_brick_wall":"sandstone_wall","stone_brick_stairs":"sandstone_stairs","stone_brick_slab":"sandstone_slab","polished_andesite":"smooth_sandstone","spruce_planks":"birch_planks","gravel":"sand"},"sandstone"),
 "badlands":(10,["#minecraft:is_badlands"],{"stone_bricks":"cut_red_sandstone","cracked_stone_bricks":"red_sandstone","mossy_stone_bricks":"terracotta","chiseled_stone_bricks":"chiseled_red_sandstone","cobblestone":"red_sandstone","mossy_cobblestone":"orange_terracotta","cobblestone_slab":"red_sandstone_slab","stone_brick_wall":"red_sandstone_wall","stone_brick_stairs":"red_sandstone_stairs","stone_brick_slab":"red_sandstone_slab","polished_andesite":"smooth_red_sandstone","spruce_planks":"acacia_planks","gravel":"red_sand"},"red_sandstone"),
 "frozen":(10,["minecraft:snowy_plains","minecraft:ice_spikes","minecraft:snowy_taiga","minecraft:snowy_slopes","minecraft:frozen_peaks","minecraft:jagged_peaks","minecraft:grove","minecraft:frozen_river","minecraft:snowy_beach","minecraft:frozen_ocean","minecraft:deep_frozen_ocean"],{"stone_bricks":"polished_diorite","cracked_stone_bricks":"diorite","mossy_stone_bricks":"packed_ice","chiseled_stone_bricks":"quartz_pillar","cobblestone":"diorite","mossy_cobblestone":"snow_block","cobblestone_slab":"polished_diorite_slab","stone_brick_wall":"diorite_wall","stone_brick_stairs":"polished_diorite_stairs","stone_brick_slab":"polished_diorite_slab","polished_andesite":"packed_ice","gravel":"snow_block"},"polished_diorite"),
 "jungle":(10,["#minecraft:is_jungle"],{"stone_bricks":"mossy_stone_bricks","cracked_stone_bricks":"mossy_stone_bricks","cobblestone":"mossy_cobblestone","cobblestone_slab":"mossy_cobblestone_slab","stone_brick_wall":"mossy_stone_brick_wall","stone_brick_stairs":"mossy_stone_brick_stairs","stone_brick_slab":"mossy_stone_brick_slab","polished_andesite":"moss_block","spruce_planks":"jungle_planks","gravel":"moss_block"},"mossy_cobblestone"),
 "swamp":(10,["minecraft:swamp","minecraft:mangrove_swamp"],{"stone_bricks":"mud_bricks","cracked_stone_bricks":"packed_mud","chiseled_stone_bricks":"mud_bricks","cobblestone":"packed_mud","cobblestone_slab":"mud_brick_slab","stone_brick_wall":"mud_brick_wall","stone_brick_stairs":"mud_brick_stairs","stone_brick_slab":"mud_brick_slab","polished_andesite":"packed_mud","spruce_planks":"mangrove_planks","gravel":"mud"},"packed_mud"),
 "dark":(10,["minecraft:dark_forest","minecraft:pale_garden","minecraft:deep_dark"],{"stone_bricks":"deepslate_bricks","cracked_stone_bricks":"cracked_deepslate_bricks","mossy_stone_bricks":"deepslate_tiles","chiseled_stone_bricks":"chiseled_deepslate","cobblestone":"cobbled_deepslate","mossy_cobblestone":"cobbled_deepslate","cobblestone_slab":"cobbled_deepslate_slab","stone_brick_wall":"deepslate_brick_wall","stone_brick_stairs":"deepslate_brick_stairs","stone_brick_slab":"deepslate_brick_slab","polished_andesite":"polished_deepslate","spruce_planks":"dark_oak_planks"},"cobbled_deepslate"),
 "blossom":(10,["minecraft:cherry_grove","minecraft:meadow","minecraft:flower_forest","minecraft:sunflower_plains"],{"stone_bricks":"quartz_bricks","cracked_stone_bricks":"calcite","mossy_stone_bricks":"calcite","chiseled_stone_bricks":"chiseled_quartz_block","cobblestone":"calcite","mossy_cobblestone":"moss_block","cobblestone_slab":"smooth_quartz_slab","stone_brick_wall":"cherry_fence","stone_brick_stairs":"quartz_stairs","stone_brick_slab":"smooth_quartz_slab","polished_andesite":"smooth_quartz","spruce_planks":"cherry_planks"},"calcite"),
 "savanna":(10,["#minecraft:is_savanna"],{"stone_bricks":"polished_granite","cracked_stone_bricks":"granite","mossy_stone_bricks":"granite","chiseled_stone_bricks":"polished_granite","cobblestone":"granite","mossy_cobblestone":"coarse_dirt","cobblestone_slab":"polished_granite_slab","stone_brick_wall":"granite_wall","stone_brick_stairs":"polished_granite_stairs","stone_brick_slab":"polished_granite_slab","polished_andesite":"smooth_red_sandstone","spruce_planks":"acacia_planks"},"granite"),
 "ocean":(10,["#minecraft:is_ocean","#minecraft:is_river"],{"stone_bricks":"prismarine_bricks","cracked_stone_bricks":"prismarine","mossy_stone_bricks":"prismarine","chiseled_stone_bricks":"dark_prismarine","cobblestone":"prismarine","mossy_cobblestone":"prismarine","cobblestone_slab":"prismarine_slab","stone_brick_wall":"prismarine_wall","stone_brick_stairs":"prismarine_brick_stairs","stone_brick_slab":"prismarine_brick_slab","polished_andesite":"dark_prismarine","gravel":"sand"},"prismarine"),
 "mountain":(10,["minecraft:stony_peaks","minecraft:windswept_hills","minecraft:windswept_gravelly_hills","minecraft:windswept_forest","minecraft:stony_shore","minecraft:dripstone_caves"],{"stone_bricks":"tuff_bricks","cracked_stone_bricks":"tuff","mossy_stone_bricks":"polished_tuff","chiseled_stone_bricks":"chiseled_tuff_bricks","cobblestone":"tuff","mossy_cobblestone":"cobblestone","cobblestone_slab":"tuff_slab","stone_brick_wall":"tuff_brick_wall","stone_brick_stairs":"tuff_brick_stairs","stone_brick_slab":"tuff_brick_slab","polished_andesite":"polished_tuff"},"tuff"),
 "taiga":(5,["#minecraft:is_taiga"],{"polished_andesite":"podzol"},"cobblestone"),
 "mushroom":(10,["minecraft:mushroom_fields"],{"stone_bricks":"mushroom_stem","cracked_stone_bricks":"mushroom_stem","mossy_stone_bricks":"red_mushroom_block","chiseled_stone_bricks":"brown_mushroom_block","cobblestone":"mycelium","mossy_cobblestone":"red_mushroom_block","polished_andesite":"mycelium","stone_brick_wall":"red_mushroom_block","spruce_planks":"brown_mushroom_block"},"mushroom_stem"),
}
for pid,(prio,biomes,blocks,found) in P.items():
  d={"priority":prio}
  if biomes: d["biomes"]=biomes
  d["blocks"]=M(blocks); d["foundation"]="minecraft:"+found
  json.dump(d,open(D+f'tower_palette/{pid}.json','w'),indent=2)
print(len(C),"challenges",len(P),"palettes")
