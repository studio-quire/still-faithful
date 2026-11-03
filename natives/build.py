"""Builds the native libraries Old Faithful ships, from pinned sources, on Windows x64 (docs/natives.md).

    python natives/build.py openal-soft
    python natives/build.py lwjgl2
    python natives/build.py sdl3
    python natives/build.py lwjgl3
    python natives/build.py all
    python natives/build.py linux-x64      (any host with Docker: the component's Linux natives)

Sources are fetched by commit into local/src/, built in local/build/, and the results land in
local/natives/<name>/<version>/windows-x64/ with a build-info.json (toolchain versions, flags, SHA-256s).
Toolchain: Visual Studio 2022 Build Tools (MSVC, Windows SDK, and the CMake and Ninja that come with it),
Temurin 8 JDK in local/tools/jdk8 (LWJGL 2's Java side and JNI headers), Temurin 25 in local/tools/jdk25
(LWJGL 3's JNI headers). Nothing is downloaded except the
pinned sources and, for LWJGL 2, a hash-checked Gradle distribution and the Maven Central artifacts its build uses.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / "local"
JOBS = 4  # the CPU is shared

SOURCES = {
    "openal-soft": {
        "repo": "https://github.com/kcat/openal-soft",
        "tag": "1.25.2",
        "commit": "b2c48f7718ef3fcf67921a8b6534c4914e328970",
        "version": "1.25.2",
    },
    "lwjgl2": {
        # Legacy Fabric's maintained LWJGL 2. No release tags: this is "Bump version", the commit their CI published
        # 2.9.4+legacyfabric.18 from (docs/natives.md has how it was matched to the published jars).
        "repo": "https://github.com/Legacy-Fabric/lwjgl",
        "commit": "0732e3317c05536effbbd7a3ebe9a2035e9d987c",
        "version": "2.9.4+legacyfabric.18",
    },
    "sdl3": {
        "repo": "https://github.com/libsdl-org/SDL",
        "tag": "release-3.4.18",
        "commit": "829a65d769d935c4852f8159e964312c0957260a",
        "version": "3.4.18",
    },
    "lwjgl3": {
        # The tag is annotated (tag object e33a7d99...); this is the commit it points at, which Central's
        # lwjgl.dll also records in its .git file.
        "repo": "https://github.com/LWJGL/lwjgl3",
        "tag": "3.4.3",
        "commit": "30fac9b95f99cda97312232be25ba55297bf9951",
        "version": "3.4.3",
    },
    "libffi": {
        # Linked into LWJGL 3's lwjgl.dll. LWJGL's CI builds it from their fork and downloads the .lib; this is the
        # commit that fork's LWJGL-3.4.3 tag points at (libffi 3.8.0 plus their CI configuration).
        "repo": "https://github.com/LWJGL-CI/libffi",
        "tag": "LWJGL-3.4.3",
        "commit": "dcd0bd813e20de474c9a7f8d1ebae6445bcf2c45",
        "version": "3.8.0",
    },
}

# LWJGL's build runs on the Gradle its wrapper names at that commit; we fetch the distribution ourselves and check
# Gradle's published hash, rather than running the repository's wrapper jar.
GRADLE = {
    "version": "8.14.3",
    "url": "https://services.gradle.org/distributions/gradle-8.14.3-bin.zip",
    "sha256": "bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531",
}

# What we expect the toolchain to be. A different one still builds, but build-info.json records what was used,
# and a mismatch is printed, since it changes the output bytes.
EXPECTED_TOOLCHAIN = {"msvc": "14.44.35207", "windows_sdk": "10.0.26100.0"}


def run(cmd, cwd=None, env=None):
    cmd = [str(c) for c in cmd]
    print("+", " ".join(cmd), flush=True)
    if env is not None:
        # Windows looks executables up on the parent's PATH, not the child's.
        cmd[0] = shutil.which(cmd[0], path=env.get("PATH")) or cmd[0]
    subprocess.run(cmd, cwd=cwd, env=env, check=True)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(name):
    """A clean checkout of exactly the pinned commit in local/src/<name>."""
    src = SOURCES[name]
    dest = LOCAL / "src" / name
    if not (dest / ".git").exists():
        dest.mkdir(parents=True, exist_ok=True)
        run(["git", "init", "-q"], cwd=dest)
        run(["git", "remote", "add", "origin", src["repo"]], cwd=dest)
        # LWJGL 3's generated Vulkan classes pass Windows' 260-character limit under a longer checkout path.
        run(["git", "config", "core.longpaths", "true"], cwd=dest)
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=dest, capture_output=True, text=True).stdout.strip()
    if head != src["commit"]:
        run(["git", "fetch", "-q", "--depth", "1", "origin", src["commit"]], cwd=dest)
        run(["git", "checkout", "-q", "--detach", "FETCH_HEAD"], cwd=dest)
    run(["git", "reset", "-q", "--hard", src["commit"]], cwd=dest)
    run(["git", "clean", "-q", "-fdx"], cwd=dest)
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=dest, capture_output=True, text=True).stdout.strip()
    if head != src["commit"]:
        sys.exit(f"{name}: checked out {head}, expected {src['commit']}")
    return dest


def vs_install():
    vswhere = Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")) / "Microsoft Visual Studio/Installer/vswhere.exe"
    out = subprocess.run([str(vswhere), "-latest", "-products", "*", "-requires",
                          "Microsoft.VisualStudio.Component.VC.Tools.x86.x64", "-property", "installationPath"],
                         capture_output=True, text=True, check=True).stdout.strip()
    if not out:
        sys.exit("No Visual Studio with the C++ x64 tools found (install VS 2022 Build Tools, docs/natives.md)")
    return Path(out)


def vs_version():
    vswhere = Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")) / "Microsoft Visual Studio/Installer/vswhere.exe"
    return subprocess.run([str(vswhere), "-latest", "-products", "*", "-property", "catalog_productDisplayVersion"],
                          capture_output=True, text=True).stdout.strip()


def msvc_env():
    """The environment vcvars64.bat sets up, plus deterministic-output flags for every cl and link run."""
    vs = vs_install()
    vcvars = vs / "VC/Auxiliary/Build/vcvars64.bat"
    out = subprocess.run(f'cmd /s /c ""{vcvars}" >nul && set"', capture_output=True, text=True, shell=True, check=True).stdout
    env = dict(line.split("=", 1) for line in out.splitlines() if "=" in line)
    # /Brepro: no timestamps or random GUIDs in objects and the PE header, so the same sources and toolchain give
    # the same bytes. CL and _LINK_ are read by cl.exe and link.exe themselves, so this covers CMake and plain cl.
    env["CL"] = "/Brepro"
    env["_LINK_"] = "/Brepro"
    cmake_bin = vs / "Common7/IDE/CommonExtensions/Microsoft/CMake"
    env["PATH"] = os.pathsep.join([str(cmake_bin / "CMake/bin"), str(cmake_bin / "Ninja"), env["PATH"]])
    return env


def toolchain_info(env):
    cl = subprocess.run(["cl"], capture_output=True, text=True, env=env, shell=True).stderr.splitlines()[0]
    cmake = subprocess.run(["cmake", "--version"], capture_output=True, text=True, env=env, shell=True).stdout.splitlines()[0]
    ninja = subprocess.run(["ninja", "--version"], capture_output=True, text=True, env=env, shell=True).stdout.strip()
    info = {
        "msvc": env.get("VCToolsVersion", "").strip(),
        "cl": cl.strip(),
        "windows_sdk": env.get("WindowsSDKVersion", "").strip().rstrip("\\"),
        "vs": vs_version(),
        "cmake": cmake,
        "ninja": ninja,
    }
    for key, want in EXPECTED_TOOLCHAIN.items():
        if info[key] != want:
            print(f"note: {key} is {info[key]}, the documented build used {want}; output bytes will differ")
    return info


def publish(name, files, info, extra=None):
    version = SOURCES[name]["version"]
    out = LOCAL / "natives" / name / version / "windows-x64"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    hashes = {}
    for target, source in files.items():
        if target.endswith(".jar"):
            # Gradle stamps jar entries with the build time; repacked, the same classes give the same jar.
            with zipfile.ZipFile(source) as z:
                entries = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
            with zipfile.ZipFile(out / target, "w", zipfile.ZIP_DEFLATED) as z:
                for n in sorted(entries, key=lambda n: (n != "META-INF/MANIFEST.MF", n)):
                    entry = zipfile.ZipInfo(n, (1980, 2, 1, 0, 0, 0))
                    entry.compress_type = zipfile.ZIP_DEFLATED
                    entry.external_attr = 0o644 << 16
                    z.writestr(entry, entries[n])
        else:
            shutil.copyfile(source, out / target)
        hashes[target] = sha256(out / target)
    record = {"name": name, "version": version, "source": SOURCES[name], "toolchain": info, "sha256": hashes}
    if extra:
        record.update(extra)
    (out / "build-info.json").write_text(json.dumps(record, indent=2) + "\n")
    for target, digest in hashes.items():
        print(f"{digest}  {out / target}")
    return out


def mapped_env(env, src, build, name):
    """env with MSVC told to see the source and build folders under neutral names. MSVC puts the absolute source
    path into __FILE__ and into the hash that names C++ anonymous namespaces (RTTI names in the DLL), so without
    this the bytes depend on where the checkout lives."""
    mapped = dict(env)
    mapped["CL"] = f"{env['CL']} /experimental:deterministic /pathmap:{src}={name} /pathmap:{build}=build"
    return mapped, f"CL={env['CL']} /experimental:deterministic /pathmap:<src>={name} /pathmap:<build>=build _LINK_=/Brepro"


def build_openal(env, info):
    src = fetch("openal-soft")
    build = LOCAL / "build" / "openal-soft"
    if build.exists():
        shutil.rmtree(build)
    options = [
        "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
        # Static CRT, as LWJGL's own natives use: players needn't have the VC++ runtime installed.
        "-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded", "-DFORCE_STATIC_VCRT=ON",
        # Only the library: no utilities, examples, tests or install step.
        "-DALSOFT_UTILS=OFF", "-DALSOFT_EXAMPLES=OFF", "-DALSOFT_TESTS=OFF", "-DALSOFT_INSTALL=OFF",
        # Fail rather than silently drop a Windows output backend.
        "-DALSOFT_REQUIRE_WASAPI=ON", "-DALSOFT_REQUIRE_DSOUND=ON", "-DALSOFT_REQUIRE_WINMM=ON",
        # EAX is a legacy Creative API nothing in the game or LWJGL 2 calls; leaving it out is less code reachable.
        "-DALSOFT_EAX=OFF",
        # C++20 modules work with this toolchain but aren't needed, and are the least settled part of the build.
        "-DALSOFT_ENABLE_MODULES=OFF",
    ]
    benv, flags = mapped_env(env, src, build, "openal-soft")
    run(["cmake", "-S", src, "-B", build] + options, env=benv)
    run(["cmake", "--build", build, "--parallel", str(JOBS)], env=benv)
    dll = build / "OpenAL32.dll"
    # Named as Legacy Fabric's LWJGL looks for it on x64 Windows (its fallback is the stock OpenAL64.dll).
    return publish("openal-soft", {"OpenAL-amd64.dll": dll}, info,
                   {"cmake_options": options, "deterministic_flags": flags})


def gradle_home():
    home = LOCAL / "tools" / f"gradle-{GRADLE['version']}"
    if (home / "bin/gradle.bat").exists():
        return home
    archive = LOCAL / "tools" / f"gradle-{GRADLE['version']}-bin.zip"
    print(f"downloading {GRADLE['url']}")
    urllib.request.urlretrieve(GRADLE["url"], archive)
    if sha256(archive) != GRADLE["sha256"]:
        archive.unlink()
        sys.exit("Gradle distribution hash mismatch")
    with zipfile.ZipFile(archive) as z:
        z.extractall(LOCAL / "tools")
    archive.unlink()
    return home


def build_lwjgl(env, info):
    src = fetch("lwjgl2")
    jdk8 = LOCAL / "tools" / "jdk8"
    jdk_runner = LOCAL / "tools" / "jdk21"
    for jdk in (jdk8, jdk_runner):
        if not (jdk / "bin/java.exe").exists():
            sys.exit(f"missing {jdk} (docs/natives.md: Temurin 8 and 21 JDKs)")
    gradle = gradle_home() / "bin/gradle.bat"
    # Their build resolves only jinput and jutils (compile-time, from Maven Central); our pins make Gradle check them.
    # The hashes match Mojang's copies of the same jars on libraries.minecraft.net, an independent source.
    shutil.copyfile(ROOT / "natives/lwjgl2-verification-metadata.xml", src / "gradle/verification-metadata.xml")

    # The Java side and the generated JNI sources and headers, with LWJGL's own build on a JDK 8 toolchain.
    genv = dict(env)
    genv["JAVA_HOME"] = str(jdk_runner)
    genv.pop("CI", None)
    gradle_user_home = LOCAL / "build" / "lwjgl2-gradle-home"
    run([gradle, "--no-daemon", "-g", gradle_user_home, "--max-workers", "2",
         f"-Porg.gradle.java.installations.paths={jdk8}", "-Porg.gradle.java.installations.auto-detect=false",
         "-Porg.gradle.java.installations.auto-download=false",
         ":lwjgl:jar", ":lwjgl:utilJar"], cwd=src, env=genv)

    # The native library: the same sources and flags as Legacy Fabric's gradle/windows.gradle, but run by us, with
    # /MP limited to JOBS processes and the deterministic flags from msvc_env().
    platform = src / "lwjgl-platform/src"
    generated = src / "lwjgl-platform/src/generated/c"
    sources = []
    for d in [platform / "common/c/common", platform / "common/c/common/opengl", generated / "generated/openal",
              generated / "generated/opencl", generated / "generated/opengl", platform / "windows/c/windows",
              platform / "windows/c/windows/opengl"]:
        sources += sorted(d.glob("*.c"))
    includes = [Path(env["VCToolsInstallDir"]) / "include", jdk8 / "include", jdk8 / "include/win32", generated,
                platform / "common/c/common", platform / "common/c/common/opengl", platform / "windows/c/windows",
                platform / "windows/c/windows/opengl"]
    work = LOCAL / "build" / "lwjgl2-natives"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    compile_flags = ["/c", "/W2", "/EHsc", "/Ox", "/Gy", "/MT", f"/MP{JOBS}", "/nologo"]
    run(["cl"] + compile_flags + [f"/I{i}" for i in includes] + sources, cwd=work, env=env)
    libs = ["Kernel32.lib", "ole32.lib", "OpenGL32.Lib", "Version.lib", "user32.lib", "Gdi32.lib", "Advapi32.lib",
            "jawt.lib", "delayimp.lib", "winmm.lib", "Comctl32.lib"]
    link_flags = ["/OPT:REF", "/OPT:ICF", "/DLL", "/DELAYLOAD:jawt.dll"]
    objects = sorted(work.glob("*.obj"))
    run(["cl", "/LD", "/nologo"] + objects + ["/Felwjgl64.dll", "/link", f"/LIBPATH:{jdk8 / 'lib'}"] + link_flags + libs,
        cwd=work, env=env)

    jars = src / "lwjgl/build/libs"
    version = SOURCES["lwjgl2"]["version"]
    dll = work / "lwjgl64.dll"
    return publish("lwjgl2", {
        # Named as Legacy Fabric's Sys looks for it first (its fallback is the stock lwjgl64.dll).
        "lwjgl-windows-amd64.dll": dll,
        "lwjgl.jar": jars / f"lwjgl-{version}.jar",
        "lwjgl_util.jar": jars / f"lwjgl_util-{version}.jar",
    }, info, {"cl_compile_flags": compile_flags, "link_flags": link_flags, "libs": libs,
              "deterministic_flags": "CL=/Brepro _LINK_=/Brepro"})


def build_sdl3(env, info):
    src = fetch("sdl3")
    build = LOCAL / "build" / "sdl3"
    if build.exists():
        shutil.rmtree(build)
    sdl = SOURCES["sdl3"]
    options = [
        "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded",
        # The shared library only, as LWJGL's CI builds it (LWJGL-CI/SDL's workflow): no tests, examples or install.
        "-DSDL_SHARED=ON", "-DSDL_STATIC=OFF", "-DSDL_TEST_LIBRARY=OFF", "-DSDL_TESTS=OFF", "-DSDL_EXAMPLES=OFF",
        "-DSDL_INSTALL=OFF",
        # LWJGL's CI adds vcpkg's libusb so SDL can dlopen libusb-1.0.dll for a few controllers (the GameCube
        # adapter); nothing ships that DLL, so we leave it out, and this also stops CMake picking up a stray one.
        "-DSDL_HIDAPI_LIBUSB=OFF",
        # Without tags in our checkout SDL would ask git for a revision; this is the same thing, stated.
        f"-DSDL_REVISION=SDL-{sdl['version']}-{sdl['commit'][:7]}", "-DSDL_VENDOR_INFO=Old Faithful",
    ]
    # SDL logs and asserts with __FILE__ too.
    benv, flags = mapped_env(env, src, build, "SDL")
    run(["cmake", "-S", src, "-B", build] + options, env=benv)
    run(["cmake", "--build", build, "--parallel", str(JOBS)], env=benv)
    return publish("sdl3", {"SDL3.dll": build / "SDL3.dll"}, info,
                   {"cmake_options": options, "deterministic_flags": flags})


def build_libffi(env):
    """libffi as LWJGL links it into lwjgl.dll: their fork's sources, compiled the way their CI's configure + make
    + msvcc.sh compile them (cl -MT -W3 -O2, ml64 for the assembly), into a static libffi.lib."""
    src = fetch("libffi")
    work = LOCAL / "build" / "lwjgl3-libffi"
    if work.exists():
        shutil.rmtree(work)
    (work / "include").mkdir(parents=True)
    # What configure would generate: ffi.h from its template, ffitarget.h copied, and fficonfig.h (ours, from
    # natives/, with the values configure reaches for this host and compiler).
    version = SOURCES["libffi"]["version"]
    major, minor, micro = (int(p) for p in version.split("."))
    ffi_h = (src / "include/ffi.h.in").read_text()
    for key, value in {"VERSION": version, "TARGET": "X86_WIN64", "HAVE_LONG_DOUBLE": "0",
                       "FFI_EXEC_TRAMPOLINE_TABLE": "0", "FFI_VERSION_STRING": version,
                       "FFI_VERSION_NUMBER": str(major * 10000 + minor * 100 + micro)}.items():
        ffi_h = ffi_h.replace(f"@{key}@", value)
    if "@" in "".join(line for line in ffi_h.splitlines() if line.lstrip().startswith("#")):
        sys.exit("libffi: ffi.h.in has a substitution we don't fill")
    (work / "include/ffi.h").write_text(ffi_h)
    shutil.copyfile(src / "src/x86/ffitarget.h", work / "include/ffitarget.h")
    shutil.copyfile(ROOT / "natives/libffi-fficonfig-msvc-x64.h", work / "fficonfig.h")

    defines = ["-DHAVE_CONFIG_H", "-DFFI_BUILDING_DLL"]
    includes = [f"-I{work}", f"-I{src}", f"-I{src / 'include'}", f"-I{work / 'include'}", f"-I{src / 'src'}"]
    c_sources = ["src/prep_cif.c", "src/types.c", "src/raw_api.c", "src/java_raw_api.c", "src/closures.c",
                 "src/tramp.c", "src/x86/ffiw64.c"]
    cflags = ["-MT", "-nologo", "-W3"] + defines + ["-O2", "-c"]
    objects = []
    for s in c_sources:
        obj = work / (Path(s).stem + ".obj")
        run(["cl"] + cflags[:3] + includes + cflags[3:] + [src / s, f"-Fo{obj}"], cwd=work, env=env)
        objects.append(obj)
    # msvcc.sh preprocesses the .S with cl -EP and assembles the result with ml64.
    asm = work / "win64_intel.asm"
    with open(asm, "w") as out:
        cmd = [shutil.which("cl", path=env["PATH"]), "-nologo", "-EP"] + includes + defines + [
            str(src / "src/x86/win64_intel.S")]
        print("+", " ".join(cmd), ">", asm, flush=True)
        subprocess.run(cmd, cwd=work, env=env, stdout=out, check=True)
    obj = work / "win64_intel.obj"
    run(["ml64", "-nologo", "-c", f"-Fo{obj}", asm], cwd=work, env=env)
    objects.append(obj)
    lib = work / "libffi.lib"
    run(["lib", "-nologo", "-Brepro", f"-OUT:{lib}"] + objects, cwd=work, env=env)
    return lib, {"c_flags": cflags, "asm": "cl -EP, then ml64 -c", "fficonfig": "natives/libffi-fficonfig-msvc-x64.h"}


def build_lwjgl3(env, info):
    """LWJGL 3's core (lwjgl.dll) and OpenGL (lwjgl_opengl.dll) JNI libraries, compiled as their
    config/windows/build.xml compiles them: the same sources, flags and link step, /MP limited to JOBS."""
    src = fetch("lwjgl3")
    jdk = LOCAL / "tools" / "jdk25"
    if not (jdk / "include/jni.h").exists():
        sys.exit(f"missing {jdk} (docs/natives.md: Temurin 25 JDK, for the JNI headers)")
    libffi, libffi_record = build_libffi(env)

    work = LOCAL / "build" / "lwjgl3-natives"
    if work.exists():
        shutil.rmtree(work)
    lwjgl = Path("modules/lwjgl")
    common = ["/c", "/EHsc", "/O2", "/GR-", "/GS-", "/MT"]
    tail = ["/nologo", "/DNDEBUG", "/DLWJGL_WINDOWS", "/DLWJGL_x64", "/GL", f"/MP{JOBS}"]
    base_includes = [f"/I{jdk / 'include'}", f"/I{jdk / 'include/win32'}",
                     f"/I{lwjgl / 'core/src/main/c'}", f"/I{lwjgl / 'core/src/main/c/windows'}"]
    link = ["/link", "/OPT:REF,ICF", "/DLL", "/INCREMENTAL:NO", "/LTCG"]

    def c_files(*dirs, exclude=()):
        files = []
        for d in dirs:
            files += [f for f in (src / d).glob("*.c") if f.name not in exclude]
        # Relative to the source root, as Ant passes them (so nothing absolute reaches __FILE__).
        return sorted(str(f.relative_to(src)) for f in files)

    def module(name, flags, extra, sources, libs=()):
        dest = work / name
        dest.mkdir(parents=True)
        run(["cl"] + common + flags + tail + [f"/Fo{dest}\\"] + base_includes + extra + sources, cwd=src, env=env)
        # Ant hands the linker the objects as the folder lists them; NTFS orders names case-insensitively.
        # Relative to the link's folder: 194 absolute paths can pass Windows' 32K command-line limit.
        objects = [p.relative_to(work) for p in sorted(dest.glob("*.obj"), key=lambda p: p.name.upper())]
        dll = work / ("lwjgl.dll" if name == "core" else f"lwjgl_{name}.dll")
        run(["cl", "/LD", "/WX", "/nologo", f"/Fe:{dll}"] + objects + list(libs) + link, cwd=work, env=env)
        return dll, {"compile": common + flags + tail, "includes": [i.replace(str(jdk), "<jdk>") for i in
                     base_includes + extra], "sources": len(sources), "link": ["/LD", "/WX"] + link,
                     "libs": [Path(l).name for l in libs]}

    core_dll, core_record = module(
        "core", ["/W4", "/WX", "/wd4710", "/wd4711"],
        [f"/I{lwjgl / 'core/src/main/c/libffi'}", f"/I{lwjgl / 'core/src/main/c/libffi/x86'}", "/DX86_WIN64"],
        c_files(lwjgl / "core/src/main/c", lwjgl / "core/src/generated/c", lwjgl / "core/src/generated/c/windows",
                lwjgl / "jawt/src/generated/c", exclude={"org_lwjgl_system_SharedLibraryUtil.c"}),
        [libffi])
    gl_dll, gl_record = module("opengl", ["/W2"], [f"/I{lwjgl / 'opengl/src/main/c'}"],
                               c_files(lwjgl / "opengl/src/generated/c"))
    jdk_version = (jdk / "release").read_text().split('JAVA_VERSION="')[1].split('"')[0]
    return publish("lwjgl3", {"lwjgl.dll": core_dll, "lwjgl_opengl.dll": gl_dll}, info, {
        "jni_headers": f"Temurin {jdk_version} (local/tools/jdk25)",
        "core": core_record, "opengl": gl_record,
        # No hash for libffi.lib: ml64 stamps its object with the time and its absolute path. Neither reaches the DLL.
        "libffi": {"source": SOURCES["libffi"], **libffi_record},
        "deterministic_flags": "CL=/Brepro _LINK_=/Brepro"})


def build_linux_x64():
    """The component's Linux x64 natives (liblwjgl.so, liblwjgl_opengl.so, libSDL3.so, libopenal.so), built in
    Docker from natives/linux/Dockerfile by natives/linux/build_linux.py: twice, in different folders, compared
    byte for byte. Results land in local/natives/<name>/<version>/linux-x64/ like the Windows ones."""
    if not shutil.which("docker"):
        sys.exit("needs Docker (docs/natives.md > Linux)")
    here = ROOT / "natives" / "linux"
    digest = hashlib.sha256((here / "Dockerfile").read_bytes()).hexdigest()[:12]
    image = f"oldfaithful-linux-build:{digest}"
    if subprocess.run(["docker", "image", "inspect", image], capture_output=True).returncode != 0:
        run(["docker", "build", "-t", image, str(here)])
    out = LOCAL / "build" / "linux-x64-out"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    run(["docker", "run", "--rm", "-v", f"{ROOT / 'natives'}:/natives:ro", "-v", f"{out}:/out", image,
         "python3.11", "/natives/linux/build_linux.py"])
    lock = out / "packages-x64.lock"
    if lock.read_text().split() != (here / "packages-x64.lock").read_text().split():
        print(f"note: the image's packages differ from natives/linux/packages-x64.lock (new list in {lock})")
    for name in ("lwjgl3", "sdl3", "openal-soft"):
        dest = LOCAL / "natives" / name / SOURCES[name]["version"] / "linux-x64"
        if dest.exists():
            shutil.rmtree(dest)
        shutil.copytree(out / name, dest)
        record = json.loads((dest / "build-info.json").read_text())
        for target, digest in record["sha256"].items():
            if sha256(dest / target) != digest:
                sys.exit(f"{dest / target} doesn't match its build-info.json")
            print(f"{digest}  {dest / target}")
    shutil.rmtree(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("what", choices=["openal-soft", "lwjgl2", "sdl3", "lwjgl3", "all", "linux-x64"])
    args = parser.parse_args()
    if args.what == "linux-x64":
        build_linux_x64()
        return
    if os.name != "nt":
        sys.exit("Windows builds run on Windows; Linux x64 is `linux-x64` (Docker); macOS is planned in docs/natives.md")
    env = msvc_env()
    info = toolchain_info(env)
    print(json.dumps(info, indent=2))
    if args.what in ("openal-soft", "all"):
        build_openal(env, info)
    if args.what in ("lwjgl2", "all"):
        build_lwjgl(env, info)
    if args.what in ("sdl3", "all"):
        build_sdl3(env, info)
    if args.what in ("lwjgl3", "all"):
        build_lwjgl3(env, info)


if __name__ == "__main__":
    main()
