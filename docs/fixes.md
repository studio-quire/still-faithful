# The Fixed edition's fix mod (oldfaithful-fixes)

Claude Opus 5.5, 2026-11-02. Old Faithful's own mod, beside Pure Faithful's: fixes of beta's clear mistakes from
`docs/bugs.md`, narrowly: beta as it was meant to work, nothing players built around. The
causes, traced in the decompiled game, are in each entry there; the mixins' comments say what each changes and why.

## Shape

- `oldfaithful-fixes/`, a mod like the others, public id `oldfaithful-fixes` (not purefaithful-: its
  `gradle.properties` sets `oldfaithful_edition=fixed`, docs/release.md > Names). Both sides (`environment: *`).
- One switch per fix in `config/oldfaithful-fixes.properties`, all on, written with explanations on first start. A
  fix that's off isn't applied at all: `FixesConfig` (the mixin config plugin) skips its mixins, so a player of a
  modded setup can switch one off to clear a clash with another mod, not just its effect. Each fix's mixins live in
  their own package under `mixin/`, named in `FixesConfig.FIXES`; a mixin outside them is an error.
- On Old Faithful's settings screen (Options > Old Faithful..., `docs/options.md` > The settings screen) through
  `FixesSettings`: each fix's name on its button, its section of the Fixes menu, a one-line hint of what beta did, and whether the world
  decides it (the server's switch in multiplayer). A new fix adds a row there, or joins `SERVER_ONLY` if it runs only
  on a server; `FixesSettingsTest` fails until it does. Every fix waits for a restart there, since its mixins are in
  the game only if it was on at start.
- Old Faithful's, not Pure Faithful's: in Old Faithful's pack, server package and release (`./gw releaseFixed`,
  `docs/release.md`), never in Pure Faithful's or in its public repository; `settings.gradle` and the harness include
  it only where it exists, so Pure Faithful's repository still builds without it.

## Where each fix runs

| # | Switch | Side | How |
|---|---|---|---|
| 7 | `sky.afterContainer` | client | Lighting off at the start of `renderSky` (a container screen leaves it on) |
| 27 | `vehicles.dismountOnTop` | both | Getting off a vehicle, the height asked for is nudged up by the least amount that puts the rider's box on top |
| 8 | `tools.lastUse` | server; single player | The used-up tool stays in the hand until the block has dropped, then goes |
| 9 | `slabs.wooden` | both | Where mining speed and harvest are decided with the position known: a wooden slab harvests as planks and mines at least as fast |
| 10 | `tools.speedByMaterial` | both | Pickaxes, axes and shovels mine fast every block of their material (`ToolSpeeds`, the table in `docs/bugs.md`); obsidian and redstone ore keep beta's speed (the user's call) |
| 11 | `furnace.lavaBucket` | world | Fuel that names a crafting remainder (only the lava bucket) leaves it in the slot |
| 19 | `liquids.dropsBelow` | world | The downward flow drops (water) or fizzes (lava) what it replaces, as the sideways flow does |
| 25 | `slimes.splitOnOverkill` | world | Health below zero counts as killed for splitting |
| 26 | `sleep.wakeThroughWalls` | world | The arrival test measures from the path's block centre, not its corner |
| 29 | `pigs.saddleDrop` | world | A saddled pig drops the saddle with its pork |
| 30 | `render.undersides` | client | An underside above the block's floor (fence bars) is lit from the block's own space, in flat and smooth lighting |
| 31 | `minecarts.passItems` | world | Items and arrows aren't solid to minecarts |
| 73 | `screens.worldTitle` | client | Create New World and Rename World keep their title at the top: its height went by the window while the fields stayed where they are |
| 33 | `items.badDataNames` | client | Slab and dye names read out-of-range data as the first kind instead of crashing |
| 34 | `render.hotbarAfterCracks` | client | The usual blending put back after the block cracks are drawn |
| 34 | `render.ridingPoses` | client | Both armour models get the riding pose and swing; the first-person hand is drawn unseated |
| 34 | `gui.deathScore` | client | The death screen's score label gets its colour code right |
| 34 | `fishing.catchToFeet` | world | The catch (and a hooked mob) is pulled towards the feet, as a server's always was |
| 38 | `sound.stepsAfterFalls` | both | The next footstep is kept within one of the distance walked, so no backlog builds in the air |
| 39 | `gui.cursorItemOnTop` | client | The cursor's stack drawn last, in front of the player figure and labels |
| 40 | `gui.sliderSteps` | client | Volume and sensitivity follow the mouse to the screen pixel and snap to whole labelled percentages; every one reachable from GUI scale Normal up |
| 41 | `redstone.repeaterUpdates` | world | Doors, trapdoors, note blocks, dispensers, TNT and rail junctions count a repeater as a source when a neighbour changes |
| 42 | `ticks.saved` | world | Pending block ticks saved with their chunk as `TileTicks` and restored on load; ticks due near the loaded edge wait, parked, instead of being dropped (`PendingTicks`) |
| 44 | `redstone.torchBurnout` | world | Burnout records from the future (an older world's clock, time set back) are dropped |
| 45 | `crafting.shiftClickFits` | world | A shift-clicked crafting result is taken only when all of it fits |
| 46 | `controls.pickBlock` | client | Pick block selects the block's item, of the same kind first; nothing for a mob (`PickBlock`) |
| 5 | `multiplayer.refusedBreaks` | server | A break refused for distance is answered with the block (within 16 blocks) |
| 15 | `multiplayer.mobPhysics` | client | A remote mob's own move still runs (ground, collisions, steps) but leaves it where the server put it |
| 16a | `multiplayer.trackAfterTeleport` | server | Viewers chosen by where an entity is, not where its viewers last saw it; a new viewer brings the next position packet forward as an absolute one |
| 16b | `multiplayer.chunkOrder` | server | Chunks waiting to be sent are ordered nearest first whenever the view re-centres |
| 36 | `multiplayer.miningDelay` | client | The pause after a break counts down and clears on letting go, as single player's |
| 48 | `multiplayer.signUpdates` | server | Block entities sent with a multi-block change are read from the changed positions |
| 49 | `multiplayer.respawnEntities` | both | Server: what a dead player saw is noted and dropped from their client on respawn (`StaleEntities`). Client: on a respawn from death in the same dimension, every entity from the server is dropped (it sends what's in range again) |
| 50 | `multiplayer.turns` | client | A living entity's last rotation from the server stays the target for moves that carry none (`TurnTarget`) |
| 55 | `doors.loneTop` | world | A door's top half passes a redstone update down only to its own bottom half |
| 56 | `falls.waterLandings` | world | A landing makes baseTick's water test again (without its push) and lands nothing in water |
| 57 | `controls.sneakCorners` | client | Sneaking, the step is also trimmed while its two parts together have nothing under them |
| 58 | `multiplayer.explosionPush` | server | An explosion's push on a player is marked for sending, as a fresh hit's is |
| 59 | `mobs.headNod` | world | A mob turning its head steps from its flipped pitch |
| 60 | `multiplayer.tpFall` | server | /tp clears the fall the player had built up (only /tp, not the server's own teleports back) |
| 61 | `multiplayer.drowningHeight` | server | Whether a player's eyes are in a fluid is measured 1.74 above the feet, as clients and single player measure |
| 62 | `multiplayer.spawnerMobs` | both | Server: a spawner's update packet carries its entity id (`SpawnerMobs`). Client: that id shown in the spawner |
| 64 | `spawning.besideWater` | world | The fluid-in-a-box test reads only the box's own blocks west and north of 0, 0 too |
| 66 | `spawning.squidInWater` | world | A water creature's place must be water, not any fluid |
| 67 | `blocks.fallingSandKeepsKind` | world | Falling sand clears a block of its kind on its first tick only; a server sends kept blocks again |
| 74 | `movement.stepUnderRoof` | wherever the entity moves: a player's client; mobs in the world | A step-up comes down as far as it rose, not always the whole step height; the same number with nothing overhead |
| 75 | `movement.boxAgainstWalls` | both; every entity | `setPosition` moves a side face that lands just past a sixteenth (within 1e-7) back onto it; the position itself is left alone |
| 70 | `render.seams` | client | Each render chunk drawn under its own translation, its corner minus the camera in double precision, rounded once; the chunk's list keeps only beta's scale about its middle (`ChunkTransform`) |
| 72 | `render.textureEdges` | client | While a chunk is built, a quad's texture coordinates lying exactly on a tile edge at either end of its range move a thousandth of a texel inward (`TextureEdges`) |

"World" means wherever the world runs: the server in multiplayer, the client in single player (beta has no
integrated server). In multiplayer a server without the mod keeps beta's behaviour for those whatever the client has.
#9 and #10 change mining speed, which both sides compute: the client times the break and the server checks it. A
vanilla server refuses a break reported much sooner than its own timing, finishes it later itself, and meanwhile drops
any second early report (the block vanishes, then comes back 4 s later). So a server with the fix says so in its login packet (`ServerMarker`: the
name field, which beta's server sends empty and its client ignores), and a client mines faster only on such a server;
elsewhere, and on a server whose switch is off, it keeps beta's speed. A vanilla client on a fixed server mines at
beta's speed, which the server accepts. #38 runs for every entity on both sides (it only changes when steps play).
#27 runs where each side places the rider; the client's own placement is what the player feels, so a fixed client on a
vanilla server stays on the boat, and the server accepts the slightly higher position. #45 is decided by the server in
multiplayer; a client with the fix only predicts it, and the server's answer corrects the inventory either way.
The multiplayer ones (2026-11-02) need nothing from the other side, so no marker: a server-side fix helps vanilla
clients on that server (they only receive packets vanilla's server could send: a block update, a sign, an absolute
position, a destroy, the same chunks in another order), and a client-side one works on any server, since each only
changes how the client shows what the server sent (#15, #50) or when it asks (#36: a vanilla server checks only each
block's own mining time, which is unchanged). #49 is fixed on either side alone. Every mix is run by
`fixesMultiplayerTest` (Tests, below).
#62 needs both sides but no marker: a fixed server's spawner packet is a sign packet at the spawner's position, which a
vanilla client applies only where it has a sign, so it ignores it (the spawner stays a pig there); a fixed client on a
vanilla server never receives one. #67's server sends a kept block again because a vanilla client's copy of the
falling block still clears it in that client's world. #57 is the client's alone: the player's own client moves the
player, and a server repeating the move with beta's two trims finds the shorter step supported. #58, #60 and #61 are
server-only and change nothing a vanilla client can't take (a velocity packet, no damage, the same drowning height the
client already uses).
#74 and #75 need nothing from the other side and send nothing different. A server's player has no step height, so #74 on a
client changes only the client's own moves, which stay within what a vanilla server accepts, and on a server only its mobs. #75
changes a box, never a position, and rebuilds are each side's own: a fixed client is safe on a vanilla server's login and
corrections, and a vanilla client on a fixed server is as before. Why #75 sits in `setPosition` for every entity rather than
only at a load: `docs/bugs.md` #75.
#42 changes the save format only by adding `TileTicks` to chunks, which vanilla ignores: a world played with the fix
opens in vanilla (its pending ticks are lost there, as always), and a vanilla world opens with the fix (nothing to
restore). The multiplayer client's world keeps vanilla's queue; only the world that saves (the server, single player)
holds ticks.
With the fixes, Pure's hardening (`ServerContent`'s item probe) accepts slabs and dyes with bad data values, since they no longer crash: #33's names make them showable.

## Tests

- `./gw :oldfaithful-fixes:test`: `SliderSteps` (every percentage labelled as itself; which are reachable pixel by
  pixel at GUI scales 1 to 6; the knob on whole pixels); `ServerMarker`. Seconds.
- `serverTest`, `clientAudit` and `clientAuditSdl` carry the mod, so every one of its mixins is applied and audited.
- `./gw :harness:clientStartupTestFixed`: the unseen start-up test with Pure's mods and this one, then `FixesCheck`
  in the running world, each scene set up away from the player: water on a torch (#19), a furnace on a lava bucket
  (#11), an overkilled big slime (#25), a killed saddled pig (#29), leaves cut with the shears' last use (#8), a
  wooden slab mined by hand tick by tick (#9: 62 ticks, beta 201), every block's fast tools against #10's table
  through the game's own multiplier, a crafting table mined with a wooden axe (#10: 39 ticks, beta 76), slab and
  dye names with bad data (#33), a door beside a repeater switching on (#41), a redstone torch with burnout records
  from a later clock (#44), torches shift-click crafted into room for half of them (#45), pick block on redstone,
  red wool and a mob (#46), a minecart rolling at a dropped item (#31), a fish reeled in level with the feet (#34),
  and last the player in a boat and out of it, the boat nudged to a height where beta's rounding drops a rider (#27;
  on vanilla the player fell 3.5 blocks). Also the player sneaking diagonally off an inside corner, through its own
  move (#57; on vanilla it steps over the gap), a step across a trench under a low roof (#74) and a box rebuilt a rounding step
  past a wall (#75). Each comes out the other way on vanilla (the new ones checked 2026-11-02
  with their mixins off). About 25 s; output in `local/run/client-startup-fixed/`.
- `./gw :oldfaithful-fixes:serverTicksTest` (#42): the real dedicated server with this mod and a test-only scenario
  mod (`src/scenario`). A repeater clock is built 400 blocks east of the spawn once the new world's generated liquid
  ticks have drained, counted running, the server saved and stopped; started again, the clock must be there and run on
  (40 switches in 80 ticks); then the same world on a server without this mod must load without errors, the clock
  there and standing still. About 45 s; each run in a folder of its own under `local/run/fixes-server-test/`. (It runs
  with the Nether on: with `allow-nether=false` the vanilla server resets the world's time and spawn on every restart,
  `docs/bugs.md` #52. With it on, the spawn still moves to the Nether's after the first stop, so the clock, once beside
  the spawn, sometimes fell outside the area loaded at start; the scenario read the unloaded chunk there
  as air and the test failed (once in seven runs, 2026-11-02; the clock was whole on disk). Its chunks are now loaded explicitly, and the test checks
  the clock is in place before counting.)
- `ServerWorldFixesTest` (run with `serverTicksTest`, about 45 s more): the world fixes from the piston hunt on the
  real server, with this mod and then without it (each must show beta's bug there), each in a new world, by the
  scenario mod's `WorldFixes`: a lone door top on a chest beside a new redstone torch (#55), pigs dropped from 12 to 31
  blocks into one-deep water (#56), a pig turning its head to something 45 degrees up (#59), pigs on grass with water
  beside their west or north side either side of 0, 0 (#64), the squid's place test on lava and water (#66), a falling
  sand some ticks into its fall in a cell of sand (#67), and on a zombie through its own move: 400 walks across a trench
  under a roof and in the open (#74: 0 sank with the fix, 64 without; the open air identical), 1000 walks to a wall past x 1024,
  then written and read (#75: 0 left inside, 10 without).
- `./gw :harness:fixesMultiplayerTest` (2026-11-02): the multiplayer fixes on the server package, in all four mixes:
  a server with and without this mod, each joined by the real client (hidden on a private desktop, as the start-up
  test) with and without it. The client (`MultiplayerCheck`) joins a scene the self-test mod builds it high above the
  terrain (`MultiplayerScenes`, which also takes "!oftest" chat commands for what only a server can do): two pigs in a
  one-block pen sampled for drift (#15), three dirt blocks mined as the game's mouse handling would (#36), a raw-client
  player turned 45 degrees at a time by the server (#50), then it dies, a pig is removed meanwhile, it respawns (#49).
  Raw-packet clients check the server side after the first client: a finish of mining 10 blocks away (#5), a console
  /tp to a player 130 blocks off (#16a, and the order of the chunks after it, #16b), a sign edited in the same tick as
  a block beside it (#48), a player told on respawn that one who left while they were dead is gone (#49), an explosion beside a player hurt a
  quarter second earlier (#58: the push is sent), a player 10 blocks into a fall moved by the console's /tp and landing
  (#60: no damage), a player standing 20 s where the water's surface lies between the two eye heights (#61: no
  drowning). And on joining, the hidden client reads a cow spawner 20 blocks off (#62: a cow only where both sides have
  the fix). Where the
  side that matters lacks the fix, each check expects beta's bug instead (all seen: pigs 0.06 to 0.15 blocks off, 5
  dead ticks, turns 15 to 30 degrees short, the pig left frozen, no block, no spawn, the west edge first, no sign
  text). About 6 minutes; `-Pfixesmp.rawOnly=true` runs only the raw-client part (about 3 minutes). Runs in
  `local/run/fixes-multiplayer-test/`.
- `./gw :harness:seamCheck` (#70, #71, #72; Claude Opus 5.5, 2026-11-03): a measurement, not a pass/fail test (`SeamCheck`).
  The hidden client (Still Faithful's mods by default; `-Pseams.mods=pure|fixed`) builds a sealed room high in the air
  at each of three places (`-Pseams.at`, default x and z 20, -76 and 708: near the origin, west and north of it, and
  far into a 1024-block region), walls two thick, pillars inside, chunk seams across every surface. Five views, each
  held for 32 frames while the camera creeps (about 1/80 block and 1/20 degree a frame); each frame's depth is read
  back before the hand and unprojected through the camera's matrices: a pixel lying outside the room (or on the far
  plane) is a hole, placed on the wall its ray crossed and classed as on a chunk seam, on another block edge (by
  surface and direction) or inside a face. `-Pseams.opaque` makes the atlas's transparent texels opaque first;
  `-Pseams.crack` measures the cracks against their block instead (#71): a floor block looked down at from nine
  places, diagonal to almost straight down, each camera position drawn without and with the cracks (their tiles made
  opaque), counting top-face pixels whose depth the cracks didn't change (lost to the block).
  `-Pseams.fixes="render.seams=false"` and `-Pseams.polish="sky.dusk=beta"` write switches. Results are appended to
  `local/run/seam-results/seams.txt`, with each view's worst frame (holes magenta) and first frame (for comparing runs
  pixel by pixel). About 6 minutes.
- `:harness:ledgeHunt` (`docs/building.md`) is the measurement behind #74 and #75, with each switch on and off (`docs/bugs.md`).
- Not checked by a test: #7, #39, #38, #30 and #34's hotbar, riding poses and death screen, and how #59's heads look
  (what's seen and heard: the user's check), #67's resending of kept blocks to a vanilla client (reasoned: a vanilla
  client's copy of the falling block clears them; not played), #40's mouse (the logic is the unit test), #26 (needs a sleeping player and a monster's path; reasoned in the mixin's comment), #8 on a real
  server (the same change as single player's, in the server's method; audited), #9 and #10 between a real client and
  server (the marker and the gating are audited and reasoned, not played), #75 on a multiplayer login beside a wall (the same
  rebuild; `fixesMultiplayerTest` shows the mixes still work, not that scene).

Old Faithful's options and comforts that aren't fixes (the FOV slider, GUI scale, Quit Game, the window) are in their
own mod, `oldfaithful-options` (`docs/options.md`).
