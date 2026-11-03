# Roadmap

Claude Opus 5.5, 2026-11-01, agreed in outline with the user the same day. Phases overlap where they don't depend on
each other. Rules from the user: everything except Minecraft is audited, pinned and kept under our control; anything
we can't bundle, we write ourselves; spending long on the lower layers is fine.

1. **Foundation** (in progress). Pinned, checksummed build on upstream Fabric Loader and Mixin with Babric's mappings;
   the hardening mod (safe resource downloader, HTTPS login, working skins); a headless dedicated-server test harness.
2. **Lower layers under our control.** Review and mirror every artifact we ship (loader, Mixin, ASM, mappings,
   LWJGL); build LWJGL's natives (OpenAL first) from source ourselves; reproducible builds and dependency
   verification; our own launcher instance profile with a modern Java and current libraries, replacing Babric's.
3. **Runs on modern machines.** One windowing, input and sound layer, settled after comparing the existing efforts
   (maintained LWJGL 2, or our own LWJGL 3 layer): high-DPI, fullscreen, raw input, working sound, current Java,
   Windows, macOS and Linux. The user's client checks start here. Settled: our own LWJGL 2 API on LWJGL 3 and SDL3
   (`docs/windowing-survey.md`). First milestone built and tested windowlessly 2026-11-01 (`docs/lwjgl.md`): the
   component, its natives, the display mod, a link check against 20 popular mods, a dev instance. The user's first
   client check of it passed and its findings are fixed (below); an unseen start-up test of the real game runs here.
   Linux x64 done the same day: natives built in Docker, the component and the real game tested on AlmaLinux 8 and
   Ubuntu 24.04 unseen (`docs/lwjgl.md` > Linux); real desktops (Wayland, audio servers, drivers, the mouse's feel)
   need outside testers. The first release is Windows only; macOS and Linux follow it, with volunteers we find
   ourselves (user, 2026-11-01). Next: fullscreen and display scales beyond this PC; controllers through SDL;
   after release, Linux arm64 and macOS (main-thread dispatch, natives through CI); an importable component file and guide for
   standalone users.
4. **Fast.** Rendering (buffers instead of display lists, chunk meshing off the main thread), a faster lighting
   engine, entity culling of our own, memory and GC, server tick. Long render distances at high frame rates. Measured
   against vanilla on fixed seeds.
5. **Fixed** (the Fixed edition; Purist leaves these alone, see CLAUDE.md > Editions). A catalogue of b1.7.3's bugs, fixed one by one; the ones that are part of beta's feel (the Far Lands and
   the like) kept as toggles in a settings screen of our own. The window remembered between launches
   (`window.remember` on by default, user 2026-11-02; built, off in Purist: `docs/lwjgl.md` > The display mod).
6. **Multiplayer.** A matching server package; networking hardened (packet limits, safe handling of what servers
   send); LAN. Started 2026-11-02 (`docs/multiplayer.md`): the survey, the Purist fixes, the package and its test. LAN isn't part of b1.7.3, so not Purist.
7. **Release.** The finished pack (one download, import into Prism or a compatible launcher), our mods on their own,
   on GitHub (studio-quire) and Modrinth (link-only), a page on studioquire.org. A small installer of our own later
   if launchers fall short.
8. **Later:** transformative mods on top, optional and separate.

Release order (user, 2026-11-02): Purist first, on Windows, once 4 and 6's hardening are done (5 isn't part of it);
then Fixed, once 5's catalogue is in good shape; macOS and Linux after, with volunteers.

## Follow-ups from the first client check (2026-11-01)
The user's check passed: Java 21, our OpenAL (sound and music), skin, online-mode join to the test server. What the
log showed, and what became of it (done the same day, checked headlessly by `clientAudit`):
- Our own log: `oldfaithful-hardening` copies stdout and stderr from pre-launch on into
  `.minecraft/logs/purefaithful/latest.log` (time-stamped, the last ten runs kept, 32 MB cap per run, `log.file` in
  its config). A session can read it there.
- The session token: vanilla's "Setting user: <name>, <session>" now prints only the name, and everything printed
  passes a redactor (the `--session` value from the launch arguments, `token:...` and JWT shapes) before it reaches
  the console or the file.
- "Failed to add pack.mcmeta" / "READ_ME_I_AM_VERY_IMPORTANT": files at the top of `resources/` are skipped quietly;
  the game only ever took sounds from subfolders, so nothing else changes.
- Java runtime: an instance can't ask Prism for a minimum patch level, and Prism's automatic download only offers
  Mojang's runtimes (21.0.7, 25.0.1). Written up in `docs/runtime-inventory.md` > Java versions; the log records which
  Java ran.

## From the SDL instance's first client check (2026-11-01)
Worked: start-up, F11 borderless fullscreen and alt-tab, resize and maximise, typing, key names, sound. Audio output
switching skipped (one output). Found, and fixed the same day (`docs/lwjgl.md`); the user's re-check is on the board:
- The window title read "Minecraft Minecraft Beta 1.7.3": that is b1.7.3's own Display title, never seen on LWJGL 2
  because the display sat in the launcher's frame ("Minecraft"). The display mod now gives the window the frame's
  title.
- The mouse was faster than vanilla at the same sensitivity: LWJGL 2 on Windows read the desktop pointer (pointer
  speed and "Enhance pointer precision" acceleration, on here), we read raw input. Now the system's settings apply by
  default, as SDL's relative system scale; raw is a setting (`docs/lwjgl.md` > Mouse has the measurement and the
  reasoning for Purist).
- Paste didn't work: Ctrl+V's character (22) wasn't delivered while text input was on. Control characters now come as
  WM_CHAR gave them, in text fields too, and paste reads SDL's clipboard instead of AWT's.
- Vanilla, both instances: the sky turns grey with the inventory open (for the Fixed edition's list). Chat (T) is
  multiplayer-only in beta's own code.
- Since then, `:harness:clientStartupTest` starts the real game here unseen (a private desktop) on every change worth
  it: main menu, paste, a small world, a clean stop.
