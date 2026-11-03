# Speed (phase 4)

Claude Opus 5.5, 2026-11-01. A benchmark of the real game, a baseline against vanilla, a profile of where the time
goes, and the plan that follows from it.

## Rules (user, 2026-11-01)

- **Reliability first** (user, 2026-11-02): what they meant by "pure wins only" is above all never trading safety,
  security or stability for speed; speed trade-offs in general aren't ruled out, and fixes may cost speed. The rule as
  first written, which the work so far followed:
- **Pure wins only.** An optimisation goes in only if it trades nothing away: looks, behaviour, world generation
  (bit-identical), mod compatibility, memory, determinism, worse lows for better averages. Anything that costs
  something is written up with the cost stated concretely and goes to the user as a question, or is dropped.
  World generation, precisely: the generator and each chunk's terrain stay bit-identical, but decoration (trees, ores,
  lakes and the like spilling into neighbouring chunks) depends on the order chunks load and populate, and frame rate
  shifts that order (above all how fast chunks are built, which asks for their neighbours). So a seed's details were
  never fixed in vanilla either, between machines, settings or two runs, and the speed mod's faster chunk building
  shifts them the same way.
- **Measure in proportion.** Profile to find where time goes: that directs the work. A change that reasons out as a
  pure win needs no confirmation series; where the gain is genuinely uncertain, a quick before/after (a run or two)
  is enough. No statistical machinery per change.

## The benchmark

`./gw :harness:benchSetup` writes `local/bench/launch.json` (each configuration's launch, as a launcher builds it);
`python tools/bench.py run LABEL [options]` runs the game; `python tools/bench.py report LABEL` summarises
(`local/bench/LABEL/summary.md`, `.json`). `python tools/jfr_profile.py local/bench/LABEL/RUN [--phase fly]` reads a
run's JFR profile (`run --jfr`): time on the game thread by part of the game, and hot methods, with Biny's names.

- **Configurations**: `sdl` (our LWJGL component, display mod), `speed` (`sdl` with `oldfaithful-speed`) and
  `lwjgl2` (vanilla's LWJGL 2, our build), all
  with the hardening and self-test mods, Java 21, Prism's default heap (-Xms512m -Xmx4096m), sound into OpenAL
  Soft's null output, b1.7.3's default window (854x480) and options (fancy, smooth lighting).
- **Script** (the self-test mod's `Bench`, from the game's tick): a new world on seed 404, the same spawn point and
  facing every run (vanilla searches for the spawn with an unseeded Random and gives a new player a random yaw; the
  bench seeds the search and faces +Z); **settle** at spawn until
  every render chunk has been built once (b1.7.3 keeps re-dirtying some, so an empty queue never comes); **stand**
  10 s; **turn** 360 degrees in 10 s, per frame as mouse look turns; **fly** 30 s along +X at y 110, 10 blocks/s,
  looking 25 degrees down, through new terrain. Weather held clear. `stand.png` and `fly.png` record what was drawn.
- **Per frame**: frame time and its parts (ticks, lighting updates, rendering, chunk building within it, terrain
  generation, swap), GPU time (GL_TIME_ELAPSED), render chunks waiting and rebuilt, when the input was read
  (`pump_us`, Display.update's return) and the display mod's frame instant (`instant_us`), both from the frame's
  start, for evenness under vsync (`docs/lwjgl.md` > Even motion); per phase CPU time and heap; every GC.
- **Modes**: throughput (`--vsync off --fps-limit 0`: Max FPS, frame times are the work) and as played
  (`--vsync on --fps-limit 1`: b1.7.3's default Balanced with vsync, as players have it now; the earlier as-played
  results below were taken with vsync off, then the layer's default).
- **Safety**: a private desktop, the game never grabs the mouse, the cursor guarded (`docs/building.md`); 30 s
  cool-downs; a GPU driver reset stops a series. Runs that start on a busy PC are left out of reports and listed.

## Is it the game as played?

- It renders for real on the RTX 5070 (GL_RENDERER; the saved frames show the world). Hidden and shown windows on
  the private desktop gave the same frame times.
- The GPU is nearly idle (GPU time under 20 µs a frame at 854x480, 4% use, 34 W): b1.7.3 is CPU-bound here, so
  the private desktop's presentation doesn't hide anything that matters.
- Not covered: a visible window under DWM, bigger windows, other GPUs, entity-heavy scenes, the Nether.

## Vsync (2026-11-01, 2026-11-02)

LWJGL 2's `Display` sets swap interval 0 on creation; our layer first left it to the driver (1), so the game was held
to the display's refresh, which starved chunk building at the default Balanced (it builds chunks until a 1/120 s
deadline, already passed at 60 Hz): at far distance 1,420 render chunks were still unbuilt after 120 s, where LWJGL 2
built them all in 34 s. We set 0 as LWJGL 2 did; the user then played without vsync and found it really bad on
today's displays (tearing, uneven pacing at hundreds of fps). So vsync is now on for players (the display mod's
`vsync=true`; our layer sets the interval explicitly either way, `docs/lwjgl.md`), and the starved chunk building is
made up for by `oldfaithful-speed` (Chunk building under vsync, below). The benchmark's `--vsync` takes `off`
(throughput), `on` (as players have it) or `driver`.

## Baseline (2026-11-01, seed 404, medians of 2 to 3 runs)

Taken before the spawn was fixed: each run stood somewhere else, so its load times compare less well than its frame
rates (the speed results below are on the fixed spawn).

Throughput (Max FPS, vsync off), fps and median frame ms:

| | far: stand | turn | fly | all built | short: stand | fly | all built |
|---|---|---|---|---|---|---|---|
| LWJGL 2 | 524 (1.63) | 555 | 437 (2.10) | 62 s | 659 | 649 | 24 s |
| ours | 588 (1.65) | 579 | 433 (2.15) | 61 s | 721 | 669 | 11-30 s |

As played (Balanced, far): about 110 fps while loading and flying (Balanced spends spare frame time building
chunks), 400-500 standing, everything built in 32-35 s, on both layers (ours after the vsync fix).

Run-to-run spread is about 5% on fps and 10% on load times at far; short distance's load time swings widely
(chunks near spawn keep re-dirtying). The two layers are equal within that spread: our layer neither costs nor
gains. Stalls over 50 ms are rare (0-3 per phase); GC pauses total 10-30 ms per phase.

## Where the time goes (JFR, far, Max FPS; game thread)

| Part | stand | fly | settle |
|---|---|---|---|
| clouds (fancy) | 58-65% | 38-41% | 42% |
| chunk meshing (`ChunkBuilder.rebuild`) | 12-13% | 28-30% | 30% |
| terrain generation | 1% | 8% | 5% |
| frame setup (`Frustum.getInstance`'s `glGetFloatv`, fog, camera) | 7-10% | 6-7% | 7% |
| terrain drawing (display lists) | 3-6% | 3% | 3% |
| lighting, ticks, entities, visibility, swap, disk | each under 4% | | |

Same on LWJGL 2 and ours: these are vanilla's costs. Inside meshing: `World.getNaturalBrightness` via smooth
lighting (7-9% of the thread), `BlockRenderManager.renderSmooth`, `WorldRegion` lookups, and `HashMap` lookups whose
buckets have turned into trees (3-6%: `ChunkCache`'s chunk map keys on `ChunkPos.hashCode`, which spreads badly).
Two `glGetError` calls a frame (vanilla's "Pre render"/"Post render" checks) take 2-3%.

Fancy clouds: 2 passes (depth, then colour) over 36 cells, each its own `Tessellator.draw`, so 72
`glDrawArrays` from client memory every frame, in quads, regardless of view distance: about 1 ms a frame on this
PC. Whether that is per-call overhead, per-vertex copying or a driver sync point that would move elsewhere is the
open question a quick before/after answers.

## oldfaithful-speed (2026-11-02)

The mod for speed work, in both editions: everything in it changes nothing the game does or shows, so Purist takes it
whole. Client only for now (the server package is phase 6). Narrow injections throughout (no `@Overwrite`, no
`@Redirect`); checked against the popular mods in `harness/linkcheck-mods.json`: UniTweaks wraps the cloud height
call inside the same cloud method (compatible), SmoothBeta replaces the same chunk map with its own and reads it
(we leave a non-vanilla map alone) and hooks the tessellator inside rebuild (untouched by us).

| Change | Why it's pure |
|---|---|
| **Clouds in one draw per pass** (`FancyCloudsMixin`) | Each pass drew its 36 cells with the same GL state; every cell sets colour, normal and texture, so the tessellator's client state is the same for the batch. Same vertices in the same order in one `glDrawArrays`: the same primitives, so the same pixels (GL draws primitives in order, each from its vertices and the state). Whole quads per cell keep the quad-to-triangle conversion aligned. The batch is drawn where the next pass changes the colour mask, and at the colour reset that ends the method |
| **Chunk maps hashed properly** (`ChunkMap`, `ChunkCacheMixin`, `MultiplayerChunkCacheMixin`) | Same keys (ChunkPos.hashCode, collisions and all), same equality, same answers; only the bucket hash mixes all the key's bits and the last hit is remembered. Unit-tested against HashMap through 400,000 random operations. Only replaces a map that is still vanilla's HashMap |
| **Light memoised per chunk rebuild** (`WorldRegionMixin`, `ChunkBuilderMixin`, `LightCache`) | Smooth lighting asks the rebuild's region for the same positions' light many times per block. The value is a function of the position while the rebuild runs (nothing changes the world during meshing), so the first answer stands. On only for the region `rebuild` makes, off when it returns; regions made anywhere else never cache |
| **The unused frustum skipped** (`GameRendererFrustumMixin`) | `renderFrame` computes the frustum (two `glGetFloatv`, each a sync with the driver) and drops it; the `FrustumCuller` made a few lines later computes it again from the then-current matrices, and that is the one culling uses. Nothing reads it in between (sky and fog setup only) |
| **Double slabs mesh no hidden top** (`DoubleSlabFacesMixin`, 2026-11-02, `docs/bugs.md` #47) | `SlabBlock.isSideVisible` computes the full block's answer for the double slab, drops it (a missing `return`) and applies the half slab's rule, which always shows the top. Answered with the full block's rule, only the top under an opaque block goes: it lies between two opaque blocks, whose side faces enclose it, and the block above draws no face there (culled against the opaque double slab), so depth hid it from every outside view. Half slabs and modded slabs keep their rule. Reasoned, not measured: generated worlds have no double slabs |

Robustness (2026-11-02, after an outside review): injectors are `require 0`, so another mod reshaping a target
turns that optimisation off instead of stopping the game. Each handler falls back to vanilla when its partners didn't
apply (the clouds' batch checks the tessellator itself: a cell whose start or draw wasn't ours draws as vanilla, and
a batch left open is closed at the next frame's start). `SpeedMixinPlugin` logs `[oldfaithful-speed] WARN ... didn't
apply` for core Mixin handlers; MixinExtras' injectors apply after its check, so the game tests run with
`-Dmixin.debug.countInjections=true`, which fails any injector of either kind that found nothing (checked by breaking
one of each). `ChunkMap` is safe for readers on other threads (threaded minimaps): each key and value travel in one
immutable entry, the table is published whole, and the last-hit cache is one reference, so a racing reader can miss
an entry but never pair one chunk's key with another's chunk. `FramePacing` builds nothing extra on Power saver or
after a frame where vanilla slept for lacking focus.

Check of the pixels: with the fixed spawn, the standing frame with and without the mod differs in no pixel below the
sky (0 of 239,120); above it only the clouds' drift between the two capture moments differs.

Results (2026-11-02, far, fixed spawn; `sdl` against `speed`, medians of 2 runs at Balanced with vsync off, then the
layer's default, and 1 at Max FPS):

| | Balanced: fill | stand fps | stand 1% low | stalls >50 ms | fly fps | Max FPS: fill | stand | fly | fly 1% low |
|---|---|---|---|---|---|---|---|---|---|
| without | 35.4 s | 329 | 47 | 5 | 110 | 60.5 s | 560 | 434 | 96 |
| with | 30.0 s | 425 | 82 | 0 | 121 | 54.3 s | 686 | 512 | 138 |

Frame time standing drops about 20% (clouds, the frustum), chunk rebuilds per second while flying rise about 20-30%
(maps, light), and the view fills sooner. As played, flying stays near vanilla's ~110-120 fps because Balanced spends
spare frame time building more chunks.

Vanilla's own loading depends on frame rate, which matters for reading load times: at Max FPS it builds one distant
chunk and runs up to 500 light updates per frame, so how chunks get re-dirtied by later light and decoration shifts
with the frame rate. (Before the spawn was fixed this made the mod look slower to fill at Max FPS; on the fixed spawn
it's faster in both modes.)

### Chunk building under vsync (`FramePacing`, 2026-11-02)

With vsync on (players' default since 2026-11-02, above), b1.7.3's chunk building starves. It builds in `renderFrame`:
one `compileChunks` call per frame at Max FPS; at Balanced, more calls until a deadline 1/120 s after the previous
frame's render (`lastFrameTime`, set right after `renderFrame` returns). Each frame now waits for the refresh, so by
the time the next `renderFrame` runs that deadline has long passed, and Balanced makes one call a frame: about 60
rebuilds a second at 60 Hz, where without vsync it made hundreds. At far distance the view never filled in the
bench's 120 s (about 2,200 chunks left).

The fix spends the frame's wait on more of vanilla's own `compileChunks` calls, unchanged, so it chooses chunks as it
always does. What had to be got right, each found by measuring:
- **Where the frame waits.** With NVIDIA's threaded driver the swap returns at once (0.04 ms), and the wait lands in
  the first call after it that synchronises with the driver: the frustum's `glGetFloat` when `renderFrame` makes its
  `FrustumCuller` (14.6 ms of the frame's 16.7 between the swap and chunk building). Other drivers wait in the swap.
  Any GL call placed before that point takes the wait itself: chunk building there cost 13 ms a call.
- **Where to build.** After the wait, the next frame's sampling of time and input shifts by however long the building
  took, which varies; moving things would judder slightly. Measured: 22% of frames standing still went over 25 ms
  (vanilla 0.2%), at the same 60 fps. So the building runs at the top of the loop, after the ticks and before the
  "Pre render" error check and the swap, while the driver is idle. The wait after it only gets shorter, and
  everything after the wait stays where it was.
- **How long.** The wait ends ("release", where the culler is made) at the display's rhythm. Building stops in time
  for the next release: the last release plus one period (the frames' median interval, never more than one refresh
  of the display, read from `Display.getDesktopDisplayMode()`, so late frames can't stretch it), less the work still
  to come before it (the most of the last 16 frames), less a margin (a quarter of the period, at least 3 ms), with
  room left for one more call at the longest of the last 32. A long tick this frame leaves no room rather than being
  added to. A smaller budget-from-recent-waits rule, and a 2 ms margin, each left frames missing a refresh: one after
  a 20 ms tick, one where the driver was still compiling the display lists just built.
- **Calls that would load.** A rebuild's `WorldRegion` asks the world for the 3x3 chunk columns around it, and a
  missing one is read from disk or generated: tens of milliseconds, more than any frame has spare. Before each extra
  call `NextPicks` works out which chunks it would rebuild, the way `compileChunks` chooses (every dirty chunk within
  16 blocks, the best distant one by `DirtyChunkSorter`, the second best if in the frustum), read-only; if any of
  them needs a chunk that isn't loaded, the extra building stops for the frame and vanilla's own call in
  `renderFrame` does it, as it would have.
- **When nothing waits** (vsync off and CPU-bound) the room is zero and the game does exactly what it did: checked,
  0 extra calls at 460-510 fps.

`-Doldfaithful.speed.pacingLog=true` logs the wait, the room and the extra calls every 300 frames.

Results (2026-11-02, far, Balanced with vsync on, 60 Hz, a quiet PC; one run each; frames over 25 ms are frames
whose sampling came at least half a frame late):

| | far view filled | rebuilds/s settling | frames >25 ms: settle | stand | turn | fly |
|---|---|---|---|---|---|---|
| without | not in 120 s (2,180 left) | 61 | 0.14% | 0.33% | 0.67% | 1.17% |
| with | 48 s | 535 | 0.53% | 0.00% | 0.17% | 0.28% |

While the view fills, a few more slow frames, and they're vanilla's own work arriving sooner. The autosave every 40
ticks (2 s) makes a tick of about 20 ms in both, and that frame is the tick plus normal rendering, 22-25 ms either way,
the next frame catching up (about 11 ms); with the mod the autosave has more freshly loaded chunks to save (median 20-22
ms against 19), so more of those frames land just over 25 ms. The extra building never runs in them (the deadline has
passed). Leaving loading to vanilla's call (`NextPicks`, added after the run above) took our own share of slow frames
from about six in 50 s to two in 56 s (single calls that ran long with no loading due and no GC), at the cost of a
slightly slower fill (54 s against 48: loading picks go one a frame, at vanilla's pace). Once filled, frames are as
even as vanilla's or more so. At Max
FPS with vsync vanilla itself stutters (11-17% of frames over 25 ms; heavy loading per frame), and with the mod it was
5-6% with two to three times the rebuilds, though those runs began on a busy PC and are indicative only.

## Plan

### Done (pure wins, above)

Clouds; chunk maps; light per rebuild; the duplicate frustum; chunk building under vsync.

### Left, and why

- **Frustum without any `glGetFloatv`**: the remaining computation (one pair per frame) could come from matrices
  tracked on the CPU, but only bit-identical matrices make it pure, and the driver's own matrix arithmetic isn't ours
  to reproduce exactly. Not started; it would need a per-frame comparison over long runs before it could go in.
- **Chunk selection** (`compileChunks` scanning the dirty list): 0.6% at Max FPS; as played, a scan of ~1,500 entries
  costs tens of microseconds per call against a millisecond or so of meshing per chunk. An index giving exactly the
  same choice has to follow the camera-dependent order every frame; not worth its risk at this size.
- **Terrain generation**: about 5% of a frame while flying (3% generating, 2% decorating), the noise loops 2.3%. A
  bit-identical rewrite of the noise could save perhaps a third of that and needs a byte-comparison harness; later.
- Meshing allocation: the profile's `ArrayList.grow` comes from `renderChunks`' list, which is cleared, not
  replaced, so it only grows once: a sampling artefact, nothing to fix.

### With a trade-off (decided by the user, 2026-11-02: neither is done)

- **Chunk meshing off the main thread** (distant chunks only; nearby ones stay synchronous as vanilla builds
  them). The biggest gain for loading and flying, but mods that hook `ChunkBuilder.rebuild` or the tessellator
  would run off the game thread (compatibility), and a distant chunk may show the world a frame or two older than
  vanilla would. Skipped entirely: breaking mods rules it out.
- **Dropping the per-frame `glGetError` checks**: 2-3%, but the game would stop logging GL errors. Kept.

### Not worth it here

Display lists to buffers (terrain drawing is 3-6%), a new lighting engine (lighting is 2-5%, and a different engine
risks different light values, which is the Fixed edition's business), entity culling (under 1% in these scenes),
GC tuning (pauses are small).
