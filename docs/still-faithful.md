# Still Faithful (the third edition)

The third edition: beta with polish, as it might have been. Old Faithful plus what later
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
| 12 | `boats.handling` | world | In water, with nobody paddling (no rider, or a rider not moving), speed × 0.96 a tick on top of beta's 0.99: from top speed it stops in about 8 blocks, not about 40 (measured 7.7 against 18.1 in 60 ticks). Partly in the water, a boat comes to rest 0.1885 over the surface, where beta's bobbed about 0.1 up and down around 0.185 for good: its push up worked out from where the surface lies against it instead of beta's count of wet fifths, and its rise and fall × 0.8 a tick (below). Paddling is beta's: same top speed, acceleration, turning. A boat `boats.steering` moves has that switch's momentum instead of the coasting; the float applies to every boat |
| | `boats.steering` | world | Later versions' controls (below): A and D turn the boat and W paddles it along its heading, at most 0.325 a tick; in water every boat a player isn't paddling as beta's keeps 0.9 of its speed a tick; a placed boat faces the placer's look (`BoatSteering`). On a server, only for a rider whose client sends its look every riding tick (#83) |
| 31 | `minecarts.cartCollisions` | world | Whether two touching carts exchange speed is decided by the rail under the cart, not beta's broken gate: they do when the other cart lies within about 37 degrees of the line the rail runs (`Minecarts`, below). Every axis behaves as east-west track did |
| 4 | `animals.keepTended` | world | Animals a player has looked after never despawn (`KeptAnimals`, below) |
| 69 | `blocks.chestsFaceYou` | world (drawn on the client) | A placed chest keeps a facing toward the placer in its data, 2 to 5 as a furnace's, and is drawn from it (`Chests`); a pair shares one across its length. Data 0, every chest from before, keeps beta's rule from the blocks around. Vanilla draws every chest by beta's rule. With `blocks.chestPairs`, placing goes by its rule instead |
| | `blocks.chestPairs` | world (drawn on the client) | Chests pair as later versions pair them (below): a chest placed joins one beside it only if that one faces the same way, stands to its left or right and isn't joined, and never while sneaking; a chest can go anywhere, beside a double chest too. A chest joined so says who to in its data (6 to 13). Needs `blocks.chestsFaceYou`, as pairs go by facing |
| | `render.chestLids` | client | A double chest's top and bottom are one lid: four half-lids made from the texture pack's lid in tiles beta leaves as placeholders (below) |
| 30 | `blocks.fenceShape` | both (aiming: client) | A fence is a post (beta's own, 6/16 to 10/16) with an arm as wide to each fence beside it, as later versions shape it: 1.5 high to bump into, 1 high to aim at, with an outline of the post and arms (`Fences`, below) |
| | `render.chatBars` | client | Each chat line's dark bar runs from 1 before its text (beta's text starts at x 2; the gap matches the right's, the user's call, 2026-11-08) to just past its end (the text's width, which counts its shadow, and 1 more, the 1 beta leaves above the text), not beta's 320 for every line; in play and with the chat open (`chat.InGameHudMixin`). Height, colour, fade and wrapping are beta's; an empty line has no bar; the open chat's typing bar stays full width (the user's ask, 2026-11-07) |
| | `render.particleDistance` | client | Particles made within 32 blocks of the camera, not beta's 16, and a puff (a blast's, a mob dying or appearing) within 64 (below) |
| | `render.entityDistance` | client | Everything drawn within 64 blocks at least (a zombie's distance), where beta's distance went by the box: a dropped item 16, a chicken 21, an arrow 32; bigger things as beta (below) |
| | `inventory.shiftClick` | client (any server) | Shift-click where beta's stopped short: a dispenser both ways, a furnace's top or fuel slot, armour on; made of beta's own clicks (`ShiftClicks`, below) |
| | `combat.heldAttack` | client (any server) | While the attack button is held, beta's repeat of the click every 5 ticks hits only the entity the press landed on; any other under the crosshair is passed over, and every one if the press was on a block or on air. The rest of the click is beta's, so mining and the swing are unchanged (`heldattack.MinecraftMixin`, below) |
| | `furnace.woodenFuel` | world | The user's ask (2026-11-09): wooden things burn in a furnace, as later versions burn them. Beta's `getFuelTime` burns any wood-material block 300 ticks, sticks and saplings 100, and nothing else wooden; where it says 0, `WoodenFuel` answers with later versions' times: wooden tools and sword 200, sign and wooden door 200, bow, fishing rod and ladder 300, boat 1200, bowl 100, and beta's wooden slab (slab data 2) 150. Where beta and later versions both burn something they agree, so it extends beta's table; other mods' fuels stand. Beta's wooden slab is stone material, and later versions kept it as the petrified oak slab, which doesn't burn; it burns here because it's called wooden and made of planks (Claude Opus 5.5's call). Shift-click sends these to the fuel slot, as it asks the same table |
| | `mobs.skeletonArrows` | world | A skeleton's arrow starts at its eyes less 0.1, where `ArrowEntity` puts any shooter's and later versions a skeleton's, not 1.4 higher, over its head; aimed so it crosses the target's distance where beta's would, spread as widely there (`SkeletonAim`, below) |
| | `mobs.squidSurface` | world | A squid in water rises each tick at most a fifth of the way to 0.3 under the surface (its middle's), so it slows as it nears, never comes out and swims on there; deeper, beta's swim (`Squids`, below) |
| | `skins.modern` | client | 64x64 skins kept whole and drawn as a modern client draws them, on a player model of their own; 64x32 skins stay beta's, processor and model (`ModernSkin`, `ModernPlayerModel`, below) |
| 14 | `saves.backgroundAutosave` | world | Chunk saves compressed and written on a thread of their own (`BackgroundSaves`, below); needs Pure Faithful's saves mod |
| 6 | `sky.dusk` | client | `smooth` (default), `stepped` or `beta`. Light applied when the chunks are drawn, not baked into them, so no chunk rebuilds: stepped draws beta's own picture at each whole darkness step, the whole view at once; smooth glides between them (below). Needs OpenGL 2.0 |

"World" means where the world runs: the server in multiplayer, the client in single player.
Dying mobs that aren't solid, built here on 2026-11-08, went to Old Faithful at the user's word (#81, `docs/fixes.md`).

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

### Fences (#30)

The user's ask (2026-11-07), after it was kept as beta. Built by Claude Opus 5.5 the same day. Beta's fence is a whole
block to aim at and a whole 1 x 1.5 x 1 box to walk into, whatever it joins; here it's the shape it's drawn as. A fence
joins only fences beside it in beta (`renderFence`), so the arms follow that, and fence gates don't exist.
- **Shape.** As 1.8 to 1.12 cover it, two boxes: one along z where it joins north or south, one along x where it joins
  east or west or neither (`FenceShape`). Collision 1.5 high, so still no jumping over; aiming 1 high, the ray tested
  against each box. The outline is 1.13's: lines round the post and arms as one shape, no edge where an arm meets the
  post (the footprint traced as one polygon over a 3 x 3 grid, `FenceShape.outline`), grown 0.002 as beta grows its box.
  Added to `FenceBlock` as overrides of `Block.addIntersectingBoundingBox` and `raycast`; `getCollisionShape` keeps the
  whole box, so placing a fence where a mob stands, arrows stuck in one and pistons moving one are beta's.
- **Multiplayer.** Both sides work out collision: the client moves its player, the server replays the move and sends
  the player back where its result is more than a quarter block off or the place is inside something. So a client is
  thin only on a server whose login packet carries 'f' (`ServerMarker`, after the fixes' 'm' and 's' and glowstone's
  'g': the field's 16 characters are now full). The other way round needs nothing: a client with whole fences never
  goes where a thin server's are, the server's replay of its moves comes out the same (only fewer boxes to stop at), and
  beta's floating kick counts blocks near the feet, not shapes, so a vanilla player on the edge of a post's top just
  looks to others as if floating. A server applies the thin shape to everything it moves: mobs, items, boats, carts.
  Aiming is the client's own everywhere: a server takes the block and face a player names (only the distance is
  checked), so breaking or placing past a post works on any server; a mob hit through the gap beside a post is hurt only
  where the server's line of sight (eyes to eyes, its own fences) agrees.
- **What changes in existing builds.** Posts placed only diagonally to each other no longer close a gap: a player,
  wolf, chicken or a zombie, skeleton or creeper (0.6 wide) fits between two such posts, as in later versions; pigs,
  cows and sheep (0.9) still don't. The "smart farm" (the whole box over farmland, #30) goes with it. Path finding is
  beta's, which treats a fence block as solid either way; mobs only stop snagging on posts as they cut corners.

### Chests (the user's ask, 2026-11-08)

The user asked for chests that "connect their textures and handle doubling in a similar way to modern minecraft". Built
by Claude Opus 5.5 the same day; the full rule is `Chests`' class comment.
- **Pairs** (`blocks.chestPairs`). Beta joins any two chests side by side and forbids a third beside a pair, so rows of
  double chests need gaps. Here a placed chest faces you (#69) and joins a chest on its left or right that faces the same
  way and isn't joined already (the clockwise side first, as 1.13 looks), never while sneaking; nothing stops a chest
  going anywhere. Later versions keep which side a chest's partner is on in its block state; here it's the data: 6 + (facing
  - 2) * 2 + side, side 0 for a partner clockwise of the front, and two chests pair only if each says the other. Every
  other chest (data 0, or a facing alone, from before or placed alone) keeps beta's rule, joined to the first such chest
  beside it in beta's order, so pairs in existing worlds, Buried Towers' included, stay as they were. A chest placed
  beside one of those without joining it says it's joined toward a side with no partner, or beta's rule would join them;
  and chests beside a new one that are drawn by beta's rule from the blocks around write the front they're drawn with
  into their data first, or the new chest would turn them. Opening goes by the partner too, the west or north half on
  top as in beta, and shut while a solid block stands on either half. 1.13's other sneak behaviour, clicking a chest's
  side to join it, isn't here: beta opens a chest you click, sneaking or not.
- **Lid** (`render.chestLids`). Beta draws both halves of a pair with the single chest's lid (tile 25), a border down
  the middle of the top and bottom. Four half-lids are made from whatever lid the texture pack has (`ChestLids`), each
  from the tile's outer half and then its middle, so the border stays round the outside and the middle joins with no
  dark band (copying the column beside the border left one: the lid shades in toward its border), into tiles 250 to 253,
  placeholders beta never draws, as terrain.png is loaded and again after a pack is chosen. A chest's top and bottom both
  lay a tile out by world x and z, so one set serves both. A mod that also writes into those placeholder tiles would
  clash; none we know of does. A drawn lid of our own was tried the same day, twice (streaks, then the original's own
  grain shapes), and the user chose this one made from the pack's lid over both (in history: 84d8cb6, 6086323).
- **Multiplayer.** Which chests join, and what opens, is the server's; a client draws by the data it's sent, so a client
  without the change draws chests joined the new way by beta's rule (a row of doubles looks like one long chest broken
  oddly), and opens what the server opens. A client with the change on a server without it may show a chest placed
  beside a double chest for a moment before the server takes it back, as for any refused placement; nothing for that
  needs the server marker, whose field is full anyway.

### Skeleton arrows (the user's ask, 2026-11-10)

Asked for on 2026-11-08 and built as the start alone (0.4.0, off by default after the user tried it), then taken out in
0.4.1 as there weren't many good ways to make it better (user, 2026-11-09). Asked for again on 2026-11-10: like later
versions, balanced to keep about the same beta efficiency, just from somewhere other than over the head, and fine for
skeletons to shoot from under a two-high ceiling. Built by Claude Opus 5.5 the same day. On by default, as later
versions have it and as asked outright (Claude Opus 5.5 and Fable 5.2's call), where 0.4.0 shipped it off.
- **Beta.** `SkeletonEntity.attack` makes the arrow at the eyes less 0.1 (`ArrowEntity`'s shooter constructor, as for a
  player), lifts it 1.4, to 2.83 over the feet, and aims from there at the target's eyes less 0.2, plus 0.2 up for each
  block across, at speed 0.6 and spread 12 (each part of the direction off by a normal 0.09). The 0.2 is the allowance
  for gravity, not for the high start: the aim is measured from wherever the arrow starts.
- **The start alone** (0.4.0's change): the arrow still arrives about where beta's did, but the slope changes. Up at a
  player above it climbs more steeply, so it's slower across, longer in the air and spread wider when it gets there;
  down at a player below, the other way. Standing, ranges 2 to 9: 4 up a cliff 52% where beta's hit 69%, 2 up 66
  against 71, level 69 against 72, 2 down 72 against 70, 4 down 67 against 59.
- **Here** (`SkeletonAim`; a wrap of the `setVelocity` call beside the `@ModifyConstant` on the 1.4): aimed so the arrow,
  unspread, crosses the target's distance at the height beta's would have, and spread by beta's 12 times how much
  quicker across it is than beta's (the ratio of the two angles' cosines), so it has spread as widely as beta's when it
  arrives. Heights come from the arrow's own steps (drag 0.99, fall 0.03 a tick) run continuously; the flat arc, as
  beta's; out of reach, the highest. Matching the height alone wasn't enough: 4 up still 56%, 4 down 64%.
- **What it does** (a re-run of beta's code, `tools/skeleton_arrows.py tables`, 20,000 shots a cell, single player; a
  server's player, aimed at 0.12 lower, gives the same picture with `--mp`). Standing, mean of ranges 2 to 9: level
  72.0 to 70.4 (each range within 3, but 84 to 79 at 4 across), 2 up 71.0 to 71.7, 4 up 68.9 to 69.0, 2 down 69.8 to
  69.9, 4 down 58.6 to 58.5. Walking (0.22 a tick) on level ground: away 40.7 to 41.6, across 16.7 to 18.8, toward
  66.3 to 80.7, as beta's arrow came down from above the head and passed over a player walking in, while from the eyes
  it stays at head height. Along a cliff's edge 2 up and 2 or 3 across, 70% and 20% to 26% and 4% (a steeper climb
  takes longer to cover the distance); 2 down, 12 and 5 to 45 and 18; from 4 across, 6% or less either way. Under a
  ceiling two high beta's arrow starts inside it and sticks there, so it never hits; here, standing, 55% in a tunnel
  one wide and 59% in a wide room (97 at 2 across, 50 to 65 at 4 to 6, 17 to 20 at 9), walking about 30. Three high,
  about beta's (64 to 65 one wide).
- **Multiplayer.** The server's: the arrow's start and speed reach every client in its spawn packet, so a client needs
  nothing.
- **The key** is 0.4.0's, `mobs.skeletonArrows`. 0.4.0 wrote it into the file as false, its default then, so a file from
  0.4.0 keeps it off until changed (the changelog says where); 0.4.1 and later never wrote it.
- **Beside Wolf Packs**, whose wrap of `spawnEntity` in the same method tells the packs about a shot: different calls,
  both apply.
- **Spider jockeys** (found 2026-11-10 after 0.6.0, on the user's question). The skeleton sits 0.175 over its spider's
  feet (`getPassengerRidingHeight`, 0.9 * 0.75 - 0.5), and beta's arrow spares only its shooter, for its first 4 ticks.
  From the eyes the arrow starts 0.4 over the spider's box as the arrow tests it (grown 0.3, so 1.2 high and 1.0 out
  each way), where beta's started 1.8 over, so a shot steeply down went into the spider, the more the steeper (1 below,
  only from right beside it; 4 below, out to 6 across): 4 below, 4 to 6 across, 0 to 6% on the player and 91 to 100% on
  the spider, where beta's hit the player 57 to 70% (`tools/skeleton_arrows.py jockey`, standing). Even beta's hit its
  own spider right below (4 down, 2 across: 96%). Fixed in `mixin/skeletons/ArrowEntityMixin`, a wrap of `isCollidable`
  in `ArrowEntity.tick` beside #81's: a skeleton's arrow spares the skeleton's mount as it does the skeleton, as later
  versions spare what the shooter rides. Below, wherever beta's arrow cleared the spider, the jockey now hits within 2
  points of beta's; closer in, more, where beta's went into the spider (4 down: 2 across 74% against 2, 3 across 75
  against 45). A client keeps the arrow's owner from its spawn packet, so one with the mod agrees; one without it may
  show the arrow glancing off the spider for a moment while the server's flies on.

### Boats at rest on the water (the user's report, 2026-11-11)

The user, playing More Faithful: is the boat meant to bob continuously up and down? It wasn't: Boat Handling had
promised a boat that rides steadily since 0.1.0, and its damping only shrank beta's bob. Fixed in Boat Handling by
Claude Opus 5.5 the same day (`mixin/boathandling`, `Boats.wetShare`).
- **Beta.** `BoatEntity.tick` tests five slices of the boat's box, each 0.12 high, the first from 0.125 under the box,
  and pushes it up by 0.04 × (2 × wet − 1) a tick, the wet share in fifths (fully under: downward speed halved and
  +0.007). Near the surface that's +0.008 with three slices wet and −0.008 with two, never nothing, so there's no
  height it rests at, only a swing across the boundary (0.185 over the surface, the boat's y being its box's middle)
  that the 0.95 drag doesn't end. Measured in `BoatCheck`'s pool (single player, the game's own tick): 0.097 up and
  down, a high point every 7.8 ticks, around 0.185, ridden or empty alike; paddled, 0.036 or 0.081 by what came
  before. The rider's eye moves with the boat exactly (`updatePassengerPosition`). Boat Handling's × 0.8 made it 0.005
  idle, a two-tick flutter, and 0.016 paddled, a high point every 3.6 ticks; a model of the tick over many starting
  states gives 0.005 to 0.037 with the damping and 0.012 to 0.33 without, so how big the swing is depends on how the
  boat came to the surface, and it never stops.
- **Here.** With one to four slices wet, the push is redone from a share worked out from the surface: the highest top
  of water among the blocks the slice tests look at (a source's or a falling column's at the top of its block, flowing
  water's an eighth lower a step, as `isFluidInBox` reads them); a half at the resting height and a fifth more every
  0.12 deeper, as beta's count rises on average, so beta's steps smoothed through their middles. Kept within a fifth of
  beta's count, as it always is where the surface is level across the box, so on uneven water the push stays near
  beta's. The push fades to nothing at one height and the damping settles the boat there with a bob or two: from where
  a spawned boat starts (0.3 down), pushed under or dropped from a block up, within 0.001 in two or three seconds.
  Fully under, out of the water, on the ground (the halving), flowing water's sideways push, splashes and breaking:
  beta's.
- **The height.** 0.1885 over the surface (6/32 and 1/1024), 0.0035 over beta's middle, so no higher or lower in the
  water to see. On a 32nd and a hair over it: a server sends positions in 32nds, and every 20 seconds floors its own
  entity's position to them (`EntityTrackerEntry`'s resync), which here moves the boat down 0.001 and back, and every
  client draws it at the same 32nd.
- **Multiplayer.** Where the world runs, so the server's. A client never floats a boat itself: its tick only moves it
  toward the positions the server sends, in 32nds every 5 ticks (beta's tracker sends a move for every entity every 5
  ticks, moved or not: its "moved enough" test reads the position where it meant the change), so with the server's
  boat still, every client's is, beta's included (measured, below). Beta's client adds 1/64 to the resync's height and
  the next move takes it off, so every 20 seconds anything the server tracks, a resting boat too, lifts about 0.01 for
  about half a second; beta's own and on every entity, left alone.
- **Cost.** Up to 18 block reads a tick for a boat partly in the water.

### Boat steering (the user's ask, 2026-11-10)

The user: boats a bit faster and easier to steer, then could we have the modern steering controls too. Built by Claude
Opus 5.5 the same day; it waited to ship with World Rules' Pickup Reach (the user's word), and both came out in 0.7.0
(2026-11-11). Still
Faithful, `boats.steering`, on by default; the user hasn't said whether it belongs here or as a More Faithful world
rule, so its physics live in `BoatSteering`, apart from the mixins (`mixin/boatsteering`), to move if they prefer.
- **Beta.** Paddling adds a fifth of the rider's own motion each tick (`BoatEntity.tick`), which is the rider's keys
  turned by where the rider looks, and keeps 0.99 of the speed: W goes the way you look, A and D sideways to it. The
  boat's yaw swings toward where it moved, up to 20 degrees a tick, and a rider's view turns with the vehicle
  (`Entity.tickRiding`, half the built-up turn a tick, at most 10), so paddling while looking off where the boat goes
  turns the view, the paddling with it, and the boat circles. Top speed about 0.29 a tick, 5.8 blocks a second
  (measured in single player: 0.184 after 100 ticks of W, and beta's 0.99 makes that 63% of the way).
- **Here**, as later versions' `Boat.controlBoat` and `floatBoat` (1.9 on), from memory of their code, not read from a
  jar here: A and D build a turn rate, 1 degree a tick each tick held, which decays with the momentum, so at most 10
  degrees a tick; W pushes 0.0325 along the boat's heading, S backs it 0.005, and A or D alone pushes 0.005 forward.
  In water the boat keeps 0.9 of its speed and turn rate each tick, applied before the keys and then the move (beta's
  0.99 after the move steps aside), so a push settles at push / (1 - 0.9), 200 times the push in blocks a second: W at 0.325 a tick, 6.5
  blocks a second, the same in every direction (beta's 0.4 limit on each axis lifted, so diagonals are no faster); S
  and a turn key alone near 0.05. The user's call (2026-11-11): about 6.5 to 6.6, a little faster than beta (about 5.8
  blocks a second measured); later versions push 0.04 (8 blocks a second), and the first build had 0.05 (10). One
  constant, `FORWARD_PUSH`. The momentum carries a boat on
  through a turn, so it drifts: at full turn its motion trails its heading by about 50 degrees, as in later versions.
- **Out of the water** beta's friction stands (halved each tick on the ground, 0.99 after the move) with the same
  pushes, so a beached boat crawls about a block a second where beta's barely moved; its turn decays by half a tick
  there (at most 2 degrees a tick). Ours, from what later versions do on land (a boat moves slowly there), not from
  their numbers.
- **Every boat in water** a player isn't paddling as beta's (nobody in it, or a steering rider) has the 0.9 momentum,
  so an empty one from 0.4 a tick (held to the top speed, 0.325) stops in about 3.25 blocks (Boat Handling's coasting
  gave 8), and keeps its heading:
  it no longer swings round to face where it drifts. A boat out of the water with nobody in it is beta's.
- **Placing.** A boat placed from the hand faces where the placer looks (`BoatItemMixin`), as in later versions; beta
  started every boat with yaw 0, which mattered to nobody while boats went where you looked. Turned just after it's
  spawned: a server starts tracking a boat from its yaw then, and a client spawns any boat with yaw 0, so the turn
  reaches clients as a change (within 11.25 degrees; the tracker's step).
- **The view** needs nothing new: beta's `tickRiding` already turns a rider's view with the vehicle, on every client,
  and keeps up with 10 degrees a tick. No 105-degree clamp: the player looks round freely.
- **Beside the other boat switches.** `boats.sturdy` is as before, so a steered boat breaks only in a real crash:
  at least 0.25 into what it hits while still going over 0.15 along it. Held to 0.325, that takes 90% of top speed and
  a hit between about 50 and 62 degrees off the wall, so a steered boat rarely breaks (at the first build's 0.5 a crash
  was a little easier than in beta; worked out 2026-11-11, when `StillCheck`'s 45-degree crash stopped breaking one). `boats.handling`'s coasting steps aside for a steered boat (a
  `BoatSteering.Boat` that says so this tick) and stays for a boat a rider paddles as beta's; its float applies to
  every boat. Each makes sense alone: steering without handling bobs as beta's; handling without steering is as it was.
- **Multiplayer.** Where the world runs, so the server's. On a server a ridden boat is ticked only by its rider's move
  packets (`ServerWorld.updateEntity` skips a boat a player rides; `onPlayerMove` ticks it), with that packet's motion
  as the rider's. A riding client sends the motion its keys gave it (`moveNonSolid`: the keys turned by its yaw, times
  its speed and friction), so with the look from the same packet the server turns it back into keys:
  forward = -vx sin(yaw) + vz cos(yaw), strafe = vx cos(yaw) + vz sin(yaw), A positive (`BoatSteering.decode`, with
  beta's own sine table, as the client turned them). Beta's client sends its look while riding only when the look
  didn't change (#83), so that needs a client that sends it every riding tick, and the server goes by evidence: two
  riding moves in a row (the motion packet beta marks with -999 for both heights) whose looks differ, which beta's
  client never sends (each full one carries the look it last sent, and any change sends the motion alone). From then on
  that connection steers; a riding move without its look puts it back. Until then, and always for beta's client and
  any other that doesn't send its look each riding tick, that rider's boat paddles as beta's, with handling's
  coasting. A client with #83 shows it at once when its view moves; one whose player gets in and presses a key without
  moving the mouse paddles as beta's until the view moves, then steers. So boats steer for players whose game has Old
  Faithful's Riding Look (Old Faithful, Still Faithful, More Faithful, with it on) on a server with Boat Steering; the
  client's own Boat Steering switch isn't needed there, since the server moves the boat. Keys alone lie on an axis or
  a diagonal of the rider's frame to float rounding; a motion that doesn't (something pushed the rider: another boat,
  a mob, a squid beside them; a block stopping one axis) isn't read as keys but keeps the last clear ones for half a
  second, then none, so a push never reads as a turn. What a client sees is the server's boat: its heading arrives in
  the tracker's 11.25-degree steps every 5 ticks, so after a turn a drawn boat can face up to that far off its course,
  and the view follows the drawn boat; a round trip and the tracker's period of latency, as beta's boats on a server
  had.
- **Cost.** A few multiplications a tick a boat, and on a server a sine and cosine per riding move.

### Squid at the surface (the user's ask, 2026-11-10)

The user, playing: squid jitter at the surface; it's their own motion and bob, but could it look more natural, with
less bob. Built by Claude Opus 5.5 the same day.
- **What it was.** Old Faithful's #77 lets squid swim up, so they reach the surface. A squid in water sets its speed every
  tick to its swim direction times its pulse, and keeps a direction until its 1-in-50 a tick re-pick (about 2.5 s); out
  of the water (#77's test: its middle over the water's real surface) it falls, its sideways speed zeroed and its
  tentacles in their out-of-water pose. So a squid heading up met the surface and bounced: a tick or two out, a drop
  of 0.04 to 0.11, back in and up again, every 2 to 10 ticks, its tentacles snapping for a tick each time (traced per
  tick in a hidden client and in a re-run of beta's code). Not something #77's fix brought in: it's beta's own squid
  code meeting the surface, which every later version has too (26.3's `Squid.aiStep` is beta's, with a looser water
  test, so its squid rise higher before dropping). So a Still Faithful polish, not an Old Faithful fix, and Old Faithful
  keeps the bounce (Claude Opus 5.5's call).
- **Here** (`Squids`, a mixin right after the in-water branch sets the speed, before the tilt is worked out from it):
  where the world runs, a squid's rise each tick is at most a fifth of the way to 0.3 under the surface (its middle's;
  the highest surface among the columns #77's test asks about, looked for two blocks up). It slows as it nears, never
  comes out, and swims on there with its back at the waterline (a flat squid's drawn body about 0.1 over it, its box
  0.175) until its re-pick turns it; risen less, it lies flatter, and its pulse, roll and tentacles are beta's. Above
  that height while still in water it sinks back the same way. Deeper than about 0.8 under the surface a full swim is
  under the limit, so nothing changes there. A squid that falls in or is knocked out still has beta's out-of-water
  branch.
- **What it does** (near the surface: the middle within a block of it, as #77 measures). A hidden client in single
  player, ten squid in a pool 3 deep, 1,200 ticks, 2026-11-10: with the switch off, 0.38 to 0.46 blocks of rise and fall
  a second, 3.8 to 4.5 turns between up and down a second, 11.5 to 14.1% of ticks out of the water, 1.7 to 2.1 tentacle
  snaps a second, body out 26 to 28% on average and 60% at most (two runs); with it, 0.12 to 0.16 blocks (squid swimming
  up and diving), 0.07 to 0.11 turns, none out, no snaps, body out 10 to 13% and 18% at most (three runs). The real server (`squidWatch -Psquids.labels=
  fixed,polished`, 6,000 ticks): water 3 deep 0.44 to 0.14 blocks a second and 4.2 to 0.13 turns, 12.8% out to none;
  30 deep, 0.46 to 0.17 and 4.7 to 0.17; 1 deep, where squid now stay at the floor, 0.27 to 0.02. Where squid end up:
  30 deep from random heights, a mean of 15.0 over the floor in the second half with the change against 14.3 without;
  from the floor 6.9 against 10.8, though that tank's squid with the change never came within a block of the surface,
  so nothing of it touched them (sixteen squid in a random walk; a re-run of beta's code with 200 squid gives 13.15
  against 13.20 after ten minutes).
  The trough of thinning flow carries all six onto dry stone either way, as in #77, and squid on land stay put.
- **Multiplayer.** The server's: a client draws the server's positions (to 1/32, lerped), so any client sees it. A client
  with #77 runs the same water test for the animation, and the middle 0.3 under is never near the line. A Still
  Faithful client on a server without the change sees the server's bounce; a smoothing of its own would draw squid
  somewhere other than the server has them, so there's none.

### Shift-click (the user's ask, 2026-11-09)

Only where beta's own shift-click stopped short, nothing it couldn't do (the user's call): it moved a stack between a
container and the inventory, or the inventory and the hotbar, but did nothing in a dispenser, never put anything into a
furnace and never put armour on. `inventory.shiftClick`, with nothing held:
- **Dispenser**: both ways, as a chest's (out to the hotbar's end first).
- **Furnace**, from the inventory: what smelts (`SmeltingRecipeManager`) to the top slot, else fuel (the furnace's own
  `getFuelTime`, so other mods' fuels count) to the bottom; if that slot holds something else, nothing moves, as in later
  versions. Logs go on top. Anything else, beta's.
- **Armour**, on the inventory screen: an `ArmorItem` goes on where its place is empty; else beta's. Not pumpkins (the
  user's call): they're carried in stacks.

Made of beta's own clicks (`ShiftClicks`, hooked where `HandledScreen.mouseClicked` sends a shift-click): pick the stack
up, put it down slot by slot as beta's `insertItem` would (onto the same item, then an empty slot), put back what didn't
fit, each click checked after it's made, anything unexpected ending the move where it stands. Not a change to the
screens' `quickMove`: a server accepts a click by comparing only the clicked slot's stack and then sends the clicker
nothing (`skipPacketSlotUpdates`), so a client whose shift-click went elsewhere than the server's would keep items that
aren't there. Clicks are what any server already checks, so it works on any server and a server needs nothing. Merges
follow Faithful Core's rule where it's installed (stack data). Only vanilla's own screens; another mod's are beta's.

Later versions' other handling (dragging to spread a stack, double-click to gather, the drop key over a slot, crafting
many batches at once, the crafting table's grid taking shift-clicks, keeping what's held when a screen closes) stays
beta's: built the same day and undone at the user's word, for only beta's gaps and no new capabilities.

### Held attack (the user's ask, 2026-11-11)

The user: no attacking something by accident after breaking a block, or after moving the crosshair off something,
while holding the button down; a Still Faithful change. Built by Claude Opus 5.5 the same day; the rule below was Claude
Opus 5.5's pick, told to the user, who may still choose later versions' (below).
- **Beta.** A press of the attack button calls `handleMouseClick(0)` on whatever the crosshair is on: an entity is hit,
  a block's mining starts, nothing gets a swing and a 10-tick wait. While the button is held,
  `Minecraft.tick` calls it again every `tps / 4` ticks (four times a second) on whatever the crosshair is on then;
  the mining itself goes on in `handleMouseDown`, every tick. So holding on after a block breaks hits the animal behind
  it, and dragging the crosshair across a tame wolf in a fight hits the wolf (it takes the hit and stands up).
- **Here** (`heldattack.MinecraftMixin`, wraps of the press's call and the repeat's, and of `attackEntity` inside the
  click): the repeat hits only the entity the press landed on; another under the crosshair is passed over, and so is
  every entity when the press was on a block or on air. Only the hit is withheld: the rest of the repeated click is
  beta's, so the arm still swings over a passed-over entity (without a miss's wait), a block under the crosshair is
  attacked and mined as before, and a hold begun on a mob goes on hitting it as beta's does. A press hits whatever
  is under the crosshair, as ever. The press's entity is forgotten when `handleMouseDown` sees the button not held (let
  go, a screen open, the window left), so a hold whose press the game never saw (another mod took it) hits no entity.
  Held weakly, so a hold through leaving a world doesn't keep the world.
- **Later versions' rule**, the alternative: a hit only on a press, never on holding (from memory of their code, not
  read here: 1.8's `runTick` calls `clickMouse` once per press and `sendClickBlockToController` while held, which mines
  and never hits; modern versions' `startAttack` and `continueAttack` the same). The difference from ours is a hold
  begun on a mob: there it hits once, here it goes on hitting, as in beta. Switching would be a change to the repeat's
  wrap alone.
- **Multiplayer.** Client input only: a server is sent a hit only when the client makes one, so it works on any server
  and a server needs nothing; with the switch it's just sent fewer.

### Particle and entity distance (the user's ask, 2026-11-09)

The user, playing with Explosive Arrows: the blast doesn't show at a distance. Built by Claude Opus 5.5 the same day.
- **Beta.** `WorldRenderer.addParticle` makes no particle more than 16 blocks from the camera, whatever made it.
  `Entity.shouldRender` draws a thing within 64 times its box's average side (times `renderDistanceMultiplier`, 10 for
  other players): a dropped item 16, a chicken 21, an arrow 32, a minecart or pig 57, a zombie 64, a spider 79;
  snowballs, eggs and the fishing float 64 and fireballs 256 by overrides of their own. Block-breaking particles have no
  limit. A server sends items and arrows within 64 blocks (the tracker's square), blasts and block events within 64,
  mobs within 160.
- **Later versions** (26.3's jar read, 2026-11-09): particles within 32 blocks, some at any distance, among them the
  puff (`poof`, beta's `explode`) and the blast's own; arrows 10 times beta's rule (320 blocks); dropped items still
  16 (no rule of their own), times the Entity Distance setting. Their single player runs a server inside the game,
  which sends blasts within 64 blocks (`ServerLevel.explode`), so that's how far a blast shows there too.
- **Here.** `render.particleDistance` (`particledistance.WorldRendererMixin`): every particle within 32, puffs (a
  blast's, a mob dying or appearing from a spawner) within 64: as far as a blast is heard (beta's sound reach at its
  volume of 4), past the bow's reach (about 52 blocks on level ground, from `ArrowEntity`'s speed 1.5, drag 0.99 and
  fall 0.03), and as far as a server sends blasts, so single player and a server agree. A blast's dark smoke is an
  ordinary particle: past 32 a blast is its white puffs, as in later versions, where smoke would be a pixel or two.
  `render.entityDistance` (`entitydistance.EntityMixin`): 64 at least, a zombie's distance and how far a server sends
  items and arrows; bigger things and the classes with their own distance keep beta's. Dropped items past 16 are ours,
  not later versions' default. Both client-only: a server needs nothing. Two switches, since they're separate things
  with separate costs: a pile of items in view might want beta's way while blasts stay far.
- **With Explosive Arrows** (the library's, since 0.3.0, which draws its own blast's puffs and smoke 16 to 64 blocks out
  itself, whatever the edition): each of its particles takes one way, its own drawing or `World.addParticle`, never
  both, so nothing doubles with the switch on. Its smoke shows to 64 where other blasts' stops at 32 (reasoned from
  the code, not run together).
- **Cost.** Ambient particles (torches, fire, lava, portals, redstone) come from display ticks that only run within 16
  blocks, so they don't grow; what's new is particles from things further off (blasts, mobs dying, splashes, a TNT
  fuse, fireballs) and things drawn 16 to 64 out. Beta's cap stays (4000 particles a layer, the oldest dropped), so a
  blast 50 blocks off costs what one within 16 always did. Measured by `DistanceCheck` (Tests), the game thread's time
  in a hidden 1000x600 window, three runs on 2026-11-09 with other programs running: 400
  apples 30 to 50 blocks out took 0.56 to 0.77 ms a frame to draw against 0.04 to 0.09 with none (about 1.5 µs an
  apple, what beta pays for them within 16); four TNT-sized blasts 40 to 52 out filled the cap (4000 particles), and
  drawing them took 0.29 to 0.51 ms a frame and moving them 0.66 to 1.14 ms a tick. With the switches off, none of
  either was drawn or made.

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

### Animals that stay (#4): what "looked after" means

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

- While iterating on one change, its own check alone: `./gw :harness:clientStartupTestStill -Ptest.only=still` (or
  `fence`, `chat`, `dusk`, `skin`, `shiftclick`, `distance`, `skeleton`, `squid`, `boat`, `held`, `fixes`, `options`, `settings`, `window`, several with commas), about
  half a minute where the whole test takes 1 to 2 minutes; the whole test, and `-Pstill.beta` where a switch changed,
  once before the change is done (`docs/building.md` > What game runs cost).
- `ShiftClickCheck` (in the same run, 2026-11-09): mouse and key events put into our LWJGL component's queues, read by
  the open screen as the real ones are. A helmet from the inventory and boots from the hotbar go on; a second helmet
  and a stack of pumpkins move as beta's; in a furnace iron ore goes on top, coal below, gold ore stays put beside the
  iron, dirt goes to the hotbar, logs on top; arrows into a dispenser and back to the hotbar's end. With
  `-Pstill.beta`, beta's result each time.
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
  a boat sliding along a wall at 0.35 holds while one driven steeply into it (0.32 into the wall, 0.2 along: at 45
  degrees, 0.3 each way, a boat held to Boat Steering's top speed met it at only 0.23, 2026-11-11) breaks, an unpaddled
  boat stops in 7.7 blocks (3.25 with Boat Steering: 0.4 held to its top speed of 0.325, then its momentum), and a full save after changing a block writes it (read back with the game's reader) through the writer
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
  keeps it on for the settings checks, and hidden clients waiting on the refresh are the stalls' trigger; two of six runs without that ended in a stall, neither of the two with it did. The real path: jeb_ and Notch (64x32: beta's), MHF_Alex and MHF_Steve (64x64, both
  slim in their profiles) through hardening's own client and parser, outside the game.
- `FenceCheck` (#30, the same run, after StillCheck), in single player, so the switch decides: `FenceWalk` (the
  harness's, shared with the multiplayer test) walks the player by a scripted input, the game's own physics, into a
  `FenceScene` (a lone post, a line of three, a corner): up to the post, past its side 0.6 off its middle, diagonally past
  its corner on a line 1.2 off, up to the line, into the corner's inside, jumping at the post, and standing 6 s on the
  edge of a post's top. Then the crosshair as each frame works it out: at the post's east face, placing cobblestone
  against it and pick block; 0.3 beside its middle at a wall and at a pig behind it; and a pig given a path every tick
  round the end of a row of five fences. Pictures of the outline on the post, the line and the corner in
  `local/run/fences/` (`-thin`, `-beta`). 2026-11-07, both ways: thin, the player stops 0.075 short of the post's middle
  line (0.3 from its face), passes its side, keeps to its diagonal line exactly, stops at 0.925 from the line and the corner,
  jumps 1.25 and never gets over, falls off the top's edge; the crosshair meets the post's face at 0.625, places east of
  it, picks the fence, and through the gap meets the wall and the pig; the pig got round in 35 ticks touching nothing. With
  `-Pstill.beta` everything at the whole block (-0.3, 1.3, pushed 0.48 off the diagonal, stood on the edge, the face at
  1.0, the fence instead of the wall and the pig), and the pig snagged on the row's end on 7 of 38 ticks. A camera
  exactly along a diagonal into a post's vertical edge misses it by rounding, as beta's own block test would; the
  pictures look from off the diagonal.
- `ChatCheck` (chat bars, the same run, after FenceCheck): messages put in the chat in a room of white wool open to the
  sky, so every bar lies on a plain wall, and each line's bar end found in the picture along the bar's top row (the last
  sharp brightening, from a pixel that isn't a letter's shadow: commas of the line above reach into that row), against
  its text: one short line, five of different lengths with a long message wrapped as beta wraps it, and the same with
  the chat open. Pictures in `local/run/fences/chat/` (`-polished`, `-beta`). 2026-11-07: every bar ended at 2 + its
  text's width + 1 (14 to 319), and with `-Pstill.beta` at 322 for every line.
- `DistanceCheck` (particle and entity distance, the same run, last; `-Ptest.only=distance`, about 20 s): the rules at
  their edges (an item, an arrow, a chicken, a zombie and a spider asked whether they'd be drawn either side of beta's
  distance and 64; a puff and a smoke particle offered at 15 to 65 blocks), then a grey platform above the clouds with
  the camera 12 over it looking down 40 degrees, its middle all platform: nine apples 16, 25, 38 and 51 blocks out, and
  an Explosive Arrow's blast (power 1.5, in the air) 13, 24, 37 and 51 out, each picture against the empty platform's,
  counting the middle's pixels that changed; the particles each blast made against the places it reached; then the
  cost (above). Pictures in `local/run/distance/` (`-polished`, `-beta`), with every cluster and every blast at once in
  `apples-` and `blasts-`. 2026-11-09, both ways: with the switches, the item, arrow and chicken drawn to 63 and not
  at 65, the zombie and spider as beta; puffs made to 63, smoke to 31; every cluster and blast changed pixels (70 to 528
  for apples, 377 to 3849 for blasts); the two nearer blasts made a puff and a smoke particle a place, the two further
  a puff a place. With `-Pstill.beta`: drawn only within 16, 32 and 21, both particles only at 15; only the nearest
  cluster and blast changed any pixel, the others none, and the further blasts made no particle.
- `SkeletonCheck` (skeleton arrows, the same run, last; `-Ptest.only=skeleton`, about 30 s with the start): where the
  arrow starts against the eyes, then the game's own `attack` and arrows from 15 skeletons a scene at targets with a
  player's box and aiming point, 20 volleys: level ground 6 across, a player 4 up a cliff and 4 down one 5 across,
  a tunnel one wide and two high 5 across, and spider jockeys 4 above a player 5 across (a still spider seated by the
  game, counting its rider's hits); each share of hits against the re-run's (`tools/skeleton_arrows.py scenes`) within
  four standard deviations and a point, and at most a stray jockey's arrow in its own spider (one with the switch, three
  without: the re-run has 0.002% and beta's own 0.045%, about 1 in 2,200, which a run of 300 met once, 2026-11-11). 2026-11-10: with the switch, the arrow
  0.10 below the eyes, 73.3% (the re-run 71.8), 71.0 (73.5), 70.7 (67.1), 55.0 (57.3); with `-Pstill.beta`, 1.30 over
  them, 67.7 (72.1), 71.7 (73.6), 66.7 (65.9) and none of 300 in the tunnel. The start alone would give about 58% up
  the cliff. The jockeys, the same day: 64.7% (65.7) with the switch and 64.7 (64.5) as beta, none in the spider; with
  the switch but the mount's mixin left out, none on the player and all 300 in the spider.
- `SquidCheck` (Squid Surfacing, the same run, last; `-Ptest.only=squid`, about 50 s with the start): ten squid in a pool
  10 by 10 and 3 deep, open to the sky, in a place where water doesn't freeze (in a snowy biome the top turns to ice,
  which is no water to a squid: the first run met it), 1,200 ticks of their own swimming, each tick of each squid in
  `local/run/squid-surface/ticks-<polished|beta>.csv`. With the switch, none out of the water near the surface and the
  middle never over 0.3 under; with `-Pstill.beta`, some out (Old Faithful's bounce). Numbers above (2026-11-10).
  `-Pstill.squidClip` adds the before-and-after film, about 70 s more: five seconds from the south shore, as a player
  standing there sees it, and five from against the west wall, eyes 0.73 over the surface, at 60 frames a second (game
  time slowed to 3 ticks a second, each drawn at a third, two thirds and all of the way from the tick before:
  `SquidClipFrame`), into `local/run/squid-surface/<side>-shore/` and `<side>-low/`; the camera stands on a block and
  is checked not to move. `squid-surface.mp4` there is both sides' frames put side by side with ffmpeg (the low view
  cropped to 800x480 from the top, 100 in, to leave out the wall's top).
- `BoatCheck` (Boat Steering, the same run, last; `-Ptest.only=boat`, about 20 s with the start): a boat placed from
  the hand with the player looking along yaw 37 and straight down at open water, then the player in a boat in a roofed
  pool 120 long, its keys put in as the game's input (`ScriptInput`): heading west and looking south, W for 20 ticks;
  from the start again looking west, W for 100; W and A for 20; nothing for 40. 2026-11-10, with the switch: the placed
  boat's yaw -53 (the look's less 90); 5.18 west and 0.00 south; 0.5000 a tick after the 100 ticks, 45.0 blocks (at the first build's 0.05 push; 2026-11-11 at 0.04, 0.4000 and 36.0, and at 0.0325, 0.3250 and 29.3, 3.36 west in the first 20); turned
  103.51 left, exactly what 18 ticks of the rate add up to (the keys reach the boat a tick or two late), the view 95.2
  with it, its motion trailing its heading by 51.9 degrees; let go, 3.85 blocks more at 0.0071 a tick, turning 0.16
  a tick. With `-Pstill.beta`: the placed boat's yaw 0; 0.50 south and 0.03 east, along the look; 0.184 a tick after
  the 100 ticks, 5.7 blocks, so a top speed of 0.29 (beta's 0.99 makes 100 ticks 63% of the way). `StillCheck`'s
  coasting boat: 3.25 blocks with Boat Steering on, as its top speed and momentum give (8 with Boat Handling alone,
  18 as beta).
  One of three whole runs that day failed it with the boat and its rider not moving at all, in the same run as six
  of `DuskCheck`'s pictures and the FOV check. Found 2026-11-11, when it came every time: the player was dead (a death
  screen open, the view narrowed). `FixesCheck` left the player standing on its #27 boat, at a height chosen for
  beta's rounding to drop a player through, and every later check starts from and returns to the player's spot; a
  move and return dropped the player into the water under it, and through `SkeletonCheck`'s long run it drowned. The
  bob had made that a matter of chance; a boat holding its height made it certain. `FixesCheck` now takes the boat
  away and turns its water to stone under the player. When the boat hasn't moved, the check still prints the boat, the
  rider, whether its region is loaded, the screen and whether the input is the test's.
  Boat Handling's float (2026-11-11), in the same pool: an empty boat spawned beside the ridden one, both left 60
  ticks, then the heights of both and the rider's eye over 100 ticks; the empty one pushed under (its box's bottom
  0.05 over the pool's floor) and then dropped from a block up, each left 100 ticks, its height over the last 20; the
  ridden boat's over the last 60 of the 100 ticks of W. With the switch: 0.00007 idle (the last of the settling), at
  0.1885 over the surface; back at rest at the same height after the push and the drop (0.00004 and 0.00006); paddled
  0.00000. With `-Pstill.beta` (the bob checked to be there): 0.097 idle, a high point every 7.8 ticks, around 0.185;
  still swinging 0.135 and 0.117 after the push and the drop; paddled 0.081. Before the fix, with the switch: 0.0046
  idle, a two-tick flutter, and 0.016 paddled. `-Pstill.switches="boats.handling=false"` (the rest as shipped) gives
  beta's numbers, as Boat Steering doesn't touch the height.
- `BoatSteeringTest` (`./gw :oldfaithful-polish:test`, seconds): a riding client's motion worked out as beta's client
  does (the keys, sneaking, the 0.98 decay, `travel` and `moveNonSolid` in floats with the game's sine table, three
  frictions) reads back as its keys at every tenth of a degree of yaw and 400 random ones, every combination of keys;
  and of 200,000 random pushes of 0.002 to 0.052 added to it, under 1 in 1000 reads as other keys.
- `HeldAttackCheck` (Held Attack, the same run, last; `-Ptest.only=held`, about 20 s with the start): the attack button
  held as a player holds it, its press and release put into our LWJGL component's queue and held state (as
  `ShiftClickCheck`'s clicks), so beta's own press, repeat and mining run, and the real wraps decide. In single player,
  the hand empty, on a floor high up: glass mined at eye height with a pig behind it; a hold begun on that pig, turned
  onto a pig beside and back; a click on the second. The pigs stand still and count the player's hits without taking
  them. Holds are four of beta's repeats long, counted from the game's tick rate (a repeat is 20 ticks at the tests'
  faster game time). 2026-11-11, both ways: the glass broke 11 ticks after the press; the pig a hold began on was hit
  4 times in 80 ticks (the press and three repeats), none while the crosshair was off it and 4 more once back on it; a
  click hit once. With the switch the pig behind the glass and the one turned onto were hit 0 times; with
  `-Pstill.beta`, 2 and 4. Not covered: a press another mod took (reasoned), multiplayer (the same client code; a
  server only receives the hits).
- `./gw :harness:fencesMultiplayerTest` (#30, about 3.5 minutes): the server package with and without the polish mod,
  each joined by the hidden client with Still Faithful's mods with and without it; the server builds the scene on
  "!oftest fences" and puts the player at each walk's start on "!oftest tp" (`MultiplayerScenes`), flying off so beta's
  floating kick runs. 2026-11-07: thin only with both, whole in the other three mixes, the same numbers as single
  player; in all four no position from the server during any walk, still connected after 6 s on a post's edge, and no
  "moved wrongly" or floating line in a server's log. Not tested: a vanilla client by itself (the client without the
  polish mod has Old Faithful's, none of which touch collision) and mobs or boats seen across mixed sides (the server
  moves them; a client without the change only sees them pass closer to a post than its own fences would let it).
  Then Boat Steering (2026-11-10; the task now takes about 3 minutes, `-Pstillmp.only=still-server-still-client` for
  one mix): the client is put at its scene and the server builds a roofed pool 20 blocks south with a boat facing west
  ("!oftest boat", within the test server's 32-block entity range) and seats it ("!oftest ride"); `BoatRide` sets the
  look south and nudges it a degree and back, holds W for 40 ticks, and where it should steer, A for 30. The client
  without the polish mod has Old Faithful's Riding Look switched off, so it rides as beta's client does. 2026-11-10:
  steered only with the polish mod on the server and Riding Look on the client: 10.73 blocks west and 0.00 south, then
  A turned the boat 212 degrees left (as the client saw the server's boat) and the view 204; in the other three mixes
  1.25 south and 0.15 east, beta's paddling along the look, curving as the boat turned the view. Not tested on a
  server: S, the turn-only push, pushes from other entities on the rider (the held keys) and a client with Riding Look
  but without the polish mod (reasoned: the server moves the boat, and the client only sends its look). 2026-11-11, at
  the push of 0.0325: 6.96 to 6.98 west in the 40 ticks (the check asks for 4, its point the direction). Boat Handling's float
  (2026-11-11), before the keys: the boat left 60 ticks, then its height in the client's world over 40. With the polish
  mod on the server, still on both clients, with and without it: 0.00000, at 0.1875 over the surface (the server's
  0.1885 in its 32nds); on the server without it, beta's bob as the client draws it, 0.045 and 0.038.
- `StillCheck`'s chest and mob scenes (2026-11-08), in the same run: #69's double chest placed looking along
  the first stays two singles under `blocks.chestPairs`; four chests in a row placed facing north make two doubles
  side by side (data 6, 7, 6, 7), each showing the end of its own front where they meet; one placed sneaking stays
  single, one facing east beside one facing north doesn't join, and a pair of beta's (data 0) keeps its pair and its
  front with a chest placed beside it. Beta: the third chest of the row and the one beside beta's pair aren't placed.
  Lids: a pair along x and one along z get tiles 250 to 253 (beta: 25), and terrain.png read back from the GPU holds the
  half-lids `ChestLids` makes from the pack's sheet, pixel for pixel.
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
  nothing goes over the network, each client draws what it fetched); the bow pose and dropped item sizes (what's seen: the user's), boat handling's and Boat Steering's feel
  (the user's), glowstone between a real client and server (the marker and gating mirror #9/#10's, audited and
  reasoned), animals kept by riding a minecart or hatched from an egg (the same keep call, reasoned).

## Kept as beta
- Eating at full health stays possible (#35): mods add foods with other effects, and blocking eating would need
  adapting for each.
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
