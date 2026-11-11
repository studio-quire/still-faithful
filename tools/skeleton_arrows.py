"""
Skeleton Arrows (Still Faithful, docs/still-faithful.md): how often a skeleton's arrow hits a player, by a re-run of
beta 1.7.3's own code (SkeletonEntity.attack, ArrowEntity's shooter constructor and tick) against a player's box, for
beta's arrow from over the head, the arrow from the eyes with beta's aim, and the arrow from the eyes as
oldfaithful-polish aims it (SkeletonAim, mirrored here). Vectorised over shots with numpy.

  python tools/skeleton_arrows.py tables [--mp] [--n N]   hit rates by range, height, standing and walking, roofs
  python tools/skeleton_arrows.py scenes                  the shares SkeletonCheck expects for its five scenes
  python tools/skeleton_arrows.py jockey [--mp] [--n N]   a spider jockey: hits on the player, and on its own spider

The skeleton's feet are at the origin, in the middle of its block; the player straight along +x, `across` blocks away,
its feet `rise` up. Single player (the default): the player's y is its feet + 1.62 and it answers 0.12 for its eyes;
on a server (--mp) y is the feet and 1.62. Walking is 0.22 a tick, a player's pace on level ground.
"""
import argparse
import math

import numpy as np

SKEL_EYE = float(np.float32(1.8) * np.float32(0.85))
LIFT = float(np.float32(1.4))
BIG = 1000.0
WALK = 0.22
RANGES = range(2, 10)
# A spider jockey: the skeleton's feet sit SEAT over its spider's (SpiderEntity.getPassengerRidingHeight, 0.9F * 0.75
# - 0.5), and the spider's box (1.4 wide, 0.9 high, under it) is in the arrow's way: beta spares only the shooter.
SEAT = float(np.float32(0.9)) * 0.75 - 0.5
SPIDER = (-1.0, 1.0, -0.3, float(np.float32(0.9)) + 0.3, -1.0, 1.0)  # grown 0.3, as the arrow tests it


# --- Aims. Each takes beta's aim from where the arrow starts (dy to the target's eyes less 0.2, plus 0.2 a block
# across) and the distance across, and returns (setVelocity's y, spread). ---

def beta_aim(vy, across):
    return vy, 12.0


def rise(across, vy, speed=0.6):
    """SkeletonAim.rise: an unspread arrow's height over its start once it has gone `across`."""
    u = speed * across / math.hypot(across, vy)
    s = across / u
    if s * 0.01 >= 1:
        return -math.inf
    n = math.log(1 - 0.01 * s) / math.log(0.99)
    return vy - 0.03 * (n - s) / 0.01


def polished_aim(vy, across, speed=0.6, spread=12.0):
    """SkeletonAim.aim: crossing the target's distance where beta's arrow (LIFT higher) would, spread as beta's has."""
    beta_vy = vy - LIFT
    want = LIFT + rise(across, beta_vy, speed)
    if across < 0.5 or not math.isfinite(want):
        return vy, spread
    at = lambda angle: rise(across, across * math.tan(angle), speed)
    lo, hi = -math.radians(80), math.radians(80)
    for _ in range(40):
        a, b = lo + (hi - lo) / 3, hi - (hi - lo) / 3
        if at(a) < at(b):
            lo = a
        else:
            hi = b
    angle = (lo + hi) / 2
    if at(angle) > want:
        lo, hi = -math.radians(80), angle
        for _ in range(40):
            mid = (lo + hi) / 2
            if at(mid) < want:
                lo = mid
            else:
                hi = mid
        angle = (lo + hi) / 2
    aimed = across * math.tan(angle)
    return aimed, spread * math.hypot(across, beta_vy) / math.hypot(across, aimed)


# (name, lift, aim)
BETA = ('beta', LIFT, beta_aim)
EYES = ('eyes', 0.0, beta_aim)
POLISHED = ('polished', 0.0, polished_aim)


# --- The arrow. ---

def crossings(px, py, pz, dx, dy, dz, box):
    """Box.raycast's rule: the segment p..p+d crosses a face plane inside the face. The nearest one's fraction, else 2."""
    x0, x1, y0, y1, z0, z1 = box
    best = np.full(np.shape(px), 2.0)
    for plane, d, base, axis in ((x0, dx, px, 0), (x1, dx, px, 0), (y0, dy, py, 1), (y1, dy, py, 1),
                                 (z0, dz, pz, 2), (z1, dz, pz, 2)):
        ok = d * d >= 1e-7
        t = np.where(ok, (plane - base) / np.where(ok, d, 1.0), -1.0)
        ok = ok & (t >= 0) & (t <= 1)
        qx, qy, qz = px + dx * t, py + dy * t, pz + dz * t
        if axis == 0:
            inside = (qy >= y0) & (qy <= y1) & (qz >= z0) & (qz <= z1)
        elif axis == 1:
            inside = (qx >= x0) & (qx <= x1) & (qz >= z0) & (qz <= z1)
        else:
            inside = (qx >= x0) & (qx <= x1) & (qy >= y0) & (qy <= y1)
        best = np.where(ok & inside, np.minimum(best, t), best)
    return best


def scene(kind, across, rise_):
    """Solid boxes (x0, x1, y0, y1, z0, z1)."""
    if kind == 'air':
        return []
    if kind == 'level':
        return [(-BIG, BIG, -BIG, 0.0, -BIG, BIG)]
    if kind == 'cliff':
        # Above: a cliff face just in front of the player. Below: the skeleton at the edge of its ledge.
        if rise_ >= 0:
            return [(-BIG, BIG, -BIG, 0.0, -BIG, BIG), (across - 0.5, BIG, -BIG, rise_, -BIG, BIG)]
        return [(-BIG, BIG, -BIG, rise_, -BIG, BIG), (-BIG, 0.5, -BIG, 0.0, -BIG, BIG)]
    if kind.startswith('roof'):
        # roofN: a ceiling N high over level ground; roofN-w: walls a block apart too (a one-wide tunnel).
        height = float(kind[4:].split('-')[0])
        boxes = [(-BIG, BIG, -BIG, 0.0, -BIG, BIG), (-BIG, BIG, height, BIG, -BIG, BIG)]
        if kind.endswith('-w'):
            boxes += [(-BIG, BIG, -BIG, BIG, 0.5, BIG), (-BIG, BIG, -BIG, BIG, -BIG, -0.5)]
        return boxes
    raise ValueError(kind)


def shots(n, across, rise_, lift, rng, aim=beta_aim, mp=False, walk=(0.0, 0.0), kind='level', max_ticks=80,
          jockey=False, spare_mount=False):
    """(hits, n, hits on its own spider), or None where the skeleton wouldn't shoot (10 or more away, or it can't see
    the player). jockey: the skeleton rides a spider whose feet are at the origin; spare_mount: the arrow passes its
    spider for its first 4 ticks, as beta's does its shooter."""
    solids = scene(kind, across, rise_)
    t_y, t_eye = (rise_, 1.62) if mp else (rise_ + 1.62, 0.12)
    feet = SEAT if jockey else 0.0
    if math.hypot(across, t_y - feet) >= 10.0:  # target.getDistance(skeleton), entity y to entity y
        return None
    for b in solids:  # canSee: eyes to eyes
        if crossings(np.array([0.0]), np.array([feet + SKEL_EYE]), np.array([0.0]),
                     np.array([across]), np.array([t_y + t_eye - feet - SKEL_EYE]), np.array([0.0]), b)[0] <= 1.0:
            return None
    # The eyes less 0.1, 0.16 to the side by the skeleton's yaw (facing the player), then the lift.
    yaw = math.radians(math.degrees(math.atan2(0.0, across)) - 90.0)
    ax, az, ay = -math.cos(yaw) * 0.16, -math.sin(yaw) * 0.16, feet + SKEL_EYE - 0.1 + lift
    dy = t_y + t_eye - 0.2 - ay
    vy0, spread = aim(dy + across * 0.2, across)
    norm = math.sqrt(across * across + vy0 * vy0)  # setVelocity(dx, vy0, dz, 0.6, spread)
    g = rng.standard_normal((3, n)) * (0.0075 * spread)
    vx, vy, vz = (across / norm + g[0]) * 0.6, (vy0 / norm + g[1]) * 0.6, g[2] * 0.6
    px, py, pz = np.full(n, ax), np.full(n, ay), np.full(n, az)
    alive = np.ones(n, bool)
    for x0, x1, y0, y1, z0, z1 in solids:
        # Starting inside a block (beta's under a low ceiling): stuck at once.
        alive &= ~((px > x0) & (px < x1) & (py > y0) & (py < y1) & (pz > z0) & (pz < z1))
    hit = np.zeros(n, bool)
    mount = np.zeros(n, bool)
    for t in range(max_ticks):
        if not alive.any():
            break
        # The player has made this tick's move before the skeleton and its arrow tick.
        tx, tz = across + walk[0] * t, walk[1] * t
        frac = np.full(n, 2.0)
        for b in solids:
            frac = np.minimum(frac, crossings(px, py, pz, vx, vy, vz, b))
        block = frac <= 1.0
        s = np.where(block, frac, 1.0)
        player = (tx - 0.6, tx + 0.6, rise_ - 0.3, rise_ + 2.1, tz - 0.6, tz + 0.6)  # its box grown 0.3
        fp = crossings(px, py, pz, vx * s, vy * s, vz * s, player)
        fm = (crossings(px, py, pz, vx * s, vy * s, vz * s, SPIDER) if jockey and not (spare_mount and t < 4)
              else np.full(n, 2.0))
        ent = (fp <= 1.0) & (fp <= fm)
        on_mount = (fm <= 1.0) & (fm < fp)
        hit |= alive & ent
        mount |= alive & on_mount
        alive &= ~ent & ~on_mount & ~block
        px, py, pz = px + vx, py + vy, pz + vz
        vx, vy, vz = vx * 0.99, vy * 0.99 - 0.03, vz * 0.99
        alive &= py > min(0.0, rise_) - 12
    return int(hit.sum()), n, int(mount.sum())


# --- Tables. ---

def rate(n, across, rise_, way, mp=False, walk=(0.0, 0.0), kind='level', seed=0):
    r = shots(n, across, rise_, way[1], np.random.default_rng(seed), aim=way[2], mp=mp, walk=walk, kind=kind)
    return None if r is None else 100.0 * r[0] / r[1]


def jockey(n, mp):
    """Standing players at each range and height from a spider jockey: hits on the player / on the spider, in %."""
    ways = (('beta', LIFT, beta_aim, False), ('polished', 0.0, polished_aim, False),
            ('polished, spider spared', 0.0, polished_aim, True))
    print('across                                     ' + ''.join(f'{h:>8d}' for h in RANGES))
    for r in (0, 2, 4, -1, -2, -3, -4, -6):
        kind = 'level' if r == 0 else 'cliff'
        for name, lift, aim, spare in ways:
            cells = []
            for h in RANGES:
                got = shots(n, h, r, lift, np.random.default_rng(3), aim=aim, mp=mp, kind=kind, jockey=True,
                            spare_mount=spare)
                cells.append('     --' if got is None else f'{100 * got[0] / n:3.0f}/{100 * got[2] / n:<3.0f}')
            print(f'{kind} {r:+d} {name:30s}' + ' '.join(f'{c:>7s}' for c in cells))


def walking(n, across, rise_, way, mp, kind, all_ways):
    ws = [(WALK, 0), (-WALK, 0), (0, WALK), (0, -WALK)] if all_ways else [(0, WALK), (0, -WALK)]
    vals = [rate(n, across, rise_, way, mp, w, kind, seed=i + 1) for i, w in enumerate(ws)]
    return None if any(v is None for v in vals) else sum(vals) / len(vals)


def row(label, fn):
    vals = [fn(h) for h in RANGES]
    got = [v for v in vals if v is not None]
    print(f'{label:40s}' + ' '.join(' --' if v is None else f'{v:3.0f}' for v in vals)
          + f'   mean {sum(got) / len(got):5.1f}')


def tables(n, mp):
    ways = (BETA, EYES, POLISHED)
    print('across                                  ' + ' '.join(f'{h:3d}' for h in RANGES))
    for r in (0, 2, 4, -2, -4):
        kind = 'level' if r == 0 else 'cliff'
        for way in ways:
            row(f'{kind} {r:+d} standing {way[0]}', lambda h: rate(n, h, r, way, mp, kind=kind))
        for way in ways:
            # Level ground: toward, away and both sides; on a cliff, along its edge.
            row(f'{kind} {r:+d} walking {way[0]}', lambda h: walking(n, h, r, way, mp, kind, r == 0))
    for name, w in (('toward', (-WALK, 0)), ('away', (WALK, 0)), ('across', (0, WALK))):
        for way in ways:
            row(f'level walking {name} {way[0]}', lambda h: rate(n, h, 0, way, mp, w))
    for kind in ('roof2-w', 'roof2', 'roof3-w', 'roof3'):
        for way in ways:
            row(f'{kind} standing {way[0]}', lambda h: rate(n, h, 0, way, mp, kind=kind))
        for way in (BETA, POLISHED):
            row(f'{kind} walking {way[0]}', lambda h: walking(n, h, 0, way, mp, kind, True))


def scenes(n):
    for label, across, r, kind in (('level ground, 6 across', 6, 0, 'level'), ('4 up a cliff, 5 across', 5, 4, 'cliff'),
                                   ('4 down a cliff, 5 across', 5, -4, 'cliff'), ('tunnel, 5 across', 5, 0, 'roof2-w')):
        print(label, ' | '.join(f'{w[0]} {rate(n, across, r, w, kind=kind, seed=7):.1f}' for w in (BETA, EYES, POLISHED)))
    # A spider jockey 4 over the player: hits on the player, then on the spider.
    for name, way, spare in (('beta', BETA, False), ('polished', POLISHED, False), ('polished, spider spared', POLISHED, True)):
        got = shots(n, 5, -4, way[1], np.random.default_rng(7), aim=way[2], kind='cliff', jockey=True, spare_mount=spare)
        print(f'jockey, 4 down a cliff, 5 across, {name}: {100 * got[0] / n:.1f} / spider {100 * got[2] / n:.1f}')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    p.add_argument('what', choices=('tables', 'scenes', 'jockey'))
    p.add_argument('--mp', action='store_true', help="a server's player (y at the feet)")
    p.add_argument('--n', type=int, default=None, help='shots a cell (tables 20000, scenes 400000)')
    a = p.parse_args()
    if a.what == 'tables':
        tables(a.n or 20000, a.mp)
    elif a.what == 'jockey':
        jockey(a.n or 20000, a.mp)
    else:
        scenes(a.n or 400000)
