"""Our mods on Modrinth (docs/release.md > Modrinth): a release's jars as new versions, checked after upload.

    python tools/modrinth.py release <version> [--edition E] [--type beta|release|alpha]   upload the release's jars
    python tools/modrinth.py verify <version> [--edition E]                                 compare with SHA256SUMS

Editions: pure-faithful (the default; local/release/<version>/), old-faithful (local/release/old-faithful-<version>/)
and still-faithful (local/release/still-faithful-<version>/).

The token is read from local/modrinth-token.txt (git-ignored) and never printed. The projects belong to the Studio
Quire organisation; creating them was a one-off, done by hand through the same API.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.modrinth.com"
UA = "studio-quire/pure-faithful (hello@studioquire.org)"
# Per edition: its name, repository, release folder and mods: jar name -> (project slug, environment as Modrinth's
# v3 versions take it, dependencies as v2 versions take them, by project slug).
EDITIONS = {
    "pure-faithful": {
        "name": "Pure Faithful", "repo": "https://github.com/studio-quire/pure-faithful", "folder": "{v}",
        "mods": {
            "purefaithful-hardening": ("pure-faithful-hardening", "client_or_server", {}),
            "purefaithful-saves": ("pure-faithful-saves", "client_or_server", {}),
            "purefaithful-speed": ("pure-faithful-speed", "client_only", {}),
            "purefaithful-display": ("pure-faithful-display", "client_only", {}),
        },
    },
    "old-faithful": {
        "name": "Old Faithful", "repo": "https://github.com/studio-quire/old-faithful", "folder": "old-faithful-{v}",
        "mods": {
            # Some fixes need both sides (mining speed), most work on either alone.
            "oldfaithful-fixes": ("old-faithful-fixes", "client_or_server_prefers_both", {}),
            "oldfaithful-lighting": ("old-faithful-lighting", "client_or_server", {}),
            # Its window defaults are asked of Pure Faithful Display, when it's there.
            "oldfaithful-options": ("old-faithful-options", "client_only", {"pure-faithful-display": "optional"}),
        },
    },
    "still-faithful": {
        "name": "Still Faithful", "repo": "https://github.com/studio-quire/still-faithful", "folder": "still-faithful-{v}",
        "mods": {
            # World changes where the world runs, what you see on the client; glowstone's speed needs both.
            # Its settings appear on Old Faithful Options' screen; background saving needs Pure Faithful Saves.
            "stillfaithful-polish": ("still-faithful-polish", "client_or_server_prefers_both",
                                     {"old-faithful-options": "optional", "pure-faithful-saves": "optional"}),
        },
    },
}


def session():
    s = requests.Session()
    s.headers.update({"Authorization": (ROOT / "local" / "modrinth-token.txt").read_text(encoding="utf-8").strip(),
                      "User-Agent": UA})
    return s


def ok(r, what):
    if r.status_code >= 300:
        raise SystemExit(f"{what}: HTTP {r.status_code}: {r.text[:500]}")
    return r.json() if r.content else None


def folder_of(ed, version):
    return ROOT / "local" / "release" / ed["folder"].format(v=version)


def release(ed, version, kind):
    s = session()
    folder = folder_of(ed, version)
    for mod, (slug, env, deps) in ed["mods"].items():
        jar = folder / f"{mod}-{version}.jar"
        project = ok(s.get(f"{API}/v2/project/{slug}"), slug)
        if any(v["version_number"] == version for v in ok(s.get(f"{API}/v2/project/{slug}/version"), slug)):
            print(f"{slug} {version}: already there")
            continue
        dependencies = [{"project_id": ok(s.get(f"{API}/v2/project/{d}"), d)["id"], "dependency_type": dep_kind}
                        for d, dep_kind in deps.items()]
        # b1.7.3 takes the babric loader only: Modrinth refuses "fabric" for game versions before 18w43b.
        data = {"name": f"{ed['name']} {version}", "version_number": version,
                "changelog": f"Part of {ed['name']} {version}. The release's changelog: {ed['repo']}/blob/main/CHANGELOG.md",
                "dependencies": dependencies, "game_versions": ["b1.7.3"], "version_type": kind, "loaders": ["babric"],
                "featured": True, "project_id": project["id"], "file_parts": ["file"], "primary_file": "file"}
        v = ok(s.post(f"{API}/v2/version", files={"data": (None, json.dumps(data), "application/json"),
                                                  "file": (jar.name, jar.read_bytes(), "application/java-archive")}), slug)
        # Sides are per version now (v3's environment); without one, Modrinth shows "unknown".
        ok(s.patch(f"{API}/v3/version/{v['id']}", json={"environment": env}), slug + " environment")
        print(f"{slug} {version}: uploaded ({v['id']})")
    verify(ed, version)


def verify(ed, version):
    s = session()
    sums = {}
    for line in (folder_of(ed, version) / "SHA256SUMS.txt").read_text().splitlines():
        digest, name = line.split()
        sums[name] = digest
    bad = 0
    for mod, (slug, _, _) in ed["mods"].items():
        v = next((v for v in ok(s.get(f"{API}/v2/project/{slug}/version"), slug) if v["version_number"] == version), None)
        if v is None:
            print(f"{slug}: no {version}"); bad += 1; continue
        f = next(f for f in v["files"] if f["primary"])
        data = requests.get(f["url"], headers={"User-Agent": UA}).content
        same = hashlib.sha256(data).hexdigest() == sums.get(f["filename"])
        print(f"{slug} {version}: {f['filename']} {'matches SHA256SUMS' if same else 'DIFFERS'}")
        bad += not same
    return bad


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("release"); r.add_argument("version"); r.add_argument("--type", default="beta")
    v = sub.add_parser("verify"); v.add_argument("version")
    for p in (r, v):
        p.add_argument("--edition", default="pure-faithful", choices=sorted(EDITIONS))
    a = ap.parse_args()
    ed = EDITIONS[a.edition]
    if a.cmd == "release":
        release(ed, a.version, a.type)
    else:
        sys.exit(1 if verify(ed, a.version) else 0)


if __name__ == "__main__":
    main()
