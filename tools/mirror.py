"""Old Faithful's artifact mirror: every jar the build and the game resolve, in one Maven repository we hold (docs/mirror.md).

    python tools/mirror.py sync      fetch everything pinned in gradle/verification-metadata.xml that isn't ours
    python tools/mirror.py natives   package our own builds (local/natives, from natives/build.py) as artifacts
    python tools/mirror.py verify    re-hash every pinned file in the mirror

The mirror is local/mirror/maven/. Gradle and the dev instance resolve only from it. Upstream files are copied
byte for byte and kept only if their SHA-256 matches the pin; our own artifacts are packaged deterministically, so
the same native build gives the same jar, and Gradle's verification pins them like everything else.
"""
import hashlib
import io
import json
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIRROR = ROOT / "local" / "mirror" / "maven"
NATIVES = ROOT / "local" / "natives"
METADATA = ROOT / "gradle" / "verification-metadata.xml"
NS = {"v": "https://schema.gradle.org/dependency-verification"}

# Where each group comes from upstream: the same split as settings.gradle had before the mirror.
UPSTREAM = [
    ("babric", "https://maven.glass-launcher.net/babric/"),
    ("net.glasslauncher", "https://maven.glass-launcher.net/releases/"),
    ("net.fabricmc", "https://maven.fabricmc.net/"),
    ("", "https://repo.maven.apache.org/maven2/"),
]
OURS = "org.studioquire."

# Our builds, as artifacts. Versions carry our build revision after the upstream version.
OUR_GROUP = "org.studioquire.oldfaithful.natives"
LWJGL = {"source": "lwjgl2", "upstream": "2.9.4+legacyfabric.18", "version": "2.9.4+legacyfabric.18.oldfaithful.1"}
OPENAL = {"source": "openal-soft", "upstream": "1.25.2", "version": "1.25.2+oldfaithful.1"}
SDL3 = {"source": "sdl3", "upstream": "3.4.18", "version": "3.4.18+oldfaithful.1"}
LWJGL3 = {"source": "lwjgl3", "upstream": "3.4.3", "version": "3.4.3+oldfaithful.1"}
# The parts of LWJGL 3's tree our two DLLs are compiled from, and the build configuration that says how; the whole
# tree (Kotlin templates, Java, every other binding) is at the pinned commit.
LWJGL3_SOURCE_PATHS = ["LICENSE.md", "README.md", "build.xml", "config", "modules/lwjgl/core/src/main/c",
                       "modules/lwjgl/core/src/generated/c", "modules/lwjgl/jawt/src/generated/c",
                       "modules/lwjgl/opengl/src/main/c", "modules/lwjgl/opengl/src/generated/c"]
LIBFFI_CONFIG = ROOT / "natives" / "libffi-fficonfig-msvc-x64.h"

# Zip entries get one fixed timestamp so packaging is deterministic.
EPOCH = (1980, 2, 1, 0, 0, 0)


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def upstream_for(group):
    for prefix, url in UPSTREAM:
        if group == prefix or group.startswith(prefix + ".") or prefix == "":
            return url
    raise ValueError(group)


def pinned():
    """(group, name, version, file, sha256) for every artifact in Gradle's verification metadata."""
    root = ET.parse(METADATA).getroot()
    for c in root.iterfind("v:components/v:component", NS):
        for a in c.iterfind("v:artifact", NS):
            yield c.get("group"), c.get("name"), c.get("version"), a.get("name"), a.find("v:sha256", NS).get("value")


def path_of(group, name, version, file):
    return MIRROR / group.replace(".", "/") / name / version / file


def write_artifact(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    # Checksum files beside each artifact, as Maven repositories serve them, so the folder can be published as is.
    path.with_name(path.name + ".sha1").write_text(hashlib.sha1(data).hexdigest())
    path.with_name(path.name + ".sha256").write_text(sha256_bytes(data))


def sync():
    fetched = present = 0
    missing_ours = []
    for group, name, version, file, digest in pinned():
        target = path_of(group, name, version, file)
        if target.exists() and sha256_bytes(target.read_bytes()) == digest:
            present += 1
            continue
        if group.startswith(OURS):
            missing_ours.append(f"{group}:{name}:{version} {file}")
            continue
        url = upstream_for(group) + str(target.relative_to(MIRROR)).replace("\\", "/")
        url = url.replace("+", "%2B")
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        actual = sha256_bytes(data)
        if actual != digest:
            sys.exit(f"hash mismatch for {url}: got {actual}, pinned {digest}; nothing written")
        write_artifact(target, data)
        fetched += 1
        print(f"fetched {group}:{name}:{version} {file}")
    print(f"{present} present, {fetched} fetched")
    if missing_ours:
        print("our own artifacts not in the mirror yet (run `natives`):")
        for m in missing_ours:
            print("  " + m)


def deterministic_jar(entries, manifest=None):
    """A jar from {name: bytes}, sorted, every entry stamped with EPOCH."""
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        names = sorted(entries)
        if manifest is not None:
            names = ["META-INF/MANIFEST.MF"] + [n for n in names if n != "META-INF/MANIFEST.MF"]
            entries = dict(entries, **{"META-INF/MANIFEST.MF": manifest})
        for n in names:
            info = zipfile.ZipInfo(n, EPOCH)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, entries[n])
    return out.getvalue()


def rejar(path, extra):
    """The classes and resources of a jar Gradle built, repacked deterministically, plus extra entries."""
    with zipfile.ZipFile(path) as z:
        entries = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
    return deterministic_jar(dict(entries, **extra))


def pom(name, version, description, licence, licence_url):
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{OUR_GROUP}</groupId>
  <artifactId>{name}</artifactId>
  <version>{version}</version>
  <description>{description}</description>
  <licenses><license><name>{licence}</name><url>{licence_url}</url></license></licenses>
</project>
""".encode()


def native_build(source, upstream_version, platform="windows-x64"):
    d = NATIVES / source / upstream_version / platform
    info = d / "build-info.json"
    if not info.exists():
        command = "linux-x64" if platform == "linux-x64" else source
        sys.exit(f"no build in {d}: run `python natives/build.py {command}` first")
    record = json.loads(info.read_text())
    for file, digest in record["sha256"].items():
        if sha256_bytes((d / file).read_bytes()) != digest:
            sys.exit(f"{d / file} doesn't match its build-info.json")
    return d, record


def source_tree(source, commit):
    src = ROOT / "local" / "src" / source
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=src, capture_output=True, text=True, check=True).stdout.strip()
    if head != commit:
        sys.exit(f"local/src/{source} is at {head}, the build was from {commit}")
    return src


def source_archive(src, commit, prefix, paths=()):
    """The pinned commit's tree (or some paths of it) as a zip; git stamps entries with the commit time, so it's
    deterministic."""
    return subprocess.run(["git", "archive", "--format=zip", f"--prefix={prefix}/", commit, "--", *paths], cwd=src,
                          capture_output=True, check=True).stdout


def merged_zip(archives, extra):
    """Several zips' entries (keeping their names, times and modes) plus extra {name: bytes}, in one zip."""
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for data in archives:
            with zipfile.ZipFile(io.BytesIO(data)) as part:
                for info in part.infolist():
                    entry = zipfile.ZipInfo(info.filename, info.date_time)
                    entry.compress_type = zipfile.ZIP_DEFLATED if not info.is_dir() else zipfile.ZIP_STORED
                    entry.external_attr = info.external_attr
                    z.writestr(entry, part.read(info))
        for name in sorted(extra):
            entry = zipfile.ZipInfo(name, EPOCH)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            z.writestr(entry, extra[name])
    return out.getvalue()


def notices(src, files):
    """Licence texts for META-INF/licenses/: a list of paths (kept by file name) or {jar name: path}."""
    if not isinstance(files, dict):
        files = {Path(f).name: f for f in files}
    return {f"META-INF/licenses/{name}": (src / f).read_bytes() for name, f in files.items()}


def natives():
    lwjgl_dir, lwjgl_info = native_build(LWJGL["source"], LWJGL["upstream"])
    openal_dir, openal_info = native_build(OPENAL["source"], OPENAL["upstream"])
    v, ov = LWJGL["version"], OPENAL["version"]
    lwjgl_commit, openal_commit = lwjgl_info["source"]["commit"], openal_info["source"]["commit"]
    lwjgl_src, openal_src = source_tree("lwjgl2", lwjgl_commit), source_tree("openal-soft", openal_commit)
    # BSD asks for the notice with binaries; LGPL-2.1 asks for the licence and the corresponding source, which the
    # -sources archives beside the natives provide (docs/mirror.md).
    lwjgl_notices = notices(lwjgl_src, ["doc/LICENSE"])
    openal_notices = notices(openal_src, ["COPYING", "BSD-3Clause", "LICENSE-pffft", "fmt-11.2.0/LICENSE"])
    bsd = ("BSD-3-Clause (LWJGL 2)", "https://github.com/Legacy-Fabric/lwjgl/blob/master/LICENSE")
    lwjgl_desc = f"LWJGL 2, Legacy Fabric's {LWJGL['upstream']} built for Old Faithful from commit {lwjgl_commit}"
    artifacts = {
        ("lwjgl", v, f"lwjgl-{v}.jar"): rejar(lwjgl_dir / "lwjgl.jar", lwjgl_notices),
        ("lwjgl", v, f"lwjgl-{v}.pom"): pom("lwjgl", v, lwjgl_desc, *bsd),
        ("lwjgl_util", v, f"lwjgl_util-{v}.jar"): rejar(lwjgl_dir / "lwjgl_util.jar", lwjgl_notices),
        ("lwjgl_util", v, f"lwjgl_util-{v}.pom"): pom("lwjgl_util", v, lwjgl_desc, *bsd),
        ("lwjgl-platform", v, f"lwjgl-platform-{v}-natives-windows.jar"): deterministic_jar(
            {"lwjgl-windows-amd64.dll": (lwjgl_dir / "lwjgl-windows-amd64.dll").read_bytes(),
             "META-INF/build-info.json": (lwjgl_dir / "build-info.json").read_bytes(), **lwjgl_notices}),
        ("lwjgl-platform", v, f"lwjgl-platform-{v}-sources.zip"): source_archive(
            lwjgl_src, lwjgl_commit, f"lwjgl-{LWJGL['upstream']}"),
        ("lwjgl-platform", v, f"lwjgl-platform-{v}.pom"): pom("lwjgl-platform", v, lwjgl_desc + " (natives)", *bsd),
        ("openal-soft", ov, f"openal-soft-{ov}-natives-windows.jar"): deterministic_jar(
            {"OpenAL-amd64.dll": (openal_dir / "OpenAL-amd64.dll").read_bytes(),
             "META-INF/build-info.json": (openal_dir / "build-info.json").read_bytes(), **openal_notices}),
        ("openal-soft", ov, f"openal-soft-{ov}-sources.zip"): source_archive(
            openal_src, openal_commit, f"openal-soft-{OPENAL['upstream']}"),
        ("openal-soft", ov, f"openal-soft-{ov}.pom"): pom(
            "openal-soft", ov, f"OpenAL Soft {OPENAL['upstream']} built for Old Faithful from commit "
            f"{openal_commit}", "LGPL-2.1-or-later", "https://github.com/kcat/openal-soft/blob/master/COPYING"),
        # The same DLL under the name LWJGL 3 loads by default; same build, same sources archive.
        ("openal-soft", ov, f"openal-soft-{ov}-natives-windows-lwjgl3.jar"): deterministic_jar(
            {"OpenAL.dll": (openal_dir / "OpenAL-amd64.dll").read_bytes(),
             "META-INF/build-info.json": (openal_dir / "build-info.json").read_bytes(), **openal_notices}),
    }
    artifacts.update(lwjgl3_artifacts())
    artifacts.update(linux_artifacts(openal_notices))
    for (name, version, file), data in artifacts.items():
        target = path_of(OUR_GROUP, name, version, file)
        write_artifact(target, data)
        print(f"{sha256_bytes(data)}  {OUR_GROUP}:{name}:{version} {file}")


def component_notices(sdl_src, lwjgl_src, libffi_src):
    """Notices for SDL3's and LWJGL 3's natives on every platform. zlib asks for nothing in binaries; HIDAPI (used
    under its BSD or original licence) and yuv2rgb (BSD-3-Clause) are compiled into SDL and ask for their notices;
    libffi (MIT) is linked into LWJGL's core library."""
    sdl = notices(sdl_src, {"LICENSE.txt": "LICENSE.txt", "hidapi-LICENSE.txt": "src/hidapi/LICENSE.txt",
                            "hidapi-LICENSE-bsd.txt": "src/hidapi/LICENSE-bsd.txt",
                            "hidapi-LICENSE-orig.txt": "src/hidapi/LICENSE-orig.txt",
                            "yuv2rgb-LICENSE": "src/video/yuv2rgb/LICENSE"})
    lwjgl = {**notices(lwjgl_src, ["LICENSE.md"]), **notices(libffi_src, {"libffi-LICENSE": "LICENSE"})}
    return sdl, lwjgl


def lwjgl3_artifacts():
    """SDL3 and LWJGL 3's natives, for the phase-3 windowing layer (docs/natives.md)."""
    sdl_dir, sdl_info = native_build(SDL3["source"], SDL3["upstream"])
    lwjgl_dir, lwjgl_info = native_build(LWJGL3["source"], LWJGL3["upstream"])
    sv, lv = SDL3["version"], LWJGL3["version"]
    sdl_commit, lwjgl_commit = sdl_info["source"]["commit"], lwjgl_info["source"]["commit"]
    libffi = lwjgl_info["libffi"]["source"]
    sdl_src, lwjgl_src = source_tree("sdl3", sdl_commit), source_tree("lwjgl3", lwjgl_commit)
    libffi_src = source_tree("libffi", libffi["commit"])
    sdl_notices, lwjgl_notices = component_notices(sdl_src, lwjgl_src, libffi_src)
    lwjgl_desc = (f"LWJGL {LWJGL3['upstream']}'s core and OpenGL JNI libraries built for Old Faithful from commit "
                  f"{lwjgl_commit}, with libffi from LWJGL-CI/libffi {libffi['commit']}")
    return {
        ("sdl3", sv, f"sdl3-{sv}-natives-windows.jar"): deterministic_jar(
            {"SDL3.dll": (sdl_dir / "SDL3.dll").read_bytes(),
             "META-INF/build-info.json": (sdl_dir / "build-info.json").read_bytes(), **sdl_notices}),
        ("sdl3", sv, f"sdl3-{sv}-sources.zip"): source_archive(sdl_src, sdl_commit, f"SDL-{SDL3['upstream']}"),
        ("sdl3", sv, f"sdl3-{sv}.pom"): pom(
            "sdl3", sv, f"SDL {SDL3['upstream']} built for Old Faithful from commit {sdl_commit}", "Zlib",
            "https://github.com/libsdl-org/SDL/blob/main/LICENSE.txt"),
        ("lwjgl3", lv, f"lwjgl3-{lv}-natives-windows.jar"): deterministic_jar(
            {"lwjgl.dll": (lwjgl_dir / "lwjgl.dll").read_bytes(),
             "lwjgl_opengl.dll": (lwjgl_dir / "lwjgl_opengl.dll").read_bytes(),
             "META-INF/build-info.json": (lwjgl_dir / "build-info.json").read_bytes(), **lwjgl_notices}),
        # LWJGL's C sources and build configuration, libffi's whole tree, and the fficonfig.h we build it with.
        ("lwjgl3", lv, f"lwjgl3-{lv}-sources.zip"): merged_zip(
            [source_archive(lwjgl_src, lwjgl_commit, f"lwjgl3-{LWJGL3['upstream']}", LWJGL3_SOURCE_PATHS),
             source_archive(libffi_src, libffi["commit"], f"libffi-{libffi['version']}-{libffi['tag']}")],
            # LF whatever git checked out, so the zip is the same on every clone.
            {f"old-faithful/natives/{LIBFFI_CONFIG.name}": LIBFFI_CONFIG.read_bytes().replace(b"\r\n", b"\n")}),
        ("lwjgl3", lv, f"lwjgl3-{lv}.pom"): pom(
            "lwjgl3", lv, lwjgl_desc, "BSD-3-Clause (LWJGL), MIT (libffi)",
            "https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md"),
    }


def linux_notices(build_dir):
    """The extra notices the Linux build wrote beside a library (natives/linux/build_linux.py write_notices)."""
    d = build_dir / "licenses"
    return {f"META-INF/licenses/{f.name}": f.read_bytes() for f in sorted(d.iterdir())} if d.exists() else {}


def linux_artifacts(openal_notices):
    """The component's Linux x64 natives, as natives-linux jars (Prism's classifier for Linux x64). Same versions,
    poms and source archives as the Windows builds: one source, two platforms."""
    sdl_dir, sdl_info = native_build(SDL3["source"], SDL3["upstream"], "linux-x64")
    lwjgl_dir, lwjgl_info = native_build(LWJGL3["source"], LWJGL3["upstream"], "linux-x64")
    openal_dir, openal_info = native_build(OPENAL["source"], OPENAL["upstream"], "linux-x64")
    sdl_src, lwjgl_src = source_tree("sdl3", sdl_info["source"]["commit"]), source_tree("lwjgl3", lwjgl_info["source"]["commit"])
    libffi_src = source_tree("libffi", lwjgl_info["libffi"]["source"]["commit"])
    sdl_notices, lwjgl_notices = component_notices(sdl_src, lwjgl_src, libffi_src)
    sv, lv, ov = SDL3["version"], LWJGL3["version"], OPENAL["version"]

    def jar(build_dir, libs, extra_notices):
        return deterministic_jar({**{lib: (build_dir / lib).read_bytes() for lib in libs},
                                  "META-INF/build-info.json": (build_dir / "build-info.json").read_bytes(),
                                  **extra_notices, **linux_notices(build_dir)})
    return {
        ("sdl3", sv, f"sdl3-{sv}-natives-linux.jar"): jar(sdl_dir, ["libSDL3.so"], sdl_notices),
        ("lwjgl3", lv, f"lwjgl3-{lv}-natives-linux.jar"): jar(lwjgl_dir, ["liblwjgl.so", "liblwjgl_opengl.so"],
                                                             lwjgl_notices),
        ("openal-soft", ov, f"openal-soft-{ov}-natives-linux.jar"): jar(openal_dir, ["libopenal.so"], openal_notices),
    }


def verify():
    bad = checked = 0
    for group, name, version, file, digest in pinned():
        target = path_of(group, name, version, file)
        if not target.exists():
            print(f"missing {target.relative_to(MIRROR)}")
            bad += 1
        elif sha256_bytes(target.read_bytes()) != digest:
            print(f"MISMATCH {target.relative_to(MIRROR)}")
            bad += 1
        checked += 1
    print(f"{checked} pinned artifacts, {bad} missing or wrong")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    commands = {"sync": sync, "natives": natives, "verify": verify}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        sys.exit(__doc__)
    commands[sys.argv[1]]()
