"""Where the game's main thread spends its time, from a benchmark run's JFR recording (docs/speed.md).

    python tools/jfr_profile.py local/bench/LABEL/RUN [--phase fly] [--top 40]

Reads RUN/profile.jfr (tools/bench.py run --jfr) with the JDK's `jfr` tool, keeps the game thread's execution and
native-method samples (JFR samples Java code and threads in native calls separately; GL and swap time show up as the
latter), names b1.7.3's classes and methods with Biny's mappings, and prints: time by part of the game (each sample
goes to the innermost frame that marks a part), and the methods with the most self and inclusive samples. With
--phase, only samples taken during that benchmark phase (from RUN/out/frames.csv's timeline). Claude Opus 5.5,
2026-11-01.
"""
import argparse
import collections
import csv
import datetime
import json
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JFR = ROOT / "local" / "tools" / "jdk21" / "bin" / "jfr"
BINY = ROOT / "local" / "mirror" / "maven" / "net" / "glasslauncher" / "biny" / "b1.7.3+e0778a3" / "biny-b1.7.3+e0778a3-v2.jar"
GAME_THREAD = "Minecraft main thread"

# (part, marker): the innermost frame on a stack that matches a marker decides the sample's part. A marker with a dot
# is an exact Class.method (simple class name); without one, any method of that class.
PARTS = [
    ("swap (Display.update)", "Display.update"),
    ("terrain generation", "ChunkCache.loadChunk"),
    ("terrain generation", "OverworldChunkGenerator"),
    ("terrain generation", "ChunkCache.getChunk"),
    ("terrain generation", "ChunkCache.decorate"),
    ("chunk saving/loading (disk)", "ChunkCache.save"),
    ("chunk saving/loading (disk)", "RegionChunkStorage"),
    ("chunk meshing (ChunkBuilder.rebuild)", "ChunkBuilder.rebuild"),
    ("chunk sorting/selection", "WorldRenderer.compileChunks"),
    ("lighting", "World.doLightingUpdates"),
    ("lighting", "LightUpdate"),
    ("visibility (frustum, occlusion)", "WorldRenderer.cullChunks"),
    ("terrain drawing", "WorldRenderer.render"),
    ("terrain drawing", "WorldRenderer.renderChunks"),
    ("entity drawing", "WorldRenderer.renderEntities"),
    ("sky", "WorldRenderer.renderSky"),
    ("clouds", "WorldRenderer.renderClouds"),
    ("clouds", "WorldRenderer.renderFancyClouds"),
    ("particles", "ParticleManager"),
    ("HUD and screens", "InGameHud.render"),
    ("HUD and screens", "Screen.render"),
    ("world tick: entities", "World.tickEntities"),
    ("world tick: blocks and chunks", "World.tick"),
    ("world tick: other", "Minecraft.tick"),
    ("frame setup (fog, camera, matrices)", "GameRenderer.renderFrame"),
    ("frame setup (fog, camera, matrices)", "GameRenderer.renderWorld"),
    ("other in the frame", "GameRenderer.onFrameUpdate"),
    ("game loop other", "Minecraft.run"),
]


def matches(frame, marker):
    cls, _, method = frame.rpartition(".")
    simple = cls.rsplit(".", 1)[-1].split("$")[0]
    if "." in marker:
        mcls, mmethod = marker.rsplit(".", 1)
        return simple == mcls and method == mmethod
    return simple == marker


def load_mappings():
    classes, methods = {}, {}
    text = zipfile.ZipFile(BINY).read("mappings/mappings.tiny").decode()
    cls = None
    for line in text.splitlines()[1:]:
        parts = line.split("\t")
        if parts[0] == "c":
            cls = parts[1].replace("/", ".")
            classes[cls] = parts[2].replace("/", ".")
        elif len(parts) > 1 and parts[1] == "m":
            methods[(cls, parts[3])] = parts[4]
    return classes, methods


def frame_name(frame, classes, methods):
    m = frame["method"]
    cls = m["type"]["name"].replace("/", ".")
    name = m["name"]
    named_cls = classes.get(cls, cls)
    named = methods.get((cls, name), name)
    if name.startswith("method_") and named == name:
        # Inherited or overriding: look the method up on any class (intermediary method names are global).
        for (c, n), v in methods.items():
            if n == name:
                named = v
                break
    return named_cls + "." + named


def phase_windows(run_dir):
    """Wall-clock windows of each benchmark phase, from frames.csv (times since the bench started) and the JFR start."""
    rows = list(csv.DictReader(open(run_dir / "out" / "frames.csv")))
    win = {}
    for r in rows:
        t = int(r["t_us"])
        lo, hi = win.get(r["phase"], (t, t))
        win[r["phase"]] = (min(lo, t), max(hi, t + int(r["frame_us"])))
    return win


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--phase")
    ap.add_argument("--top", type=int, default=30)
    args = ap.parse_args()
    run = Path(args.run)
    cache = run / "profile-samples.json"
    if not cache.is_file():
        with open(cache, "w", encoding="utf-8") as f:
            subprocess.run([str(JFR), "print", "--json", "--stack-depth", "96", "--events",
                            "jdk.ExecutionSample,jdk.NativeMethodSample", str(run / "profile.jfr")], stdout=f, check=True)
    events = json.load(open(cache, encoding="utf-8"))["recording"]["events"]
    classes, methods = load_mappings()

    window = None
    if args.phase:
        # frames.csv times count from the bench's t=0; JFR's are wall clock.
        win = phase_windows(run)
        if args.phase not in win:
            sys.exit(f"no phase {args.phase}")
        start = bench_start_wallclock(run, events)
        lo, hi = win[args.phase]
        window = (start + datetime.timedelta(microseconds=lo), start + datetime.timedelta(microseconds=hi))

    parts = collections.Counter()
    self_ = collections.Counter()
    incl = collections.Counter()
    total = 0
    for e in events:
        v = e["values"]
        thread = (v.get("sampledThread") or {}).get("javaName")
        if thread != GAME_THREAD:
            continue
        if window:
            t = parse_time(v["startTime"])
            if not (window[0] <= t <= window[1]):
                continue
        frames = [frame_name(f, classes, methods) for f in (v.get("stackTrace") or {}).get("frames", [])]
        if not frames:
            continue
        total += 1
        self_[frames[0]] += 1
        for f in set(frames):
            incl[f] += 1
        part = "unattributed"
        for f in frames:
            hit = next((p for p, marker in PARTS if matches(f, marker)), None)
            if hit:
                part = hit
                break
        parts[part] += 1

    print(f"{run.name}{' / ' + args.phase if args.phase else ''}: {total} samples on the game thread")
    print("\nBy part (innermost marker):")
    for p, n in parts.most_common():
        print(f"  {100 * n / total:5.1f}%  {p}")
    print(f"\nSelf (top {args.top}):")
    for f, n in self_.most_common(args.top):
        print(f"  {100 * n / total:5.1f}%  {f}")
    print(f"\nInclusive (top {args.top}, b1.7.3 and LWJGL only):")
    shown = 0
    for f, n in incl.most_common():
        if f.startswith(("net.minecraft", "org.lwjgl")):
            print(f"  {100 * n / total:5.1f}%  {f}")
            shown += 1
            if shown >= args.top:
                break


def parse_time(s):
    # JFR prints e.g. 2026-11-01T22:21:12.413123456+01:00 (nanoseconds); Python takes microseconds.
    date, frac = s.split(".", 1)
    n = 0
    while n < len(frac) and frac[n].isdigit():
        n += 1
    digits, tz = frac[:n], frac[n:]
    return datetime.datetime.fromisoformat(f"{date}.{digits[:6]}{tz}")


def bench_start_wallclock(run, events):
    """Wall-clock time of the bench's t=0, which Bench writes to out/bench-start.txt."""
    marker = run / "out" / "bench-start.txt"
    if not marker.is_file():
        sys.exit("no out/bench-start.txt: --phase needs a run from the current harness")
    return parse_time(marker.read_text().strip())

if __name__ == "__main__":
    main()
