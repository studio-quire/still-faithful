# Still Faithful (the third edition)

The third edition (CLAUDE.md > Editions): beta with polish, as it might have been. Old Faithful plus what later
versions improved, still beta in content. Entry numbers are `docs/bugs.md`'s. Decisions are the user's (2026-11-02)
unless marked open. Built from 2026-11-02 by Claude Opus 5.5. Public since 0.1.0 (2026-11-03, on Old Faithful 0.1.2
and Pure Faithful 0.1.3): github.com/studio-quire/still-faithful, studioquire.org/still-faithful/; no Modrinth
(`docs/release.md` > Modrinth).

## Shape

- Its own mod, `oldfaithful-polish/` (Gradle name), public id `stillfaithful-polish` (`oldfaithful_edition=still` in
  its `gradle.properties`, which also holds the edition's version and public words, so they stay out of the other
  editions' public snapshots with the folder). Both sides. Built like `oldfaithful-fixes` (`docs/fixes.md` > Shape): one
  switch per change in `config/stillfaithful-polish.properties`, all on, written with explanations on first start;
  `PolishConfig` (the mixin config plugin) skips a switched-off change's mixins, each change's mixins in their own
  package under `mixin/`. `mixin/edition` is always in.
- On the settings screen as a section of its own in Changes, **Polish** (`PolishSettings`), each button "Name: Polished / Beta";
  every change waits for a restart, as the fixes do. The edition renames Old Faithful's screen: "Still Faithful..."
  in Options and the screen's title (`mixin/edition`, into the options mod's classes, only where it's installed).
- The title line reads "Still Faithful <version>": Pure Faithful's hardening mod draws it and names the first
  edition mod by id (`oldfaithful-fixes` wins over `stillfaithful-polish`), so the polish mod rewrites its text
  (`BrandingMixin`) rather than having a released Pure Faithful changed.
- Builds: `./gw releaseStill` (`gradle/still-faithful.gradle`) writes `local/release/still-faithful-<v>/`: the pack
  `StillFaithful-<v>.zip` (a Prism instance with all three editions' mods, its dusk violet seal), the server package
  and the mod, on released Pure Faithful and Old Faithful parts (checked) and under the release guard
  (`docs/release.md`). `./gw prismStillInstance` writes `local/prism/StillFaithful-dev.zip` beside the other dev
  instances. Texts in
  `release/still-faithful/`. The icon (`prism/release/icons/final/stillfaithful-*`): the family's β seal in dusk violet with Old
  Faithful's silver rim, `07-still-a` (the user's pick, 2026-11-02), made by `tools/icon_candidates.py`.

## The changes

| # | Switch | Side | How |
|---|---|---|---|
| 13 | `blocks.stairsDropStairs` | world | `StairsBlock.dropStacks` drops one of the stairs (same chance, same one random draw as beta's single drop) instead of handing it to the base block |
| 24 | `leaves.placedStay` | world | Placed leaves get data `kind | 4` (`PlacedLeaves`) instead of beta's `kind | 8` (marked to decay at once); leaves with bit 4 never decay. Bit 4 is unused in beta (every reader masks with 3 or tests 1, 2, 8), later versions use it the same way; a vanilla game reading the world sees ordinary leaves |
| 10 note | `glowstone.anyTool` | both | `PlayerInventory.isUsingEffectiveTool` is true for glowstone: 1.0's drop and 1.0's speed by hand (about 10 ticks; beta 31 and nothing). Multiplayer: the server marks its login packet ('g', `ServerMarker`, the fixes' format, so both marks share the field); a client mines faster only there |
| 20 | `liquids.lavaThins` | world | Where beta draws its 3-in-4 refusal to thin flowing lava (and then schedules nothing, so cut-off lava lingers), the lava thins and its next tick comes 4 times later, as later versions do; the same draw is made, so the world's random runs as before. Lava already stuck in a world goes once something beside it changes |
| 20 | `liquids.waterSources` | world | The "still water below" test reads the block below's data, not the flowing block's own |
| 34 | `render.droppedItemSize` | client | Dropped non-cube blocks (stairs, fences, cactus, plates, buttons, trapdoors) at a quarter scale like every dropped block, not a half |
| - | `menu.texturePacks` | client | The title screen's Mods and Texture Packs button reads Texture Packs, as later betas renamed it (user, 2026-11-05) |
| 34 | `render.bowPose` | client | Players' and skeletons' bows take the held-tool path, shifted and turned (`BowPose`); third person only |
| 12 | `boats.sturdy` | world | A break needs beta's own condition (a sideways collision while still moving over 0.15 along the free axis) and a real crash: at least 0.25 blocks a tick lost into what it hit. So every break here would also be one in beta; scrapes along a shore or an item never break a boat. Items and arrows aren't solid to boats (as #31 for carts) |
| 12 | `boats.handling` | world | In water, with nobody paddling (no rider, or a rider not moving), speed × 0.96 a tick on top of beta's 0.99: from top speed it stops in about 8 blocks, not about 40 (measured 7.7 against 18.1 in 60 ticks). At the surface, vertical speed × 0.8 a tick (rides steadily instead of bobbing). Paddling is beta's: same top speed, acceleration, turning |
| 31 | `minecarts.cartCollisions` | world | Whether two touching carts exchange speed is decided by the rail under the cart, not beta's broken gate: they do when the other cart lies within about 37 degrees of the line the rail runs (`Minecarts`, below). Every axis behaves as east-west track did |
| 4 | `animals.keepTended` | world | Animals a player has dealt with never despawn (`KeptAnimals`, below) |
| 69 | `blocks.chestsFaceYou` | world (drawn on the client) | A placed chest keeps a facing toward the placer in its data, 2 to 5 as a furnace's, and is drawn from it (`Chests`); a pair shares one across its length. Data 0, every chest from before, keeps beta's rule from the blocks around. Vanilla draws every chest by beta's rule |
| | `skins.modern` | client | 64x64 skins kept whole and drawn as a modern client draws them, on a player model of their own; 64x32 skins stay beta's, processor and model (`ModernSkin`, `ModernPlayerModel`, below) |
| 14 | `saves.backgroundAutosave` | world | Chunk saves compressed and written on a thread of their own (`BackgroundSaves`, below); needs Pure Faithful's saves mod |
| 6 | `sky.dusk` | client | `smooth` (default), `stepped` or `beta`. Light applied when the chunks are drawn, not baked into them, so no chunk rebuilds: stepped draws beta's own picture at each whole darkness step, the whole view at once; smooth glides between them (below). Needs OpenGL 2.0 |

"World" means where the world runs: the server in multiplayer, the client in single player.

### Dusk and dawn without rebuilds (#6)

The switch is a choice (user, 2026-11-03): **Smooth** (the default, once its fog followed the terrain; the user
first found smooth dusk "a little weird" with the fog stepping) glides; **Stepped** keeps beta's eleven steps, each
one beta's own picture, but the whole view changes at once with no rebuild wave; **Beta** is beta's baking and rebuilds (the dusk mixins left out). On the screen,
"Dusk: Stepped / Smooth / Beta"; Stepped and Smooth switch live (the same draw-time light, only the darkness it's
drawn at differs: `DuskLight.smooth`), to or from Beta waits for a restart (the options mod's `Setting` with both
`applyNow` and `running`). Stepped draws at the world's own `ambientDarkness`, the whole value beta bakes with and
lights entities, particles, the hand and the fog's brightness with, so every frame is one step throughout; the sky,
clouds and fog colour were continuous in beta and stay so.

Built by a Claude Opus 5.5 subagent, 2026-11-02 (`DuskLight`'s class comment has the full design). Each chunk vertex
stores the light it was lit from (up to four sky/block pairs in the tessellator's spare int, the colour's lost
fraction in another) instead of a baked brightness, and a vertex-only GLSL 1.20 program (`DuskShader`) applies a
continuous darkness when the chunk lists are drawn (`ftransform()`, the texture matrix, fog distance as beta sets it,
radial with NV fog; fragments stay fixed-function, and the colour is cut to a byte as the tessellator cuts it).
The pairs come from beta's own renderers without reimplementing them: every light reading is max(sky - darkness,
block) at some position, and renderer colours are linear in the luminances they read, so each block is drawn once
with every luminance 1 (tint and shade) and, if it read more than one pair, again without output with each pair as a
power of five, the colour ratio decoding into quarter-weights. That covers smooth and flat lighting, Old Faithful's
underside fix, torches, fluids (one small hook) and every special renderer. A block whose light ignores the sky keeps
beta's colours; a vertex that doesn't decode (a modded renderer doing something non-linear) keeps beta's, and its
chunk keeps beta's rebuild per step. Entities, block entities, particles, the hand and rain and snow read the same
darkness (beta's reading at the two whole steps either side, mixed). A darkness step marks no chunk: over a
compressed dusk beta marked about 260-270 chunks in 11 steps, this none. Cost: 26-40% of blocks take the second,
no-output pass; chunk building measured 0-20% slower (noisy); the shader's frame cost wasn't measured. The fog's
brightness (below Far view distance beta darkens the fog and the horizon by the light at the camera, eased a tenth a
tick) reads the same darkness too (2026-11-03, the user's ask): in smooth dusk it glides with the terrain instead of
stepping and easing after each of beta's steps; in stepped it's beta's. At Far that brightness is always 1, and the
fog colour followed the time of day continuously already.
Limits: run only on our LWJGL component and an NVIDIA GPU (the GL20 calls exist in LWJGL 2, unrun there; without
NV_fog_distance the shader assumes eye-plane fog); Advanced OpenGL and anaglyph reasoned, not tested; a mod that meshes
chunks off the main thread, or SmoothBeta (which hooks the tessellator during chunk builds), won't compose: switch it
off for those.

### Modern skins

Pure Faithful's hardening fetches real skins, 64x64 included, but beta's `SkinImageProcessor` draws them on a 64x32
canvas and `ModelPart` divides V by 32. Client-only: nothing from the server or other players. No setting beyond the
switch. Built 2026-11-05 by Claude Opus 5.5; drawn as a modern client draws them since the same day, after the user
asked whether the far faces of a layer looked the same.
- **Whole skins.** The skin's download gets `ModernSkin.processor` instead of beta's (capes, through the same call, keep
  beta's). A 64x64 image becomes a `ModernSkin` (a BufferedImage, which is the mark); anything else goes to beta's
  processor, so classic skins are beta's exactly.
- **Transparency.** As 26.3's `SkinTextureDownloader`: head, body and limbs opaque, each pixel in the colour the file
  holds even where the file makes it transparent (beta's draw-over would turn it black); layers exactly as painted, holes
  and half-transparent pixels too. Beta's hat rule (no transparent pixel at all and the area is cleared) is for 64x32
  skins there, so a layer painted wholly solid shows.
- **The model.** `PlayerEntityRenderer` keeps beta's model and two `ModernPlayerModel`s (wide, slim), and sets `model` and
  `bipedModel` to the one for the player being drawn, from what the texture manager holds for them at that moment, then
  puts beta's back. Zombies, skeletons, armour and anything else on `BipedEntityModel` are untouched, and a player
  still waiting for a skin stays on beta's model and char.png. It's 26.3's layout (`PlayerModel`): left limbs of their
  own, layers 0.25 out (the hat stays 0.5, and stays where beta leaves it when sneaking), slim arms 3 wide, legs 0.1
  inward of beta's. Faces: V halved, right to the pixel's edge (beta stops a tenth short), and each box's bottom face
  turned over.
- **How it's drawn** (`ModernPlayerModel.render`). All from 26.3, whose `PlayerModel` uses `entityTranslucent`: blended
  (source alpha), alpha under 0.1 discarded, depth written, no culling, and all of the model's quads sorted by their
  middle's distance, farthest first (`sortOnUpload`; orthographic: by depth, the inventory's figure). So twelve parts'
  faces are collected, sorted and drawn each frame with GL's own calls, not display lists or the tessellator, whose
  `normal()` ruins a normal with a negative x (a rolled arm's top went dark). A skin with no half-transparent pixels
  looks as it did, sorted or not; a half-transparent one now blends rather than showing as solid. The last is the look
  most unlike beta, and the user's call if they don't want it. Not "improved transparency", 26.3's order-independent
  option, which is off by default.
- **Far faces.** Since 1.21.9 `entity.fsh` has `PER_FACE_LIGHTING`: `gl_FrontFacing` picks the normal's light or its
  negative, so a face seen from behind is lit as if facing you, which fixed-function GL calls two-sided lighting (on
  around the model, off after). Through 1.21.8 (no such thing in the shaders, read from the jars) a face was lit by its
  outward normal from both sides, as beta's fixed function does. It only shows on faces with a vertical component, since
  beta's and modern's two lights mirror each other horizontally: the insides of tops and bottoms, and limbs and heads
  when tilted. skinview3d, a second reference, also draws its layers double-sided, transparent, with an alpha test
  (`model.ts`), though with a light model of its own.
- **Slim arms.** The flag is in the profile's textures metadata (`"metadata":{"model":"slim"}` under SKIN, absent for
  wide; read from jeb_ and MHF_Alex, 2026-11-05). A mixin at hardening's `Skins.parseProfile` reads it out of the
  response hardening already fetched, so Pure Faithful isn't changed and there's no request of ours: its HTTPS, size
  checks and one lookup per name are all there is, and a server can do nothing it couldn't before. A lookup of our own
  would double the calls to a rate-limited service; reading the arm's unused pixels only guesses. Without hardening
  there are no skins, and the mixin isn't applied.
- **Poses.** Layers copy their limb's pivot, rotation and visibility after `setAngles`, which includes Faithful Core's
  `PlayerRender.onPose` listeners. In a slim arm, what's held moves half a pixel inward, as later versions do.
- **The hand.** The first-person hand is the one thing drawn outside the renderer's `render`, so `HeldItemRenderer` chooses
  the model around `renderHand`, before it's entered: Old Faithful's riding fix clears `riding` at its start, on whichever
  model is then in place. Arm and sleeve go through the same drawing, with culling off (26.3's `renderHand` uses
  `entityTranslucent` too, read), the alpha test on, and each put back as found. The hand keeps beta's pose, and a map's
  two hands both show the right arm's texture, as beta's `renderHand` has it; later versions draw the left arm there.
- **Left as they are.** What's drawn after the player meets a half-transparent pixel's depth, so water, particles and
  a later entity don't show through it, nor what the figure holds or wears behind one: 26.3 orders translucent things by
  distance, beta's frame has no such pass.
- **Comparing with a real client.** `tools/modern-skins/`: `fetch.py` gets a client as a launcher would, `run.sh` runs
  it unseen with no account or network, a Java agent (`Driver.java`) giving each view `SkinCheck` wrote to mannequins
  wearing the test skins through the client's own downloader, and `sheet.py` puts the pictures side by side and counts
  the figure's pixels that differ. Read and run for behaviour only; none of Mojang's code is copied or kept in the repo.
  2026-11-05, 26.3 against this build, six skins from up to sixteen views: inside the figure the colours are the same to a
  level or two, half-transparent ones included, and what differs is lines along edges, the figure a pixel or two off at
  these distances. Beta's own placement stays, and 26.3 has no first-person or inventory picture here: the hand is read
  from its code, and the inventory's figure keeps beta's lights, its head darker on one side than a classic skin's
  because beta's display lists turn every +x face's normal to -x and ours don't.

### Carts meeting carts (#31)

Built 2026-11-02 by Claude Opus 5.5 (`Minecarts`' comment has the detail). Beta's gate in `MinecartEntity.onCollision`,
`(dx * other.velocityZ + dz * other.prevX)^2 > 5`, skips the exchange (bumping, pushing, a furnace cart driving a train,
the booster) and leaves the carts as plain boxes, walls to each other. In line on east-west track dz is 0 and it never
fires; side by side on parallel east-west tracks dz is about 1 times an x in the hundreds, so it fires, as meant. So
east-west track had the intended behaviour; on north-south track it fires for carts in line too (except within a few
blocks of x = 0), and on curves on either axis.
- The rule: the exchange happens when the other cart lies within about 37 degrees (cosine 0.8, later versions' figure,
  which they take from the cart's facing) of the line the cart's rail runs; a curve's line is its chord, which beta's
  carts run along; off the rails, the cart's facing. The rail rather than the facing because a cart that has never
  moved faces east on any track. Each touching pair runs it both ways a tick, each with its own rail, so at a curve's
  ends one way counts.
- Effect: east-west track exactly as beta (the same numbers with the switch on and off); north-south and curved track
  now the same as east-west: bumping, furnace trains, head-on carts bouncing apart, boosters; carts on parallel tracks
  leave each other alone everywhere. In existing worlds, piles of overlapping carts parked on north-south or curved
  track come alive when something moves them.
- Done as the gate's limit (the constant 5.0 becomes infinite when lined up, -1 when not), so UniTweaks' and
  RetroTweaks' "Minecart Boosters" option, which zeroes the gate's value, changes nothing beside it.
- Multiplayer: the exchange runs only where the world runs (`onCollision` returns at once on a client), so a server
  with the switch decides for every client, vanilla ones included; a client predicts a cart's rail motion between the
  server's updates, never collisions. Reasoned from the code, not played between a real client and server.
- Boosters overflow (the trace's open question, now measured): two overlapping carts rolling together gain about 1.38
  a tick (two exchanges of 1.2, less friction), the speed they store (their pace stays 0.4 a tick) passes 1.3e154 after
  about 1,100 ticks (empty, about 440 blocks; carrying pigs, which slow the gain, about 2,000 ticks), where the rail
  code's squares overflow: Infinity, then NaN for a tick. Beta's own guards end it harmlessly: `Entity.move` zeroes a
  NaN speed and `World.updateEntity` puts back a NaN or infinite position, so the pair stops dead, one cart a few blocks
  on. On the real server with the switch (both axes) and without it (east-west): no position ever NaN, the pigs still
  riding, no errors, the world saved on stopping. Beta's behaviour on east-west track, now on every axis; left as it is
  (no crash or damage; a cap on the stored speed would make such a pair roll on forever instead).

### Animals that stay (#4): what "dealt with" means

Beta has no feeding, breeding or leads; players herd animals by punching them along, carry them in minecarts, and
shear, milk, saddle and ride them. So an animal is kept for good once:
- a right-click on it does something: milked (the bucket changes), sheared (the shears wear), ridden (a saddled pig),
  or an item it takes (a saddle or dye is used up);
- it rides something or is ridden (pushed into a minecart);
- it hatched from an egg a player threw.

Cows, pigs, sheep and chickens; wolves keep beta's rule (tamed ones already stay; a wild one you hit turns hostile).
Not kept: animals a player hits (the user's call, 2026-11-02: hit animals needn't stay), and animals fenced in without
being tended (so a penned wild chicken, or a pig without a saddle, still despawns). The mark is saved as `PersistenceRequired` (later versions' tag for the same thing, so a
world carried forward keeps them); beta ignores and drops it. Kept animals count towards beta's animal cap (about 17
around one player), so a big farm near you means fewer wild animals spawning nearby; Beta 1.8 changed spawning to
go with keeping animals, which isn't taken here.

### Autosave off the game thread (#14)

With the saves mod a chunk save costs the game thread about 1 ms, mostly compression (`docs/saves.md` > Tests,
measured), and an autosave saves up to 24 chunks: up to ~25 ms every 2 seconds while building or exploring, and the
same for chunks unloading. Here the chunk's save data (the same NBT, built by the same code other mods add to, at
the same moment) is still made on the game thread; compressing and writing move to one writer thread, which starts at
once, so a save is on disk when beta's would have been (beta stopped the game meanwhile) and then as durable as the
saves mod makes it. Why it can't tear or lose a save (`BackgroundSaves`'s comment has the detail):
- every chunk save (autosave, unload, full save) goes through one queue and one writer, in order;
- a chunk loading waits for its own queued save first;
- the region file is taken on the game thread and held until written; every closing of region files
  (`RegionIo.flush`) waits for the queue first (the writer never takes RegionIo's lock, so no deadlock);
- all region reads and writes are under the region's own lock (vanilla's synchronized methods, which the saves mod's
  writer uses);
- a full save (leaving, save-all, stopping) waits for the queue in the storage's flush, before the saves mod forces
  everything to disk; the save snapshot is taken in one tick, as beta's;
- a hard kill mid-write is a crash mid-write, harmless with the saves mod; a shutdown waits up to 10 s for the queue.
Without the saves mod the switch does nothing (beta's own writer isn't crash-safe, so writes stay synchronous).

## Tests

- `./gw :harness:clientStartupTestStill`: the hidden start-up test with all three editions' mods and the polish
  mod's test-only `selftest` mod (`StillCheck`, a `WorldCheck`), after Old Faithful's `FixesCheck`; then the options
  and settings checks (the Polish section, "Still Faithful...", every label fitting). Each check expects the change
  where its switch was on at start and beta's behaviour where off; `-Pstill.beta` starts with every switch off, and
  every check came out beta's way (2026-11-02): stairs dropped planks and cobblestone; placed birch leaves had data 10
  and decayed; glowstone took 32 ticks by hand and dropped nothing; cut-off lava stayed at level 2 on the refusal
  draw; water between two sources over still water stayed level 1; every touched animal despawned 200 blocks from the
  player; the sliding boat broke; the unpaddled boat glided 18.1 blocks; the writer thread wrote nothing. With the
  switches on: stairs drop themselves, placed leaves get data 6 and stay (also when a broken log marks them), a tree's
  leaves still decay, glowstone by hand in 10 ticks drops dust, the lava thins to 6, the water becomes a source, a
  sheep sheared, a pig saddled and a chicken read back with the mark stay while a hit cow and an untouched one despawn,
  a boat sliding along a wall at 0.35 holds while one driven in at 45 degrees breaks, an unpaddled boat stops in 7.7
  blocks, and a full save after changing a block writes it (read back with the game's reader) through the writer
  thread. #31 (2026-11-02): `CartScenes` (shared with the server test) on an east-west and a north-south axis, both
  more than 40 blocks from x = 0: a cart into a standing one, a furnace cart behind a cart, two overlapping carts
  rolling together, carts head-on, a cart past a standing one on the next track, a cart into one standing on a curve.
  Polished: all meet on both axes (knocked on 2.76 blocks, the train pushed 9.12, the booster's stored speed 10.2 after
  10 ticks, head-on 2.92 apart, the curve's cart knocked on 1.24), the parallel one left alone. Beta: the same on
  east-west track except the curve; on north-south track and on the curve, walls (nothing moved, head-on stopped
  touching, the booster only slowing, 0.266). The boat basins have a roof since (open still water froze in cold
  biomes and stopped the coasting boat in some worlds, beta's way). Output in `local/run/client-startup-still/`.
- `DuskCheck` (#6, in the same run, a second WorldCheck): a scene of torches, glowstone, lava, fire, a jack-o'-lantern,
  a powered repeater, water, glass, ice, leaves, tinted grass, plants, slabs, stairs, fences with raised undersides,
  rails, redstone, a lever, ladder, door, bed, piston, a torch-lit room under open sky, a chest, a minecart and block
  cracks, drawn in the same hidden window with beta's baking and then with smooth dusk, compared pixel by pixel at
  whole darkness steps with animations, particles, clouds, weather and fog held still. Largest difference 1/255 (0 of
  600,000 pixels further off) at steps 0, 3, 4, 7 and 11 in smooth and flat, Fancy and Fast, and the Nether's
  (`-Pstill.duskFull`, about 5 minutes; the default run does smooth-Fancy and flat-Fast at 0, 4, 5, 11, about 1.5
  minutes in all); halfway between steps every pixel lies between beta's two pictures (within 4/255, for entity
  shadows); no vertex fell back; dusk marks no chunk. Stepped dusk (2026-11-03), in smooth-Fancy: with the time of
  day running its own course (no darkness set by the test) at the middle of steps 0, 4, 5 and 11, where smooth would
  draw 0, 4.5, 5.5 and 11, it drew at 0, 4, 5 and 11 and matched beta's baking there to 1/255; dusk played through
  marks no chunk in either mode, and stepped frames are drawn at whole steps only, smooth ones between. The pictures
  are in `local/run/client-startup-still/dusk/`.
- `SkinCheck` (modern skins, the same run, a third WorldCheck): generated skins (`TestSkins`: a loud one, a part and
  layer to each of twelve hues with a black and a white corner on every face; wide, slim, classic 64x32; a realistic
  one, wide and slim; one with a cape; and for looking through a layer, a sieve of holes and rows of alpha with a hue to
  each face, one with only tops and bottoms, one half-transparent, one painted with no transparency) answered where hardening meets the network (`Https.send`), so its name lookup,
  profile, decode and the game's download thread run as for a real player; figures that hold a pose, against a white
  wool backdrop. Checks: what each skin arrives as, one name lookup and profile each, slim from the profile's flag
  (also for the same name in another case), capes on beta's canvas, the transparency rules on the processor; in the
  front and back pictures of the loud skins, every part's hue present or absent, on the side of the figure it belongs
  to, a slim arm 0.80 of a wide one's width; the first-person sleeve; a layer's inside lit as modern lights it, from below and above; half-transparent pixels
  blending; a solid layer showing; a transparent base pixel's colour; GL's state as found after a model and a hand,
  from sixteen starting states (the picture checks and the processor's fail on the code before this, seen 2026-11-05).
  Pictures in `skins/` of the run folder: every skin
  from four sides, the loud ones from above and below, walking, swinging, sneaking, riding, sleeping, hurt, with a bow,
  a sword, a block, armour, armour while riding, the cape from behind, zombies and skeletons, and the player's own
  hand, map, third person and inventory figure in each skin; `skins/compare/` the views `tools/modern-skins` also takes
  a real client through. 2026-11-05, both ways: with the switch everything as
  above; with `-Pstill.beta` every skin came out 64x32 on beta's model, mirrored, no sleeve, and the classic skin's and
  the zombies' pictures matched the switched-on run's to the pixel in one pair of runs and within 1/255 (2 on the hand)
  in another, across the backdrop too: smooth dusk's light against beta's (the bow pose is off there too; walking
  differs by the frame's place in its tick). SkinCheck turns vsync off while it runs and puts it back: the start-up test
  keeps it on for the settings checks, and hidden clients waiting on the refresh are the stalls' trigger (root
  `docs/gpu-resets.md`); two of six runs without that ended in a stall, neither of the two with it did. The real path: jeb_ and Notch (64x32: beta's), MHF_Alex and MHF_Steve (64x64, both
  slim in their profiles) through hardening's own client and parser, outside the game.
- `./gw :oldfaithful-saves:serverSaveTest -Pstill.saves`: the saves mod's real-server test with the polish mod beside
  it (damaged chunks; the server killed outright 8 times during bursts of save-all, every chunk and level.dat read
  back with vanilla's reader after each, nothing gone): passed 2026-11-02 with the background writer doing the
  writing.
- `./gw :oldfaithful-polish:serverCartsTest` (#31, about 4 minutes): the real dedicated server with the scenario mod
  (`src/scenario`, `CartScenario`) and the polish mod, then without it, each in a new world: the same scenes as the
  start-up test on both axes, then four long boosters (an empty pair and a pair carrying pigs on each axis, 960 blocks
  of track) followed for 2,200 ticks. Every scene as predicted both ways; the boosters overflow and stop where carts
  meet (Infinity at tick 1,099 empty, 1,984 with pigs), north-south ones without the mod never boost; no position NaN,
  pigs riding, no server errors. `-Ppolish.run=<ticks>` shortens it, `-Ppolish.debug` prints a booster tick by tick.
  Passed 2026-11-02.
- `serverTest`, `clientAudit` and `clientAuditSdl` carry the mod, so every mixin is applied and audited.
- Not checked by a test: how modern skins look on real players and between a real client and server (reasoned:
  nothing goes over the network, each client draws what it fetched); the bow pose and dropped item sizes (what's seen: the user's), boat handling's feel and the
  bobbing (the user's), glowstone between a real client and server (the marker and gating mirror #9/#10's, audited and
  reasoned), animals kept by riding a minecart or hatched from an egg (the same keep call, reasoned).

## Kept as beta
- Eating at full health stays possible (#35): mods add foods with other effects, and blocking eating would need
  adapting for each.
- The fence hitbox ("smart farms", #30).
- New worlds spawning near 0,0 on servers with the Nether on (#52).
- Torches drawn at full brightness (#34): deliberate in `renderTorch`; the user likes it, the glow is how they
  remember early versions.
- Clouds passing through tall terrain (#34): a feature, they float at one height, as in later versions.
- Multiplayer: the mining click tempo (#36), other players' 11° turning steps (#50), not holding a player still
  after a teleport (#16b).
- Closing the window without saving: players use it to undo item loss.
- The vignette's banding at night (user, 2026-11-03): beta's own. Its texture is a smooth fade, but it multiplies a
  dark 8-bit frame, which has only a few levels to step through, so the fade shows as rings.

## Open (traced 2026-11-02)

The user's calls (2026-11-02): #31 fixed in Still Faithful on a switch, as recommended (built: The changes); #63
stays beta; #66's generation slips skipped. The spawner's corner-centred area: asked whether it matters.


- **#63, slimes skip the room check.** Slimes spawn below y 16 (not 40), in slime chunks (1 in 10), a further 1 in 10;
  sizes 1, 2, 4 equally; a size 4 is worth about 16 slimeballs. With the room check, size 2 fails beside any wall and
  size 4 needs a clear 3×3×3 space; in the common 2-high stacked farm layers every size-4 spawn would fail, and those
  are most of the output (about 80% fewer balls; open rooms 3+ high about 22% fewer). In beta a size 4 stuck in a
  2-high layer suffocates in about 8 s and splits, so walls convert big slimes rather than wasting them.
  Recommendation: leave as beta (at most a switch defaulting to beta): the visible gain is small, the cost falls on
  existing farms and scarce slimeballs.
- **#66, generation slips.** The Nether's decoration is seeded from chunk coordinates only (never the world seed), so
  no seed reproduces it today; fixing can't break a seed, affects new chunks only, decoration only: fine for Still
  Faithful but low value. Clay: the vein is centred 8 blocks off the water it tested (copied ore offset); fixing shifts
  random draws for everything decorated after it (ores, trees) in new chunks: leave. Climate sampled off the lattice
  (blocks 1, 4, 7, 10, 13 instead of 0, 4, 8, 12, 16; a small step at every chunk seam): fixing changes every new
  chunk's terrain against the seed: leave (seeds matter to beta players). The spawner's area centred on its corner
  isn't generation at all (runs in play, no seeds): Still Faithful on a switch would be fine (it shifts where spawner
  farms succeed, so not Old Faithful). If a generation fix is ever wanted, it should be a per-world setting fixed at
  creation and kept in level.dat, so no world mixes rules.
