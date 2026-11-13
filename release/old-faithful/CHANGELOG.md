# Changelog

Old Faithful's releases, newest first. Each has its own version number, carried by Old Faithful's mods, its pack and
its server package, and names the Pure Faithful release it's built on, whose mods and LWJGL are inside the pack as
that release has them; [Pure Faithful's changelog](https://github.com/studio-quire/pure-faithful/blob/main/CHANGELOG.md)
covers those. Entries say what a player or server owner notices.

## 0.2.8 (2026-11-13)

Released with Still Faithful 0.8.1, which carries it; on Pure Faithful 0.1.6, as 0.2.7 was.

- `oldfaithful-settings` won't start beside a Faithful Core older than the library's 0.9.0: the game, or a server,
  stops at launch with Fabric Loader's message that the two are incompatible, where with 0.2.7 the game stopped later,
  when a server refused you over mods. This matters only if you've added the library's mods; take Faithful Core 0.9.0
  or later with this version.

## 0.2.7 (2026-11-13)

Released with Still Faithful 0.8.0, which carries it; on Pure Faithful 0.1.6, as 0.2.6 was.

- Pickaxes mine redstone ore fast, as they mine stone and the other ores (Tool Speeds, under Blocks and Items). Beta's
  pickaxes, though you need one to get redstone from the ore, were slow on it: 4.5 seconds with an iron pickaxe,
  where iron ore takes 0.75. Obsidian keeps beta's speed. On a server, redstone ore needs 0.2.7 or later on both
  sides, and players on 0.2.6 or earlier mine the blocks Tool Speeds speeds up at beta's speed on a server with this
  version.
- `oldfaithful-settings` no longer holds the Faithful Library's on/off switches (they're More Faithful's own now), which
  matters only if you've added the library's mods: take Faithful Core from the library's 0.9.0 with this version, as an
  older one stops the game when a server refuses you over mods; in an instance from the library's old pack for Old
  Faithful, keep its own `oldfaithful-settings` (the library's README says why).

## 0.2.6 (2026-11-11)

Released with Still Faithful 0.7.1, which carries it; on Pure Faithful 0.1.6. Old Faithful's own mods are unchanged.

- From Pure Faithful 0.1.6: the bow, doors opening and closing, and explosions sound as they did in beta, where launchers
  today give later recordings. Beta's bow and doors, which Mojang no longer offers, are fetched once from Modification
  Station's archive of beta's files. Pure Faithful's changelog has the detail.

## 0.2.5 (2026-11-11)

Released with Still Faithful 0.7.0, which carries it; on Pure Faithful 0.1.5, as 0.2.4 was. Two new fixes, each on its
own switch:

- On a server, while you ride a boat, minecart or pig, other players see your head turn, and the server knows which
  way you face (Riding Look, under Multiplayer). Beta's game told the server where you looked while riding only when
  you held your view still, so to others you stared ahead until you stopped turning. Now it says so every tick you
  ride, in the same message beta sent when you held still, which any server reads. It's your game's switch.
- Mobs at the edges of the screen no longer vanish while part of them is still in view. Beta stopped drawing a mob as
  soon as its hitbox left the view, but most mobs reach past their hitbox: a cow's head, a zombie's arms, a squid's
  tentacles, the top of a ghast's body, and any mob falling over as it dies. So as you turned, a head or a tentacle at the
  edge of the screen blinked out, and something moving fast could vanish a moment before it left. Now a mob stays drawn
  while any part of it is in view (Mobs at Edges, under Looks and Sounds). It changes only what your own game draws,
  so it works on any server.

And a correction to an earlier fix: on a server, after a teleport or a fast move, the chunk you stand in now always
arrives first. Standing at an x or z of -1, -17, -33 and so on, it came second. It's the server's switch, `multiplayer.chunkOrder` in its `config/oldfaithful-fixes.properties`.

## 0.2.4 (2026-11-09)

Released with Still Faithful 0.5.1, which carries it; on Pure Faithful 0.1.5, as 0.2.3 was.

- Saving is faster in a world with many blocks waiting to update. Old Faithful saves those waiting updates with their
  chunks (Saved Block Ticks, under Redstone and Lighting: it's why a repeater clock runs on after a reload), and until now a save
  went through every one of them in the world for each chunk it wrote, so the more were waiting, the slower it was. New
  land leaves hundreds of thousands, mostly water and lava: on a server that had made a lot of it, saving took 43
  seconds, and now takes 4. What's saved is the same.

## 0.2.3 (2026-11-09)

Released with Still Faithful 0.4.0, which carries it; on Pure Faithful 0.1.5, whose servers make new land faster (see
its changelog). One new fix, on its own switch, under Mobs:

- A mob that had died stayed solid for the second it took to fall over: it pushed you and other mobs, they pushed it,
  and arrows bounced off it, though nothing could hurt it any more. Now a dying mob pushes nothing, nothing pushes
  it, and arrows, snowballs, eggs and fishing lines fly through it (Dying Mobs). On a server it's the server's
  switch, except whether a dying mob pushes you, which your own game decides.

## 0.2.2 (2026-11-08)

Released with Still Faithful 0.3.0, which carries it; on Pure Faithful 0.1.4, which brings its own fix (see its
changelog): a rare crash when a server makes a new world. Two new fixes, each on its own switch, under Mobs:

- Squid sank to the bottom and stayed within about half a block of it: at some heights their test for being in water
  failed, and they couldn't swim past those. They now swim all through the water, up to the surface (Squid Swimming).
- Squid made footsteps on the sea floor, and lit redstone ore as they went (Squid Footsteps).

And one for servers, on the switch `multiplayer.closedScreens` in the server's `config/oldfaithful-fixes.properties`
(players with any client get it):

- Placing a block, or using any item, a moment after closing a chest, furnace, dispenser or workbench could leave your
  screen showing one more of it than you had, until that slot changed again.

For the Faithful Library: while a world's rules are locked, the settings' Mods menu doesn't switch off a library mod
that has rules. Without the library nothing changes.

## 0.2.1 (2026-11-06)

Released with Still Faithful 0.2.2, which carries it; still on Pure Faithful 0.1.3. Two new fixes, each on its own
switch, under Players and Vehicles:

- With a block low overhead (a roof 2 above the floor, say), a step could sink you about a third of a block into the
  floor or the block ahead, and you were shoved back out. A step now comes down only as far as it went up. Mobs sank
  the same way and no longer do (Steps Under Roofs).
- Rejoining a world right against a wall, a rounding slip could put you a hair inside it. You then walked into the wall,
  suffocating if it reached your eyes, until you were pushed out.
  The sides of your box now stop at the wall (Rejoining by Walls).

A correction: Old Faithful 0.1.3 said that rejoining a world far from its spawn, the walls around you were solid from
the first moment, and its README said you couldn't walk into one and suffocate. That was wrong. Testing since shows
the game doesn't move you until the land near you has loaded, so that change didn't
address the problem it was made for. What was reported then may have been the rounding slip fixed here, though
we can't be certain. In our tests the change made no difference a player would notice, so its switch (Rejoining Worlds,
`world.rejoinTerrain`) is gone; a config file that still names it loads as before.

We can't show that these two fixes cover every time you've been moved into a block and pushed out. If it happens
again, please tell us in an issue where you were and whether anything was overhead.

## 0.2.0 (2026-11-05)

Released with Still Faithful 0.2.0, which carries it; still on Pure Faithful 0.1.3.

- The settings screen (Options > Old Faithful...) is in menus now: **Fixes**, a page for each kind of fix, and
  **Changes**, for the window, mouse and display settings and the Quit Game button. Long sections turn over with
  arrows beside Done. Switches keep their names and config files, so your settings carry over.
- Fixes for menus and screens have a section of their own, **Screens**.
- A new fix, on its own switch (World Screen Titles, under Screens): in a big window, the Create New World and Rename
  World titles stay at the top instead of coming down among the fields.
- The settings screen is a small mod of its own, **oldfaithful-settings**, in the pack. If you use Old Faithful's
  mods on their own, add its jar beside them: Old Faithful Options needs it.

## 0.1.4 (2026-11-03)

Released with Still Faithful 0.1.2, which carries it; still on Pure Faithful 0.1.3. Two new fixes, each on its own
switch (Chunk Seams and Texture Edges, under Looks and Sounds):

- Dots of sky no longer flicker along block edges every 16 blocks, where the chunks the world is drawn in meet (beta
  placed each chunk a hair off; most visible west and north of 0, 0 and far from it).
- Dots of sky no longer flicker along some edges of grass, stone, dirt, planks, bricks, wool and other blocks, where
  a face's texture read the transparent picture beside it.

## 0.1.3 (2026-11-03)

Released with Still Faithful 0.1.1, which carries it; still on Pure Faithful 0.1.3. A new fix, on its own switch:

- Rejoining a single-player world far from its spawn, the walls around you are solid from the first moment (beta
  loaded the land around the spawn first, so for a moment you could walk into a wall and suffocate).

  Corrected in 0.2.1: this was wrong, and the change didn't address that problem.

## 0.1.2 (2026-11-03)

Released with the first Still Faithful, which carries it; still on Pure Faithful 0.1.3. Nothing changes for Old
Faithful players.

- The settings screen can show a setting whose choices switch partly at once and partly after a restart (Still
  Faithful's Dusk).

## 0.1.1 (2026-11-02)

On Pure Faithful 0.1.3, which brings its own fixes (see its changelog). New fixes, each on its own switch:

- Falling into water one block deep no longer hurts from some heights as if you'd landed on stone.
- Sneaking stops you at an inside corner when you step off it diagonally.
- Mobs look up or down at you without nodding their heads.
- West and north of 0, 0, mobs spawn beside water and lava as they do elsewhere; squid no longer spawn in lava.
- Falling sand and gravel no longer delete blocks of their own kind they're pushed into.
- A door's top half standing on its own (only in an edited world) no longer deletes the block under it.
- Multiplayer: spawners show the mob they spawn, where both the server and the player have Old Faithful (a server
  with it sends nothing vanilla clients would notice). Explosions push a player who was hurt a moment before. A /tp
  in mid-fall no longer lands the fall at the other end. The server no longer drowns a player whose screen shows
  full air (the two measured the water at slightly different heights).

## 0.1.0 (2026-11-02)

The first release of Old Faithful, for Windows, on Pure Faithful 0.1.2.

- Fixes for beta 1.7.3's own clear mistakes, each on a switch in `config/oldfaithful-fixes.properties`: mining and
  tools (a tool's last use, wooden slabs, tools slow on their own materials), items and crafting (the furnace's lava
  bucket, shift-click crafting, pick block, a crash on bad slab and dye items), the world (water washing items away,
  slimes, mobs through walls, saddles, minecarts and items, fishing, getting out of boats), redstone (repeaters,
  clocks that stop on reload, torch burnout), the screen and sound (the grey sky with a chest open, the cursor's
  item, fence undersides, sliders, footsteps after stairs and more) and multiplayer (ghost blocks, mob jitter,
  players unseen after a teleport, the order chunks arrive in, the mining pause, blank signs, frozen entities after
  a respawn, players turned a few degrees off).
- Lighting as beta's own rule says it should be: no dark pockets under overhangs or at chunk edges, lava from world
  generation lit; existing worlds heal as they load.
- Options: a settings screen (Options > Old Faithful...) for every switch, a field-of-view slider, the GUI at Normal
  on a first start, Quit Game on the title screen, and the window maximised the first time and remembered after.
- "Old Faithful 0.1.0" on the title screen, under beta's version line.
