"""Builds the LWJGL component's Linux x64 natives inside natives/linux/Dockerfile's image (docs/natives.md > Linux).

Run by `python natives/build.py linux-x64` on the host, which mounts natives/ at /natives (read-only) and an output
folder at /out. Here: fetch the pinned sources (SOURCES in natives/build.py), build everything twice in two folders
with different paths, require byte-identical results, then write each library with a build-info.json to
/out/<name>/. Libraries: liblwjgl.so and liblwjgl_opengl.so (LWJGL 3.4.3, libffi linked in), libSDL3.so,
libopenal.so (OpenAL Soft). Everything a library talks to on the player's system (X11, Wayland, libdecor, PipeWire,
PulseAudio, ALSA, JACK, D-Bus, IBus, udev, libusb, libGL) is loaded at runtime, which the build checks.
"""
import concurrent.futures
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, "/natives")
from build import SOURCES  # noqa: E402  (natives/build.py: the pins, shared with the Windows build)

JOBS = 4  # the host's CPU is shared
JDK = Path("/opt/jdk25")
TOOLSET = "/opt/rh/gcc-toolset-15/root/usr/bin"
WORKS = [Path("/w/a"), Path("/w/second-build-somewhere-else")]

# What SDL must have found: these drivers, each loaded at runtime (the *_DYNAMIC defines name the soname it dlopens).
SDL_REQUIRED = ["SDL_VIDEO_DRIVER_X11", "SDL_VIDEO_DRIVER_X11_DYNAMIC", "SDL_VIDEO_DRIVER_WAYLAND",
                "SDL_VIDEO_DRIVER_WAYLAND_DYNAMIC", "SDL_VIDEO_DRIVER_WAYLAND_DYNAMIC_LIBDECOR",
                "SDL_VIDEO_DRIVER_X11_XINPUT2", "SDL_VIDEO_DRIVER_X11_XRANDR", "SDL_VIDEO_DRIVER_X11_XFIXES",
                "SDL_VIDEO_DRIVER_X11_XCURSOR", "SDL_VIDEO_OPENGL", "SDL_VIDEO_OPENGL_GLX", "SDL_VIDEO_OPENGL_EGL",
                "SDL_AUDIO_DRIVER_PIPEWIRE_DYNAMIC", "SDL_AUDIO_DRIVER_PULSEAUDIO_DYNAMIC",
                "SDL_AUDIO_DRIVER_ALSA_DYNAMIC", "SDL_USE_IME", "SDL_JOYSTICK_LINUX", "SDL_JOYSTICK_HIDAPI",
                "SDL_UDEV_DYNAMIC", "HAVE_DBUS_DBUS_H", "HAVE_IBUS_IBUS_H"]
# The only libraries ours may link directly: glibc's and, for OpenAL Soft (C++), the system's libstdc++ and libgcc.
ALLOWED_NEEDED = {"libc.so.6", "libm.so.6", "libdl.so.2", "libpthread.so.0", "librt.so.1", "ld-linux-x86-64.so.2",
                  "libstdc++.so.6", "libgcc_s.so.1"}


def run(cmd, cwd=None, env=None, capture=False):
    cmd = [str(c) for c in cmd]
    if not capture:
        print("+", " ".join(cmd) if len(cmd) < 40 else " ".join(cmd[:20]) + f" ... ({len(cmd)} args)", flush=True)
    r = subprocess.run(cmd, cwd=cwd, env=env, check=True, text=True, capture_output=capture)
    return r.stdout if capture else None


def toolchain_env():
    env = dict(os.environ)
    env["PATH"] = TOOLSET + ":" + env["PATH"]
    env["LC_ALL"] = "C"
    env["SOURCE_DATE_EPOCH"] = "0"
    return env


ENV = toolchain_env()


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def fetch(name):
    src = SOURCES[name]
    dest = Path("/src") / name
    if not dest.exists():
        dest.mkdir(parents=True)
        run(["git", "init", "-q"], cwd=dest)
        run(["git", "fetch", "-q", "--depth", "1", src["repo"], src["commit"]], cwd=dest)
        run(["git", "checkout", "-q", "--detach", "FETCH_HEAD"], cwd=dest)
    head = run(["git", "rev-parse", "HEAD"], cwd=dest, capture=True).strip()
    if head != src["commit"]:
        sys.exit(f"{name}: checked out {head}, expected {src['commit']}")
    return dest


def fresh(path):
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
    return path


def prefix_map(src, build, name):
    """GCC flags that replace the source and build folders' absolute paths with neutral names in the output."""
    return [f"-ffile-prefix-map={src}={name}", f"-ffile-prefix-map={build}=build"]


def strip(path):
    run(["strip", "--strip-unneeded", path], env=ENV)


# --- libffi and LWJGL 3 ---------------------------------------------------------------------------------------------

def build_libffi(work):
    """libffi as LWJGL-CI/libffi's CI builds it for Linux: autogen, configure --disable-shared --with-pic, make; the
    convenience archive is what LWJGL links into liblwjgl.so."""
    src = work / "src" / "libffi"
    shutil.copytree(Path("/src/libffi"), src)
    run(["./autogen.sh"], cwd=src, env=ENV)
    env = dict(ENV, CFLAGS="-O2 -g0 " + " ".join(prefix_map(src, src, "libffi")))
    run(["./configure", "--disable-shared", "--with-pic", "--host=x86_64-pc-linux-gnu", "--disable-docs"], cwd=src, env=env)
    run(["make", f"-j{JOBS}"], cwd=src, env=env)
    return src / "x86_64-pc-linux-gnu" / ".libs" / "libffi_convenience.a"


def build_lwjgl3(work):
    """liblwjgl.so and liblwjgl_opengl.so as LWJGL's config/linux/build.xml builds them: the same sources, flags,
    version script, link and strip; files in sorted order; one gcc per file, JOBS at a time."""
    libffi = build_libffi(work)
    src = work / "src" / "lwjgl3"
    shutil.copytree(Path("/src/lwjgl3"), src)
    lw = Path("modules/lwjgl")
    out = fresh(work / "build" / "lwjgl3")
    base = ["-c", "-std=gnu11", "-m64", "-O3", f"-flto={JOBS}", "-fPIC"]
    tail = ["-pthread", "-DNDEBUG", "-DLWJGL_LINUX", "-DLWJGL_x64", "-U_FORTIFY_SOURCE", "-D_FORTIFY_SOURCE=0",
            "-D_GNU_SOURCE", "-D_FILE_OFFSET_BITS=64", f"-I{JDK}/include", f"-I{JDK}/include/linux",
            f"-I{lw}/core/src/main/c", f"-I{lw}/core/src/main/c/linux"] + prefix_map(src, out, "lwjgl3")

    def compile_all(dest, flags, extra, sources):
        dest.mkdir(parents=True, exist_ok=True)

        def one(s):
            run(["gcc"] + base + flags + tail + extra + ["-o", dest / (Path(s).stem + ".o"), s], cwd=src, env=ENV)
        with concurrent.futures.ThreadPoolExecutor(JOBS) as pool:
            list(pool.map(one, sources))

    def c_files(*dirs, exclude=()):
        files = []
        for d in dirs:
            files += [f for f in (src / d).glob("*.c") if f.name not in exclude]
        return sorted(str(f.relative_to(src)) for f in files)

    def link(name, objdir, libs=()):
        lib = out / f"lib{name}.so"
        objects = sorted(objdir.glob("*.o"))
        run(["gcc"] + objects + ["-shared", "-m64", "-z", "noexecstack", "-O3", f"-flto={JOBS}", "-fPIC", "-pthread",
                                 "-o", lib, "-Wl,--no-undefined", f"-Wl,--version-script,{src / 'config/linux/version.script'}"]
            + list(libs) + prefix_map(src, out, "lwjgl3"), cwd=out, env=ENV)
        strip(lib)
        return lib

    core_flags = ["-Werror", "-Wfatal-errors", "-Wall", "-Wextra", "-pedantic"]
    uring = lw / "core/src/main/c/linux/liburing"
    # build.xml's beforeCompile: liburing with the compile macro's default (empty) flags.
    compile_all(out / "core", [], [f"-I{uring}", f"-I{uring}/include", "-DCONFIG_HAVE_MEMFD_CREATE"],
                c_files(uring))
    compile_all(out / "core", core_flags,
                [f"-I{lw}/core/src/main/c/libffi", f"-I{lw}/core/src/main/c/libffi/x86", "-DX86_64",
                 f"-I{uring}", f"-I{uring}/include"],
                c_files(lw / "core/src/main/c", lw / "core/src/generated/c", lw / "core/src/generated/c/linux",
                        lw / "jawt/src/generated/c"))
    core = link("lwjgl", out / "core", [libffi, "-ldl"])
    compile_all(out / "opengl", ["-Werror", "-Wfatal-errors"], [f"-I{lw}/opengl/src/main/c"],
                c_files(lw / "opengl/src/generated/c", exclude={"org_lwjgl_opengl_WGL.c"}))
    gl = link("lwjgl_opengl", out / "opengl")
    return {"liblwjgl.so": core, "liblwjgl_opengl.so": gl}, {
        "compile": base + ["<module flags>"] + [t for t in tail if not t.startswith(("-I", "-ffile-prefix-map"))],
        "core_flags": core_flags, "opengl_flags": ["-Werror", "-Wfatal-errors"],
        "link": ["-shared", "-m64", "-z", "noexecstack", "-O3", f"-flto={JOBS}", "-fPIC", "-pthread",
                 "-Wl,--no-undefined", "-Wl,--version-script,config/linux/version.script"],
        "core_libs": ["libffi_convenience.a (LWJGL-CI/libffi, configure --disable-shared --with-pic)", "-ldl"],
        "strip": "strip --strip-unneeded",
    }


# --- SDL3 -----------------------------------------------------------------------------------------------------------

def build_sdl3(work):
    src = work / "src" / "sdl3"
    shutil.copytree(Path("/src/sdl3"), src)
    build = fresh(work / "build" / "sdl3")
    sdl = SOURCES["sdl3"]
    cflags = ["-U_FORTIFY_SOURCE", "-D_FORTIFY_SOURCE=0"]
    options = [
        "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
        "-DSDL_SHARED=ON", "-DSDL_STATIC=OFF", "-DSDL_TEST_LIBRARY=OFF", "-DSDL_TESTS=OFF", "-DSDL_EXAMPLES=OFF",
        "-DSDL_INSTALL=OFF",
        f"-DSDL_REVISION=SDL-{sdl['version']}-{sdl['commit'][:7]}", "-DSDL_VENDOR_INFO=Old Faithful",
    ]
    flags = " ".join(cflags + prefix_map(src, build, "SDL"))
    run(["cmake", "-S", src, "-B", build] + options + [f"-DCMAKE_C_FLAGS={flags}"], env=ENV)
    config = next(build.rglob("SDL_build_config.h")).read_text()
    missing = [d for d in SDL_REQUIRED if not re.search(rf"^#define {d}\b", config, re.M)]
    if missing:
        sys.exit(f"SDL is missing {missing}: a -devel package or /opt/deps library isn't where its check looks")
    dynamic = dict(re.findall(r'^#define (SDL_\w+_DYNAMIC\w*) "([^"]+)"', config, re.M))
    run(["cmake", "--build", build, "--parallel", str(JOBS)], env=ENV)
    lib = build / "libSDL3.so.0"
    lib = lib.resolve()
    strip(lib)
    return {"libSDL3.so": lib}, {"cmake_options": options, "c_flags": cflags, "runtime_loaded": dynamic}


# --- OpenAL Soft ----------------------------------------------------------------------------------------------------

def build_openal(work):
    src = work / "src" / "openal-soft"
    shutil.copytree(Path("/src/openal-soft"), src)
    build = fresh(work / "build" / "openal-soft")
    options = [
        "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
        "-DALSOFT_UTILS=OFF", "-DALSOFT_EXAMPLES=OFF", "-DALSOFT_TESTS=OFF", "-DALSOFT_INSTALL=OFF",
        # Fail rather than silently drop a Linux output backend; all three are dlopened (ALSOFT_DLOPEN, the default).
        "-DALSOFT_REQUIRE_PIPEWIRE=ON", "-DALSOFT_REQUIRE_PULSEAUDIO=ON", "-DALSOFT_REQUIRE_ALSA=ON",
        "-DALSOFT_DLOPEN=ON",
        "-DALSOFT_EAX=OFF", "-DALSOFT_ENABLE_MODULES=OFF",
        # AlmaLinux 8's DBus1Config.cmake overwrites PKG_CONFIG_PATH and PKG_CONFIG_LIBDIR, which hides /opt/deps'
        # PipeWire from the checks after it; without it OpenAL Soft finds D-Bus (for RTKit) through pkg-config.
        "-DCMAKE_DISABLE_FIND_PACKAGE_DBus1=ON",
    ]
    flags = " ".join(prefix_map(src, build, "openal-soft"))
    run(["cmake", "-S", src, "-B", build] + options + [f"-DCMAKE_C_FLAGS={flags}", f"-DCMAKE_CXX_FLAGS={flags}"], env=ENV)
    config = "".join(p.read_text() for p in build.glob("config*.h"))
    have = sorted(set(re.findall(r"^#define (HAVE_(?:PIPEWIRE|PULSEAUDIO|ALSA|OSS|JACK|WAVE|RTKIT|DBUS)\w*)", config, re.M)))
    missing = [h for h in ("HAVE_PIPEWIRE", "HAVE_PULSEAUDIO", "HAVE_ALSA", "HAVE_WAVE") if h not in have]
    if missing:
        sys.exit(f"OpenAL Soft is missing {missing}")
    run(["cmake", "--build", build, "--parallel", str(JOBS)], env=ENV)
    lib = (build / "libopenal.so").resolve()
    strip(lib)
    return {"libopenal.so": lib}, {"cmake_options": options, "features": have}


BUILDERS = {"lwjgl3": build_lwjgl3, "sdl3": build_sdl3, "openal-soft": build_openal}


# --- checks and records ---------------------------------------------------------------------------------------------

def elf_facts(path):
    dyn = run(["readelf", "-d", path], env=ENV, capture=True)
    needed = re.findall(r"\(NEEDED\)\s+Shared library: \[([^\]]+)\]", dyn)
    soname = re.findall(r"\(SONAME\)\s+Library soname: \[([^\]]+)\]", dyn)
    syms = run(["objdump", "-T", path], env=ENV, capture=True)

    def newest(prefix):
        versions = {tuple(int(x) for x in v.split(".")) for v in re.findall(rf"\b{prefix}_([0-9.]+)\b", syms)}
        return ".".join(map(str, max(versions))) if versions else None
    bad = [n for n in needed if n not in ALLOWED_NEEDED]
    if bad:
        sys.exit(f"{path} links {bad} directly; everything on the player's system must be loaded at runtime")
    return {"needed": needed, "soname": soname[0] if soname else None, "newest_glibc": newest("GLIBC"),
            "newest_glibcxx": newest("GLIBCXX"), "newest_cxxabi": newest("CXXABI")}


def write_notices(name, out):
    """Notices for code compiled into the Linux libraries beyond what the Windows ones contain (mirror.py adds each
    project's own licence as on Windows): PipeWire's and libdecor's headers (MIT, inline code in SDL and OpenAL
    Soft), and for SDL the Wayland protocol code it generates from the XML files in its tree (each carries its
    own permissive notice)."""
    if name not in ("sdl3", "openal-soft"):
        return
    licenses = fresh(out / "licenses")
    shutil.copyfile("/opt/deps/licenses/pipewire-COPYING", licenses / "pipewire-COPYING")
    if name == "sdl3":
        shutil.copyfile("/opt/deps/licenses/libdecor-LICENSE", licenses / "libdecor-LICENSE")
        blocks = []
        for xml in sorted(Path("/src/sdl3/wayland-protocols").glob("*.xml")):
            m = re.search(r"<copyright>(.*?)</copyright>", xml.read_text(encoding="utf-8"), re.S)
            if m:
                text = "\n".join(line.strip() for line in m.group(1).strip().splitlines())
                blocks.append(f"== {xml.name} ==\n{text}\n")
        (licenses / "wayland-protocols-NOTICES.txt").write_text(
            "Notices of the Wayland protocol files in SDL's wayland-protocols/ folder, whose generated code is "
            "compiled into libSDL3.so.\n\n" + "\n".join(blocks))


def toolchain():
    gcc = run(["gcc", "--version"], env=ENV, capture=True).splitlines()[0]
    ld = run(["ld", "--version"], env=ENV, capture=True).splitlines()[0]
    cmake = run(["cmake", "--version"], env=ENV, capture=True).splitlines()[0]
    ninja = run(["ninja", "--version"], env=ENV, capture=True).strip()
    glibc = run(["ldd", "--version"], env=ENV, capture=True).splitlines()[0]
    release = Path("/etc/almalinux-release").read_text().strip()
    packages = run(["rpm", "-qa", "--qf", "%{NAME}-%{EPOCHNUM}:%{VERSION}-%{RELEASE}.%{ARCH}\n"], env=ENV, capture=True)
    lock = "\n".join(sorted(packages.split())) + "\n"
    expected = Path("/natives/linux/packages-x64.lock")
    note = None
    if not expected.exists():
        note = "no packages-x64.lock to compare with"
    elif expected.read_text().replace("\r\n", "\n") != lock:
        note = "the image's packages differ from packages-x64.lock; output bytes may differ from the recorded build"
    if note:
        print("note:", note)
    Path("/out/packages-x64.lock").write_text(lock)
    return {"image_base": "almalinux:8@sha256:2d0f2eb2a83cc09dbc2eb92fade4dbe8f709628149e81aa00aec70630406603b",
            "os": release, "glibc": glibc, "gcc": gcc, "ld": ld, "cmake": cmake, "ninja": ninja,
            "jni_headers": "Temurin 25.0.5+8 (linux x64)",
            "packages_lock_sha256": hashlib.sha256(lock.encode()).hexdigest(),
            "packages_match_lock": note is None,
            "build_only_deps": {"pipewire": "1.6.11 (4287f315)", "libdecor": "0.2.5 (38ddc360)",
                                "meson": "1.12.1"}}


def main():
    names = sys.argv[1:] or list(BUILDERS)
    for n in names:
        fetch(n)
    if "lwjgl3" in names:
        fetch("libffi")
    info = toolchain()
    results = []
    for work in WORKS:
        fresh(work)
        results.append({n: BUILDERS[n](work) for n in names})
    failed = False
    for n in names:
        files_a, record = results[0][n]
        files_b, _ = results[1][n]
        out = fresh(Path("/out") / n)
        hashes, elf = {}, {}
        for target, path in files_a.items():
            a, b = sha256(path), sha256(files_b[target])
            if a != b:
                print(f"NOT REPRODUCIBLE: {n} {target}: {a} vs {b}")
                failed = True
            shutil.copyfile(path, out / target)
            hashes[target] = a
            elf[target] = elf_facts(out / target)
        src = SOURCES[n]
        extra = {"libffi": {"source": SOURCES["libffi"]}} if n == "lwjgl3" else {}
        build_info = {"name": n, "version": src["version"], "platform": "linux-x64", "source": src,
                      "toolchain": info, "sha256": hashes, "elf": elf, **record, **extra,
                      "reproducible": "built twice in different folders; identical bytes" if not failed else "NO",
                      "deterministic_flags": "-ffile-prefix-map for the source and build folders"}
        (out / "build-info.json").write_text(json.dumps(build_info, indent=2) + "\n")
        write_notices(n, out)
        for target, digest in hashes.items():
            print(f"{digest}  {n}/{target}  needs {elf[target]['needed']}, glibc {elf[target]['newest_glibc']}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
