"""Writes the Woodcutting mod's stonecutter recipes (data/ahrivaea/recipe/woodcutting/).

Logs and wood work in the stonecutter like stone does: one log gives what its 4 planks would make at a crafting
table (rounded down), with no sticks needed, plus its stripped log and wood. Planks give stairs, slabs, buttons and
sticks one at a time. Run again after changing the tables below:  python3 tools/woodcutting_recipes.py [items dir]
(with the path of a Minecraft jar's assets/minecraft/items folder, every item id is checked)."""
import json, os, shutil, sys

OUT = 'src/main/resources/data/ahrivaea/recipe/woodcutting'

# name, log word, wood word, item tag of all its logs
WOODS = [(w, 'log', 'wood', f'{w}_logs') for w in
         ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'poplar']] + \
        [(w, 'stem', 'hyphae', f'{w}_stems') for w in ['crimson', 'warped']]

# what one log (any of the type, stripped or not) makes
FROM_LOG = [('planks', 4), ('stairs', 4), ('slab', 8), ('fence', 2), ('fence_gate', 1), ('door', 2), ('trapdoor', 1),
            ('pressure_plate', 2), ('button', 4), ('sign', 2)]
# what one plank makes
FROM_PLANKS = [('stairs', 1), ('slab', 2), ('button', 1)]

recipes = {}

def add(name, ingredient, result, count):
    recipes[name] = {"type": "minecraft:stonecutting", "ingredient": ingredient,
                     "result": {"id": f"minecraft:{result}", "count": count}}

for w, log, wood, tag in WOODS:
    # stripping, and logs into wood (bark on all sides)
    add(f'stripped_{w}_{log}', f'minecraft:{w}_{log}', f'stripped_{w}_{log}', 1)
    add(f'{w}_{wood}_from_{log}', f'minecraft:{w}_{log}', f'{w}_{wood}', 1)
    add(f'stripped_{w}_{wood}_from_{log}', f'minecraft:{w}_{log}', f'stripped_{w}_{wood}', 1)
    add(f'stripped_{w}_{wood}', f'minecraft:{w}_{wood}', f'stripped_{w}_{wood}', 1)
    add(f'stripped_{w}_{wood}_from_stripped_{log}', f'minecraft:stripped_{w}_{log}', f'stripped_{w}_{wood}', 1)
    for what, n in FROM_LOG:
        add(f'{w}_{what}_from_{log}', f'#minecraft:{tag}', f'{w}_{what}', n)
    for what, n in FROM_PLANKS:
        add(f'{w}_{what}_from_planks', f'minecraft:{w}_planks', f'{w}_{what}', n)
    add(f'stick_from_{w}_planks', f'minecraft:{w}_planks', 'stick', 2)
    add(f'stick_from_{w}_{log}', f'#minecraft:{tag}', 'stick', 8)

# bamboo: a block of bamboo is 2 planks
add('stripped_bamboo_block', 'minecraft:bamboo_block', 'stripped_bamboo_block', 1)
for what, n in [('planks', 2), ('stairs', 2), ('slab', 4), ('mosaic', 2), ('button', 2), ('pressure_plate', 1), ('fence', 1), ('door', 1)]:
    add(f'bamboo_{what}_from_block', '#minecraft:bamboo_blocks', f'bamboo_{what}', n)
for what, n in [('stairs', 1), ('slab', 2), ('mosaic', 1), ('button', 1)]:
    add(f'bamboo_{what}_from_planks', 'minecraft:bamboo_planks', f'bamboo_{what}', n)
add('bamboo_mosaic_stairs', 'minecraft:bamboo_mosaic', 'bamboo_mosaic_stairs', 1)
add('bamboo_mosaic_slab', 'minecraft:bamboo_mosaic', 'bamboo_mosaic_slab', 2)
add('stick_from_bamboo_planks', 'minecraft:bamboo_planks', 'stick', 2)

if len(sys.argv) > 1:
    have = {f[:-5] for f in os.listdir(sys.argv[1])}
    bad = sorted({r['result']['id'][10:] for r in recipes.values()} - have
                 | {r['ingredient'][10:] for r in recipes.values() if not r['ingredient'].startswith('#')} - have)
    if bad: sys.exit('unknown items: ' + ', '.join(bad))

shutil.rmtree(OUT, ignore_errors=True)
os.makedirs(OUT)
for name, r in recipes.items():
    with open(f'{OUT}/{name}.json', 'w') as f: json.dump(r, f, indent=2); f.write('\n')
print(f'wrote {len(recipes)} recipes to {OUT}')
