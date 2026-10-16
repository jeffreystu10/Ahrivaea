"""Imports creature models made in Blockbench (.bbmodel, "Mythic Mob Library" exports) into the creatures mod.

    python3 tools/import_models.py <zip or .bbmodel or folder> ...

Each .bbmodel names its creature and influence in its "mythic_mob" block. For each one this writes:
  assets/ahrivaea/geo/creature/<species>/<influence>.geo.json        the model (Bedrock geometry, per-face UVs)
  assets/ahrivaea/geo/creature/<species>/<influence>.animation.json  its animations (idle, fly ...)
  assets/ahrivaea/textures/entity/creature/<species>/<influence>[_enraged][_mutated].png
and sets the form's "size" in data/ahrivaea/creature/<species>.json from the model's height, so its hitbox
matches. The source files are kept in art/models/<influence>/. Models whose creature isn't in the roster
(or doesn't have that influence) are kept but not used; the report lists them.
"""
import base64
import glob
import json
import math
import os
import re
import shutil
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/ahrivaea')
DATA = os.path.join(ROOT, 'src/main/resources/data/ahrivaea/creature')
ART = os.path.join(ROOT, 'art/models')
REPORT = os.path.join(ROOT, 'tools/data/model_import_report.txt')

# model creature names that differ from the roster's names
ALIASES = {
    'dancingaxe': 'enchanted_axe',
    'djinn': 'djinni',
    'hauntedlantern': 'enchanted_lantern',
    'rugofsmothering': 'enchanted_rug',
    'spellbook': 'living_spellbook',
    'tarasque': 'terrasque',
}
# the base hitbox of a creature at size 1, in blocks
BASE_W, BASE_H = 0.7, 0.8


def norm(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())


def roster():
    out = {}
    for f in glob.glob(os.path.join(DATA, '*.json')):
        sid = os.path.basename(f)[:-5]
        d = json.load(open(f))
        out[norm(sid)] = sid
        out.setdefault(norm(d['name']), sid)
    return out


def rot_matrix(r):
    """Blockbench's bone rotation (degrees, applied Z then Y then X ... as three.js 'ZYX')."""
    x, y, z = (math.radians(a) for a in r)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    mul = lambda a, b: [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(mul(rz, ry), rx)


def convert(bb):
    """A .bbmodel (Blockbench project) to Bedrock geometry + animations, and its rest-pose bounding box."""
    elements = {e['uuid']: e for e in bb['elements']}
    tw, th = bb['resolution']['width'], bb['resolution']['height']
    ident = bb['mythic_mob']['creature']
    bones, points = [], []

    def cube(e):
        f, t = e['from'], e['to']
        inflate = e.get('inflate', 0)
        c = {'origin': [-t[0], f[1], f[2]], 'size': [t[i] - f[i] for i in range(3)], 'uv': {}}
        if inflate: c['inflate'] = inflate
        for face, fd in e['faces'].items():
            if fd.get('texture') is None: continue
            u1, v1, u2, v2 = fd['uv']
            c['uv'][face] = {'uv': [u1, v1], 'uv_size': [u2 - u1, v2 - v1]}
        return c

    def walk(nodes, parent, chain):
        loose = []
        for n in nodes:
            if isinstance(n, str):
                if n in elements: loose.append(elements[n])
                continue
            o, r = n['origin'], n.get('rotation', [0, 0, 0])
            bone = {'name': n['name'], 'pivot': [-o[0], o[1], o[2]]}
            if parent: bone['parent'] = parent
            if any(r): bone['rotation'] = [-r[0], -r[1], r[2]]
            mine = [elements[c] for c in n['children'] if isinstance(c, str) and c in elements]
            if mine: bone['cubes'] = [cube(e) for e in mine]
            bones.append(bone)
            here = chain + [(o, rot_matrix(r))]
            for e in mine:
                for corner in range(8):
                    p = [e['to'][i] if corner >> i & 1 else e['from'][i] for i in range(3)]
                    for origin, m in reversed(here):
                        d = [p[i] - origin[i] for i in range(3)]
                        p = [origin[i] + sum(m[i][k] * d[k] for k in range(3)) for i in range(3)]
                    points.append(p)
            walk(n['children'], n['name'], here)
        if loose:
            bones.append({'name': (parent or 'root') + '_cubes', 'pivot': [0, 0, 0], **({'parent': parent} if parent else {}),
                          'cubes': [cube(e) for e in loose]})
            for e in loose:
                points.extend([e["from"], e["to"]])

    walk(bb['outliner'], None, [])
    lo = [min(p[i] for p in points) for i in range(3)]
    hi = [max(p[i] for p in points) for i in range(3)]
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {'identifier': 'geometry.' + ident, 'texture_width': tw, 'texture_height': th,
                            'ahrivaea:real_size': True},
            'bones': bones,
        }],
    }

    names = {a['uuid']: a for a in bb.get('animations', [])}
    anims = {}
    for a in names.values():
        short = a['name'].split('.')[-1]
        bones_out = {}
        for an in a.get('animators', {}).values():
            if an.get('type', 'bone') != 'bone': continue
            chans = {}
            for k in an.get('keyframes', []):
                dp = k['data_points'][0]
                chans.setdefault(k['channel'], {})['%g' % k['time']] = [str(dp.get(c, '0')) for c in 'xyz']
            for ch, frames in chans.items():
                bones_out.setdefault(an['name'], {})[ch] = next(iter(frames.values())) if len(frames) == 1 else frames
        anims[short] = {'loop': a.get('loop') == 'loop', 'animation_length': a.get('length', 1), 'bones': bones_out}
    animation = {'format_version': '1.8.0', 'animations': anims}
    return geo, animation, lo, hi


def size_for(lo, hi):
    h = max(hi[1], 0) - max(lo[1], 0)
    wide = max(hi[0] - lo[0], hi[2] - lo[2])
    return round(max(h / 16 / BASE_H, 0.5 * wide / 16 / BASE_W, 0.5), 2)


def collect(paths, tmp):
    files = []
    for p in paths:
        if os.path.isdir(p):
            files += glob.glob(os.path.join(p, '**/*.bbmodel'), recursive=True)
            for z in glob.glob(os.path.join(p, '**/*.zip'), recursive=True): files += collect([z], tmp)
        elif p.endswith('.zip') or zipfile.is_zipfile(p):
            out = tempfile.mkdtemp(dir=tmp)
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if n.endswith('.bbmodel') and not n.startswith('__MACOSX'):
                        z.extract(n, out)
                        files.append(os.path.join(out, n))
        elif p.endswith('.bbmodel'):
            files.append(p)
    return files


def main(paths):
    known = roster()
    report, used = [], {}
    tmp = tempfile.mkdtemp()
    try:
        files = collect(paths, tmp)
        groups = {}
        for f in files:
            bb = json.load(open(f))
            mm = bb.get('mythic_mob')
            if not mm:
                report.append(f'{os.path.basename(f)}: no mythic_mob block, skipped')
                continue
            key = (mm['creature'], mm['influence'], mm.get('baby', False))
            variant = ('_enraged' if mm.get('enraged') else '') + ('_mutated' if mm.get('mutated') else '')
            groups.setdefault(key, {})[variant] = (f, bb)
        for (creature, influence, baby), variants in sorted(groups.items()):
            base_file, base = variants.get('', next(iter(variants.values())))
            os.makedirs(os.path.join(ART, influence), exist_ok=True)
            for f, _ in variants.values(): shutil.copy(f, os.path.join(ART, influence, os.path.basename(f)))
            if baby:
                report.append(f'{creature} ({influence}): baby model kept in art/, not used yet')
                continue
            sid = ALIASES.get(norm(creature)) or known.get(norm(creature))
            species = json.load(open(os.path.join(DATA, sid + '.json'))) if sid else None
            if not species or influence not in species['forms']:
                report.append(f'{creature} ({influence}): no such creature in the roster; kept in art/models/{influence}/, not used')
                continue
            geo, animation, lo, hi = convert(base)
            gdir = os.path.join(ASSETS, 'geo/creature', sid)
            tdir = os.path.join(ASSETS, 'textures/entity/creature', sid)
            os.makedirs(gdir, exist_ok=True)
            os.makedirs(tdir, exist_ok=True)
            json.dump(geo, open(os.path.join(gdir, influence + '.geo.json'), 'w'), separators=(',', ':'))
            json.dump(animation, open(os.path.join(gdir, influence + '.animation.json'), 'w'), indent=1)
            for variant, (f, bb) in variants.items():
                src = bb['textures'][0]['source']
                open(os.path.join(tdir, influence + variant + '.png'), 'wb').write(base64.b64decode(src.split(',', 1)[1]))
            size = size_for(lo, hi)
            species['forms'][influence]['size'] = size
            with open(os.path.join(DATA, sid + '.json'), 'w') as fh:
                json.dump(species, fh, indent=2)
                fh.write('\n')
            used[f'{sid}/{influence}'] = (creature, base['mythic_mob'].get('size'), size, len(variants), list(animation['animations']))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    lines = [f'{len(used)} models in use'] + [f'  {k}: from "{v[0]}" ({v[1]}), size {v[2]}, {v[3]} textures, animations {", ".join(v[4])}'
                                              for k, v in sorted(used.items())] + report
    # keep earlier imports' lines for other influences
    old = open(REPORT).read().splitlines() if os.path.exists(REPORT) else []
    keep = [l for l in old[1:] if l.startswith('  ') and l.split(':')[0].strip() not in used]
    if keep:
        lines = [f'{len(used) + len(keep)} models in use'] + sorted(lines[1:len(used) + 1] + keep) + report
    open(REPORT, 'w').write('\n'.join(lines) + '\n')
    print('\n'.join(lines))


if __name__ == '__main__':
    main(sys.argv[1:])
