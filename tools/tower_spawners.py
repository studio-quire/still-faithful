#!/usr/bin/env python3
"""The spawners in Buried Towers' dumps and the light they stand in, by beta's rule (docs/lighting.md).

  tower_spawners.py <dump.bin>...

Works out block light from what stands in each dump (the sky is left out: underground, where the spawners are, it
plays no part), then for each spawner how much of the cells it tries (4 across, 1 up or down, weighted as beta picks
them) are dark enough for a monster to appear, as the mod's Guards.works does. Above about 3% a spawner spawns about
as often as it can, since it tries again every tick until one works. Prints spawners a floor and how well they work by
floor kind, and how many and of what kinds by depth.
"""
import sys
from collections import defaultdict, deque

from tower_view import Dump

LUMINANCE = {50: 14, 10: 15, 11: 15, 51: 15, 89: 15, 91: 15, 62: 13, 76: 7, 90: 11}
# What light passes through, at a cost of 1 unless it's here with more.
CLEAR = {0, 6, 8, 9, 20, 26, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 51, 52, 55, 59, 63, 64, 65, 66, 68, 69, 70, 71, 72, 75, 76, 77, 78, 79, 81, 83, 85, 90, 92, 93, 96, 18}
COST = {8: 3, 9: 3, 79: 3, 18: 1, 30: 1}
# What a monster can stand in: CLEAR without water.
ROOM = CLEAR - {8, 9, 79, 18, 20, 26, 52, 81, 85, 92}


def light(d):
    r = d.radius + 2
    low = min(f["y"] for f in d.info["floors"]) - 3
    level = {}
    queue = deque()
    for x in range(d.cx - r, d.cx + r + 1):
        for z in range(d.cz - r, d.cz + r + 1):
            for y in range(max(low, 1), 127):
                b = d.id(x, y, z)
                if b in LUMINANCE:
                    level[(x, y, z)] = LUMINANCE[b]
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        v = level[(x, y, z)]
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            b = d.id(*n)
            if b not in CLEAR:
                continue
            nv = v - max(1, COST.get(b, 0))
            if nv > level.get(n, 0):
                level[n] = nv
                queue.append(n)
    return level


def works(d, level, sx, sy, sz):
    """The share of what a spawner tries that could work, weighted as it picks: (random - random) * 4 leans to the middle."""
    total = good = 0.0
    for ix in range(-4, 5):
        for iz in range(-4, 5):
            w = (4.5 - abs(ix)) * (4.5 - abs(iz))
            for dy in (-1, 0, 1):
                x, y, z = sx + ix, sy + dy, sz + iz
                total += w
                if d.id(x, y, z) in ROOM and d.id(x, y + 1, z) in ROOM:
                    lv = level.get((x, y, z), 0)
                    if lv < 8:
                        good += w * (8 - lv) / 8
    return good / total


def floor_of(d, y):
    return next((f for f in d.info["floors"] if y >= f["y"] - 2), d.info["floors"][-1])


def main(paths):
    by_kind = defaultdict(list)
    floors_of_kind = defaultdict(int)
    count_of_kind = defaultdict(int)
    band = defaultdict(lambda: [0, 0])
    mobs = defaultdict(lambda: defaultdict(int))
    for path in paths:
        d = Dump(path)
        level = light(d)
        ground = d.info["groundY"]
        deep = max(1, ground - 8)
        spawners = [(tuple(map(int, k.split(","))), v[8:]) for k, v in d.fixtures.items() if v.startswith("spawner:")]
        for f in d.info["floors"]:
            mine = [(c, mob) for c, mob in spawners if floor_of(d, c[1]) is f]
            floors_of_kind[f["kind"]] += 1
            count_of_kind[f["kind"]] += len(mine)
            for c, mob in mine:
                by_kind[f["kind"]].append(works(d, level, *c))
            b = "above" if f["y"] >= ground else "%.1f" % min(0.8, int((ground - f["y"]) / deep * 5) / 5)
            band[b][0] += len(mine)
            band[b][1] += 1
            for c, mob in mine:
                mobs[b][mob] += 1
    print("%d towers" % len(paths))
    for k in sorted(floors_of_kind):
        v = by_kind.get(k, [])
        print("  %-20s floors %3d  spawners a floor %.2f  dark enough: least %5.1f%%, mean %5.1f%%" % (k, floors_of_kind[k], count_of_kind[k] / floors_of_kind[k], 100 * min(v) if v else 0, 100 * sum(v) / len(v) if v else 0))
    print("by depth, the share of the way down from the land:")
    for b in sorted(band):
        print("  %-6s floors %3d  spawners a floor %.2f  %s" % (b, band[b][1], band[b][0] / band[b][1], dict(sorted(mobs[b].items()))))


if __name__ == "__main__":
    main(sys.argv[1:])
