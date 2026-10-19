"""The creatures tools/build_models.py builds: wave 2 of the creature list, the Iron Golem and the Whale.

Each function returns a Model. Sizes are in pixels (16 to a block); the library's medium creatures are about
34 px (two blocks) tall, small ones 12-20 px, large ones 50-80 px. Palette roles: "body" colours shift for Mutated
creatures (and darken when Enraged), "accent" colours shift a little, "eye" colours turn red when Enraged and
"fixed" ones (teeth, claws, metal) never change.
"""
from build_models import Model, wave, spin

# ---------------------------------------------------------------- shared skeletons


def humanoid(m, H, c, root=None, wide=1.0, legs=True, head=1.0):
    """Legs, body, arms and head for a creature H px tall. c names the palette colours: leg, foot, torso, belt, arm,
    hand, head, eye (any may repeat). Returns the key heights and widths for decoration."""
    leg_h = round(H * 0.4)
    torso_h = round(H * 0.34)
    head_h = round((H - leg_h - torso_h) * head)
    tw, td = H * 0.34 * wide, H * 0.19 * wide
    lw = tw / 2 - 0.4
    aw = max(2.0, tw * 0.3)
    arm_h = torso_h * 1.12
    hip, sh = leg_h, leg_h + torso_h
    if legs:
        for side, name in ((-1, 'leg_left'), (1, 'leg_right')):
            x = side * tw / 4
            m.bone(name, (x, hip, 0), root)
            m.box(name, x, 0, 0, lw, leg_h, td * 0.8, c['leg'])
            m.box(name, x, 0, -0.6, lw + 0.4, max(1.5, leg_h * 0.15), td * 0.8 + 1.4, c['foot'])
    m.bone('body', (0, hip, 0), root)
    m.box('body', 0, hip, 0, tw, torso_h, td, c['torso'])
    m.box('body', 0, hip, 0, tw + 0.4, max(1.2, torso_h * 0.12), td + 0.4, c['belt'])
    for side, name in ((-1, 'arm_left'), (1, 'arm_right')):
        x = side * (tw / 2 + aw / 2)
        m.bone(name, (x, sh - 1, 0), 'body')
        m.box(name, x, sh - arm_h, 0, aw, arm_h, aw, c['arm'])
        m.box(name, x, sh - arm_h - 0.2, 0, aw + 0.3, max(1.5, arm_h * 0.16), aw + 0.3, c['hand'])
    hw = max(head_h, tw * 0.7)
    m.bone('head', (0, sh, 0), 'body')
    m.box('head', 0, sh, 0, hw, head_h, hw * 0.95, c['head'])
    m.pair('head', 'head', hw * 0.22, sh + head_h * 0.42, -hw * 0.475 - 0.2, hw * 0.17, max(1, head_h * 0.15), 0.5, c['eye'])
    return dict(hip=hip, sh=sh, top=sh + head_h, tw=tw, td=td, hw=hw, head_h=head_h, aw=aw, arm_h=arm_h, leg_h=leg_h)


def biped_idle(legs=True, arm=14, leg=20, extra=None):
    b = {'body': {'rotation': [wave(1.5, 144), '0', '0']},
         'head': {'rotation': ['0', wave(9, 90, 57), '0']},
         'arm_left': {'rotation': [wave(arm, 288, 180), '0', '0']},
         'arm_right': {'rotation': [wave(arm, 288, 0), '0', '0']}}
    if legs:
        b['leg_left'] = {'rotation': [wave(leg, 288, 0), '0', '0']}
        b['leg_right'] = {'rotation': [wave(leg, 288, 180), '0', '0']}
    b.update(extra or {})
    return b


def quadruped(m, L, leg_h, body_h, W, c, root=None, head=8, neck=True, snout=0, tail=6, lw=None):
    """Four legs, a body L long and W wide, a neck and head at the front (-z) and a tail. c: leg, foot, body, belly,
    head, snout, eye, tail. Returns key positions."""
    lw = lw or max(3.0, W * 0.32)
    top = leg_h + body_h
    for name, sx, sz in (('front_left_leg', -1, -1), ('front_right_leg', 1, -1), ('back_left_leg', -1, 1), ('back_right_leg', 1, 1)):
        x, z = sx * (W / 2 - lw / 2), sz * (L / 2 - lw / 2 - 1)
        m.bone(name, (x, leg_h, z), root)
        m.box(name, x, 0, z, lw, leg_h + 1, lw, c['leg'])
        m.box(name, x, 0, z - 0.4, lw + 0.4, max(1.2, leg_h * 0.16), lw + 1.0, c['foot'])
    m.bone('body', (0, leg_h, 0), root)
    m.box('body', 0, leg_h, 0, W, body_h, L, c['body'])
    m.box('body', 0, leg_h - 0.3, 0, W * 0.8, max(1.2, body_h * 0.2), L * 0.75, c['belly'])
    hz = -L / 2
    hy = leg_h + body_h * 0.55
    if neck:
        m.bone('neck', (0, hy, hz), 'body')
        m.box('neck', 0, hy - head * 0.3, hz - head * 0.25, head * 0.7, head * 0.8, head * 0.6, c['body'])
        hz -= head * 0.5
        hy += head * 0.25
        parent = 'neck'
    else:
        parent = 'body'
    m.bone('head', (0, hy, hz), parent)
    m.box('head', 0, hy, hz - head / 2, head, head * 0.85, head, c['head'])
    if snout:
        m.box('head', 0, hy, hz - head - snout / 2 + 0.2, head * 0.55, head * 0.45, snout, c['snout'])
    m.pair('head', 'head', head * 0.26, hy + head * 0.5, hz - head - 0.2, head * 0.16, head * 0.14, 0.5, c['eye'])
    if tail:
        m.bone('tail', (0, top - 1.5, L / 2), 'body')
        m.box('tail', 0, top - 2.5, L / 2 + tail / 2, max(1.5, W * 0.15), max(1.5, W * 0.15), tail, c['tail'])
    return dict(top=top, hz=hz, hy=hy, head=head)


def quad_idle(leg=20, freq=324, extra=None):
    b = {'front_left_leg': {'rotation': [wave(leg, freq, 0), '0', '0']},
         'front_right_leg': {'rotation': [wave(leg, freq, 180), '0', '0']},
         'back_left_leg': {'rotation': [wave(leg, freq, 180), '0', '0']},
         'back_right_leg': {'rotation': [wave(leg, freq, 0), '0', '0']},
         'body': {'rotation': [wave(1.5, 180), '0', '0']},
         'head': {'rotation': [wave(4, 162), wave(6, 108), '0']},
         'tail': {'rotation': ['0', wave(14, 180), '0']}}
    b.update(extra or {})
    return b


def wings(m, parent, x, y, z, span, chord, c_main, c_tip, c_edge=None, membrane=False):
    """A pair of spread wings: wing_left / wingtip_left (towards -x) and the right ones, reaching back along +z."""
    half = span / 2
    for side, w, t in ((-1, 'wing_left', 'wingtip_left'), (1, 'wing_right', 'wingtip_right')):
        m.bone(w, (side * x, y, z), parent, rot=(0, 0, -side * 12))
        m.bone(t, (side * (x + half), y, z), w, rot=(0, 0, -side * 6))
        cx = side * (x + half / 2)
        m.box(w, cx, y - 0.5, z + 0.6, half, 1.2, 1.2, c_edge or c_main)  # the arm of the wing
        if membrane:
            m.box(w, cx, y - 0.2, z + chord / 2, half, 0.5, chord, c_main)
        else:
            for k in range(3):  # feathers, longer towards the tip
                m.box(w, side * (x + half * (k + 0.5) / 3), y - 0.3, z + chord * (0.4 + 0.1 * k), half / 3 + 0.2, 0.7, chord * (0.8 + 0.2 * k), c_main)
        tx = side * (x + half * 1.5)
        m.box(t, tx, y - 0.4, z + 0.5, half, 1.0, 1.0, c_edge or c_main)
        if membrane:
            m.box(t, tx, y - 0.2, z + chord * 0.4, half, 0.5, chord * 0.8, c_tip)
        else:
            for k in range(3):
                m.box(t, side * (x + half + half * (k + 0.5) / 3), y - 0.2, z + chord * (0.55 - 0.08 * k), half / 3 + 0.2, 0.6,
                      chord * (1.1 - 0.15 * k), c_tip)


def wing_anim(b, amp=26, freq=216):
    b['wing_left'] = {'rotation': ['0', '0', f'-(-{amp}*math.sin(query.life_time*1*{freq}+0))']}
    b['wing_right'] = {'rotation': ['0', '0', f'-({amp}*math.sin(query.life_time*1*{freq}+0))']}
    b['wingtip_left'] = {'rotation': ['0', '0', f'-(-{amp * 0.7:.1f}*math.sin(query.life_time*1*{freq}+-40))']}
    b['wingtip_right'] = {'rotation': ['0', '0', f'-({amp * 0.7:.1f}*math.sin(query.life_time*1*{freq}+-40))']}
    return b


def fly_from(idle, pitch, amp=58, freq=360):
    """The fly animation: the idle one leaning forward (fly_root) and flapping harder."""
    b = {k: dict(v) for k, v in idle.items()}
    wing_anim(b, amp, freq)
    b['fly_root'] = {'rotation': [wave(1.7, 180, 0, pitch), '0', '0']}
    for leg in ('leg_left', 'leg_right'):
        if leg in b: b[leg] = {'rotation': ['10', '0', '0']}
    return b


def chain(m, parent, n, x, y, z, seg, w, h, c, stripe=None, taper=0.9, name='seg'):
    """A body of n segments reaching back along +z from (x, y, z), each a little smaller. Returns the bone names."""
    names = []
    for i in range(n):
        b = m.bone(f'{name}_{i + 1}', (x, y + h / 2, z), parent)
        m.box(b, x, y, z + seg / 2, w, h, abs(seg) + 0.4, c)
        if stripe and i % 2 == 0:
            m.box(b, x, y + h - 0.2, z + seg / 2, w * 0.6, 0.6, abs(seg) * 0.6, stripe)
        names.append(b)
        parent, z = b, z + seg
        y += (h - h * taper) / 2
        w, h = w * taper, h * taper
    return names


def chain_anim(names, amp=10, freq=180, step=45, axis=1):
    out = {}
    for i, n in enumerate(names):
        r = ['0', '0', '0']
        r[axis] = wave(amp, freq, -i * step)
        out[n] = {'rotation': r}
    return out


def spider(m, root, L, W, H, leg_reach, c, legs=4):
    """A spider: head and abdomen, eight stepped legs (legs per side). c: body, abdomen, mark, leg, joint, eye."""
    hip = H
    m.bone('body', (0, hip, 0), root)
    m.box('body', 0, hip - 2, -L * 0.25, W * 0.6, H * 0.45, L * 0.4, c['body'])
    m.bone('abdomen', (0, hip, L * 0.05), 'body')
    m.box('abdomen', 0, hip - 2.5, L * 0.3, W, H * 0.75, L * 0.6, c['abdomen'])
    m.box('abdomen', 0, hip - 2.5 + H * 0.75 - 0.2, L * 0.3, W * 0.4, 0.6, L * 0.4, c['mark'])
    for i in range(4):  # eyes: two big, two small
        m.box('body', (-1 if i % 2 == 0 else 1) * (0.9 + (i // 2) * 1.3), hip - 2 + H * 0.3 - (i // 2) * 0.8,
              -L * 0.45 - 0.3, 1.0 if i < 2 else 0.7, 1.0 if i < 2 else 0.7, 0.5, c['eye'])
    names = []
    for side, label in ((-1, 'left'), (1, 'right')):
        for k in range(legs):
            z = -L * 0.35 + k * (L * 0.5 / (legs - 1))
            b = m.bone(f'leg_{label}_{k + 1}', (side * W * 0.3, hip - 1, z), 'body')
            x0 = side * W * 0.3
            m.cube(b, (min(x0, x0 + side * leg_reach * 0.45), hip - 1.6, z - 0.6), (leg_reach * 0.45, 1.2, 1.2), c['leg'])  # out
            kx = x0 + side * leg_reach * 0.45
            m.box(b, kx, hip - 1.6, z, 1.6, 2.6, 1.6, c['joint'])  # knee
            m.cube(b, (min(kx, kx + side * leg_reach * 0.4), hip - 0.4, z - 0.5), (leg_reach * 0.4, 1.0, 1.0), c['leg'])  # up and out
            fx = kx + side * leg_reach * 0.4
            m.box(b, fx, 0, z, 1.1, hip + 0.4, 1.1, c['leg'])  # down to the ground
            names.append(b)
    return names


def spider_idle(names):
    b = {'body': {'rotation': [wave(1.2, 200), '0', '0']}, 'abdomen': {'rotation': [wave(3, 160), '0', '0']}}
    for i, n in enumerate(names):
        b[n] = {'rotation': ['0', wave(9, 340, (i % 4) * 90 + (180 if 'right' in n else 0)), '0']}
    return b


def bob(amp=1.0, freq=150):
    return {'position': ['0', wave(amp, freq), '0']}


# ---------------------------------------------------------------- fire


def embering_flame():
    m = Model('embering_flame', 'fire', [
        ('core', '#ffb43a', 'body'), ('flame', '#ff7a1a', 'body'), ('deep', '#d93a14', 'body'), ('tip', '#ffe27a', 'accent'),
        ('coal', '#2b1d1a', 'fixed'), ('eye', '#fff6c8', 'eye'), ('ember', '#ff5a1f', 'accent')])
    m.bone('fly_root', (0, 8, 0))
    m.bone('body', (0, 4, 0), 'fly_root')
    m.box('body', 0, 3, 0, 5, 3, 5, 'coal')  # the coal it burns from
    m.box('body', 0, 5, 0, 7, 6, 7, 'deep')
    m.box('body', 0, 6, 0, 8, 6, 6, 'flame')
    m.box('body', 0, 7, 0, 6, 6, 8, 'flame')
    m.box('body', 0, 6.5, -0.5, 5.5, 6.5, 6, 'core')
    m.pair('body', 'body', 1.5, 10, -4.3, 1.2, 1.6, 0.5, 'eye')
    m.box('body', 0, 8.6, -4.3, 2.2, 0.6, 0.5, 'coal')  # mouth
    m.bone('flames', (0, 12, 0), 'body')
    m.box('flames', 0, 12, 0, 5, 3, 5, 'flame')
    m.box('flames', -1, 14.5, 0.5, 3, 3, 3, 'core')
    m.box('flames', 1.3, 15, -0.8, 2, 4, 2, 'tip')
    m.box('flames', -1.2, 17, 0.6, 1.4, 2.5, 1.4, 'tip')
    m.bone('embers', (0, 9, 0), 'fly_root')
    for x, y, z in ((6, 7, 0), (-5.5, 11, 2), (0.5, 4, 6), (-1, 13, -6)):
        m.box('embers', x, y, z, 1.2, 1.2, 1.2, 'ember')
    idle = {'fly_root': bob(1.2, 160), 'flames': {'rotation': [wave(6, 300), '0', wave(8, 240, 90)]},
            'body': {'rotation': ['0', wave(6, 120), '0']}, 'embers': {'rotation': ['0', spin(150), '0']}}
    m.anim('idle', idle)
    return m


def magma_viper():
    m = Model('magma_viper', 'fire', [
        ('scale', '#3a2a26', 'body'), ('dark', '#211714', 'body'), ('magma', '#ff6a1c', 'accent'), ('glow', '#ffb648', 'accent'),
        ('belly', '#7a3a22', 'body'), ('eye', '#ffe14a', 'eye'), ('fang', '#f2ead8', 'fixed'), ('tongue', '#c3242c', 'fixed')])
    m.bone('body', (0, 2, -8))
    m.box('body', 0, 0, -8, 4.5, 4, 5, 'scale')
    m.box('body', 0, 3.8, -8, 2.2, 0.6, 4, 'magma')
    segs = chain(m, 'body', 7, 0, 0, -5.5, 5, 4.4, 3.8, 'scale', 'magma', taper=0.88)
    for s in segs[1::2]:
        b = m.by_name[s]
        x, y, z = b['pivot']
        m.box(s, 0, 0, z + 2, 3.4, 0.4, 3, 'belly')
    # the head raised at the front
    m.bone('neck', (0, 3, -10), 'body')
    m.box('neck', 0, 2, -11, 3.6, 6, 3.4, 'scale')
    m.box('neck', 0, 2.2, -12.8, 2.8, 5, 0.4, 'belly')
    m.bone('head', (0, 8, -11), 'neck')
    m.box('head', 0, 7, -14, 5.5, 3, 7, 'scale')
    m.box('head', 0, 6.6, -16.5, 4, 1.6, 3, 'dark')
    m.box('head', 0, 9.9, -13.5, 3, 0.6, 5, 'magma')
    m.pair('head', 'head', 2.1, 8.6, -15.4, 1.0, 1.0, 1.4, 'eye')
    m.pair('head', 'head', 1.1, 5.4, -17.3, 0.6, 1.6, 0.6, 'fang')
    m.box('head', 0, 6.6, -19, 0.6, 0.4, 2.2, 'tongue')
    m.box('head', 0, 10.5, -11.5, 1.6, 1.2, 1.6, 'glow')
    idle = chain_anim(segs, 9, 160, 40)
    idle['neck'] = {'rotation': [wave(4, 120), wave(10, 90), '0']}
    idle['head'] = {'rotation': [wave(5, 200, 30), '0', '0']}
    m.anim('idle', idle)
    return m


def magma_dryad():
    m = Model('magma_dryad', 'fire', [
        ('bark', '#3b2b27', 'body'), ('basalt', '#262124', 'body'), ('crack', '#ff6a1c', 'accent'), ('glow', '#ffc04d', 'accent'),
        ('leaf', '#e2401c', 'body'), ('leaf2', '#ff8a2a', 'body'), ('eye', '#fff1a0', 'eye'), ('root', '#4a3127', 'body'),
        ('face', '#7a5240', 'body')])
    c = dict(leg='bark', foot='root', torso='bark', belt='basalt', arm='bark', hand='basalt', head='face', eye='eye')
    d = humanoid(m, 36, c, wide=0.9)
    hip, sh, top, tw, td = d['hip'], d['sh'], d['top'], d['tw'], d['td']
    # glowing cracks in the bark
    m.box('body', -1.5, hip + 3, -td / 2 - 0.15, 0.8, 6, 0.3, 'crack')
    m.box('body', 1.2, hip + 6, -td / 2 - 0.15, 0.8, 4, 0.3, 'glow')
    m.box('body', 0, sh - 3, -td / 2 - 0.15, 4, 0.8, 0.3, 'crack')
    for name in ('leg_left', 'leg_right'):
        x = m.by_name[name]['pivot'][0]
        m.box(name, x, 4, -td * 0.4 - 0.15, 0.8, 5, 0.3, 'crack')
        m.box(name, x + (1.2 if x < 0 else -1.2), 0, -1, 1.2, 1.2, 4, 'root')  # roots for toes
    for name in ('arm_left', 'arm_right'):
        x = m.by_name[name]['pivot'][0]
        s = -1 if x < 0 else 1
        m.box(name, x + s * 1.6, sh - 5, 0, 2.5, 1, 1, 'root')  # twig
        m.box(name, x + s * 2.6, sh - 4.2, 0, 1, 1, 1, 'leaf2')
    # a crown of burning leaves
    hw = d['hw']
    for i, (x, z, h, col) in enumerate(((-3, 0, 4, 'leaf'), (0, -1, 6, 'leaf2'), (3, 0, 4, 'leaf'), (-1.5, 2, 5, 'leaf2'),
                                         (1.5, 2.5, 5, 'leaf'), (0, 3.5, 3, 'glow'), (-4, 2.5, 3, 'leaf2'), (4, 2.5, 3, 'leaf2'))):
        m.box('head', x, top - 1, z, 2.6, h, 2.6, col)
    m.box('head', 0, top - 3, hw / 2 + 0.6, hw * 0.9, 6, 1.6, 'leaf')  # leaves down the back
    m.box('head', 0, sh + 1.5, -hw * 0.475 - 0.2, 2, 0.5, 0.4, 'crack')  # mouth
    idle = biped_idle(arm=12, leg=16)
    m.anim('idle', idle)
    return m


# ---------------------------------------------------------------- frost


def polar_bear_folk():
    m = Model('polar_bear_folk', 'frost', [
        ('fur', '#eeeef0', 'body'), ('shade', '#c9cdd6', 'body'), ('snout', '#e6dcc6', 'body'), ('nose', '#1c1c22', 'fixed'),
        ('eye', '#1c1c22', 'eye'), ('scarf', '#3f7fbf', 'accent'), ('scarf2', '#9ad1f5', 'accent'), ('claw', '#5a5450', 'fixed')])
    c = dict(leg='fur', foot='shade', torso='fur', belt='scarf', arm='fur', hand='shade', head='fur', eye='eye')
    d = humanoid(m, 34, c, wide=1.25, head=0.9)
    sh, top, hw, td, hip = d['sh'], d['top'], d['hw'], d['td'], d['hip']
    m.box('body', 0, hip + 2, -td / 2 - 0.2, d['tw'] * 0.6, d['sh'] - hip - 4, 0.4, 'shade')  # belly
    m.box('body', 0, sh - 2.2, 0, d['tw'] + 0.8, 2.4, td + 0.8, 'scarf')  # scarf
    m.box('body', -2.5, sh - 7, -td / 2 - 0.6, 2, 5, 0.8, 'scarf2')
    m.box('head', 0, sh + 1, -hw * 0.475 - 1.6, hw * 0.45, d['head_h'] * 0.4, 3.2, 'snout')
    m.box('head', 0, sh + 2.6, -hw * 0.475 - 3.3, 1.8, 1.2, 0.6, 'nose')
    m.pair('head', 'head', hw * 0.35, top - 0.4, 0.5, 2.2, 2.2, 1.2, 'fur')  # ears
    for name in ('arm_left', 'arm_right'):
        x = m.by_name[name]['pivot'][0]
        for k in (-0.8, 0.8):
            m.box(name, x + k, sh - d['arm_h'] - 1.2, -1.4, 0.6, 1.2, 0.6, 'claw')
    m.anim('idle', biped_idle(arm=10, leg=14))
    return m


def helebrog():
    m = Model('helebrog', 'frost', [
        ('fur', '#6b4a32', 'body'), ('dark', '#4a3122', 'body'), ('belly', '#8c6a4c', 'body'), ('frost', '#cfeaf7', 'accent'),
        ('ice', '#7fc6e6', 'accent'), ('fang', '#f4efe2', 'fixed'), ('eye', '#a8e4ff', 'eye'), ('nose', '#1e1614', 'fixed')])
    c = dict(leg='dark', foot='nose', body='fur', belly='belly', head='fur', snout='belly', eye='eye', tail='dark')
    q = quadruped(m, 34, 12, 16, 18, c, head=11, snout=4, tail=3, lw=6)
    top, hz, hy, h = q['top'], q['hz'], q['hy'], q['head']
    m.box('body', 0, top - 1, -6, 16, 4, 12, 'fur')  # shoulder hump
    m.box('body', 0, top + 2.6, -6, 10, 1.2, 8, 'frost')  # frost on the hump
    for z, hgt in ((-9, 4), (-5, 5), (-1, 3.5), (3, 3), (7, 2)):
        m.box('body', 0, top + (3 if z < 2 else 0), z, 2, hgt, 2, 'ice')  # ice spikes down the back
    m.pair('head', 'head', 1.6, hy - 6, hz - h - 2.2, 1.2, 8, 1.2, 'fang')  # the sabre teeth
    m.box('head', 0, hy + 1.6, hz - h - 4.3, 2, 1.2, 0.6, 'nose')
    m.pair('head', 'head', h * 0.38, hy + h * 0.85, hz - h * 0.4, 2.4, 2.4, 1.2, 'dark')  # ears
    m.pair('head', 'head', h * 0.38, hy + h * 0.85 + 2.2, hz - h * 0.4, 1.4, 0.8, 1.0, 'frost')
    for name in ('front_left_leg', 'front_right_leg'):
        x, _, z = m.by_name[name]['pivot']
        m.box(name, x, 0, z - 3.6, 4.5, 1.2, 1.2, 'frost')  # icy claws
    m.anim('idle', quad_idle(14, 260))
    return m


def crystalline_construct():
    m = Model('crystalline_construct', 'frost', [
        ('ice', '#a9dcf2', 'body'), ('ice2', '#7cc3e8', 'body'), ('deep', '#3d84c6', 'body'), ('white', '#eef9ff', 'accent'),
        ('core', '#5ef0ff', 'accent'), ('eye', '#e8ffff', 'eye'), ('stone', '#59677a', 'fixed')])
    c = dict(leg='ice2', foot='stone', torso='ice', belt='deep', arm='ice2', hand='deep', head='ice', eye='eye')
    d = humanoid(m, 46, c, wide=1.35, head=0.75)
    hip, sh, top, tw, td, hw = d['hip'], d['sh'], d['top'], d['tw'], d['td'], d['hw']
    m.box('body', 0, hip + 5, -td / 2 - 0.4, 6, 6, 1, 'deep')
    m.box('body', 0, hip + 6, -td / 2 - 0.8, 4, 4, 1, 'core')  # the glowing core
    for side in (-1, 1):  # crystal spikes on the shoulders
        x = side * (tw / 2 + d['aw'] / 2)
        arm = 'arm_left' if side < 0 else 'arm_right'
        m.box(arm, x, sh, -1, 3, 5, 3, 'white')
        m.box(arm, x + side * 1.5, sh + 2, 1.5, 2, 7, 2, 'ice')
        m.box(arm, x - side * 0.5, sh + 1, 2.5, 1.6, 4, 1.6, 'deep')
        m.box(arm, x, sh - d['arm_h'] - 2.5, 0, d['aw'] + 1.6, 3, d['aw'] + 1.6, 'ice')  # crystal fists
    for x, z, h in ((-2, 0, 5), (1.5, -1, 7), (0, 2, 4), (3, 1.5, 3)):
        m.box('head', x, top - 0.5, z, 2, h, 2, 'white' if h > 4 else 'ice2')
    m.box('head', 0, sh + d['head_h'] * 0.3, -hw * 0.475 - 0.3, hw * 0.7, 1, 0.5, 'deep')
    m.anim('idle', biped_idle(arm=9, leg=12, extra={'body': {'rotation': [wave(1, 120), wave(3, 90), '0']}}))
    return m


# ---------------------------------------------------------------- water


def water_gull():
    m = Model('water_gull', 'water', [
        ('white', '#f4f6f8', 'body'), ('grey', '#9aa6b2', 'body'), ('tip', '#2a2f36', 'fixed'), ('beak', '#f2b632', 'fixed'),
        ('leg', '#f08a3c', 'fixed'), ('eye', '#1a1a1a', 'eye'), ('wave', '#3f9fd6', 'accent'), ('wave2', '#a8e0f5', 'accent')])
    m.bone('fly_root', (0, 6, 0))
    for side, name in ((-1, 'leg_left'), (1, 'leg_right')):
        m.bone(name, (side * 1.5, 4, 0.5), 'fly_root')
        m.box(name, side * 1.5, 0, 0.5, 0.8, 4, 0.8, 'leg')
        m.box(name, side * 1.5, 0, -0.3, 1.6, 0.5, 2, 'leg')
    m.bone('body', (0, 4, 0), 'fly_root')
    m.box('body', 0, 4, 0.5, 5, 5, 9, 'white')
    m.box('body', 0, 7.5, 1.5, 5.2, 1.4, 7, 'grey')
    m.box('body', 0, 6, 6, 3.5, 2, 4, 'grey')  # tail
    m.box('body', 0, 6.5, 8, 3, 1, 2, 'tip')
    m.bone('head', (0, 9, -3), 'body')
    m.box('head', 0, 8, -4.5, 4, 4, 4, 'white')
    m.box('head', 0, 9, -7.4, 1.4, 1, 2.4, 'beak')
    m.box('head', 0, 8.5, -7, 1.2, 0.6, 1.6, 'beak')
    m.pair('head', 'head', 2.05, 10.4, -5, 0.3, 0.9, 0.9, 'eye')
    m.box('head', 0, 12, -4.5, 1.2, 1.2, 3, 'wave')  # a little crest of sea-spray
    m.box('head', 0, 12.6, -3.2, 1, 1, 1.6, 'wave2')
    wings(m, 'body', 2.5, 8, -1, 18, 5, 'grey', 'tip', 'white')
    idle = wing_anim({'body': {'rotation': [wave(2, 150), '0', '0']}, 'head': {'rotation': [wave(6, 220), wave(10, 90), '0']},
                      'leg_left': {'rotation': [wave(18, 300), '0', '0']}, 'leg_right': {'rotation': [wave(18, 300, 180), '0', '0']}}, 14, 200)
    m.anim('idle', idle)
    m.anim('fly', fly_from(idle, 12, 50, 380))
    return m


def nautiloper():
    m = Model('nautiloper', 'water', [
        ('shell', '#efe2c4', 'body'), ('stripe', '#a0552e', 'body'), ('inner', '#f6c9b0', 'body'), ('skin', '#c96f5a', 'body'),
        ('tent', '#e08c72', 'body'), ('eye', '#1a2a33', 'eye'), ('pearl', '#bdf3ff', 'accent'), ('rim', '#7a3f22', 'body')])
    m.bone('body', (0, 6, 0))
    # the coiled shell: rings getting smaller as they curl up and back
    for i, (y, z, s) in enumerate(((2, 3, 14), (8, 6, 11), (12, 3.5, 8), (13.5, 0, 6), (11, -1.5, 4.5))):
        m.box('body', 0, y, z, 9 - i * 0.8, s * 0.75, s * 0.75, 'shell' if i % 2 == 0 else 'stripe')
    for z in (-1, 3, 7):
        m.box('body', 0, 2.2, z, 9.2, 0.8, 1, 'stripe')
    m.box('body', 0, 3, -3, 8, 8, 1.2, 'rim')  # the shell's mouth
    m.box('body', 0, 3.6, -3.4, 6.5, 6.5, 1, 'inner')
    m.bone('head', (0, 6, -4), 'body')
    m.box('head', 0, 3.5, -5, 6, 5, 3, 'skin')
    m.box('head', 0, 8.2, -4.6, 5, 1.5, 2.4, 'skin')  # hood
    m.pair('head', 'head', 2.6, 6, -6, 1.6, 1.6, 1, 'eye')
    names = []
    for i, (x, y) in enumerate(((-2.2, 4.5), (-0.8, 3.5), (0.8, 3.5), (2.2, 4.5), (-1.5, 2.2), (1.5, 2.2))):
        n = m.bone(f'tentacle_{i + 1}', (x, y, -6.5), 'head')
        m.box(n, x, y - 0.5, -9, 1, 1, 5, 'tent')
        m.box(n, x, y - 0.6, -11.6, 0.8, 0.8, 1.2, 'skin')
        names.append(n)
    m.box('body', 0, 17.2, 2, 1.4, 1.4, 1.4, 'pearl')
    idle = {'body': {'rotation': [wave(3, 120), '0', wave(2, 90)], 'position': ['0', wave(0.8, 120), '0']},
            'head': {'rotation': [wave(4, 150), '0', '0']}}
    for i, n in enumerate(names):
        idle[n] = {'rotation': [wave(12, 260, i * 60), wave(8, 200, i * 45), '0']}
    m.anim('idle', idle)
    return m


def coral_crawler():
    m = Model('coral_crawler', 'water', [
        ('shell', '#2f6f8f', 'body'), ('shell2', '#3f8fb0', 'body'), ('under', '#c9a37a', 'body'), ('coral1', '#ff6f91', 'accent'),
        ('coral2', '#ffa64d', 'accent'), ('coral3', '#b46cff', 'accent'), ('claw', '#1f4e66', 'body'), ('eye', '#111111', 'eye'),
        ('stalk', '#c9a37a', 'body'), ('lapis', '#2a4fd1', 'fixed')])
    m.bone('body', (0, 6, 0))
    m.box('body', 0, 4, 0, 18, 6, 14, 'shell')
    m.box('body', 0, 9.8, 0, 15, 1.6, 11, 'shell2')
    m.box('body', 0, 3.6, 0, 14, 1, 10, 'under')
    # coral growing on its back
    for x, z, h, col in ((-4, -2, 6, 'coral1'), (-5.5, 1.5, 4, 'coral2'), (2, 0, 8, 'coral3'), (4.5, 3, 5, 'coral1'),
                         (0, 4, 4, 'coral2'), (-1.5, -3.5, 3, 'coral3')):
        m.box('body', x, 11, z, 1.6, h, 1.6, col)
        m.box('body', x + 1.2, 11 + h * 0.55, z, 2.2, 1.2, 1.2, col)
    m.box('body', 4, 11, -3.5, 1.5, 1.5, 1.5, 'lapis')
    m.pair('body', 'body', 2.2, 10, -6.5, 1, 4, 1, 'stalk')  # eye stalks
    m.pair('body', 'body', 2.2, 13.5, -6.5, 1.8, 1.8, 1.8, 'eye')
    names = []
    for side, label in ((-1, 'left'), (1, 'right')):
        for k in range(3):
            z = -3 + k * 3.5
            n = m.bone(f'leg_{label}_{k + 1}', (side * 8, 6, z), 'body')
            m.box(n, side * 11, 5, z, 6, 1.4, 1.4, 'claw')
            m.box(n, side * 14, 0, z, 1.4, 6, 1.4, 'claw')
            names.append(n)
        cl = m.bone(f'claw_{label}', (side * 6, 6, -7), 'body')
        m.box(cl, side * 8, 5, -10, 2, 2, 5, 'claw')
        m.box(cl, side * 9, 4, -15, 5, 4, 5, 'shell2')  # pincer
        m.box(cl, side * 8, 7.5, -16.5, 2.5, 1.5, 3, 'shell')
    idle = {'body': {'rotation': ['0', wave(4, 140), wave(2, 220)]},
            'claw_left': {'rotation': [wave(10, 200), '0', '0']}, 'claw_right': {'rotation': [wave(10, 200, 140), '0', '0']}}
    for i, n in enumerate(names):
        idle[n] = {'rotation': ['0', wave(10, 360, i * 70), '0']}
    m.anim('idle', idle)
    return m


# ---------------------------------------------------------------- nature


def poisonous_frog():
    m = Model('poisonous_frog', 'nature', [
        ('skin', '#7ad13a', 'body'), ('spot', '#1b1b1b', 'fixed'), ('belly', '#f1d23a', 'body'), ('dark', '#3f8a1e', 'body'),
        ('eye', '#ffcc22', 'eye'), ('pupil', '#111111', 'fixed'), ('toe', '#ff7a2a', 'accent')])
    m.bone('body', (0, 3, 0))
    m.box('body', 0, 2, 0.5, 8, 5, 9, 'skin')
    m.box('body', 0, 1.6, 0, 6, 1, 7, 'belly')
    for x, z in ((-2, -1), (2.5, 1.5), (-1, 3), (1.5, -2.5)):
        m.box('body', x, 6.9, z, 1.8, 0.5, 1.8, 'spot')
    m.bone('head', (0, 5, -4), 'body')
    m.box('head', 0, 3, -5.5, 8.5, 4, 4, 'skin')
    m.box('head', 0, 3, -7.6, 7, 1, 0.5, 'dark')  # mouth line
    m.pair('head', 'head', 2.6, 6.5, -5.5, 2.6, 2.6, 2.6, 'eye')
    m.pair('head', 'head', 2.6, 7.3, -6.9, 1.2, 1.2, 0.4, 'pupil')
    for side, name in ((-1, 'leg_left'), (1, 'leg_right')):
        m.bone(name, (side * 4, 3, 3), 'body')
        m.box(name, side * 5, 1, 3, 2.5, 3, 5, 'dark')
        m.box(name, side * 5.5, 0, 0, 2.8, 0.8, 2.6, 'toe')
        arm = m.bone('arm_' + name[4:], (side * 3, 3, -3), 'body')
        m.box(arm, side * 3.6, 0, -3.5, 1.4, 3, 1.4, 'skin')
        m.box(arm, side * 3.8, 0, -4.2, 2, 0.6, 2, 'toe')
    idle = {'body': {'rotation': [wave(2, 120), '0', '0'], 'position': ['0', wave(0.4, 240), '0']},
            'head': {'rotation': [wave(3, 240), '0', '0']},
            'leg_left': {'rotation': [wave(6, 240), '0', '0']}, 'leg_right': {'rotation': [wave(6, 240, 180), '0', '0']}}
    m.anim('idle', idle)
    return m


def oxe_turtle():
    m = Model('oxe_turtle', 'nature', [
        ('shell', '#5a6b2e', 'body'), ('plate', '#7b8a3a', 'body'), ('moss', '#4f9a2c', 'accent'), ('skin', '#8a6a48', 'body'),
        ('horn', '#e8dcc0', 'fixed'), ('eye', '#1a1a10', 'eye'), ('flower', '#ffd84a', 'accent'), ('belly', '#c9b080', 'body'),
        ('nose', '#4a3626', 'fixed')])
    c = dict(leg='skin', foot='nose', body='shell', belly='belly', head='skin', snout='belly', eye='eye', tail='skin')
    q = quadruped(m, 26, 7, 9, 22, c, head=9, snout=3, tail=3, lw=6)
    top, hz, hy, h = q['top'], q['hz'], q['hy'], q['head']
    m.box('body', 0, top - 1, 0, 20, 4, 24, 'shell')  # domed shell
    m.box('body', 0, top + 2.6, 0, 14, 2, 17, 'plate')
    for x, z in ((-4, -4), (4, -4), (0, 1), (-4, 6), (4, 6)):
        m.box('body', x, top + 4.2, z, 4, 0.8, 4, 'shell')
    m.box('body', -3, top + 4.8, 2, 7, 1.2, 6, 'moss')
    m.box('body', 4, top + 4.8, -2, 1.2, 2, 1.2, 'flower')
    m.box('body', 0, 7, 0, 23, 1.5, 27, 'plate')  # the shell's rim
    # ox horns
    for side in (-1, 1):
        m.box('head', side * 6, hy + h * 0.65, hz - h * 0.6, 5, 1.6, 1.6, 'horn')
        m.box('head', side * 8, hy + h * 0.65 + 1.4, hz - h * 0.6, 1.4, 3, 1.4, 'horn')
    m.pair('head', 'head', 1, hy + 1.2, hz - h - 3.3, 0.8, 0.8, 0.5, 'nose')
    m.anim('idle', quad_idle(10, 200, {'head': {'rotation': [wave(3, 120), wave(10, 70), '0']}}))
    return m


def leaf_web_spider():
    m = Model('leaf_web_spider', 'nature', [
        ('leaf', '#3f8f2a', 'body'), ('leaf2', '#62b83a', 'body'), ('vein', '#a7d95a', 'accent'), ('twig', '#5a3d22', 'body'),
        ('joint', '#3b2716', 'body'), ('eye', '#ff9f1a', 'eye'), ('silk', '#eef2e6', 'fixed')])
    c = dict(body='leaf', abdomen='leaf2', mark='vein', leg='twig', joint='joint', eye='eye')
    names = spider(m, None, 22, 14, 10, 16, c)
    # leaves layered on the abdomen, and silk hanging from it
    for x, z, w in ((-3, 4, 5), (3, 6, 5), (0, 9, 6), (0, 2, 4)):
        m.box('abdomen', x, 16, z, w, 0.8, 4, 'leaf')
    m.box('abdomen', 0, 3, 13.5, 0.4, 5, 0.4, 'silk')
    m.box('abdomen', 1.5, 4.5, 12.5, 0.4, 3.5, 0.4, 'silk')
    m.anim('idle', spider_idle(names))
    return m


# ---------------------------------------------------------------- storm


def lightning():
    m = Model('lightning', 'storm', [
        ('bolt', '#fff27a', 'body'), ('white', '#ffffff', 'accent'), ('blue', '#7fb2ff', 'accent'), ('cloud', '#5b6b8c', 'body'),
        ('cloud2', '#8796b5', 'body'), ('eye', '#1a2a5a', 'eye')])
    m.bone('fly_root', (0, 10, 0))
    m.bone('body', (0, 8, 0), 'fly_root')
    # a zig-zag body of light
    for x, y, h in ((0, 12, 5), (-1.5, 9, 4), (0.5, 6, 4), (-1, 3.5, 3.5), (0.5, 1.5, 2.5)):
        m.box('body', x, y, 0, 4.5 - (12 - y) * 0.25, h, 3, 'bolt')
    m.box('body', 0, 12.5, -1.6, 3.5, 3.5, 0.4, 'white')
    m.pair('body', 'body', 0.9, 14, -1.9, 0.9, 1.4, 0.4, 'eye')
    for side, name in ((-1, 'arm_left'), (1, 'arm_right')):
        m.bone(name, (side * 2, 13, 0), 'body')
        m.box(name, side * 4, 13, 0, 4, 1, 1, 'bolt')
        m.box(name, side * 6, 10.5, 0, 1, 3, 1, 'bolt')
        m.box(name, side * 7.5, 9.5, 0, 3, 1, 1, 'white')
    m.bone('cloud', (0, 18, 0), 'fly_root')
    for x, z, w in ((0, 0, 9), (-4, 1, 5), (4, -1, 5), (1, 3, 5)):
        m.box('cloud', x, 17, z, w, 3, 5, 'cloud' if w > 5 else 'cloud2')
    m.bone('sparks', (0, 8, 0), 'fly_root')
    for x, y, z in ((5, 4, 2), (-5, 7, -2), (2, 15, 4), (-3, 2, -3)):
        m.box('sparks', x, y, z, 0.8, 0.8, 0.8, 'blue')
    idle = {'fly_root': bob(1.5, 200), 'body': {'rotation': ['0', wave(10, 300), wave(3, 500)]},
            'arm_left': {'rotation': ['0', '0', wave(20, 400)]}, 'arm_right': {'rotation': ['0', '0', wave(20, 400, 120)]},
            'cloud': {'rotation': ['0', spin(40), '0']}, 'sparks': {'rotation': ['0', spin(-260), '0']}}
    m.anim('idle', idle)
    return m


def electric_eel():
    m = Model('electric_eel', 'storm', [
        ('skin', '#2c3a52', 'body'), ('belly', '#6a7a8c', 'body'), ('glow', '#ffe94a', 'accent'), ('glow2', '#a6f0ff', 'accent'),
        ('fin', '#3d5577', 'body'), ('eye', '#fff8b0', 'eye'), ('mouth', '#14181f', 'fixed')])
    m.bone('body', (0, 4, -10))
    m.box('body', 0, 1, -13, 5, 5, 7, 'skin')  # head
    m.box('body', 0, 0.8, -15.5, 4, 3.2, 3, 'skin')
    m.box('body', 0, 0.8, -17.1, 3.5, 0.8, 0.4, 'mouth')
    m.pair('body', 'body', 2.3, 4, -14, 0.8, 1.4, 1.4, 'eye')
    m.box('body', 0, 6, -12, 1, 1, 3, 'glow')
    segs = chain(m, 'body', 9, 0, 1, -9.5, 4.5, 4.4, 4.4, 'skin', 'glow', taper=0.93)
    for i, s in enumerate(segs):
        z = m.by_name[s]['pivot'][2]
        if i % 2 == 1: m.pair(s, s, 2.0, 2, z + 2, 0.6, 1.2, 1.2, 'glow2')  # lights down its sides
        if i < 6: m.box(s, 0, 0.6, z + 2.2, 3, 0.6, 3.6, 'belly')
    m.box(segs[-1], 0, 1.5, m.by_name[segs[-1]]['pivot'][2] + 5, 0.6, 4, 4, 'fin')  # tail fin
    m.pair('body', 'body', 3, 2.5, -10, 2, 0.6, 2, 'fin')
    idle = chain_anim(segs, 12, 200, 40)
    idle['body'] = {'rotation': ['0', wave(6, 200, 40), '0']}
    m.anim('idle', idle)
    return m


def cloud_rider():
    m = Model('cloud_rider', 'storm', [
        ('cloud', '#e6ecf4', 'body'), ('cloud2', '#b9c6d8', 'body'), ('cloak', '#3c5a8c', 'body'), ('cloak2', '#25395c', 'body'),
        ('skin', '#d8b08c', 'fixed'), ('eye', '#1a2a4a', 'eye'), ('feather', '#f2f2f2', 'accent'), ('bolt', '#ffe94a', 'accent'),
        ('gold', '#e6b84a', 'fixed'), ('lance', '#7a7f8a', 'fixed')])
    m.bone('fly_root', (0, 12, 0))
    m.bone('cloud', (0, 6, 0), 'fly_root')
    for x, y, z, w, h, d, col in ((0, 2, 0, 18, 6, 14, 'cloud'), (-7, 3, 2, 8, 6, 9, 'cloud2'), (7, 3, -1, 8, 6, 9, 'cloud2'),
                                  (0, 4, 7, 10, 5, 6, 'cloud'), (-3, 7, -3, 8, 3, 7, 'cloud'), (0, 1, -6, 9, 4, 4, 'cloud2')):
        m.box('cloud', x, y, z, w, h, d, col)
    m.box('cloud', 3, 0, 2, 1, 3, 1, 'bolt')
    m.box('cloud', 3.8, -2, 2, 1, 2.5, 1, 'bolt')
    # the rider, sitting on it
    m.bone('body', (0, 10, 0), 'fly_root')
    m.box('body', 0, 9, 0, 9, 11, 5, 'cloak')
    m.box('body', 0, 9, 2.8, 10, 12, 1, 'cloak2')  # cape
    m.box('body', 0, 18.5, 0, 9.4, 1.2, 5.4, 'gold')
    for side, name in ((-1, 'arm_left'), (1, 'arm_right')):
        m.bone(name, (side * 5.5, 19, 0), 'body')
        m.box(name, side * 5.8, 11, 0, 3, 8, 3, 'cloak')
        m.box(name, side * 5.8, 10.2, 0, 3, 1.5, 3, 'skin')
    m.box('arm_right', 6, 6, -2, 1, 1, 22, 'lance')  # a long lance
    m.box('arm_right', 6, 5.5, -13, 1.6, 1.6, 2.5, 'gold')
    m.bone('head', (0, 20, 0), 'body')
    m.box('head', 0, 20, 0, 7, 7, 7, 'skin')
    m.pair('head', 'head', 1.6, 23, -3.7, 1.2, 1.2, 0.5, 'eye')
    m.box('head', 0, 26.5, 0, 8, 2, 8, 'cloak2')  # cap
    m.box('head', 0, 27.5, 1, 1, 6, 2, 'feather')
    m.box('head', 0, 30, 3, 1, 2, 3, 'feather')
    idle = {'fly_root': bob(1.2, 120), 'cloud': {'rotation': ['0', wave(3, 80), '0']},
            'body': {'rotation': [wave(2, 120), '0', '0']}, 'head': {'rotation': ['0', wave(12, 70), '0']},
            'arm_left': {'rotation': [wave(10, 160), '0', '0']}, 'arm_right': {'rotation': [wave(4, 160, 90), '0', '0']}}
    m.anim('idle', idle)
    fly = dict(idle)
    fly['fly_root'] = {'rotation': [wave(1.5, 180, 0, 10), '0', '0']}
    m.anim('fly', fly)
    return m


# ---------------------------------------------------------------- earth


def sand_spider():
    m = Model('sand_spider', 'earth', [
        ('sand', '#d9c08a', 'body'), ('sand2', '#c2a26a', 'body'), ('stripe', '#8a6a3a', 'body'), ('leg', '#b08c58', 'body'),
        ('joint', '#6b5232', 'body'), ('eye', '#1a1208', 'eye')])
    c = dict(body='sand2', abdomen='sand', mark='stripe', leg='leg', joint='joint', eye='eye')
    names = spider(m, None, 18, 12, 8, 13, c)
    for z in (5, 8.5):
        m.box('abdomen', 0, 12.1, z, 9, 0.6, 1.2, 'stripe')
    m.anim('idle', spider_idle(names))
    return m


def three_headed_cobra():
    m = Model('three_headed_cobra', 'earth', [
        ('scale', '#8a7a3a', 'body'), ('dark', '#4f4520', 'body'), ('hood', '#c9a24a', 'body'), ('mark', '#3a2a14', 'fixed'),
        ('belly', '#e0cc8c', 'body'), ('eye', '#ff3a1a', 'eye'), ('fang', '#f4efe2', 'fixed'), ('tongue', '#b02030', 'fixed')])
    m.bone('body', (0, 3, 0))
    # coils on the ground
    m.box('body', 0, 0, 2, 18, 4, 16, 'scale')
    m.box('body', 0, 4, 2, 13, 4, 12, 'dark')
    m.box('body', 0, 8, 1, 8, 3, 8, 'scale')
    m.box('body', 9.5, 0, 9, 4, 3, 6, 'scale')  # tail end
    m.box('body', 12, 0, 12, 3, 2, 4, 'dark')
    heads = []
    for k, (x, lean) in enumerate(((-8, -1), (0, 0), (8, 1))):
        neck = m.bone(f'neck_{k + 1}', (x * 0.4, 10, 0), 'body')
        m.box(neck, x * 0.6, 10, -1, 3.4, 8, 3.4, 'scale')
        m.box(neck, x * 0.8, 17, -2, 3, 6, 3, 'scale')
        m.box(neck, x * 0.8, 11, -2.9, 2.4, 11, 0.6, 'belly')
        m.box(neck, x, 18, -2, 8, 9, 1.2, 'hood')  # the hood
        m.box(neck, x, 21, -2.7, 3, 3, 0.4, 'mark')
        head = m.bone(f'head_{k + 1}', (x, 26, -2), neck)
        m.box(head, x, 25, -4.5, 4.4, 3, 6, 'scale')
        m.pair(head, head, 1.6, 27, -6.8, 0.9, 0.9, 1, 'eye') if x == 0 else None
        if x != 0:
            m.box(head, x - 1.6, 27, -6.8, 0.9, 0.9, 1, 'eye')
            m.box(head, x + 1.6, 27, -6.8, 0.9, 0.9, 1, 'eye')
        m.box(head, x - 1, 24, -7.2, 0.6, 1.4, 0.6, 'fang')
        m.box(head, x + 1, 24, -7.2, 0.6, 1.4, 0.6, 'fang')
        m.box(head, x, 25.4, -8.6, 0.6, 0.3, 2, 'tongue')
        heads.append((neck, head))
    idle = {'body': {'rotation': ['0', wave(3, 80), '0']}}
    for k, (neck, head) in enumerate(heads):
        idle[neck] = {'rotation': [wave(5, 140, k * 120), '0', wave(7, 110, k * 100)]}
        idle[head] = {'rotation': [wave(6, 200, k * 80), wave(10, 90, k * 60), '0']}
    m.anim('idle', idle)
    return m


def sand_spirit():
    m = Model('sand_spirit', 'earth', [
        ('sand', '#e3c98f', 'body'), ('sand2', '#c9a96a', 'body'), ('sand3', '#a9844c', 'body'), ('stone', '#b07a4a', 'body'),
        ('eye', '#ffb43a', 'eye'), ('clay', '#9aa3b5', 'accent'), ('dark', '#5a4026', 'fixed')])
    m.bone('vortex', (0, 0, 0))
    for i, (y, w) in enumerate(((0, 4), (2.5, 6), (5, 8), (8, 10), (11, 12))):
        ring = m.bone(f'ring_{i + 1}', (0, y, 0), 'vortex')
        m.box(ring, 0, y, 0, w, 2.6, w, ('sand', 'sand2', 'sand3')[i % 3])
        m.box(ring, w / 2, y + 0.6, 0, 1.4, 1.4, 1.4, 'sand3')  # loose grains
        m.box(ring, -w / 2 + 0.3, y + 1, w / 3, 1, 1, 1, 'sand')
    m.bone('body', (0, 14, 0), 'vortex')
    m.box('body', 0, 13.5, 0, 10, 9, 6, 'stone')
    m.box('body', 0, 18, 0, 12, 4, 7, 'sand2')
    m.box('body', 0, 15, -3.2, 4, 3, 0.6, 'clay')
    for side, name in ((-1, 'arm_left'), (1, 'arm_right')):
        m.bone(name, (side * 6.5, 21, 0), 'body')
        m.box(name, side * 7.5, 13, 0, 3.5, 9, 3.5, 'sand2')
        m.box(name, side * 7.5, 11.5, 0, 4.2, 2.5, 4.2, 'stone')
    m.bone('head', (0, 22, 0), 'body')
    m.box('head', 0, 22, 0, 8, 7, 7, 'sand')
    m.box('head', 0, 22, 0, 8.6, 2, 7.6, 'sand2')
    m.box('head', 0, 26.5, 0.5, 6, 3, 5, 'sand2')  # windswept top
    m.box('head', 0, 28.5, 2.5, 3, 2, 4, 'sand3')
    m.pair('head', 'head', 1.8, 25, -3.6, 1.8, 1.2, 0.5, 'eye')
    m.box('head', 0, 23.2, -3.6, 3, 0.6, 0.5, 'dark')
    idle = {'vortex': bob(0.8, 140), 'body': {'rotation': [wave(2, 120), wave(5, 80), '0']},
            'head': {'rotation': ['0', wave(10, 90), '0']},
            'arm_left': {'rotation': [wave(12, 180), '0', wave(6, 140)]}, 'arm_right': {'rotation': [wave(12, 180, 180), '0', wave(6, 140, 90)]}}
    for i in range(5):
        idle[f'ring_{i + 1}'] = {'rotation': ['0', spin(200 + i * 60 if i % 2 == 0 else -(160 + i * 50)), '0']}
    m.anim('idle', idle)
    return m


# ---------------------------------------------------------------- void


def brain_dog():
    m = Model('brain_dog', 'void', [
        ('fur', '#3a2a4f', 'body'), ('dark', '#22182f', 'body'), ('brain', '#e6a3d8', 'accent'), ('fold', '#b56ab0', 'accent'),
        ('eye', '#d77bff', 'eye'), ('nose', '#120c18', 'fixed'), ('belly', '#57466e', 'body'), ('tooth', '#efe8dc', 'fixed')])
    c = dict(leg='fur', foot='dark', body='fur', belly='belly', head='fur', snout='dark', eye='eye', tail='fur')
    q = quadruped(m, 18, 6, 8, 9, c, head=8, snout=3.5, tail=6, lw=3)
    hz, hy, h = q['hz'], q['hy'], q['head']
    # the brain sitting open on its head
    m.box('head', 0, hy + h * 0.85 - 0.5, hz - h * 0.45, h * 0.9, 3.2, h * 0.85, 'brain')
    for z in (-2.4, 0, 2.4):
        m.box('head', 0, hy + h * 0.85 + 2.4, hz - h * 0.45 + z, h * 0.8, 0.6, 0.8, 'fold')
    m.box('head', 0, hy + h * 0.85 + 2.4, hz - h * 0.45, 0.6, 0.7, h * 0.8, 'fold')
    m.pair('head', 'head', h * 0.4, hy + h * 0.85, hz - h * 0.15, 1.8, 3, 1.6, 'dark')  # ears
    m.box('head', 0, hy + 1.6, hz - h - 4.3, 1.6, 1.2, 0.6, 'nose')
    m.pair('head', 'head', 1.2, hy - 0.4, hz - h - 3.8, 0.6, 0.8, 0.6, 'tooth')
    m.anim('idle', quad_idle(20, 340, {'tail': {'rotation': ['0', wave(25, 400), '0']}}))
    return m


def swirling_psychosis():
    m = Model('swirling_psychosis', 'void', [
        ('void', '#1b1026', 'body'), ('purple', '#6a2fb0', 'body'), ('violet', '#a45cff', 'accent'), ('pink', '#ff6fd8', 'accent'),
        ('eye', '#f6f0ff', 'eye'), ('pupil', '#0a0610', 'fixed'), ('mist', '#3e2a5c', 'body')])
    m.bone('fly_root', (0, 14, 0))
    m.bone('core', (0, 14, 0), 'fly_root')
    m.box('core', 0, 10, 0, 8, 8, 8, 'void')
    m.box('core', 0, 11, -4.2, 6, 6, 0.6, 'eye')
    m.box('core', 0, 12.5, -4.6, 2.6, 3, 0.4, 'pupil')
    m.box('core', 0, 12, -4.5, 6.6, 0.6, 0.4, 'violet')
    for i, (r, y, w, col) in enumerate(((9, 13.5, 2, 'purple'), (12, 11, 1.6, 'violet'), (7, 17, 1.4, 'pink'))):
        ring = m.bone(f'ring_{i + 1}', (0, 14, 0), 'core', rot=(18 * (i - 1), 0, 22 * (1 - i)))
        for k in range(8):  # a broken ring of blocks swirling round
            if k % 3 == 2: continue
            import math
            a = k * math.pi / 4
            m.box(ring, r * math.cos(a), y, r * math.sin(a), w + 1, w, w + 1, col)
    names = []
    for k, (x, z) in enumerate(((-2.5, -1), (2.5, 0), (0, 2.5), (-1, -3))):
        t = m.bone(f'tendril_{k + 1}', (x, 10, z), 'core')
        m.box(t, x, 6, z, 1.4, 4, 1.4, 'mist')
        m.box(t, x + 0.4, 3, z, 1, 3, 1, 'purple')
        m.box(t, x, 1, z, 0.8, 2, 0.8, 'violet')
        names.append(t)
    idle = {'fly_root': bob(1.4, 110), 'core': {'rotation': ['0', wave(12, 90), '0']},
            'ring_1': {'rotation': ['0', spin(180), '0']}, 'ring_2': {'rotation': ['0', spin(-120), '0']},
            'ring_3': {'rotation': ['0', spin(260), '0']}}
    for k, n in enumerate(names):
        idle[n] = {'rotation': [wave(14, 220, k * 80), '0', wave(10, 180, k * 50)]}
    m.anim('idle', idle)
    return m


def psychic_vampire():
    m = Model('psychic_vampire', 'void', [
        ('skin', '#d8c8e8', 'body'), ('cloak', '#24123a', 'body'), ('lining', '#7a2fb0', 'accent'), ('vein', '#c04cff', 'accent'),
        ('eye', '#ff4cf0', 'eye'), ('boot', '#140a20', 'fixed'), ('collar', '#3a1f5c', 'body'), ('gem', '#58f0ff', 'accent'),
        ('fang', '#ffffff', 'fixed')])
    c = dict(leg='cloak', foot='boot', torso='cloak', belt='collar', arm='cloak', hand='skin', head='skin', eye='eye')
    d = humanoid(m, 36, c, wide=0.85, head=1.05)
    hip, sh, top, tw, td, hw = d['hip'], d['sh'], d['top'], d['tw'], d['td'], d['hw']
    m.box('body', 0, hip - 8, td / 2 + 0.4, tw + 3, sh - hip + 8, 1, 'cloak')  # long cape
    m.box('body', 0, hip - 7.5, td / 2 - 0.2, tw + 2.2, sh - hip + 7, 0.4, 'lining')
    m.box('body', 0, sh - 1, 0, tw + 4, 6, td + 2.5, 'collar')  # high collar
    m.box('body', 0, sh - 4, -td / 2 - 0.3, 1.6, 1.6, 0.6, 'gem')
    # an enlarged cranium with glowing veins
    m.box('head', 0, top - 1, 0.5, hw + 1.6, 5, hw + 1.4, 'skin')
    for x in (-2.2, 0.4, 2.6):
        m.box('head', x, top + 3.7, 0.5, 0.6, 0.6, hw, 'vein')
    m.box('head', -2.8, top - 0.5, -hw / 2 - 0.4, 0.5, 4, 0.6, 'vein')
    m.pair('head', 'head', 0.9, sh + 1.2, -hw * 0.475 - 0.25, 0.6, 1.2, 0.4, 'fang')
    m.anim('idle', biped_idle(arm=8, leg=12, extra={'head': {'rotation': [wave(3, 60), wave(14, 50), '0']}}))
    return m


# ---------------------------------------------------------------- fae


def garden_fairy():
    m = Model('garden_fairy', 'fae', [
        ('skin', '#f5d6c0', 'fixed'), ('dress', '#ff8fc8', 'body'), ('petal', '#ffd1ea', 'body'), ('hair', '#6fcf5a', 'accent'),
        ('wing', '#bff3ff', 'accent'), ('wing2', '#ffe98a', 'accent'), ('eye', '#2a5a2a', 'eye'), ('flower', '#ffd23a', 'accent'),
        ('leg', '#e6a07a', 'fixed')])
    m.bone('fly_root', (0, 8, 0))
    for side, name in ((-1, 'leg_left'), (1, 'leg_right')):
        m.bone(name, (side * 0.9, 5, 0), 'fly_root')
        m.box(name, side * 0.9, 1, 0, 1.2, 4, 1.2, 'leg')
    m.bone('body', (0, 5, 0), 'fly_root')
    m.box('body', 0, 3.5, 0, 5, 3, 4, 'petal')  # petal skirt
    m.box('body', 0, 4, 0, 4, 2, 5, 'dress')
    m.box('body', 0, 6, 0, 3, 4, 2, 'dress')
    for side, name in ((-1, 'arm_left'), (1, 'arm_right')):
        m.bone(name, (side * 2, 9.5, 0), 'body')
        m.box(name, side * 2.1, 6.5, 0, 1, 3.5, 1, 'skin')
    m.bone('head', (0, 10, 0), 'body')
    m.box('head', 0, 10, 0, 4.5, 4.5, 4.5, 'skin')
    m.box('head', 0, 13.5, 0.3, 5, 2, 5, 'hair')
    m.box('head', 0, 11, 2, 5, 3.5, 1.2, 'hair')
    m.box('head', 1.8, 14.5, -1, 1.6, 1.6, 1.6, 'flower')
    m.pair('head', 'head', 1, 11.6, -2.4, 0.8, 1, 0.4, 'eye')
    wings(m, 'body', 1.2, 9, 1.2, 9, 5, 'wing', 'wing2', 'wing', membrane=True)
    idle = wing_anim({'fly_root': bob(1, 180), 'body': {'rotation': [wave(3, 150), '0', '0']},
                      'head': {'rotation': ['0', wave(12, 100), '0']},
                      'arm_left': {'rotation': [wave(25, 200), '0', '0']}, 'arm_right': {'rotation': [wave(25, 200, 180), '0', '0']},
                      'leg_left': {'rotation': [wave(10, 150), '0', '0']}, 'leg_right': {'rotation': [wave(10, 150, 180), '0', '0']}},
                     30, 600)
    m.anim('idle', idle)
    m.anim('fly', fly_from(idle, 20, 40, 720))
    return m


def glistening_gator():
    m = Model('glistening_gator', 'fae', [
        ('scale', '#2fa59a', 'body'), ('scale2', '#56c9c0', 'body'), ('belly', '#e8d6f0', 'body'), ('glint', '#ffffff', 'accent'),
        ('pink', '#ff9ad8', 'accent'), ('eye', '#ffe36a', 'eye'), ('tooth', '#f6f2e4', 'fixed'), ('berry', '#ffb53a', 'accent')])
    c = dict(leg='scale', foot='scale2', body='scale', belly='belly', head='scale', snout='scale', eye='eye', tail='scale')
    q = quadruped(m, 26, 4, 7, 12, c, head=8, neck=False, snout=9, tail=0, lw=3.5)
    top, hz, hy, h = q['top'], q['hz'], q['hy'], q['head']
    tail = chain(m, 'body', 4, 0, 4.5, 13, 5, 7, 5, 'scale', 'scale2', taper=0.75, name='tail')
    for z in (-8, -4, 0, 4, 8):  # ridges and glints along the back
        m.box('body', -2.5, top, z, 1.5, 1.4, 2, 'scale2')
        m.box('body', 2.5, top, z + 1, 1.5, 1.4, 2, 'scale2')
    for x, z in ((0, -6), (-3.5, 2), (3.5, -1), (0, 6), (-2, 9)):
        m.box('body', x, top + 0.2, z, 0.8, 0.8, 0.8, 'glint')
    m.box('body', 1, top + 0.4, 3, 2, 1.6, 2, 'pink')  # a flower growing on it
    m.box('body', 1, top + 2, 3, 0.8, 0.8, 0.8, 'berry')
    m.pair('head', 'head', 3, hy + h * 0.75, hz - h * 0.4, 2.2, 2, 2.4, 'scale2')  # eye bumps
    for k in range(4):
        m.pair('head', 'head', 2.4, hy - 0.6, hz - h - 1.5 - k * 2, 0.6, 1, 0.6, 'tooth')
    idle = quad_idle(14, 240, {'head': {'rotation': [wave(4, 90), wave(6, 60), '0']}})
    idle.update(chain_anim(tail, 12, 160, 50))
    m.anim('idle', idle)
    return m


def fairy_flower():
    m = Model('fairy_flower', 'fae', [
        ('stem', '#4c9f3a', 'body'), ('leaf', '#6fcf5a', 'body'), ('petal', '#ff6fb5', 'body'), ('petal2', '#ffb3d9', 'body'),
        ('disk', '#ffd84a', 'accent'), ('eye', '#5a2a4a', 'eye'), ('root', '#7a5a3a', 'body'), ('glow', '#fff4a8', 'accent')])
    for side, name in ((-1, 'leg_left'), (1, 'leg_right')):
        m.bone(name, (side * 1.6, 3, 0), None)
        m.box(name, side * 1.6, 0, 0, 1.6, 3.5, 1.6, 'root')
        m.box(name, side * 2.8, 0, -0.8, 2.2, 0.8, 1, 'root')
    m.bone('body', (0, 3, 0))
    m.box('body', 0, 3, 0, 4, 3, 4, 'root')  # bulb
    m.box('body', 0, 6, 0, 2, 11, 2, 'stem')
    for side, name in ((-1, 'arm_left'), (1, 'arm_right')):
        m.bone(name, (side * 1, 11, 0), 'body')
        m.box(name, side * 4, 10, 0, 6, 1, 3, 'leaf')
        m.box(name, side * 6.5, 10.5, 0, 2, 0.8, 2, 'leaf')
    m.bone('head', (0, 17, 0), 'body')
    m.box('head', 0, 15, -0.5, 7, 7, 2, 'disk')  # the flower's face
    m.pair('head', 'head', 1.4, 19, -1.7, 1, 1.4, 0.4, 'eye')
    m.box('head', 0, 16.6, -1.7, 2, 0.6, 0.4, 'eye')
    import math
    for k in range(8):  # petals round the face
        a = k * math.pi / 4
        m.box('head', 5 * math.cos(a), 18.5 + 5 * math.sin(a) - 1.5, 0, 3.6, 3.6, 1.2, 'petal' if k % 2 == 0 else 'petal2')
    m.box('head', 0, 18, 0.8, 1.2, 1.2, 0.8, 'glow')
    idle = {'body': {'rotation': [wave(3, 100), '0', wave(4, 80)]}, 'head': {'rotation': [wave(6, 120), wave(10, 70), wave(5, 90)]},
            'arm_left': {'rotation': ['0', '0', wave(12, 160)]}, 'arm_right': {'rotation': ['0', '0', wave(12, 160, 180)]},
            'leg_left': {'rotation': [wave(14, 240), '0', '0']}, 'leg_right': {'rotation': [wave(14, 240, 180), '0', '0']}}
    m.anim('idle', idle)
    return m


# ---------------------------------------------------------------- darkness


def shadow_stepper():
    m = Model('shadow_stepper', 'darkness', [
        ('shadow', '#1a1a22', 'body'), ('shadow2', '#2c2c3a', 'body'), ('wisp', '#5a3a7a', 'accent'), ('wisp2', '#8a5ab0', 'accent'),
        ('eye', '#f2f2ff', 'eye'), ('sand', '#3a2f28', 'fixed')])
    c = dict(leg='shadow', foot='shadow2', torso='shadow', belt='wisp', arm='shadow2', hand='shadow', head='shadow', eye='eye')
    d = humanoid(m, 38, c, wide=0.7, head=0.9)
    hip, sh, top, hw = d['hip'], d['sh'], d['top'], d['hw']
    # long fingers, wisps of shadow trailing off it
    for name in ('arm_left', 'arm_right'):
        x = m.by_name[name]['pivot'][0]
        for k in (-0.7, 0.7):
            m.box(name, x + k, sh - d['arm_h'] - 3.5, 0, 0.5, 3.5, 0.5, 'shadow2')
    m.box('body', 0, hip + 4, d['td'] / 2 + 1, 4, 6, 2, 'wisp')
    m.box('body', 0, hip + 1, d['td'] / 2 + 2.5, 2.6, 4, 1.6, 'wisp2')
    m.box('head', 0, top, 1, hw * 0.8, 2, hw * 0.8, 'wisp')
    m.box('head', 0, top + 1.6, 2, hw * 0.5, 2.5, hw * 0.5, 'wisp2')
    for name in ('leg_left', 'leg_right'):
        x = m.by_name[name]['pivot'][0]
        m.box(name, x, 0, 0, 3, 0.4, 3, 'sand')
    m.anim('idle', biped_idle(arm=16, leg=22, extra={'head': {'rotation': [wave(4, 160), wave(16, 60), '0']}}))
    return m


def cloud_of_darkness():
    m = Model('cloud_of_darkness', 'darkness', [
        ('cloud', '#2a2a36', 'body'), ('cloud2', '#3e3e50', 'body'), ('deep', '#15151d', 'body'), ('eye', '#ff3a3a', 'eye'),
        ('glow', '#7a3ab0', 'accent'), ('soul', '#5ad7e0', 'accent')])
    m.bone('fly_root', (0, 14, 0))
    m.bone('body', (0, 14, 0), 'fly_root')
    for x, y, z, w, h, d, col in ((0, 9, 0, 18, 10, 14, 'cloud'), (-8, 11, 1, 9, 9, 10, 'cloud2'), (8, 10, -1, 9, 9, 10, 'cloud2'),
                                  (0, 16, 2, 12, 6, 10, 'cloud2'), (-3, 18, -1, 7, 5, 7, 'cloud'), (4, 8, 4, 8, 5, 8, 'deep')):
        m.box('body', x, y, z, w, h, d, col)
    m.pair('body', 'body', 3, 14, -7.3, 2.4, 1.6, 0.6, 'eye')
    m.box('body', 0, 11, -7.3, 6, 1, 0.6, 'deep')
    m.box('body', 0, 12, -7.1, 7, 0.6, 0.4, 'glow')
    names = []
    for k, (x, z) in enumerate(((-6, -3), (-2, 3), (2, -4), (6, 2), (0, 0))):
        t = m.bone(f'tendril_{k + 1}', (x, 9, z), 'body')
        m.box(t, x, 5, z, 2, 4, 2, 'cloud2')
        m.box(t, x, 2, z, 1.4, 3, 1.4, 'deep')
        names.append(t)
    m.box('body', -7, 19, -2, 1, 1, 1, 'soul')
    idle = {'fly_root': bob(1.5, 90), 'body': {'rotation': [wave(3, 70), wave(6, 50), '0']}}
    for k, n in enumerate(names):
        idle[n] = {'rotation': [wave(16, 200, k * 70), '0', wave(10, 160, k * 40)]}
    m.anim('idle', idle)
    return m


def dracolich():
    m = Model('dracolich', 'darkness', [
        ('bone', '#e2dccb', 'body'), ('bone2', '#b8b0a0', 'body'), ('dark', '#3a3530', 'fixed'), ('membrane', '#3a2a40', 'body'),
        ('membrane2', '#5a3a5a', 'body'), ('eye', '#6bff7a', 'eye'), ('glow', '#3fdc6a', 'accent'), ('horn', '#2a2622', 'fixed')])
    m.bone('fly_root', (0, 22, 0))
    # legs of bone
    for name, sx, sz in (('front_left_leg', -1, -1), ('front_right_leg', 1, -1), ('back_left_leg', -1, 1), ('back_right_leg', 1, 1)):
        x, z = sx * 8, sz * 12
        m.bone(name, (x, 20, z), 'fly_root')
        m.box(name, x, 10, z, 3, 11, 3, 'bone')
        m.box(name, x, 9, z, 4, 2, 4, 'bone2')  # knee
        m.box(name, x, 0, z, 2.4, 10, 2.4, 'bone')
        m.box(name, x, 0, z - 2, 5, 1.2, 5, 'bone2')  # claws
        m.box(name, x, 0, z - 4.8, 4, 0.8, 1, 'horn')
    m.bone('body', (0, 20, 0), 'fly_root')
    m.box('body', 0, 25, 0, 3, 3, 34, 'bone')  # spine
    for z in range(-12, 13, 4):  # ribs
        m.box('body', 0, 18, z, 13, 1.4, 1.4, 'bone2')
        m.pair('body', 'body', 6.2, 18, z, 1.4, 8, 1.4, 'bone')
    m.box('body', 0, 21, 0, 4, 4, 4, 'glow')  # the soul in its chest
    for z in range(-14, 16, 5):
        m.box('body', 0, 28, z, 1.2, 2.5, 1.2, 'horn')
    m.box('body', 0, 17, 12, 14, 5, 6, 'bone2')  # hips
    neck = chain(m, 'body', 3, 0, 24, -17, -5, 3, 3, 'bone', None, taper=0.95, name='neck')
    head_z = m.by_name[neck[-1]]['pivot'][2] - 5
    m.bone('head', (0, 26, head_z), neck[-1])
    m.box('head', 0, 24, head_z - 5, 9, 7, 10, 'bone')
    m.box('head', 0, 23, head_z - 12, 6, 4, 7, 'bone')  # snout
    m.box('head', 0, 21.5, head_z - 11, 5.4, 1.5, 7, 'bone2')  # jaw
    m.pair('head', 'head', 2.3, 26, head_z - 10.3, 2.4, 2, 1, 'dark')  # eye sockets
    m.pair('head', 'head', 2.3, 26.4, head_z - 10.6, 1.2, 1, 0.8, 'eye')
    for side in (-1, 1):
        m.box('head', side * 3, 30, head_z - 2, 1.6, 6, 1.6, 'horn')
        m.box('head', side * 3.5, 34, head_z + 0.5, 1.2, 1.2, 4, 'horn')
    for k in range(4):
        m.pair('head', 'head', 2.4, 22, head_z - 9 - k * 1.8, 0.6, 1.4, 0.6, 'bone2')
    tail = chain(m, 'body', 6, 0, 22, 17, 5, 3, 3, 'bone', 'bone2', taper=0.85, name='tail')
    wings(m, 'body', 4, 27, -8, 52, 20, 'membrane', 'membrane2', 'bone', membrane=True)
    for side, w in ((-1, 'wing_left'), (1, 'wing_right')):  # tears in the membrane: bone struts
        for k in range(3):
            m.box(w, side * (4 + 13 * (k + 0.5) / 3 * 2), 26.6, -8 + 10, 0.8, 0.8, 18, 'bone2')
    idle = quad_idle(10, 220)
    idle.update(chain_anim(tail, 10, 140, 40))
    idle.update(chain_anim(neck, 4, 90, 30, axis=0))
    idle['head'] = {'rotation': [wave(5, 120), wave(10, 60), '0']}
    idle.pop('tail', None)
    wing_anim(idle, 12, 140)
    m.anim('idle', idle)
    fly = fly_from(idle, 8, 34, 220)
    for leg in ('front_left_leg', 'front_right_leg', 'back_left_leg', 'back_right_leg'):
        fly[leg] = {'rotation': ['30' if 'back' in leg else '-20', '0', '0']}
    m.anim('fly', fly)
    return m


# ---------------------------------------------------------------- arcane


def enchanted_furnace():
    m = Model('enchanted_furnace', 'arcane', [
        ('stone', '#7a7a7a', 'body'), ('stone2', '#5c5c5c', 'body'), ('dark', '#2a2a2a', 'fixed'), ('fire', '#ffb33a', 'accent'),
        ('fire2', '#ff6a1c', 'accent'), ('rune', '#b46cff', 'accent'), ('eye', '#ffe27a', 'eye'), ('iron', '#c8c8c8', 'fixed'),
        ('copper', '#c46a3a', 'fixed')])
    for side, name in ((-1, 'leg_left'), (1, 'leg_right')):
        m.bone(name, (side * 3.5, 4, 0), None)
        m.box(name, side * 3.5, 0, 0, 2.4, 4, 2.4, 'iron')
        m.box(name, side * 3.5, 0, -0.8, 3.4, 1, 4, 'dark')
    m.bone('body', (0, 4, 0))
    m.box('body', 0, 4, 0, 14, 14, 14, 'stone')
    m.box('body', 0, 4, 0, 14.4, 2, 14.4, 'stone2')
    m.box('body', 0, 16, 0, 14.4, 2, 14.4, 'stone2')
    m.box('body', 0, 6, -7.1, 8, 5, 0.4, 'dark')  # the mouth, burning
    m.box('body', 0, 6.4, -7.3, 6, 2.2, 0.4, 'fire2')
    m.box('body', 0, 8.4, -7.3, 4, 1.6, 0.4, 'fire')
    m.pair('body', 'body', 3, 13, -7.3, 2, 1.6, 0.4, 'eye')  # eyes: the vents
    for x, y in ((-6.6, 9), (6.6, 12), (-6.6, 14), (6.6, 6)):
        m.box('body', x, y, -2, 1, 1, 4, 'rune')
    m.box('body', 4, 18, 3, 3, 4, 3, 'stone2')  # chimney
    m.box('body', 4, 22, 3, 2, 1, 2, 'fire')
    for side, name in ((-1, 'arm_left'), (1, 'arm_right')):
        m.bone(name, (side * 7, 13, 0), 'body')
        m.box(name, side * 8, 8, 0, 2, 5, 2, 'copper')
        m.box(name, side * 8, 6.5, 0, 3, 2, 3, 'iron')
    idle = {'body': {'rotation': [wave(1.5, 200), '0', wave(2, 200, 90)], 'position': ['0', wave(0.4, 400), '0']},
            'leg_left': {'rotation': [wave(16, 400), '0', '0']}, 'leg_right': {'rotation': [wave(16, 400, 180), '0', '0']},
            'arm_left': {'rotation': [wave(20, 300), '0', '0']}, 'arm_right': {'rotation': [wave(20, 300, 180), '0', '0']}}
    m.anim('idle', idle)
    return m


def clockwork_horse():
    m = Model('clockwork_horse', 'arcane', [
        ('brass', '#c9a04a', 'body'), ('copper', '#b8673a', 'body'), ('iron', '#8a8f99', 'fixed'), ('dark', '#3a3530', 'fixed'),
        ('gear', '#e2c46a', 'accent'), ('eye', '#6fd8ff', 'eye'), ('glow', '#a77bff', 'accent'), ('rivet', '#4a3a2a', 'fixed')])
    c = dict(leg='iron', foot='dark', body='brass', belly='copper', head='brass', snout='copper', eye='eye', tail='iron')
    q = quadruped(m, 26, 16, 10, 10, c, head=7, snout=6, tail=8, lw=3)
    top, hz, hy, h = q['top'], q['hz'], q['hy'], q['head']
    for z in (-8, -2, 4, 9):  # rivet lines and plates
        m.box('body', 0, top - 0.5, z, 10.4, 0.6, 0.6, 'rivet')
    m.pair('body', 'body', 5.3, 19, -1, 0.6, 5, 5, 'copper')  # side plates
    import math
    for side in (-1, 1):  # gears on its flanks
        for k in range(4):
            a = k * math.pi / 2
            m.box('body', side * 5.6, 21 + 2.8 * math.sin(a), 4 + 2.8 * math.cos(a), 0.8, 1.6, 1.6, 'gear')
        m.box('body', side * 5.6, 20.3, 3.3, 0.8, 3, 3, 'gear')
    m.box('body', 0, top, 0, 2.5, 4, 1, 'iron')  # wind-up key
    m.box('body', 0, top + 4, 0, 6, 3, 1, 'iron')
    m.box('body', 0, top - 1, -8, 2.4, 1, 6, 'glow')  # glowing core in its back
    m.box('neck', 0, hy - 1, hz + 3.2, 1.6, 7, 2, 'iron')  # mane of pipes
    m.pair('head', 'head', 1.6, hy + h * 0.85, hz - h * 0.25, 1.2, 2.6, 1.2, 'copper')  # ears
    m.box('head', 0, hy + 2, hz - h - 6.3, 3, 1, 0.5, 'dark')
    for name in ('front_left_leg', 'front_right_leg', 'back_left_leg', 'back_right_leg'):
        x, _, z = m.by_name[name]['pivot']
        m.box(name, x, 8, z, 3.8, 2, 3.8, 'brass')  # knee joints
    m.anim('idle', quad_idle(18, 300, {'tail': {'rotation': [wave(8, 200), wave(18, 300), '0']}}))
    return m


def enchanted_bell():
    m = Model('enchanted_bell', 'arcane', [
        ('gold', '#e8c04a', 'body'), ('gold2', '#c99a2a', 'body'), ('dark', '#4a3a1a', 'fixed'), ('clapper', '#8a6a2a', 'fixed'),
        ('rune', '#b46cff', 'accent'), ('rune2', '#ff8ae6', 'accent'), ('eye', '#2a1a4a', 'eye'), ('rope', '#b08c58', 'fixed')])
    m.bone('body', (0, 2, 0))
    m.box('body', 0, 2, 0, 12, 2, 12, 'gold2')  # the lip
    m.box('body', 0, 4, 0, 10, 6, 10, 'gold')
    m.box('body', 0, 10, 0, 8, 3, 8, 'gold')
    m.box('body', 0, 13, 0, 5, 2, 5, 'gold2')
    m.box('body', 0, 15, 0, 2, 3, 2, 'rope')
    m.box('body', 0, 17, 0, 4, 1, 1, 'rope')
    m.pair('body', 'body', 2, 8, -5.2, 1.6, 2, 0.4, 'eye')
    m.box('body', 0, 5.5, -5.2, 3, 0.8, 0.4, 'eye')
    m.box('body', 0, 6.5, 0, 10.6, 0.8, 10.6, 'rune')
    m.bone('clapper', (0, 8, 0), 'body')
    m.box('clapper', 0, 0, 0, 2, 3, 2, 'clapper')
    m.box('clapper', 0, 3, 0, 0.8, 5, 0.8, 'dark')
    m.bone('runes', (0, 8, 0), 'body')
    import math
    for k in range(6):
        a = k * math.pi / 3
        m.box('runes', 9 * math.cos(a), 8 + (k % 2) * 3, 9 * math.sin(a), 1.4, 2, 0.4, 'rune' if k % 2 else 'rune2')
    idle = {'body': {'rotation': ['0', '0', wave(6, 260)], 'position': ['0', wave(1, 260, 90, 1)]},
            'clapper': {'rotation': ['0', '0', wave(14, 260, 60)]}, 'runes': {'rotation': ['0', spin(90), '0']}}
    m.anim('idle', idle)
    return m


def iron_golem():
    m = Model('iron_golem', 'arcane', [
        ('iron', '#b9bec6', 'body'), ('iron2', '#8d939c', 'body'), ('dark', '#4a4f57', 'body'), ('rivet', '#2e3238', 'fixed'),
        ('rune', '#7b5cd6', 'accent'), ('glow', '#c7a8ff', 'accent'), ('eye', '#7ae0ff', 'eye'), ('rust', '#9a5a3a', 'fixed')])
    c = dict(leg='iron2', foot='dark', torso='iron', belt='dark', arm='iron2', hand='dark', head='iron', eye='eye')
    d = humanoid(m, 48, c, wide=1.4, head=0.7)
    hip, sh, top, tw, td, hw = d['hip'], d['sh'], d['top'], d['tw'], d['td'], d['hw']
    m.box('body', 0, sh - 6, 0, tw + 2, 6, td + 2, 'iron2')  # heavy shoulders
    m.box('body', 0, hip + 4, -td / 2 - 0.3, 6, 7, 0.6, 'dark')  # a rune plate on the chest
    m.box('body', 0, hip + 6, -td / 2 - 0.7, 3, 3, 0.6, 'glow')
    m.box('body', 0, hip + 4.4, -td / 2 - 0.6, 5, 0.6, 0.4, 'rune')
    for x in (-tw / 2 + 1, tw / 2 - 1):
        for y in (hip + 3, sh - 3):
            m.box('body', x, y, -td / 2 - 0.3, 0.8, 0.8, 0.5, 'rivet')
    for name in ('arm_left', 'arm_right'):
        x = m.by_name[name]['pivot'][0]
        m.box(name, x, sh - d['arm_h'] * 0.55, 0, d['aw'] + 0.8, 1.2, d['aw'] + 0.8, 'rune')  # rune bands
        m.box(name, x, sh - d['arm_h'] - 3, 0, d['aw'] + 2, 4, d['aw'] + 2, 'iron2')  # big fists
    for name in ('leg_left', 'leg_right'):
        x = m.by_name[name]['pivot'][0]
        m.box(name, x, d['leg_h'] * 0.5, 0, 6.4, 1.2, d['td'] * 0.8 + 0.6, 'rune')
    m.box('head', 0, sh + d['head_h'] * 0.35, -hw * 0.475 - 0.3, hw * 0.8, 1.2, 0.5, 'dark')  # visor
    m.box('head', 0, top - 0.5, 0, hw * 0.4, 2, hw * 0.6, 'iron2')  # crest
    m.box('head', 2.5, sh + 1, -hw * 0.475 - 0.3, 1.2, 1.4, 0.4, 'rust')
    m.anim('idle', biped_idle(arm=10, leg=12))
    return m


# ---------------------------------------------------------------- radiant


def orb_of_light():
    m = Model('orb_of_light', 'radiant', [
        ('core', '#fffbe6', 'body'), ('light', '#ffe98a', 'body'), ('gold', '#ffc93a', 'accent'), ('ray', '#fff3b0', 'accent'),
        ('eye', '#c98a1a', 'eye')])
    m.bone('fly_root', (0, 10, 0))
    m.bone('body', (0, 10, 0), 'fly_root')
    m.box('body', 0, 6, 0, 8, 8, 8, 'light')
    m.box('body', 0, 7, 0, 9, 6, 6, 'light')
    m.box('body', 0, 7, 0, 6, 6, 9, 'light')
    m.box('body', 0, 5, 0, 6, 10, 6, 'light')
    m.box('body', 0, 7.5, 0, 5, 5, 5, 'core')
    m.pair('body', 'body', 1.4, 10, -4.6, 1, 1.4, 0.4, 'eye')
    m.bone('rays', (0, 10, 0), 'fly_root')
    for k, (x, z) in enumerate(((8, 0), (-8, 0), (0, 8), (0, -8))):
        m.box('rays', x, 9.5, z, 1.2 if x == 0 else 3, 1, 3 if x == 0 else 1.2, 'ray')
    m.bone('ring', (0, 10, 0), 'fly_root', rot=(20, 0, 15))
    for k in range(8):
        import math
        a = k * math.pi / 4
        m.box('ring', 7 * math.cos(a), 9.5, 7 * math.sin(a), 1.4, 1, 1.4, 'gold')
    idle = {'fly_root': bob(1.2, 130), 'rays': {'rotation': ['0', spin(120), '0']}, 'ring': {'rotation': ['0', spin(-80), '0']},
            'body': {'rotation': ['0', wave(10, 90), '0']}}
    m.anim('idle', idle)
    return m


def golden_owl():
    m = Model('golden_owl', 'radiant', [
        ('gold', '#e8b83a', 'body'), ('gold2', '#c98f22', 'body'), ('cream', '#fff1c4', 'body'), ('eye', '#fff8d0', 'eye'),
        ('pupil', '#2a1a08', 'fixed'), ('beak', '#7a5a2a', 'fixed'), ('talon', '#5a4020', 'fixed'), ('glow', '#ffffff', 'accent')])
    m.bone('fly_root', (0, 8, 0))
    for side, name in ((-1, 'leg_left'), (1, 'leg_right')):
        m.bone(name, (side * 1.8, 3, 0), 'fly_root')
        m.box(name, side * 1.8, 0, 0, 1.4, 3, 1.4, 'gold2')
        m.box(name, side * 1.8, 0, -0.8, 2.2, 0.6, 2.2, 'talon')
    m.bone('body', (0, 3, 0), 'fly_root')
    m.box('body', 0, 3, 0, 8, 9, 7, 'gold')
    m.box('body', 0, 3.5, -3.6, 5.5, 7, 0.4, 'cream')  # chest
    for y in (5, 7.5):
        m.box('body', 0, y, -3.9, 4, 0.5, 0.4, 'gold2')
    m.box('body', 0, 3, 4, 5, 3, 2, 'gold2')  # tail
    m.bone('head', (0, 12, 0), 'body')
    m.box('head', 0, 12, 0, 9, 7, 7, 'gold')
    m.box('head', 0, 12.5, -3.6, 7.5, 5.5, 0.4, 'cream')  # face disk
    m.pair('head', 'head', 1.9, 14, -3.9, 2.8, 2.8, 0.4, 'eye')
    m.pair('head', 'head', 1.9, 14.7, -4.2, 1.2, 1.4, 0.4, 'pupil')
    m.box('head', 0, 12.6, -4.4, 1.2, 1.6, 1, 'beak')
    m.pair('head', 'head', 3.3, 18.8, -0.5, 1.6, 2.6, 1.6, 'gold2')  # ear tufts
    m.box('head', 0, 20, 0, 1, 1, 1, 'glow')
    wings(m, 'body', 4, 10.5, -1, 16, 6, 'gold2', 'gold', 'gold2')
    idle = wing_anim({'body': {'rotation': [wave(2, 120), '0', '0']}, 'head': {'rotation': ['0', wave(40, 45), wave(6, 90)]},
                      'leg_left': {'rotation': [wave(10, 240), '0', '0']}, 'leg_right': {'rotation': [wave(10, 240, 180), '0', '0']}},
                     10, 160)
    m.anim('idle', idle)
    m.anim('fly', fly_from(idle, 14, 48, 300))
    return m


def archangel():
    m = Model('archangel', 'radiant', [
        ('robe', '#f6f2e6', 'body'), ('robe2', '#e2d8c0', 'body'), ('gold', '#f2c64a', 'accent'), ('skin', '#f0d2b4', 'fixed'),
        ('hair', '#fff2b0', 'body'), ('eye', '#5ad0ff', 'eye'), ('wing', '#ffffff', 'body'), ('wing2', '#e8eef8', 'body'),
        ('halo', '#fff07a', 'accent'), ('steel', '#c9d2dc', 'fixed'), ('gem', '#5ad0ff', 'accent')])
    m.bone('fly_root', (0, 20, 0))
    c = dict(leg='robe2', foot='gold', torso='robe', belt='gold', arm='robe', hand='skin', head='skin', eye='eye')
    d = humanoid(m, 42, c, root='fly_root', wide=1.0)
    hip, sh, top, tw, td, hw = d['hip'], d['sh'], d['top'], d['tw'], d['td'], d['hw']
    m.box('body', 0, hip - 10, 0, tw + 1.2, 10, td + 1.2, 'robe')  # robe over the legs
    m.box('body', 0, hip - 10, 0, tw + 1.6, 1.2, td + 1.6, 'gold')
    m.box('body', 0, sh - 6, -td / 2 - 0.3, tw * 0.8, 6, 0.6, 'gold')  # breastplate
    m.box('body', 0, sh - 4, -td / 2 - 0.7, 1.6, 1.6, 0.4, 'gem')
    m.pair('body', 'body', tw / 2 + d['aw'] / 2, sh - 1, 0, d['aw'] + 1.4, 3, d['aw'] + 1.4, 'gold')  # pauldrons
    m.box('head', 0, top - 2, 0.6, hw + 0.8, 3, hw + 0.4, 'hair')
    m.box('head', 0, sh + 1, hw / 2 + 0.4, hw, d['head_h'] - 1, 1, 'hair')
    m.bone('halo', (0, top + 3, 0), 'head')
    m.box('halo', 0, top + 3, 0, 9, 0.8, 1, 'halo')
    m.box('halo', 0, top + 3, 0, 1, 0.8, 9, 'halo')
    m.box('halo', 0, top + 3, 0, 7, 0.8, 7, 'halo')
    m.box('arm_right', tw / 2 + d['aw'] / 2, hip - 8, -2, 1.2, 22, 1.2, 'steel')  # a long sword
    m.box('arm_right', tw / 2 + d['aw'] / 2, hip + 12, -2, 4, 1, 1.4, 'gold')
    wings(m, 'body', 2, sh - 1, td / 2, 34, 12, 'wing', 'wing2', 'gold')
    # a second, smaller pair below
    for side, w in ((-1, 'lower_wing_left'), (1, 'lower_wing_right')):
        m.bone(w, (side * 2, hip + 4, td / 2), 'body', rot=(0, 0, side * 18))
        for k in range(3):
            m.box(w, side * (4 + k * 3.5), hip + 3.5, td / 2 + 4 + k, 3.6, 0.7, 8 + k * 2, 'wing2')
    idle = biped_idle(legs=True, arm=8, leg=6)
    idle['halo'] = {'rotation': ['0', spin(-100), '0']}
    idle['fly_root'] = bob(1, 100)
    wing_anim(idle, 16, 150)
    idle['lower_wing_left'] = {'rotation': ['0', '0', wave(10, 150, 60)]}
    idle['lower_wing_right'] = {'rotation': ['0', '0', wave(-10, 150, 60)]}
    m.anim('idle', idle)
    fly = fly_from(idle, 60, 50, 300)
    fly['head'] = {'rotation': ['-50', '0', '0']}
    m.anim('fly', fly)
    return m


# ---------------------------------------------------------------- none: the Whale (wave 1, no library model)


def whale():
    m = Model('whale', 'none', [
        ('skin', '#3f5a78', 'body'), ('skin2', '#34495f', 'body'), ('belly', '#c9d2dc', 'body'), ('groove', '#9aa8b8', 'body'),
        ('eye', '#0f1620', 'eye'), ('barnacle', '#d8d2c0', 'fixed'), ('dark', '#22303f', 'body')])
    m.bone('body', (0, 12, 0))
    m.box('body', 0, 0, -6, 26, 22, 44, 'skin')
    m.box('body', 0, 0, -6, 22, 4, 40, 'belly')
    for x in (-6, -2, 2, 6):
        m.box('body', x, -0.2, -16, 1.2, 1, 20, 'groove')  # throat grooves
    m.box('body', 0, 21.8, -10, 16, 1.6, 24, 'skin2')
    m.box('body', 0, 23.4, -16, 2, 1, 3, 'dark')  # blowhole
    m.box('body', 0, 22, 2, 2, 4, 6, 'skin2')  # dorsal fin
    m.box('body', 0, 2, -29, 24, 18, 2, 'skin')  # the head's front
    m.pair('body', 'body', 13.2, 8, -22, 0.6, 2, 2.4, 'eye')
    for x, y, z in ((-9, 18, -20), (10, 15, -14), (-11, 12, -4), (8, 20, 4)):
        m.box('body', x, y, z, 2, 1, 2, 'barnacle')
    for side, name in ((-1, 'fin_left'), (1, 'fin_right')):
        m.bone(name, (side * 13, 6, -16), 'body')
        m.box(name, side * 19, 4, -14, 12, 2, 7, 'skin2')
        m.box(name, side * 25.5, 4, -12.5, 2, 1.6, 5, 'belly')
    tail = chain(m, 'body', 3, 0, 4, 16, 9, 16, 14, 'skin', None, taper=0.7, name='tail')
    last = tail[-1]
    z = m.by_name[last]['pivot'][2]
    m.bone('fluke', (0, 9, z + 9), last)
    m.box('fluke', 0, 8, z + 12, 30, 2, 7, 'skin2')
    m.pair('fluke', 'fluke', 12, 8, z + 15.5, 7, 1.8, 4, 'dark')
    idle = {'body': {'rotation': [wave(2, 60), wave(2, 45), '0'], 'position': ['0', wave(1, 60), '0']},
            'fin_left': {'rotation': ['0', '0', wave(12, 80)]}, 'fin_right': {'rotation': ['0', '0', wave(-12, 80)]},
            'fluke': {'rotation': [wave(18, 90, -150), '0', '0']}}
    idle.update(chain_anim(tail, 8, 90, 50, axis=0))
    m.anim('idle', idle, 4)
    return m


ALL = [embering_flame, magma_viper, magma_dryad, polar_bear_folk, helebrog, crystalline_construct, water_gull, nautiloper,
       coral_crawler, poisonous_frog, oxe_turtle, leaf_web_spider, lightning, electric_eel, cloud_rider, sand_spider,
       three_headed_cobra, sand_spirit, brain_dog, swirling_psychosis, psychic_vampire, garden_fairy, glistening_gator,
       fairy_flower, shadow_stepper, cloud_of_darkness, dracolich, enchanted_furnace, clockwork_horse, enchanted_bell,
       iron_golem, orb_of_light, golden_owl, archangel, whale]
