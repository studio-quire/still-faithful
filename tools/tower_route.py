#!/usr/bin/env python3
"""Walks a Buried Tower by a player's rules, from what stands in the dump, knowing nothing of how it was drawn.

  tower_route.py <dump.bin> [<route.json>]

From just outside the gate, with no block placing: walk, step up one, jump a gap of up to two, drop, climb ladders,
swim, open doors with a lever by them, open trapdoors, dig only floating gravel, and chop through a tree beta grew
round the tower: leaves above the land anywhere, logs outside the walls. A drop of 3 or less costs nothing, more
costs what beta's fall damage does, and a landing in water is free. Prints whether each floor's reached and how, and
exits 1 if a floor can't be reached, a route has to hurt more than a player can take, or no way down was used.
"""
import heapq
import json
import sys

from tower_view import Dump

AIR, WATER_F, WATER, LAVA_F, LAVA, GRAVEL, SAND = 0, 8, 9, 10, 11, 13, 12
LADDER, IRON_DOOR, WOOD_DOOR, TRAPDOOR, LEVER = 65, 71, 64, 96, 69
LOG, LEAVES = 17, 18
# How far the path can fall away below the hall's floor (Tower.APRON).
APRON = 6
THROUGH = {0, 8, 9, 6, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 55, 59, 63, 65, 66, 68, 69, 70, 72, 76, 77, 78, 83, 93, 64, 71, 96}
HARMFUL = {10, 11, 51}
NO_CARRY = {0, 8, 9, 10, 11, 6, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 63, 65, 66, 68, 69, 70, 72, 76, 77, 78, 83, 93, 64, 71, 90}
MAX_HURT = 18


def water(i):
    return i in (WATER, WATER_F)


class Walker:
    def __init__(self, d, dig_doors=False):
        self.d = d
        # Mining through an iron door with no lever in reach: only to get out of a cell a way landed in (the user's call, 2026-11-08: a breach into a cell, believable, dug out of).
        self.dig_doors = dig_doors
        self.id = d.id
        self.ground = d.info["groundY"]
        self.round = (d.radius + 0.5) ** 2
        # A cart run's ride: from its doorway at the top to its doorway at the foot, as a rider gets there.
        self.rides = {}
        cart = d.info.get("cart")
        if cart:
            floors = d.info["floors"]
            up, low = floors[cart["from"]], floors[cart["to"]]
            self.rides[(up["mouth"][0], up["stand"], up["mouth"][1])] = (up["landing"][0], low["stand"], up["landing"][1])

    def tree(self, block, x, y, z):
        """What a player would chop through: a tree outside the walls, down to as low as the path goes, or leaves over the hall's floor, where the tower's own are."""
        if block not in (LOG, LEAVES):
            return False
        if (x - self.d.cx) ** 2 + (z - self.d.cz) ** 2 > self.round:
            return y >= self.ground - APRON
        return block == LEAVES and y >= self.ground

    def clear(self, x, y, z):
        return self.fits(self.id(x, y, z), x, y, z) and self.fits(self.id(x, y + 1, z), x, y + 1, z)

    def fits(self, block, x, y, z):
        if block == IRON_DOOR:
            return self.dig_doors or self.lever_near(x, y, z)
        return block in THROUGH or self.tree(block, x, y, z)

    def lever_near(self, x, y, z):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(-2, 2):
                    if self.id(x + dx, y + dy, z + dz) == LEVER:
                        return True
        return False

    def held(self, x, y, z):
        """Whether a player stops here rather than falling on."""
        here, below = self.id(x, y, z), self.id(x, y - 1, z)
        return below not in NO_CARRY or here == LADDER or water(here) or water(below) or below == LADDER

    def fall(self, x, y, z):
        """Where a player lands from here, and what it costs. None if it ends in lava or the void."""
        start = y
        while not self.held(x, y, z):
            y -= 1
            if y < 1 or not self.fits(self.id(x, y, z), x, y, z):
                return None
        if self.id(x, y, z) in HARMFUL or self.id(x, y - 1, z) in HARMFUL:
            return None
        if water(self.id(x, y, z)) or water(self.id(x, y - 1, z)):
            return y, 0
        return y, max(0, start - y - 3)

    def moves(self, x, y, z):
        here = self.id(x, y, z)
        ride = self.rides.get((x, y, z))
        if ride:
            yield ride, 0, "cart"
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if self.clear(nx, y, nz) and self.id(nx, y, nz) not in HARMFUL:
                got = self.fall(nx, y, nz)
                if got:
                    yield (nx, got[0], nz), got[1], "walk" if got[0] == y else "drop"
            # A step or a jump up one.
            if self.clear(nx, y + 1, nz) and self.fits(self.id(x, y + 2, z), x, y + 2, z) and not self.clear(nx, y, nz):
                if self.held(nx, y + 1, nz):
                    yield (nx, y + 1, nz), 0, "step"
        # A jump across a gap, any way round, up to two cells of nothing between: over what's clear from the feet up.
        if self.fits(self.id(x, y + 2, z), x, y + 2, z):
            for jx in range(-3, 4):
                for jz in range(-3, 4):
                    far = (jx * jx + jz * jz) ** 0.5
                    if far < 1.5 or far > 3.3:
                        continue
                    fx, fz = x + jx, z + jz
                    steps = max(abs(jx), abs(jz))
                    cells = [(x + round(jx * k / steps), z + round(jz * k / steps)) for k in range(1, steps + 1)]
                    if not all(self.clear(cx, y, cz) and self.fits(self.id(cx, y + 2, cz), cx, y + 2, cz) for cx, cz in cells):
                        continue
                    if self.id(fx, y, fz) in HARMFUL:
                        continue
                    got = self.fall(fx, y, fz)
                    if got:
                        yield (fx, got[0], fz), got[1], "jump"
        if here == LADDER or self.id(x, y + 1, z) == LADDER:
            if self.clear(x, y + 1, z):
                yield (x, y + 1, z), 0, "ladder"
        below = self.id(x, y - 1, z)
        if below == LADDER or here == LADDER:
            if self.fits(below, x, y - 1, z):
                yield (x, y - 1, z), 0, "ladder"
        if below == TRAPDOOR:
            got = self.fall(x, y - 1, z)
            if got:
                yield (x, got[0], z), got[1], "trapdoor"
        if water(below) and self.clear(x, y - 1, z):
            yield (x, y - 1, z), 0, "swim"
        if water(here):
            for dx, dy, dz in ((0, 1, 0), (0, -1, 0)):
                if self.clear(x + dx, y + dy, z + dz) and (dy < 0 or water(self.id(x, y + 1, z))):
                    yield (x + dx, y + dy, z + dz), 0, "swim"
        if below in (GRAVEL, SAND) and self.id(x, y - 2, z) in THROUGH:
            got = self.fall(x, y - 1, z)
            if got:
                yield (x, got[0], z), got[1], "dig"


def walk(d, dig_doors=False):
    w = Walker(d, dig_doors)
    gx, gz = d.info["gate"]
    y = max(f["y"] for f in d.info["floors"] if f["kind"] == "gate hall") + 12
    while y > 1 and (d.id(gx, y - 1, gz) in NO_CARRY or w.tree(d.id(gx, y - 1, gz), gx, y - 1, gz)):
        y -= 1
    start = (gx, y, gz)
    best = {start: 0}
    came = {}
    queue = [(0, 0, start)]
    tick = 0
    while queue:
        hurt, _, cell = heapq.heappop(queue)
        if hurt > best.get(cell, 1 << 30):
            continue
        for nxt, cost, how in w.moves(*cell):
            total = hurt + cost
            if total > MAX_HURT or total >= best.get(nxt, 1 << 30):
                continue
            best[nxt] = total
            came[nxt] = (cell, how)
            tick += 1
            heapq.heappush(queue, (total, tick, nxt))
    return start, best, came


def path_to(cell, came):
    out = []
    while cell in came:
        prev, how = came[cell]
        out.append((cell, how))
        cell = prev
    out.append((cell, "start"))
    out.reverse()
    return out


def main():
    d = Dump(sys.argv[1])
    problems, lines, route = report(d, False)
    if problems:
        # A way down can land in a cell block's locked cell (one tower in 300): a player mines out through its iron door, so that's no fault if it's all it takes.
        dug, dug_lines, dug_route = report(d, True)
        if not dug:
            lines = dug_lines + ["  landed behind a locked iron door: dug out through it, as a player would (" + "; ".join(problems) + ")"]
            problems, route = dug, dug_route
    for line in lines:
        print(line)
    for p in problems:
        print("  PROBLEM:", p)
    if len(sys.argv) > 2:
        with open(sys.argv[2], "w") as out:
            json.dump({"route": [list(c) for c, _ in route], "moves": [h for _, h in route], "problems": problems}, out)
    sys.exit(1 if problems else 0)


def report(d, dig_doors):
    start, best, came = walk(d, dig_doors)
    floors = d.info["floors"]
    problems = []
    route = []
    out = []
    print = out.append
    print(f"from {start}: {len(best)} places reached")
    last = None
    for f in floors:
        spots = [c for c in best if f["y"] <= c[1] <= f["y"] + f["h"] - 1 and f["stand"] - 1 <= c[1] <= f["stand"] + 1]
        least = min((best[c] for c in spots), default=None)
        down = f.get("down")
        mouth = f.get("mouth")
        mouth_ok = None
        if mouth:
            mouth_ok = any(c[0] == mouth[0] and c[2] == mouth[1] and abs(c[1] - f.get("leave", f["stand"])) <= 1 for c in best)
        print(f"  {f['index']} {f['kind']:<20} y={f['y']:<3} h={f['h']:<2} stand={f['stand']:<3} reached={len(spots):<5} hurt={least} way down: {down} mouth reached={mouth_ok}")
        if not spots:
            problems.append(f"floor {f['index']} ({f['kind']}) can't be reached")
        elif mouth_ok is False:
            problems.append(f"floor {f['index']} ({f['kind']}): can't reach its way down, {down}, at {mouth}")
        if spots:
            last = min(spots, key=lambda c: best[c])
    chests = [tuple(int(v) for v in k.split(",")) for k, v in d.fixtures.items() if v.startswith("chest:vault")]
    near = None
    for cx, cy, cz in chests:
        for c in best:
            if abs(c[0] - cx) + abs(c[2] - cz) <= 2 and abs(c[1] - cy) <= 1 and (near is None or best[c] < best[near]):
                near = c
    if near is None:
        problems.append("no vault chest can be reached")
    else:
        route = path_to(near, came)
        hows = {}
        for _, how in route:
            hows[how] = hows.get(how, 0) + 1
        print(f"  to the vault: {len(route)} moves, hurt {best[near]} half-hearts, {hows}")
    # A hidden room is found by digging: nowhere in it may be walked to.
    room = d.info.get("hidden")
    if room:
        x0, y0, z0, x1, y1, z1 = room["room"]
        if any(x0 - 1 <= c[0] <= x1 + 1 and y0 <= c[1] <= y1 and z0 - 1 <= c[2] <= z1 + 1 for c in best):
            problems.append(f"the hidden room at {room['chest']} can be walked into")
        print(f"  hidden room at {room['chest']}, not reached without digging")
    # Every other chest and what can't be got to, for a look.
    missed = 0
    for k, v in d.fixtures.items():
        if not v.startswith("chest") or v == "chest:hidden":
            continue
        cx, cy, cz = (int(p) for p in k.split(","))
        if not any(abs(c[0] - cx) + abs(c[2] - cz) <= 2 and abs(c[1] - cy) <= 2 for c in best):
            missed += 1
    print(f"  chests out of reach without digging: {missed} of {sum(1 for v in d.fixtures.values() if v.startswith('chest') and v != 'chest:hidden')}")
    return problems, out, route


if __name__ == "__main__":
    main()
