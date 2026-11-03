# Our LWJGL component: the LWJGL 2 API on LWJGL 3 and SDL3

Claude Opus 5.5, 2026-11-01. Phase 3's windowing, input and sound layer, as `docs/windowing-survey.md` recommended
and the user accepted: our own MIT implementation of the LWJGL 2 API that b1.7.3 and its mods call, on LWJGL 3.4.3
with SDL3 as the only backend, shipped as the launcher's `org.lwjgl` component in place of LWJGL 2. First milestone
(Windows, enough for a client check) built and tested windowlessly; the user's first client check of it passed
(2026-11-01) and its findings (title, mouse speed, paste) are fixed.

## What ships

| Piece | What it is |
|---|---|
| `oldfaithful-lwjgl-<v>.jar` (`org.studioquire.oldfaithful:oldfaithful-lwjgl`) | One jar: LWJGL 3.4.3's core, OpenGL, OpenAL and SDL classes (Central's, PGP-signed by LWJGL's lead, pinned by hash), plus the LWJGL 2 API. Baked at build time, nothing transformed at runtime, one copy of every class |
| natives | `lwjgl.dll`, `lwjgl_opengl.dll`, `SDL3.dll` (3.4.18), `OpenAL.dll` (our OpenAL Soft 1.25.2), all built here (`docs/natives.md`) |
| `oldfaithful-display` mod | The game-side half: off the applet frame, resizable, HiDPI, fullscreen. Does nothing (and says so in the log) on LWJGL 2 |

The `org.lwjgl` component is `prism/sdl-dev/patches/org.lwjgl.json`; `./gw prismSdlInstance` builds the whole dev
instance around it (`docs/building.md`).

## How the jar is made (`buildSrc/.../lwjgl/LwjglBaker.java`)

- **Generated forwarders.** For each LWJGL 2 GL/AL binding method (read from our LWJGL 2 build) that LWJGL 3 lacks,
  a forwarding method is added to the LWJGL 3 class of the same name. LWJGL 2 drops type suffixes and overloads on the
  buffer (`glGetFloat(int, FloatBuffer)` -> `glGetFloatv`; `glGetQueryObjectuARB` -> `glGetQueryObjectuivARB`;
  `glGetInteger(int, int, IntBuffer)` -> `glGetIntegeri_v`), takes Strings where LWJGL 3 takes CharSequence, and
  for some pointer functions infers the GL type from the buffer or an `unsigned` flag (`glColorPointer(4, true, 32,
  ByteBuffer)`, as b1.7.3's tessellator calls it). For those, the type and its position are read from LWJGL 2's own
  implementation (the constant it passes to its native call), not guessed. Methods inherited in LWJGL 2 from a class
  LWJGL 3 lacks (ARBProgram, ARBBufferObject) forward to wherever LWJGL 3 declares the function.
  Counts (build report, `oldfaithful-lwjgl/build/reports/lwjgl-bake.txt`, also inside the jar): 3,498 LWJGL 2 binding
  methods; 2,276 already in LWJGL 3; 741 forwarders generated (29 supplying a GL type); 481 unmapped, nearly all
  obscure vendor extensions, GLSync/callback types, or LWJGL 2's convenience overloads of rare queries. The link check
  says which of those anything we care about uses: none.
- **Patch classes** (`@Into("...")`): hand-written LWJGL 2 methods that need logic, forwarded from the LWJGL 3 class:
  `AL.create/destroy/isCreated/getDevice/getContext`, `ALC10`'s device/context-object methods, two `BufferUtils`
  helpers, `GL20.glShaderSource(int, ByteBuffer)` and its ARB twin (SmoothBeta uses it). A public LWJGL 3 method in
  the way is a build error; a package-private one is renamed with its callers (only `AL.destroy()`, which LWJGL 3's
  `ALC.destroy` calls).
- **LWJGL 2's argument checks** (`Checks`, 2026-11-02). LWJGL 2 checked every buffer a binding method was given
  (`BufferChecks`: remaining size, directness, null termination; `GLChecks`' image sizes for glReadPixels,
  glTexImage*, glTexSubImage*, glGetTexImage and the like) and threw `IllegalArgumentException`; LWJGL 3 mostly
  doesn't, so a buffer too small for glReadPixels would be overrun in native memory, and a heap buffer would be read
  as an address. The bake reads each method's checks from LWJGL 2's bytecode and puts the same calls, pointed at our
  `LegacyChecks` (LWJGL 2's messages, off with `-Dorg.lwjgl.util.NoChecks=true` as before), at the top of the LWJGL 3
  method or forwarder that stands in for it: 1,205 checks in 1,099 methods. As in LWJGL 2.9.4, image sizes ignore
  pixel-store state (row length, alignment, skips; 2.9.4's `GLChecks` doesn't read it), an unknown format or type
  checks nothing, and glGetTexImage checks one pixel (LWJGL 2 couldn't know the texture's size). Not restored, and
  listed in the bake report: 25 checks whose size isn't an expression of the arguments (NV_path_rendering's
  metrics, indirect draws' stride conditionals, PointerBuffers), and LWJGL 2's state-tracked ones (a pixel buffer or
  vertex buffer object bound or not, which would need its `StateTracker` or a GL query per call; with one bound,
  LWJGL 3 passes the buffer's address as an offset and the GL reports the error).
- **Generated `ContextCapabilities`**: LWJGL 2's 378 flags, 313 of them copied from LWJGL 3's `GLCapabilities` fields
  of the same names. Of the 65 LWJGL 3 doesn't know, 36 are set as LWJGL 2 set them, from the context's extension
  strings (read as LWJGL 2's `GLContext` read them; the string each flag tests is read from LWJGL 2's own
  constructor, so `GL_EXT_texture_3d`, which tests a name no driver reports, stays false there too). The other 29
  (ATI's and NV's old program and combiner extensions, EXT_fog_coord, EXT_draw_range_elements,
  EXT_multi_draw_arrays and the like) stay false: they have functions LWJGL 3 has no binding for, so their LWJGL 2
  classes aren't in the jar. LWJGL 2 cleared a flag when it couldn't load the extension's functions; true would send
  a caller into a NoClassDefFoundError. None of the 65 is read by b1.7.3 or the checked mods.
- **Written fresh** (`oldfaithful-lwjgl/src/main/java`): `Display`, `Drawable`, `SharedDrawable`, `GLContext`,
  `Util`, `Keyboard`, `Mouse`, `Cursor`, `Controllers` (none attached), `Sys`, `MemoryUtil`, `LWJGLUtil`, the AL patch
  and the internals in `org.studioquire.oldfaithful.lwjgl` (SDL window, input state, key map).
- **Taken from LWJGL 2 as compiled by our build** (BSD): `LWJGLException`, `DisplayMode`, `PixelFormat`,
  `OpenGLException`, `input.Controller`, and lwjgl_util's geometry, vector and GLU packages (82 classes), which call
  LWJGL 2's GL API and so run on the forwarders. Nothing from the LGPL layers (Pylon, Staráč, legacy-lwjgl3).
- Our natives' SHA-1s replace Central's in `META-INF/windows/x64/...`, so LWJGL 3's library check passes for ours.

## Behaviour, and where it differs from LWJGL 2 on purpose

- **Sizes are framebuffer pixels** (window coordinates times SDL's pixel density); `getPixelScaleFactor()` is 1.
  So the game renders at the screen's resolution and mouse coordinates match, on any display scale.
- **Keys bind by physical position** (SDL scancode -> LWJGL 2's DirectInput codes), so W/A/S/D are the same keys on
  AZERTY. LWJGL 2 on Windows bound by virtual key, so a non-QWERTY player's existing rebinds can shift once.
  `getKeyName` shows the layout's letter for letter keys (AZERTY's forward key reads "Z"), LWJGL 2's names otherwise;
  a key whose LWJGL 2 name the layout gave to another key is named by what it types (AZERTY's key LWJGL 2 calls M,
  beside the "M" key, reads ","), and keys LWJGL 2 never named (the ISO key left of Z) likewise, else "KEY" and the
  code. Every name is unique, so `getKeyIndex(getKeyName(k)) == k` on any layout and mods that save keybinds by name
  read them back; `getKeyIndex` also takes LWJGL 2's own names ("NONE" included) unless the layout shows that name on
  another key, in which case it means that key (on LWJGL 2 for Windows, "Z" meant the key labelled Z, too). Names
  follow layout changes (SDL's keymap event).
- **Keyboard events**: at most 250 unread, as LWJGL 2 kept them (its read buffer of 50 behind its window's queue of
  200), newer ones dropped when full; key states follow every event regardless.
- **`Display.wasResized`** is true from the update that saw the window change size until the next update, however
  often it's read, as in LWJGL 2.
- **Characters**: each key press carries the character it types in the same event, as LWJGL 2 did with Windows'
  WM_CHAR (Enter '\r', Backspace '\b', keypad digits with Num Lock; with Ctrl: letters and @ [ \ ] ^ _ their control
  codes, Enter '\n', Backspace 127). Control codes come in text fields too, where SDL never sends them as text: b1.7.3
  pastes on Ctrl+V (22); Tab, Enter and 22 are the only control characters its code reads. SDL text input (and the system IME) is on
  only while repeat events are, which is exactly while b1.7.3's text fields are open; there, text joins its key press
  or arrives as its own event with key 0 (IME, dead keys). Elsewhere characters come from the layout's key map, so an
  IME never sits in the way of movement keys.
- **Mouse**: grabbed motion is SDL's relative mode, moved as the desktop pointer would be: the system's pointer speed
  and acceleration applied to raw input (SDL's relative system scale), as LWJGL 2 read it on Windows.
  `mouse.input=raw` in the display mod's config gives the device's own motion. Consecutive motion merges into one
  event (high-rate mice). Why: Mouse, below.
- **Clipboard**: `Sys.getClipboard` is SDL's; the display mod points b1.7.3's paste (`Screen.getClipboard`, AWT in
  vanilla) at it.
- **Fullscreen** is borderless at the desktop's mode by default; `fullscreen.exclusive=true` sets a real video mode
  (the closest to the mode asked for, or the desktop's). The window never minimises on focus loss.
- **No AWT embedding**: `Display.setParent` is accepted and ignored; the display mod takes the game off the canvas.
- **Sound**: one device and context as LWJGL 2 opened them; when the system's default output changes, the device
  moves to the new default (OpenAL Soft's system-events and reopen extensions) instead of going silent.
- **Vsync** is set explicitly at creation, never left to the driver: off in the layer itself, as LWJGL 2 set it (swap
  interval 0; b1.7.3 never changes it), and **on for players** through the display mod's `vsync=true` (2026-11-02:
  the user found the game really bad without it on today's displays, where it tears and judders at hundreds of fps).
  `Settings.vsync` carries the choice; `Display.setVSyncEnabled` still works. Vsync starves b1.7.3's chunk building,
  which `oldfaithful-speed` makes up for (`docs/speed.md` > Chunk building under vsync).
- Gamma (`setDisplayConfiguration`), custom cursor images and OpenCL sharing aren't offered.

## Mouse: matching LWJGL 2's feel (2026-11-01)

The user's first client check found mouse look faster than vanilla at the same sensitivity, though smooth. Cause:
LWJGL 2 on Windows never read the mouse itself. Grabbed, it took WM_MOUSEMOVE pointer positions and re-centred the
cursor each frame (`WindowsMouse`, `WindowsDisplay.centerCursor` in its source), so the game saw the pointer's
movement: the device's counts after Windows' pointer speed slider and, with "Enhance pointer precision" on (Windows'
default, and on here), its acceleration curve. We read raw counts. At aiming speeds that curve gives well under one
pixel per count, so raw felt faster. `tools/mouse_ballistics.py` feeds a synthetic mouse through SDL's port of
Windows' ballistics (SDL 3.4.18's `WIN_ApplySystemScale`: fixed-point, per input report) with this PC's settings
(slider 10/20, enhance on, default curves). Pixels per count, which is what the game turns by:

| Hand speed (800 DPI, 1000 Hz) | 1 cm/s | 5 | 10 | 20 | 40 | 80 |
|---|---|---|---|---|---|---|
| LWJGL 2 (Windows pointer, modelled) | 0.57 | 0.60 | 0.72 | 0.92 | 1.11 | 1.85 |
| Ours before (raw) | 1 | 1 | 1 | 1 | 1 | 1 |

So raw was 1.1 to 1.75 times faster for ordinary aiming and slower only for fast flicks. Our layer now asks SDL for
its relative system scale by default, which applies that same function to raw input, per report, from the same
registry curves and settings, and follows changes to them live; our float accumulation keeps the fractions as
Windows' pointer does. The "after" column therefore equals the LWJGL 2 one by construction. Not measured: SDL's port
against Windows itself, which needs injected input (SendInput) and so moves the cursor of whoever is using the PC.
The user's feel check is the real test.

The choice, for the Purist edition: on Windows, beta's mouse look followed the desktop pointer's settings,
acceleration included, for every player; players who had turned acceleration off (many had) got one pixel per count
there and get the same here. That is "as beta felt", so it's the default. Raw input stays one setting away
(`mouse.input=raw`): many players prefer it and a modern game would use it, so it's a candidate default for the
Fixed edition and belongs in our settings screen when there is one. Doubts: at display scales other than 100% SDL
scales by the display's DPI for DPI-aware processes, not compared with Windows; macOS (where LWJGL 2 also read the
pointer) needs the same look when it comes. Linux: below.

## Even motion under vsync (Claude Opus 5.5, 2026-11-02)

The user found mouse look slightly uneven with vsync on ("micro-stutters"), our instance better than plain b1.7.3
but not smooth; with the speed mod off, better still. Three causes, from the code and the benchmark's per-frame
timing (`docs/speed.md` > The benchmark, columns `pump_us` and `instant_us`):
- **When input is read.** b1.7.3 reads the mouse when `Display.update` returns, before it renders; the frame shows
  the motion since the previous read. Under vsync the frames are shown one refresh apart, but the read lands at a
  different point of each: after the ticks (every third frame at 60 Hz) and, with the speed mod, sometimes after the
  refresh wait itself (the driver's wait moved to the "Pre render" `glGetError` on tick frames; why, unclear). So the
  view turned by uneven amounts per frame shown: standing with the speed mod, 64% of read-to-read intervals were more
  than 4 ms off the refresh grid (7.6% without it).
- **The game's clock.** `Timer.advance` measures in whole milliseconds, at the top of the loop, which itself lands at a
  varying point of the refresh; walking, entities and the camera move by that measure.
- **Whole pixels.** Mouse look turns by integer deltas per frame, each axis rounded on its own (as the Windows
  pointer LWJGL 2 read did): a slow diagonal steps one axis, then the other. Beta's own look.

What we do (display mod, our layer only):
- `MouseState.frameStart(instant)` gives grabbed motion a cut-off: motion stamped after the frame's instant (SDL stamps
  raw mouse input as it arrives, on its own thread) waits for the next frame, wherever the pump falls. Motion stamped
  during the pump itself (a backend that stamps on reading) counts at once, as before.
- `FrameClock` (`frame.smooth`, on by default): with vsync on, each frame's instant is the last plus the display's
  period (the median frame interval, if within a tenth of the refresh), drawn an eighth of the way towards the
  measured top of the loop so it stays locked to it; a missed refresh or any jump over half a period takes the measured
  time. The timer (`TimerMixin`) and the mouse cut-off both use it, so movement and turning agree and each frame moves
  the world by one refresh's worth. Without vsync it's the measured time at full precision; `frame.smooth=false` is
  vanilla's timing exactly.
- `mouse.subpixel` (off by default; the user's call, 2026-11-02: off in Purist, an option in Fixed): look turns by the unrounded motion (`MouseState.takenFX`,
  the same events the integer deltas came from), so slow diagonals don't step. A feel change against beta's whole
  pixels: which editions take it, and the default, are the user's call.

## Linux (Claude Opus 5.5, 2026-11-01)

The component runs on Linux x64 with natives built here in Docker (`docs/natives.md` > Linux x64); the jar carries
their hashes beside the Windows ones, and `prism/sdl-dev/patches/org.lwjgl.json` names `natives-linux` for each
library. The Java side needed no change.

- **Tested**, in containers with an X server nobody sees (Xvfb) and Mesa's software GL, on AlmaLinux 8 (glibc 2.28,
  Mesa 23.1, the oldest system the natives target) and Ubuntu 24.04 (Mesa 25.2): every `lwjglTest` check passes on
  both (`:harness:lwjglTestLinux`): GL 4.5 compatibility on llvmpipe, the tessellator-style draws, shared contexts,
  resize, all the keyboard, text and mouse checks, sound through OpenAL Soft's wave writer with a device reopen
  mid-stream. No LWJGL hash complaints, so the baked `.sha1`s match. The real game passes `clientStartupTestLinux` on both,
  with no warnings in its output: main menu, title, hidden window, mouse setting, the paste key path (Xvfb's
  clipboard is empty, so no text pasted), a world created and run 100 ticks, sound to a file, a clean stop.
- **Mouse**: LWJGL 2 on X11 read the pointer too (`LinuxMouse`: motion events' positions, the pointer warped back to
  the centre), so grabbed motion carried X's pointer acceleration. SDL 3.4.18 with our default (relative system scale
  on) uses, on X11, the XInput2 raw event's `valuators.values` instead of its `raw_values`
  (`SDL_x11xinput2.c` `parse_relative_valuators`), and on Wayland the relative-pointer protocol's accelerated `dx`
  times the window's pointer scale instead of `dx_unaccel` (`SDL_waylandevents.c`). Per XInput2's definition the
  former are the values after the server's acceleration; whether every X driver (libinput, evdev) fills them that
  way, and how the result compares with LWJGL 2's warp-and-read in feel, isn't measured: Xvfb has no real pointer, so
  it's for a Linux player's check. LWJGL 2 never ran natively on Wayland (XWayland), so there the accelerated motion
  is the faithful choice. `mouse.input=raw` gives the unaccelerated values on both.
- **Not tested here**: Wayland itself (SDL prefers it when the compositor supports what it wants, else X11 through
  XWayland; libdecor draws the title bar on GNOME), real audio servers (PipeWire, PulseAudio, ALSA: all built in and
  loaded at runtime), IMEs (IBus is built in), controllers, NVIDIA's and AMD's drivers, HiDPI. Those need Linux
  players (the user's plan: a few outside testers).

## The display mod (`oldfaithful-display`)

- **De-AWT**: Fabric starts b1.7.3 as an applet in an AWT frame. The mod keeps the game's canvas out of the applet
  (adding it is what started the game thread), tells the game it has none (b1.7.3's own path without a canvas),
  starts the game thread when the applet starts, and makes Fabric's frame an invisible, unfocusable 1x1 window before
  it's shown, disposing of it right after. A crash gets its own window with the game's crash panel.
- **Resize and HiDPI**: the window is resizable, and each frame the game takes the framebuffer's size as it took the
  canvas's (and once right after the window opens, so the loading screen fits); the first window opens scaled to the
  display (`window.scaleToDisplay`).
- **The launcher's window settings** (Claude Opus 5.5, 2026-11-02). Prism's "Window size" and "Start Minecraft
  maximized" never reached a Fabric b1.7.3 instance, on LWJGL 2 either: Prism hands them to an old game only through
  its own applet frame (`LegacyFrame`: the applet's preferred size, then `MAXIMIZED_BOTH`), and our instance, like
  Babric's, has the `noapplet` trait so Fabric can start the game, which makes Prism's legacy launcher call Fabric's
  main "without size parameters" (Prism 9.4's `LegacyLauncher`; checked by running the installed `NewLaunch*.jar` with
  a probe main class: no `--width`/`--height`, for a size or for maximised). Fabric's own frame then opened at
  854x480. What does arrive: Prism's `SystemProperties` sets `org.prismlauncher.window.dimensions` (`<w>x<h>` or
  `maximized`) "for mods". The display mod reads it (`WindowPlacement`) and opens the window as vanilla in Prism's
  frame did: at that size exactly (unscaled; `window.scaleToDisplay` only scales the game's own 854x480, which is also
  Prism's default and so means "not set"), or maximised over the game's usual size, which is what it restores to.
  `--width`/`--height` from other launchers still size Fabric's frame and so the game, as before. Every window is kept
  whole on the usable area of a display (moved in, shrunk only if larger), which changes nothing for one that fits.
- **Remembering the window** (`window.remember`): its windowed bounds, maximised state and display are kept as the game
  stops (`config/purefaithful-display-window.properties`) and restored next launch, following the display by name if
  it moved in the desktop and onto another if it's gone; a launcher setting changed since the window was kept wins
  (the file holds the launcher's request then), so the last thing changed wins. Fullscreen is kept too (the user's ask,
  2026-11-02; vanilla didn't): a game that stopped fullscreen opens its kept window and goes fullscreen over it as F11
  would, so the game's flag agrees and one F11 brings that window back (maximised or not). Fullscreen comes as
  `fullscreen.exclusive` says now, on whichever display the window opened on (its own, or the one it was fitted
  onto if that's gone); a changed launcher setting opens windowed, as the launcher asks. **Off in Pure Faithful**, on in Old Faithful (user, 2026-11-02): Pure only undoes our launch's loss of the
  launcher's settings; remembering the window is a change from the original, so it belongs to the Fixed edition,
  which sets it on by default (`oldfaithful-options`, below).
- **Starting maximised** (`window.startMaximized`, 2026-11-02): with neither the launcher's setting (Prism's property,
  or `--width`/`--height`) nor a remembered placement, the window opens maximised over the game's usual size. Off in
  Pure Faithful; on in Old Faithful (the user's ask), which with `window.remember` makes it the first start only.
- **Defaults from other mods**: a mod's fabric.mod.json can ask for defaults, `"custom":
  {"purefaithful-display:defaults": {"<setting>": "<value>"}}`; they stand in for ours wherever the config file says
  nothing and are what gets written for a missing setting. Old Faithful's `oldfaithful-options` asks for
  `window.remember` and `window.startMaximized`, so standalone installs get its window as the pack does
  (`docs/options.md`).
- **Title**: b1.7.3 titles its Display "Minecraft Minecraft Beta 1.7.3", which nobody saw: the display sat inside the
  launcher's frame, titled "Minecraft". The window gets the frame's title instead.
- **Paste** reads the clipboard through our `Sys.getClipboard` (SDL) rather than AWT.
- **Fullscreen toggle (F11)**: no display-mode switch and no desktop-sized window afterwards; borderless fullscreen
  survives alt-tab (exclusive still leaves on focus loss, as before).
- **Vsync** on by default (`vsync`); LWJGL 2 had it off (above).
- **Even motion**: the frame clock and the mouse's cut-off (`frame.smooth`), and sub-pixel look (`mouse.subpixel`):
  "Even motion under vsync" above.
- Config: `.minecraft/config/purefaithful-display.properties`.

## Tests (all windowless; `docs/building.md` has the commands)

- `:harness:linkCheck`: every `org.lwjgl` reference in b1.7.3 (161), our mods, 20 popular b1.7.3 Babric
  mods on Modrinth (`harness/linkcheck-mods.json`, pinned by SHA-512; StationAPI, SmoothBeta, UniTweaks, MojangFix,
  Mod Menu, Glass Config API, Ears, ...) and our jar's own classes resolves against the jar. 0 unresolved. Expected:
  fullscreen-tweaks mixes into LWJGL 2's internal `WindowsDisplay`/`LinuxDisplay`, which can't exist here (what it
  fixes, minimise on focus loss, we do anyway). Mixins into our classes' internals aren't checked beyond that.
- `:harness:lwjglTest`: every class the bake changed loads, verifies and initialises; Display on a hidden SDL window
  with a GL context (draws as the tessellator does through the type-supplying forwarders and reads the pixels back;
  gluPerspective via `glGetFloat`; fog and light buffers; LWJGL 2's checks rejecting too-small and heap buffers for
  glReadPixels, glTexImage2D, glTexSubImage2D, glGetTexImage and glGetFloat; the extension-string flags against the
  context's extensions; a SharedDrawable on another thread; resize and `wasResized`; a placement off every display
  opening whole on one, and one following its display, maximise pending); keyboard and mouse against
  synthetic SDL events (characters, numpad, held modifiers, Ctrl, AltGr, repeats, text input and IME text, focus
  loss, the 250-event bound, key names round-tripping on this layout and on synthetic AZERTY, QWERTZ, Dvorak and 300
  random ones, positions, merged motion, buttons, wheel, grab and release); the game's own paulscode (from the
  official jar, read locally) playing a game sound and streaming music through our AL into OpenAL Soft's wave writer,
  with a device reopen mid-stream; the file must hold sound. Passes on Java 21.0.13 and 25.0.5.
- `:harness:clientAuditSdl`: the client audit with our jar on the classpath and both mods, so every display-mod
  injection point is applied and checked (a broken one was checked to fail).
- `:harness:clientStartupTest`: the real game on this component, started as the SDL dev instance starts it, unseen on
  a private desktop: main menu, title, hidden window, mouse setting, a synthetic Ctrl+V into the server address, a
  small world created and run for 100 ticks, the game's own clean stop; the window at Prism's size, and three short
  launches for Prism's maximised, the remembered window's round trip, and stopping fullscreen then starting in it and
  F11 back to the kept window (`docs/building.md`). Borderless only, on a hidden window, where SDL holds fullscreen as
  a pending state: the flags and the kept window are checked, not a window actually covering a display; exclusive
  isn't run, since a video mode change would reach the user's screen from the private desktop.
  `./gw :oldfaithful-display:test`: which kept files restore fullscreen (only with their remembered placement; not
  after a launcher change, nor with remembering off).

## Not done yet, and doubts

- Real windows and focus, the mouse's feel and sound through speakers stay the user's checks; the start-up test
  covers start-up, the de-AWT path, input plumbing and a world, unseen.
- Untested here: fullscreen (borderless and exclusive) and display scales other than 100% (this PC's), multiple
  monitors, real IMEs and non-US layouts, an actual default-device change. A real Prism launch with a window size or
  maximised set (the hidden test passes Prism's property itself, and a hidden window's maximise is SDL's pending one,
  applied when it's shown).
- A tear on alt-tab (the user, Still Faithful's play-test, 2026-11-03): in borderless fullscreen, sometimes a line
  of tearing with the world showing through (the pause backdrop, most likely). Not reproducible unseen (it's in
  what the display scans out; a read-back frame is whole). From the code (Claude Opus 5.5): every frame is drawn
  whole and swapped with vsync, focus changes neither the window's size nor the swap interval, and the pause screen
  comes 0.5 s after focus is lost (beta's `onFrameUpdate`), so a frame without the backdrop is one from before the
  pause: most likely the driver's present path switching between flipping a window that covers the display and
  composing it, and briefly showing its older buffer. The user's answers: a maximised window doesn't seem to do it;
  NVIDIA's "Prefer layered on DXGI swapchain" doesn't stop it. Remedy if ever wanted: a window one pixel larger than
  the display in borderless fullscreen (always composed, as Chromium does; up to a frame more latency, as maximised).
  Left alone (the user's call, 2026-11-03).
- Java 25 prints LWJGL's `sun.misc.Unsafe` deprecation warning (its legacy memory backend); harmless now.
- macOS (at release, the user's call): natives need CI (`docs/natives.md`) and the main-thread dispatch the survey
  describes. Linux arm64: planned
  in `docs/natives.md`. Linux x64 on real desktops: above.
- Controllers (SDL gamepad), an icon for the window, IME candidate placement, `Display.sync`-based frame pacing.
- Standalone use (an existing instance, not our pack): an importable component file and a one-step guide, for
  release; the component format is the dev instance's `org.lwjgl.json`.
