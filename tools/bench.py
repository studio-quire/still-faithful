"""Old Faithful's speed benchmark: runs the real game unseen on a fixed script and summarises the frames (docs/speed.md).

    ./gw :harness:benchSetup                      once, and after changing a mod: writes local/bench/launch.json
    python tools/bench.py run LABEL [options]     runs the game; results in local/bench/LABEL/<config>-vd<N>-r<i>/
    python tools/bench.py report LABEL            summary.md and summary.json in local/bench/LABEL/

Each run launches the game as a player's launcher does (Fabric, our mods, Prism's default heap) on a private desktop
(tools/private_desktop.py: nothing shown, no focus, no input), with sound mixed into OpenAL Soft's null output. The
self-test mod's Bench creates a world on a fixed seed and plays a fixed script: settle at spawn, stand, turn, fly.
Before each run the PC is checked for competing load; GPU power, clocks and temperature are sampled throughout; a
driver reset (nvlddmkm event 153) during a run stops the series. Runs are interleaved across configurations and
distances, with a cool-down between them. If the cursor guard stops a run, the run is retried
once the user has been away (no input) for three minutes, at once if they already are; a run that trips it three
times is skipped. Trips are noted in run.json.

Claude Opus 5.5, 2026-11-01.
"""
import argparse
import csv
import datetime
import json
import os
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BENCH = ROOT / "local" / "bench"
DISTANCES = {0: "far", 1: "normal", 2: "short", 3: "tiny"}
PHASES = ["settle", "stand", "turn", "fly"]
STALL_MS = 50
CONTENDED_CPU, CONTENDED_GPU = 50, 20  # percent busy before a run starts


def gpu_util(seconds=3.0):
    try:
        out = subprocess.run(["nvidia-smi", "--query-gpu=utilization.gpu,power.draw", "--format=csv,noheader,nounits",
                              "-lms", "250"], capture_output=True, text=True, timeout=seconds)
        text = out.stdout
    except subprocess.TimeoutExpired as e:
        text = (e.stdout or b"").decode() if isinstance(e.stdout, bytes) else (e.stdout or "")
    vals = [l.split(",") for l in text.strip().splitlines() if l.strip()]
    if not vals:
        return None, None
    return (statistics.mean(float(v[0]) for v in vals), statistics.mean(float(v[1]) for v in vals))


def cpu_busy(seconds=3.0):
    import psutil
    return psutil.cpu_percent(interval=seconds)


def competing_load():
    """What else is using the machine, before a run: GPU and CPU use, and the busiest other processes."""
    import psutil
    for p in psutil.process_iter():
        try:
            p.cpu_percent(None)
        except psutil.Error:
            pass
    util, power = gpu_util()
    cpu = cpu_busy()
    top = []
    for p in psutil.process_iter(["name"]):
        try:
            c = p.cpu_percent(None)
        except psutil.Error:
            continue
        if c > 5 and p.info["name"] not in ("System Idle Process",):
            top.append((round(c), p.info["name"]))
    top.sort(reverse=True)
    return {"gpuUtil": util, "gpuPowerW": power, "cpuPercent": cpu, "busiest": top[:5]}


def gpu_resets_since(t0):
    """nvlddmkm event 153 (a GPU driver reset) since t0, from the System log."""
    ps = ("Get-WinEvent -FilterHashtable @{LogName='System'; ProviderName='nvlddmkm'; Id=153; StartTime='%s'} "
          "-ErrorAction SilentlyContinue | Measure-Object | Select-Object -ExpandProperty Count") % t0.strftime("%Y-%m-%dT%H:%M:%S")
    out = subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True)
    try:
        return int(out.stdout.strip() or 0)
    except ValueError:
        return 0


AWAY_S = 180  # no real input for this long: the user is away


def user_idle_s():
    """Seconds since the user's last keyboard or mouse input (GetLastInputInfo)."""
    import ctypes
    from ctypes import wintypes

    class LASTINPUTINFO(ctypes.Structure):
        _fields_ = [("cbSize", wintypes.UINT), ("dwTime", wintypes.DWORD)]
    info = LASTINPUTINFO()
    info.cbSize = ctypes.sizeof(info)
    ctypes.windll.user32.GetLastInputInfo(ctypes.byref(info))
    return ((ctypes.windll.kernel32.GetTickCount() - info.dwTime) & 0xFFFFFFFF) / 1000


def wait_until_away():
    """After a cursor-guard trip: carry on at once if the user is away, otherwise wait until they are."""
    idle = user_idle_s()
    if idle >= AWAY_S:
        print(f"  user idle {idle:.0f} s (away): retrying", flush=True)
        return
    print(f"  user active (idle {idle:.0f} s): pausing until {AWAY_S} s without input", flush=True)
    while user_idle_s() < AWAY_S:
        time.sleep(15)
    print("  user away now: retrying", flush=True)


def run_one(spec, config, distance, run_dir, args):
    cfg = spec["configs"][config]
    game = run_dir / "game"
    out = run_dir / "out"
    if run_dir.exists():
        shutil.rmtree(run_dir)
    (game / "mods").mkdir(parents=True)
    out.mkdir()
    for m in cfg["mods"]:
        shutil.copy2(m, game / "mods")
    for name, text in cfg.get("configFiles", {}).items():
        (game / "config").mkdir(exist_ok=True)
        (game / "config" / name).write_text(text)
    if Path(spec["sounds"]).is_dir():
        shutil.copytree(spec["sounds"], game / "resources")
    # Max FPS, so frame times measure the work; the rest at b1.7.3's defaults (fancy, smooth lighting, sound on).
    (game / "options.txt").write_text(f"viewDistance:{distance}\nfpsLimit:{args.fps_limit}\n")

    jvm = ["-Xms512m", "-Xmx4096m",  # Prism's defaults, as the user's instances have them
           f"-Djava.library.path={cfg['natives']}", f"-Dorg.lwjgl.librarypath={cfg['natives']}",
           f"-Doldfaithful.bench.out={out}", f"-Doldfaithful.bench.seed={args.seed}",
           f"-Doldfaithful.bench.vsync={args.vsync}"]
    for key in ("stand", "turn", "fly", "settleTimeout"):
        v = getattr(args, key.lower())
        if v is not None:
            jvm.append(f"-Doldfaithful.bench.{key}={v}")
    if config != "lwjgl2":  # our component (sdl, and speed: sdl with the speed mod)
        jvm.append("--enable-native-access=ALL-UNNAMED")
        if not args.shown:
            jvm.append("-Doldfaithful.lwjgl.hidden=true")
    if args.jfr:
        jvm.append(f"-XX:StartFlightRecording=filename={run_dir / 'profile.jfr'},settings=profile,"
                   "jdk.ExecutionSample#period=5ms,jdk.NativeMethodSample#period=5ms")
    jvm += args.jvm or []
    cmd = [sys.executable, str(ROOT / "tools" / "private_desktop.py"), "--timeout", str(args.timeout), "--",
           spec["java"], *jvm, "-cp", os.pathsep.join(cfg["classpath"]), spec["mainClass"],
           "--username", "OldFaithfulBench", "--gameDir", str(game)]
    env = dict(os.environ, ALSOFT_DRIVERS="null")

    load = competing_load()
    sampler_log = open(run_dir / "gpu.csv", "w")
    sampler = subprocess.Popen(["nvidia-smi", "--query-gpu=timestamp,utilization.gpu,power.draw,clocks.gr,temperature.gpu,memory.used",
                                "--format=csv,noheader,nounits", "-lms", "500"], stdout=sampler_log, stderr=subprocess.DEVNULL)
    t0 = datetime.datetime.now()
    start = time.time()
    with open(run_dir / "output.log", "w", encoding="utf-8", errors="replace") as log:
        code = subprocess.run(cmd, cwd=game, env=env, stdout=log, stderr=subprocess.STDOUT).returncode
    wall = time.time() - start
    sampler.terminate()
    sampler.wait()
    sampler_log.close()
    resets = gpu_resets_since(t0)
    info = {"config": config, "distance": distance, "exit": code, "wallS": round(wall, 1), "loadBefore": load,
            "gpuResets": resets, "hidden": bool(not args.shown and config != "lwjgl2"), "jfr": bool(args.jfr),
            "started": t0.isoformat(timespec="seconds")}
    (run_dir / "run.json").write_text(json.dumps(info, indent=1))
    latest = game / "logs" / "purefaithful" / "latest.log"
    if latest.is_file():
        shutil.copy2(latest, run_dir / "game.log")
    if not args.keep_game:
        shutil.rmtree(game, ignore_errors=True)
    return info


def cmd_run(args):
    spec = json.loads((BENCH / "launch.json").read_text())
    label_dir = BENCH / args.label
    label_dir.mkdir(parents=True, exist_ok=True)
    configs = args.configs.split(",")
    distances = [int(d) for d in args.distances.split(",")]
    plan = [(r, d, c) for r in range(1, args.repeats + 1) for d in distances for c in configs]
    (label_dir / "plan.json").write_text(json.dumps({"argv": sys.argv[1:], "runs": plan}))
    for i, (r, d, c) in enumerate(plan):
        run_dir = label_dir / f"{c}-vd{d}-r{r}"
        print(f"[{i + 1}/{len(plan)}] {c} view distance {DISTANCES[d]} ({d}), repeat {r}", flush=True)
        trips = []
        while True:
            info = run_one(spec, c, d, run_dir, args)
            if info["exit"] not in (125, 126):
                break
            # The guard stopped the game and restored the clip (tools/private_desktop.py). HandsOff should make this
            # impossible; if it happens anyway, the user's presence decides (docs/building.md).
            trips.append({"exit": info["exit"], "at": info["started"], "userIdleS": round(user_idle_s())})
            print(f"  CURSOR GUARD stopped the run (exit {info['exit']}, see output.log)", flush=True)
            if len(trips) >= 3:
                print("  three guard trips on this run: skipping it", flush=True)
                break
            wait_until_away()
        if trips:
            info["guardTrips"] = trips
            (run_dir / "run.json").write_text(json.dumps(info, indent=1))
            if info["exit"] in (125, 126):
                continue
        result = (run_dir / "out" / "bench.json").is_file()
        print(f"  exit {info['exit']}, {info['wallS']} s, results {'written' if result else 'MISSING'}; load before: "
              f"GPU {info['loadBefore']['gpuUtil']}%, CPU {info['loadBefore']['cpuPercent']}%", flush=True)
        log = (run_dir / "output.log").read_text(encoding="utf-8", errors="replace")
        hands_off = ("[private_desktop] cursor guard on" in log and "[oldfaithful-bench] HandsOff active" in log
                     and "grabbed=true" not in log and "Mouse.isGrabbed() = true" not in log)
        print(f"  cursor: guard {'on' if '[private_desktop] cursor guard on' in log else 'MISSING'}, HandsOff "
              f"{'active' if '[oldfaithful-bench] HandsOff active' in log else 'MISSING'}, "
              f"{log.count('[oldfaithful-handsoff] blocked')} grab/warp calls blocked, never grabbed: {hands_off}", flush=True)
        if not hands_off:
            print("  cursor safety not confirmed for this run: stopping the series", flush=True)
            return 4
        if info["gpuResets"]:
            print(f"  GPU driver reset during the run ({info['gpuResets']} event 153): stopping the series", flush=True)
            return 2
        if i + 1 < len(plan):
            time.sleep(args.cooldown)
    return 0


# ---------------------------------------------------------------------------------------------------------- report

def pct(sorted_vals, p):
    if not sorted_vals:
        return None
    k = (len(sorted_vals) - 1) * p
    lo = int(k)
    hi = min(lo + 1, len(sorted_vals) - 1)
    return sorted_vals[lo] + (sorted_vals[hi] - sorted_vals[lo]) * (k - lo)


def summarise_run(run_dir):
    meta = json.loads((run_dir / "out" / "bench.json").read_text())
    info = json.loads((run_dir / "run.json").read_text())
    rows = list(csv.DictReader(open(run_dir / "out" / "frames.csv")))
    gcs = list(csv.DictReader(open(run_dir / "out" / "gc.csv")))
    res = meta["result"]
    out = {"run": run_dir.name, "config": info["config"], "distance": info["distance"], "gl": meta["gl"], "display": meta["display"],
           "hidden": info.get("hidden"), "worldgenMs": res.get("worldgenMs"), "settledMs": res.get("settledMs"),
           "settleDirtyLeft": res.get("settleDirtyLeft"), "flyDistance": res.get("flyDistance"),
           "gpuMissing": res.get("gpuResultsMissing"), "loadBefore": info["loadBefore"], "phases": {}}
    for ph in PHASES:
        fr = [r for r in rows if r["phase"] == ph]
        if not fr:
            continue
        ft = sorted(int(r["frame_us"]) / 1000 for r in fr)
        span = (int(fr[-1]["t_us"]) + int(fr[-1]["frame_us"]) - int(fr[0]["t_us"])) / 1e6
        mean = lambda col: statistics.mean(int(r[col]) for r in fr) / 1000
        gpu = [int(r["gpu_us"]) / 1000 for r in fr if int(r["gpu_us"]) >= 0]
        pauses = [float(g["duration_ms"]) for g in gcs if g["phase"] == str(PHASES.index(ph) + 1) and "Concurrent" not in g["name"]]
        cpu = res.get(f"{ph}CpuMs")
        out["phases"][ph] = {
            "frames": len(fr), "seconds": round(span, 2), "fps": round(len(fr) / span, 1) if span else None,
            "medianMs": round(pct(ft, 0.5), 3), "p99Ms": round(pct(ft, 0.99), 3), "p999Ms": round(pct(ft, 0.999), 3),
            "maxMs": round(ft[-1], 1), "stalls": sum(1 for v in ft if v > STALL_MS),
            "low1Fps": round(1000 / statistics.mean(ft[int(len(ft) * 0.99):]), 1),  # mean of the slowest 1%, as fps
            "tickMs": round(mean("tick_us"), 3), "lightMs": round(mean("light_us"), 3), "renderMs": round(mean("render_us"), 3),
            "compileMs": round(mean("compile_us"), 3), "genMs": round(mean("gen_us"), 3), "swapMs": round(mean("swap_us"), 3),
            "gpuMs": round(statistics.median(gpu), 3) if gpu else None,
            "rebuildsPerS": round(sum(int(r["rebuilt"]) for r in fr) / span, 1) if span else None,
            "gcPauses": len(pauses), "gcPauseMs": round(sum(pauses), 1), "gcMaxMs": max(pauses) if pauses else 0,
            "cpuCores": round(cpu / 1000 / span, 2) if cpu and span else None,
        }
    return out


def cmd_report(args):
    label_dir = BENCH / args.label
    runs = []
    for d in sorted(label_dir.iterdir()):
        if d.is_dir() and (d / "out" / "bench.json").is_file():
            try:
                runs.append(summarise_run(d))
            except Exception as e:  # a broken run is reported, not fatal
                print(f"{d.name}: {e}")
    groups = {}
    contended = []
    for r in runs:
        load = r["loadBefore"]
        if (load.get("cpuPercent") or 0) > CONTENDED_CPU or (load.get("gpuUtil") or 0) > CONTENDED_GPU:
            contended.append(r)  # something else was busy as it started: left out of the medians, listed below
            continue
        groups.setdefault((r["config"], r["distance"]), []).append(r)
    lines = [f"# Benchmark {args.label}", ""]
    if runs:
        lines += [f"GL: {runs[0]['gl']}; display {runs[0]['display'][0]}x{runs[0]['display'][1]}", ""]
    metrics = [("fps", "fps"), ("medianMs", "median ms"), ("p99Ms", "p99 ms"), ("low1Fps", "1% low fps"), ("stalls", ">50ms"),
               ("tickMs", "tick"), ("lightMs", "light"), ("renderMs", "render"), ("compileMs", "compile"), ("genMs", "gen"),
               ("swapMs", "swap"), ("gpuMs", "gpu"), ("rebuildsPerS", "rebuilds/s"), ("gcPauseMs", "GC ms"), ("cpuCores", "cores")]
    summary = {}
    for (config, distance), rs in sorted(groups.items()):
        key = f"{config}-vd{distance}"
        lines += [f"## {config}, view distance {DISTANCES[distance]} ({len(rs)} runs)", ""]
        wg = [r["worldgenMs"] for r in rs]
        st = [r["settledMs"] for r in rs]
        fd = [r["flyDistance"] for r in rs]
        lines.append(f"World creation {fmt_spread(wg)} ms; all chunks built after {fmt_spread(st)} ms "
                     f"(dirty left: {[r['settleDirtyLeft'] for r in rs]}); flew {fmt_spread(fd)} blocks")
        lines.append("")
        lines.append("| phase | " + " | ".join(m[1] for m in metrics) + " |")
        lines.append("|---" * (len(metrics) + 1) + "|")
        summary[key] = {"worldgenMs": wg, "settledMs": st, "flyDistance": fd, "phases": {}}
        for ph in PHASES:
            vals = {m: [r["phases"][ph][m] for r in rs if ph in r["phases"]] for m, _ in metrics}
            if not vals["fps"]:
                continue
            summary[key]["phases"][ph] = vals
            lines.append(f"| {ph} | " + " | ".join(fmt_spread(vals[m]) for m, _ in metrics) + " |")
        lines.append("")
    for r in contended:
        lines.append(f"Left out (busy PC at the start: CPU {r['loadBefore']['cpuPercent']}%, GPU {r['loadBefore']['gpuUtil']}%, "
                     f"{r['loadBefore'].get('busiest')}): {r['run']}")
    lines.append("")
    lines.append("Cells: median of runs, with the range in brackets when runs differ. Frame-part columns are mean ms per "
                 "frame (gen is inside tick and compile); gpu is the median GL_TIME_ELAPSED; cores is process CPU time / wall.")
    (label_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (label_dir / "summary.json").write_text(json.dumps({"runs": runs, "groups": summary}, indent=1))
    print("\n".join(lines))


def fmt_spread(vals):
    vals = [v for v in vals if v is not None]
    if not vals:
        return "-"
    med = statistics.median(vals)
    if len(vals) == 1 or min(vals) == max(vals):
        return fmt(med)
    return f"{fmt(med)} [{fmt(min(vals))}-{fmt(max(vals))}]"


def fmt(v):
    if isinstance(v, int) or (isinstance(v, float) and v.is_integer() and abs(v) >= 100):
        return str(int(v))
    return f"{v:.3g}" if abs(v) < 100 else f"{v:.0f}"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("label")
    r.add_argument("--configs", default="sdl,lwjgl2")
    r.add_argument("--distances", default="0,2", help="0 far, 1 normal, 2 short, 3 tiny")
    r.add_argument("--repeats", type=int, default=3)
    r.add_argument("--cooldown", type=int, default=30, help="seconds between runs (GPU and CPU)")
    r.add_argument("--timeout", type=int, default=600)
    r.add_argument("--seed", type=int, default=404)
    r.add_argument("--fps-limit", type=int, default=0, help="b1.7.3's Performance: 0 max fps, 1 balanced, 2 power saver")
    r.add_argument("--vsync", default="off", choices=["off", "on", "driver"],
                   help="off: swap interval 0 (measures the work); on: 1, as players have it; driver: as the game and its mods leave it")
    r.add_argument("--shown", action="store_true", help="sdl: show the window on the private desktop (default hidden; "
                   "frame times matched either way, 2026-11-01). LWJGL 2's window is always shown there")
    r.add_argument("--jfr", action="store_true", help="record a JFR profile of each run (profile.jfr)")
    r.add_argument("--keep-game", action="store_true", help="keep the game folder (world, logs) after the run")
    r.add_argument("--stand", type=float)
    r.add_argument("--turn", type=float)
    r.add_argument("--fly", type=float)
    r.add_argument("--settletimeout", type=float)
    r.add_argument("--jvm", nargs="*", help="extra JVM arguments")
    rp = sub.add_parser("report")
    rp.add_argument("label")
    args = ap.parse_args()
    sys.exit(cmd_run(args) if args.cmd == "run" else cmd_report(args))


if __name__ == "__main__":
    main()
