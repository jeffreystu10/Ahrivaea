"""Turns the creature list (tools/data/Monster_List_v.1.xlsx) into creature data files.

    python3 tools/creatures_from_sheet.py [path/to/Monster_List.xlsx] [items dir to check ids against]

Sheet1 has a table per influence (rarity, name, biomes, drops); Sheet2 has each creature's primary and secondary
work. Creatures with the same name under several influences become one species with a form per influence. Each form
gets its own spawn (biomes, rarity weight, levels), drops and jobs. Stats come from the existing creature file, else
out/roster.json, else are rolled (seeded by name, so they stay the same between runs): 0-100 each, 500-600 in total.
Writes src/main/resources/data/ahrivaea/creature/<id>.json and tools/data/creature_import_report.txt.
"""
import json, os, random, re, sys
import openpyxl

SRC = sys.argv[1] if len(sys.argv) > 1 else 'tools/data/Monster_List_v.1.xlsx'
ITEMS = sys.argv[2] if len(sys.argv) > 2 else None
OUT = 'src/main/resources/data/ahrivaea/creature'
ROSTER = '/home/claude/out/roster.json'

INFLUENCES = ['none', 'fire', 'frost', 'storm', 'earth', 'water', 'nature', 'void', 'fae', 'darkness', 'radiant', 'arcane']
STATS = ['hp', 'attack', 'defense', 'speed', 'magic', 'luck', 'charisma']

# rarity -> (spawn weight, min level, max level)
RARITY = {'Common': (40, 2, 12), 'Uncommon': (18, 6, 18), 'Rare': (8, 12, 26), 'Super Rare': (3, 20, 35), 'Legendary': (1, 30, 45)}
# how common the whole influence is (the "Overall Rarity" in each table's title)
OVERALL = {'Common': 1.5, 'Rare': 1.0, 'Super Rare': 0.6, 'Legendary': 0.3}

BIOMES = {
    'All': ['#minecraft:is_overworld', '#minecraft:is_nether', '#minecraft:is_end'],
    'All Overworld': ['#minecraft:is_overworld'],
    'Crimson Forests': ['minecraft:crimson_forest'],
    'Dripstone Cavern': ['minecraft:dripstone_caves'],
    'Mushroom Forest': ['minecraft:mushroom_fields'],
    'Old Growth Taiga': ['minecraft:old_growth_spruce_taiga', 'minecraft:old_growth_pine_taiga'],
    'Small end Islands': ['minecraft:small_end_islands'],
    'Swamp Mangrove Swamp': ['minecraft:swamp', 'minecraft:mangrove_swamp'],
    'Wooded Bandlands': ['minecraft:wooded_badlands'],
}

# drop names that aren't just the item id spelled out
ITEM_NAMES = {
    'Raw Beef': 'beef', 'Raw Chicken': 'chicken', 'Raw Cod': 'cod', 'Raw Salmon': 'salmon', 'Raw Porkchop': 'porkchop',
    'Raw Mutton': 'mutton', 'Raw Rabbit': 'rabbit', 'Redstone Dust': 'redstone', 'Slimeball': 'slime_ball',
    "Rabbit's Foot": 'rabbit_foot', "Dragon's Breath": 'dragon_breath', 'Wool': 'white_wool', 'Carpet': 'white_carpet',
    'Mushroom': 'brown_mushroom', 'Eye of Ender': 'ender_eye', 'Spider Web': 'cobweb', 'Quartz': 'quartz',
    'Banana': 'ahrivaea:banana', 'Ivory': 'ahrivaea:ivory', 'Purpur Shard': 'ahrivaea:purpur_shard', 'Raw Rat': 'ahrivaea:raw_rat',
}

# work text -> job, first match wins (see docs/creatures.md for what each job does)
JOBS = [
    ('defense', r'defen|patrol|protect|security|guard|deterr|detection|hunting|mob-farm control|storage-room|valuable-item'),
    ('ranching', r'hostile-mob|mob drop|mob-farm|egg|milk|wool|shearing|feather|leather|livestock|ranch|husbandry|breeding|slimeball|ink-sac|string and|arrow and bone|rabbit hide'),
    ('fishing', r'fish|aquatic|kelp|seagrass|ocean|underwater|water-channel|beach'),
    ('logging', r'tree|logging|sapling|wood processing|spruce and birch'),
    ('growth', r'growth|irrigation|pollination|compost|healing and farm|crop and animal-area'),
    ('farming', r'crop|seed|berry|berries|mushroom|carrot|truffle|plant collection|floor-area'),
    ('smelting', r'smelt|furnace|cooking|fuel|glass and brick|machine operation'),
    ('brewing', r'brew|potion'),
    ('enchanting', r'enchant'),
    ('redstone', r'redstone'),
    ('ice', r'snow|ice'),
    ('lighting', r'light'),
    ('crafting', r'craft|stonecutting|block processing|stone and brick|cobblestone processing'),
    ('mining', r'mining|quarry|ore|stone|cobblestone|sand|gravel|clay|cave|resource gathering|nether|end resource|glowstone|copper'),
    ('transport', r'pickup|courier|transport|routing|sort|chest|teleport|item'),
    ('riding', r'mounted|travel'),
    ('ranching', r'collection|gathering|harvest'),
]

TABLE = re.compile(r'^\s*(\w[\w ]*?)\s*-\s*Overall Rarity\s*-\s*(.+?)\s*$')
DROP = re.compile(r"^\s*(.+?)\s*\((\d+)%\s*[|,]\s*(\d+)(?:\s*[-–]\s*(\d+))?\)\s*$")

report = []


def slug(s):
    return re.sub(r'[^a-z0-9]+', '_', s.lower()).strip('_')


def biomes(text):
    out = []
    for b in re.split(r',\s*', text or ''):
        b = b.strip()
        if not b: continue
        out += BIOMES.get(b, ['minecraft:' + slug(b)])
    return sorted(set(out), key=out.index)


def item_id(name):
    clean = re.sub(r'\s*\(mod item\)\s*', '', name).strip()
    i = ITEM_NAMES.get(clean, slug(clean))
    return i if ':' in i else 'minecraft:' + i


def drops(text):
    out = []
    for part in re.split(r';', text or ''):
        if not part.strip() or part.strip() == 'Drop': continue
        m = DROP.match(part)
        if not m:
            report.append(f'unreadable drop: {part!r}')
            continue
        name, pct, lo, hi = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4) or m.group(3))
        d = {'item': item_id(name), 'chance': pct / 100, 'min': lo, 'max': hi}
        if name.strip() == 'Enchanted Book': d['enchant'] = True
        out.append(d)
    return out


def job(text):
    t = (text or '').lower()
    for key, pattern in JOBS:
        if re.search(pattern, t): return key
    report.append(f'no job for: {text!r}')
    return 'transport'


def rolled_stats(seed):
    r = random.Random(seed)
    while True:
        s = [r.randint(40, 100) for _ in STATS]
        if 500 <= sum(s) <= 600: return dict(zip(STATS, s))


def main():
    wb = openpyxl.load_workbook(SRC, data_only=True)
    # Sheet1: one table per influence on each side (columns A-D and E-H)
    entries = []
    current = [None, None]
    for row in wb['Sheet1'].iter_rows(values_only=True):
        for half, off in ((0, 0), (1, 4)):
            v = row[off] if off < len(row) else None
            m = TABLE.match(v) if isinstance(v, str) else None
            if m:
                current[half] = (m.group(1).strip().lower(), m.group(2).strip())
                continue
            name = row[off + 1] if off + 1 < len(row) else None
            if not v or not name or v in ('Commanilty', 'Commonality') or current[half] is None: continue
            inf, overall = current[half]
            if inf not in INFLUENCES: report.append(f'unknown influence {inf}'); continue
            entries.append(dict(influence=inf, overall=overall, rarity=v.strip(), name=name.strip(),
                                biomes=biomes(row[off + 2]), drops=drops(row[off + 3])))
    # Sheet2: work
    work = {}
    for row in list(wb['Sheet2'].iter_rows(values_only=True))[1:]:
        if not row or not row[2]: continue
        inf = str(row[0]).strip().lower()
        work[('none' if inf == 'normal' else inf, row[2].strip())] = [w for w in (row[4], row[5]) if w]

    roster = {}
    if os.path.exists(ROSTER):
        for r in json.load(open(ROSTER)):
            roster[(r['monster'], r['influence'].lower())] = dict(zip(STATS, r['stats']))

    species = {}
    for e in entries:
        sid = slug(e['name'])
        path = f'{OUT}/{sid}.json'
        if sid not in species:
            species[sid] = json.load(open(path)) if os.path.exists(path) else {'name': e['name'], 'forms': {}}
            species[sid].setdefault('forms', {})
            species[sid]['_seen'] = set()
        s = species[sid]
        if e['influence'] in s['_seen']:
            report.append(f"{e['name']} is listed twice under {e['influence']}; kept the first")
            continue
        s['_seen'].add(e['influence'])
        form = s['forms'].setdefault(e['influence'], {})
        if 'stats' not in form:
            form['stats'] = roster.get((e['name'], e['influence'])) or rolled_stats(f"{e['name']}/{e['influence']}")
        weight, lo, hi = RARITY.get(e['rarity'], RARITY['Common'])
        form['rarity'] = e['rarity']
        form['spawn'] = {'biomes': e['biomes'], 'min_level': lo, 'max_level': hi,
                         'weight': max(1, round(weight * 10 * OVERALL.get(e['overall'], 1.0)))}
        form['drops'] = e['drops']
        w = work.get((e['influence'], e['name']))
        if w is None: report.append(f"no work listed for {e['name']} ({e['influence']})")
        form['jobs'] = [{'job': job(t), 'text': t} for t in (w or [])]

    os.makedirs(OUT, exist_ok=True)
    all_items = set()
    for sid, s in species.items():
        s.pop('_seen')
        s.pop('spawn', None)  # each form spawns on its own now
        first = next(iter(s['forms'].values()))
        s.setdefault('stats', first['stats'])
        if 'size' not in s or 'movement' not in s:
            s.setdefault('size', size_guess(s['name']))
            s.setdefault('movement', movement_guess(s))
        s['forms'] = {k: s['forms'][k] for k in INFLUENCES if k in s['forms']}
        for f in s['forms'].values():
            for d in f.get('drops', []): all_items.add(d['item'])
        with open(f'{OUT}/{sid}.json', 'w') as fh: json.dump(s, fh, indent=2); fh.write('\n')

    if ITEMS:
        have = {f[:-5] for f in os.listdir(ITEMS)}
        for i in sorted(all_items):
            if i.startswith('minecraft:') and i[10:] not in have: report.append(f'unknown item {i}')
    forms = sum(len(s['forms']) for s in species.values())
    report.insert(0, f'{len(species)} species, {forms} forms, {len(entries)} rows; drop items: {len(all_items)}')
    open('tools/data/creature_import_report.txt', 'w').write('\n'.join(report) + '\n')
    print('\n'.join(report[:40]))


FLYERS = r'dragon|phoenix|wyrm|wyvern|griff|harpy|roc\b|bat\b|stirge|pegasus|eagle|owl|hawk|raven|crow|sprite|pixie|fairy|faerie|wisp|angel|thunderbird|djinn|genie|bird|moth|bee\b|wasp|gargoyle|peryton|hippogriff|couatl|zephyr|cloud|wind|air elemental|imp\b|mephit|beholder|will|ghost|specter|spectre|wraith|banshee|eye'
SWIMMERS = r'fish|shark|orca|squid|kraken|leviathan|isonade|scylla|charybdis|aspidochelone|eel|ray\b|jelly|crab|lobster|merrow|mermaid|selkie|kelpie|water weird|nereid|octo|whale|dolphin|turtle|serpent'
BIG = r'dragon|wyrm|treant|troll|giant|orca|kraken|leviathan|behemoth|tarrasque|roc\b|aspidochelone|titan|golem|colossus|mammoth|elder|ancient|balor|whale|hydra|death knight|ogre|cyclops|ent\b|moose|elephant|scylla'


def size_guess(name):
    n = name.lower()
    if re.search(BIG, n): return 1.6
    if re.search(r'rat|imp\b|sprite|pixie|fairy|wisp|mephit|frog|snail|fish|bat\b|stirge|bee\b', n): return 0.7
    return 1.0


def movement_guess(s):
    n = s['name'].lower()
    if re.search(SWIMMERS, n): return 'swim'
    if re.search(FLYERS, n): return 'fly'
    water = all(re.search(r'ocean|river', b) for f in s['forms'].values() for b in f['spawn']['biomes'])
    return 'swim' if water else 'walk'


if __name__ == '__main__':
    main()
