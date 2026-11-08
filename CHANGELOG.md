# Changelog

Still Faithful's releases, newest first. Each has its own version number, carried by Still Faithful's mod, its pack
and its server package, and names the Old Faithful and Pure Faithful releases it's built on, whose mods and LWJGL
are inside the pack as those releases have them;
[Old Faithful's changelog](https://github.com/studio-quire/old-faithful/blob/main/CHANGELOG.md) and
[Pure Faithful's](https://github.com/studio-quire/pure-faithful/blob/main/CHANGELOG.md) cover those. Entries say
what a player or server owner notices.

## 0.3.0 (2026-11-08)

On Old Faithful 0.2.2 and Pure Faithful 0.1.4. In Still Faithful's own mod, each on its own switch under Polish:

- Fences are a post with arms to the fences beside it, as in later versions, instead of a whole block: you can stand
  right up against a post, walk past its corner, and aim past it at what's behind, and the outline shows the post and
  arms. They're still too high to jump over. On a server, walking past posts needs the server to have this too
  (elsewhere fences stay whole to walk into, so you're never pulled back); what you aim at is yours anywhere (Fence
  Shape). In builds you already have, posts that only touch diagonally no longer close a gap: a player, wolf, chicken,
  zombie, skeleton or creeper fits between them, as in later versions, while pigs, cows and sheep still don't. Builds
  that relied on the whole block (the "smart farm" trick, which kept crops from being trampled) work only with the
  switch off.
- Each line of chat has a dark bar only as long as its own text, instead of the chat's full width, in play and with
  the chat open (Chat Bars).

Pure Faithful 0.1.4 fixes a rare crash when a server makes a new world. From Old Faithful 0.2.2, each on its own
switch, under Fixes > Mobs:

- Squid sank to the bottom and stayed within about half a block of it: at some heights their test for being in water
  failed, and they couldn't swim past those. They now swim all through the water, up to the surface (Squid Swimming).
- Squid made footsteps on the sea floor, and lit redstone ore as they went (Squid Footsteps).

And one for servers, on the switch `multiplayer.closedScreens` in the server's `config/oldfaithful-fixes.properties`
(players with any client get it):

- Placing a block, or using any item, a moment after closing a chest, furnace, dispenser or workbench could leave your
  screen showing one more of it than you had, until that slot changed again.

## 0.2.2 (2026-11-06)

On Old Faithful 0.2.1 and Pure Faithful 0.1.3. Still Faithful's own mod is unchanged.

From Old Faithful 0.2.1, each on its own switch, under Fixes > Players and Vehicles:

- With a block low overhead (a roof 2 above the floor, say), a step could sink you about a third of a block into the
  floor or the block ahead, and you were shoved back out. A step now comes down only as far as it went up. Mobs sank
  the same way and no longer do (Steps Under Roofs).
- Rejoining a world right against a wall, a rounding slip could put you a hair inside it. You then walked into the wall,
  suffocating if it reached your eyes, until you were pushed out.
  The sides of your box now stop at the wall (Rejoining by Walls).

A correction: Still Faithful 0.1.1 and Old Faithful 0.1.3 said that rejoining a world far from its spawn, the walls around you were solid from
the first moment, and Old Faithful's README said you couldn't walk into one and suffocate. That was wrong. Testing since shows
the game doesn't move you until the land near you has loaded, so that change didn't
address the problem it was made for. What was reported then may have been the rounding slip fixed here, though
we can't be certain. In our tests the change made no difference a player would notice, so its switch (Rejoining Worlds,
`world.rejoinTerrain`) is gone; a config file that still names it loads as before.

We can't show that these two fixes cover every time you've been moved into a block and pushed out. If it happens
again, please tell us in an issue where you were and whether anything was overhead.

## 0.2.1 (2026-11-05)

On Old Faithful 0.2.0 and Pure Faithful 0.1.3, both unchanged. Modern Skins now draws skins as a modern client draws
them. Older skins, mobs and armour look as they did.

- Half-transparent pixels in a jacket, sleeves, trousers or hat blend, instead of showing as solid.
- Seen through a hole in a layer, the layer's far side is lit from your side, as on a modern client.
- The first-person arm and sleeve are drawn the same way, so a sleeve with holes or half-transparent pixels matches
  the rest of the figure.
- A layer painted wholly solid stays. It used to be cleared, as beta clears a hat with no transparent pixels.
- A pixel of the head, body or limbs that the skin's file makes transparent keeps its colour instead of turning black.
- Slim arms and the legs sit a little differently, where a modern client puts them.

## 0.2.0 (2026-11-05)

On Old Faithful 0.2.0 and Pure Faithful 0.1.3. New, each on its own switch under Changes > Polish:

- **Modern skins**: skins made for later versions show whole, with the jacket, sleeves and trousers, separate left
  arm and leg, and slim arms for skins that have them. Older skins look as they did in beta. Yours and other
  players', on any server; the server needs nothing (Modern Skins).
- The title screen's Mods and Texture Packs button reads Texture Packs, which is all it ever held (Texture Packs).

From Old Faithful 0.2.0:

- The settings screen (Options > Still Faithful...) is in menus now: **Fixes**, a page for each kind of fix, and
  **Changes**, with Still Faithful's Polish and the window and mouse settings. Your settings carry over.
- In a big window, the Create New World and Rename World titles stay at the top (World Screen Titles, in Fixes'
  new Screens section).

## 0.1.2 (2026-11-03)

On Old Faithful 0.1.4 and Pure Faithful 0.1.3. Still Faithful's own mod is unchanged.

- From Old Faithful 0.1.4: dots of sky no longer flicker along block edges, where the chunks the world is drawn in
  meet or at the edges of some blocks' textures (Chunk Seams and Texture Edges, under Looks and Sounds).

## 0.1.1 (2026-11-03)

On Old Faithful 0.1.3 (walls solid from the first moment when you rejoin a world far from its spawn) and Pure
Faithful 0.1.3.

Corrected in 0.2.2: that claim about the walls was wrong, and the change didn't address that problem.

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
