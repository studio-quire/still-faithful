Still Faithful @VERSION@
@TAGLINE@

This folder is a Prism Launcher instance. Prism downloads Minecraft beta 1.7.3 itself, from Mojang, the first time
you launch it; everything else Still Faithful needs is already here. You need a Minecraft account that owns the game.

Still Faithful is beta 1.7.3 with polish, as it might have been: Old Faithful @FIXED_VERSION@ (beta as it was meant
to work, on Pure Faithful @PURE_VERSION@) plus the improvements later versions made while the game stayed beta in
content. Nothing new to collect or craft; beta's world, made kinder to live in.

Still Faithful's own, each on its own switch:
- Stairs drop themselves, not a plank or a cobblestone.
- Leaves you place never decay; leaves on trees still do.
- Chests face you when you place them, and pair as in later versions: only with a chest to the left or right that
  faces the same way and isn't joined already, never while you sneak, so double chests can stand side by side. A
  double chest has one lid across both halves. Chests placed before keep beta's rules.
- Shift-click works in a dispenser both ways, into a furnace (what smelts to the top, fuel to the bottom), and puts
  armour on, from your inventory. It works on any server.
- Wooden tools, bows, fishing rods, bowls, signs, wooden doors, ladders, boats and wooden slabs burn in a furnace, as
  in later versions; beta burned planks, logs, sticks, saplings and most wooden blocks, but none of these.
- Glowstone drops by hand or with any tool, as from 1.0.
- Lava cut off from its source thins out instead of lingering for good, and water between two sources becomes a
  source over still water too.
- Boats: only a real crash or a hit breaks them, and floating items no longer stop them. A boat you stop paddling
  slows to a stop in a few blocks and rides the water steadily; top speed and turning are beta's.
- Minecarts meet on every track as they did on east-west track: on north-south and curved track a cart no longer
  stops dead against another, so furnace trains, bumping and boosters work there too. Carts on parallel tracks still
  leave each other alone.
- Dusk and dawn glide smoothly, fog included, without beta's eleven steps and the wave of chunks redrawing across
  the landscape at each one (Options > Still Faithful... > Changes > Polish > Dusk: Stepped keeps beta's steps, each changing
  the whole view at once). Smooth and Stepped need OpenGL 2.0 and have so far been tested only on NVIDIA graphics:
  if the sky or the landscape looks wrong, set Dusk to Beta, beta's own way, after a restart.
- Animals you've looked after stay: sheared, milked, saddled, dyed, ridden, carried in a minecart or hatched from
  your egg. Wild ones still come and go as in beta.
- Saving without the pause: the game's save every 2 seconds is compressed and written in the background, the same
  saves at the same moments, crash-safe as Pure Faithful's.
- Fences are a post with arms to the fences beside them, as in later versions, instead of a whole block: you can stand
  against a post, walk past its corner and aim past it, and still can't jump over. On a server, walking past posts
  needs the server to have this too.
- Each line of chat has a dark bar only as long as its text, not the chat's full width.
- Skeletons shoot from their eyes instead of from well above their heads, so under a ceiling two blocks high their
  arrows no longer stick in it; elsewhere they hit a player standing about as often as before (Skeleton Arrows).
- Squid that swim up to the surface slow as they near it and swim on just under it, instead of bouncing against it a
  few times a second (Squid Surfacing).
- Blasts and small things show from further away: particles within 32 blocks (beta's 16), the puffs of a blast or a
  mob dying within 64, and dropped items, arrows, chickens and everything else drawn to 64 blocks at least, as far as
  a zombie (beta stopped drawing a dropped item at 16).
- Dropped stairs, fences and other part-blocks lie at the size of other dropped blocks, and players and skeletons
  hold a bow like a bow.
- Modern skins show whole: jacket, sleeves and trousers, a left arm and leg of their own, and slim arms where the
  skin has them, drawn as a modern client draws them. Older skins look as they did in beta.
- The title screen's Mods and Texture Packs button reads Texture Packs, which is all it ever held.
- The title screen says "Still Faithful @VERSION@" under beta's own version line.

From Old Faithful: fixes for beta's own clear mistakes, lighting as beta's own rule says it should be, a field-of-view
slider, and Options > Still Faithful... with every switch in the game, in menus of Fixes and Changes. From Pure Faithful: its own resizable window,
borderless fullscreen (F11), speed with the same picture, safe logins, skins and sounds, and saves that survive
crashes and power cuts.

Your worlds are in .minecraft/saves, in beta 1.7.3's own format: they open in the original game, Pure Faithful and
Old Faithful too (there, placed leaves can decay again and kept animals can despawn).

Settings: in the game, Options > Still Faithful... has every switch, in beta's own style; Still Faithful's are under
Changes > Polish. Most changes take effect after a restart (the screen marks one that's waiting); Dusk's Smooth and Stepped
switch at once. The same settings are files in .minecraft/config:
- stillfaithful-polish.properties: a switch for each of Still Faithful's changes, all on.
- oldfaithful-fixes.properties, oldfaithful-lighting.properties, oldfaithful-options.properties: Old Faithful's.
- purefaithful-display.properties and purefaithful-hardening.properties: Pure Faithful's.

If something goes wrong, the game's log is .minecraft/logs/purefaithful/latest.log.

Made by Claude Opus 5.5, an AI model by Anthropic, at Studio Quire. Idea, direction and play-testing: Shoalstone.
Open source under the MIT licence. Software by others included here, and its licences: THIRD-PARTY-NOTICES.txt,
licenses/ and sources/.
Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
