# Changelog

Still Faithful's releases, newest first. Each has its own version number, carried by Still Faithful's mod, its pack
and its server package, and names the Old Faithful and Pure Faithful releases it's built on, whose mods and LWJGL
are inside the pack as those releases have them;
[Old Faithful's changelog](https://github.com/studio-quire/old-faithful/blob/main/CHANGELOG.md) and
[Pure Faithful's](https://github.com/studio-quire/pure-faithful/blob/main/CHANGELOG.md) cover those. Entries say
what a player or server owner notices.

## 0.1.2 (2026-11-03)

On Old Faithful 0.1.4 and Pure Faithful 0.1.3. Still Faithful's own mod is unchanged.

- From Old Faithful 0.1.4: dots of sky no longer flicker along block edges, where the chunks the world is drawn in
  meet or at the edges of some blocks' textures (Chunk Seams and Texture Edges, under Looks and Sounds).

## 0.1.1 (2026-11-03)

On Old Faithful 0.1.3 (walls solid from the first moment when you rejoin a world far from its spawn) and Pure
Faithful 0.1.3.

- Chests face you when you place them, and a second chest beside one joins its facing, on its own switch (Chest
  Facing, under Polish). Chests placed before keep beta's rule, which worked the front out from the blocks around.

## 0.1.0 (2026-11-03)

The first release of Still Faithful, for Windows, on Old Faithful 0.1.2 and Pure Faithful 0.1.3. Each change on its
own switch in `config/stillfaithful-polish.properties` and under Options > Still Faithful... > Polish:

- Dusk and dawn without beta's steps and the wave of chunks redrawn at each: smooth by default, fog included, or
  stepped (beta's own pictures, the whole view at once), or beta's way. Smooth and stepped need OpenGL 2.0 and have
  been tested only on NVIDIA graphics so far: if anything looks wrong, set Dusk to Beta.
- Stairs drop themselves; placed leaves never decay; glowstone drops by hand or with any tool.
- Lava cut off from its source thins out; water between two sources becomes a source over still water.
- Boats break only in a real crash, and slow to a stop when nobody paddles.
- Minecarts meet on north-south and curved track as on east-west.
- Animals you've sheared, milked, saddled, dyed, ridden, carted or hatched stay.
- Chunk saves compressed and written in the background (with Pure Faithful's saves mod).
- Dropped part-blocks at the size of other dropped blocks; bows held like bows.
- "Still Faithful 0.1.0" on the title screen, under beta's version line.
