# Changelog

Still Faithful's releases, newest first. Each has its own version number, carried by Still Faithful's mod, its pack
and its server package, and names the Old Faithful and Pure Faithful releases it's built on, whose mods and LWJGL
are inside the pack as those releases have them;
[Old Faithful's changelog](https://github.com/studio-quire/old-faithful/blob/main/CHANGELOG.md) and
[Pure Faithful's](https://github.com/studio-quire/pure-faithful/blob/main/CHANGELOG.md) cover those. Entries say
what a player or server owner notices.

## 0.7.0 (2026-11-11)

On Old Faithful 0.2.5 and Pure Faithful 0.1.5.

- Boats steer as in later versions (Boat Steering, a new switch under Polish, on). A and D turn the boat, and your view
  turns with it; W paddles it forward the way it faces, S backs it slowly, and A or D alone lets it creep forward as it
  turns. Beta's boats went the way you looked, swung round to face wherever they were moving and turned your view with
  them, so paddling while looking anywhere but straight ahead sent them in circles. They're a little faster too: up to
  6.5 blocks a second, where beta's reached about 5.8. A boat nobody paddles slows to a stop in a few blocks, and a boat you place
  faces the way you look. On a server, this is the server's switch, and it steers for players whose game tells the
  server where they look every tick while they ride: Old Faithful's new Riding Look fix does, so Old Faithful, Still
  Faithful and More Faithful players steer, from the moment their view first moves once they're in. Until then, and for
  players with any other game, boats paddle as in beta.
- Boats no longer bob up and down at the surface (Boat Handling). Beta's boat never settled on the water, it swung up
  and down for as long as it floated; Boat Handling made the swing smaller but never stopped it. Now a boat settles
  after a bob or two, about as high in the water as beta's, and stays there, empty or ridden, still or paddled, and
  your view with it. On a server this is the server's switch, and players with any game see the boat still.
- Holding the button to attack or mine no longer hits whatever comes under the crosshair (Held Attack, a new switch
  under Polish, on). Beta repeated your click four times a second while you held the button, on whatever the crosshair
  was on then, so holding on after mining a block hit the animal behind it, and sweeping the crosshair across your wolf
  in a fight hit the wolf. Now a hold goes on hitting only what you clicked, and passes over anything else; mining, and
  a new click on anything, are as before. (Later versions hit only when you click, so a hold on a mob hits it once;
  here it goes on hitting it, as in beta.) Your own game's, on any server: the server needs nothing.
- From Old Faithful 0.2.5, which it carries: mobs at the edges of the screen stay drawn while any part of them
  is in view, where beta dropped them while a head, arms or tentacles still showed; and a correction to the fix that
  sends a server's chunks nearest first. Old Faithful's changelog has the detail.

## 0.6.1 (2026-11-10)

On Old Faithful 0.2.4 and Pure Faithful 0.1.5, as 0.6.0 was.

- A skeleton riding a spider no longer shoots its own spider (Skeleton Arrows). Since 0.6.0 its arrows started at its
  eyes, just above the spider, so the more steeply it shot down at you, the more of them hit the spider instead: nearly
  all when it was 4 blocks above you and up to 6 blocks away. Now they pass it, as in later versions. From a few blocks
  off you're hit about as often as before 0.6.0; right below it, more often, since there even beta's arrows hit the
  spider. On a server, the server's switch.

## 0.6.0 (2026-11-10)

On Old Faithful 0.2.4 and Pure Faithful 0.1.5, as 0.5.1 was. In Still Faithful's own mod, on new switches under Polish,
all on:

- Skeletons shoot from their eyes, as in later versions, instead of from well above their heads (Skeleton Arrows).
  Under a ceiling two blocks high (a tunnel, a low cave, a doorway) their arrows used to stick in the ceiling, so they
  never hit you there; now they can. Standing elsewhere, you're hit about as often as before. On the move it can
  differ: walking straight at a skeleton you're hit more often, since beta's arrows came down from above its head and
  passed over you, and walking along a ledge just above or below one close by, less or more often. On a server,
  this is the server's switch, and works for players with any client. If you played 0.4.0, which had this switch off
  unless you turned it on, your settings may still have it off: Options > Still Faithful... > Changes > Polish, or
  mobs.skeletonArrows in config/stillfaithful-polish.properties.
- Squid at the surface no longer bounce (Squid Surfacing). A squid that swam up to the surface bounced against it:
  each time it came halfway out it dropped back and swam up again, a few times a second, its tentacles twitching. Now
  it slows as it nears the surface and swims on just under it, its back at the waterline, until it turns. Where squid
  swim and end up is otherwise as before. On a server, this is the server's switch, and works for players with any
  client.
- Blasts show from further away (Particle Distance). Beta made no particle more than 16 blocks from you, so a blast or
  a mob dying further off left no puff, even within a bow shot. Now particles show within 32 blocks, as in later
  versions, and the puffs of a blast or a mob dying within 64, as far as a blast is heard.
- Small things show from further away (Entity Distance). Beta drew a thing only within a distance that went by its
  size, so a dropped item vanished past 16 blocks, a chicken past 21 and an arrow past 32. Now everything shows within
  64 blocks at least, as far as a zombie; bigger things as far as before.

Particle Distance and Entity Distance are your own game's: they work on any server, which needs nothing.

## 0.5.1 (2026-11-09)

On Old Faithful 0.2.4, which saves faster in a world with many blocks waiting to update (see its changelog), and Pure
Faithful 0.1.5.

- Wooden Fuel's description in the settings took three lines where there's room for two, so it could run over the line
  under it. It's shorter now.

## 0.5.0 (2026-11-09)

On Old Faithful 0.2.3 and Pure Faithful 0.1.5, as 0.4.1 was. In Still Faithful's own mod, on its own switch under
Polish:

- Wooden things burn in a furnace (Wooden Fuel): wooden tools and swords, bows, fishing rods, bowls, signs, wooden
  doors, ladders, boats and wooden slabs, for as long as later versions burn them. A boat smelts six items; a bow,
  fishing rod or ladder one and a half, as a plank does; a tool, sign or door one; a wooden slab three quarters; a
  bowl half. Beta burned planks, logs, sticks, saplings and most wooden blocks, but none of these. On a server,
  whether they burn is the server's switch, and works for players with any client; shift-click sends them to the
  fuel slot when your own game has the switch on.

## 0.4.1 (2026-11-09)

On Old Faithful 0.2.3 and Pure Faithful 0.1.5, as 0.4.0 was.

- Skeleton Arrows, new in 0.4.0 and off unless you turned it on, is gone. If you had turned it on, skeletons shoot
  from well above their heads again, as in beta, so in a tunnel two blocks high their arrows stick in the ceiling
  again; otherwise nothing changes. A config file that still names it loads as before.

## 0.4.0 (2026-11-09)

On Old Faithful 0.2.3 and Pure Faithful 0.1.5. In Still Faithful's own mod, each on its own switch under Polish:

- Chests pair as in later versions (Chest Pairs, which needs Chest Facing on): a chest you place joins one beside it
  only if that one faces the same way, stands to its left or right and isn't joined already, and never while you
  sneak. A chest can go anywhere, beside a double chest too, so double chests can stand side by side in a row. Chests
  placed before keep the pairs they had. On a server this is the server's switch; players without Still Faithful see
  a row of these double chests drawn oddly, though they open as they should.
- A double chest has one lid across both halves, as in later versions, made from your texture pack's own chest lid,
  instead of a border down the middle (Chest Lids).
- Shift-click works where beta's stopped short (Shift-Click): in a dispenser both ways, as in a chest; into a furnace,
  what smelts to the top slot (logs too) and fuel to the bottom, unless that slot holds something else; and armour
  from your inventory onto you, where that place is empty. It's made of the clicks you could make yourself, so it
  works on any server.
- Skeletons can shoot their arrows from their eyes, as in later versions, instead of from well above their heads,
  with beta's speed and aim (Skeleton Arrows). Off unless you turn it on; on a server, the server's switch.

Pure Faithful 0.1.5's servers make new land faster. From Old Faithful 0.2.3, on its own switch under Fixes > Mobs:

- A mob that had died stayed solid for the second it took to fall over: it pushed you and other mobs, they pushed it,
  and arrows bounced off it, though nothing could hurt it any more. Now a dying mob pushes nothing, nothing pushes
  it, and arrows, snowballs, eggs and fishing lines fly through it (Dying Mobs). On a server it's the server's
  switch, except whether a dying mob pushes you, which your own game decides.

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
