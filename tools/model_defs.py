"""The creatures tools/build_models.py builds until their Mythic Mob Library models arrive (now just the Iron Golem
and the Whale). When a library model is imported, its creature is taken out of here.

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


# ---------------------------------------------------------------- frost


# ---------------------------------------------------------------- water


# ---------------------------------------------------------------- nature


# ---------------------------------------------------------------- storm


# ---------------------------------------------------------------- earth


# ---------------------------------------------------------------- void


# ---------------------------------------------------------------- fae


# ---------------------------------------------------------------- darkness


# ---------------------------------------------------------------- arcane


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


ALL = [iron_golem, whale]
