"""Checks the per-mod jars in build/mods: every file the project builds is in some jar, and no jar
uses code that's only in another jar (so each mod works on its own)."""
import fnmatch, glob, json, os, re, sys, zipfile

mods = json.load(open('mods.json'))
problems = []
jars = {}
for m in mods['mods']:
    found = glob.glob(f"build/mods/ahrivaea-{m['key']}-*.jar")
    if not found: problems.append(f"no jar for {m['key']}"); continue
    jars[m['key']] = zipfile.ZipFile(found[0])

# 1. every built file belongs to a mod
built = []
for root in ('build/classes/java/main', 'build/classes/java/client', 'build/resources/main', 'build/resources/client'):
    for dp, _, fs in os.walk(root):
        for f in fs: built.append(os.path.relpath(os.path.join(dp, f), root))
inside = set()
for z in jars.values(): inside.update(z.namelist())
for f in built:
    if f != 'fabric.mod.json' and f not in inside: problems.append(f"{f} isn't in any mod's jar (add it to an 'include' list in mods.json)")

# 2. each jar's code only refers to Ahrivaea classes inside the same jar
for key, z in jars.items():
    names = set(z.namelist())
    for n in names:
        if not n.endswith('.class'): continue
        for ref in set(re.findall(rb'dev/ahrivaea/[A-Za-z0-9_/$]+', z.read(n))):
            cls = ref.decode() + '.class'
            if cls not in names: problems.append(f"{key}: {n} uses {ref.decode()}, which is in a different mod")
    meta = json.loads(z.read('fabric.mod.json'))
    print(f"{key}: {len(names)} files, mod id {meta['id']}")

for p in sorted(set(problems)): print('PROBLEM', p)
sys.exit(1 if problems else 0)
