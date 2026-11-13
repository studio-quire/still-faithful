# Lighting (`oldfaithful-lighting`, docs/bugs.md #3)

Claude Opus 5.5, 2026-11-02. The Old Faithful edition's lighting fix: what b1.7.3 gets wrong, the mod, how it's
checked.

## The rule, and what "right" means

b1.7.3 has one rule for a cell's light, in `LightUpdate.updateLight`: sky light is 15 at or above the column's
height-map value; block light starts at the block's luminance; otherwise a cell takes its brightest neighbour less its
own opacity (at least 1), never below 0 or its own source, and an opaque block that gives no light is 0. Because every
step costs at least 1, the rule has exactly one solution for a given world: light can't hold itself up in a loop. And
when every cell agrees with its six neighbours under the rule, the light is that solution; any wrong light shows up
somewhere as a cell that disagrees. "Right" here is vanilla's own rule carried out without losing any of the work, not
a new lighting model: where vanilla got it right nothing changes.

## What vanilla loses (traced in the decompiled source)

- **A new chunk's own light requests.** `Chunk.populateHeightMap` (called by the generator, and by chunk loading when
  sky light is missing) asks for updates wherever neighbouring columns differ in height (`lightGaps`), but the chunk
  isn't in the chunk map yet, so `World.queueLightUpdate` drops every request centred in it (`isPosLoaded`). Most of
  it is redone by accident when decoration places ores and trees (each placed block re-asks for its column); what
  isn't stays dark: pockets under overhangs and cave mouths.
- **Generated lava never lights anything.** The terrain generator writes lava straight into the block array, and
  `Chunk.populateBlockLight` is empty, so underground lava lakes and the Nether's lava seas leave their surroundings
  (and their own cells) at block light 0 until something nearby is changed.
- **Columns next to unloaded chunks.** `LightUpdate` skips a column unless the chunks of all eight columns around it are
  loaded, and nothing comes back for it. The rule only reads the four beside it, so a column in a chunk's corner was
  skipped whenever the diagonal chunk was missing.
- **Light through water and ice.** When a cell's light changes, `World.updateLight` asks a neighbour to update only if
  the neighbour doesn't already hold the new light less one. That's sound for cells costing 1 to pass, not for water
  and ice (opacity 3): light behind them that falls by exactly two can leave them too bright. Rare (found by reading,
  not seen in the test).
- **The queue thrown away.** Past 1,000,000 queued updates, `queueLightUpdate` clears the queue ("aborting lighting
  updates"). The single player client works through 500 updates a frame, so heavy generation can pile that up.
- **Light half done at a save.** Leaving a world (or stopping a server) saves whatever is still queued as unfinished.
- Not causes, though bugs.md listed them: the 50-deep recursion cap (`lightingQueueCount`) can't be reached, since
  nothing inside `queueLightUpdate` queues again; and `doLightingUpdates`' 500 a call only paces the work (the server
  loops until the queue is empty).

## The mod

Vanilla's queue and `LightUpdate` stay the worker for everything the game changes as it's played, so other mods'
updates go the same way; what the mod finds wrong in a new chunk it lights itself, at once.

- **Every chunk checked as it enters** (`Relight`, `ChunkMixin`): when a chunk is put in the world, generated or read
  from disk, it's noted; at the start of the next lighting pass each of its cells is checked against the rule, and
  so are the cells facing it in its loaded neighbours (whose own check was waiting for it). A cell whose six
  neighbours aren't all loaded is skipped, and checked when the last of those chunks enters. Cells that are too dark
  are lit there and then, and the light spread from them as far as it reaches (`Spread`: a flood, brightest first,
  that only ever raises a cell to what a neighbour passes on under the rule, reporting each change as `setLight`
  does, so render chunks rebuild and a server tells its players). Cells that are too bright go to vanilla's queue,
  whose updates bring light down, and so does any neighbour of a lit cell found too bright (vanilla's own updates
  ask a changed cell's neighbours, and the height map's direct writes rely on it). All of it happens in the lighting
  pass, never while a chunk is meshed (so `oldfaithful-speed`'s light cache still holds).
- **Decoration's light checked the same way** (`GeneratorDecorateMixin`, `Relight.requested`): a chunk is decorated
  when its neighbours on one side load, often after its own check, and the updates decoration asks for (lava and
  water lakes, glowstone, trees) are noted and their cells checked at the next pass like an entering chunk's.
- **Columns skipped only for their own neighbours** (`LightUpdateMixin`): a column waits only while a chunk holding
  one of its four neighbours is missing, which that chunk's check covers.
- **Water and ice always asked** (`WorldMixin.updateLight`): a lit neighbour that costs 2 or more to pass updates
  whenever the light beside it changes.
- **The queue never thrown away** (`WorldMixin`): the million limit is lifted.
- **Light finished before a full save** (`WorldMixin.saveWithLoadingDisplay`, full saves only; autosaves don't wait).
  Single player makes a full save on Save and Quit and on changing dimension. Closing the window makes none in b1.7.3
  (the progress screen throws while the game stops, and `Minecraft.stop` swallows it), so then only the autosaves'
  chunks are on disk; any light they hold unfinished is put right when those chunks next load.

Existing worlds heal as they're played: each chunk is checked when it loads, so wrong light in a world made without
the mod is put right the first time its chunk loads with it, and saved so. Save files stay in b1.7.3's format.

Not on a multiplayer client: there the server's light is the world's (and `Chunk.load` isn't called for chunks
received from a server). The client's own queue handling (the corner columns, water, no clearing) applies as it does
anywhere.

### Distant lava stayed dark

The first version sent what the check found through vanilla's queue. The single player client works through that
queue at 500 updates a frame, newest first, and exploring adds work faster than that, so a far chunk's lava (a lake's
light is thousands of updates) sat at the bottom of the queue while new chunks kept coming, and the light came on only
when something near the player poked the lava (random ticks run within nine chunks of a player) or the queue finally
ran dry. Surface lava lakes, placed by decoration, waited the same way even in vanilla's own queue. On a server the
queue is emptied every tick, so the server test couldn't see it. Lighting the found cells at once (the spread) and
checking decoration's requests fixed it; the paced test below catches it.

## The check (`./gw :oldfaithful-lighting:serverLightTest`)

`LightCheck` computes the rule's solution from scratch for a saved world (a flood from the sources over every chunk in
the region files, missing chunks reading 0 as the game reads them) and compares it, cell by cell, with the saved light
in every chunk whose eight neighbours were saved (light travels at most 15 blocks, so those can't depend on anything
outside the files). It shares no code with the mod. `./gw :oldfaithful-lighting:lightCheck -Pworld=<folder>` runs it
on any world.

The test runs the real dedicated server on seed 404 with a fixed spawn and a scenario (`src/scenario`, a test-only
mod): the spawn areas of both dimensions; a band of chunks beyond them generated in a scattered order; half of those
unloaded and loaded again after their neighbours changed; 60 explosions (surface, big, underground); 40 glowstone
lamps, half taken away again; stone laid over water and partly removed. Then:

| world (seed 404) | overworld: sky wrong | block wrong | Nether: block wrong |
|---|---|---|---|
| vanilla server, the scenario | 492 (in 25 chunks) | 12,017 (369 chunks; 7,024 of them lava cells) | 34,234 (281 chunks; lava seas) |
| with the mod | 0 | 0 | 0 |
| vanilla's world opened with the mod, every chunk loaded | 0 | 0 | 0 |
| with the mod's world opened again | 0 | 0 | 0 |

919 overworld and 515 Nether chunks compared, about 30 and 17 million cells of each light type. Nearly all the
errors are too dark: generated lava (the block light column) and pockets the dropped and skipped requests left (sky).
Each server run also checks the loaded world in memory at every full save (`-Doldfaithful.lighting.verify=true`, in
`Verify`): no cell disagrees.

**Paced** (the regression test for distant lava): the server's lighting held to a single player client's pace (three
passes of 500 updates a tick, as 60 frames a second do; `ServerPaceMixin` in the scenario), a band of Nether chunks
loaded two a tick, then the loaded world checked in memory the moment the last one is in, before the queue is done.
The first version left 259,958 cells wrong there; now 0. In the real client as played (far view, vsync, Balanced),
checked every 10 s while settling and flying (`-Doldfaithful.lighting.verifyEvery=10`): the first version had 407 to
726 cells wrong at 8-15 chunks out while loading; now 0 to 51, each gone by the next check (what vanilla's queue was still working through).

The single player client, checked through the benchmark's worlds (far view, settle,
fly 300 blocks, about 330 chunks saved): vanilla left 288 block light cells wrong (lava), the mod none; sky light was
right in both, so the sky errors are rarer than the catalogue first guessed.

## Cost

The check of an entering chunk, with the lighting it does, takes about 0.4 ms (most cells pass on a lookup or two;
the spread is far cheaper than vanilla's updates for the same light). Figures from the first version, which queued the
fixes, are marked; the spread made the server's start faster than vanilla's own.

- **Single player as played** (far view, vsync on, Balanced; one run, the current version): time in
  `doLightingUpdates` per frame 0.26-0.36 ms on average while loading and flying (vanilla's 500 updates a frame
  plus the checks), 99th percentile 2.3-2.4 ms, none standing; 7 frames over 25 ms while the view filled and 8 while
  flying, of about 3,300 and 1,800.
- **Single player, first version** (`python tools/bench.py run ... --configs speed,lighting`, far view, Max FPS; 2 runs each, the
  lighting runs on a busier PC, so fps is indicative only): time in `doLightingUpdates` per frame 0.052-0.070 ms
  against 0.038-0.051 while loading and flying (about 0.6 s more over a 2-minute run, under 1% of frame time), none
  standing; fly fps 385-431 against 371-392 and the far view filled in 63-64 s against 65-72 s, so nothing measurable
  beyond the noise. One frame of 35-44 ms right after entering a new world, where the chunks generated behind the
  loading screen are checked at once (vanilla's longest lighting frame there was 5-20 ms). The queue peaked at
  126,000 at that moment and stayed under 25,000 while flying.
- **Server**: starting a new world (1,250 chunks across both dimensions) takes 4.4-4.5 s, against vanilla's 7.6-8.4 s
  (the first version: 8.6-8.7 s): lighting a chunk's lava once beats vanilla's updates working it out cell by cell.

`-Doldfaithful.lighting.stats=true` logs the checks, cells found wrong and the queue's peak every ten seconds.

## What changes for players

- Lava lakes (on the surface and underground) light their surroundings as soon as they load, near or far, and the
  Nether's lava seas light the shores and air above them, as lava was always meant to (it's a light source; generation just never lit it). The largest visible change, and it
  affects spawning: the overworld's hostile mobs spawn only where it's dark, so fewer spawn right beside lava (the
  Nether's pigmen and ghasts ignore light, `PigZombieEntity.canSpawn`, so nothing changes there).
- The occasional dark pocket under an overhang or at a chunk border fills in.
- Existing worlds change as they're played: each chunk is put right the first time it loads with the mod, and saved
  so. Nothing else in the world changes, and the files stay b1.7.3's.
- Lava's light has a switch (below); the rest has none: it's one mechanism, keeping every cell to beta's own rule,
  with no part of it a player would want back. Players who want all of beta's light leave the mod out.

## The switch (`config/oldfaithful-lighting.properties`)

`lava.lightGenerated`, on by default: lava from terrain generation lights as it loads.
The file is written with an explanation on first start (`LightingConfig`, as `oldfaithful-fixes` does); a server's
setting is its world's. Old Faithful's settings screen (Lava Light, `docs/options.md` > The settings screen) changes it
while the game runs: the check reads it per chunk, so it changes land as it loads, and either way the light it finds
stays consistent (a lit body of lava is a source under both; a dark one beside land lit under the full rule is lit
through by the spread), so light already in the world stays as it is.

Off is beta's behaviour for that lava, precisely: lava whose block light is 0 counts as giving none until light
reaches it. A neighbour lit to 1 or more, or an update of the lava itself, makes it a source like any lava, and the
light then runs through the whole body of lava touching it, as vanilla's own updates run it (a lit cell's update asks
its neighbours, and lava asked lights). So nothing is left half lit: a body of lava is dark or lit throughout, and
everything else keeps to the rule. In the code, the entry check (`Relight.wanted`) leaves such lava alone, the spread
lights any light source it reaches, and the decoration check uses the full rule, since vanilla's queue recomputes
every cell decoration asks about (lava placed by decoration, a lake on the surface, was always lit). Lava placed or
moved by players, buckets and flowing was always lit, and still is.

Checked on the server (the test's `lavaoff` variant, same seed and scenario): against the full rule the overworld has
about 9,850 block light cells dark (6,128 of them lava; vanilla had 12,017 wrong, 7,024 lava) and the Nether 5,520, with
sky light right and nothing too bright; against the rule with beta's unlit lava (`LightCheck`'s `betaLava`), none.
The Nether has far fewer than vanilla's 34,234, most likely because in vanilla the light of fire and glowstone reaching a lava
sea often stopped partway (the losses above), leaving seas half lit; here a sea that light touches lights through.
