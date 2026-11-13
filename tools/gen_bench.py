"""World generation on the real server, timed: how long chunks take to make with a set of library mods, and where it goes.

    python tools/gen_bench.py LABEL [--mods none|towers|all|a,b] [--seed 1733] [--at 64,64] [--size 32]
                              [--order rows] [--per-tick 8] [--style simple|complex] [--jfr] [--top 25]
                              [--tower-jar PATH] [--plans RX0,RZ0,N]

Runs beta's server from the server package (`./gw serverPackage`) with Faithful Core, Settings, `library/tower-check`
and the chosen library mods' built jars (`./gw :library:<mod>:remapJar` first), on a new world, and has tower-check
load a square of chunks in an order, `--per-tick` a tick, as a player's walk would. tower-check times each load
(decorating the neighbours it completes included) and prints total, mean, p50, p99 and worst; with --jfr, a profile of
the server thread over just the generation, by method, ours and beta's (Biny names): JFR samples every 10 ms here, so
it wants a few seconds of work. Results in local/run/gen-bench/LABEL/result.txt. `towers` is Buried Towers alone; `all`
every library mod in the packs. --plans is how to show two builds draw the same towers: `--size 2 --plans -12,-12,24`
(and again with `--style complex`), then compare its plans.txt with library/tower-check/reference/plans-1733-simple.txt
(or -complex), 576 squares as Buried Towers 0.2.0 drew them, with the vault's zombies of 2026-11-09 and no lapis
veins since 2026-11-12.
Claude Opus 5.5, 2026-11-08.
"""
import argparse
import collections
import datetime
import json
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import jfr_profile  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "local" / "tools" / "jdk21" / "bin" / "java.exe"
PACKAGE = ROOT / "build" / "server-package" / "OldFaithful-server"
SERVER_JAR = ROOT / "local" / "cache" / "minecraft" / "b1.7.3-server.jar"
PACK_MODS = ["world-rules", "water-bottles", "hearty-food", "alchemy", "blue-flowers", "item-repair", "buried-towers"]


def newest_jar(folder, prefix):
    jars = [p for p in folder.glob(prefix + "*.jar") if not p.name.endswith(("-dev.jar", "-sources.jar")) and "selftest" not in p.name]
    if not jars:
        sys.exit(f"no jar in {folder}: build it first")
    return max(jars, key=lambda p: p.stat().st_mtime)


def mod_jars(mods):
    jars = [newest_jar(ROOT / "oldfaithful-core" / "build" / "libs", "faithful-core-"),
            newest_jar(ROOT / "oldfaithful-settings" / "build" / "libs", "oldfaithful-settings-"),
            newest_jar(ROOT / "library" / "tower-check" / "build" / "libs", "tower-check-")]
    names = [] if mods == "none" else ["buried-towers"] if mods == "towers" else PACK_MODS if mods == "all" else mods.split(",")
    for name in names:
        jars.append(newest_jar(ROOT / "library" / name / "build" / "libs", name + "-"))
    return jars


def swap_tower_jar(jars, path):
    path = Path(path)
    return [path if j.name.startswith("buried-towers-") else j for j in jars]


class Server:
    def __init__(self, cmd, cwd, log):
        self.lines = []
        self.cond = threading.Condition()
        self.log = open(log, "w", encoding="utf-8")
        self.proc = subprocess.Popen(cmd, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                     text=True, encoding="utf-8", errors="replace", bufsize=1)
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for line in self.proc.stdout:
            self.log.write(line)
            self.log.flush()
            with self.cond:
                self.lines.append(line.rstrip("\n"))
                self.cond.notify_all()

    def send(self, text):
        self.proc.stdin.write(text + "\n")
        self.proc.stdin.flush()

    def wait_for(self, marker, seconds, start=0):
        deadline = time.time() + seconds
        with self.cond:
            while True:
                for line in self.lines[start:]:
                    if marker in line:
                        return line
                left = deadline - time.time()
                if left <= 0 or self.proc.poll() is not None:
                    raise SystemExit(f"no '{marker}' within {seconds} s (see the log)")
                self.cond.wait(min(left, 1))


def profile(jfr, window, top):
    samples = jfr.with_suffix(".json")
    with open(samples, "w", encoding="utf-8") as f:
        subprocess.run([str(jfr_profile.JFR), "print", "--json", "--stack-depth", "128", "--events", "jdk.ExecutionSample", str(jfr)], stdout=f, check=True)
    events = json.load(open(samples, encoding="utf-8"))["recording"]["events"]
    classes, methods = jfr_profile.load_mappings()
    self_, incl, total = collections.Counter(), collections.Counter(), 0
    for e in events:
        v = e["values"]
        if (v.get("sampledThread") or {}).get("javaName") != "Server thread":
            continue
        t = jfr_profile.parse_time(v["startTime"])
        if not window[0] <= t <= window[1]:
            continue
        frames = [jfr_profile.frame_name(f, classes, methods) for f in (v.get("stackTrace") or {}).get("frames", [])]
        if not frames:
            continue
        total += 1
        self_[frames[0]] += 1
        for f in set(frames):
            incl[f] += 1
    samples.unlink()
    out = [f"{total} samples on the server thread during generation", "", f"Self (top {top}):"]
    out += [f"  {100 * n / total:5.1f}%  {f}" for f, n in self_.most_common(top)]
    out += ["", f"Inclusive (top {top}):"]
    out += [f"  {100 * n / total:5.1f}%  {f}" for f, n in incl.most_common(top)]
    out += ["", f"Inclusive, our code (top {top}):"]
    out += [f"  {100 * n / total:5.1f}%  {f}" for f, n in incl.most_common() if "studioquire" in f and "towercheck" not in f and ".api.Ticks" not in f
            and ".api.Listeners" not in f][:top]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("label")
    ap.add_argument("--mods", default="towers")
    ap.add_argument("--seed", type=int, default=1733)
    ap.add_argument("--at", default="64,64", help="the square's first chunk: keep it clear of the spawn area (about 12 chunks round a spawn the server picks by an unseeded random), which is made already")
    ap.add_argument("--size", type=int, default=32)
    ap.add_argument("--order", default="rows")
    ap.add_argument("--per-tick", type=int, default=8)
    ap.add_argument("--style")
    ap.add_argument("--jfr", action="store_true")
    ap.add_argument("--tower-jar", help="this Buried Towers jar in place of the built one")
    ap.add_argument("--plans", help="RX0,RZ0,N: after generating, every square of N by N squares' site and a digest of its tower as drawn, into plans.txt")
    ap.add_argument("--top", type=int, default=25)
    args = ap.parse_args()

    if not PACKAGE.is_dir():
        sys.exit(f"no server package in {PACKAGE}: run `./gw serverPackage` in this checkout first")
    run = ROOT / "local" / "run" / "gen-bench" / args.label
    if run.exists():
        shutil.rmtree(run)
    shutil.copytree(PACKAGE, run)
    shutil.copy(SERVER_JAR, run / "minecraft_server.jar")
    jars = mod_jars(args.mods)
    if args.tower_jar:
        jars = swap_tower_jar(jars, args.tower_jar)
    for jar in jars:
        shutil.copy(jar, run / "mods" / jar.name)
    (run / "server.properties").write_text("\n".join([
        "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=0", "view-distance=3", "max-players=20",
        "spawn-monsters=false", "spawn-animals=false", "allow-nether=false", f"level-seed={args.seed}", ""]))
    if args.style:
        (run / "config").mkdir(exist_ok=True)
        (run / "config" / "faithful-rules.properties").write_text(f"rule.buried-towers.style={args.style}\n")

    cmd = [str(JAVA), "-Xmx1400m"]
    if args.jfr:
        cmd.append(f"-XX:StartFlightRecording=filename={run / 'profile.jfr'},settings=profile")
    cmd += ["-cp", "libraries/*;minecraft_server.jar", "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui"]
    server = Server(cmd, run, run / "server.log")
    try:
        server.wait_for("Done (", 300)
        x0, z0 = (int(v) for v in args.at.split(","))
        mark = len(server.lines)
        began = datetime.datetime.now().astimezone()
        server.send(f"towertest gen {x0} {z0} {args.size} {args.order} {args.per_tick}")
        done = server.wait_for("[tower-check] DONE gen", 1800, mark)
        ended = datetime.datetime.now().astimezone()
        if args.plans:
            rx0, rz0, n = (int(v) for v in args.plans.split(","))
            mark = len(server.lines)
            server.send(f"towertest plans {rx0} {rz0} {n} {run / 'plans.txt'}")
            began_plans = time.time()
            server.wait_for("[tower-check] DONE plans", 3600, mark)
            plans_s = time.time() - began_plans
        server.send("stop")
        server.proc.wait(180)
    finally:
        if server.proc.poll() is None:
            server.proc.kill()
    stats = done.split("DONE gen", 1)[1].strip()
    out = [f"{args.label}: mods={args.mods} seed={args.seed} at={args.at} size={args.size} order={args.order} style={args.style or 'default'}",
           "mods: " + " ".join(j.name for j in jars), stats, f"wall_s={(ended - began).total_seconds():.1f}"]
    if args.plans:
        out.append(f"plans_s={plans_s:.1f}")
    if args.jfr:
        out += [""] + profile(run / "profile.jfr", (began, ended), args.top)
    shutil.rmtree(run / "world", ignore_errors=True)
    for name in ("libraries", "sources", "licenses"):
        shutil.rmtree(run / name, ignore_errors=True)
    (run / "result.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
