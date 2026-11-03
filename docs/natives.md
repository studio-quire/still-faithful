# Native libraries: what we build and how

Claude Opus 5.5, 2026-11-01. The game's native layer is LWJGL 2 (Legacy Fabric's maintained fork) and OpenAL Soft.
We build both from pinned sources; nothing native in our instance comes from someone else's binary. This is the bridge
until phase 3's windowing layer (`docs/windowing-survey.md`) replaces LWJGL 2: our own LWJGL 2 API on LWJGL 3 with
SDL3 for windowing, whose natives (SDL3, LWJGL 3's core and OpenGL libraries, OpenAL Soft again) are built the same way
(Claude Opus 5.5, 2026-11-01).

## What's built

| Artifact (our mirror, `org.studioquire.oldfaithful.natives`) | Source | Pinned at |
|---|---|---|
| `openal-soft:1.25.2+oldfaithful.1:natives-windows` (`OpenAL-amd64.dll`) | [kcat/openal-soft](https://github.com/kcat/openal-soft) tag 1.25.2, the latest release (2026-05-12) | commit `b2c48f77…6e0b` |
| `lwjgl:2.9.4+legacyfabric.18.oldfaithful.1`, `lwjgl_util:…` (Java side) | [Legacy-Fabric/lwjgl](https://github.com/Legacy-Fabric/lwjgl) 2.9.4+legacyfabric.18 | commit `0732e331…987c` ("Bump version", the commit their CI published .18 from; the repo has no tags) |
| `lwjgl-platform:…:natives-windows` (`lwjgl-windows-amd64.dll`) | the same | the same |
| `sdl3:3.4.18+oldfaithful.1:natives-windows` (`SDL3.dll`) | [libsdl-org/SDL](https://github.com/libsdl-org/SDL) tag release-3.4.18, the latest release (2026-10-02) | commit `829a65d7…260a` |
| `lwjgl3:3.4.3+oldfaithful.1:natives-windows` (`lwjgl.dll`, `lwjgl_opengl.dll`) | [LWJGL/lwjgl3](https://github.com/LWJGL/lwjgl3) tag 3.4.3 (annotated, tag object `e33a7d99…`) | commit `30fac9b9…9951`, the one Central's `lwjgl.dll.git` records |
| (linked into `lwjgl.dll`) libffi 3.8.0 | [LWJGL-CI/libffi](https://github.com/LWJGL-CI/libffi) tag LWJGL-3.4.3 | commit `dcd0bd81…2c45` |
| `openal-soft:1.25.2+oldfaithful.1:natives-windows-lwjgl3` (`OpenAL.dll`) | the OpenAL Soft build above, same bytes, under the name LWJGL 3 loads | |
| `lwjgl3:…:natives-linux` (`liblwjgl.so`, `liblwjgl_opengl.so`), `sdl3:…:natives-linux` (`libSDL3.so`), `openal-soft:…:natives-linux` (`libopenal.so`) | the same LWJGL 3, libffi, SDL and OpenAL Soft commits, built for Linux x64 (below) | the same |

The LWJGL 3 rows are the phase-3 layer's natives; its Java side comes from Maven Central's 3.4.3 jars (`lwjgl`,
`lwjgl-opengl`, `lwjgl-sdl`, `lwjgl-openal`), pinned by hash. SDL 3.4.18 rather than the 3.4.14 LWJGL 3.4.3 ships:
SDL keeps the 3.x ABI backward-compatible, LWJGL looks functions up by name at runtime and doesn't check the version,
and 3.4.18 exports exactly the 1273 functions 3.4.14 does (the binding's `SDL_VERSION` constant says 3.4.14).

Each natives jar carries `META-INF/build-info.json` (source commit, toolchain versions, flags, SHA-256s) and the
licence texts; `-sources.zip` beside it is `git archive` of the pinned commit (OpenAL Soft is LGPL-2.1, so its source
travels with the binary; `docs/mirror.md`). Windows x64, and Linux x64 for the component; jinput isn't built or shipped at all (the game
never reads controllers, and `oldfaithful-hardening` skips the call that would load it: `docs/runtime-inventory.md`).

## Building (Windows x64)

    python natives/build.py all        # or openal-soft / lwjgl2 / sdl3 / lwjgl3
    python tools/mirror.py natives     # package them into the mirror (docs/mirror.md)

Toolchain, all on this machine (2026-11-01):
- **Visual Studio 2022 Build Tools** 17.14.41 with the C++ x64 workload: MSVC 14.44.35207 (cl 19.44.35229), Windows SDK
  10.0.26100.0, and the CMake 3.31.6 and Ninja 1.12.1 that come with it. The script finds it with `vswhere` and runs
  `vcvars64.bat`; a different version still builds, and `build-info.json` records what was used.
- **Temurin 8 JDK** (8u504-b01, `OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip`, SHA-256 `ea43d46e…b2aa` from
  Adoptium's API) in `local/tools/jdk8`: LWJGL's Java side and its JNI headers target Java 8. Temurin 21 in
  `local/tools/jdk21` runs LWJGL's Gradle. Temurin 25 in `local/tools/jdk25` gives LWJGL 3 its JNI headers (their
  release build compiles with the JDK running Ant, 27-ea; `jni.h` only ever appends, so the code is the same).
- **Gradle 8.14.3** for LWJGL's own build (the version its wrapper names at the pinned commit), downloaded by the
  script and checked against Gradle's published SHA-256; the repository's wrapper jar is never run.

What the script does:
- **Sources**: `git fetch` of exactly the pinned commit into `local/src/<name>`, reset and cleaned, HEAD checked.
- **OpenAL Soft**: CMake + Ninja, Release, static CRT (`/MT`: players needn't install the VC++ runtime), library
  only, WASAPI, DirectSound and WinMM backends required, EAX off (a legacy Creative API nothing here calls), C++20
  modules off. 4 parallel jobs.
- **LWJGL**: Legacy Fabric's Gradle build (on the JDK 8 toolchain, 2 workers) runs the code generator and compiles the
  Java side and JNI headers; then the script compiles the native library itself with `cl`, with exactly the sources,
  flags and libraries of their `gradle/windows.gradle`, except `/MP4` instead of all cores. Their CI does the same
  through Gradle. Their build resolves only jinput 2.0.5 and jutils 1.0.0 (compile-time, from Maven Central); the
  script drops in `natives/lwjgl2-verification-metadata.xml` so Gradle checks them (the hashes match Mojang's copies
  of the same jars).
- **SDL3**: CMake + Ninja, Release, static CRT, shared library only, every subsystem left on as upstream and LWJGL
  have them (video with WGL, events, input, HIDAPI, joystick, haptic, audio, GPU, render; SDL's audio goes unused
  next to OpenAL but costs nothing). As LWJGL-CI/SDL's workflow builds it, except: Ninja instead of the VS generator,
  no libusb (LWJGL adds vcpkg's so SDL can dlopen `libusb-1.0.dll` for the GameCube adapter; nobody ships that DLL),
  and `SDL_REVISION` stated (`SDL-3.4.18-829a65d (Old Faithful)`) since our checkout has no tags. LWJGL-CI/SDL's
  patches on top of upstream touch only AltiVec and X11, so upstream's tree is the same source for Windows.
- **LWJGL 3** (`build_lwjgl3`): exactly what `config/windows/build.xml` does for the `core` and `opengl` modules,
  run by the script: the same source sets (the generated C is in the repository), flags (`/O2 /GR- /GS- /MT /GL`,
  core at `/W4 /WX`), relative paths from the repository root as Ant passes them, and link (`/LTCG /OPT:REF,ICF`),
  with `/MP4`. Core links `libffi.lib`, which LWJGL's build downloads from build.lwjgl.org (LWJGL-CI/libffi's CI
  output); we build it from that fork's LWJGL-3.4.3 tag instead. Their CI runs autogen + configure + make under
  Cygwin with `msvcc.sh`; we compile the same eight sources with what `msvcc.sh` turns that into (`cl -MT -W3 -O2
  -DFFI_BUILDING_DLL`, the `.S` preprocessed with `cl -EP` then `ml64`), with `ffi.h` filled from its template and
  `natives/libffi-fficonfig-msvc-x64.h` standing in for configure's `fficonfig.h`. `FFI_BUILDING_DLL` makes
  `lwjgl.dll` export 31 `ffi_*` functions, as LWJGL's does.
- **Deterministic output**: `CL=/Brepro` and `_LINK_=/Brepro` for every compile and link (no timestamps or random
  GUIDs in objects or PE headers); jars repacked with sorted entries and one fixed timestamp. No source paths end up in
  the DLLs (checked with `strings`). MSVC also puts the absolute source path into `__FILE__` (SDL logs and asserts
  with it) and into the hash that names C++ anonymous namespaces (RTTI names in OpenAL Soft), so CMake builds add
  `/experimental:deterministic /pathmap:<source>=<name> /pathmap:<build>=build` and give the same bytes wherever the
  checkout lives. The C builds run by the script itself need nothing: LWJGL 3's use relative paths as Ant's do, and
  LWJGL 2's and libffi's code doesn't use `__FILE__`. `ml64` ignores `/Brepro`, so `libffi.lib` carries a timestamp
  and path, but neither reaches `lwjgl.dll`.

## Checks

- **Reproducible**: every clean rebuild gives byte-identical DLLs, and the same ones from a checkout at a different
  path (checked for OpenAL Soft, SDL3 and LWJGL 3 by building in a second folder; LWJGL 2's hash survived the
  project folder's rename from `amber`):
  `OpenAL-amd64.dll` `08458d1b…44aa`, `lwjgl-windows-amd64.dll` `18a2a215…7681`, `SDL3.dll` `2ce771bb…cb80`,
  `lwjgl.dll` `bbce342c…b7e0`, `lwjgl_opengl.dll` `fca1eb21…dba7`. With the same toolchain, anyone gets these
  hashes; with another they won't, so the build record matters. (OpenAL Soft's first hash, `654cb0b3…2605`, was
  built under `projects/amber/` and couldn't be reproduced after the rename, because of the anonymous-namespace
  names; the path mapping above fixed that, and the code is otherwise the same.)
- **Our LWJGL 3 natives match LWJGL's**: the same exports as Central's 3.4.3 DLLs (2050 in `lwjgl.dll`, including
  libffi's 31; 2280 in `lwjgl_opengl.dll`), the same sizes, `KERNEL32.dll` as the only import. Disassembled and
  compared with addresses normalised, every exported function of `lwjgl_opengl.dll` is identical, and in `lwjgl.dll`
  all but six, which differ only in which internal functions the linker placed after them. Our `libffi.lib` and
  LWJGL's (build.lwjgl.org, dated the day of the LWJGL-3.4.3 tag) hold the same 101 functions, instruction for
  instruction, which is what checks `natives/libffi-fficonfig-msvc-x64.h`. Their compiler was MSVC 19.44.35228, one
  build from ours. SDL3.dll has the same 1273 exports and the same imports as LWJGL's 3.4.14 build.
- **Our LWJGL matches what Legacy Fabric published**: 624 of 629 classes in our `lwjgl.jar` and all 117 in
  `lwjgl_util.jar` are byte-identical to their `2.9.4+legacyfabric.18` jars; the other five (generated capability
  tables) hold the same members and instructions in a different order (their generator iterates unordered sets).
  So the pinned commit is .18's source, and their published jars trace to it.
- **They run**: `./gw :harness:nativesTest` loads them the way a launcher does and exercises them with no visible
  window: the LWJGL library, display modes, an offscreen OpenGL context through a pbuffer (every generated capability
  loaded, a clear read back), OpenAL Soft enumerating the real output devices (none opened), and mixing a static buffer
  and a streaming queue on OpenAL Soft's silent null output. Passes on Java 8u51 (Prism's `jre-legacy`), 8u504, 21.0.13
  and 25.0.5. Java 24+ prints JNI "restricted method" warnings (harmless; `--enable-native-access=ALL-UNNAMED` would
  silence them but breaks Java 8, so the instance doesn't pass it).
- **The LWJGL 3 set runs** (a throwaway windowless check, 2026-11-01; the harness tests are the phase-3 layer's):
  Central's 3.4.3 jars (`lwjgl`, `lwjgl-opengl`, `lwjgl-sdl`, `lwjgl-openal`, no natives jars) with
  `org.lwjgl.librarypath` pointing at a folder holding only our four DLLs: `SDL_Init(VIDEO | EVENTS)`, a hidden SDL
  window with a WGL context, `GL.createCapabilities()`, a clear read back from an FBO, then OpenAL Soft through ALC on
  `ALSOFT_DRIVERS=null`, playing a buffer. Passes on Java 21.0.13 and 25.0.5; every library loads from our folder
  (`opengl32` from the system, as it should). What showed up:
  - LWJGL compares each loaded library's SHA-1 with a `.sha1` file inside its Java jar (Central's build), and prints
    "[LWJGL] [ERROR] Incompatible Java and native library versions detected" for each of ours, debug mode or not.
    Harmless; `-Dorg.lwjgl.util.NoHashChecks=true` (or `Configuration.DISABLE_HASH_CHECKS`) turns it off.
  - On Java 25, LWJGL 3.4.3 uses FFM for downcalls but still loads `lwjgl.dll` (JNI) for the core, so the DLL is
    needed on every Java. Java 25 also warns about `System::load` (`--enable-native-access=ALL-UNNAMED` silences it)
    and about LWJGL's `sun.misc.Unsafe` memory backend (`Configuration.MEMORY_BACKEND` offers others).
  - LWJGL doesn't check the SDL version: 3.4.18 loads and reports itself (`SDL-3.4.18-829a65d (Old Faithful)`).
  - In debug mode LWJGL says `AL_SOFT_callback_buffer` and `AL_SOFT_events` lack entry points: its binding wants
    `...DirectSOFT` variants that OpenAL Soft added after 1.25.2 (LWJGL's own OpenAL.dll is a later commit). Those two
    extensions then read as absent; nothing else changes, and we don't use them.
- What only the user's client check shows: a real window, input, and sound through the speakers.

## Can LWJGL 2's own natives be built the same way?

Yes, and they are: `lwjgl-windows-amd64.dll` above is Legacy Fabric's LWJGL 2 built from source here (it's the
Windows counterpart of `lwjgl64.dll`; Legacy Fabric renames natives per architecture). Mojang's 2015
`lwjgl64.dll`/`OpenAL64.dll` can't be: they're MSVC 2008 builds with no build record (`docs/runtime-inventory.md`), so we
don't ship them.

## Could LWJGL 3's Java side be built here too?

Yes, later, if we want it (not done; Claude Opus 5.5, 2026-11-01). Their Ant build's `compile` first runs the Kotlin
generator (kotlinc 2.4.0 and a few jars from `update-dependencies.xml`, which we'd pin by hash), which regenerates the
Java and C that are already committed at the tag; running it and checking the output matches the commit is a good
check in itself. Then it wants JDK 25 or later (the multi-release jar has `core11` and `core25` classes, the latter
FFM) and `JAVA8_HOME` for the Java 8 boot classpath, all of which we have or can pin.
The jars wouldn't be byte-identical to Central's, since those were compiled by an Azul 27 early-access javac; we'd
compare class by class as we did for LWJGL 2. Building them would also let us drop the `.sha1` files that trigger the
hash-check message above, or fill them with our hashes.

## Linux x64 (Claude Opus 5.5, 2026-11-01)

The component's four libraries for Linux x64, built here in Docker from the same pinned commits as the Windows ones:

    python natives/build.py linux-x64   # any host with Docker; results in local/natives/<name>/<version>/linux-x64/
    python tools/mirror.py natives      # natives-linux jars beside the Windows ones

- **Where**: `natives/linux/Dockerfile`, AlmaLinux 8 pinned by digest with GCC 15 from `gcc-toolset-15`: the base and
  compiler LWJGL's own Linux CI uses, so ours load wherever LWJGL 3.4.3's do (glibc 2.28 and later: RHEL/Alma/Rocky 8,
  Debian 10, Ubuntu 18.10 onwards). Debian 11 was the other candidate; its glibc 2.31 would have dropped systems
  LWJGL itself supports, for nothing. gcc-toolset links what newer GCCs added to libstdc++ statically, so OpenAL Soft
  needs only the system's libstdc++ (GLIBCXX 3.4.22, GCC 6). The packages the image resolved are in
  `natives/linux/packages-x64.lock`; a build compares against it and records the result (a different set still
  builds, but bytes may differ). AlmaLinux keeps only current packages in its main repositories, so the lock is a
  record more than a way back; older ones are in its vault.
- **Build-only dependencies** in `/opt/deps`, never shipped: PipeWire 1.6.11 and libdecor 0.2.5 from pinned commits
  (MIT), and Meson 1.12.1 from PyPI by hash to build them. SDL and OpenAL Soft load both from the player's system at
  runtime, but need their headers and pkg-config files at build time, and SDL the library's soname; AlmaLinux 8's
  PipeWire is 0.3.6 (SDL wants 0.3.44+) and it has no libdecor. LWJGL-CI's SDL workflow gets these from a third-party
  repository (raven) instead; we build them.
- **What's built** (`natives/linux/build_linux.py`, run in the container; 4 jobs):
  - libffi from LWJGL-CI's fork as their CI builds it (`autogen.sh`, `configure --disable-shared --with-pic`, `make`),
    linked into `liblwjgl.so`.
  - `liblwjgl.so`, `liblwjgl_opengl.so`: the sources, flags (`-O3 -flto`, `-Werror` and friends), version script,
    link and strip of LWJGL's `config/linux/build.xml`, run directly as on Windows; jawt included, WGL excluded.
  - `libSDL3.so`: CMake + Ninja as LWJGL-CI's workflow (with `-U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0`), stripped. The
    build fails unless SDL found X11 (XInput2, XRandR, XFixes, Xcursor), Wayland with libdecor, GLX and EGL,
    PipeWire, PulseAudio, ALSA, udev, D-Bus and IBus, all loaded at runtime (`runtime_loaded` in build-info.json lists
    each soname). JACK, KMS/DRM, libusb (HIDAPI's fallback for a few controllers), fribidi and libthai likewise. On
    Linux libusb stays on: unlike Windows nothing has to ship it, the system's is loaded if present.
  - `libopenal.so`: OpenAL Soft with PipeWire, PulseAudio and ALSA required (OSS, JACK, wave writer and RTKit too),
    all dlopened, EAX and modules off. AlmaLinux 8's `DBus1Config.cmake` overwrites `PKG_CONFIG_PATH`, which hid our
    PipeWire from the check after it, so `CMAKE_DISABLE_FIND_PACKAGE_DBus1` makes OpenAL Soft find D-Bus through
    pkg-config instead.
- **Checks in the build**: each library links only glibc (and OpenAL Soft libstdc++/libgcc); anything else fails it.
- **Reproducible**: everything is built twice in folders with different paths (`-ffile-prefix-map`) and must be
  byte-identical, and separate runs agree: `liblwjgl.so` `d46fcf64…239e`, `liblwjgl_opengl.so` `8492c1bd…a1f2`,
  `libSDL3.so` `6c7f351f…60b5`, `libopenal.so` `1fc59d52…3bea`.
- **They match LWJGL's**: against Central's 3.4.3 `natives-linux` jars, the same exported symbols (2220 in
  `liblwjgl.so`, 2271 in `liblwjgl_opengl.so`, 1272 in `libSDL3.so` (3.4.14's), 179 in `libopenal.so`), the same
  linked libraries and the same newest glibc symbol (2.28; 2.27 for SDL). `liblwjgl_opengl.so` is even the same size.
- **Notices**: the Linux jars carry the Windows ones plus PipeWire's (its headers' inline code is in SDL and OpenAL
  Soft), libdecor's, and `wayland-protocols-NOTICES.txt`, the notices of the protocol files whose generated code is
  in `libSDL3.so`. The sources archives are shared with Windows.
- **Not built: LWJGL 2 for Linux.** The component replaces LWJGL 2 on every platform; the LWJGL 2 instance stays a
  Windows comparison point (the user's first client check), and nothing we ship would load a Linux LWJGL 2.
- **Tested**: `docs/lwjgl.md` > Linux.

### Linux arm64 (planned)
The same build cross-compiled (LWJGL-CI cross-compiles from Ubuntu 22.04 with `gcc-12-aarch64-linux-gnu`) or built
natively under QEMU, and tested in an arm64 container under QEMU. Not done: under QEMU's user emulation a JVM and
llvmpipe run many times slower (an estimate, not measured here), so the tests would take long on a shared CPU, and the
players who'd use it (Raspberry Pi, Asahi Macs) are few; a GitHub `ubuntu-24.04-arm` runner is the better place once
the repository is public. LWJGL's classifier is `natives-linux-arm64`; how Prism's component format selects natives
by architecture is still to check.

## macOS: the plan (needs CI, waits until release)

Not built yet; it needs Macs, which means CI. It waits until release (user, 2026-11-01): no CI minutes on the private
repo for it now. Outside players test it (the user's decision).
- **Where**: GitHub Actions in this repo's public home under studio-quire once it's public (free for public repos).
- **What**: the component's libraries (`liblwjgl.dylib`, `liblwjgl_opengl.dylib`, `libSDL3.dylib`, `libopenal.dylib`)
  for arm64 (`macos-14`/`15`) and x64 (`macos-13` while it exists, or cross-compiled), separate per architecture as
  LWJGL's jars have, with LWJGL's `config/macos/build.xml` flags and deployment targets; OpenAL Soft on CoreAudio.
  Built twice to compare where the toolchain allows (`-ffile-prefix-map`; `ld -no_uuid` or accept the UUID).
  Gatekeeper: dylibs loaded by Java don't need notarising when the launcher downloads them without quarantine (Prism
  does), but check on a real Mac; signing needs an Apple developer account (paid: the user's call).
- **The layer needs work too**: on macOS SDL's window and events must live on the main thread, which the game's own
  thread layout doesn't give it (`docs/windowing-survey.md` has the dispatch design).
- Windows arm64 would cross-compile here with the MSVC ARM64 tools (not installed yet).
