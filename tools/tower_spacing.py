#!/usr/bin/env python3
"""How close Buried Towers stand to their nearest neighbours, from `:harness:buriedTowersTest -Ptowers.mode=spacing`.

  tower_spacing.py <run dir>

For each margin the run wrote, over its seeds: how many squares have a tower, and the nearest-neighbour distances in
bands, counting only towers whose eight neighbouring squares are all inside the area.
"""
import glob
import math
import os
import sys
from collections import defaultdict

run = sys.argv[1]
BANDS = [0, 100, 150, 200, 250, 300, 400, 10**9]
by_margin = defaultdict(list)
counts = defaultdict(int)
seeds = defaultdict(int)
for path in sorted(glob.glob(os.path.join(run, "*", "places-*.txt"))):
    margin = int(path.rsplit("-", 1)[1].split(".")[0])
    pts = [tuple(map(int, l.split())) for l in open(path) if l.strip()]
    counts[margin] += len(pts)
    seeds[margin] += 1
    span = 384
    grid = {}
    for p in pts:
        grid.setdefault((p[0] // span, p[1] // span), []).append(p)
    lo = min(p[0] for p in pts) // span + 1
    hi = max(p[0] for p in pts) // span - 1
    for p in pts:
        sx, sz = p[0] // span, p[1] // span
        # only squares with a full ring of neighbours inside the area
        if not (lo <= sx <= hi and lo <= sz <= hi):
            continue
        best = min(math.hypot(p[0] - q[0], p[1] - q[1]) for dx in (-1, 0, 1) for dz in (-1, 0, 1) for q in grid.get((sx + dx, sz + dz), []) if q != p)
        by_margin[margin].append(best)
for m in sorted(by_margin):
    ds = sorted(by_margin[m])
    n = len(ds)
    row = []
    for a, b in zip(BANDS, BANDS[1:]):
        k = sum(1 for d in ds if a <= d < b)
        row.append("%s-%s: %.1f%%" % (a, b if b < 10**9 else "", 100 * k / n))
    print("margin %3d: %d sites over %d seeds (%.1f%% of squares), nearest neighbour min %.0f, median %.0f; %s" % (m, counts[m], seeds[m], 100 * counts[m] / (seeds[m] * 48 * 48), ds[0], ds[n // 2], ", ".join(row)))
