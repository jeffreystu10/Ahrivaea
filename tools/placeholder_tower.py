"""Generates the placeholder tower (ruined and finished) as structure files, until the real tower is ready.
Run: python3 tools/placeholder_tower.py   (needs: pip install nbtlib)"""
import math, random
from nbtlib import File, Compound, List, Int, String
OUT = 'src/main/resources/data/ahrivaea/structure/tower/'
W, H = 11, 27          # footprint and height
C = (W - 1) / 2

def inside(x, z, r):
    return max(abs(x - C), abs(z - C)) + 0.5 * min(abs(x - C), abs(z - C)) <= r   # octagon

def shell(x, z):
    return inside(x, z, 5.0) and not inside(x, z, 4.0)

def build(ruined):
    rnd = random.Random(7)
    B = {}
    for x in range(W):
        for z in range(W):
            if not inside(x, z, 5.0):
                continue
            top = H
            if ruined:
                top = 3 + int(abs(math.sin(x * 1.7 + z * 0.9)) * 7) + rnd.randint(0, 2)
            for y in range(H):
                pos = (x, y, z)
                if y == 0:
                    B[pos] = 'minecraft:polished_andesite' if not ruined else rnd.choice(['minecraft:polished_andesite', 'minecraft:cobblestone', 'minecraft:gravel'])
                elif shell(x, z) and y < top:
                    if ruined:
                        B[pos] = rnd.choice(['minecraft:cracked_stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'])
                    else:
                        B[pos] = 'minecraft:stone_bricks' if y % 7 else 'minecraft:chiseled_stone_bricks'
                elif not ruined and y in (7, 14, 21) and inside(x, z, 4.0) and not (x == C and z == C + 3):
                    B[pos] = 'minecraft:spruce_planks'
                else:
                    B[pos] = 'minecraft:air'
    if not ruined:
        # windows, battlements, door, ladder, roof beacon
        for y in range(3, H - 2, 7):
            for (x, z) in [(int(C), 0), (int(C), W - 1), (0, int(C)), (W - 1, int(C))]:
                B[(x, y, z)] = B[(x, y + 1, z)] = 'minecraft:glass_pane'
        for x in range(W):
            for z in range(W):
                if shell(x, z):
                    B[(x, H - 1, z)] = 'minecraft:stone_brick_wall' if (x + z) % 2 else 'minecraft:air'
                elif inside(x, z, 4.0):
                    B[(x, H - 1, z)] = 'minecraft:air'
                    B[(x, H - 2, z)] = 'minecraft:spruce_planks' if not (x == C and z == C + 3) else 'minecraft:air'
        B[(int(C), H - 1, int(C))] = 'minecraft:lantern'
        for y in range(1, H - 1):
            B[(int(C), y, int(C) + 4)] = 'minecraft:stone_bricks'
            B[(int(C), y, int(C) + 3)] = 'minecraft:ladder[facing=north]'
    else:
        for _ in range(14):
            x, z = rnd.randint(1, W - 2), rnd.randint(1, W - 2)
            if inside(x, z, 4.0) and (x, z) != (int(C), int(C)):
                B[(x, 1, z)] = rnd.choice(['minecraft:cobblestone', 'minecraft:gravel', 'minecraft:mossy_cobblestone', 'minecraft:cobblestone_slab'])
    # doorway on the north side and the offering chest in the middle
    for y in (1, 2):
        B[(int(C), y, 0)] = 'minecraft:air'
    B[(int(C), 1, int(C))] = 'minecraft:chest[facing=north]'
    return B

def save(B, name):
    palette, idx, blocks = [], {}, []
    for pos, s in sorted(B.items()):
        if s not in idx:
            n, _, props = s.partition('[')
            c = Compound({'id': String(n)})   # 26.x block state keys: id, properties
            if props:
                c['properties'] = Compound({k: String(v) for k, v in (p.split('=') for p in props.rstrip(']').split(','))})
            idx[s] = len(palette); palette.append(c)
        blocks.append(Compound({'state': Int(idx[s]), 'pos': List[Int]([Int(v) for v in pos])}))
    f = File({'DataVersion': Int(5023), 'size': List[Int]([Int(W), Int(H), Int(W)]), 'palette': List[Compound](palette),
              'blocks': List[Compound](blocks), 'entities': List[Compound]([])}, gzipped=True)
    f.save(OUT + name + '.nbt')
    print(name, len(blocks), 'blocks', len(palette), 'states')

save(build(True), 'ruined')
save(build(False), 'finished')
