"""Makes Creature_Roster.xlsx: every creature form in the game, with where it spawns, its stats, drops and jobs
(the creature list's wording and the job it was sorted into, and whether that job works yet).
    python3 tools/roster_sheet.py out.xlsx"""
import glob, json, sys
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

WORKING = {'farming', 'growth', 'logging', 'mining', 'smelting', 'transport', 'ranching', 'fishing', 'defense', 'brewing', 'enchanting', 'redstone', 'crafting', 'ice', 'lighting'}
INF = ['none', 'fire', 'frost', 'storm', 'earth', 'water', 'nature', 'void', 'fae', 'darkness', 'radiant', 'arcane']
RAR = {'Common': 0, 'Uncommon': 1, 'Rare': 2, 'Super Rare': 3, 'Legendary': 4}
STATS = ['hp', 'attack', 'defense', 'speed', 'magic', 'luck', 'charisma']

rows = []
for f in sorted(glob.glob('src/main/resources/data/ahrivaea/creature/*.json')):
    sid = f.split('/')[-1][:-5]
    d = json.load(open(f))
    for inf, fm in d['forms'].items():
        if 'spawn' not in fm: continue
        name = fm.get('name') or (d['name'] if inf == 'none' else inf.capitalize() + ' ' + d['name'])
        st = fm.get('stats') or d['stats']
        jobs = fm.get('jobs', [])
        def job(i):
            if i >= len(jobs): return '', '', ''
            j = jobs[i]
            return j['text'], j['job'].capitalize(), 'yes' if j['job'] in WORKING else 'later'
        drops = '; '.join(f"{x['item'].split(':')[1].replace('_', ' ')} ({round(x['chance'] * 100)}%, {x['min']}{'' if x['max'] == x['min'] else '-' + str(x['max'])})" for x in fm.get('drops', []))
        sp = fm['spawn']
        rows.append([inf.capitalize(), fm.get('rarity', ''), name, sid, d.get('movement', 'walk'), ', '.join(b.split(':')[1] for b in sp['biomes']),
                     sp['weight'], f"{sp['min_level']}-{sp['max_level']}", *[st[s] for s in STATS], sum(st.values()), drops, *job(0), *job(1)])
rows.sort(key=lambda r: (INF.index(r[0].lower()), RAR.get(r[1], 9), r[2]))

wb = Workbook()
ws = wb.active
ws.title = 'Creatures'
head = ['Influence', 'Rarity', 'Name', 'Id', 'Moves', 'Biomes', 'Spawn weight', 'Levels', 'HP', 'Attack', 'Defense', 'Speed', 'Magic', 'Luck',
        'Charisma', 'Total', 'Drops (chance, amount)', 'Job 1 (from the list)', 'Job 1', 'Works now', 'Job 2 (from the list)', 'Job 2', 'Works now']
ws.append(head)
for r in rows: ws.append(r)
fill = PatternFill('solid', fgColor='2F3B4C')
for c in ws[1]:
    c.font = Font(bold=True, color='FFFFFF')
    c.fill = fill
    c.alignment = Alignment(wrap_text=True, vertical='center')
widths = [10, 11, 24, 18, 7, 40, 8, 7, 5, 6, 7, 6, 6, 5, 8, 6, 60, 30, 11, 9, 30, 11, 9]
for i, w in enumerate(widths, 1): ws.column_dimensions[get_column_letter(i)].width = w
for row in ws.iter_rows(min_row=2):
    for c in (row[5], row[16]): c.alignment = Alignment(wrap_text=True, vertical='top')
    for c in (row[19], row[22]):
        if c.value == 'later': c.font = Font(color='9C6500')
ws.freeze_panes = 'D2'
ws.auto_filter.ref = ws.dimensions

# job summary
js = wb.create_sheet('Jobs')
js.append(['Job', 'Works now', 'Forms with it', 'What the worker does'])
desc = {'Farming': 'Harvests ripe crops and replants them', 'Growth': 'Tends plants so they grow faster', 'Logging': 'Fells natural trees and replants',
        'Mining': 'Works a Mining Post (stone and ores by location)', 'Smelting': 'Runs furnaces with no fuel, twice as fast',
        'Transport': 'Carries loose items and smelting output to chests', 'Ranching': 'Produces its own drops every 2-3 minutes',
        'Fishing': 'Fishes from water in the base', 'Defense': 'Fights off raids on the base',
        'Brewing': 'Runs brewing stands with no blaze powder, twice as fast', 'Enchanting': "Studies at an enchanting table: bottles o' enchanting, sometimes a book",
        'Redstone': 'Powers a Creature Dynamo (signal 15)', 'Crafting': 'Works crafters', 'Ice': 'Makes ice and snowballs from water',
        'Lighting': "Puts torches from the base's chests on dark spots", 'Riding': 'Comes with rideable creatures'}
counts = {}
for r in rows:
    for j in {r[18], r[21]} - {''}: counts[j] = counts.get(j, 0) + 1
for j, n in sorted(counts.items(), key=lambda x: -x[1]):
    js.append([j, 'yes' if j.lower() in WORKING else 'later', n, desc.get(j, '')])
for c in js[1]:
    c.font = Font(bold=True, color='FFFFFF')
    c.fill = fill
for i, w in enumerate([12, 10, 13, 55], 1): js.column_dimensions[get_column_letter(i)].width = w
wb.save(sys.argv[1] if len(sys.argv) > 1 else 'Creature_Roster.xlsx')
print(len(rows), 'forms')
