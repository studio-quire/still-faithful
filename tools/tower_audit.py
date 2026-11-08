#!/usr/bin/env python3
"""Reads Buried Towers for the faults a picture is slow to find, from the dumps PlanDump or the test driver writes.

  tower_audit.py <dump.bin>...

Per floor kind: what a player could strip out (bookshelves, obsidian, iron doors, ore, metal blocks), anything from the
Nether, and what makes no sense as a built place: blocks floating with nothing joined to them, posts on nothing, half
beds, doors or ladders with nothing to hold them, chests that can't open or be reached, anything that burns where lava could set fire beside it (`lava_lights`),
water with something solid right over it (a monster under it can't come up, and drowns: a free grinder, the user found),
and a hidden room that isn't sealed (`hidden_room`).
Prints a line for each fault and a table at the end; exits 1 if there's a fault of a kind that's meant never to happen.
"""
import sys
from collections import Counter, defaultdict, deque

from tower_view import Dump

NAMES = {14: "gold ore", 15: "iron ore", 16: "coal ore", 21: "lapis ore", 56: "diamond ore", 73: "redstone ore", 20: "glass", 35: "wool", 41: "gold block", 42: "iron block", 45: "brick",
         47: "bookshelf", 49: "obsidian", 57: "diamond block", 22: "lapis block", 71: "iron door", 61: "furnace", 86: "pumpkin", 91: "jack o' lantern", 54: "chest", 52: "spawner"}
NETHER = {87: "netherrack", 88: "soul sand", 89: "glowstone", 90: "portal"}
THIN = {0, 8, 9, 10, 11, 6, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 55, 59, 63, 64, 65, 66, 68, 69, 70, 71, 72, 76, 77, 78, 83, 93}
NOT_SOLID = THIN | {26, 44, 53, 67, 85, 96, 18, 20}
# Gravel on crumbling stairs and a plug floats on purpose, until something beside it changes.
MAY_FLOAT = {13}
# Whatever's of a material beta burns (wood, leaves, wool, TNT): lava lights fire beside it, though a chest or a door never catches itself.
FLAMMABLE = {5, 17, 18, 25, 26, 35, 46, 47, 53, 54, 58, 63, 64, 68, 72, 84, 85, 95, 96}
# Not air, but nothing lava's heat stops at (Material.blocksMovement false): fluids, fire, plants, torches, ladders, rails, levers, buttons, wire, repeaters, a snow layer, a portal.
HEAT_PASSES = {6, 8, 9, 10, 11, 27, 28, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 65, 66, 69, 75, 76, 77, 78, 83, 90, 93, 94}
SIDES = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0))
# Ladder meta to the way its back lies, as (dx, dz).
LADDER_BACK = {2: (0, 1), 3: (0, -1), 4: (1, 0), 5: (-1, 0)}


def floor_at(d, y):
    for f in d.info["floors"]:
        if y >= f["y"] - 2:
            return f["kind"]
    return d.info["floors"][-1]["kind"]


def audit(path):
    d = Dump(path)
    # A simple tower's strongroom is out in the rock past the wall.
    r = d.radius + (5 if d.info.get("style") == "SIMPLE" else 1)
    counts = defaultdict(Counter)
    faults = []
    low = min(f["y"] for f in d.info["floors"]) - 3
    cells = {}
    for x in range(d.cx - r, d.cx + r + 1):
        for z in range(d.cz - r, d.cz + r + 1):
            for y in range(max(low, 1), 127):
                block = d.id(x, y, z)
                if block:
                    cells[(x, y, z)] = block

    lit = {}

    def say(kind, x, y, z, what):
        faults.append((kind, f"{floor_at(d, y)} at {x},{y},{z}: {what}"))

    for (x, y, z), block in cells.items():
        kind = floor_at(d, y)
        # Past the wall only the strongroom's things count: the land's own ore isn't the tower's.
        near = (x - d.cx) ** 2 + (z - d.cz) ** 2 <= (d.radius + 0.5) ** 2
        if block in NAMES and (near or block in (49, 54, 52, 71)):
            counts[kind][NAMES[block]] += 1
        if block in NETHER:
            say("nether", x, y, z, NETHER[block])
        below, above = d.id(x, y - 1, z), d.id(x, y + 1, z)
        if block == 85 and below in THIN:
            say("post on nothing", x, y, z, f"on {below}")
        elif block == 50 and d.meta(x, y, z) == 5 and (below in NOT_SOLID and below != 85):
            say("torch on nothing", x, y, z, f"on {below}")
        elif block == 54:
            if above not in NOT_SOLID:
                say("chest under a block", x, y, z, f"{above} on it")
            if below in THIN:
                say("chest in the air", x, y, z, f"on {below}")
            if all(d.id(x + dx, y, z + dz) not in NOT_SOLID and d.id(x + dx, y, z + dz) not in (54, 13) for dx, _, dz in SIDES[:4]):
                say("chest walled in", x, y, z, "solid all round")
        elif block == 26:
            head = d.meta(x, y, z) & 8
            direction = d.meta(x, y, z) & 3
            dx, dz = {0: (0, 1), 1: (-1, 0), 2: (0, -1), 3: (1, 0)}[direction]
            if head:
                dx, dz = -dx, -dz
            if d.id(x + dx, y, z + dz) != 26:
                say("half a bed", x, y, z, "")
        elif block == 65:
            back = LADDER_BACK.get(d.meta(x, y, z))
            if back and d.id(x + back[0], y, z + back[1]) in NOT_SOLID:
                say("ladder on nothing", x, y, z, "")
        elif block in (64, 71):
            if d.meta(x, y, z) & 8:
                if below != block:
                    say("half a door", x, y, z, "top")
            elif above != block or below in NOT_SOLID:
                say("half a door", x, y, z, f"bottom on {below}")
        if block in (8, 9) and near and above not in THIN:
            say("water under a block", x, y, z, f"{above} over it")
        if block in (10, 11):
            for c in lava_lights(d, x, y, z):
                lit.setdefault(c, (x, y, z))

    for (x, y, z), (lx, ly, lz) in sorted(lit.items()):
        say("wood in lava's reach", x, y, z, f"{d.id(x, y, z)}, lava at {lx},{ly},{lz}")

    hidden_room(d, say)

    # Whatever isn't joined to the biggest lump, which is the tower and the land under it.
    seen = {}
    lumps = []
    for start, block in cells.items():
        if start in seen or block in THIN:
            continue
        lump = []
        queue = deque([start])
        seen[start] = len(lumps)
        while queue:
            c = queue.popleft()
            lump.append(c)
            for dx, dy, dz in SIDES:
                n = (c[0] + dx, c[1] + dy, c[2] + dz)
                if n not in seen and n in cells and cells[n] not in THIN:
                    seen[n] = len(lumps)
                    queue.append(n)
        lumps.append(lump)
    if lumps:
        biggest = max(lumps, key=len)
        for lump in lumps:
            if lump is biggest or all(cells[c] in MAY_FLOAT for c in lump):
                continue
            x, y, z = lump[0]
            what = Counter(cells[c] for c in lump).most_common(3)
            if not touching_edge(d, lump, r):
                say("floating", x, y, z, f"{len(lump)} cells: {what}")
    return d, counts, faults


def lava_lights(d, x, y, z):
    """What still lava at a cell can set alight, by beta's random tick (StillLiquidBlock.onTick): up to two steps, each a cell up and up to one across either way;
    a step into air sets fire there if anything beside it burns, and stops; into anything else that stops movement, stops; through a fluid, a torch, a plant, goes on.
    So whatever burns within three cells up and three across can catch, unless solid blocks stand in the way. Every flammable cell any walk reaches."""
    found = set()

    def beside(cx, cy, cz):
        hits = [(cx + dx, cy + dy, cz + dz) for dx, dy, dz in SIDES if d.id(cx + dx, cy + dy, cz + dz) in FLAMMABLE]
        found.update(hits)
        return bool(hits)

    for a in (-1, 0, 1):
        for b in (-1, 0, 1):
            c1 = (x + a, y + 1, z + b)
            first = d.id(*c1)
            if first == 0:
                if beside(*c1):
                    continue
            elif first not in HEAT_PASSES:
                continue
            for a2 in (-1, 0, 1):
                for b2 in (-1, 0, 1):
                    c2 = (c1[0] + a2, c1[1] + 1, c1[2] + b2)
                    if d.id(*c2) == 0:
                        beside(*c2)
    return found


def depths(d):
    """Each column's distance in from the outside of the walls, in steps, as Tower.shape works it out: 0 outside, up to the wall's thickness the wall, more inside."""
    r = d.radius
    solid = {(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1) if x * x + z * z <= (r + 0.5) ** 2}
    depth = {}
    queue = deque()
    for x, z in solid:
        if any((x + dx, z + dz) not in solid for dx, _, dz in SIDES[:4]):
            depth[(x, z)] = 1
            queue.append((x, z))
    while queue:
        x, z = queue.popleft()
        for dx, _, dz in SIDES[:4]:
            n = (x + dx, z + dz)
            if n in solid and n not in depth:
                depth[n] = depth[(x, z)] + 1
                queue.append(n)
    return lambda x, z: depth.get((x - d.cx, z - d.cz), 0)


def hidden_room(d, say):
    """A hidden room is sealed: from its chest, what's open (air, anything not a whole block) stays a small pocket, with no water, lava, light or monster's
    spawner in it, closed all round by whole opaque blocks that don't fall or melt, inside the tower and a cell in from its wall. The way past it is a ladder or stairs."""
    gap = d.info.get("gap", -1)
    room = d.info.get("hidden")
    if gap is None or gap < 0:
        return
    floor = d.info["floors"][gap]
    if not room:
        say("gap with no room", d.cx, floor["y"] - 4, d.cz, f"under {floor['kind']}")
        return
    if floor.get("down") not in ("ladder", "stairs"):
        say("hidden room by a special way", d.cx, floor["y"] - 4, d.cz, str(floor.get("down")))
    wall = 1 if d.info.get("style") == "SIMPLE" else 2
    depth = depths(d)
    x, y, z = room["chest"]
    if d.id(x, y, z) != 54:
        say("hidden room without its chest", x, y, z, f"{d.id(x, y, z)} there")
        return
    pocket = {(x, y, z)}
    queue = deque([(x, y, z)])
    edge = set()
    while queue and len(pocket) <= 64:
        c = queue.popleft()
        for dx, dy, dz in SIDES:
            n = (c[0] + dx, c[1] + dy, c[2] + dz)
            if n in pocket:
                continue
            if d.id(*n) in NOT_SOLID or d.id(*n) == 54:
                pocket.add(n)
                queue.append(n)
            else:
                edge.add(n)
    if len(pocket) > 64:
        say("hidden room open", x, y, z, f"its air runs on past {len(pocket)} cells")
        return
    inside = [c for c in pocket if d.id(*c) not in (0, 54)]
    if inside:
        say("hidden room not empty", x, y, z, str(Counter(d.id(*c) for c in inside).most_common(3)))
    if any(depth(c[0], c[2]) < wall + 2 for c in pocket):
        say("hidden room by the wall", x, y, z, "a cell of it beside the outer wall or in it")
    for n in edge:
        block = d.id(*n)
        if block in (79, 52, 12, 13) or depth(n[0], n[2]) < wall + 1:
            say("hidden room badly closed", *n, f"{block} beside it at depth {depth(n[0], n[2])}")
            break


def touching_edge(d, lump, r):
    """Land cut off by the edge of what's looked at isn't floating."""
    return any(abs(c[0] - d.cx) >= r or abs(c[2] - d.cz) >= r for c in lump)


def main():
    totals = defaultdict(Counter)
    seen = Counter()
    fault_kinds = Counter()
    towers = hidden = 0
    per_tower = defaultdict(list)
    for path in sys.argv[1:]:
        d, counts, faults = audit(path)
        towers += 1
        hidden += 1 if d.info.get("hidden") else 0
        for f in d.info["floors"]:
            seen[f["kind"]] += 1
        whole = Counter()
        for kind, c in counts.items():
            totals[kind].update(c)
            whole.update(c)
        for name, n in whole.items():
            per_tower[name].append(n)
        for kind, text in faults:
            fault_kinds[kind] += 1
            print(f"{path.replace(chr(92), '/').split('/')[-1]}: {kind}: {text}")
    print(f"\n{towers} towers, {hidden} with a hidden room. Per floor of each kind:")
    for kind in sorted(totals):
        n = max(seen[kind], 1)
        print(f"  {kind:<20} x{seen[kind]:<3} " + ", ".join(f"{name} {v / n:.0f}" for name, v in totals[kind].most_common()))
    print("Per tower, mean and most:")
    for name, values in sorted(per_tower.items()):
        print(f"  {name:<16} {sum(values) / towers:6.0f} {max(values):6}")
    print("Faults:", dict(fault_kinds) or "none")
    sys.exit(1 if fault_kinds else 0)


if __name__ == "__main__":
    main()
