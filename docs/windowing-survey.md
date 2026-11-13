# Windowing, input and sound: survey and recommendation

(Built: `docs/lwjgl.md`. This survey stays as the record of why.)

Claude Opus 5.5, 2026-11-01, for roadmap phase 3. Everything was checked on 2026-11-01 unless marked. Shallow clones
are in `local/survey/` (about 28 MB). Older clones of LWJGL 2 and LegacyFix are in `local/audit/`. I read code and issue
trackers only. I built nothing and ran no game.

## Recommendation: write our own, small, on LWJGL 3.4 with SDL3

**What it is.** An MIT-licensed implementation of the LWJGL 2 API that b1.7.3 and its mods call, running on
[LWJGL 3.4](https://github.com/LWJGL/lwjgl3) with **SDL3 as the only backend** (no GLFW). LWJGL 3's OpenAL Soft
handles sound. It ships as the `org.lwjgl` component of our Prism instance profile, in place of LWJGL 2, rather than as
a mod in `mods/`. The game-side fixes go in an Old Faithful mod as ordinary mixins: de-AWT, resize, HiDPI, the fullscreen
toggle, text-input areas.

**Why not keep LWJGL 2 (Legacy Fabric's build).** It is the right *interim* choice but can't reach phase 3's goals
without us taking on a dead C codebase on three platforms:
- No raw input. On Windows it reads `WM_MOUSEMOVE` and re-centres the cursor, so pointer acceleration applies.
- No Wayland, only XWayland.
- Fullscreen is an exclusive mode switch through xrandr or ChangeDisplaySettings.
- macOS HiDPI is partial and switched off in fullscreen. The maintainer has no Apple hardware
  ([LF#8](https://github.com/Legacy-Fabric/lwjgl/issues/8)).
- Controllers go through 2013 JInput natives. b1.7.3 calls `Controllers.create()` at startup, which is the known
  freeze path.

**Why not adopt an existing LWJGL 3 layer.** None is something we can depend on safely for the core of the edition:
- **legacy-lwjgl3** (moehreag) is the most mature, but it is effectively Ornithe-only. Its b1.7.3 mixins target
  Calamus names, and the Babric branch is from 2024-10. It has open b1.7.3 bugs: no sound, white screen, broken
  fullscreen.
- **Staráč** has been dormant since June. It is GLFW-only, and RetroCenter (server-pushed mods) is welded in.
- **Pylon** is technically the closest (SDL3, Java 8, supports Babric, active, LGPL-3.0, so legal as a separate jar).
  But it is two months old, has one main author, and spans many versions, so b1.7.3 isn't its focus. It also has open
  scaling and IME bugs, needs a separate de-AWT mod on b1.7.3, and modifies LWJGL 3's own classes. Depending on it
  makes the most player-facing layer someone else's moving target.
- **RetroDragon** is a renderer project, not a windowing layer to adopt.

**Why SDL3.** The ecosystem has converged on it:
- Mojang moved modern Minecraft from GLFW to SDL3 in 26.3 snapshot 4, July 2026, made borderless the default and
  preferred Wayland ([GamingOnLinux](https://gamingonlinux.com/2026/07/minecraft-26-3-snapshot-4-brings-sdl3-and-prefers-wayland-on-linux)).
- lwjgl3ify 3.x, legacy-lwjgl3 (its default), Pylon and RetroDragon all moved to it.
- On legacy-lwjgl3's tracker, users report that SDL fixes GLFW's Linux fullscreen and scaling bugs
  ([#31](https://github.com/moehreag/legacy-lwjgl3/issues/31)).
- SDL3 brings things we would otherwise write ourselves:
  - raw relative mouse;
  - Wayland pointer warping and decorations (Staráč needed a custom C helper and a prebuilt libdecor for these under
    GLFW);
  - IME text input with candidate-window placement;
  - per-display pixel density;
  - borderless and exclusive fullscreen;
  - a gamepad API for later.

**The shape.**
- **API gaps are generated, not hand-written.** A build-time tool (ASM) diffs LWJGL 2.9.4's public `GL*`, `ARB*` and
  `AL*` signatures against LWJGL 3.4's. It emits forwarding stubs for the missing ones, for example
  `AL10.alListener(int, FloatBuffer)` → `alListenerfv`, `glGetFloat` → `glGetFloatv` and
  `ARBOcclusionQuery.glGetQueryObjectuARB` → `glGetQueryObjectuivARB`. It merges them into the LWJGL 3 classes in the
  jar we ship. This is Pylon's "bake" idea, written fresh. The output has no runtime transformation, no Mixin into
  library classes and no two LWJGLs on one classpath. That ambiguity is the likeliest source of the "no sound"
  reports below.
- **`Display`, `Mouse`, `Keyboard`, `DisplayMode`, `Cursor` and `Controllers` are written fresh on SDL3.**
  `BufferUtils`, `Sys`, `LWJGLException`, GLU and `util.vector` are copied from LWJGL 2 itself, which is BSD, with its
  notices.
- **Sound:**
  - `AL.create/destroy/isCreated` on ALC.
  - We add what vanilla lacks: following the default output device when it changes (OpenAL Soft's system-events and
    reopen-device extensions), so unplugging headphones doesn't silence the game.
  - SDL3 and OpenAL Soft are plain shared libraries that LWJGL 3 `dlopen`s. Phase 2's "build natives ourselves" can
    therefore build them from upstream source (CMake) without touching LWJGL's own build. Only `lwjgl` and
    `lwjgl_opengl` are JNI.
- **Checks that track the goal and need no window focus:**
  1. A link check: every `org/lwjgl` member referenced by b1.7.3 and by the mods we care about resolves against our
     jar. This catches the whole NoSuchMethodError class of failure, including silent sound.
  2. Keyboard and mouse semantics tests that push synthetic SDL events into a hidden window and assert LWJGL 2's
     behaviour: numpad characters, held modifiers, repeat, AltGr. These are exactly where lwjgl3ify keeps regressing
     (below).
  3. A sound smoke test through paulscode with OpenAL Soft's `wave` backend writing to a file, which proves samples
     come out without speakers.

**Size, honestly.**
- About **4–5k lines of our own Java**:
  - Display and GL context: ~900;
  - Keyboard with scancode table and LWJGL 2 semantics: ~600;
  - Mouse: ~400;
  - AL and device follow: ~250;
  - macOS main-thread dispatch: ~150;
  - the stub generator: ~600;
  - game-side mixins: ~600–800;
  - tests: ~1000.
- Plus roughly 1.5k lines copied from LWJGL 2 (BSD) and the generated stubs.
- For comparison, Pylon is about 8k lines in total, including vector utilities and Forge/multi-version glue.
- Getting to "good on Windows" is a matter of weeks of model work.
- **The real cost is platform shake-out, not code:** macOS arm64, Wayland compositors, keyboard layouts and IMEs. Every
  open bug in every layer below is of that kind, and the one Windows PC here can't check them. We need a plan for macOS
  and Linux testing: hardware, or a few outside players. Whatever we choose, that is the open question for phase 3.

**What to learn from the others** (ideas only; their code is LGPL and stays out of ours):
- Pylon: macOS main-thread dispatch through libdispatch, so no `-XstartOnFirstThread` is needed (`MainThread.java`).
  Also deferring text input on Wayland while keys are held, and build-time baking with a hard failure on a missing
  patch target.
- legacy-lwjgl3: framebuffer scaling on by default, an IME preedit overlay with a small API for other mods' text
  fields, and per-version mixin gating.
- lwjgl3ify: a fully generated LWJGL 2 surface, plus its tracker as a catalogue of fidelity traps.
  - Numpad keys returning `\0` from `getEventCharacter` ([#332](https://github.com/GTNewHorizons/lwjgl3ify/issues/332)).
  - `isKeyDown` failing for modifiers on Windows under SDL3 ([#388](https://github.com/GTNewHorizons/lwjgl3ify/issues/388)).
  - AltGr read as Ctrl, and IME candidates stuck top-left ([#371](https://github.com/GTNewHorizons/lwjgl3ify/issues/371)).
  - macOS clipboard, and HDR switched off in fullscreen.
- Pylon [#18](https://github.com/notdevcody/pylon/issues/18): at 125% Windows scaling the fullscreen framebuffer is
  2048×1152 instead of 2560×1440. Set DPI awareness and high pixel density deliberately and test fractional scales.

**Design choices to make on purpose:**
- **Keys bind by physical scancode, names display by layout.** Modern Minecraft does this. LWJGL 2 on Windows mapped
  by virtual key, so AZERTY players had to rebind, and existing `options.txt` files carry LWJGL key codes either way.
- **Raw input on by default, with a toggle.** It changes feel for players used to Windows acceleration.
- **Borderless by default, exclusive as an option.**
- **Vsync and frame pacing are ours.** Vanilla never calls `setVSyncEnabled` or `Display.sync`; its limiter is its own
  code. So this belongs in phase 3 or 4 regardless of layer.

**Interim.** Until ours passes the checks above, ship Legacy Fabric's LWJGL `2.9.4+legacyfabric.18` on Java 21 with
our own de-AWT mixin (b1.7.3's `Minecraft.main` builds an AWT frame and calls `Display.setParent(canvas)`). This also
covers an early release before phase 3 completes.

**Distribution caveat.** A library component is natural in our pack. For "our mods on their own in existing setups" it
means asking players to swap a Prism component, as lwjgl3ify does with its patch files, rather than dropping in a jar.
A jar-in-jar or agent form could come later if people ask. Worth discussing.

## What b1.7.3 actually calls

I scanned the constant pools of the official client jar (Mojang's code, read locally; the extracted classes are
deleted). The jar references **144 distinct `org.lwjgl` members in 17 classes**, including paulscode, which b1.7.3
bundles inside `minecraft.jar`:

| Area | Members used |
|---|---|
| `GL11` | 61 |
| `Display` / `DisplayMode` / `GLContext` / `ContextCapabilities` | 12 / 3 / 1 / 3 fields (ARB occlusion query, ARB VBO, NV fog distance) |
| `Mouse` / `Keyboard` / `Cursor` | 15 / 9 / 1 |
| `ARBOcclusionQuery` / `ARBVertexBufferObject` | 4 / 3 |
| `AL` / `AL10` (paulscode) | 3 / 19 |
| `GLU`, `BufferUtils`, `Sys`, `LWJGLException`, `Controllers` | 2, 3, 2, 2, 1 (`Controllers.create`) |

Signatures that LWJGL 3 lacks include:
- `AL10.alListener(I, FloatBuffer)` and `AL10.alSource(II, FloatBuffer)`;
- `GL11.glGetFloat`, `glFog`, `glLight`, `glLightModel`, `glTexEnv` and similar buffer overloads;
- `glGetQueryObjectuARB`;
- `GLContext.getCapabilities` returning `ContextCapabilities`.

All the layers below hand-stub these. Mods add more: SmoothBeta and StationAPI use GL15+ through the LWJGL 2 API. That
is why the stubs should be generated against the full LWJGL 2 surface, not just this list.

## What players feel

| | LWJGL 2 (Legacy Fabric .18) | legacy-lwjgl3 1.4.10 | Pylon 0.2.6 | Staráč 1.3.2 | Ours (planned) |
|---|---|---|---|---|---|
| Backend | Native LWJGL 2 (C, 2015 base) | SDL3 (default) or GLFW | SDL3 | GLFW | SDL3 |
| High-DPI, scaling | Windows: sharp only if the JVM is DPI-aware and AWT is gone. macOS: partial, off in fullscreen, GUI scale capped at 3 (LF#8) | Framebuffer scaling on by default. Fractional-scaling fullscreen bug under GLFW (#31) | High pixel density off unless a mod asks. Lower-resolution framebuffer at 125% (#18, open) | GLFW content scale; fixes AWT white border (inherited from gambac) | Pixel density on, DPI awareness set; test 100/125/150/200% and Retina |
| Fullscreen | Exclusive mode switch | Black screen entering or leaving (#15, #31); b1.7.3 toggle broken on Windows (#41, 2026-10) | Borderless default, exclusive option; maximise+F11 bug fixed 2026-10-19 | GLFW | Borderless default, exclusive option |
| Raw mouse, grab | No raw input (Windows `WM_MOUSEMOVE` + recentre). UniTweaks adds raw input through JInput on a polling thread | GLFW raw motion or SDL relative mode | SDL relative mode, raw by default; fixes high-poll-rate lag | GLFW raw motion; custom Wayland pointer-warp native | SDL relative mode, raw by default, toggle |
| Keyboard, text | VK-based codes on Windows; no IME | Scancode mapping; IME with preedit overlay; Linux movement-keys bug (#34, open) | Scancode mapping; system IME, but on in non-text GUIs (#12) and clashing with IME mods (#23) | GLFW key callbacks | Scancode binding, layout names, IME only in text fields |
| Controllers | JInput 2013 natives; `Controllers.create()` freeze risk | — | stub | — | `Controllers` stub now; SDL gamepad later |
| Sound | Works; sounds cut off at the end on macOS arm64 (LF#10, open, Java side suspected); unverified OpenAL DLLs | **No sound on b1.7.3** (#14, 2024-10; #22, Babric, 2025-07), both open | LWJGL 3 OpenAL Soft; no sound reports | LWJGL 3 OpenAL Soft; no tracker activity | LWJGL 3 or our own OpenAL Soft; follows device changes; wave-backend test |
| Vsync, pacing | `Display.setVSyncEnabled` (vanilla never calls it) | swap interval 0/1 | swap interval 0/1 | swap interval | ours: adaptive where available, refresh-aware limiter |
| macOS arm64 | Natives exist; HiDPI fixes partial; maintainer can't test | Works with caveats; a Tahoe hang the maintainer attributes to the driver (#30, open) | Main-thread dispatch built in; fixes the resize crash | MacOSDisplayHelper; inverted colours fixed | Pylon-style dispatch; needs a tester |
| Linux Wayland, X11 | X11 and XWayland only | Native Wayland; no system decorations (#25, open); scaling mismatch fixed (#17) | Native Wayland through SDL | Native Wayland with its own libdecor and warp helpers | Native Wayland through SDL |
| Java | 8+ | 17+ | 8+ | 21+ | 8+ API; we pin 21 or 25 |

## What we care about

| | Licence | Maintenance (2026-11-01) | Code | Size | API coverage, method | b1.7.3 on Babric |
|---|---|---|---|---|---|---|
| [Legacy-Fabric/lwjgl](https://github.com/Legacy-Fabric/lwjgl) | BSD | `.18` 2026-09-30; thecatcore alone; 4 open issues | LWJGL 2 itself plus fixes; C natives | Small jar + natives per OS | Complete by definition | Yes (Babric's default) |
| [moehreag/legacy-lwjgl3](https://github.com/moehreag/legacy-lwjgl3) ([Modrinth](https://modrinth.com/mod/moehreag-legacy-lwjgl3)) | LGPL-2.1 | 1.4.10 2026-10-04; moehreag 113 of ~170 commits; 15 open issues; 25.6k downloads | Decent; some carelessness (ALC failure checks compare with `-1` instead of `0`) | 17.6 MB (GLFW+SDL, 6 platforms) | Hand-written stubs merged into LWJGL 3 classes by Mixin at runtime | **No**: Ornithe/Calamus mixins; `babric-b1.7.3` branch last 2024-10-09 |
| [notdevcody/pylon](https://github.com/notdevcody/pylon) ([Modrinth](https://modrinth.com/mod/pylon)) | LGPL-3.0-only | Started 2026-08-24 (as "Lenis"); 0.2.6 on 2026-10-25; releases every few days; notdevcody 47 of 57 commits; 6 open | The cleanest of the lot: small, careful error handling | ~8k lines; 13 MB jar | Hand stubs baked into LWJGL 3 classes at build time; LWJGL 2 classes written fresh | Yes since 0.2.4 (2026-10-19), **plus a de-AWT mod** (it recommends LegacyFix, GPL-3.0) |
| [matthewperiut/starac](https://github.com/matthewperiut/starac) ([Modrinth](https://modrinth.com/mod/starac)) | LWJGL dirs LGPL-2.1 (from legacy-lwjgl3), rest MIT | Last commit 2026-06-09; 380 downloads; no issues filed | gambac fork + legacy-lwjgl3's GLFW code + Wayland natives + RetroCenter + StationAPI compat | 9.6 MB | As legacy-lwjgl3 | Yes |
| [matthewperiut/retrodragon](https://github.com/matthewperiut/retrodragon) | Says MIT + LGPL-2.1 | Started 2026-07-30; 0.1.15 2026-09-14; one author; not on Modrinth | Staráč's layer moved to SDL3, plus a WebGPU (Dawn) renderer and GL shim; ~160k lines including generated bindings | Large | — | Experimental renderer, not a layer to adopt |
| [MCPHackers/legacy-lwjgl3](https://github.com/MCPHackers/legacy-lwjgl3) | LGPL-2.1 | 2026-07-11 | For LaunchWrapper, not Fabric; relocates to its own package | — | — | No |
| [Zarzelcow/legacy-lwjgl3](https://github.com/Zarzelcow/legacy-lwjgl3) | LGPL-2.1 | Dead since 2023 | Origin of legacy-lwjgl3 and Staráč's layer | — | — | — |
| [GTNewHorizons/lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify) | LGPL-3.0 | 3.0.42 2026-10-30; eigenraven; 78 open issues; 39k downloads | The most mature. Relocates the LWJGL 2 API to `org.lwjglx` with a generated full surface; SDL3 in 3.x | — | Full | 1.7.10 Forge only; reference |
| BTA's fork | Private | — | — | — | — | Not available |

Smaller finds that aren't layers: [CypressLWJGL3](https://github.com/Cfauto28/CypressLWJGL3) (a legacy-lwjgl3 port),
[legacy-lwjgl3-lenis-shim](https://github.com/thecountrox/legacy-lwjgl3-lenis-shim), and
[BlazeSDL](https://modrinth.com/mod/blazesdl) (SDL3 for modern versions, ended once Mojang adopted SDL3).

**Lineage, for licences.** Zarzelcow → moehreag's legacy-lwjgl3 → Staráč's `lwjgl3compat` → RetroDragon's
`window/lwjgl3compat`, all LGPL-2.1. Pylon is independent (LGPL-3.0).
- Much of every layer's `GLU`, `PixelFormat`, `util.vector` and `Sync` is LWJGL 2's own BSD code. Take those from LWJGL
  2 directly, never through the layers.
- OpenAL Soft is LGPL: shipping its library unmodified as a separate file is fine, with a pointer to its source.
- SDL3 and GLFW are zlib.

## Notes on the "no sound" reports

- **legacy-lwjgl3 #14 and #22** (b1.7.3) are open with no diagnosis. #22 says the Ornithe build has sound and the Babric
  build doesn't.
  - All layers now stub `alListener`, `alSource` and `alSourceStop(IntBuffer)`, so a missing stub isn't the cause any
    more.
  - b1.7.3's paulscode sits inside `minecraft.jar`, so legacy-lwjgl3's trick of moving the separate
    `librarylwjglopenal` jar onto Knot's classpath does nothing here.
  - My unverified guess is that both LWJGLs are on the Knot classpath and `org.lwjgl.openal.*` resolves to LWJGL 2 for
    some classes. Replacing the component, rather than layering on top, removes that whole class of failure.
- **Legacy Fabric LF#10** (cut-off sounds, macOS arm64) is in their Java changes, per the reporter. It wouldn't touch a
  LWJGL 3 build.
- The "no sound" reports on UniTweaks and MojangFix are about sound downloads, not OpenAL.

## Not checked

- I didn't build or run any of these, so every behaviour above comes from code and issue trackers, not observation.
- I didn't verify SDL3's default DPI behaviour on Windows. The Pylon #18 diagnosis is mine.
- I didn't verify LWJGL 3.4's Java 8 floor beyond Pylon targeting Java 8 with LWJGL 3.4.x.
  - On JDK 24+, JNI and FFM native access warn without `--enable-native-access=ALL-UNNAMED`. Our instance profile
    should set it.
  - LWJGL 3.4 uses FFM by default on JDK 25.
- I didn't read Staráč's RetroCenter again, or RetroDragon beyond its windowing and licence.
- I didn't count which LWJGL 2 members the popular Babric mods reference. The link check above would do that once we
  pick the mods.
- How we test on macOS and Linux is open.
