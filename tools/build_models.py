"""Builds creature models in the Mythic Mob Library's style (blocky parts, one flat colour per cube from a 64x16
palette texture) for creatures that don't have a library model yet.

    python3 tools/build_models.py [species ...]

The creatures are described in tools/model_defs.py. For each one this writes, like tools/import_models.py does:
  assets/ahrivaea/geo/creature/<species>/<influence>.geo.json        the model (Bedrock geometry)
  assets/ahrivaea/geo/creature/<species>/<influence>.animation.json  its idle (and fly) animations
  assets/ahrivaea/textures/entity/creature/<species>/<influence>[_enraged][_mutated].png
and sets the form's "size" in data/ahrivaea/creature/<species>.json from the model's size. Enraged textures turn the
eyes red and darken the body; Mutated ones shift the body's colours round the colour wheel. A real library model
imported later with tools/import_models.py simply replaces these files.
"""
import colorsys
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/ahrivaea')
DATA = os.path.join(ROOT, 'src/main/resources/data/ahrivaea/creature')
BASE_W, BASE_H = 0.7, 0.8  # the base hitbox at size 1, as in import_models.py
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def wave(amp, freq, phase=0, base=None):
    """A Molang sine: amp * sin(time * freq + phase), plus base."""
    s = f'{amp}*math.sin(query.life_time*1*{freq}+{phase})'
    return s if base is None else f'{base}+{s}'


def spin(speed):
    return f'query.life_time*1*{speed}'


class Model:
    """One creature model. Coordinates are Bedrock's: pixels (1/16 block), y up from the feet, the front towards -z,
    and the creature's left towards -x. Cube origins are absolute, as in Bedrock geometry."""

    def __init__(self, species, influence, palette):
        self.species, self.influence = species, influence
        self.palette = palette  # [(name, '#rrggbb', role)], role: body, accent, eye or fixed
        self.index = {name: i for i, (name, _, _) in enumerate(palette)}
        assert len(palette) <= 16, species
        self.bones, self.by_name = [], {}
        self.anims = {}

    # ---- geometry ----
    def bone(self, name, pivot, parent=None, rot=None):
        assert name not in self.by_name, name
        b = {'name': name, 'pivot': [round(v, 3) for v in pivot]}
        if parent: b['parent'] = parent
        if rot and any(rot): b['rotation'] = [round(v, 3) for v in rot]
        self.bones.append(b)
        self.by_name[name] = b
        return name

    def cube(self, bone, origin, size, col):
        i = self.index[col]
        u, v = (i % 8) * 8, (i // 8) * 8
        c = {'origin': [round(x, 3) for x in origin], 'size': [round(max(x, 0.01), 3) for x in size],
             'uv': {f: {'uv': [u, v], 'uv_size': [8, 8]} for f in FACES}}
        self.by_name[bone].setdefault('cubes', []).append(c)

    def box(self, bone, cx, y0, cz, w, h, d, col):
        """A cube centred on (cx, cz) in x and z, from y0 up."""
        self.cube(bone, (cx - w / 2, y0, cz - d / 2), (w, h, d), col)

    def pair(self, bone_l, bone_r, cx, y0, cz, w, h, d, col):
        """The same cube on both sides (cx is the distance out from the middle)."""
        self.box(bone_l, -cx, y0, cz, w, h, d, col)
        self.box(bone_r, cx, y0, cz, w, h, d, col)

    # ---- animation ----
    def anim(self, name, bones, length=1):
        self.anims[name] = {'loop': True, 'animation_length': length, 'bones': bones}

    # ---- output ----
    def bounds(self):
        lo, hi = [1e9] * 3, [-1e9] * 3
        for b in self.bones:
            for c in b.get('cubes', []):
                for i in range(3):
                    lo[i] = min(lo[i], c['origin'][i])
                    hi[i] = max(hi[i], c['origin'][i] + c['size'][i])
        return lo, hi

    def size(self):
        lo, hi = self.bounds()
        h = max(hi[1], 0) - max(lo[1], 0)
        wide = max(hi[0] - lo[0], hi[2] - lo[2])
        return round(max(h / 16 / BASE_H, 0.5 * wide / 16 / BASE_W, 0.5), 2)

    def geo(self):
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.' + self.species, 'texture_width': 64, 'texture_height': 16,
                            'ahrivaea:real_size': True},
            'bones': self.bones}]}

    def textures(self):
        out = {}
        for enraged in (False, True):
            for mutated in (False, True):
                img = Image.new('RGBA', (64, 16), (0, 0, 0, 0))
                for i, (_, hexcol, role) in enumerate(self.palette):
                    rgb = variant(hexcol, role, enraged, mutated)
                    for x in range(8):
                        for y in range(8):
                            img.putpixel(((i % 8) * 8 + x, (i // 8) * 8 + y), rgb + (255,))
                out[('_enraged' if enraged else '') + ('_mutated' if mutated else '')] = img
        return out

    def write(self):
        geo_dir = os.path.join(ASSETS, 'geo/creature', self.species)
        tex_dir = os.path.join(ASSETS, 'textures/entity/creature', self.species)
        os.makedirs(geo_dir, exist_ok=True)
        os.makedirs(tex_dir, exist_ok=True)
        with open(os.path.join(geo_dir, self.influence + '.geo.json'), 'w') as f:
            json.dump(self.geo(), f, separators=(',', ':'))
        with open(os.path.join(geo_dir, self.influence + '.animation.json'), 'w') as f:
            json.dump({'format_version': '1.8.0', 'animations': self.anims}, f, indent=1)
        for suffix, img in self.textures().items():
            img.save(os.path.join(tex_dir, self.influence + suffix + '.png'))
        path = os.path.join(DATA, self.species + '.json')
        s = json.load(open(path))
        s['forms'][self.influence]['size'] = self.size()
        with open(path, 'w') as f:
            json.dump(s, f, indent=2)
            f.write('\n')
        return self.size()


def variant(hexcol, role, enraged, mutated):
    r, g, b = int(hexcol[1:3], 16), int(hexcol[3:5], 16), int(hexcol[5:7], 16)
    if role == 'eye' and enraged: return (255, 26, 26)
    if mutated and role in ('body', 'accent'):
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        h = (h + (0.45 if role == 'body' else 0.2)) % 1
        r, g, b = (round(c * 255) for c in colorsys.hls_to_rgb(h, l, min(1, s * 1.1 + 0.05)))
    if enraged and role == 'body':
        r, g, b = min(255, round(r * 0.95 + 14)), round(g * 0.82), round(b * 0.82)
    return (r, g, b)


def main(only):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import model_defs
    built = []
    for fn in model_defs.ALL:
        m = fn()
        if only and m.species not in only: continue
        size = m.write()
        lo, hi = m.bounds()
        cubes = sum(len(b.get('cubes', [])) for b in m.bones)
        built.append(f'  {m.species}/{m.influence}: {len(m.bones)} bones, {cubes} cubes, '
                     f'{hi[1] - max(lo[1], 0):.0f} px tall, {max(hi[0] - lo[0], hi[2] - lo[2]):.0f} px long, size {size}, '
                     f'animations {", ".join(m.anims)}')
    print(f'{len(built)} models built')
    print('\n'.join(built))


if __name__ == '__main__':
    main(set(sys.argv[1:]))
