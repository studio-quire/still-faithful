# Building and testing

Claude Opus 5.5, 2026-11-01.

## Quick reference

`gw` / `gw.cmd` run the Gradle wrapper with the portable JDK in `local/tools/jdk21` if it's there (any JDK 17+ on
`JAVA_HOME` also works). Gradle is kept small: two workers, no parallel projects, 1 GB heap. `./gw --stop` stops every Gradle daemon on the PC, other worktrees' running tests included, so check nobody else is running one. Run it when
done; the CPU is shared.

| Command | What it does |
|---|---|
| `./gw build` | Compiles the mods, runs unit tests, remaps. Installable jars: `<mod>/build/libs/<mod>-<version>.jar` (the `-dev` jar is the named one, not for players). |
| `./gw :harness:serverTest` | Headless dedicated server with our mods: boots, generates and saves a world, refuses an online-mode login through the hardened check, stops; fails on any error from our mixins. About 20 s. |
| `./gw :harness:nativesTest` | Loads our LWJGL and OpenAL builds as a launcher does, with no visible window: display modes, an offscreen GL context, OpenAL device listing and silent mixing (`docs/natives.md`). A few seconds. |
| `./gw :harness:clientAudit` | Starts the client through Fabric with no window, applies every client mixin, and exercises sounds, skins, capes and the session join against Mojang, launched with a made-up session that must not appear in the console or in `logs/purefaithful/latest.log`; then exits. The first run downloads all sounds (44 MB) into `local/cache/selftest-sounds`. |
| `-Poldfaithful.gameJava=<java>` | Runs any of those on another JRE (an absolute path), e.g. Prism's `jre-legacy` (8u51). All three pass on 8u51, 21 and 25. |
| `-Poldfaithful.upstream` | Resolves from the original repositories instead of our mirror, for adding or updating a dependency (`docs/mirror.md`). |
| `./gw decompileMinecraft` | Vineflower source of the merged named jar in `local/decomp/` for reading mixin targets. Never commit it. |
| `./gw prismDevInstance` | `local/prism/OldFaithful-dev.zip`, a Prism instance to import for client checks (below). |
| `./gw :harness:linkCheck` | Every `org.lwjgl` reference in b1.7.3, our mods, the popular mods pinned in `harness/linkcheck-mods.json` and our LWJGL jar's own classes resolves against our LWJGL component (`docs/lwjgl.md`). Report: `harness/build/reports/linkcheck.txt`. Seconds; the first run fetches the mods (hash-checked) into `local/cache/linkcheck-mods`. |
| `./gw :oldfaithful-speed:test` | `ChunkMap` against HashMap through 400,000 random operations on chunk-shaped keys, and `TickSet` against HashSet with the game's own `BlockEvent`s. Seconds. The speed mod itself joins `clientAudit`, `clientAuditSdl`, the start-up tests and `serverTest`; the benchmark's `speed` configuration measures it, and `tools/gen_bench.py` the server (`docs/speed.md`). |
| `./gw :oldfaithful-fixes:test`, `./gw :harness:clientStartupTestFixed`, `./gw :oldfaithful-fixes:serverTicksTest` | The Fixed edition's fix mod: its unit test, the start-up test with it, then its fixes set up and checked in the running world, and the real server keeping a repeater clock running across a restart (#42) (`docs/fixes.md`). Seconds; about 25 s; about 45 s. The mod also joins `serverTest` and the client audits. |
| `./gw :oldfaithful-fixes:squidWatch [-Psquids.ticks=12000]` | #76 and #77 measured: real squid on the real server with the fix mod and without it, side by side, their height over time and how they behave at the surface; `local/run/squid-watch/` (`docs/fixes.md` > Tests). As long as its ticks at 20 a second (10 minutes by default); a server each and nothing shown, so no `gwl.sh` lock. |
| `./gw :harness:fixesMultiplayerTest` | Old Faithful's multiplayer fixes on the server package with and without the fix mod, each joined by the real client (hidden) with and without it, plus raw-packet clients for the server side; without a fix the check expects beta's bug (`docs/fixes.md` > Tests). About 4.5 minutes. |
| `./gw :oldfaithful-lighting:serverLightTest`, `:oldfaithful-lighting:lightCheck -Pworld=<folder>` | Old Faithful's lighting (`docs/lighting.md`): the real server with and without the mod through a scenario (also at a single player client's lighting pace, checked before the queue is done, and with `lava.lightGenerated` off), every saved chunk's light against b1.7.3's rule computed from scratch; the checker alone on any world. About 2 minutes. The mod also joins `serverTest`, the client audits and `clientStartupTestFixed`. |
| `./gw :oldfaithful-options:test` | Old Faithful's options mod (`docs/options.md`): its unit tests (the FOV, and the settings screen's config editing and registry). It joins the client audits and `clientStartupTestFixed`, which with it checks Quit Game, the FOV in the world and the settings screen (clicks, files, what applies at once, multiplayer, layouts at three GUI scales in three window sizes, pictures in `settings-shots/`), and adds a first start (maximised, GUI Normal, stopped by Quit Game). |
| `./gw :harness:clientStartupTestLibrary` | The settings screen's checks alone on Still Faithful with the library's mods in their folder, and pictures of every level of the menus (`docs/options.md` > Tests). About half a minute. |
| `./gw :harness:librarySelftest -Plibrary.mods=a,b` | Library mods' own tests (`docs/library-authoring.md` > Testing): single player and a real server with a hidden client joined, each fresh and after a restart. Fails under the mod's name, and on a test that reported no checks. `-Plibrary.singlePlayerOnly` or `-Plibrary.serverOnly` for one half. |
| `./gw :harness:libraryRulesTest` | Faithful Core's base layer and World Rules end to end: the server package with them against raw clients (vanilla, with the core, hostile), the rules' scenes on the real server (console command `ruletest`), the hidden client joined to it, a hidden client without the library on a server with a long day, a server with a must-match mod, and the hidden client in single player launched from `mods-library/selection.txt`. About 3 minutes. |
| `./gw :harness:libraryHooksTest` | Every Faithful Core hook and attachment on the real server and the hidden client, with the test-only `hook-check` mod: raw clients, a restart, an order salt, single player reopened, and all of it beside Still Faithful. Its own run dir, `local/run/hooks-test`. About 3 minutes. |
| `./gw :harness:libraryStatic [-Plibrary.mods=a,b]`, `:harness:libraryStaticBadTest` | The conflict harness's first stage, no game launched: what the library's jars hold, what common code reaches, direct chunk reads, hand-rolled stack comparisons, every mixin target (report in `harness/build/reports/library-static.txt`), two mods on one method (exceptions: `harness/library-accepted.txt`). The second runs the rules over planted mistakes. Seconds. |
| `./gw :harness:libraryTest [-Plibrary.mods=a,b]` | The conflict harness (`docs/library-harness.md`): `libraryStatic`, then registry, combinations, salts, round-trips, multiplayer and monkey, stopping at the first stage that fails. Each failure names the stage, the mod and the rule; all of them in `report.txt` in the run folder (`local/run/lib-harness-test`, or `-Plibrary.runDir`). `-Plibrary.stages=registry` for some stages (that one about 20 s), `-Plibrary.keepGoing`, `-Plibrary.seed=N`. `-Plibrary.mods` takes any folder in `library/`, test mods too. About 15 minutes for a few mods at three games at a time, longer at the default of one (`-Plibrary.parallel=N`); the editions and the monkey are most of it. |
| `./gw :harness:libraryBadTest [-Plibrary.stages=monkey]` | The harness on planted mistakes (`library/fixture-bad-*`): every rule must fire and name the mod. About 7 minutes, most of it the monkey. |
| `./gw :harness:coreInertTest` | Faithful Core with no content: chunks byte for byte and pictures pixel for pixel against the same without it, vanilla peers both ways (`docs/library-harness.md`, stage 8). |
| `./gw :harness:libraryContentTest`, `libraryContentTestStill` | Faithful Core's content layer end to end: the real server and hidden client with a test mod, a shifted numbering, a removed mod, the real unmodified server. About a minute; pictures in `local/run/library-content-test/single-player/shots/`. |
| `./gw :harness:libraryApiTest` | The content APIs, `library/api-check` end to end: worldgen on a fixed seed with all, none and one of its features, compared cell by cell; the real server with the hidden client joined, each block entity path, recipes, furnace, tools, storages, entities, a screen, a sound; the same with `oldfaithful-fixes` on the server, after a restart; single player, with pictures in `local/run/api-test/single-player/shots/`. Tag names are checked against `library/tags.txt`. About 90 s. |
| `./gw :harness:buriedTowersTest [-Ptowers.seeds=a,b] [-Ptowers.size=48]` | Buried Towers on really generated worlds (`library/tower-check` drives the real server): each seed's square of chunks in rows, rings, shuffled across a restart, backwards with every edition's mods and through a crash, every finished cell of every tower against its plan in each; an older world made without the mod, a tower crossing it staying out and its chunks unchanged; a lost chunk of a tower back as part of it, with a player's block beside it left alone; every iron door with a lever shut across its doorway after the restart, and its lever opening it; veins with the mod against without. `-Ptowers.mode=spacing` writes where each seed's sites would stand over 48 squares each way, at the margin in use and the first one, for `tools/tower_spacing.py <run>`'s nearest-neighbour table. `-Ptowers.style=complex` makes the worlds' towers complex, in any mode. `-Ptowers.mode=view -Ptowers.count=N` instead dumps N towers a seed (or with `-Ptowers.at=x,z;x,z` the towers round those places), one of each climate first, into `<run>/<seed>/towers/` for `tools/tower_view.py <dump> <out>` (a plan per floor, sections, a sheet) and `tools/tower_route.py <dump>` (walks it by a player's rules from outside the gate). `-Ptowers.run=` for the run folder. |
| `./gw :harness:sceneShotTowers -PsceneViews="x y z yaw pitch time;..." -PsceneSeed=N [-PsceneBright=false] [-PsceneTowers=complex] [-PsceneRun=local/run/<folder>]` | Pictures in a new world with Buried Towers (simple unless told complex; a view `use x y z` clicks the block there instead, a lever say, `dig x y z` takes it out and `put x y z id [data]` puts one in, telling its neighbours, for the views after it, and `probe x y z r` writes the blocks within r of a place into `shots/probe-<n>.txt`; scheduled block ticks don't run in this world, so sand put over air stays, and a crust's collapse can't be pictured here: `WaysTest` checks that), every light level shown as daylight unless told otherwise, into `local/run/scene-shot-towers/shots/` (or the `-PsceneRun` folder's). Each view is shot once the render chunks within 64 blocks that are in view are built (`-PsceneSettleRadius`), 240 ticks at most; `-PsceneSettle=false` holds every view the whole 240, which fills in more of the far land in views from outside. Without the bright light, beta's light queue keeps every view to the whole hold. With `-PspawnWatch="cx cz radius floorY floorH ticks;..."` instead: each floor watched in turn at Normal, the player held unseen in the stone under it, and every monster that appears counted, with each spawner's share and the light round it, into `shots/spawns.txt` (`SpawnWatch`). With `-PcartRide="sx sy sz ex ey ez lx ly lz times;..."` instead (a dump's `cart`: the top brake, the foot's, the lever): each tower's cart run ridden as a player would, peaceful, a cart on the top brake, the player in, the lever pulled, until it stops; passes on the foot's brake with the rider in, unhurt and never off the rails, into `shots/rides.txt` (`CartRide`). Safe at `-Ptest.speed=4`: a ride of 100 blocks takes about 300 ticks. The cart run is off in generation since 2026-11-08 (`docs/buried-towers.md`), so no tower has one to ride until it's back. |
| `./gw releaseLibrary [-Prelease.test]` | The Faithful Library's release in `local/release/faithful-library-<version>/` (`docs/release.md`). |
| `./gw :harness:libraryPackTest` (`libraryPackTestOld`, `libraryPackTestStill`) | The library packs and server packages as they ship, unpacked, launched with their own component's arguments: nothing ticked (the edition's whole start-up test, none of the library's code present, a vanilla login, the Mods menu offering all of it), no list, everything ticked, a server as it comes, with the mods but Water Bottles, with all (vanilla refused, Match This Server's Mods pressed), and with Water Bottles taken out again. `local/run/library-pack-<edition>/`. About 8 minutes each. |
| `python tools/stagehand.py <command>` | Scripts a running game through the Stagehand mod (`docs/stagehand.md`). |
| `tools/gwl.sh <log> --exec pack python tools/pack_start.py <zip> <run dir> <label> [--style simple\|complex] [--world-ticks N]` | A Prism instance zip as built (a test pack before the user imports it) unpacked and started unseen as Prism would, the start-up test driving it into a new world held for the ticks (2400 by default); prints the game folder. While it's held, `tools/stagehand.py --dir <that folder> --world-wait 60 ...` drives it: always with `--dir`, or it finds the user's instance. Needs `:harness:remapJar`. |
| `./gw prismLibraryInstance`, `./gw prismLibraryStagehandInstance`, `./gw libraryServerPackage` | `local/prism/FaithfulLibrary-dev.zip`, Still Faithful's dev instance with the Faithful Library in `mods-library/`; `FaithfulLibrary-dev-stagehand.zip` beside it, the same with Stagehand on, a session's test pack (`docs/stagehand.md`); `local/server-package/FaithfulLibrary-server/`, the server package with it. |
| `./gw :harness:lwjglTest` | Our LWJGL component at run time with no visible window: changed classes load and verify; Display, GL and a shared context on a hidden SDL window; keyboard and mouse against synthetic SDL events; the game's paulscode playing through our AL into OpenAL Soft's wave writer. About 20 s. Takes `-Poldfaithful.gameJava` (passes on 21 and 25). |
| `./gw :harness:clientAuditSdl` | The client audit on our LWJGL component with `oldfaithful-display`, whose mixins apply only there. |
| `./gw :harness:clientStartupTest` | The real client on our LWJGL component, unseen, as the SDL instance starts it: main menu, a paste, a small world created and run, a clean stop (below). About 10 s; Windows; needs `python`. |
| `./gw :harness:lwjglTestLinux`, `:harness:clientStartupTestLinux` | The same two tests on Linux x64, in Docker on AlmaLinux 8 and Ubuntu 24.04 (Xvfb, Mesa's llvmpipe), through `tools/in_linux.py`; any host with Docker, nothing shown. Per image: `lwjglTestLinux-alma8-display`, `clientStartupTestLinux-ubuntu2404` and so on (below). |
| `./gw :harness:clientStartupTestStill [-Pstill.beta]`, `./gw :oldfaithful-saves:serverSaveTest -Pstill.saves` | Still Faithful's mod (`docs/still-faithful.md` > Tests): the start-up test with all three editions' mods and its changes checked in the world, stepped and smooth dusk pixel by pixel against beta's (`-Pstill.beta`: every switch off, beta's behaviour expected; `-Pstill.duskFull`: every lighting mode and the Nether, about 5 minutes), about 1.5 minutes; the saves mod's real-server kill test with its background autosave beside it. |
| `./gw :harness:fencesMultiplayerTest` | Still Faithful's fences (#30) between the server package with and without the polish mod and the hidden client with and without it: walks into a scene the server builds, thin only where both sides have it, never sent back or kicked (`docs/still-faithful.md` > Tests). |
| `./gw releaseStill [-Prelease.test]`, `./gw prismStillInstance` | Still Faithful's release in `local/release/still-faithful-<v>/` (its pack, server package, its mod, SHA256SUMS; on released Pure Faithful and Old Faithful parts, under the release guard; a test build into `-test/`), its public snapshot built and checked (`docs/release.md`), and its dev instance `local/prism/StillFaithful-dev.zip`. |
| `./gw :harness:benchSetup`, `python tools/bench.py` | The speed benchmark: the real game unseen on a fixed script, per-frame timings, reports (`docs/speed.md`). |
| `python tools/gen_bench.py LABEL [--mods none\|towers\|all\|a,b] [--jfr] [--plans RX0,RZ0,N] [--tower-jar PATH]` | World generation on the real server, timed: `library/tower-check` loads a square of chunks with a set of library mods (their jars built first), and each load's time comes back as total, p50, p99 and worst; `--jfr` profiles the server thread over just the generation; `--plans` writes every square's site and a digest of its tower as drawn, to show two builds draw the same towers (`--size 2 --plans -12,-12,24`, and again with `--style complex`, against `library/tower-check/reference/plans-1733-simple.txt` or `-complex`). About 35 s for 72x72 chunks; a game launch, so through `tools/gwl.sh --exec`. |
| `./gw prismSdlInstance` | `local/prism/OldFaithful-sdl-dev.zip`: the dev instance on our LWJGL component (below). |
| `./gw prismFixedInstance` | `local/prism/OldFaithful-fixed-dev.zip`: the SDL dev instance with Old Faithful's own mods (whose `oldfaithful-options` brings its display defaults), for client checks of the Fixed edition. |
| `./gw :harness:multiplayerTest` | The server package started by its own `start.bat`, offline mode, against raw-packet clients, honest and malicious. The server must refuse the malicious ones, keep answering `list`, keep its world whole, and never run out of memory. About a minute. |
| `./gw serverPackage`, `serverPackageZip` | `local/server-package/OldFaithful-server/` (and `.zip`): start scripts that check the server jar's hash, Fabric Loader and its libraries, the hardening, saves and speed mods; the owner adds `minecraft_server.jar`. |
| `./gw release` | A release in `local/release/<version>/` (the pack, the mods, the LWJGL component file, the server package, sources, SHA256SUMS), and the public snapshot built and checked; nothing published (`docs/release.md`). Seconds. |
| `./gw releaseFixed [-Prelease.test]` | Old Faithful's release in `local/release/old-faithful-<version>/` (its pack, server package, its three mods, SHA256SUMS; a test build into `-test/`), and its public snapshot built and checked (`docs/release.md`). Seconds. |
| `./gw :harness:sceneShot [-PsceneSeed=N]` | Pictures for the site and listings: Old Faithful's mods in a hidden 1440x900 window, the new world searched for lava lying in caves near the spawn (lit by the lighting mod), the best six shot from the cave's air with the HUD hidden, into `local/run/scene-shot/shots/` with where each was (`SceneShot`). The spawn moves between runs, so the places do too. About 90 s. |
| `./gw :harness:clientStartupTestPack`, `clientStartupTestPackFixed`, `clientStartupTestPackStill` | The start-up test on what's inside each release pack (its libraries, natives and mods), unseen; Old Faithful's and Still Faithful's with Old Faithful's fix and first-start checks, Still Faithful's title line and settings button. About 30 s, 45 s and 45 s. |
| `./gw :harness:sceneShotStill -PsceneViews="x y z yaw pitch time;..." [-PsceneSeed=N] [-PsceneRun=local/run/<folder>]` | Landscapes with Still Faithful's mods (its dusk) for the site: each view held at its time of day at the Far render distance until the view fills in, then shot, into `local/run/scene-shot-still/shots/` (`SceneShot`'s view mode; x and z a block, y the eye's height; seed 31337 by default). About a minute a view. Each run replaces its folder (`local/run/scene-shot-still/` unless `-PsceneRun` gives it its own), so copy pictures out before another run. Views as `sceneShotTowers` takes them, `put` and `dig` included; a view first clears the weather, so in a snowy biome put blocks after one. |
| `./gw :harness:ledgeHunt -Pledge.mods=plain\|pure\|old\|still -Pledge.runs=A=30000,W=400,S=20000 [-Pledge.label=x] [-Pledge.seed=N] [-Pledge.open]` | The ledge hunt (`LedgeHunt`): the real client's physics on scripted input in built scenes, up to about 280,000 ticks a second, every tick checked for the box in a block, the push-out acting, the eye, the player placed from outside. Families: A approaches, W long wanders, S and Q write-and-read while touching, R and P real rejoins, D straight walks, E and F taps off an edge, U write-and-read and T real rejoins against a 4-high wall past a power of two, V real rejoins against one anywhere, H low steps under roofs, N real terrain at real time (`N=<seconds>`, `-Pledge.view=1`). `-Pledge.only=F:cell:run` replays one run into `trace.txt`; `-Pledge.fixes="switch=value other=value"` sets fixes; `-Pledge.runDigests` adds each run's digest, to find the run that differs. `local/run/ledge-hunt-<label>/hunt/`: `summary.txt` (with the ticks hurt, any cause), `episodes.tsv`, `events.txt`, and `digests.txt` to compare mod sets bit for bit. Families that build at new places each cell (Q, U) get a launch of their own: chunks are never unloaded, so after others the heap fills with queued light and block ticks and the saves crawl. Fails if vsync is on. Not a pass/fail test of the game. |
| `./gw :harness:runServer` | A local online-mode server with our mods on 127.0.0.1:25565, world kept in `local/run/server`. `stop` or kill it to end. |

What game runs cost (measured over 2026-11-06 and 07: about 20 hours of locked runs, most of a library mod job's wall-clock, mostly fixed in-game waits, game time at 20 ticks a second and whole suites run for small things), so the smallest run that answers gets picked:
- `libraryStatic`: seconds, no lock. `librarySelftest -Plibrary.serverOnly`: about a minute, no client, enough for world generation counts and server-side checks. `librarySelftest -Plibrary.singlePlayerOnly`: single player fresh and reopened, no servers; for Blue Flowers 101 s of the full run's 174 (2026-11-07).
- `librarySelftest -Plibrary.mods=<mod>`: 2 to 3 minutes. Six client launches and four servers (single player, reopened, a server joined, restarted, the core-only and the real server), about 10 s each, plus the mod's own test at 20 ticks a second, mostly in the first single player and first server join. Iterate on the half you're looking at, and run the whole once at the end.
- `libraryTest` on every mod: about 25 minutes, or 14 with `-Ptest.speed=4` (2026-11-07, nine mods: selftest 617 s to 288, the monkey 339 to 90, combinations 351 to 246; multiplayer, mostly launches and raw clients, stays at 2). Selftest about 10 (single player and the first join are 80% of it), the monkey 5.5 (its tick budget), combinations 4-6, multiplayer 2, the rest 2. After a failure, rerun only that stage (`-Plibrary.stages=<stage>`, with `-Plibrary.mods`) and the whole run once at the end; `-Plibrary.monkeyTicks=1500` while iterating.
- `sceneShotTowers`: about 20 s plus a second or two a view, as each is shot once it's built near the camera (ten views: 35 s, where the old 240-tick hold took 2 min 34 s; rooms came out pixel for pixel the same, 2026-11-07). Views far apart cost no more than near ones. All the views in one run beat a run per batch; the shots folder is shared, so copy them out, or give the run its own with `-PsceneRun`. `-PspawnWatch` takes its ticks at 20 a second: 3,000 a floor is 2.5 minutes.
- `-Ptest.speed=4` on any hidden-client task (`ClientStartupTask`'s): game time four times as fast (ticks of 13 ms) in each hidden client's single player, and on the library harness's servers (`HarnessServer`, which have the harness probe) with the clients that join them; the server says its pace in the login packet and the client follows, so anywhere else (the real server, servers without the probe, the pack, hooks and rules tests' own servers) both stay at 20 a second. Beta's network writer's 100 ms sleep shrinks with it, so a round trip takes as many ticks as before. A `librarySelftest` of Blue Flowers in single player: 101 s to 28. A spawn watch's ticks go four times as fast (1,500 ticks: 104 s to 36). Opt-in, for iterating; run the whole thing at the game's own pace once at the end, as a test can lean on real time: two did (Stagehand's inbox looks once a second; World Rules' sun allowed for a tick of 50 ms), and now read `oldfaithful.test.tickMs`. A test with a wall-clock wait or a real-time poll does the same. A client that can't keep its 77 ticks a second falls behind the server, which a test of fine timing may notice. Not for pictures: `sceneShotTowers`' settling counts ticks, and its time goes to building chunks anyway.
- Waiting on a run: `gwl.sh` in the background and its completion notice, not sleep loops; the run's own log once at the end. A compile check (`:harness:compileJava`) through `gwl.sh` takes no slot.
- The library tests' logins follow at once: the 5.5 s waits between them went (2026-11-07), as beta's login throttle exempts 127.0.0.1.

Setting up a new machine:
- JDKs from Adoptium, hashes checked against Adoptium's API, unpacked into `local/tools/`: Temurin 21
  (`OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip`, SHA-256 `a4dbaa05…2004f9`) as `jdk21`, the build's own; Temurin
  8u504 (`ea43d46e…b2aa`) as `jdk8`, for building LWJGL; Temurin 25.0.5 (`636bdc13…6c42`) as `jdk25`, for testing.
- `python tools/mirror.py sync` fills the artifact mirror; `python natives/build.py all` (Visual Studio 2022 Build
  Tools, `docs/natives.md`), `python natives/build.py linux-x64` (Docker) and `python tools/mirror.py natives` add our
  native builds. Gradle resolves nothing else.
- Docker (Desktop here, with its dashboard-on-start turned off so starting it shows nothing) for the Linux builds and
  tests: `docker desktop start`.

## The toolchain: our own small plugin

`buildSrc/` holds a Gradle plugin of our own (about 1,000 lines, half of it the test harness) instead of Fabric
Loom plus an add-on. Considered:

- **Babric's Loom add-on**: no licence, so not ours to copy (the brief and the audit).
- **Ornithe's Ploceus** (Apache-2.0): built for Ornithe's Calamus intermediary and feather/nests/signature
  processing, compiled against a Loom `1.18-SNAPSHOT` and Java 25. Using it with Babric's intermediary means fighting
  its design, and a snapshot dependency can't be pinned reproducibly.
- **Loom alone with our own add-on**: Loom is large, churns with every Gradle release, and an add-on has to reach into
  its internals (Babric's does), which break between versions.

For one frozen game version the job is small and fixed, so our own plugin does exactly it, on two well-maintained
libraries, tiny-remapper (LGPL-3.0, build-time only, never shipped) and mapping-io (Apache-2.0):

1. `downloadMinecraftClient` / `downloadMinecraftServer`: HTTPS only, kept only if the SHA-1 matches the pinned hash
   (`gradle.properties`). A correct file already in `local/cache/minecraft` is used as is, so the jars can be dropped
   in by hand.
2. `prepareMinecraft`: client remapped `clientOfficial → intermediary`, server `serverOfficial → intermediary`
   (Babric's split namespaces), merged with `@Environment` marks on side-only classes and members, then remapped
   `intermediary → named` (Biny). Output: `local/cache/minecraft/b1.7.3-merged-{intermediary,named}.jar`.
   Interfaces on one side only get `@EnvironmentInterface` (four classes, e.g. the server-only `SpawnableEntity` on
   `AnimalEntity`), so Fabric strips them in a dev-mode run on the merged jar; without it a dev-mode client crashed
   loading such a class (found 2026-11-03). Production launches never use the merged jar.
3. Each mod (`oldfaithful.mod` plugin) compiles against the named jar plus Fabric Loader, Mixin and MixinExtras
   (compile-only: the loader provides them at runtime), to **Java 8 bytecode**, so the mods also run on old JREs.
   `remapJar` rewrites it to intermediary with tiny-remapper's Mixin extension, which rewrites Mixin annotation
   targets in place: **no refmaps**, so a mixin config must not name one.

Missing compared with Loom, add when needed: IDE run configurations, access wideners, jar-in-jar, remapping other
mods as dependencies (`modImplementation`). Loom stays an option if we ever need its breadth.

## What's pinned, and where

| What | Version | Pinned by |
|---|---|---|
| Gradle | 9.8.2 | `distributionSha256Sum` in the wrapper properties; the wrapper jar matches Gradle's published SHA-256 |
| Every Maven artifact (build, mods, harness) | `gradle.properties` | SHA-256 in `gradle/verification-metadata.xml` (Gradle dependency verification; covers `buildSrc` too). A changed artifact fails the build. Checked against independent sources once: Fabric's published hashes for ASM and Mixin, the audit's earlier copy of the intermediary. |
| Repositories | | Only our mirror, `local/mirror/maven` (`docs/mirror.md`). With `-Poldfaithful.upstream`, the originals: HTTPS, each limited to the groups we expect from it (`exclusiveContent`), and our own group never from them |
| Game client | b1.7.3, from Mojang | SHA-1 `43db9b49…` in `gradle.properties` |
| Game server | b1.7.3 | SHA-1 `2f90dc1c…`. **Mojang no longer hosts it** (every launcher.mojang.com / piston-data / S3 URL is 404), so it comes from Omniarchive's archive; the hash is Mojang's original, confirmed by two independent manifests (Ornithe's and Babric's). Local only, never redistributed. |
| Intermediary | `babric:intermediary-upstream:b1.7.3:v2` (CC0) | dependency verification |
| Names | `net.glasslauncher:biny:b1.7.3+e0778a3:v2` (CC0), the latest release | dependency verification |
| Fabric Loader / Mixin / MixinExtras / ASM | 0.20.1 / 0.17.5+mixin.0.8.7 / 0.5.5 (bundled in the loader) / 9.10.1 | dependency verification |
| LWJGL 2 / OpenAL Soft | Legacy Fabric's 2.9.4+legacyfabric.18 / 1.25.2, built by us | Source commits in `natives/build.py`; the built artifacts by dependency verification (`docs/natives.md`) |
| LWJGL 3 (Java side) | 3.4.3 from Maven Central (`lwjgl`, `-opengl`, `-openal`, `-sdl`) | dependency verification; the jars' PGP signatures checked once against LWJGL's release key (Ioannis Tsakpinis, `72EB0D1C9B1FB0FC`) |
| SDL3, LWJGL 3 natives, libffi | 3.4.18 / 3.4.3 / 3.8.0, built by us | as LWJGL 2 above (`docs/natives.md`) |
| Mods for the link check | `harness/linkcheck-mods.json` | SHA-512 from Modrinth; read only, never run or shipped |
| Mojang's legacy asset index | `pre-1.6`, SHA-1 `3d8e5548…` | hard-coded in `LegacyAssets`; the mod refuses any other |

Updating something: change the version in `gradle.properties`, run
`./gw -Poldfaithful.upstream --write-verification-metadata sha256 build :harness:nativesTest prismDevInstance decompileMinecraft`,
review the diff to the verification file, checking new hashes against a source other than the repository they came
from where one exists, then `python tools/mirror.py sync`.

## Writing mixins

- Read targets in `local/decomp/` (or `javap -p` on the named jar). Biny leaves a few names as `method_123`; avoid
  depending on those where the URL or a parameter carries the same information.
- Handler names start with `oldfaithful$`. Prefer MixinExtras' `@WrapOperation` / `@ModifyExpressionValue` to `@Redirect`
  (they compose with other mods), and `@Inject` at `HEAD` + cancel only where a whole method is replaced on purpose.
- Never `@Overwrite` or paste a vanilla method body: that ships Mojang's code.
- `defaultRequire: 1` is set, so a target that moves is an error, not a silent no-op. `clientAudit` and
  `serverTest` force-load every target (Mixin's audit) and fail on any Mixin error; a deliberately broken target
  was checked to fail the audit.
- Logic that can live outside a mixin does, in plain classes with unit tests (`oldfaithful-hardening/src/test`).

## The harness

`harness/` is a test-only mod (`oldfaithful-selftest`, never shipped) plus the tasks above. It launches the game the way a
player's launcher does: Fabric Loader, Mixin and ASM, the intermediary jar and the untouched official jar on the
classpath, our remapped jars in `mods/`; Fabric remaps the game itself. The self-test runs inside that runtime (the
server's init entrypoint; the client's pre-launch entrypoint, which exits before the game's main) and prints
`[oldfaithful-selftest] PASS/FAIL` lines the task checks. Full output: `local/run/*/harness-output.log`.

## The client start-up test

`./gw :harness:clientStartupTest` (Claude Opus 5.5, 2026-11-01; allowed by the user the same day) launches the game
the way the SDL dev instance does: Fabric's launch with the official jar, our LWJGL component and its natives, the
hardening and display mods plus the self-test mod, Java 21, offline as `OldFaithfulTest`, in a fresh
`local/run/client-startup` with the sounds copied in from `local/cache/selftest-sounds` (clientAudit fills it) and a
tiny render distance. The self-test's `StartupCheck` drives it from the game's tick: the main menu comes up; the window
is titled "Minecraft", exists and was never shown or active; the mouse is on the system's pointer settings; a synthetic
Ctrl+V into Multiplayer's address field pastes what SDL reads from the clipboard (read, never written or printed);
then a new world (`saves/oldfaithful-startup`) is created as the Create World screen does, runs 100 ticks, and the
game stops itself as its close button would. The launch carries a Prism window size (1000x600, in Prism's system
property) and the window and the game must be that size. Two short launches follow in `window-memory/` with the display
mod's `window.remember` on, to the main menu only: Prism asking for maximised over a placement kept from when it asked
otherwise (Prism's change wins), which the self-test then resizes, moves and maximises; then one that must open exactly
there; the kept file is read back after each (`docs/lwjgl.md` > The display mod). `./gw :harness:clientStartupTestFullscreen`
adds a second launch that asks for (borderless) fullscreen at its stop, and a third that opens that way over the kept window and
comes back on F11. On a hidden window SDL only records the request and nothing happens at the OS level, so this checks our
kept state, not real fullscreen, which stays the user's check (below). The task fails on a failed or missing check, any error-like line in the
output (exceptions, stack frames, `[ERROR]`, GL errors, Mixin errors, our WARNs), no "OpenAL initialized", no
"Stopping!", no saved world, a nonzero exit or the timeout. Output: `local/run/client-startup/harness-output.log` and
the game's own `logs/purefaithful/latest.log` there.

Why it can run while the PC is in use:
- **A private desktop.** `tools/private_desktop.py` starts the game on a Windows desktop of its own (CreateDesktop),
  which is never switched to: nothing on it can be seen, take the user's focus or receive their input, whatever the
  game does, including AWT's frames and a crash window. Checked: a visible window made there isn't among the user's
  desktop's windows, and GL there is the real NVIDIA driver.
- **The cursor is shared, though.** The session has one cursor and one clip rectangle: ClipCursor from the private
  desktop confines the user's cursor (a LWJGL 2 benchmark run took the user's mouse that way, 2026-11-01, and a probe
  confirmed it), and SetCursorPos may move it. So in test and benchmark launches the game never grabs or warps the
  mouse (the self-test mod's `HandsOff`: b1.7.3's `Mouse.lockCursor`/`unlockCursor` don't reach LWJGL, so neither
  LWJGL 2 nor SDL clips, centres or warps), and `private_desktop.py` guards the cursor while the game runs: a changed
  clip stops it at once and restores the clip (exit 125); repeated cursor moves with no mouse input behind them stop
  it too (exit 126, best effort: it can't see a warp while the user is moving the mouse). SDL can still release a
  clip (`ClipCursor(NULL)`) on window events if the user's own app has clipped from the desktop's corner; the guard
  would catch that. The guard only ever clears a clip, never sets one (2026-11-06: putting back a clip held from before the test began pinned the user's cursor to a pixel when their game let go). A clip already there when a test starts is the user's, and so is its release. A clip the user's own app sets (the foreground window's process outside the test's job, the clip within that window) is taken as theirs and adopted, not undone (2026-11-03: undoing it let go of the user's game's mouse mid-play). `PRIVATE_DESKTOP_NO_GUARD=1` turns the guard off (the user's call, 2026-11-03:
  their own game clipping the cursor kept stopping tests; `HandsOff` still keeps the test game from grabbing). When the guard stops a benchmark run, `tools/bench.py` doesn't halt the series
  (user, 2026-11-01: the cursor being taken while they're away doesn't matter, hours of stopped work does): it checks
  the user's idle time (GetLastInputInfo); after three minutes without input they count as away and the run is
  retried at once, otherwise it waits until they are. Trips are recorded in the run's `run.json`. HandsOff stays on
  regardless: not touching the cursor is the default.
- **A hidden window** besides (`-Doldfaithful.lwjgl.hidden=true`: created hidden, never shown or raised). Our layer
  never grabs (relative mode) or warps the real cursor for a hidden window, whatever the caller asks; the grab's
  semantics still apply to its own state. That makes `:harness:lwjglTest`, which runs on the user's desktop and
  grabs and releases, safe too (before this, its release check moved the user's cursor with SetCursorPos).
- **No waiting on the refresh**: hidden clients run with swap interval 0 once the main menu's checks are done, as waiting on it looks like what stalls the display. Only the short runs that test the vsync setting, with `oldfaithful-options` and no `worldChecksOnly`, keep it on.
- **No sound out loud**: OpenAL Soft's wave writer (`ALSOFT_DRIVERS=wave`) writes `sound.wav` in the run folder.
- **Always stops**: the game and anything it starts run in a job object that's killed on the timeout (180 s), and
  whenever the launcher script itself ends, even if killed; the Gradle task waits 30 s more, then kills the script.
- **On Linux** (`clientStartupTestLinux-<image>`): the same launch inside a container from
  `harness/linux/Dockerfile.<image>`, through `tools/in_linux.py`, which mounts the project (read-only but
  `local/run/`) and Gradle's caches, translates the command line's paths and runs it on an Xvfb display under a
  timeout (300 s: llvmpipe is slower). Nothing reaches the host's screen, input or speakers. Output in
  `local/run/client-startup-linux-<image>/`.
- Only in this test, the self-test mod stops the game pausing itself when its window lacks focus (it never has
  focus here), so the world runs. Nothing else of the game's is changed.

## Client checks (the user's)

What the headless runs can't show: the game with a window, a real account, sound through the speakers, and Prism's
own launch. `./gw prismDevInstance` writes `local/prism/OldFaithful-dev.zip`, our own instance: our `net.minecraft`
component (Mojang's jar by URL and SHA-1, Fabric's main class through Prism's legacy launcher with `noapplet`, Java 21
or 25), our LWJGL and OpenAL builds, Fabric Loader, Mixin, ASM and the intermediary, every library inside the zip
(Prism's `local` hint), and the mod. Prism picks Java 21 for it (its own `java-runtime-delta` if automatic Java is on).
Import it in Prism (Add Instance > Import); it doesn't touch other instances.

1. Launch with a Microsoft account; make a singleplayer world. It starts, no crash, and Prism's log shows Java 21.
2. Sounds: footsteps, breaking blocks, music after a few minutes (our OpenAL Soft build). Prism copies the sounds
   into `.minecraft/resources`, so nothing should download.
3. Your skin in third person (F5), and your cape if you have one.
4. Online-mode join: with `./gw :harness:runServer` running, Multiplayer > 127.0.0.1. Getting in means the
   client's HTTPS join and the server's HTTPS check both worked.
5. Prism's log for the instance has no `[oldfaithful-hardening] WARN` lines, no Mixin errors, and no "Failed to initialise
   controllers" (the skipped jinput call). "Setting user:" shows your name only, with no token after it, and there's
   no "Failed to add pack.mcmeta" or "READ_ME_I_AM_VERY_IMPORTANT".
6. `.minecraft/logs/purefaithful/latest.log` in the instance folder holds the same output, time-stamped, and a session
   can read it from there; no token anywhere in it.
7. If it doesn't start at all: Prism's log for the launch.

### Phase 3: the SDL instance

`./gw prismSdlInstance` writes `local/prism/OldFaithful-sdl-dev.zip`: the same instance with our LWJGL component in
place of LWJGL 2 and `oldfaithful-display` beside the hardening mod. It imports alongside the LWJGL 2 one (Prism
names it "Old Faithful (SDL dev)"), so the two can be compared. What to look at, beyond the list above:

1. It starts in a window of its own, with no empty "Minecraft" frame flashing or left behind, and takes focus.
2. Resize the window, maximise it, drag it between displays if there are two: the picture fills it and stays sharp,
   the GUI rescales.
3. F11: fullscreen at once (borderless); alt-tab away and back: still fullscreen; F11 again: the window comes back
   at its earlier size.
4. Mouse look: smooth, and the same speed as on the LWJGL 2 instance at the same sensitivity (it follows Windows'
   pointer settings, as vanilla did; `mouse.input=raw` in `config/purefaithful-display.properties` for raw input);
   menus: the pointer where it should be after Esc.
5. Typing in chat and on signs, with Shift, with the keypad, and anything layout-specific you use; Ctrl+V pastes
   into the server address and world names; WASD and other bound keys work, and the Controls screen names keys
   sensibly. The window's title reads "Minecraft".
6. Sound and music as on the LWJGL 2 instance; unplug or switch the output device while playing: sound follows.
7. `.minecraft/logs/purefaithful/latest.log` has no `WARN`, no exception, no `[LWJGL] [ERROR]`.
8. If it doesn't start: Prism's log for the launch, and the log above (it starts before the window does).
