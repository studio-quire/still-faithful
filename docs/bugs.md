# b1.7.3's own bugs: the catalogue (phase 5, the Fixed edition)

Claude Opus 5.5, 2026-11-02. Second pass, then the first fixes. Purist leaves all of it alone except where an entry
says it may be the user's call to take into Purist. Old Faithful (Fixed) is narrow (user, 2026-11-02): beta as it was
meant to work, its clear mistakes fixed, and anything players built around left as beta (at most a setting that
defaults to beta). What later versions changed, the **ask** entries, belongs to the polished edition, Still Faithful,
not to Fixed. Fixed so far: forty-six entries in `oldfaithful-fixes` (`docs/fixes.md`), with forty-nine switches between them (#34 has four) in `config/oldfaithful-fixes.properties`; lighting (#3) in `oldfaithful-lighting` (`docs/lighting.md`).

Sources:
- The decompiled game (`local/decomp/b1.7.3-named`, Babric's names), read for causes.
- For the list: the bugfix sections of UniTweaks, RetroTweaks, MojangFix(-StationAPI) and LegacyFix
  (`local/audit/`), read for what they fix, never for how.
- The Minecraft Wiki's pages for Beta 1.7, 1.7.3 and 1.8 (what 1.8 fixed or changed is the best list of what 1.7.3
  got wrong).
- Mojira barely covers this version: the tracker opened after 1.0, and beta-era reports lived on forums that are
  mostly gone. So the wiki stands in for it, and the multiplayer and piston entries are thinner than they'd be with
  a real tracker. The wiki's per-topic pages (mobs, redstone, doors, rails, farming) were only reached through the
  version pages' change lists.
- A read-only bug hunt (2026-11-02) through redstone, doors, rails, crops, containers, entity saving and the
  multiplayer tracker and chunk map: #41 to #51, and the multiplayer entries traced.
- The piston hunt on the real server (2026-11-02, below): #53 to #55 and #67; and a read of movement, damage, spawning
  and generation, checked on the server where cheap: #56 to #66.

Key:
- **Status**: **T** = cause traced in the decompiled code here (class/method given); **L** = listed by a fix mod,
  the wiki or wide knowledge, cause not traced (the entry says so).
- **Kind**: **bug** (clearly wrong), **quirk** (part of beta's feel: a toggle, default as beta), **ask** (what later
  versions changed and players may have built around: the user's call; Old Faithful leaves it as beta), **speed**.
- **Ed.** (edition): **Pure** = Pure Faithful, so every edition (worlds lost, crashes, security, speed with identical
  pixels); **OF** = Old Faithful (a clear mistake); **SF** = Still Faithful (an ask); **none** = a quirk, beta's
  feel. "OF / SF" splits an entry: its bug part and its ask part.
- **Impact**: how much players notice: high (most players, or loses their work), mid (many, or a regular annoyance),
  low (few, or cosmetic and rare).
- In the table, **fixed** means in Pure Faithful (so in Old Faithful too); **fixed (OF)** means in Old Faithful's
  `oldfaithful-fixes` (or, for #3, `oldfaithful-lighting`). **built (SF)** means in Still Faithful's own mod (`stillfaithful-polish`, released
  from 0.1.0).

## Ranked summary

| # | What | Kind | Ed. | Impact | Status |
|---|---|---|---|---|---|
| 1 | A chunk that fails to load is silently regenerated, erasing what was built there | bug | Pure | high | fixed |
| 2 | Chunk saves overwrite in place, and an oversized chunk is silently never saved | bug | Pure | high, rare | fixed |
| 3 | Lighting errors: generated lava lights nothing; dark pockets under overhangs and at chunk edges | bug | OF | mid | fixed (OF) |
| 4 | Farm animals despawn: pens empty while you're away (all but tamed wolves) | ask | SF | high | built (SF) |
| 5 | Multiplayer ghost blocks: a break the server refuses for distance is corrected only 4 s later | bug | OF | low-mid (MP) | fixed (OF) |
| 6 | Dusk and dawn: every step of daylight rebuilds the whole view (stutter, stepped darkening) | ask (speed, looks) | SF | mid-high | built (SF) |
| 7 | Grey sky while an inventory or any container is open | bug | OF | mid | fixed (OF) |
| 8 | Tools' last use: the block drops nothing (multiplayer: everything; single player: leaves cut with shears) | bug | OF | mid | fixed (OF) |
| 9 | Wooden slabs need a pickaxe to drop | bug | OF | mid | fixed (OF) |
| 10 | Wrong tools for many blocks (axes slow on crafting tables, fences, doors; pickaxes on furnaces, rails...) | bug | OF | mid | fixed (OF) |
| 11 | Furnace eats the lava bucket | bug | OF | mid | fixed (OF) |
| 12 | Boats break on bumping into things at speed | ask | SF | mid | built (SF) |
| 13 | Stairs drop their material (wood stairs: a plank; cobblestone stairs: one cobblestone) | ask | SF | mid | built (SF) |
| 14 | Autosave every 2 seconds on the game thread: a hitch every 40 ticks | ask (speed) | SF | mid | built (SF) |
| 15 | Multiplayer mob jitter: the client runs its own physics on remote mobs, on top of the server's positions | bug | OF | mid (MP) | fixed (OF) |
| 16a | Multiplayer: a player who teleports or comes through a portal stays invisible to players already there | bug | OF | mid (MP) | fixed (OF) |
| 16b | Multiplayer: after a /tp or a fast move, chunks arrive row by row, not nearest first: seconds over air | bug (order), ask (holding still) | OF / SF | low-mid (MP) | order fixed (OF); lasting holes L |
| 17 | Advanced OpenGL: chunks flicker out of view | bug | OF | mid | L |
| 18 | Screenshots fail after the window is enlarged | bug | Pure | mid now | fixed |
| 19 | Water flowing down destroys torches, rails and redstone without dropping them | bug | OF | low-mid | fixed (OF) |
| 20 | Lava lingers without its source; water sources never form above water | ask | SF | low-mid | built (SF) |
| 21 | Far Lands | quirk | none | low-mid | L |
| 22 | Jitter and stretched textures far from the origin (float precision) | bug | OF | low-mid | L |
| 23 | Piston crashes: out-of-range data crashes; the piston hunt found one in normal play (#53) | bug | OF | low | T |
| 24 | Player-placed leaves decay unless a log is within 4 | ask | SF | low-mid | built (SF) |
| 25 | Slimes don't split when killed by a big hit | bug | OF | low | fixed (OF) |
| 26 | Mobs that wake you can reach you through a wall | bug | OF | low-mid | fixed (OF) |
| 27 | Falling through the boat when getting out (boats whose top is just under y 64, 32, 16...) | bug | OF | low-mid | fixed (OF) |
| 28 | Sleeping view: listed as turned the wrong way; traced, the first-person view is the bed's own and ignores the mouse | not found | none | low | T |
| 29 | Saddled pigs don't drop the saddle | bug | OF | low | fixed (OF) |
| 30 | Fences: dark bottom face; hitbox too big (the "smart farm" trick) | bug, ask | OF / SF | low | fixed (OF): the face; hitbox L |
| 31 | Minecarts: stopped by items and arrows; a slip in cart-on-cart collisions | bug, ask | OF / SF | low-mid | fixed (OF): items; the slip built (SF) |
| 33 | Crash on a slab or dye item with a bad data value | bug | OF | low | fixed (OF) |
| 34 | Small visual bugs (list in the entry) | bug, ask | OF / SF | low | fixed (OF): four; the rest T or L |
| 35 | Eating at full health wastes the food | ask | SF | low | L |
| 36 | Multiplayer mining delay: the 5-tick cooldown never resets; the next block waits for the click's repeat | bug (reset), ask (tempo) | OF / SF | low-mid (MP) | reset fixed (OF) |
| 37 | Skeletons circle the player, hard to hit | quirk | none | low | L |
| 38 | Footsteps saved up going down stairs, then played in a burst at the bottom | bug | OF | mid | fixed (OF) |
| 39 | The item on the cursor draws behind the player model and the screen's labels | bug | OF | low-mid | fixed (OF) |
| 40 | Option sliders can't reach every percentage | bug | OF | low | fixed (OF) |
| 41 | Repeaters never update the doors, trapdoors, note blocks, dispensers, TNT or rail junctions they power | bug | OF | mid | fixed (OF) |
| 42 | Pending block ticks are never saved, and are dropped near the loaded edge: repeater clocks stop on reload | bug | OF | mid | fixed (OF) |
| 43 | Breaking a chest, furnace, dispenser or jukebox with no block entity crashes; Pure's saves fix can leave such blocks | bug | Pure | low odds, a crash | fixed |
| 44 | Redstone torch burnout memory never expires if time goes back: torches burn out for good | bug | OF | low-mid | fixed (OF) |
| 45 | Shift-clicking a crafting result into a nearly full inventory loses what doesn't fit | bug | OF | low | fixed (OF) |
| 46 | Pick block on redstone, repeaters, doors, signs, wheat, beds or cake selects nothing; on a mob, stone | bug | OF | low | fixed (OF) |
| 47 | Double slabs always mesh their top face (a missing `return`) | speed | Pure | low | fixed |
| 48 | Multiplayer: a sign edited in the same tick as another block change in its chunk shows blank to others | bug | OF | low-mid (MP) | fixed (OF) |
| 49 | Multiplayer: after a respawn, entities near where you died can stay frozen on your screen | bug | OF | low-mid (MP) | fixed (OF) |
| 50 | Multiplayer: other players turn in steps and can stay up to about 11 degrees off | bug (yaw), ask (threshold) | OF / SF | low (MP) | yaw fixed (OF) |
| 51 | Multiplayer: a server movement check (details kept private) | ask | none | low | T |
| 52 | Server with `allow-nether=false`: every restart resets the world's time and weather, and a new world's spawn (level.dat overwritten by the unticked Nether's); with the Nether on, its spawn and weather still win, and a restart undoes sleep | bug | Pure / OF | mid (MP) | fixed (Nether off); T |
| 53 | A crash involving pistons near the edge of the loaded area (details kept private) | bug | Pure | low odds, a crash | fixed |
| 54 | Pistons: an interaction that can lose blocks (details kept private) | bug | none (left as beta) | low-mid (redstone builds) | T |
| 55 | A door's top half on its own passes redstone updates to the block below as if it were the door's bottom: that block is deleted and a door dropped | bug | OF | low | fixed (OF) |
| 56 | One-deep water breaks only some long falls: the landing tick isn't checked for water, so most heights hurt as on stone | bug | OF | mid | fixed (OF) |
| 57 | Sneaking doesn't stop a diagonal step off an inside corner | bug | OF | low-mid | fixed (OF) |
| 58 | Multiplayer: an explosion doesn't push a player who was hurt in the last half second (or just joined or respawned) | bug | OF | low-mid (MP) | fixed (OF) |
| 59 | Mobs nod their heads every tick when looking up or down at you | bug | OF | low | fixed (OF) |
| 60 | Multiplayer: a /tp keeps the fall the player had built up, and lands it as damage | bug | OF | low (MP) | fixed (OF) |
| 61 | Multiplayer: the server measures drowning from 1.62 above the feet, the client from 1.74 | bug | OF | low (MP) | fixed (OF) |
| 62 | Multiplayer: every mob spawner shows a spinning pig | bug | OF | mid (MP) | fixed (OF) |
| 63 | Slimes spawn with no check for room: big ones in walls, inside each other | bug, ask (farm rates) | OF / SF | low | T |
| 64 | West and north of 0, 0, mobs won't spawn beside water or lava on their west or north side | bug | OF | low | fixed (OF) |
| 65 | A spawner set to a thing that isn't a mob (an edited world) crashes the game every tick a player is near | bug | Pure | low (edited worlds) | fixed |
| 66 | Small spawning and generation slips: squid in lava, Nether chunks without a heightmap, the Nether's decoration unseeded, clay beside the water it tested | bug, ask | OF / SF / none | low | squid fixed (OF); the Nether's heightmap T |
| 67 | Falling sand or gravel deletes any block of its own kind it passes into (a piston pushing it into a column, say) | bug | OF | low | fixed (OF) |
| 68 | Single player: a rejoin prepares the terrain around the world's spawn, not around the player. Nothing a player notices; left as beta. The suffocation reported with it may well be #75's | bug | none | none | T |
| 69 | Chests can't be turned: the front is worked out from the blocks around (south when free-standing, sideways in a corner) | ask | SF | mid | built (SF) |
| 70 | Dots of sky flicker along block edges where render chunks meet: each chunk is placed with a rounding error bigger than beta's overlap | bug | OF | mid | fixed (OF) |
| 71 | Screen-aligned lines flicker over the block-breaking cracks (seen by the user); the cracks never lose depth to their block in measurement | ? | ? | low-mid | not reproduced; set aside (user) |
| 72 | Dots of sky along some edges of grass, stone, dirt, planks, bricks, wool and others: a face's texture starts exactly on its tile's edge, and a hair of rounding reads the transparent tile beside it in the atlas | bug | OF | mid | fixed (OF) |
| 73 | Create New World and Rename World: in a big window the title comes down among the fields (seen by the user) | bug | OF | low | fixed (OF) |
| 74 | Under a block 2 above the floor, a step-up sinks the player 0.3 into the floor or a ledge, then the push-out shoves them out | bug | OF | mid | fixed (OF) |
| 75 | Rejoining while touching a wall can put the box a rounding step inside it: the player walks in, suffocates if the wall reaches the eye, and is pushed out | bug | OF | low-mid | fixed (OF) |

#32 (video settings sliders can't be dragged) left the table: vanilla has no slider there (its entry).

## Already handled by Purist work

- AMD z-fighting ("bit depth"; Beta 1.8 went to a 24-bit depth buffer): our LWJGL layer asks for at least 24 bits
  (`Window.create`).
- Controller start-up freeze (jinput): our `Controllers` reports none.
- Fullscreen, alt-tab, high DPI, resizing, paste, mouse feel: `oldfaithful-display` and our LWJGL layer.
- Skins, capes, logins, sound downloads: `oldfaithful-hardening`.
- For phase 6 (multiplayer) rather than here: the client's artificial delay in packet processing and the missing
  TCP_NODELAY (listed by UniTweaks).

## Beta design, not bugs (no entry)

Changed later on purpose and part of what beta is: zombies drop feathers, arrows vanish on hitting a mob, no hunger,
taiga with snow, wheat seeds from grass. If a Fixed edition wants "what it should have been", these belong to the
later transformative packs, not to bug fixing. Also not a bug in vanilla: #32 (its entry).

Looked at by the bug hunt and left out: `PortalForcer`'s second search loop lacks the first's `y > 0` guard, but only a
column of air down through y 0 could make it spin. Wolves heal by raw pork's value whatever meat they're fed
(`WolfEntity.interact`): maybe deliberate, an SF note at most. Trees growing over glass, torches and chests beside
saplings (`OakTreeFeature`'s canopy check is a layer off) lasted until well after beta.

## Possibly the user's call for Purist too

Purist fixes what broke because hardware and systems moved on. Three entries sit on that line:
- **1 and 2 (save safety).** Not caused by modern machines, but they lose players' worlds, and a hardening pack that
  leaves known data loss in place may not read as "hardened".
- **18 (screenshots).** The bug is beta's, but our resizable window makes it easy to hit.

## Entries

### 1. Failed chunk load regenerates the chunk (T)
Fixed in Purist, 2026-11-02: `oldfaithful-saves` (`docs/saves.md`).
`ChunkCache.loadChunkFromStorage` catches any exception from `RegionChunkStorage.loadChunk` and returns null;
`ChunkCache.loadChunk` then asks the generator for a fresh chunk, which is saved over the old one at the next save.
Same for a chunk record missing `Level` or `Blocks` (`RegionChunkStorage.loadChunk` returns null). A truncated
record, a corrupt NBT tag or a block entity whose `readNbt` throws (`AlphaChunkStorage.loadChunkFromNbt` ->
`BlockEntity.createFromNbt`) is enough. Fix: on a failed load, copy the raw record aside (a `corrupt/` folder), and
load what can be read (drop only the bad block entity or entity, keep the blocks); regenerate only when nothing is
readable, and say so in the log. Risk: low; changes nothing in a healthy world.

### 2. Non-atomic chunk saves (T)
Fixed in Purist, 2026-11-02, with level.dat, player and map files: `oldfaithful-saves` (`docs/saves.md`).
`RegionFile.writeChunkData`: a chunk that still fits its sectors is rewritten in place, so a crash or power cut
mid-write leaves a corrupt record (then entry 1 regenerates it). A chunk needing 256 sectors or more (1 MB compressed)
returns without saving and without a word. Nothing is synced to disk. Fix: write the new record to free sectors, then
update the header (old data stays valid until the header points elsewhere); log oversized chunks and store them
anyway in a side file; sync on world save. Risk: low-moderate (region format must stay readable by vanilla; the
format allows this). Interaction: entry 14's cadence.

### 3. Lighting errors (T)
Fixed in Old Faithful, 2026-11-02: `oldfaithful-lighting`, a mod of its own (`docs/lighting.md`, with the causes in
full). Measured, not guessed: a checker computes b1.7.3's own light rule from scratch for a saved world and compares
every cell. On seed 404 with explosions, lamps and chunks loaded in scattered orders, the vanilla server left 492 sky
and 12,017 block light cells wrong in 919 overworld chunks, and 34,234 block light cells in 515 Nether chunks; with the
mod, none, and a world made without it is put right as its chunks load. The causes, traced: a new chunk's own light
requests are dropped because it isn't in the chunk map yet (`queueLightUpdate`'s `isPosLoaded`; decoration redoes most
by accident); generated lava never lights anything (`populateBlockLight` is empty), by far the largest part, in caves
and the Nether's lava seas; `LightUpdate` skips columns whose diagonal chunk is missing and nothing returns for them;
light falling behind water or ice can leave it too bright (`World.updateLight`'s shortcut); the queue is thrown away
past a million; a full save keeps light half done. Not causes: the 50-deep cap can't be reached, and the 500 a call
only paces the work. What players see change: underground lava lakes and the Nether's lava seas light their
surroundings (fewer hostile mobs spawn right beside them), and the rare dark pocket fills in. A first version queued its
fixes behind vanilla's 500-a-frame queue, so in single player far lava stayed dark until the player came near (the
user, 2026-11-02); now what a loading chunk needs is lit at once (`docs/lighting.md` > Distant lava stayed dark). The original "high,
never heals" impact was the catalogue's guess; fresh worlds measured show mostly the lava, so "mid". Generated lava's
light has a switch, on by default (the user's call, 2026-11-02): `lava.lightGenerated` in
`config/oldfaithful-lighting.properties`; off leaves it dark as beta did, every other fix staying on
(`docs/lighting.md` > The switch).

### 4. Farm animals despawn (T, ask)
`LivingEntity.tryDespawn`: any mob more than 32 blocks from the nearest player, once 600 ticks have passed since it was
last hurt, has a 1 in 800 chance a tick of vanishing, and beyond 128 blocks vanishes at once (while its chunk is
loaded); `canDespawn()` is true for every mob except a
tamed wolf (`WolfEntity`). So penned cows, pigs (saddled ones too), sheep and chickens disappear whenever the player
wanders off. Beta 1.8 stopped passive mobs despawning. Players rebuilt farms around this for years; whether it's a bug
or beta's design is the user's call (a fix: passive mobs never despawn, or only naturally spawned ones away from
players' builds; Beta 1.8's way pairs with its changed animal spawning). **Built in Still Faithful** (2026-11-02,
`animals.keepTended`): animals a player has dealt with stay (`docs/still-faithful.md` says what counts).

### 5. Ghost blocks in multiplayer (T)
`ServerPlayNetworkHandler.handlePlayerAction`: a start or finish of mining more than 6 blocks (squared distance 36)
from the player's position returns without a `BlockUpdateS2CPacket`, while the client has already removed the block
(`MultiplayerInteractionManager`). The client then shows air where the server has a block (walk into it, fall
through, get pushed back) for about 4 s, until it undoes the unconfirmed change (`ClientWorld`'s block resets, 80
ticks). Fix: send the block update back whenever the server refuses. Risk: none. Fits phase 6 too.
**Fixed (OF)**, `multiplayer.refusedBreaks` (server): a start or finish refused for distance is answered with the
block, within 16 blocks (vanilla's own reach for answering an aborted break). Not a Pure matter: it costs a moment's
hole on screen, no safety or world state.

### 6. Dusk and dawn rebuild the whole view (T, ask)
`World.updateSkyBrightness` -> `notifyAmbientDarknessChanged` -> `WorldRenderer.notifyAmbientDarknessChanged` marks
every sky-lit render chunk dirty whenever the ambient darkness steps (about eleven steps at each dusk and dawn), since
sky brightness is baked into the chunk meshes. Each step re-meshes the whole view: a wave of chunk rebuilds (a hitch,
and with a far view the darkening sweeps across over seconds), and the light changes in visible steps. Beta 1.8 made
the cycle smooth with no chunk updates (a lightmap). Fix: apply sky brightness at draw time (a lightmap or a
per-vertex sky term), so no rebuild is needed. It changes how dusk looks (smooth instead of stepped), so not a pure
speed win: the user's call, Fixed edition, possibly a toggle. Interaction: phase 4 (chunk building throughput; the
vsync chunk budget). **Built in Still Faithful** (2026-11-02, `sky.dusk`): light applied when the chunks are
drawn, exactly beta's at each step (checked pixel by pixel), no rebuilds; needs OpenGL 2.0. Smooth by default, its fog following
the terrain (the user's call after playing both, 2026-11-03); stepped (beta's steps, the whole view at once) or beta's
rebuild as choices.

### 7. Grey sky while a container is open (T)
`HandledScreen.render` ends with `glEnable(GL_LIGHTING)` (and depth test). The next frame `GameRenderer.renderFrame`
draws the sky (`WorldRenderer.renderSky`, at Far and Normal) before anything calls `Lighting.turnOff`, so the sky is
lit by the fixed-function lights with the normal `applyFog` sets (pointing down): only ambient reaches it, and it
reads grey. Every frame while the screen is open, and the first frame after it closes. Fix: disable lighting before
the sky (or not leave it on after the screen). Risk: none. Interaction: `renderFrame` is where phase 4's chunk budget
lives (separate lines).
**Fixed in Old Faithful** (2026-11-02, `sky.afterContainer`): lighting off at the start of `WorldRenderer.renderSky`
(renderSky turns it off itself just after the dome; in any other frame it's already off there).

### 8. Last durability point: no drop (T)
`ServerPlayerInteractionManager.tryBreakBlock` clears the broken tool from the hand before checking
`player.canHarvest`, so in multiplayer the block mined with a tool's last use (stone, ore, obsidian) drops nothing.
`SingleplayerInteractionManager.breakBlock` checks `canHarvest` first, so single player only loses drops decided
inside `afterBreak` from the hand: leaves cut with shears (`LeavesBlock.afterBreak`). (Snow with a shovel was listed
here too; traced, snow's drop needs only `canHarvest`, so single player keeps it.) Fix: decide drops before removing
the tool. Risk: none.
**Fixed in Old Faithful** (2026-11-02, `tools.lastUse`), server and single player: the used-up tool stays in the hand
through the harvest check and `afterBreak`, then is removed.

### 9. Wooden slabs need a pickaxe (T)
`SlabBlock` is `Material.STONE` for every variant, and stone material needs a pickaxe to drop, so wooden slabs mined
by hand or axe vanish. Fix: wooden variant harvestable without a tool, axe effective. Risk: low (one block, metadata
dependent).
**Fixed in Old Faithful** (2026-11-02, `slabs.wooden`). Wider than it looked: the game asks harvest and mining speed of
the block alone (`canHarvest(Block)`, `getHardness(PlayerEntity)`), never the data value, and stone material also made
wooden slabs mine at under a third of planks' speed by hand. So the fix answers at the seven places that know the
position (both client interaction managers' start and progress, the server's three timing checks) and at the two
harvest checks: a wooden slab harvests as planks and mines at the faster of planks' speed and its own, so a pickaxe
keeps its stone speed. Both sides; in multiplayer the client mines faster only on a server with the fix (#10's marker).
Their stone sound stays (beta's; not a clear mistake on its own).

### 10. Tool effectiveness gaps (T)
The user's call (2026-11-02): every mismatch between what a block is made of and the tool that mines it fast is a bug;
obsidian and redstone ore keep beta's speed (below).
How beta decides (traced): a break's progress per tick is `Block.getHardness(player)`: without `canHarvest`,
`1 / hardness / 100`; with it, `PlayerEntity.getBlockBreakingSpeed / hardness / 30`, the speed being the held item's
`getMiningSpeedMultiplier` (divided by 5 in water and by 5 in the air). `ToolItem.getMiningSpeedMultiplier` returns the
tool material's speed (wood 2, stone 4, iron 6, diamond 8, gold 12) only for blocks on a hand-written list in
`PickaxeItem`, `AxeItem` and `ShovelItem`, else 1. Harvest (`PlayerInventory.isUsingEffectiveTool`) is separate: any
block whose material doesn't require a tool drops by hand, otherwise `isSuitableFor`, which for the pickaxe falls back
to material (stone or metal). So a pickaxe was *required* for a furnace and then mined it at hand speed. Swords (1.5
on everything, 15 on cobwebs) and shears (15 on leaves and cobwebs, 5 on wool) have no gaps.

The table: what each tool mines fast, by block id. **Beta** = its list; **added** = the fix.
- Pickaxe, beta: stone, cobblestone, gold/iron/coal/diamond/lapis ore, lapis/gold/iron/diamond blocks, sandstone,
  slabs (43, 44), mossy cobblestone, ice, netherrack. Added (stone or metal material, or stone/iron by substance):
  dispenser 23, powered and detector rails 27, 28, bricks 45, spawner 52, furnaces 61, 62, rail 66, cobblestone stairs
  67, stone pressure plate 70, iron door 71, stone button 77, glowstone 89 (stone material in b1.6.6 to 1.0), bedrock
  7 (no effect: unbreakable). Left at beta: obsidian 49, redstone ore 73, 74.
- Axe, beta: planks, log, bookshelf, chest. Added (wood material, or wooden by substance): note block 25, wooden
  stairs 53, crafting table 58, signs 63, 68, wooden door 64, ladder 65, wooden pressure plate 72, jukebox 84, fence
  85, pumpkin and jack-o'-lantern 86, 91 (pumpkin material; axe in later versions), locked chest 95 (hardness 0, no
  effect), trapdoor 96. Wooden slabs: #9's fix.
- Shovel, beta: grass, dirt, sand, gravel, farmland, snow, snow block, clay. Added (sand material): soul sand 88.
- Left alone, as no tool's substance: glass, sponge, cactus, cake, bed (wool material), pistons (their own material,
  mixed wood, stone and iron), lever (cobblestone on a stick), and everything with hardness 0 (torches, redstone,
  repeaters, plants).

Intent or oversight: the lists are hand-written id lists that grew as blocks were added (sandstone, lapis and
netherrack are on the pickaxe's; the golden tools' speed was raised in Beta 1.2) while blocks of the same material
from the same era or earlier never were (crafting table, furnace, doors); the pickaxe's harvest check
already reasons by material. Mojang listed it among fixes in Beta 1.9 Prerelease 6 (tool weaknesses fixed, crafting tables mining faster with an
axe; rails and redstone ore faster with pickaxes; obsidian, spawners, both doors faster), and 1.0.0-RC1 then raised obsidian's hardness to 50 to restore its slowness (Minecraft
Wiki's version pages). Obsidian's 15 s with a diamond pickaxe was a property players knew and built around; on the
list at beta's hardness 10 it would take under 2 s (modern: ~9.4 s at hardness 50), so it stays. Redstone ore's slowness
is well known too, and stays by the user's call.

**Fixed in Old Faithful** (2026-11-02, `tools.speedByMaterial`): `ToolItem.getMiningSpeedMultiplier` returns the tool's
speed for the added blocks (`ToolSpeeds`), so every place that times a break agrees. Mining speed only: which blocks need
a tool to drop is unchanged. Multiplayer: the server marks its login packet (`ServerMarker`) and a client mines at the
fixed speeds only on a server with the fix. A vanilla server checks a reported break against its own timing (at least
70% of it, `ServerPlayerInteractionManager.continueMining`); sooner, it finishes the break itself later, and while it
does, it drops any other early report outright: that block vanishes on the client and comes back 4 s later (the
client undoes a change the server hasn't confirmed, `ClientWorld`'s block resets), to be mined again. #9 now does the
same.
Harvest, possibly clear enough to fix (not changed): glowstone needs a pickaxe to drop at all since Beta 1.6.6 made it
stone material (so things could be placed on it); 1.0.0-RC1 put it back to glass material and said "a pickaxe is no
longer needed". **Built in Still Faithful** (2026-11-02, `glowstone.anyTool`): harvestable by anything,
glowstone staying stone material.

### 11. Furnace eats the lava bucket (T)
`FurnaceBlockEntity.tick` decrements the fuel stack and nulls it at zero; a lava bucket leaves no empty bucket.
Fix: leave an empty bucket in the fuel slot. Risk: none.
**Fixed in Old Faithful** (2026-11-02, `furnace.lavaBucket`): fuel that names a crafting remainder (only the lava
bucket: the bucket) leaves it in the fuel slot.

### 12. Boats break on collisions (T, ask)
`BoatEntity.tick`: any horizontal collision above 0.15 speed kills the boat and drops three planks and two sticks.
Players hated it, but it was how beta's boats worked, and later versions changed it on purpose. Options: keep, toggle
(default beta), or fix. The user's call. Traced further (2026-11-02): `Entity.move` zeroes the velocity of a blocked
axis, so the speed tested is what's left along the free axis: a boat sliding along a shore or an item at over 0.15
breaks, while one driven head-on into a wall at any speed doesn't (beta players learnt to land head-on).
**Built in Still Faithful** (2026-11-02, `boats.sturdy`, `boats.handling`; `docs/still-faithful.md`).

### 13. Stairs drop their material (T, ask)
`StairsBlock.getDroppedItemId/Count` delegate to the base block: wooden stairs drop a plank, cobblestone stairs one
cobblestone, from a recipe of six blocks for four stairs. Later versions drop the stairs. Bug or beta design: the
user's call. **Built in Still Faithful** (2026-11-02, `blocks.stairsDropStairs`).

### 14. Autosave every 2 seconds (T, ask, speed)
`World.tick` calls `saveWithLoadingDisplay(false, null)` when the world time is a multiple of `saveInterval` (40
ticks): level.dat plus up to 24 dirty chunks (`ChunkCache.save`), compressed and written on the game thread. A
periodic hitch while building or exploring. Moving saves off the thread or spacing them out changes timing and the
crash-loss window, so it isn't a pure speed win (phase 4 didn't take it). With entry 2 fixed, a longer interval is
safer. The user's call. **Built in Still Faithful** (2026-11-02, `saves.backgroundAutosave`): the same saves at the
same moments, compressed and written by a thread of their own, on top of the saves mod (`docs/still-faithful.md`).

### 15. Multiplayer mob jitter (T)
`LivingEntity.tickMovement` runs on the client for remote mobs. `interpolateOnly` (set in
`ClientNetworkHandler.onLivingEntitySpawn`) only skips the AI (line 674). Each tick still lerps toward the server's
position and then calls `this.travel(...)` (line 693), which applies gravity, velocity, friction and collision,
followed by the push loop. So the client simulates its own physics on top of the server's positions:
- penned mobs push each other apart on the client and get pulled back by the next packet;
- a jumping or falling mob is dragged under its target by the client's gravity;
- knockback is applied twice.

`OtherPlayerEntity` overrides `tickMovement` with the lerp alone, which is why other players don't jitter this way.
UniTweaks' "multiplayer entity physics fix" agrees: it drops client-side moves of remote living entities.

Fix: for `interpolateOnly` living entities, skip the motion in `travel` and the push loop. Client only; risk low
(knockback then shows only through the server's positions, slightly later).

Caution: `EntityTrackerEntry.notifyNewLocation` (line 111) tests `Math.abs(var2) >= 8 || ...` on the *absolute*
positions times 32, not on the deltas `var7..var9`. So every tracked entity sends a move packet every
`trackingFrequency` ticks even when it's standing still. That's a slip too, but it resyncs every client every 2 to 3
ticks and so hides part of the drift above. Fix it only together with the client change, and keep a periodic resync.
**Fixed (OF)**, `multiplayer.mobPhysics` (client): the client's own move still runs on a remote mob (on the ground or
not, what it touches, footsteps) but the mob is put back where the server's path has it. Measured on the real server
(`fixesMultiplayerTest`): two penned pigs, beta 0.07 blocks off the server's position, fixed exactly on it. The
tracker's "moved" slip is left as it is: fixing it on the server would take away the resync vanilla clients rely on
(their pigs would drift), and it costs only a few bytes a tick per entity.

### 16a. Invisible after a teleport (T)
`EntityTrackerEntry.notifyNewLocation` (lines 91-97) chooses who should see the entity (`updateListeners`) *before*
it sends the position and updates `lastX/Y/Z`. `updateListener` measures range from that stale `lastX`. So on the
tick a player teleports, their own entry picks viewers by the old position. Viewers at the destination are never
added, and the check doesn't run again until the teleporter moves 4 blocks (`> 16.0` squared).

The teleporter does see everyone else, because `EntityTracker.tick` uses the player's current position for the
other entries. The others don't see the teleporter until one of the two walks 4 blocks (or the teleporter relogs):
for players standing around a spawn or a meeting point, that can last a long time.

Dimension change has the same problem. `PlayerManager.changePlayerDimension` spawns the player into the new world,
which creates the tracker entry at the scaled coordinates, and only then does `PortalForcer.moveToPortal` move them,
up to 128 blocks. A respawn in the same dimension is fine: it creates a fresh entry at the right place.

A related offset: spawn packets for newly added viewers carry the current position, but the relative moves that
follow are counted from `lastX`. A moving entity that comes into view stays offset until the next absolute packet,
which can be up to 400 ticks away.

Fix: in the big-move branch, send the absolute position and update `lastX` first, then `updateListeners`; build
spawn packets from `lastX/Y/Z`. Risk: low.
**Fixed (OF)**, `multiplayer.trackAfterTeleport` (server), a little differently: viewers are chosen by where the entity
is, and adding a viewer makes the entry's next position packet absolute (vanilla's 400-tick resync, brought forward),
which also ends the offset. Checked with a console /tp on the real server; beta's server left the player invisible.

### 16b. Chunk holes after moving fast (T for the ordering; lasting holes L)
On the client, a pre-chunk packet makes a blank all-air chunk at once, and `MultiplayerChunkCache.isChunkLoaded`
always returns true, so the client never holds the player still over chunks it hasn't received; they fall. On the
server, chunk data trickles out at one chunk per received move packet, and only while fewer than 4 world packets
are queued (`ServerPlayerEntity.playerTick`).

On login, respawn and portals, `ChunkMap.addPlayer` queues chunks nearest first, in a spiral. But
`ChunkMap.updatePlayerChunks`, used for every same-dimension /tp or fast move, queues them row by row from the west
edge. At view distance 10, the chunk under the player is about number 220 of 441: at least 11 s over air.

Load/unload ordering, `removePlayer` against `addPlayer`, and dimension changes were checked: no path drops a chunk
for good, so a hole that lasts until relog (widely reported) is still unexplained. Tracing it further would need a
capture of one.

Fix: sort the pending chunks by distance in `updatePlayerChunks` (bug, low risk). Optionally hold the client player
still over chunks it hasn't received (ask: the server's flying check, `floatingTime > 80`, could kick a hovering
player).
**Order fixed (OF)**, `multiplayer.chunkOrder` (server): what's waiting is sorted by distance whenever the view
re-centres. After a /tp the first chunk sent is the one underfoot (beta's: the west edge). Holding still: Still
Faithful's.

### 17. Advanced OpenGL flicker (L)
With Advanced OpenGL on, chunks blink out at the edges of view when turning: occlusion query results lag the camera.
Not traced. Interaction: phase 4's culling and chunk work; any fix there must keep Purist's pixels where the option
is off.

### 18. Screenshots fail after enlarging the window (T)
`Screenshot.take` reallocates its pixel buffer only when `capacity < width * height`, but uses `width * height * 3`
bytes. After the window grows (say 1281x720 to a 1920x1080 maximise), `glReadPixels` overruns the buffer and the
screenshot fails. Vanilla's window could be resized too, but our resizable, HiDPI-scaled window makes it common.
Fix: compare against the size actually used. Risk: none. **Fixed in both editions** (user, 2026-11-02): `oldfaithful-display`'s
`ScreenshotMixin` drops a buffer too small for the read.

### 19. Water flowing down destroys without drops (T)
`FlowingLiquidBlock.onScheduledTick`: sideways spread goes through `spreadTo`, which calls `dropStacks` on what it
replaces; downward flow calls `world.setBlock(x, y - 1, z, ...)` directly, so torches, rails, redstone, flowers and
crops below a falling stream vanish. Fix: drop (or fizz, for lava) on the downward path as on the sideways one.
Risk: none (players used water to clear things either way).
**Fixed in Old Faithful** (2026-11-02, `liquids.dropsBelow`): the downward flow (`FlowingLiquidBlock.onTick`'s two
five-argument `setBlock` calls) drops or fizzes first, as `spreadTo` does.

### 20. Lingering lava; water sources over water (T, ask)
Same method. Lava: with 3 chances in 4 it refuses to thin out (`random.nextInt(4) != 0`), and when it refuses it
neither changes nor schedules another tick, so flowing lava cut off from its source can stay forever until something
next to it changes. Water: the check meant to make a new source above still water reads the current block's data
(`world.getBlockMeta(x, y, z) == 0`, never true for flowing water there) instead of the block below, so sources only
form over solid ground. Both are slips in the code, but both shape builds players made (lava lighting, water
setups), so: the user's call; toggles likely. **Built in Still Faithful** (2026-11-02, `liquids.lavaThins`,
`liquids.waterSources`): lava thins every time, waiting four times longer on beta's refusals, as later versions do.

### 21. Far Lands (L, quirk)
Terrain noise overflows past about 12.5 million blocks out. Part of beta's lore: a toggle, default as beta. Beta 1.8
moved them beyond 32 million.

### 22. Far-out jitter and stretching (L)
Rendering and movement lose float precision far from the origin: camera and entities shake, and ladders, rails,
redstone and the sides of falling liquids stretch or snap well before the Far Lands (Beta 1.8 fixed these). Fixable
by rendering relative to the camera. Not traced. Bug, separate from 21. Interaction: phase 4's rendering.

### 23. Piston crashes (partly T)
Beta 1.7.3 fixed piston duplication and one sign crash; Beta 1.8 "fixed several crash bugs relating to pistons"
without naming them. A read of `PistonBlock`, `PistonExtensionBlock`, `PistonHeadBlock` and `PistonBlockEntity` found
no crash reachable in normal play. Out-of-range data does crash:
- a piston with facing 6 or 7: `checkExtended` excludes only data 7, so 6, 14 and 15 reach `HEAD_OFFSET_*[6/7]`;
- a piston head with facing 6 or 7 (`TEXTURE_SIDES`);
- a moving block whose pushed id is 0 or unknown (`dropStacks` on `Block.BLOCKS[id]`).

All of these need an edited world or a hostile server, and the hardening already turns the client-side case into a
disconnect. Beta 1.8's unnamed piston crashes may be in code 1.8 itself added. A real crash hunt would still push and
pull every block kind with a piston on the test server. Quasi-connectivity and block-update detection are quirks
technical players rely on: leave them.
The piston hunt (below, 2026-11-02) then pushed and pulled everything on the real server: one crash in normal play (#53),
and two ways pistons lose blocks (#54, #67).

### 24. Player-placed leaves decay (T, ask)
Leaves decay only once bit 8 of their data is set (`LeavesBlock.onTick`). The bit is set on leaves within 4 of a
broken log (`LogBlock.onBreak`) and within 1 of any broken leaves block (`LeavesBlock.onBreak`), and placed leaves get
it at once: `LeavesBlockItem.getPlacementMetadata` returns `meta | 8` (corrected 2026-11-02, seen in the running game:
placed birch leaves have data 10). So placed leaves decay on their next random tick unless a log is within 4. Beta
1.8 added a no-decay bit. Beta's design or a bug: the user's call, Still Faithful's.
**Built in Still Faithful** (2026-11-02, `leaves.placedStay`, `docs/still-faithful.md`): placed leaves get bit 4
instead, and never decay.

### 25. Slimes don't split on overkill (T)
`SlimeEntity.markDead` splits only when `health == 0`; a hit that takes health below zero kills a big slime outright.
Fix: `health <= 0`. Risk: none.
**Fixed in Old Faithful** (2026-11-02, `slimes.splitOnOverkill`). Despawning, peaceful and the void leave health above
zero and still don't split.

### 26. Waking mobs reach you through a wall (T)
`NaturalSpawner.spawnMonstersAndWakePlayers` accepts a monster when the path the pathfinder found ends within 1.5
blocks of the player on each axis. The path is the closest the mob can get, not a path to the bed. The pathfinder
itself treats closed doors as solid. The fix first suggested here (require the path to reach the bed's block) would
wake no one: the bed isn't walkable, so no path ends on it. Traced further: the test compares the path's end block by
its corner with the player's exact position, so it leans half a block toward +x and +z, and a sleeper lies at the head
end, 0.1 from the bed's edge. With the head toward +x or +z, a monster one block beyond the wall behind the head
counts (1.1); in the other two directions it doesn't, and only there does a monster beside the foot count.
**Fixed in Old Faithful** (2026-11-02, `sleep.wakeThroughWalls`): the test measures from the end block's centre, the
same in every direction: a monster has to reach a block next to the sleeper's end of the bed or beside the bed. In
+x and +z beds this also lets one beside the foot wake you, as the other two directions always did.

### 27. Falling through a boat on getting out (T)
Leaving a vehicle (`Entity.setVehicle`) places the rider with `setPositionAndAnglesKeepPrevAngles(x,
vehicle.boundingBox.minY + vehicle.height, z)`. That adds `standingEyeHeight` to y, and `setPosition` subtracts it
again for the box, so the feet land exactly on the boat's top, give or take the rounding of (y + 1.62) - 1.62.
`Box`'s y-offset test treats a box as below only when `other.maxY <= this.minY`. If the rounding comes out one ulp
low, the boat's box overlaps instead, `move` ignores it, and the player drops through. Whether it happens depends on
the coordinates. Fix: place the rider a hair higher; UniTweaks' `boatDismountFix` adds 0.01, which fits this cause.
Risk: none.
**Fixed in Old Faithful** (2026-11-02, `vehicles.dismountOnTop`): the height asked for is nudged up by the least amount
that puts the box on top (a few billionths of a block). Reproduced first: the rounding drops a rider only where the top
and the eyes fall on either side of a power of two, a top between 62.38 and 64 (or 30.38 and 32, 126.38 and 128...),
about half the heights there. A boat on sea-level water floats with its top near 64.5, so it's safe; one on water a
block lower (top near 63.5) dropped the player 3.5 blocks in the test scene, and with the fix they stand on it.

### 28. Sleeping view (T: not a bug in the first-person view)
The bug hunt read that the view in bed follows the mouse: while asleep, `GameRenderer.applyCameraTransform` rotates by
the bed's direction, then by -(yaw + 180) about y and -pitch about x, and `onFrameUpdate` keeps turning the player with
the mouse. But the method's common part right after applies +pitch and +(yaw + 180) (unless the debug camera is on),
which undoes those two exactly, so the first-person view is the bed's turn alone: along the bed from the pillow to the
foot, whatever the mouse does (checked for all four bed directions, 2026-11-02). Left as beta: no clear mistake found.
What the mouse does change is the player's facing on waking, and what other players see of the sleeper's head;
UniTweaks' "sleeping camera rotation fix" may address either. Neither is clearly a slip.

### 29. Saddled pigs drop no saddle (T)
`PigEntity` only drops pork; the saddle is lost. Later versions drop it. Fix: drop it. Risk: none.
**Fixed in Old Faithful** (2026-11-02, `pigs.saddleDrop`), with the pork, where drops are decided.

### 30. Fences (T for the face, ask)
The bottom face of a fence on a block renders dark, and the fence's hitbox ignores its connections. The oversized
hitbox reaches over farmland, which players used to stop crops being trampled ("smart farms", Beta 1.8 removed it):
the lighting is a bug, the hitbox the user's call (not traced).
The face, traced: flat lighting (`BlockRenderManager.renderFlat`) lights a face that lies inside the block's own space
from that space for the top and the four sides (`maxY != 1`, `minZ > 0`...), and the door renderer does for its
underside too, but `renderFlat`'s underside always takes the light of the block below. A fence's bars have raised
undersides; on a fence standing on the ground the block below is solid, its light 0, so they draw black. Smooth
lighting (`renderSmooth`) takes every face's light from outside the block, so the same. **Fixed in Old Faithful**
(2026-11-02, `render.undersides`): an underside above the block's floor takes the light of the block's own space in
both modes. Only raised undersides change: fence bars, and the inner faces of pistons pushing down. Smooth lighting's
other faces of partial blocks (a slab's top under a ceiling) keep beta's way: not listed, and how smooth lighting treats
partial blocks as a whole is a bigger question than a slip.

### 31. Minecarts (T, ask)
Items and arrows: not `onCollision` (a cart calls it only for other carts, and mobs for themselves), but
`MinecartEntity.getCollisionAgainstShape`, which returns any entity's box as solid, so a cart moving into an item or
an arrow lying on the track collides with it and stops dead. **Fixed in Old Faithful** (2026-11-02,
`minecarts.passItems`): items and arrows aren't solid to carts; mobs, players and carts still are. Boats have the same
method, so an item floating in a boat's path is a wall to it too, and boats break on hitting walls at speed (#12): the
same slip, left as beta for now since it touches #12's question. In the cart-on-cart branch the check meant to read the other cart's motion mixes `velocityZ` with `prevX` (a
position) where `velocityX` belongs. Partly traced: the item push is plain; what the slip does to two-cart behaviour,
including the booster trick players rely on, isn't worked out. Items: bug. The slip: the user's call once it's
understood. Traced 2026-11-02 (`docs/still-faithful.md` > Open): the gate it breaks never fires on east-west track
and fires almost everywhere on north-south and curved track (walling carts off from each other there); boosters come
from the normal branch, so the slip switches them off on most N-S track. **Built in Still Faithful** (2026-11-02, `minecarts.cartCollisions`, the
user's call): the rail under the cart decides, so every axis behaves as east-west track did (`docs/still-faithful.md` >
Carts meeting carts). The gate does fire for carts side by side on parallel east-west tracks, as meant; and a booster
pair that keeps meeting overflows its stored speed after about 1,100 ticks, which beta's own guards end harmlessly
(the pair stops). Also: `MinecartEntity.animateHurt` (client) grows the wobble as `strength + strength * 10`, where the
server adds `amount * 10`, and prints "Animating hurt" to stdout on every hit. Cosmetic and log noise only.

### 32 to 34
- 32: the video settings sliders can't be dragged. Traced: beta's video settings screen has no sliders (only the main
  options screen has, and there they drag). `VideoOptionsScreen.buttonClicked` rebuilds the screen after any click,
  which would drop a slider mid-drag, so this bites only mods that add one there (brightness, say). Nothing to fix in
  vanilla; worth fixing if Old Faithful ever adds a slider there itself.
- 33: a slab item with a bad data value crashes (a name lookup out of range). Traced: `SlabBlockItem` and `DyeItem`
  index their names with the raw data value, so the tooltip crashes. Pure's hardening refuses such items from a server
  (a disconnect, not a crash), but not from a save. **Fixed in Old Faithful** (2026-11-02, `items.badDataNames`): out
  of range reads as the first kind (stone slab, black dye), as later versions do; with it, the hardening's item probe
  accepts them.
- 34, small visual bugs, traced 2026-11-02 (client only unless said):
  - **Fixed in Old Faithful** (`render.hotbarAfterCracks`): hotbar flashes white. `WorldRenderer.renderMiningProgress`
    sets additive blending and leaves it set; the outline, clouds, rain or Fancy's vignette usually reset it, but with
    Fast graphics in the Nether while aiming at a mob none runs, and the hotbar draws additively. The usual blending is
    put back at the method's end.
  - **Fixed** (`render.ridingPoses`): leggings and first-person hand while riding. The riding pose and arm swing go
    only to the armour model the last armour piece drawn left selected (`LivingEntityRenderer.render`), never the
    leggings' with boots on; `PlayerEntityRenderer.renderHand` doesn't reset `riding`, so the empty hand keeps the last
    drawn player's riding tilt. Both armour models get the pose; the hand is drawn unseated.
  - **Fixed** (`gui.deathScore`): death screen score format. `DeathScreen` draws "Score: &e" where the font's colour
    code is the section sign.
  - **Fixed** (`fishing.catchToFeet`, where the world runs): caught fish flies behind you. `FishingBobberEntity.use`
    aims the catch (and a hooked mob) at `owner.y`, which is the feet on a server (`ServerPlayerEntity` has eye height
    0) but the eyes in single player, 1.62 higher: from about 12 blocks out the fish sails over your head. Aimed at the
    feet in both now, as a server always did.
  - Left, judgement calls (built in Still Faithful, 2026-11-02: the bow and dropped item sizes): bow held like an item (third person; beta has no bow pose, Beta 1.8 added one with the
    draw); torches at full brightness (an explicit check in `renderTorch`, deliberate); dropped item sizes (non-cube 3D
    blocks drop at twice the size, `ItemRenderer.render`'s `isFullCube` test: a slip, but fixing it visibly changes
    familiar drops, so the user's call); clouds through blocks (design); entities unlit in the void
    (`Entity.getBrightnessAtEyes`; reads as deliberate, and the fix would touch game logic).
  - Left, small but not worth it now: torch bottom (`renderTiltedTorch` draws no underside: a missing face, but adding
    one means choosing its texture); grass block item (the item draws the side texture on every face; unobtainable in
    vanilla).
  - Not found: breaking animation invisible from below (the underside gets the overlay like every face; perhaps only low
    contrast on the darker underside). Worth a look in game before calling it a bug.

### 35. Eating at full health (L, ask)
Food eaten at full health is wasted. Beta has no hunger, so this is design: ask.

### 36. Multiplayer mining delay (T)
`MultiplayerInteractionManager.processBlockBreakingAction` counts `breakingDelayTicks` down only inside
`if (this.breakingBlock)`. After a break, `breakingBlock` is false, and `cancelBlockBreaking` never resets the delay
(single player's sets `breakingDelayTicks = 0`). So every block after the first in a session starts with 5 dead
ticks, however long ago the last break was.

While the button is held, multiplayer starts the next block only when the held click repeats (`Minecraft`, every
`tps/4` = 5 ticks) and calls `attackBlock`. After a break, single player resumes at a fixed +7 ticks; multiplayer
resumes at +6 to +10 depending on where the repeat timer is. A fresh click costs 5 dead ticks in multiplayer against
1 in single player.

The server (`ServerPlayerInteractionManager.continueMining`) accepts a finish once `hardness * (ticks since start +
1) >= 0.7` and checks nothing between blocks. Since the client sends START before its dead ticks, removing them
still passes, but makes players faster than the stock client, which is why some servers call the fix a hack.

Fix: count the delay down whatever `breakingBlock` is, reset it on cancel, and restart from
`processBlockBreakingAction` as single player does. The missing reset is a clear slip (OF); the overall tempo is
what servers built around (SF, or a switch defaulting to beta). Phase 6.
**Reset fixed (OF)**, `multiplayer.miningDelay` (client): the pause counts down and clears on letting go, as single
player's does; the next block still starts on the held click's repeat (the tempo: SF). Measured on the real server:
dirt by hand takes 15 ticks; beta 20 for every later block, fixed 15; a vanilla server accepts every break.

### 37. Skeletons circle (L, quirk)
Skeletons strafe around the player (Beta 1.8 changed it): their AI, a quirk.

### 38. Footsteps burst at the bottom of stairs (T)
Noticed by the user while playing (2026-11-02). `Entity.move` adds walked distance to `horizontalSpeed` every tick,
but plays a step (and advances `nextStepSoundDistance` by one) only when the block under the feet isn't air.
Going down stairs the player is briefly airborne, so distance piles up silently; back on solid ground one step
plays every tick until the counter catches up: a rattle of steps at the bottom. Fix: when no step can play,
keep `nextStepSoundDistance` level with the distance walked, so the backlog never builds. Risk: none.
**Fixed in Old Faithful** (2026-11-02, `sound.stepsAfterFalls`): after each move, the next step is kept within one of
the distance walked, so a landing owes at most one step. Steps filling in the short drops down a staircase (Still Faithful) were
discussed and left out (the user, 2026-11-03).

### 39. The cursor's item draws behind the player and labels (T)
Noticed by the user (2026-11-02). `HandledScreen.render` draws the stack held on the cursor at z +32 with depth
on, then `drawForeground` (labels such as "Crafting") with depth off; `InventoryScreen.drawBackground` drew the
player model earlier at z 50 with depth on. So the held item passes behind the model and under the labels. Fix:
draw the cursor stack after `drawForeground`, above everything, as later versions do. Risk: mods drawing
foreground overlays expect vanilla's order; check the popular container mods.
**Fixed in Old Faithful** (2026-11-02, `gui.cursorItemOnTop`): drawn at the end of `HandledScreen.render`, at z 232
(clear of the figure), with the screen's own item lighting, GL state left as vanilla leaves it. Of the link check's
mods, InventoryTweaks and MojangFix change only the slot highlight in `render`; BHCreative (a StationAPI mod) hooks
the screen more widely, so a clash there is what the switch is for. It also covers what #34 listed as "items under
container text".

### 40. Sliders can't reach every percentage (T)
Noticed by the user (2026-11-02). `SliderWidget` takes its value from the mouse's GUI x across a 142-unit track, so
there are 143 positions at any window size. Volume (0-100%, 0.7% a step) can reach every whole percent but only by
hitting the right GUI pixel, fiddly at large GUI scales; sensitivity is labelled 0-200% (`GameOptions.getString`),
1.4% a step, so about four in ten values can't be chosen. The cause is the whole-unit mouse: the screen has more
pixels than GUI units at any scale above Small. Risk: none.
**Fixed in Old Faithful** (2026-11-02, `gui.sliderSteps`): the sliders read the mouse to the screen pixel (142s + 1
positions at GUI scale s), land on whole labelled steps (the smallest float that labels as that step), and draw the
knob on its pixel. Every label is reachable from Normal (2) up; at Small the volumes all are, and 143 of
sensitivity's 201, one per pixel. An arrow-key nudge came first and was taken out at the user's word (2026-11-02):
fix the cause, not a key workaround. The game's own three sliders only; a mod's are left alone.

### 41. Repeaters don't update what they power (T)
Fixed, on by default: the user's call (2026-11-02), though a circuit could have a repeater beside a door it never opened.
Five blocks look at their power only when the neighbour update comes from a block whose `canEmitRedstonePower()` is
true: doors, both halves (`DoorBlock.neighborUpdate`), trapdoors, note blocks, dispensers and TNT (each one's
`neighborUpdate`). Every redstone source returns true there (wire, torches, levers, buttons, plates, detector rails)
except the repeater: `RepeaterBlock.canEmitRedstonePower()` returns false for both ids (93, 94). A repeater switches
state through `world.setBlock(x, y, z, POWERED_REPEATER/REPEATER, meta)` and its own `onPlaced(World, x, y, z)`, and
both notify with the repeater's id only. So any of those five directly in front of a repeater (or beside the block a
repeater powers) never reacts. It catches up only when some other emitter next to it changes, and then
`world.isPowered` does see the repeater (`RepeaterBlock.isPoweringSide`). Pistons and powered rails are fine: they
don't filter by id.

Fix: in those five checks, count the two repeater ids as emitters. Not by making `canEmitRedstonePower` return true:
`RedstoneWireBlock.shouldConnectTo` uses it, and wire would then connect to a repeater's sides and change its
behaviour. Risk: low. A build that counts on a door next to a repeater not following it is counting on erratic
behaviour, since the door follows whenever anything else nearby updates. The usual workaround, a dust block after
the repeater, keeps working.
**Fixed in Old Faithful** (2026-11-02, `redstone.repeaterUpdates`), with a sixth receiver the same check missed: a plain
rail's junction (`RailBlock.neighborUpdate` switches a T-junction only for a neighbour that says it emits). Default on;
the switch is there for a circuit that counted on the old way.

### 42. Pending block ticks aren't saved (T)
`World.scheduleBlockUpdate` keeps pending ticks only in memory (`scheduledUpdates`), never in the chunk or level
file, and `processScheduledTicks` is never flushed: no caller passes `true`. Ticks are also lost near the loaded edge:
- a tick isn't scheduled at all when the 17x17x17 region around it isn't loaded;
- a tick that comes due while that region isn't loaded is removed without running.

So anything mid-change when the world closes, the server stops, or its chunk unloads (or sits near the edge) loses
its next step. Blocks with random ticks recover on their own, within tens of seconds on average and at a random
moment: buttons, pressure plates, redstone torches, detector rails and flowing liquids all have
`setTickRandomly(true)`. Repeaters have no random tick, so they stay stuck until a neighbour changes: a repeater
clock (a pulse running round a loop) stops dead on reload. Sand or gravel placed just before a save can stay
floating.

Release 1.3.1 fixed the repeater case ("no longer stuck when loading the chunks they are in") by saving ticks with
the chunk (`TileTicks`). Fix: do the same, a `TileTicks`-style list in the chunk's NBT restored on load (b1.7.3
ignores unknown tags, so saves stay readable), and keep ticks for unloaded regions instead of dropping them. Risk:
moderate (an addition to the save format, and chunk-unload timing). Freezing things by unloading them exploits a
loss rather than a feature. Interaction: oldfaithful-saves (the chunk writer), #14.
**Fixed in Old Faithful** (2026-11-02, `ticks.saved`, `PendingTicks`): each chunk's pending ticks are written into it as
`TileTicks` (1.3's list and fields: i, x, y, z and t, the delay left), restored when it loads, and leave the world's
queue when it unloads. A tick is queued whenever its own chunk is loaded; one that comes due without the blocks within
8 of it loaded is parked off the queue and returns once they load, so it never takes a turn from the 1,000 a tick (the
spawn area alone leaves tens of thousands of liquid ticks at its edge, which retried on the queue would starve
everything else). Ticks still run only with their surroundings loaded, so they never load chunks. Checked on the real
server (`:oldfaithful-fixes:serverTicksTest`): a repeater clock built and running, the server stopped and started, the
clock runs on; the same world on a vanilla server loads cleanly and its clock stands still. Noticed on the way: a new
world's generation leaves some 240,000 liquid ticks, worked through at 1,000 a tick (about four minutes); vanilla
dropped what was left on quitting, now it's saved and finished next time.

### 43. A missing block entity crashes the game when its block is broken (T, Pure)
Fixed (2026-11-02, after 0.1.0) in `oldfaithful-saves`: every loaded chunk's vanilla blocks with a block entity that
lack one get a fresh default (`docs/saves.md` > Chunk loads); `serverSaveTest` breaks all seven kinds after it.
`ChestBlock`, `FurnaceBlock`, `DispenserBlock` and `JukeboxBlock` read their block entity in `onBreak` and use it
without checking for null. `Chunk.setBlock` sets the new block id *before* it calls the old block's `onBreak`. And
`Chunk.getBlockEntity` only creates a missing block entity lazily while the block at that spot still has one
(`BLOCKS_WITH_ENTITY[id]`), which at that point it no longer does. So if one of these blocks has no block entity
when it's broken (by a player, an explosion, a piston, water...), `onBreak` throws a NullPointerException. That
crashes single player and stops a dedicated server ("Unexpected exception", seen in the test).

In vanilla this needs an edited or converted world: a chunk whose `TileEntities` list lacks the entry, or has an
entry with an unknown id, which `createFromNbt` skips. `oldfaithful-saves` makes it reachable by design: a block
entity whose data throws is left out and the chunk is kept (`AlphaChunkStorageMixin`; the chest case in
`ServerSaveTest`). The chest then has no block entity until something calls `getBlockEntity` on it while it's still
standing; opening it does that. Break it first and the game crashes.

Fix, in Pure with the saves mod: when a block entity is left out, put the block's fresh default one in its place
(an empty chest, which is what the player would see anyway). Done more widely: every chunk loaded
through the saves mod is checked, which also mends edited worlds and ones 0.1.0 saved so; the `onBreak`s are left
unguarded (a world loaded without the mod, or through StationAPI's storage, keeps vanilla's crash).

### 44. Redstone torch burnout never expires after time goes back (T)
`RedstoneTorchBlock.burnoutEntries` is a static list: every world, every dimension, the whole session. `onTick`
removes entries only from its front, and only while `world.getTime() - entry.time > 100`. Open a world whose time is
lower than the last world's (most other worlds), or set the time back on a server, and the front entries are in the
future: nothing is removed until the clock passes them.

Meanwhile every torch turn-off appends an entry, and `isBurnedOut` counts all the entries at that position. So any
torch that has turned off eight times since is burnt out for good (an unlit torch relights only when not burnt out),
and every torch clock in that world dies. The list also grows without bound and is scanned on every torch tick. All
it takes is a torch that switched off in the previous world's last five seconds. Entries are matched by position,
not by world.

Beta 1.8 fixed the server case ("redstone circuits not working after /time set"). Fix: drop entries whose time is
ahead of the world's (or key them by world and clear the list on a world change). The burnout rule itself is
unchanged. Risk: none.
**Fixed in Old Faithful** (2026-11-02, `redstone.torchBurnout`): entries from the future are dropped first on every
torch tick.

### 45. Shift-click crafting loses what doesn't fit (T)
`CraftingScreenHandler.quickMove` for slot 0 (the same pattern is in `PlayerScreenHandler`) puts the result into the
inventory with `insertItem`. That tops up partial stacks first, then needs one empty slot for the rest. With no empty
slot and only partial room, part of the result goes in and the rest stays in the result slot, and `onTakeItem` still
uses the ingredients. Using them calls `onSlotUpdate`, which refills the result slot with a fresh result, so the
leftover is gone: four torches with room for two loses two. Fix: take the result only if all of it fits (or hand the
leftover to the cursor). Risk: none.
**Fixed in Old Faithful** (2026-11-02, `crafting.shiftClickFits`): the result is taken only when all of it fits, in both
crafting screens, wherever the clicks are decided (the server, or single player).

### 46. Pick block picks the block's id, not its item (T)
`Minecraft.handlePickBlock` passes the id of the block under the crosshair to `PlayerInventory.setHeldItem`, which
selects a hotbar slot holding that id. It maps grass to dirt, double slab to slab and bedrock to stone. But blocks
whose item has a different id (redstone wire, repeaters, both doors, signs, wheat, beds, cake) find nothing, and slab
kinds aren't told apart. Pointing at a mob reads the hit result's block coordinates, which are 0, 0, 0 for an entity
hit: bedrock, mapped to stone. Fix: map blocks to their items, and ignore entity hits. Risk: none. UniTweaks'
`pickBlockFix` covers the same ground.
**Fixed in Old Faithful** (2026-11-02, `controls.pickBlock`, `PickBlock`): blocks map to their items (redstone,
repeaters, doors, signs, wheat to seeds, beds, cake, sugar cane, unlit torches, lit furnaces and redstone ore, piston
heads, farmland to dirt), items with kinds match the kind first (wool colour, slab, log, sapling), and a mob picks
nothing. Beta's own mappings stay (grass to dirt, double slab to slab, bedrock to stone).

### 47. Double slabs always draw their top (T, speed)
`SlabBlock.isSideVisible` begins with `if (this != Block.SLAB) super.isSideVisible(...)` and discards the result: a
missing `return`. The double slab then falls into the single slab's rule, where `side == 1` returns true, so its top
face is meshed even under a solid block. Depth hides it, so the pixels are identical; only meshing and drawing cost.
A pure speed fix for `oldfaithful-speed` (large double-slab floors under blocks); probably small.
**Fixed in both editions** (2026-11-02, `oldfaithful-speed`'s `DoubleSlabFacesMixin`): the double slab answers with the
full block's rule. The faces dropped lie between two opaque blocks and the block above draws none there, so no pixel
changes (reasoned; generated worlds have no double slabs for the benchmark to compare). Half slabs, and any slab a mod
adds, keep their own rule.

### 48. Signs show blank to other players in busy chunks (T)
`ChunkMap$TrackedChunk.updateChunk` has three branches:
- one changed block: sent with its block entity (correct);
- 10 or more: the whole area, with its block entities (correct);
- 2 to 9: the block entities are looked up at a position decoded from `this.dirtyBlockCount >> 12 & 15`,
  `dirtyBlockCount & 0xFF` and `>> 8 & 15` (lines 159-161), where `this.dirtyBlocks[var11]` belongs.

Signs are the only block entity with an update packet, and `ServerPlayNetworkHandler`'s sign update reaches others
only through this path (`blockUpdateEvent`). So a sign edited in the same tick as any other block change in that
chunk (flowing water, crops, redstone) reaches no other player until the chunk is sent again. There's also a
leftover `System.out.println("Sending!")` there. Fix: decode `dirtyBlocks[var11]`. Risk: none.
**Fixed (OF)**, `multiplayer.signUpdates` (server); the stray print goes with vanilla's loop. Checked live: another
player got the text; from beta's server, nothing.

### 49. Frozen entities after a respawn (T; seen on the real server)
When a dead player is removed (after the 20-tick death animation), `EntityTracker.onEntityRemoved` takes them off
every entry's viewer list without sending destroy packets. After a respawn in the same dimension, the client keeps
its world. So mobs, items and players near the death spot that later die, get picked up or leave stay frozen on that
client until it relogs. Fix: send the destroys to the dead player there, or clear non-player entities on respawn.
Risk: low. Seen live (`fixesMultiplayerTest`): a pig removed while the player was dead stayed on their screen after
the respawn. (The body is ticked, and so removed, only on the client's move packets, which a dead client keeps sending.)
**Fixed (OF)** on either side, `multiplayer.respawnEntities`: a fixed server notes what the dead player was seeing
and tells their client to drop it on respawn (the death screen's view is unchanged); a fixed client drops every
entity the server gave it on a respawn from death in the same dimension (the server sends what's in range again, as
it always did). Either alone clears it.

### 50. Other players turn in steps (T)
`ClientNetworkHandler.onEntity` uses the entity's *current* yaw when a packet carries no rotation
(`packet.rotate ? ... : var2.yaw`), which retargets the lerp to where it already is. Players are sent every 2 ticks
with 3 lerp steps, so a move-only packet right after a rotation packet cancels the last third of each turn. And
`EntityTrackerEntry.notifyNewLocation` sends rotation only on a change of 8/256 (11.25 degrees) or more. So others
turn in steps, and can be left facing up to about 11 degrees off. Fix: keep the last target yaw and pitch for
move-only packets (bug). Lowering the threshold is an ask. Minor: `onEntityPosition` adds `+0.015625` to y where
relative moves and spawns don't, a 1/64-block bob every 400 ticks.
**Yaw fixed (OF)**, `multiplayer.turns` (client), for players and mobs. A sudden turn was left a third short (measured:
45-degree turns ending 15 to 30 degrees off; fixed: on target). The 11.25-degree step stays (SF); so does the 1/64
bob (probably meant: an absolute position rounds down, and the lift keeps a mob out of the floor).

### 51. A server movement check (T)
About what a server trusts from its clients; the details are kept private. Left as beta in every edition (user, 2026-11-02).

### 52. `allow-nether=false` resets time and spawn on restart (T; fixed in Pure for the Nether off)
Seen 2026-11-02 while testing #42 on the vanilla server. Traced: `MinecraftServer.loadWorld` always makes the Nether
(`ReadOnlyServerWorld`), whose `World` constructor reads its own copy of level.dat's data (on a new world, running
its own spawn search: near 0, 64, 0); `World.save` writes level.dat from the world's own copy; the tick loop skips the
Nether when it's off, so its copy never advances; and `saveWorlds` (save-all, stopping) saves world 0, then world 1.
So every clean stop leaves the Nether's copy: the time and weather the session began with (Time 0 forever on a world
made with the Nether off), and on a new world the Nether's spawn. Autosaves come from the overworld only, so a kill
keeps the right file. The seed survives (both copies share it). Reproduced on vanilla's server and Pure Faithful
0.1.0's package: an existing world switched to Nether off went back at every stop to the time it was switched at, a
new one to 0 with spawn 0, 64, 0. Not ours. **Pure**:
the world's time lost at every restart is lost world state; fixed in `oldfaithful-saves` (`docs/saves.md`): the
unticked Nether's copy is never written, so nothing changes with the Nether on.
With the Nether on, the same last write still decides (vanilla, left as is in Pure): a new world's spawn moves to the
Nether's after its first autosave (it shows from the first restart), the weather in level.dat is the Nether's frozen
copy (so every restart rerolls it), and a night slept through is undone by a restart (only the overworld skips it;
`/time` sets both, so it's fine). Read and reproduced for spawn and weather, sleep read only. **OF** candidate (the
level's data should be the overworld's alone); the spawn near 0, 0 is what beta servers had, so moving it may be an ask.

### 53. A crash involving pistons near the edge of the loaded area (T; Pure)
Found by the piston hunt (below), 2026-11-02; the details are kept private. Fixed in both editions (`oldfaithful-saves`).

### 54. A piston interaction that can lose blocks (T)
Found by the piston hunt (below), 2026-11-02; the details are kept private. Left as beta in every edition (user, 2026-11-02): it's rare, technical players use the behaviour, and changing the order pistons write in risks breaking contraptions.

### 55. A lone door top passes updates to the block below (T, seen)
`DoorBlock.neighborUpdate`: a top half (data bit 8) checks that a door is below it and removes itself if not, then,
when the update came from a redstone source, calls the same method for the cell below without checking it's a door. So
the block below is treated as a door's bottom whose top is missing: removed, and a door dropped (`dropStacks` with the
door's own drop). Seen in the hunt: a door top on a piston, a torch placed beside it, the piston gone and a door item
lying there. Needs a top half with no bottom, which vanilla play doesn't leave (breaking either half removes both) but
an edited or converted world can; then any block under it (a chest, spilling its contents) is deleted. Fix: forward
only when the block below is this door. OF, low.
**Fixed in Old Faithful** (2026-11-02, `doors.loneTop`): the update goes down only to the door's own bottom half.
Checked on the real server (`ServerWorldFixesTest`): a lone top on a chest, a redstone torch placed beside it; the
chest stays (beta: gone, a door dropped).

### 56. One-deep water breaks only some falls (T, seen)
`Entity.baseTick` zeroes the fall distance only when the entity is in water at the start of a tick (`checkWaterCollisions`,
a box shrunk 0.4 at each end, so only once the feet are within 0.6 of the pool's floor). The tick that lands moves the
entity from above that window to the floor and `Entity.fall` lands the whole fall (`LivingEntity.onLanding`) with no
second look for water. Measured on the real server (the hunt's `falls` set, a pig dropped onto one-deep water and onto
stone from 4 to 40 blocks): from 12 blocks down, about half the heights hurt exactly as stone does (12: 8 damage, 20:
16, 31: 28), the rest are safe; each height always the same way. Players get the same rule (single player runs it on
the client; a server runs it from the move packets). Fix: check for water once more when a fall lands, as later versions
do. OF, mid: more landings become safe, none less.
**Fixed in Old Faithful** (2026-11-02, `falls.waterLandings`): the landing makes baseTick's water test once more
(without its push) and lands nothing if it finds water. Checked on the real server (`ServerWorldFixesTest`): pigs
dropped from 12 to 31 blocks into one-deep water, none hurt; beta hurt 13 of the 20 (12: 8 damage, 31: 28). A
design to keep in mind: a mob farm dropping mobs into water deep enough to count, relying on the fall to hurt them,
would stop hurting them at the heights where beta did; none is known (farms drop onto blocks), and the switch is there.

### 57. Sneaking off a corner diagonally (T)
`Entity.move`'s sneak guard trims the x step and the z step separately, each tested alone against support a block down,
never the two together. At an inside corner (an L of blocks) the x step is held by one block and the z step by the
other, so a diagonal step lands over the gap and the player drops. OF, low-mid; both sides run the same code, so a
client fix alone is enough.
**Fixed in Old Faithful** (2026-11-02, `controls.sneakCorners`, client): after beta's two trims, the step is trimmed
while the two parts together have nothing under them, as later versions do. A server repeats the move with beta's
trims, which find support for each part, so it accepts the shorter step. Checked in the running client
(`clientStartupTestFixed`): sneaking diagonally off an inside corner, the player stays on it.

### 58. Explosions don't push a recently hurt player on a server (T)
`Explosion.explode` damages each entity, then adds its push to the entity's velocity. A server sends a player's velocity
only when `velocityModified` is set, which `LivingEntity.damage` does only for a fresh hit (`scheduleVelocityUpdate`);
within the 10 ticks after earlier damage (or the invulnerable seconds after joining or respawning) it returns before
that. The client never runs the explosion itself (`ClientNetworkHandler.onExplosion`), so the push is lost: chained TNT, a
creeper right after a zombie's hit. Mobs are moved by the server, so they're pushed. Fix (server only, vanilla clients
fine): mark the player's velocity for sending when an explosion pushes them. OF, low-mid.
**Fixed in Old Faithful** (2026-11-02, `multiplayer.explosionPush`, server). Checked by `fixesMultiplayerTest`: a raw
client hurt, then an explosion beside it a quarter second later, is sent its push (beta: nothing).

### 59. Mobs nod (T)
`LivingEntity.lookAt` steps the pitch with `pitch = -lerpRotation(pitch, target, max)`: the step starts from the stored
pitch though it works in the negated one, so it settles only while the target is within half the step limit of level.
Beyond that the head swings between two angles every tick (a zombie looking 45 degrees up: -45 and +15; directly
under a player, about 80 degrees). Fix: step from `-pitch`. OF, low (cosmetic); worth a look in game first.
**Fixed in Old Faithful** (2026-11-02, `mobs.headNod`). Mob pitch only sets how the head is drawn: skeletons' arrows
and ghasts' fireballs are aimed apart from it. Checked on the real server (`ServerWorldFixesTest`): a pig looking 45
degrees up settles at -45 (beta: -40, 0, -40, 0...). How it looks in game is the user's check.

### 60. /tp keeps the fall (T)
`ServerPlayNetworkHandler.teleport` (used by /tp) moves the player without resetting `fallDistance`, so a player
teleported mid-fall lands the fall they'd built at the destination (20 blocks fallen: 16 damage). OF, low (MP).
**Fixed in Old Faithful** (2026-11-02, `multiplayer.tpFall`, server): /tp clears the fall. Only /tp: the server's own
teleport back of a player who moved wrongly keeps beta's behaviour, so a fall can't be shed by provoking one. Checked
by `fixesMultiplayerTest`: a raw client 10 blocks into a fall, moved by /tp, lands unhurt (beta: 7 damage).

### 61. Drowning measured at different heights (T)
`Entity.isInFluid` tests `y + getEyeHeight()`. The client's player keeps its eyes in `y` and adds 0.12
(`PlayerEntity.getEyeHeight`), 1.74 above the feet; the server's (`ServerPlayerEntity.getEyeHeight`) is 1.62. With the
water surface between those heights (standing on a full block in water two levels down from full, or on a closed
trapdoor under water) the server drowns a player whose screen shows full air; mining speed in water disagrees the same
way. OF, low.
**Fixed in Old Faithful** (2026-11-02, `multiplayer.drowningHeight`, server): the server measures a player's eyes where
clients and single player do (1.74), so it agrees with the air bubbles a player sees; vanilla clients already measure
there. Checked by `fixesMultiplayerTest`: a raw client standing 20 s with the surface between the two heights isn't
hurt (beta: drowning damage after 16 s).

### 62. Every spawner is a pig in multiplayer (T)
A server sends no block entity data but signs' (`ChunkMap$TrackedChunk.sendBlockEntityUpdate`: only `SignBlockEntity`
has an update packet), so the client makes the spawner's block entity itself, whose constructor says "Pig", and draws a
spinning pig in every dungeon. Spawning itself is the server's and right. Fix needs both sides (a payload only a fixed
client reads). OF, mid (MP).
**Fixed in Old Faithful** (2026-11-02, `multiplayer.spawnerMobs`, both): a fixed server gives spawners an update packet,
the sign packet at the spawner's position with a mark and the entity id (`SpawnerMobs`); a vanilla client applies a
sign packet only to a sign, so it ignores it and keeps its pig, and a fixed client sets the spawner's mob. Ids over 15
characters (the sign line's limit; mods' mobs) aren't sent. Checked in all four mixes by `fixesMultiplayerTest`: a cow
spawner shows a cow only where both sides have the fix, a pig elsewhere, and no client is disconnected.

### 63. Slimes skip the room check (T)
`SlimeEntity.canSpawn` replaces `LivingEntity.canSpawn` without calling it, so slimes skip the collision, entity and
fluid tests every other mob keeps: a size-4 slime (2.4 blocks wide) spawns wherever two blocks of air sit on a solid
block, so in walls (and suffocates, splitting) and inside other slimes. A clear slip (OF), but slime farms are built on
today's rates: a switch defaulting to beta at least, or Still Faithful's. Traced further (2026-11-02, `docs/still-faithful.md` > Open): slimes spawn below y 16; with the
room check, the size-4 spawns most farm output comes from would fail in the usual 2-high layers (about 80% fewer
slimeballs); recommendation: leave as beta.

### 64. A wrong extra block west and north of 0, 0 (T)
`World.isBoxSubmergedInFluid` subtracts one from the box's lowest x, y and z when they're negative, though
`MathHelper.floor` already floors negative numbers. So in the negative quadrants it also tests the block beyond a mob's
west (or north) side: no mob spawns with water or lava beside it there (`LivingEntity.canSpawn`), riverbanks and
spawning floors beside channels included, while east and south of 0, 0 they do; the swimmer's lift out of water reads it
too. The server-only `isAnyBlockInBox` has the same subtraction (movement checks). OF, low.
**Fixed in Old Faithful** (2026-11-02, `spawning.besideWater`): `isBoxSubmergedInFluid` tests the box's own blocks.
`isAnyBlockInBox` is left as beta: it only serves the server's flying check, and fixing it would make that check
stricter for honest players. Checked on the real server first (`ServerWorldFixesTest`, the
control): a pig on grass with water beside its west (or north) side may spawn east and south of 0, 0 and not west and
north of it; with the fix it may everywhere.

### 65. A spawner of something that isn't a mob crashes (T; Pure)
`MobSpawnerBlockEntity.tick` casts what `EntityRegistry.create` makes to `LivingEntity`; an `EntityId` naming another
entity (`PrimedTnt`, `Item`, as world editors allow) throws every tick a player is within 16 blocks: the crash screen, or
a server that stops. An unknown id is handled. Edited worlds only. Pure (a crash).
**Fixed in both editions** (2026-11-02, `oldfaithful-saves`, `ChunkLoad.repairSpawners`): a loaded spawner whose id names
a registered entity that isn't a mob is set back to a pig and listed with the repairs (`docs/saves.md`); an unknown id
is left (handled, and maybe a mod's mob). `serverSaveTest` loads a primed-TNT spawner and ticks it as if a player
stood beside it; without the repair the server stops.

### 66. Small spawning and generation slips (T)
- Squid can spawn in lava (`NaturalSpawner.isValidSpawnPos` asks for any fluid; the pack's walk can reach a lava lake at
  the same height): unseen, it burns. **Fixed in Old Faithful** (2026-11-02, `spawning.squidInWater`): the place must
  be water. Checked on the real server (`ServerWorldFixesTest`): lava refused, water accepted (beta: both accepted).
- Nether chunks never get a heightmap (`NetherChunkGenerator.getChunk` skips `populateHeightMap`), so every Nether cell
  "sees the sky"; the only effect found: plants on dirt there never die of darkness. OF at most; left as beta
  (2026-11-02): nothing visible to gain, and a heightmap in new Nether chunks only (the old ones keep theirs empty)
  would touch the Nether's light and sky checks for no player-facing fix.
- Generation, left as beta because fixing changes seeds (Still Faithful at most): the Nether's decoration isn't seeded
  (`NetherChunkGenerator.decorate` never reseeds, so glowstone, fire and lava falls depend on load order); clay is placed
  8 blocks from the water it tested (`ClayOreFeature`), which is why beta's clay sits in beach sand; climate is sampled
  off the noise lattice at chunk seams (about a tenth of a block); the spawner's spawn area is centred on its corner. The spawner one isn't generation (it runs in play and changes no seed); traced further 2026-11-02,
with recommendations, in `docs/still-faithful.md` > Open.

### 67. Falling sand deletes sand it passes into (T, seen)
`FallingBlockEntity.tick` removes the block of its own kind at its position on every tick, not only the first (where
it's meant to clear the block it fell from; later versions check `timeFalling == 1`). So falling sand or gravel that
ends up inside a cell holding the same block deletes it: seen in the piston hunt when a piston pushing up into a sand
column shoved a falling sand into the sand above it (`chains/2x12+0/f1/plain`: two sand, one left; 13 such scenes, plain
and sticky). Also reachable by placing sand into a falling sand's path. Fix: only on the first tick. OF, low.
**Fixed in Old Faithful** (2026-11-02, `blocks.fallingSandKeepsKind`): the block is cleared on the first tick only. A
vanilla client on a fixed server still clears such a block in its own copy of the world (it runs the same tick for the
falling block), so the server sends each kept block again as it's passed and when the falling block lands. Checked on
the real server (`ServerWorldFixesTest`): a falling sand some ticks into its fall in a cell of sand leaves two sand
(one dropped as an item); beta, one.

### 68. A rejoin prepares the terrain around the spawn, not the player (T, measured; left as beta)
`Minecraft.setWorld` (single player) runs `prepareWorld`, which loads the chunks 128 blocks around, before the saved
player is read (`World.addPlayer` reads it afterwards; `getPlayerForProxy` always returns null), so `prepareWorld`'s own
"around the player if there is one" never applies on a rejoin and it centres on the world spawn. Respawns and portals
prepare after the player is placed and are fine. Old Faithful 0.1.3 to 0.2.0 (`world.rejoinTerrain`, client) centred
`prepareWorld` on the saved player's position when no player existed yet. **Removed in 0.2.1**, as below; a config file
naming the switch loads as before, since only the switches the mod knows are read.
It was written up as the cause of a report by the user playing Still Faithful (2026-11-03: on a rejoin about 230 blocks
from spawn they walked a little into a wall, took suffocation damage and were pushed out), reasoning that the walls had no
collision until their chunks loaded (`World.getEntityCollisions` skips a column whose chunk isn't loaded, and
`ChunkCache.isChunkLoaded` is true only for chunks already in it). Measured 2026-11-06, that isn't what happens. `World.updateEntity`
doesn't tick the player until the chunks within 32 blocks are loaded, and with the switch off, 160 real rejoins (`:harness:ledgeHunt`,
real save, quit and reopen, families T and V, half of V 230 to 630 blocks from spawn) had no first tick over an unloaded
column, a first tick at most 1 world tick later (with it on, 0), no longer to reopen, and the sinks of #75 at the same
rate (10 of 100 with the switch on or off). What the report describes is what #75 does beside a wall as tall as the eye:
same walk in, 60 ticks of damage over those 10, same push-out; with #75's fix 0 of 100. Whether theirs was that event can't be
shown after the fact, since their world isn't known to have had such a wall. 0 of 60 at arbitrary places (V), so it takes a
wall past a power of two, or a rarer run of walking than these.
So this switch prevented nothing measured, which also made the "walls solid from the first moment" of the 0.1.3 changelog and
Old Faithful's README wrong (corrected in 0.2.1's).

What it did do, measured 2026-11-06 on a hidden client, Still Faithful, Far view, 48 reopenings, 24 each way, in alternating
launches at Balanced and Max FPS, of worlds saved 300 and 4000 blocks from spawn (a probe not kept in the tree). It works as written: on entering the world, 289
of the 289 chunks around the player are loaded with it, 1 without. Nothing a player would notice follows:
| Medians, ms unless marked | With | Without |
|---|---|---|
| Reopening, after the first in a launch | 160 to 168 | 168 to 182 |
| Reopening, the first after starting the game | 286 to 292 | 298 to 332 |
| Render chunks built 1 s in (of 5408) | 48 to 66 | 38 to 54 |
| Within 64 blocks and in view, all built: 300 from spawn | 3070 to 3300 | 3074 to 3319 |
| The same, 4000 from spawn | 48 to 138 | 222 to 316 |
| Within 64 blocks, all built | 33,800 to 39,000 | 33,900 to 36,700 |
| Longest frame in the first opening after starting the game, 300 from spawn | 130 | 300 to 330 |
| Longest frame in later openings | 32 to 38 | 34 to 38 |
The longest frame is the one difference above noise, and it's a single frame as the empty view first appears, about a
fifth of a second once in a session. At equal rates of building, the fill times differ by less than run to run.
What this leaves open: these clients ran at 60 frames a second and one rebuild a frame, on a fast disk with the world in the
disk cache; a client with vsync on, which builds more a frame, isn't run hidden (those stall this PC's display), and a slow cold disk
wasn't tried. A slow disk might show more, moved from the first frames to the loading screen.

### 69. Chests can't be turned (T, ask)
Asked by the user (2026-11-03). Beta stores no facing for a chest (data always 0); `ChestBlock.getTextureId` works the
front out every time it's drawn: away from an opaque block behind it, south when free-standing, and its east-west test
runs after the north-south one, so in a corner a chest turns sideways; a double chest faces across its length the same
way. Later versions keep a facing chosen at placement. SF. **Built in Still Faithful** (2026-11-03,
`blocks.chestsFaceYou`): a placed chest faces the placer, in its data as furnaces keep theirs (`Chests`); a chest
placed beside one with a facing joins it, otherwise both halves take the placer's, turned across the pair if they
looked along it. Data 0 (every chest placed before) keeps beta's rule, so nothing in an existing world turns; vanilla
draws every chest by beta's rule, and the dropped item has data 0 as before. Checked in `clientStartupTestStill`.

### 70. Sky through the seams between chunks (T, measured)
Reported by the user playing Still Faithful (2026-11-03): dots of sky colour along block edges, worse when moving.
Beta builds each render chunk (16 blocks a side) as a display list of vertices relative to its corner, exact, and
places it in two translations that nearly cancel: `ChunkRenderer.render` by (the chunk's 1024-block region - the
camera, the camera cast to a float), then the list's own `translateToRenderPosition` by the chunk's place in that
region (`x & 1023`), then a scale of 1.000001 about the chunk's middle (beta's own guard against cracks: an overlap of
8 millionths of a block at each side). The driver adds the translations in single precision at magnitudes up to
1023, so each chunk lands off by its own rounding, up to about 0.0001 blocks: several times the overlap. Where two
chunks meet, the faces either side then miss by a hair, and a pixel centre landing in the gap shows what's behind:
inside solid ground nothing is drawn, so the sky or fog colour. Faces within a chunk share exact vertices under one
matrix and never part. Worst where the chunks' places in their region are large: anywhere at negative x or z (`x &
1023` is near 1023 there) and toward the far side of each 1024-block region. A float32 model of the matrix stack
(Claude Opus 5.5, 2026-11-03) has 28% of chunk joins open, by up to 0.0001 blocks, at negative or far coordinates,
2% near the origin's positive side.

Measured with `:harness:seamCheck` (`docs/fixes.md` > Tests; Still Faithful's mods, 1600x900, five views of a sealed
room held for 32 frames each while the camera creeps, holes counted from the depth), hole pixels a frame, beta /
fixed: near 20, 20: 0.18 / 0.06; at -76, -76: 1.51 / 0.06; at 708, 708: 1.10 / 0.06. On chunk joins: 21, 221 and
157 holes over 160 frames in beta, 0 with the fix at every place. The 0.06 left is #72 (with the atlas made opaque
it drops to one stray pixel in 160 frames, the same with or without the fix). OF, mid (everyone sees it, a dot at a
time). **Fixed in Old Faithful** (2026-11-03, `render.seams`, client): the list keeps only the scale about its
middle, and each chunk is translated once, by its corner minus the camera worked out in double precision
(`ChunkTransform`), so the rounding is that of the distance to the chunk, inside the overlap (the model: no join open
at any place). Not bit-identical elsewhere: moving each chunk by its old rounding error (under 0.0001 blocks) changes
which texel some pixels on a texel boundary land on, 0.01 to 0.26% of a frame's pixels, by a neighbouring texel's
colour (almost all under 12 levels of 255); beta's own picture changes the same way whenever the camera moves.
Cost (`tools/bench.py` configurations `fixes` and `fixesbeta`, Far, Max FPS, 854x480, 3 runs each, the PC in use;
`local/bench/seams-cost/`): median frame 1.54 against 1.34 ms standing, 1.38 against 1.29 turning, 2.02 against 1.93
flying, so 0.1 to 0.2 ms a frame for the per-chunk matrix calls (a push, a translation and a pop for each chunk drawn,
instead of one for each 1024-block region); nothing under vsync.

### 71. Lines over the block-breaking cracks (seen, not reproduced; set aside)
Reported by the user playing Still Faithful (2026-11-03), in their words or close to them: looking down, breaking the
top of a block, with the camera facing a diagonal direction, flickering lines flash over the breaking animation. It
looked like z-fighting (they first said "as if aliasing"), but it wasn't the cracks going behind their block and it
wasn't fighting with its block; it was across the whole crack, not at the block's edges, in bands parallel with the
screen (straight screen-aligned stripes). What was ruled out (`:harness:seamCheck -Pseams.crack`, the crack tiles
made opaque, each camera position drawn without and with the cracks): over nine views from diagonal to almost
straight down, standing on the block or two blocks off, at 20, 20, at 4100, 4100 and at -9000, -9000, with smooth and
with beta's dusk, the cracks never lost depth to their block (0 of 7.4 million crack pixels at each place), so a fix
for that (drawing the cracks through the block's own chunk transform) was dropped. Not yet tried: the user's real
settings in a hidden run (window size, the visible window's depth bits), the real crack textures and blending in
motion. **Set aside by the user** (2026-11-03); open. Next step if it returns: a screenshot or short recording of the
bands, ideally with F3 showing the position and the window's size.

### 72. Sky through the edges of some blocks' textures (T, measured)
Found measuring #70 (2026-11-03). A face's texture coordinates start exactly on its tile's edge in the terrain atlas
(`(tile + min * 16) / 256` in `BlockRenderManager`'s face methods) and end 0.01 texel short of the far edge. With
nearest sampling, a pixel just inside the starting edge can come out a hair below it in the graphics card's
interpolation and read the last texel of the tile before it. Where that texel is transparent, the terrain pass's
alpha test drops the pixel and the sky shows: a dot of sky as #70's, along the edge. Which faces: the atlas wraps
(`GL_REPEAT`), so the first row's tiles (grass top, stone, dirt, grass side, planks, slabs, bricks, TNT) read the
last row (the cracking stages and empty tiles, all transparent) along the top edge of their sides and one edge of
their tops and bottoms; grass tops also read the sapling beside them along another edge; wool, lapis blocks,
snowy grass sides and diamond ore read partly transparent neighbours. Measured with `seamCheck`: 8 to 21 hole pixels
over 160 frames in the test room with beta's drawing and 10 with #70's fix (0.06 a frame), on block edges of grass
tops and stone bottoms (plus one stray pixel at a pillar's corner); none once the atlas's transparent texels are made
opaque. Where the neighbouring texel is opaque the same slip gives a pixel of the neighbour's colour instead (atlas
bleeding), not measured. OF (a clear mistake), mid. **Fixed in Old Faithful** (2026-11-03, the user's go,
`render.textureEdges`, client): while a chunk is built, each finished quad's texture coordinates that lie exactly on a
tile edge at either end of its range move a thousandth of a texel inward (`TextureEdges`); quads whose range crosses a
tile edge, and beta's own 0.01-texel end, are left alone. Measured with #70's fix on both ways: 10 hole pixels over 160
frames at each of the three places without it, 0 with it (the stray pixel went too). Elsewhere 0.06% of a frame's
pixels change (875 to 963 of 1.44 million in each view's first frame), nearly all by under 25 levels of 255: pixels
on a texel boundary inside a face taking the neighbouring texel. Its cost is a few comparisons per quad while chunks
are built (reasoned, not benchmarked).

### 74. A step-up under a low roof sinks into the floor (T, seen)
Asked about by the user (2026-11-06), who remembered being moved slightly into a block and pushed out on a simple
one-block ledge, single player on Still Faithful; whether theirs had a block overhead isn't known yet. Later versions'
MC-72868, fixed in 1.8.1-pre1. `Entity.move`'s step-up rises at most `stepHeight`, moves, then always goes down the full
`stepHeight`, using a collision list gathered upward from the feet, which leaves out the floor being stood on (touching
isn't intersecting). When a block overhead clips the rise (2 air blocks: rise 0.2), the descent ends 0.3 below where it
began. Plain blocks can start it: on the tick after walking off an edge `onGround` is still true and gravity has put the
box below the edge, so that block stops the plain move and the step is tried and accepted. Once overlapping, `Box`'s
offsets ignore that block, so the player falls or walks further in until `ClientPlayerEntity.pushOutOfBlock`'s probes
(0.21 from the centre) are in it. The eye falls about 1.01 in the tick, with `cameraOffset` 0.71. In ice or glass, which
don't suffocate, nothing pushes the player out.
Seen on the real client (`:harness:ledgeHunt`, Still Faithful; scripted keys, so these aren't
rates in play): trench one wide, roof 2 above its floor, straight at full speed, 439 of 3000 crossings; roof 3 above or
none, 0 of 1500 each. Ledge, tapping off the edge and back: roof 2 above its top, 146 of 8000; a single block 2 above
the top over the low side, 132; a row over the ledge only, 0; roof 3 above or none, 0 of 8000 each. Same on plain, Pure,
Old and Still. In a standalone copy of `move`, limiting the descent to the actual rise gave 0 sinks over
the same runs. **Fixed in Old Faithful** (2026-11-06, `movement.stepUnderRoof`): the way down is as long as the way up.
With nothing overhead those are the same number, so ordinary steps don't change. A step with the rise clipped still lands
on anything it can reach: repeaters and trapdoors under a roof 2 up, soul sand to stone under one 3 up; too-tall ones are
refused as before (scenes H, identical with the switch off in all 56 cells). In the open a trench crossing is what it
always was; under a roof it now crosses the same way.
| Scene (ledge hunt, Still Faithful) | Beta (switch off; same on plain) | Fixed |
|---|---|---|
| D, trench, roof 2 above its rim | 439 of 3000 sink | 0 |
| D, one-block pit, same roof | 104 of 1500 | 0 |
| D, off a ledge and back, roof 2 above its top | 16 of 3000 | 0 |
| E, tap off a ledge, roof 2 above its top | 146 of 8000 | 0 |
| F, a single block overhead | 132 of 8000 | 0 |
| F, a row overhead on the low side | 118 of 8000 | 0 |
| Controls: roof 3 up, none, a row over the ledge only | 0 | 0 |
None of these took damage: the eye stays above the block it sank into. A zombie through its own `move` on the real server
(`ServerWorldFixesTest`): 64 of 400 walks across a trench under a roof sink, 0 fixed; in the open 0 and the same result both
ways. A server's player has no step height, so on a server only mobs are affected.
Digests over 38 million ticks (A, W, S, `docs/building.md`): open-air walks and wanders identical in all 4500 cells with
both switches on and off. With roofs, 94 of 4500 differ, all with a roof low enough to clip the rise; with both fixes, ticks overlapping a block go from 20,912 to 0.

### 75. Rejoining against a wall lets you walk into it (T, seen)
Found looking into #74 (2026-11-06). `Entity.read` calls `setPosition`, which rebuilds the box as x plus and minus
`width / 2`. Move translates the two faces separately, and the rebuild can land a face about 1e-14 past a wall the player
was touching; the next move ignores that block and walks in up to the probes. No roof needed and no camera drop. Real
save, quit and reopen beside a wall just past a power of two: 7 of 100, plain and Still; arbitrary scenes, 0 of 60 real
rejoins, though writing and reading back the position there gave 1132 of 67624. Why those walls expose it is a model's
reading, confirmed in a standalone copy of the arithmetic: inside one power of two both faces round alike, and only a walk
across one leaves the width a step off, which a rebuild from the middle then shows. Beside a wall as tall as the eye the walk in
suffocates: it may well be what #68's report was.
**Fixed in Old Faithful** (2026-11-06, `movement.boxAgainstWalls`): at the end of `Entity.setPosition`, a side face within 1e-7 past a
sixteenth (where every block's sides lie) goes back onto it. The worst rounding measured in that standalone copy was 3.7e-9, at
x 8.4 million.
Where the line is, and why: in `setPosition`, for every entity, not only at a load. A multiplayer login or correction goes through
`setPositionAndAngles` to the same rebuild, so a load-only fix leaves a server's players with the bug; a mob read from its chunk
has it too (10 of 1000 zombies on the real server, 0 fixed). Beds, portals and dismounts rebuild centred, away from walls, and
this is nothing to them. `Box` stays strict, so no collision anywhere is looser. No world read, so it's safe during a chunk's
load. A box only narrows, by at most 1e-7, and positions saved or sent don't change. Sides only: a floor's few bits come back
exact (1.79 million rebuilds with a camera offset, 0 below the floor). A wall that's another entity's box isn't covered.
| Scene (ledge hunt, Still Faithful) | Beta (switch off; same on plain) | Fixed |
|---|---|---|
| Q, write and read at a ledge past a power of two | 1632 of 20000 walk in | 0 |
| U, the same at a 4-high wall | 1982 of 20000, 5361 ticks of damage | 0, none |
| P, real rejoin at such a ledge | 7 of 100, no damage | 0 |
| T, real rejoin at such a wall | 10 of 100, 60 ticks of damage | 0, none |
| V, real rejoin at a 4-high wall anywhere | 0 of 60 | 0 |
| S, write and read at arbitrary scenes | 1132 of 67624 touching | 0 |
Digests: S differs in about a third of its cells, by design, since each holds 100 reads. Elsewhere only a placing can show it: 2 of 60
cells in F, a scene's start landing 7e-8 past a sixteenth by chance (replayed). A and W identical. A multiplayer login beside a
wall wasn't run; it's the same rebuild.

## The piston hunt

Claude Opus 5.5, 2026-11-02. A private test-only tool (about 6 minutes) runs the real b1.7.3 server with a test-only mod
(never shipped) in a flat, bare test world: each scene gets its own cell of air, a script of what happens tick
by tick (power is a redstone torch placed and taken away), 30 ticks to settle, then checks. An exception from a block
entity, block tick or entity, which would stop a vanilla server, is caught, recorded against its scene, and the hunt
goes on (`-Phunt.unguarded=true` lets it crash as vanilla does). Checked after every scene: moving-piston blocks left,
block entities on the wrong block, in the world's list but not their chunk, set over a live one or onto a block without
one; heads without their piston and extended pistons without their head; and a census of the cell before and after
(blocks, moving blocks by what they carry, falling blocks, dropped items), so anything duplicated or lost shows as a
difference. Then the server restarts and every chunk used is read back from disk. Output in
`local/run/piston-hunt/run-*/`: `hunt-report.txt`, `hunt-deltas.tsv` (every scene's census difference),
`hunt-reload.txt`. `-Phunt.only=<scene prefix> -Phunt.trace=true` prints a scene's every block change and piston action;
`-Phunt.mods=saves,fixes` adds our mods, to re-run after a fix.

The scenes, 25,848: every block id 1 to 96 with every data value 0 to 15, pushed and pulled in all six directions by
plain and sticky pistons (18,432); chains of 1, 2, 11, 12 and 13 of stone and of sand, ending in air, obsidian, a flower,
a piston or water; a piston in front of a piston in every pair of facings, retracted and extended, one or both powered;
eight power timings (a pulse in one tick, on-off-on in one tick, gaps of 1 to 3 ticks, flicker, re-powering mid-retract)
for eight blocks and a 12-chain; pistons at y 0 to 4 and 122 to 127 facing up, down and sideways; 35 special builds
(beds and doors both ways, rails, wire, repeaters, plates, torches, levers, buttons, ladders, signs and trapdoors on what's
pushed, cactus, cane, wheat, snow, flowers, a chest beside the path, an item, a pig and a minecart on what's pushed, a cart
in front, falling sand, water and lava beside the head, fire, TNT); chains across chunk borders; pushes and pulls with the scenes' chunks saved, or unloaded and loaded again, at each step of the
move; pistons working together (2,808); and 1,500 random machines of pistons, blocks, wire, rails, repeaters, chests
and torches coming and going.

Found: #53 and #54 (details kept private),
#55 (the lone door top), #67 (falling sand pushed into sand deletes it). Everything else came out clean: no exception in
normal play, no duplication, no other block lost in any single-piston scene, chain, timing, height, special build or
save (census differences there are breakable blocks dropping as they should, and cells cut short by the scene's own
edges); every timing leaves pistons and heads
consistent; saves and unloads at any step of a move load back and finish (the moving block entity is saved and resumes).
The one exception found in a loaded world needs data no game makes: #23's facings 6 and 7, for a piston (build and
push) and a head (any neighbour change); and a lone moving-piston block or head placed by hand stays as it is (edited
worlds only). Beta 1.8's unnamed piston crash fixes most likely include #53.

Re-run with `oldfaithful-saves` and `oldfaithful-fixes` (2026-11-02, after #55, #65, #67 and the rest were fixed): the
same 955 scenes with problems (#23's out-of-range data, #54's contests, the fuzz machines' #54 cases) and nothing new;
#53's faults gone, #67's chains keep their sand, a lone door top no longer takes the block below with it (it goes
alone, as a top half does). The fuzz machines' census moved a few blocks either way in about 1,100 of 1,500: their
drops scatter on the world's random numbers, which the fixes draw differently (a door top no longer drops a door);
the totals are within a few percent and no machine gained a problem.

Not a bug in vanilla, a trap for mods: a chunk unloaded and loaded again within one tick, before block entities tick,
loses its block entities. The world's tick drops the unloaded chunk's old block entities from its list by position
(`World.tickEntities`: `removeBlockEntityAt` on whatever chunk is there now), so it removes the reloaded chunk's fresh
ones: chests emptied, moving blocks stuck as block 36 (seen in the hunt by forcing it). Vanilla never loads a chunk
between the unload (`chunkSource.tick`, early in `World.tick`) and that cleanup, on a server or in single player; a mod
that loads chunks in the world's tick could.

## Still thin

The hole in #16b that lasts until relog wants a capture of one. Rails, crops, doors and redstone were read in the bug
hunt (2026-11-02): #41, #42, #44. Where more hunting would pay off, after the piston hunt (2026-11-02):
- Pistons in multiplayer: the client runs each piston action again on its own world (`ClientNetworkHandler.onPlayNoteSound`),
  so a client that disagrees with the server about the cells (ghost blocks, #5; holes, #16b) can lose or keep blocks
  differently; the hunt was server-only. Hardening's C1 turns a throw there into a disconnect.
- More piston interactions like #54.
- Sand and gravel generators and other entity-heavy machines (#67 was found by a scene not built for it); minecarts
  and boats pushed by pistons; items and mobs in moving blocks.
- Boat and minecart physics beyond #12 and #31, riders' suffocation, `ItemEntity` and flying mobs' movement; a server
  check of #63 (slimes in walls). (#64's mirrored spawning was checked on the server, 2026-11-02.)
