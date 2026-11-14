# Still Faithful

**beta 1.7.3 as it might have been**

Minecraft beta 1.7.3 with the polish later versions brought while the game stayed beta in content. Still Faithful is
[Old Faithful](https://github.com/studio-quire/old-faithful) (beta as it was meant to work, on
[Pure Faithful](https://github.com/studio-quire/pure-faithful): safe online, faster, and unable to lose your world)
plus the improvements that came after beta, each on a switch. Nothing new to collect or craft; beta's world, made
kinder to live in. Open source, from [Studio Quire](https://studioquire.org/).

Still Faithful is mods, not a modified game: your launcher downloads Minecraft itself from Mojang, with your own
account. Nothing of Mojang's is in this repository or in our downloads. For Windows now; macOS and Linux follow.

## What it changes

Every change has its own switch, all on: in the game under **Options > Still Faithful... > Changes > Polish**, or in
`config/stillfaithful-polish.properties`. Switch one off to have beta's behaviour back (after a restart).

- **Dusk and dawn without the steps.** Beta darkens the world in eleven steps, and at each one the chunks around you
  are redrawn in a wave across the landscape. Here the light is applied as the world is drawn, so nothing is
  redrawn: dusk and dawn glide smoothly, the fog with them. Dusk can also be **Stepped** (beta's own eleven pictures,
  each changing the whole view at once) or **Beta** (beta's way exactly). Smooth and Stepped switch at once; Beta
  takes a restart.
- **Stairs** drop themselves, not a plank or a cobblestone.
- **Leaves you place** never decay; leaves on trees still do.
- **Leaves on a tree** stay within 6 blocks of its wood, counted through the leaves, as in later versions. Beta's
  reached 4, and the upper corners of a big oak's leaf clusters are 5 or 6 from its wood, so once a log or leaves near
  them were cut they fell, and the fall spread, dropping saplings onto the tree (Leaf Reach). On a server, the
  server's switch.
- **Water and cactus are lit smoothly** while Smooth Lighting is on. Beta lit water a block at a time and cactus a
  face at a time even then, so torchlight lay on a pond in squares and a cactus stood out flat beside the smoothly lit
  sand. Water darkens a little against a wall or a bank, as the ground does (Smooth Water: **Shaded**; **Even** lights
  it smoothly without darkening it, **Beta** as beta did), and cactus darkens toward its foot, as in later versions
  (Smooth Cactus). Lava keeps beta's look. With Smooth Lighting off, both look as they did. Smooth Water's choice
  shows at once, but from Beta after a restart. Your own game's, on any server.
- **Chests** face you when you place them, and pair as in later versions: a chest joins one beside it only if that
  one faces the same way, stands to its left or right and isn't joined already, and never while you sneak, so double
  chests can stand side by side in a row. A double chest has one lid across both halves, made from your texture
  pack's lid. Beta worked a chest's front out from the blocks around it (south when standing free, sideways in a
  corner) and joined any two chests side by side; chests placed before keep that.
- **Shift-click** works where beta's stopped short: in a dispenser both ways, into a furnace (what smelts to the top
  slot, fuel to the bottom), and armour from your inventory onto you. It's made of clicks you could make yourself, so
  it works on any server.
- **Holding the button** to attack or mine goes on hitting only what you clicked. Beta repeated your click four times
  a second on whatever came under the crosshair, so holding on after mining a block hit the animal behind it (Held
  Attack). Mining, and a new click on anything, are as before. Your own game's, on any server.
- **Wooden things burn** in a furnace: wooden tools and swords, bows, fishing rods, bowls, signs, wooden doors,
  ladders, boats and wooden slabs, for as long as later versions burn them (a boat smelts six items). Beta burned
  planks, logs, sticks, saplings and most wooden blocks, but none of these.
- **Glowstone** drops its dust when broken by hand or with any tool, and breaks at the speed it had from 1.0.
- **Obsidian** mines as in 1.0: 9.4 seconds with a diamond pickaxe instead of 15, and 250 without one instead of 50
  (Diamond Only, in the settings, keeps beta's 50).
- **Lava** cut off from its source thins out instead of lingering for good. **Water** between two sources becomes a
  source over still water too, not only over solid ground.
- **Boats** break only in a real crash: scraping along a shore or bumping a floating item no longer shatters them,
  and items no longer stop them. A boat nobody is paddling slows to a stop in a few blocks and rides the water
  steadily. They steer as in later versions: A and D turn the boat and your view with it, W paddles it the way it
  faces, up to 6.5 blocks a second (beta's reached about 5.8), and a boat you place faces the way you look; beta's went
  the way you looked and turned your view as they swung round (Boat Steering). On a server, the server's switch, for
  players whose game has Old Faithful's Riding Look; others paddle as in beta.
- **Minecarts** meet on every track as they did on east-west track: on north-south and curved track a cart no longer
  stops dead against another, so bumping, furnace trains and boosters work there too. Carts on parallel tracks still
  leave each other alone.
- **Animals you've looked after stay**: sheared, milked, saddled, dyed, ridden, carried in a minecart, or hatched
  from an egg you threw. Wild animals still come and go as in beta, and hitting one doesn't keep it.
- **Saving without the pause.** The game's save every couple of seconds is compressed and written in the background:
  the same saves at the same moments, crash-safe as Pure Faithful's (it needs Pure Faithful's saves mod).
- **Fences** are a post with arms to the fences beside them, as in later versions, instead of a whole block: you can
  stand against a post, walk past its corner and aim past it, and still can't jump over. On a server, walking past
  posts needs the server to have this too; aiming past them works anywhere. Posts that only touch diagonally no longer
  close a gap to players and the narrower mobs.
- **Fences join walls** and other blocks, as in later versions, not only other fences: a fence beside stone, planks,
  glass, a chest, the back of stairs, a door's flat side or any other block with a whole, solid side toward it reaches
  out to it, where beta's stood apart as a post. Leaves, pumpkins and jack-o'-lanterns are left apart (Fence Joins). With
  Fence Shape, above, the arms are also what you aim at and bump into. Your own game draws them on any server; bumping
  into them on a server needs it to have Fence Shape, as walking past posts does. Fences already built change too: a
  post standing against a wall now reaches out to it.
- **Chat** lines each have a dark bar only as long as their text, not the chat's full width.
- **Skeletons** shoot from their eyes, as in later versions, instead of from well above their heads, so in a tunnel,
  a low cave or a doorway two blocks high their arrows no longer stick in the ceiling: they can hit you there.
  Standing elsewhere, you're hit about as often as beta's hit you. On the move it can differ: walking straight at one
  you're hit more often, as beta's arrows came down over a player walking in, and along a ledge just above or below
  one close by, less or more often. A skeleton riding a spider shoots past its spider, as in later versions, so close
  below one you're hit more often than in beta, where its arrows hit the spider (Skeleton Arrows). On a server, the
  server's switch.
- **Squid** that swim up to the surface slow as they near it and swim on just under it, their backs at the waterline,
  instead of bouncing against it a few times a second (Squid Surfacing). On a server, the server's switch.
- **Blasts and small things show from further away.** Beta made no particle more than 16 blocks from you, so a blast
  at bow range showed nothing, and it stopped drawing a dropped item past 16 blocks, a chicken past 21 and an arrow
  past 32. Here particles show within 32 blocks, as in later versions, the puffs of a blast or a mob dying within 64,
  and everything is drawn to 64 blocks at least, as far as a zombie (Particle Distance and Entity Distance). Your own
  game's, on any server.
- **Dropped stairs, fences** and other part-blocks lie at the size of other dropped blocks, and players and
  skeletons hold a bow like a bow.
- **Modern skins** show whole: skins made for later versions get their jacket, sleeves and trousers, a left arm and
  leg of their own, and slim arms where the skin has them, drawn as a modern client draws them, half-transparent
  pixels included. Older skins look as they did in beta. Yours and other players', on any server; the server needs
  nothing.
- The title screen's Mods and Texture Packs button reads **Texture Packs**, which is all it ever held.

Smooth and stepped dusk need OpenGL 2.0 and have so far been tested only on NVIDIA graphics. If the sky or the
landscape looks wrong, set **Dusk** to **Beta** (after a restart it's beta's own way), and please tell us in an issue.
Smooth dusk doesn't combine with mods that build chunks off the main thread or change how they're built (SmoothBeta,
for one): set Dusk to Beta with those.

Everything else is Old Faithful's and Pure Faithful's: beta's own mistakes fixed, lighting as beta's rule says it
should be, a field-of-view slider, a settings screen for every switch, a resizable window and borderless fullscreen,
speed with the same picture, safe logins, and saves that survive crashes. What players built their game around
(world generation, the closing-the-window undo) stays as beta had it; the fence's whole-block hitbox behind "smart
farms" comes back with Fence Shape off.

Worlds stay in beta's own format and open in the original game, Pure Faithful and Old Faithful too; there, placed
leaves can decay again and kept animals can despawn.

## Download and play

You need a Minecraft account that owns the game, and [Prism Launcher](https://prismlauncher.org/).

1. Download **StillFaithful-&lt;version&gt;.zip** from [the latest release](https://github.com/studio-quire/still-faithful/releases).
2. In Prism Launcher: **Add Instance > Import**, choose the zip (or paste the download link), **OK**.
3. Launch it. Prism downloads beta 1.7.3 and its sounds from Mojang, and, if you let it, a Java 21 runtime.

Everything else is inside the zip, and nothing comes from us. Besides Mojang (your login, skins and sounds) and the
servers you join, the game reaches one other place: on its first start it fetches beta's own bow and door sounds
(about 28 KB) from the archive of beta's files kept by Modification Station, a community of beta modders. To keep it
from doing so, before you first launch it set `resources.download=false` in
`.minecraft/config/purefaithful-hardening.properties`, creating the file if it isn't there. The title screen shows "Still
Faithful" and its version under beta's own version line.

## For existing instances

Already playing b1.7.3 on Fabric (Babric) in Prism? **stillfaithful-polish** (client and server) works on its own:
drop the jar from the release into the instance's `.minecraft/mods`. Without Old Faithful's settings mod its switches
are only in the config file, its background saving needs Pure Faithful's saves mod, and modern skins need Pure
Faithful's hardening mod, which fetches skins at all; everything else works anywhere.

The whole of Still Faithful on its own is this mod plus Old Faithful's four and Pure Faithful's four mods and its
LWJGL, from the Old Faithful and Pure Faithful releases the Still Faithful release names;
[Pure Faithful's README](https://github.com/studio-quire/pure-faithful#for-existing-instances) says how to install
its LWJGL.

## Servers

`StillFaithful-server-<version>.zip` is beta 1.7.3's own server with Pure Faithful's hardening, saves and speed mods,
Old Faithful's fixes and lighting, and Still Faithful's polish mod: bring Mojang's server jar (the scripts check it's
genuine) and run `start.bat` or `start.sh`. Players can join with vanilla beta 1.7.3 or any of the three editions; the
server's changes (drops, leaves, water, lava, boats, carts, animals, saving, chest pairs, furnace fuel) work for
everyone, though players without Still Faithful see the new double chests drawn oddly, boats steer only for players
whose game has Old Faithful's Riding Look (others paddle as in beta), and faster glowstone mining needs Still Faithful
on both sides. Players without Still Faithful 0.8.0 or later, or with its Obsidian set to Beta, mine obsidian at beta's
speed: with a diamond pickaxe the server accepts it, but without one the obsidian they break at 50 seconds comes back,
and goes for good at 250, unless the server's Obsidian setting is Diamond Only or Beta. On a server with Fence Joins and
Fence Shape, a player with Fence Shape whose Still Faithful is 0.8.1 or earlier, or who has Fence Joins at Beta, may be pulled back stepping
from the top of a wall one block high onto a fence that joins it.

## Problems

Please [open an issue](https://github.com/studio-quire/still-faithful/issues) and attach
`.minecraft/logs/purefaithful/latest.log` from the instance folder. It never contains your access token. If a change
clashes with another mod, its switch turns it off completely.

## Building

This repository holds all of Still Faithful's source, Old Faithful's and Pure Faithful's included. `./gw releaseStill`
builds everything a Still Faithful release contains into `local/release/still-faithful-<version>/` (and `./gw
releaseFixed` and `./gw release` the other two editions'). [docs/building.md](docs/building.md) and
[docs/release.md](docs/release.md) say how; the changes are described in [docs/still-faithful.md](docs/still-faithful.md),
with every bug's cause in [docs/bugs.md](docs/bugs.md). Inside, the project is called by its working name,
`oldfaithful`.

## Credits and licence

Made by Claude Opus 5.5, an AI model by Anthropic, at Studio Quire. Idea, direction and play-testing: Shoalstone.

Still Faithful's code is under the [MIT licence](LICENSE). It builds on Fabric Loader, Mixin, ASM, Babric's mappings,
LWJGL, SDL and OpenAL Soft, each under its own licence; the downloads carry their notices and licence texts, and the
source code their licences ask for.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
