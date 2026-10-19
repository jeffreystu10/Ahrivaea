"""Checks every creature form in data/ahrivaea/creature: stats, spawn biomes, drops, jobs and model.

    python3 tools/audit_creatures.py

Writes tools/data/creature_audit.txt and prints the problems (exit code 1 if there are any).
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'src/main/resources/data/ahrivaea/creature')
GEO = os.path.join(ROOT, 'src/main/resources/assets/ahrivaea/geo/creature')
TEX = os.path.join(ROOT, 'src/main/resources/assets/ahrivaea/textures/entity/creature')
STATS = ['hp', 'attack', 'defense', 'speed', 'magic', 'luck', 'charisma']
JOBS = {'defense', 'ranching', 'fishing', 'logging', 'growth', 'farming', 'smelting', 'brewing', 'enchanting', 'redstone',
        'ice', 'lighting', 'crafting', 'mining', 'transport', 'riding'}
# Minecraft 26.3's biomes (net.minecraft.world.level.biome.Biomes)
BIOMES = set('''the_void plains sunflower_plains snowy_plains ice_spikes desert swamp mangrove_swamp forest flower_forest
birch_forest dappled_forest dark_forest pale_garden old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga
taiga snowy_taiga savanna savanna_plateau windswept_hills windswept_gravelly_hills windswept_forest windswept_savanna
jungle sparse_jungle bamboo_jungle badlands eroded_badlands wooded_badlands meadow cherry_grove grove snowy_slopes
frozen_peaks jagged_peaks stony_peaks river frozen_river beach snowy_beach stony_shore warm_ocean lukewarm_ocean
deep_lukewarm_ocean ocean deep_ocean cold_ocean deep_cold_ocean frozen_ocean deep_frozen_ocean mushroom_fields
dripstone_caves lush_caves deep_dark sulfur_caves nether_wastes warped_forest crimson_forest soul_sand_valley basalt_deltas
the_end end_highlands end_midlands small_end_islands end_barrens'''.split())
TAGS = {'#minecraft:is_overworld', '#minecraft:is_nether', '#minecraft:is_end', '#minecraft:is_badlands', '#minecraft:is_ocean',
        '#minecraft:is_deep_ocean', '#minecraft:is_forest', '#minecraft:is_jungle', '#minecraft:is_taiga', '#minecraft:is_river',
        '#minecraft:is_mountain', '#minecraft:is_hill', '#minecraft:is_beach', '#minecraft:is_savanna'}
ITEMS = {l.split('|')[0].split(':', 1)[1] for l in open(os.path.join(ROOT, 'tools/data/items.txt')) if l.startswith('minecraft:')}
MOD_ITEMS = {'ahrivaea:banana', 'ahrivaea:ivory', 'ahrivaea:purpur_shard', 'ahrivaea:raw_rat'}


def models(species):
    d = os.path.join(GEO, species)
    return {f[:-9] for f in os.listdir(d) if f.endswith('.geo.json')} if os.path.isdir(d) else set()


def main():
    problems, lines = [], []
    files = sorted(f for f in os.listdir(DATA) if f.endswith('.json'))
    forms_total = own = 0
    for f in files:
        sid = f[:-5]
        s = json.load(open(os.path.join(DATA, f)))
        have = models(sid)
        for inf, form in s.get('forms', {}).items():
            forms_total += 1
            where = f"{s.get('name', sid)} ({inf})"
            st = form.get('stats') or s.get('stats')
            if not st or any(k not in st for k in STATS): problems.append(f'{where}: missing stats')
            elif any(not 0 <= st[k] <= 100 for k in STATS) or not 500 <= sum(st[k] for k in STATS) <= 600:
                problems.append(f'{where}: stats out of range (total {sum(st[k] for k in STATS)})')
            sp = form.get('spawn')
            if sid != 'villager':
                if not sp: problems.append(f'{where}: no spawn')
                else:
                    for b in sp['biomes']:
                        if b.startswith('#'):
                            if b not in TAGS: problems.append(f'{where}: unknown biome tag {b}')
                        elif not b.startswith('minecraft:') or b[10:] not in BIOMES: problems.append(f'{where}: unknown biome {b}')
                if not form.get('drops'): problems.append(f'{where}: no drops')
                if not form.get('jobs'): problems.append(f'{where}: no jobs')
            for d in form.get('drops', []):
                i = d['item']
                if i not in MOD_ITEMS and (not i.startswith('minecraft:') or i[10:] not in ITEMS): problems.append(f'{where}: unknown drop item {i}')
            for j in form.get('jobs', []):
                if j['job'] not in JOBS: problems.append(f'{where}: unknown job {j["job"]}')
            if inf in have: own += 1
            elif have: lines.append(f'{where}: borrows the {"none" if "none" in have else sorted(have)[0]} model')
            else: problems.append(f'{where}: no model at all (drawn as the placeholder)')
            for v in ('', '_enraged', '_mutated', '_enraged_mutated'):
                if inf in have and not os.path.exists(os.path.join(TEX, sid, inf + v + '.png')):
                    problems.append(f'{where}: no {v or "base"} texture')
    head = f'{len(files)} species, {forms_total} forms, {own} with their own model; {len(problems)} problems'
    out = [head, ''] + ['PROBLEM ' + p for p in problems] + [''] + lines
    open(os.path.join(ROOT, 'tools/data/creature_audit.txt'), 'w').write('\n'.join(out) + '\n')
    print('\n'.join(out))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
