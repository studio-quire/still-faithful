# What a player's runtime loads

Claude Opus 5.5, 2026-11-01 (facts gathered with a subagent and checked on that date; anything unchecked is marked).
Everything the game loads besides Minecraft itself: first our own instance as it now stands, then the Prism
components it replaced, piece by piece. Downloads for checking are in `local/inventory/` (one folder per source).

## Our instance (`prism/dev`, built by `./gw prismDevInstance`)

| On the classpath / in natives | Version | Licence | From |
|---|---|---|---|
| Fabric Loader (with MixinExtras 0.5.5 nested) | 0.20.1 | Apache-2.0, shaded parts below | our mirror, byte-identical to Fabric's maven |
| sponge-mixin | 0.17.5+mixin.0.8.7 | MIT | the same |
| ASM, -analysis, -commons, -tree, -util | 9.10.1 | BSD-3-Clause | our mirror, byte-identical to Maven Central |
| Babric intermediary | b1.7.3 v2 | CC0 | our mirror |
| LWJGL, lwjgl_util | Legacy Fabric's 2.9.4+legacyfabric.18, **built by us** | BSD-3-Clause | our mirror (`docs/natives.md`) |
| `lwjgl-windows-amd64.dll` | the same, **built by us** (MSVC 14.44, reproducible) | BSD-3-Clause | the same |
| `OpenAL-amd64.dll` | OpenAL Soft **1.25.2**, **built by us** (reproducible) | LGPL-2.1+ | the same |
| Minecraft b1.7.3 client | Mojang's, SHA-1 `43db9b49…` | Mojang's | Prism, from Mojang |
| Java | 21 (Prism's `java-runtime-delta`, Mojang's 21.0.7) or 25; Java 8 still works if chosen by hand | | Prism's Java management or the player's own |

No jinput, no log4j, Guava, commons, SLF4J or Gson beyond what's shaded inside Mixin and MixinExtras. All libraries
travel inside the instance zip with Prism's `local` hint, pinned by SHA-256 in our build; our own `net.minecraft`
component replaces Prism's so Java 21 is asked for and the unneeded `legacyServices` trait is gone (below). Prism's
`NewLaunchLegacy.jar` (GPL-3.0, Prism's own) still starts the game; it isn't ours to ship and doesn't need to be.

## The components it replaced

### Classpath (Prism's components, the phase-1 dev instance)

| Piece | Version | Licence | SHA-256 (jar) | From | Redistribute? |
|---|---|---|---|---|---|
| Fabric Loader | 0.20.1 | Apache-2.0 (+ shaded, below) | `d0c5a082e07410aaff9de7ab196aa6b4236002870f9ba3ad5c1260fcce5110ad` | maven.fabricmc.net (not on Central) | Yes, with notices (below) |
| ↳ MixinExtras (nested jar) | 0.5.5 | MIT | `5da883dc4bfb16e4ceca3f16d8c4ad937bdfb1ce337ed5ba15472d4d81dd6242` | inside the loader, byte-identical to `io.github.llamalad7:mixinextras-fabric:0.5.5` | Yes |
| sponge-mixin (FabricMC fork) | 0.17.5+mixin.0.8.7 | MIT | `03f0065023bfc3045d71bfb99872451d9ad5a0800a5a0dac34ab8c7d5c134f5d` | maven.fabricmc.net | Yes (Guava and Gson inside are Apache-2.0) |
| ASM, -analysis, -commons, -tree, -util | 9.10.1 | BSD-3-Clause | `ed825d10…0fcb`, `dede75a2…2bfc`, `6d0abefb…7895`, `3dfb0d5b…506b`, `1bb99d09…655c` | Prism fetches from maven.fabricmc.net; identical SHA-1s on Maven Central | Yes, with the BSD notice |
| Babric intermediary | `babric:intermediary-upstream:b1.7.3:v2` | CC0 | `c042865157f4c6adb957b7b191b757419542d8cabbf5b1971125cc19d4566d15` | maven.glass-launcher.net/babric (last modified 2024-10-18) | Yes. Data only: one `mappings/mappings.tiny`, no code |
| LWJGL (Mojang's build) | 2.9.4-nightly-20150209 | BSD | `833e721817f70d1445eec13d8ce5a86e12af70efac5014356302e2bbc68b3fe2` | libraries.minecraft.net | Yes, with the notice (none is inside the jar) |
| lwjgl_util | same | BSD | `034456b132a336b72757325bb7aaf99e075681ee5650d2bea21717bd485de42f` | same | Yes |
| lwjgl-platform natives-windows | same | BSD (LWJGL), LGPL-2 (OpenAL Soft) | `f2e1f2c6bd7511a7504f389b8b716f5d8dc2fdc71e29c89b52644314cf0a228e` | same | LGPL parts need the licence and a source offer |
| jinput / jinput-platform natives-windows | 2.0.5 | BSD (Sun) | `79b5a4f5…f018` / `24afbd5e…287f` | same | Yes, with the notice |
| jutils | 1.0.0 | BSD (Sun, assumed; not in the jar) | `c3334ff39cf0ab3b54925619101054c90098b7c733b1e7834c7b75e4c41e84a5` | same | Yes |
| Prism's `NewLaunch.jar`, `NewLaunchLegacy.jar` | Prism 11.1.1 | GPL-3.0-only | (part of Prism) | Prism's install | Not ours to ship; never needed |
| JRE | Mojang `jre-legacy` **8u51** (`8u51-cacert462b08`: 2015 runtime, refreshed CA store) | | per-file SHA-1 in Mojang's manifest | Prism's auto-Java | Not ours to ship |

Every SHA-256 above matches `gradle/verification-metadata.xml` where the build also uses the artifact, and every
Mojang-hosted file matches the SHA-1 in Prism's meta. The loader is signed by Fabric's CI key (`CN=Fabric, OU=CI`,
self-signed, no timestamp) and was built by FabricMC's Release workflow at tag `0.20.1` (`cb9523b`); Mixin, MixinExtras
and ASM are unsigned.

**What's inside the loader** (relocated under `net.fabricmc.loader.impl.lib`, versions from the loader's
`build.gradle` at the tag): tiny-remapper 0.15.0 (**LGPL-3.0**), mapping-io 0.9.1 (Apache-2.0), class-tweaker 0.3.0
(Apache-2.0; it replaced access-widener), SAT4J core and pb 2.3.6 (EPL-1.0 or LGPL-2.1; the loader ships its
`about-sat4j.html`). The jar carries only `LICENSE_fabric-loader` and that SAT4J page: **no LGPL text for
tiny-remapper and no Apache notices for the rest.** If we mirror or bundle the loader, we add those texts and point
to the sources (tiny-remapper's sources jar is on Fabric's maven).

**What's inside Mixin and MixinExtras**: Mixin shades a minimised **Guava 21.0** and **Gson 2.2.4** under
`org.spongepowered.include` (from FabricMC/Mixin's `build.gradle`); nothing in Mixin references an unrelocated Guava
or Gson. MixinExtras shades Gson 2.11.0, commons-lang3 3.3.2 (a handful of classes), ANTLR runtime 4.13.1 and
java-semver 0.10.2, all relocated.

### Natives

Hashes are the first 16 hex digits of SHA-256 (full ones: `python -I` over the jars in `local/inventory/`).

**Mojang's LWJGL 2.9.4-nightly-20150209** (what Prism's own `org.lwjgl` component loads, and the phase-1 dev instance did):

| File | Built | Toolchain | Traces to source? |
|---|---|---|---|
| `lwjgl64.dll` `ebdcedbc3e24b911`, `lwjgl.dll` `fff711369747e9bb` | 2015-02-09 15:24 / 15:45 UTC | MSVC 2008 (linker 9.0) | Roughly: LWJGL/lwjgl `e4b098c` (2015-02-09 15:08 UTC) is the last commit before the build; no build record links them. |
| `OpenAL64.dll` `9261b66010a845dd`, `OpenAL32.dll` `baf27fc91dc852d7` | 2013-03 | MSVC 2008 | **OpenAL Soft 1.15.1** (Dec 2012) by its version string; builder unknown. |
| `liblwjgl64.so`, `libopenal64.so` (1.15.1) | 2013–2015 | GCC 4.4 (Ubuntu) | As above. |
| macOS `liblwjgl.dylib` (x86_64 only), `openal.dylib` (1.15.1, x86/x86_64, built under `/Users/computer/git/openal-soft`) | 2013–2015 | Apple toolchain, macOS 10.8 SDK | As above. No arm64. |
| jinput `jinput-dx8_64.dll`, `jinput-raw_64.dll` (+32-bit, wintab) | 2011-03-23 | MSVC 2008 | jinput 2.0.5 from java.net; no build record. |

Prism's meta fills the gaps with third-party builds: linux-arm32/arm64 from `theofficialgman/lwjgl3-binaries-*`
(OpenAL Soft 1.21.1, GCC 7–10, 2019–2021) and macOS arm64 from `MinecraftMachina/lwjgl` (OpenAL Soft 1.21.1) and
`r58Playz/jinput-m1`. All personal GitHub repos; none checked against source.

**Legacy Fabric's LWJGL 2.9.4+legacyfabric.18** (what we now build ourselves; repo `https://repo.legacyfabric.net/legacyfabric/`,
published 2026-09-30 21:14 UTC; `maven.legacyfabric.net` redirects there):

- **Every published file is byte-identical to the output of a public CI run.** Workflow run 36777506725
  (workflow_dispatch at commit `0732e33` "Bump version", 2026-09-30 21:08 UTC): its `maven` artifact contains the
  same `lwjgl`, `lwjgl_util`, `lwjgl-platform` natives jars and poms as the repository, and its per-platform artifact
  has the same `lwjgl-windows-amd64.dll` (`a07d522d855c89e5`, MSVC 14.51, 2026-09-30 21:10). The clone in
  `local/audit/LegacyFabric_lwjgl` (HEAD `2535748`) differs from `0732e33` only in the CI workflow and Gradle wrapper,
  so it is the .18 source. The repo has no release tags; `0732e33` is the pin.
- **The OpenAL binaries are LWJGL 3's, not custom builds.** `OpenAL-amd64.dll` (`1e4fe04805bc7b9d`), `-i386`,
  `-aarch64`, and the Linux amd64/aarch64 and macOS amd64/aarch64 libraries are byte-identical to the natives in
  `org.lwjgl:lwjgl-openal:3.3.6` on Maven Central, whose jars record the source commit (`*.dll.git`):
  `90191edd20bb877c5cbddfdac7ec0fe49ad93727`, exactly the upstream tag **OpenAL Soft 1.24.1**. So they trace to LWJGL's
  CI building that tag; we can't reproduce them bit for bit, and LWJGL's CMake options aren't recorded in the jar.
  They're checked into LF's repo (2025-09-26, "Update shipped OpenAL") and copied into the natives jar.
- Not everything is current: `libopenal-i386.so` is Mojang's 2013 1.15.1 build (same hash), and the macOS jar still
  carries Mojang's 1.15.1 `openal.dylib` next to the 1.24.1 ones. The armhf, riscv64 and ppc64el OpenAL builds
  (1.24.1, Ubuntu 22.04 GCC 11.4) weren't matched to anything.
- The Java side drops Mojang's `sun.misc.Unsafe` accessor (`MemoryUtilSun` is a stub), which matters on Java 24+.
  Natives are renamed (`lwjgl-windows-amd64.dll`, `OpenAL-amd64.dll`), so LF's jars and natives only work as a set.
- jinput isn't part of LF's LWJGL; Babric's instance still pulls Mojang's 2011 jinput.

Current upstreams for comparison: OpenAL Soft **1.25.2** (2026-05-12); LWJGL 3 **3.4.3**.

## Does anything need log4j, Guava, commons, Gson, SLF4J?

Scanned the bytecode of the loader (with MixinExtras), Mixin, the b1.7.3 client and server jars, and
`oldfaithful-hardening` for references to `org/apache/logging`, `org/apache/log4j`, `com/google/common`, `com/google/gson`,
`org/apache/commons`, `org/slf4j`:

- **b1.7.3 (client and server) and oldfaithful-hardening: none.**
- **Fabric Loader: optional only.** `Log4jLogHandler` and `Slf4jLogHandler` are used if those libraries are on the
  classpath (else its built-in handler); `McLibrary`/`LoaderLibrary` name them only to classify jars.
- **Mixin**: only its relocated Guava/Gson, plus log4j in its LaunchWrapper/ModLauncher/FML service classes, which
  Fabric never loads.
- **So nothing in our stack needs them.** Babric's instance ships log4j 2.16, Guava 31.0.1 and commons for the
  third-party mods written against them (common in the Babric scene, e.g. `LogManager.getLogger`; I didn't count
  which). Consequence: our pack can run our own mods without them, but a player who drops a third-party Babric mod
  into our pack may get `NoClassDefFoundError`. Options: ship current versions (log4j-api only, or log4j 2.25.x),
  or document it. A decision for later, not needed for our own mods.

## Known vulnerabilities (osv.dev, 2026-11-01)

| Component | Hits | Reachable here? |
|---|---|---|
| Fabric Loader 0.20.1, sponge-mixin 0.17.5, MixinExtras 0.5.5, ASM 9.10.1, tiny-remapper 0.15.0, mapping-io 0.9.1, class-tweaker 0.3.0, SAT4J 2.3.6, Gson 2.11.0, ANTLR 4.13.1, java-semver 0.10.2 | none | |
| Guava 21.0 (shaded in Mixin) | CVE-2018-10237, CVE-2026-102554 (Java deserialisation DoS); CVE-2020-8908, CVE-2023-2976 (temp dirs) | No. Nothing deserialises Java objects (the only `ObjectInputStream` references are Guava's own `readObject` methods), and the minimised copy contains no `createTempDir` or `FileBackedOutputStream`. |
| Gson 2.2.4 (shaded in Mixin) | CVE-2022-25647 (DoS parsing crafted JSON) | No: Mixin parses mixin configs and refmaps from installed mods, which are code we already trust. |
| commons-lang3 3.3.2 (shaded in MixinExtras) | CVE-2025-48924 (`ClassUtils` recursion on long input) | No: fed class names from mixins. |
| LWJGL 2 (both builds), lwjgl-openal 3.3.6, jinput 2.0.5, jutils 1.0.0 (Maven); openal-soft, lwjgl, jinput in Debian/Ubuntu/Alpine | none | Natives are thinly covered by OSV; "none" means little for 2011–2013 C code. |
| JRE 8u51 | not queried | Eleven years of JDK security fixes missing. Mojang's Java 21 runtime is 21.0.7 and its 25 is 25.0.1, against Temurin's 21.0.13+9 and 25.0.5+8 today. |

## How Prism handles libraries (source of Prism 11.1.1)

From `launcher/minecraft/Library.cpp` (`getDownloads`, `getApplicableFiles`), `LibrariesTask.cpp`,
`MinecraftInstance.cpp`, `launch/AutoInstallJava.cpp`, `launch/LauncherPartLaunch.cpp`, and
`libraries/launcher/legacy/.../LegacyLauncher.java`:

- **A library with only `name` + `url` is downloaded with no checksum at all** (`add_download(..., QString())`).
  That is how Prism's Fabric Loader component brings in the loader, Mixin and ASM. A library with
  `downloads.artifact.sha1` gets a `ChecksumValidator` at download time.
- **Nothing is re-verified at launch.** Library cache entries are `MakeEternal`; a file already in Prism's shared
  `libraries/` under the same Maven path is used as is, whichever instance put it there. So our own builds need
  coordinates of their own (our group or a version suffix), never LF's or Mojang's, or a cached file of the same
  name would be used instead.
- **`"MMC-hint": "local"`**: the file is taken from the instance's own `libraries/` folder by file name; Prism only
  checks it exists (`check_local_file`) and **does no hash check**. Natives work the same way (`getApplicableFiles`
  resolves the classifier's file name under the instance folder; `ExtractNatives` unzips it into `natives/`, ignoring
  `extract.exclude`). Read from the code, not tried. This is how a self-contained pack zip can carry every library.
- **`file://` URLs**: no special handling found; downloads go through Qt's network manager (`Net::ApiDownload`),
  which may or may not accept `file:`. Not checked; the local hint is the supported route.
- **Traits**: `legacyLaunch` (from Prism's b1.7.3 meta) makes Prism use its legacy launcher (`NewLaunchLegacy.jar`
  prepended to the classpath, `org.prismlauncher.EntryPoint` as main); `noapplet` (ours) makes that launcher call the
  main class (Fabric's `KnotClient`) instead of wrapping an applet in its own frame. `legacyServices` + the
  instance's `OnlineFixes` setting turn on Prism's online fixes (a URL handler for the old auth and skin services,
  plus `--add-opens java.base/java.net` on modular Javas); we leave it off. Traits only accumulate (`+traits`), so a
  later component can't remove `legacyLaunch`; only replacing the `net.minecraft` component can.
- **Java choice**: if automatic Java download is on, Prism takes `compatibleJavaName` (the last component's that sets
  it wins) and tries each of `compatibleJavaMajors` in order (lists are **appended** across components, so Prism's
  b1.7.3 `[8]` comes first). Without auto-download it walks Prism's list of installed Javas and takes the first whose
  major is in the list. Both only when the global "automatic Java switch" is on and the instance doesn't override its
  Java path. Prism's Java meta offers only Mojang's runtimes (`jre-legacy` 8u51, `java-runtime-delta` 21.0.7,
  `java-runtime-epsilon` 25.0.1), fetched by manifest with per-file SHA-1. **Our instance replaces the `net.minecraft`
  component** (Mojang's jar by URL and SHA-1, `compatibleJavaMajors` `[21, 25]`, `compatibleJavaName`
  `java-runtime-delta`, traits `legacyLaunch`, `texturepacks`, `noapplet`). Java 8 is left out of the list so Prism
  never picks it over 21; it still runs if a player sets it by hand (and tells Prism to skip the compatibility check).

## Java versions

- **Java 25 is the ceiling for the current launch path.** b1.7.3's client entrypoint is
  `net.minecraft.client.MinecraftApplet`, so Fabric Loader launches it through its `AppletMain`/`AppletLauncher`
  (`MinecraftGameProvider.launch`, `AppletLauncher extends java.applet.Applet`). JEP 504 removed the Applet API in
  **JDK 26**. Running on 26+ needs a launch path without applets (our own game provider or entry shim). Prism's legacy
  launcher also references `Applet`, but with `noapplet` it probably never loads it (not checked).
- Mojang's 2015 LWJGL uses `sun.misc.Unsafe` (warned about from JDK 24, JEP 471/498); Legacy Fabric's doesn't.
  JNI loading warns from JDK 24 (JEP 472) unless `--enable-native-access=ALL-UNNAMED`.
- Temurin is GPLv2 with the Classpath Exception: we could bundle a JRE with its notices, but Prism's auto-Java or the
  player's own Java avoids shipping 50+ MB.

### Getting a current Java (checked 2026-11-01, Prism's `AutoInstallJava.cpp` on its development branch and its meta)

Prism picked Mojang's 21.0.7 for the client check. What an instance can and can't do about that:
- **No minimum patch level.** A component names majors (`compatibleJavaMajors`) and one Mojang runtime
  (`compatibleJavaName`); nothing more. With automatic download on, Prism fetches only from `net.minecraft.java`,
  which holds Mojang's runtimes alone (`java-runtime-delta` 21.0.7, `java-runtime-epsilon` 25.0.1); without it, it
  takes the first installed Java of a listed major, in its own list order. An instance's own `JavaPath` wins over
  both, but it's an absolute path on the player's machine, so a pack can't carry one.
- **Prism does offer current runtimes, by hand**: Settings > Java > Download Java lists Eclipse Temurin from
  `net.adoptium.java` (21.0.13+9 today, SHA-256-checked archives). Then the instance's Java setting picks it. Prism's
  development branch also lets a Java be pinned per major in the global settings, which then wins for every instance
  asking for that major.
- **What we do now**: nothing in the instance (Mojang's 21.0.7 is a decade newer than the 8u51 Prism would otherwise
  use, and works); our log's first lines record the Java that ran. The pack's guide (phase 7) gets one optional step,
  "download Temurin 21 in Prism and pick it for the instance". No in-game nag: a hard-coded "current" version rots.
- **Later, if it matters**: our own installer (phase 7) can fetch a pinned Temurin; or suggest upstream that Prism's
  automatic download fall back to Temurin, or let a component prefer it (an outward step, so the user's call).
- Exposure meanwhile is modest: the game talks to Mojang over HTTPS and to servers through packet code we harden; the
  JDK fixes missing from 21.0.7 are mostly in areas the game doesn't use, though TLS and image decoding are reached.

## Findings that matter

1. **The OpenAL DLLs in Legacy Fabric's LWJGL trace to source after all**: LWJGL 3.3.6's builds of OpenAL Soft
   1.24.1 (tag commit `90191ed`). We build our own anyway, for control and a current version (1.25.2).
2. **Legacy Fabric .18 is reproducible from public CI** (commit `0732e33`, run 36777506725, every published file
   matched), and our own build from that commit matches their published jars class for class (`docs/natives.md`).
   Their two leftover 1.15.1 OpenAL builds (Linux i386, macOS `openal.dylib`) aren't in ours.
3. **Prism's own b1.7.3 components run 2013 OpenAL Soft 1.15.1, 2015 LWJGL and 2011 jinput natives**, all MSVC 2008
   builds with no build record, on a 2015 JRE. Our instance replaces all of it.
4. **b1.7.3 calls `Controllers.create()`, so jinput's 2011 DLLs load on every launch**, for controllers the game never
   reads. `oldfaithful-hardening` now skips the call (a `controllers` config option turns it back on) and our instance has
   no jinput.
5. **Prism checks no hash for name+url libraries and never re-checks cached ones**, and the shared cache keys on the
   Maven path. Our instance ships its libraries inside the zip with the `local` hint, under coordinates of our own.
6. **Java 26+ breaks the applet launch path** (JEP 504). 21 and 25 are fine (the harness passes on both); beyond
   that needs our own entry point, a job for phase 3's windowing layer.
7. **No logging/Guava/commons needed** by the game, the loader or our mod; only third-party Babric mods might want
   them, which matters if players add such mods to our pack (a decision for the pack, later).
8. **Prism's Java 21 is Mojang's 21.0.7** (April 2025), six updates behind Temurin's 21.0.13. Better than 8u51 by a
   decade, but not current, and an instance can't require more (Getting a current Java, above).
9. **Notice obligations when we redistribute**: the loader jar lacks tiny-remapper's LGPL-3.0 text and the Apache
   notices of its shaded parts; the ASM, LWJGL and jinput jars carry no licence files; OpenAL Soft is LGPL. Our own
   builds now carry their notices and sources; the rest is listed in `docs/mirror.md` for before anything is public.

## Not checked

- Prism itself: its handling of local-hint libraries and natives, and of `file://` URLs, is from reading its source;
  the user's client check with our instance is the test. Our natives were run headlessly (`docs/natives.md`); the
  inventory's downloads were only read.
- The third-party arm builds in Prism's meta, and LF's armhf/riscv64/ppc64el OpenAL against any source.
- LWJGL's exact CMake options for its OpenAL builds (they live in LWJGL-CI's workflow, not the jar).
- Whether Prism's legacy launcher links on JDK 26 with `noapplet`.
- Which third-party Babric mods use log4j/Guava/commons.
- CVEs for the JREs individually, and the jutils licence text (assumed Sun BSD like jinput).
