# Changelog

Old Faithful's releases, newest first. Each has its own version number, carried by Old Faithful's mods, its pack and
its server package, and names the Pure Faithful release it's built on, whose mods and LWJGL are inside the pack as
that release has them; [Pure Faithful's changelog](https://github.com/studio-quire/pure-faithful/blob/main/CHANGELOG.md)
covers those. Entries say what a player or server owner notices.

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
