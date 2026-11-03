# Releases

Claude Opus 5.5, 2026-11-02. How a release of each edition is built, what's in it and why, the version scheme, the
licence obligations and how each is met, and the steps to publish. Three editions ship: **Pure Faithful** (public
since 0.1.0), **Old Faithful**, which is Pure Faithful plus its own mods (public since 0.1.0, 2026-11-02, on Pure
Faithful 0.1.2), and **Still Faithful**, which is Old Faithful plus its own mod (public since 0.1.0, 2026-11-03, on
Old Faithful 0.1.2 and Pure Faithful 0.1.3). Windows only for now (roadmap phase 7).

## Names

Players see **Pure Faithful** (user, 2026-11-02): everything the Pure edition is made of is Pure Faithful's publicly,
and Old Faithful (the Fixed edition) is Pure Faithful plus its own mods. So public names say purefaithful: the pack
(`PureFaithful-<v>.zip`, the instance it imports as), mod ids and jars (`purefaithful-saves` and so on, from the
project folder's name in `ModPlugin`), the config files (`config/purefaithful-*.properties`), the log
(`logs/purefaithful/`, in Old Faithful too), log line prefixes, the title line, the component's coordinates
(`org.studioquire.purefaithful:purefaithful-lwjgl`), release asset names, the repository (`studio-quire/pure-faithful`)
and the page (`studioquire.org/pure-faithful/`). Internal names stay `oldfaithful`: the project folder and private
repository, Gradle projects and tasks, Java packages, mixin config names and handler prefixes, system properties, the
harness's own mod and lines, and our natives' Maven coordinates (`org.studioquire.oldfaithful.natives`, versions
`+oldfaithful.N`; they show only in the pack's `libraries/` file names and Prism's library cache). The words come from
`release_name`, `release_file_name` and `release_tagline` in `gradle.properties`.

Old Faithful's own mods (`oldfaithful-fixes`, `-lighting`, `-options`) are Old Faithful's publicly, so they keep
`oldfaithful-*`: a mod project whose `gradle.properties` sets `oldfaithful_edition=fixed` gets that public id and jar
name from `ModPlugin`, the green seal (`prism/release/icons/final/oldfaithful-128.png`) for its icon, Old Faithful's
version and its links. Their config files and log prefixes say `oldfaithful-*` too. The edition's own words are the
`fixed_release_*` properties: the pack `OldFaithful-<v>.zip`, the server package `OldFaithful-server-<v>.zip`, the
repository `studio-quire/old-faithful`, the page `studioquire.org/old-faithful/`.

Still Faithful's mod (`oldfaithful-polish`, `oldfaithful_edition=still`) is `stillfaithful-polish` publicly, with the
dusk violet seal; its config file and log prefix say `stillfaithful-polish`. Its words (`still_*`) and version
(`still_edition_version`) are in `oldfaithful-polish/gradle.properties`, not the root's, so they stay out of the other
editions' snapshots with the folder: the pack `StillFaithful-<v>.zip`, the server package `StillFaithful-server-<v>.zip`,
the repository `studio-quire/still-faithful`, the page `studioquire.org/still-faithful/`.

## Building one

    ./gw release                 # Pure Faithful: everything into local/release/<version>/, with SHA256SUMS.txt
    ./gw releaseFixed            # Old Faithful: into local/release/old-faithful-<version>/
    ./gw releaseStill            # Still Faithful: into local/release/still-faithful-<version>/
    python tools/publish.py --check [--target old-faithful|still-faithful]

Neither publishes anything. GitHub rewrites `+` in asset names, so assets have none. Pure Faithful's release:

| File | What it is |
|---|---|
| `PureFaithful-<v>.zip` | The pack: a Prism instance to import (below) |
| `purefaithful-{hardening,saves,speed,display}-<v>.jar` | The mods on their own (Modrinth, existing instances) |
| `org.lwjgl.json` | Our LWJGL component for an existing instance, with each library's URL on this release, SHA-1 and size |
| `purefaithful-lwjgl-<v>.jar`, `purefaithful-lwjgl3-…-natives-windows.jar`, `purefaithful-sdl3-…-natives-windows.jar`, `purefaithful-openal-soft-…-natives-windows-lwjgl3.jar` | The libraries `org.lwjgl.json` names; must be on the same release |
| `PureFaithful-server-<v>.zip` | The server package (`docs/multiplayer.md`), with its notices and sources |
| `PureFaithful-<v>-third-party-sources.zip` | The sources the licences ask for, beside the component's separate downloads |
| `SHA256SUMS.txt` | For the release notes |

Old Faithful's:

| File | What it is |
|---|---|
| `OldFaithful-<v>.zip` | The pack: Pure Faithful's pack with Old Faithful's three mods added, its name, seal and README |
| `oldfaithful-{fixes,lighting,options}-<v>.jar` | Old Faithful's own mods on their own |
| `OldFaithful-server-<v>.zip` | The server package with the fixes and lighting mods added, and its own README |
| `SHA256SUMS.txt` | For the release notes |

Still Faithful's:

| File | What it is |
|---|---|
| `StillFaithful-<v>.zip` | The pack: Old Faithful's pack with Still Faithful's mod added, its name, seal and README |
| `stillfaithful-polish-<v>.jar` | Still Faithful's mod on its own |
| `StillFaithful-server-<v>.zip` | The server package with the polish mod added, and its own README |
| `SHA256SUMS.txt` | For the release notes |

Old Faithful's and Still Faithful's releases have no component file, natives or separate sources zip: Pure Faithful's mods and component
on their own are on the Pure Faithful release each names (below), Old Faithful's mods on the Old Faithful release
Still Faithful's names, and each pack and server package carries its own sources.

Every zip entry has one fixed timestamp and a fixed order, so a rebuild from the same commit and mirror gives the same
bytes (checked for both editions: two runs, identical `SHA256SUMS.txt`; Pure Faithful's 0.1.1 rebuilt byte for byte
after the release script learned editions). Text files go in as checked out, so the bytes also depend on line
endings: build from an LF checkout (this tree's working files are mostly LF, but edits leave some CRLF; a fresh
checkout on Windows is CRLF unless told otherwise). 0.1.1 was cut in a detached worktree checked out with
`git -c core.autocrlf=false -c core.eol=lf`, `local/` folders junctioned in, and a clone of the public snapshot
rebuilt it byte for byte.

**A release folder never changes silently.** It's the record of what was published (and what Old Faithful's check
compares against), so `release` and `releaseFixed` stop before writing into a folder whose `SHA256SUMS.txt` this
build would change (a file changed, added or removed), naming the files (`releaseStill` too), unless `-Prelease.overwrite` says the folder
is an unpublished build to replace. Changed code under a released version means a new version. Test builds
(`-Prelease.test`) have their own folder and aren't guarded.

`gradle/release.gradle` describes each edition once (`releaseEditions`: names, version, icon, mods, server mods,
README and notices header) and builds both from the same templates; Old Faithful's own texts are in
`release/old-faithful/`. A project marked `oldfaithful_edition=fixed` that the script's `fixedMods` doesn't list stops
the build, so a new Old Faithful mod is placed in the pack and server package on purpose. Still Faithful's edition is
added to `releaseEditions` by `gradle/still-faithful.gradle` (its texts in `release/still-faithful/`), with the same
rule for its `stillMods`.

## The pack

`prism/release/` is its template; `gradle/release.gradle` fills it. What differs from the dev instances, and why:

- **Name and icon.** Prism names an imported instance after the zip's file name (`InstanceImportTask` takes
  `completeBaseName`), not `instance.cfg`'s `name`, so the file name is what players see first:
  `<release_file_name>-<version>.zip`. The icon, the β seal (`prism/release/icons/final/`, drawn by `tools/icon_candidates.py`; the user's pick), is
  installed by Prism from the instance root because `iconKey` names it. `instance.cfg`'s `notes` (quoted, since
  Prism reads it with QSettings, which would split a comma) shows in Prism's Notes page.
- **Inside, not fetched.** Every library is in the zip with Prism's `local` hint: one download, nothing fetched but
  Minecraft and its sounds from Mojang (and Java, if the player lets Prism get it), no dependency on our hosting.
  Prism doesn't hash-check local files, so the zip's SHA-256 in the release notes is the check.
- **Windows only.** Only the Windows natives; the dev instance's Linux ones are left out until a Linux release.
- **No instance JVM arguments.** The dev instance overrides the player's JVM arguments to pass
  `--enable-native-access=ALL-UNNAMED`; the pack's `net.minecraft` component adds it with `+jvmArgs` instead, so
  the player's own settings stay. Java 21 and 25 accept it; the component already limits Java to those.
- **Readable by players.** `README.txt` (what it is and isn't, where worlds, settings and the log are), and the
  licence material below.
- Minecraft's jar is always Mojang's, by URL and SHA-1, as in the dev instances. A .mrpack (Modrinth's pack format)
  can't express our own `net.minecraft` and `org.lwjgl` components, so the pack is a Prism zip only.
- **Old Faithful's** is the same template with its own name, seal (`iconKey=oldfaithful`), notes, README
  (`release/old-faithful/pack-README.txt`), notices header and licence folder (`licenses/old-faithful/`). The LWJGL
  component in it keeps Pure Faithful's name and version. No config files are shipped: each mod writes its own
  defaults, and Old Faithful's window defaults (remembered, maximised the first time) come from `oldfaithful-options`
  asking the display mod for them (`docs/options.md`), so standalone installs get them too.
- **Tested unseen**: `./gw :harness:clientStartupTestPack` and `clientStartupTestPackFixed` unpack each release pack
  and run the start-up test on what's inside it (its libraries, natives and mods, with the self-test mod beside
  them); Old Faithful's runs its fix checks in the world and its first-start checks too. Prism itself and the
  instance files aren't run (below, Not checked yet).

## On their own: mods and the LWJGL component

- The mods are the same jars as in the packs. Each carries the MIT licence (`LICENSE_<id>`), the icon, and contact
  links in `fabric.mod.json` (Old Faithful's point to its own repository and page).
- **The component, one step** (README.md has the player's wording): with Prism closed, put `org.lwjgl.json` into the
  instance's `patches/` folder, replacing what's there. Prism prefers a local patch file over its metadata for the
  same uid (`ComponentUpdateTask` `loadComponent`), downloads each library from its URL and checks its SHA-1.
  Prism's "Import Components" button doesn't serve: it refuses when `patches/org.lwjgl.json` exists, as it does in
  Babric's instance. Not tried in a real Prism yet: whether Prism rewrites `mmc-pack.json`'s old LWJGL version
  cleanly, and the downloads from GitHub's release URLs (redirects to its CDN).
- The component file adds no JVM arguments: a Babric instance may run Java 8, which refuses
  `--enable-native-access`. The component's classes are all Java 8 bytecode, and on Temurin 8u504 its core, SDL3 and
  OpenAL Soft load and start (a throwaway check, 2026-11-02); the game on it under Java 8 isn't tested, so the guide
  says Java 21.
- **Old Faithful's mods need none of Pure Faithful's**: no code or `depends` between them, each works alone in any
  Babric instance. Two soft links: `oldfaithful-options` asks the display mod for its window defaults (ignored
  without it), and `oldfaithful-fixes` names the edition on the title line through the hardening mod (no line
  without it). So a standalone player takes Old Faithful's jars from its release and, for the whole edition, Pure
  Faithful's mods and component from the Pure Faithful release it names (Old Faithful's README says so); on Modrinth,
  no required dependencies, `oldfaithful-options` with an optional one on Pure Faithful Display.

## Versions

- **Each edition has its own number.** Pure Faithful's is `oldfaithful_version` in `gradle.properties` (the property
  keeps the project's internal name), carried by its mods, the component (`<v>+lwjgl.3.4.3`), its pack and server
  package. Old Faithful's is `fixed_edition_version`, carried by its own mods, its pack and server package; it
  started at 0.1.0. Semantic versioning in spirit for both: patch for fixes, minor for anything a player would
  notice, major when worlds, configs or setup stop carrying over. Pre-releases get a suffix (`0.2.0-beta.1`).
  Why not one shared number: Old Faithful will change far more often than Pure Faithful (the bug catalogue), and a
  shared number would put Pure Faithful releases with nothing in them on GitHub and Modrinth each time.
- **Old Faithful ships on a released Pure Faithful.** Its pack carries Pure Faithful's mods and component as some Pure
  Faithful release has them, byte for byte, and its README, notices and changelog name that version. `releaseFixed`
  checks this (`releaseFixedPureCheck`): the Pure Faithful jars it builds against `local/release/<pure
  version>/SHA256SUMS.txt`; if they differ, it stops, since Pure Faithful has changed since its release and needs a
  new version, released together with this Old Faithful (build both from the same commit). If that folder doesn't
  exist yet, it says so and goes on: build Pure Faithful's from the same commit. `-Prelease.test` builds anyway, with
  a warning, into `local/release/old-faithful-<version>-test/`, for play-testing only.
- **Still Faithful ships on a released Old Faithful and Pure Faithful** the same way: `releaseStill` checks
  (`releaseStillPartsCheck`) Pure Faithful's jars and component against `local/release/<pure version>/` and Old
  Faithful's three against `local/release/old-faithful-<fixed version>/`, and stops if either differs or (unlike Old
  Faithful's check) isn't there. Its own number, `still_edition_version`, started at 0.1.0. So a change to an Old
  Faithful mod (0.1.2 was one: the options mod's partly-live settings, for Still Faithful's Dusk) means an Old
  Faithful release with the Still Faithful that needs it.
- **The component must change version whenever its bytes do**, which Pure Faithful's number guarantees (and the
  check above carries into Old Faithful): Prism keeps downloaded libraries in a shared cache by Maven path and never
  re-checks them, so a changed jar under an old version would never reach players.
- Our natives carry their own build revision (`3.4.18+oldfaithful.1`); a rebuild that changes their bytes bumps it
  (the verification metadata fails otherwise).
- Git tags `v<version>` on each public repository; GitHub release `v<version>`; Modrinth versions `<version>` with
  game version b1.7.3 and loader babric. Each edition's changelog gets an entry per release, in players' terms
  (`CHANGELOG.md` Pure Faithful's, `release/old-faithful/CHANGELOG.md` Old Faithful's and
  `release/still-faithful/CHANGELOG.md` Still Faithful's, each becoming its repository's `CHANGELOG.md`); the release notes are that entry plus `SHA256SUMS.txt`.
- **In the game**: the title screen shows "Pure Faithful <version>" under beta's own "Minecraft Beta 1.7.3", in the
  same grey (the hardening mod's `Branding`; every Pure Faithful setup has that mod). An edition built on it names
  itself there instead: a mod with `"custom": {"purefaithful-hardening:branding": "<name>"}` in its `fabric.mod.json`
  makes the line "<name> <that mod's version>"; `oldfaithful-fixes` says "Old Faithful". The polish mod rewrites the
  line to "Still Faithful <its version>" (`BrandingMixin`), since the first edition mod by id wins there. The client's log starts with
  the mods and their versions. The start-up tests read the title screen's pixels, save the corner
  (`title-corner.png`) and check the line's text.

## Licences: what we ship and what each asks

Our own code is MIT. Everything else, by artifact (each pack's `THIRD-PARTY-NOTICES.txt` is assembled from its
header and `release/notices/`; licence texts in `release/licenses/` plus each jar's own). Old Faithful's pack and
server package contain the same third-party software as Pure Faithful's, so the same notices, texts and sources:

| What | Licence | What it asks of us | How it's met |
|---|---|---|---|
| Fabric Loader 0.20.1 | Apache-2.0 | Licence copy; keep NOTICE files | Licence in `licenses/`; the loader has no NOTICE |
| inside it: tiny-remapper 0.15.0 | LGPL-3.0-or-later | Prominent notice; LGPL and GPL texts; the Library's source, and the application's in a form that allows relinking (§4d) | Notice; both texts; tiny-remapper's and the loader's sources in `sources/` of the pack and server zips |
| inside it: SAT4J 2.3.6 | EPL-1.0 (or LGPL-2.1+) | Licence copy; say where the source is | EPL text, the loader's `about-sat4j.html`, sources in `sources/`; MiniSat's MIT notice |
| inside it: mapping-io, class-tweaker, Gson's reader | Apache-2.0 | Licence copy | As the loader |
| inside it: MixinExtras 0.5.5 (+ Gson 2.11.0, Commons Lang 3.3.2 parts, ANTLR 4.13.1, java-semver 0.10.2) | MIT, Apache-2.0, BSD-3, MIT | Notices; Commons Lang's NOTICE | All texts; the NOTICE in `licenses/common/` |
| Mixin 0.8.7 (+ Guava 21, Gson 2.2.4) | MIT, Apache-2.0 | Notices | Its licence file; Apache text |
| ASM 9.10.1 | BSD-3-Clause | Copyright and conditions in the documentation | ASM's licence text (not in its jars) |
| Babric intermediary | CC0 | Nothing | Credited anyway |
| LWJGL 3.4.3 classes, LWJGL 2 classes | BSD-3-Clause | Notices | In the component jar's `META-INF/licenses/` and the pack's `licenses/` |
| LWJGL 2's GLU tessellator (from JOGL, from SGI's OpenGL Sample Implementation) | BSD (Sun) + SGI Free Software License B 1.1 | FreeB 1.1 §3.1: its Notice and a copy of the licence in the documentation | The JOGL notice file, with SGI's Notice, and FreeB 1.1's text, in the jar and `licenses/` |
| LWJGL 3 bindings' sources' notices (Khronos, SDL, libffi, liburing) | MIT-style, zlib | Notices | In the jar and `licenses/` |
| `lwjgl.dll`, `lwjgl_opengl.dll` (+ libffi) | BSD-3, MIT | Notices | In the natives jar and `licenses/` |
| `SDL3.dll` (+ HIDAPI, yuv2rgb) | zlib; HIDAPI's BSD option; BSD-3 | zlib asks nothing of binaries; the others notices | In the natives jar and `licenses/` |
| `OpenAL.dll`, OpenAL Soft 1.25.2 (+ pffft, {fmt}, BSD parts) | LGPL-2.0-or-later (its COPYING is the Library GPL 2) | §6: prominent notice and licence; the Library's complete source (with the scripts used to build it) accompanying it, or from the same place; replaceable by the user | Notice; COPYING; the source archive and `natives/build.py` (as `build-natives.py`) in the pack's `sources/`, and on the release page beside the separate natives jar; the DLL is a separate file the game loads at run time, unmodified |
| MSVC's static runtime in our DLLs | Microsoft's | Nothing for statically linked runtime code | |

Mojang's game, assets and server are never shipped. Every player-facing text carries "Not an official Minecraft
product. Not approved by or associated with Mojang or Microsoft."

Doubts, judged rather than asked of a lawyer: whether tiny-remapper's sources plus the loader's (with the loader's
public build at its tag) are everything LGPL-3 §4(d)(0) wants for a shaded jar (we think yes, and it's Fabric's own
distribution model); FreeB 1.1 is honoured as written, not leaning on SGI's 2008 FreeB 2.0.

## The public repositories

Each edition's public repository (`studio-quire/pure-faithful`, `studio-quire/old-faithful`, `studio-quire/still-faithful`)
is a snapshot of the project's working repository, made for each release by a tool that checks what goes
public; its history starts at the edition's first release.

## Publishing, when the user says go

Pure Faithful:

1. `./gw build` and the harness's tests (`docs/building.md`), `./gw release`; then `python tools/publish.py --check`
   on the same commit, and `python tools/publish.py`.
2. Push `../pure-faithful-public` to `studio-quire/pure-faithful`, tag `v<version>`.
3. A GitHub release `v<version>` with every file in `local/release/<version>/` (names unchanged: `org.lwjgl.json`'s
   URLs point at them), notes from `CHANGELOG.md` and `SHA256SUMS.txt`.
4. Check the standalone component in a Prism instance that downloads from the release (the URLs only work now).
5. Modrinth: `python tools/modrinth.py release <version>` (below).
6. studioquire.org: the page, the card, `releases.json` (quire-site's CLAUDE.md says how).

Old Faithful (0.1.0 went out this way with Pure Faithful 0.1.2, 2026-11-02, from one commit):

1. If Pure Faithful has changed since its last release, Old Faithful can't ship on it (`releaseFixedPureCheck`):
   bump `oldfaithful_version`, give `CHANGELOG.md` an entry, and release Pure Faithful first from the same commit as
   above. Date Old Faithful's entry in `release/old-faithful/CHANGELOG.md` and check it names that Pure Faithful
   version, as its README, notices and release notes will.
2. Bring `release/old-faithful/README.md` and the changelog up to date with the fixes (the bug hunt adds them).
3. `./gw build`, the harness's tests including `clientStartupTestFixed`, `fixesMultiplayerTest` and
   `clientStartupTestPackFixed`, `./gw release releaseFixed` (no `-Prelease.test`: the Pure Faithful check must
   pass); `python tools/publish.py --target old-faithful --check`, then without `--check`.
4. Push `../old-faithful-public` to `studio-quire/old-faithful` (remote `public`), tag `v<version>`.
5. A GitHub pre-release `v<version>` with every file in `local/release/old-faithful-<version>/`, notes from its
   changelog entry, a line naming the Pure Faithful release whose mods and component it carries (with a link), and
   `SHA256SUMS.txt`.
6. Modrinth: `python tools/modrinth.py release <version> --edition old-faithful`.
7. studioquire.org: `/get/old-faithful` follows the newest release by itself; the page's text when the README's
   changes. Its cover and link preview are a real frame, lava in a cave lit by the lighting mod, taken unseen by
   `./gw :harness:sceneShot`.

Still Faithful (0.1.0 went out this way with Old Faithful 0.1.2, 2026-11-03, from one commit):

1. If Old Faithful's or Pure Faithful's mods changed since their last releases, release them first from the same
   commit (above); `releaseStillPartsCheck` stops otherwise. Date the entry in `release/still-faithful/CHANGELOG.md`
   and check it names those versions.
2. `./gw build`, the harness's tests including `clientStartupTestStill` and `clientStartupTestPackStill`, `./gw
   release releaseFixed releaseStill` (Pure Faithful's and Old Faithful's released folders are rebuilt under the
   guard, so a difference stops it); `python tools/publish.py --target still-faithful --check`, then without.
3. Push `../still-faithful-public` to `studio-quire/still-faithful` (remote `public`), tag `v<version>`.
4. A GitHub pre-release `v<version>` with every file in `local/release/still-faithful-<version>/`, notes from its
   changelog entry, the Old Faithful and Pure Faithful releases it carries (with links), and `SHA256SUMS.txt`.
5. Modrinth: `python tools/modrinth.py release <version> --edition still-faithful`.
6. studioquire.org: `/get/still-faithful` follows the newest release. Its cover and link preview are a real frame of
   dusk with smooth dusk on, taken unseen by `./gw :harness:sceneShotStill -PsceneViews=...` (SceneShot's view mode).

## Modrinth

Since 2026-11-02 the four Pure Faithful mods are Modrinth projects in the **Studio Quire** organisation
(modrinth.com/organization/studio-quire; its icon is the site's mark, rendered from quire-site's `mark.svg`), which
the user asked the studio to manage: `pure-faithful-hardening`, `-saves`, `-speed`, `-display`, texts from
`release/drafts/modrinth.md`, the β seal, MIT, source and issues on GitHub. Old Faithful's three were made the
same way on 2026-11-02: `old-faithful-fixes`, `-lighting`, `-options`, texts in `release/drafts/old-faithful-modrinth.md`,
the green seal, source and issues on `studio-quire/old-faithful`. Still Faithful's one, `still-faithful-polish`, on
2026-11-03: texts in `release/drafts/still-faithful-modrinth.md`, the violet seal, `studio-quire/still-faithful`. The token (the user's account, Shoalstone) is in
`local/modrinth-token.txt`, git-ignored; never print it.

- A release: `python tools/modrinth.py release <version> [--edition old-faithful] [--type beta|release]` uploads each
  jar from the edition's release folder and checks it against `SHA256SUMS.txt` (`verify` alone re-checks).
  Upload every release, even while the projects are in review (0.1.1 was), so approval doesn't serve an old one.
- b1.7.3 takes the `babric` loader only: Modrinth refuses `fabric` for game versions before 18w43b (older uploads
  elsewhere show both).
- Sides are set per version as v3's `environment` (`client_or_server` for hardening, saves and lighting,
  `client_or_server_prefers_both` for fixes and polish, `client_only` for speed, display and options); v2's
  `client_side`/`server_side` on the project are ignored and read back from it.
- No required dependencies between any of the mods: each works alone. The display mod's real requirement, our LWJGL
  component, isn't something Modrinth can install; its description says how. `oldfaithful-options` versions list
  Pure Faithful Display as optional.

## Not checked yet

- The packs imported into a real Prism: the import name from the file name, the icon, the notes, `+jvmArgs` reaching
  the game, Java 21 chosen, a first launch downloading the game and its sounds. The template is the dev instance's,
  which the user has imported, plus those four, each read from Prism 11.1.1's source, not tried. What's inside each
  pack starts and plays unseen (`clientStartupTestPack`, `-Fixed`).
- The standalone component in a real Babric instance: replacing `patches/org.lwjgl.json` and the display mod
  working there. (Its download URLs resolve on the 0.1.2 release, each library's SHA-1 and size as the file says,
  checked 2026-11-02.)
- The server packages from the release zips on a machine other than this one.
