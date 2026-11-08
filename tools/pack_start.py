"""Starts a copy of a Prism instance zip unseen, as Prism would launch it, with the harness's start-up test driving it into a new world held open.

    python tools/pack_start.py <zip> <run dir> <label> [--style simple|complex] [--world-ticks N]

For checking a test pack (local/prism/FaithfulLibrary-dev-stagehand.zip) before the user imports it, e.g. with
`tools/stagehand.py --dir <game folder> --world-wait 60 locate tower` while the world is held. Always give stagehand.py
the --dir: on its own it finds the user's Prism instance. A game launch, so it takes a gwl.sh slot (`tools/gwl.sh`'s
--exec form). The zip is unpacked fresh into <run dir>/instance each time and the label's game folder is a new copy of
its .minecraft. Needs `./gw :harness:remapJar` built and the start-up test's sounds in local/cache/selftest-sounds.
Prints the game folder once it's set up, then waits for the game, which stops itself after the world ticks.
"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

ap = argparse.ArgumentParser()
ap.add_argument("zip")
ap.add_argument("run")
ap.add_argument("label")
ap.add_argument("--style", help="Buried Towers' style for the new world")
ap.add_argument("--world-ticks", type=int, default=2400, help="how long the world is held, at 20 a second")
ap.add_argument("--extra-mods", help="mods the pack has past a release pack's, by name, comma-separated, for the settings check; Stagehand for a zip named for it")
a = ap.parse_args()
extra = a.extra_mods if a.extra_mods is not None else ("Stagehand" if "stagehand" in Path(a.zip).name.lower() else "")

run = Path(a.run).resolve()
inst = run / "instance"
natives = run / "natives"
for d in (inst, natives):
    if d.exists():
        shutil.rmtree(d)
with zipfile.ZipFile(a.zip) as z:
    z.extractall(inst)
natives.mkdir(parents=True)
for jar in (inst / "libraries").glob("*natives-windows*.jar"):
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if not n.startswith("META-INF/") and not n.endswith("/"):
                (natives / Path(n).name).write_bytes(z.read(n))

harness_jars = [p for p in (REPO / "harness/build/libs").glob("harness-*.jar") if not p.name.endswith("-dev.jar")]
if not harness_jars:
    sys.exit("no harness jar: ./gw :harness:remapJar")
harness = max(harness_jars, key=lambda p: p.stat().st_mtime)

game = run / a.label
if game.exists():
    shutil.rmtree(game)
shutil.copytree(inst / ".minecraft", game)
shutil.copy(harness, game / "mods")
shutil.copytree(REPO / "local/cache/selftest-sounds/resources", game / "resources")
(game / "options.txt").write_text("viewDistance:3\n")
wave = str(game / "sound.wav").replace("\\", "/")
(game / "alsoft.ini").write_text(f"[general]\nchannels = stereo\nsample-type = int16\nfrequency = 44100\n[wave]\nfile = {wave}\n")
if a.style:
    (game / "config").mkdir(exist_ok=True)
    (game / "config" / "faithful-rules.properties").write_text(f"rule.buried-towers.style={a.style}\n")

cfg = (inst / "instance.cfg").read_text()
pack_args = next(l.split("=", 1)[1] for l in cfg.splitlines() if l.startswith("JvmArgs=")).split()
libs = inst / "libraries"
cp = [str(p) for p in sorted(libs.glob("*.jar")) if "natives" not in p.name and not p.name.startswith("intermediary-")]
cp += [str(next(libs.glob("intermediary-*.jar"))), str(REPO / "local/cache/minecraft/b1.7.3-client.jar")]
java = str(REPO / "local/tools/jdk21/bin/java.exe")
cmd = [java, "-Dmixin.debug.countInjections=true", "-Xmx1g", "-Doldfaithful.lwjgl.hidden=true", "-Doldfaithful.selftest.startup=true",
       f"-Djava.library.path={natives}", f"-Dorg.lwjgl.librarypath={natives}"] + pack_args + [
       "-Doldfaithful.selftest.libraryPack=on", "-Doldfaithful.selftest.expectSize=1000x600", "-Dorg.prismlauncher.window.dimensions=1000x600",
       "-Doldfaithful.selftest.expectMaximized=false", "-Doldfaithful.selftest.edition=Still Faithful:stillfaithful-polish",
       f"-Doldfaithful.selftest.packExtraMods={extra}", f"-Doldfaithful.selftest.worldTicks={a.world_ticks}", f"-Doldfaithful.selftest.stallMs={(a.world_ticks // 20 + 150) * 1000}",
       "-cp", os.pathsep.join(cp), "net.fabricmc.loader.impl.launch.knot.KnotClient", "--username", "OldFaithfulTest", "--gameDir", str(game)]
env = dict(os.environ, ALSOFT_DRIVERS="wave", ALSOFT_CONF=str(game / "alsoft.ini"))
timeout = a.world_ticks // 20 + 240
full = [sys.executable, str(REPO / "tools/private_desktop.py"), "--timeout", str(timeout), "--"] + cmd
print(game, flush=True)
with open(game / "harness-output.log", "w", encoding="utf-8") as log:
    rc = subprocess.call(full, cwd=game, env=env, stdout=log, stderr=subprocess.STDOUT)
print("exit", rc, flush=True)
sys.exit(rc)
