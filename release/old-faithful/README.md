# Old Faithful

**beta 1.7.3, fixed and fast**

Minecraft beta 1.7.3 as it was meant to work. Old Faithful is [Pure Faithful](https://github.com/studio-quire/pure-faithful)
(beta exactly as it was, made easy to play on today's computers: safe online, faster, and unable to lose your
world) plus fixes for beta's own clear mistakes, each on a switch. What players built their game around stays as
beta had it. Open source, from [Studio Quire](https://studioquire.org/).

Old Faithful is mods, not a modified game: your launcher downloads Minecraft itself from Mojang, with your own
account. Nothing of Mojang's is in this repository or in our downloads. For Windows now; macOS and Linux follow.

## What it fixes

Mistakes where beta plainly didn't do what it meant to. Every fix has its own switch, all on: in the game under
**Options > Old Faithful...**, or in `config/oldfaithful-fixes.properties`. Switch one off to have beta's behaviour
back (after a restart).

- **Mining and tools.** A tool's last use drops the block it breaks. Wooden slabs drop without a pickaxe. Axes,
  pickaxes and shovels are fast on every block of their material (crafting tables, fences and doors for axes;
  furnaces, rails and redstone ore for pickaxes); obsidian keeps beta's speed.
- **Items and crafting.** The furnace gives back the bucket when it burns lava. Shift-clicking a crafting result
  takes it only if all of it fits. Pick block selects redstone, repeaters, doors, signs, wheat, beds and cake, and
  nothing for a mob. Slab and dye items with a bad data value no longer crash the game.
- **The world.** Water flowing down drops the torches, rails and redstone it washes away. Slimes split however hard
  they're hit. Mobs that wake you can't reach you through a wall. Saddled pigs drop their saddle. Minecarts pass
  through dropped items and arrows. A fish is reeled in to your feet. Getting out of a boat no longer drops you
  through it. Water one block deep breaks a fall from any height. Sneaking stops you at an inside corner too. Mobs
  look at you without nodding. A mob that has died no longer pushes or is pushed while it falls over, and arrows fly
  through it instead of bouncing off. Mobs spawn beside water west and north of 0, 0 as they do elsewhere, and squid no
  longer spawn in lava. Squid swim all through the water rather than sinking to the bottom, and make no footsteps. Falling sand and gravel no longer delete blocks of their own kind. With a block low
  overhead, a step no longer sinks you into the floor and shoves you back out. Rejoining a world right against a wall,
  a rounding slip no longer lets you walk into it and suffocate until pushed out.
- **Redstone.** Repeaters work the doors, trapdoors, note blocks, dispensers, TNT and rail junctions they power.
  Repeater clocks keep running when the world is reloaded (waiting block updates are saved with their chunk). A
  redstone torch doesn't burn out for good when the world's time goes back. A lone door top (in an edited world) no
  longer deletes the block under it.
- **Lighting.** Light as beta's own rule says it should be: no dark pockets under overhangs or at chunk edges, and
  lava from world generation lights its surroundings (a switch in `config/oldfaithful-lighting.properties`).
  Existing worlds heal as their chunks load.
- **On screen.** The sky keeps its colour with a chest or inventory open. The item on the cursor is drawn in front
  of the player figure and labels. Fence bars' undersides are lit. The hotbar after block cracks, riding poses and
  the death screen's score are drawn right. Option sliders reach every percentage. Footsteps aren't saved up on the
  way down stairs and played in a burst. No dots of sky flicker along block edges, where the world's chunks meet or
  at the edges of grass, stone and other blocks' textures. In a big window, the Create New World and Rename World
  titles stay at the top. Mobs at the edges of the screen stay drawn while any part of them is in view, where a head
  or tentacles blinked out.
- **Multiplayer.** A break the server refuses is put back at once, not 4 seconds later. Mobs don't jitter. Players
  who teleport or come through a portal are seen by those already there. Chunks arrive nearest first after a fast
  move. The pause after breaking a block clears when you let go. Signs edited beside other changes show their text.
  Nothing near where you died stays frozen after you respawn. Other players no longer stay turned a few degrees
  off where they face. While you ride, they see your head turn. Spawners show the mob they spawn, not always
  a pig. Explosions push a player who was just hurt. A /tp in mid-fall doesn't land the fall. The server and your screen
  agree on when you're drowning.

And a few options: a field-of-view slider (Options, beside Difficulty), the GUI at Normal size on a first start,
Quit Game on the title screen, and a window that opens maximised the first time and where you left it after
(`config/oldfaithful-options.properties`), and the settings screen itself: every switch of the installed mods,
Pure Faithful's display settings included, in menus of fixes and changes, in beta's own style.

## What stays beta

What players built their game around, or that's a matter of taste rather than a mistake, stays as it was: farm
animals despawn, boats break when they bump into things, stairs drop planks or cobblestone, lava lingers without
its source, player-placed leaves decay, fences keep the hitbox behind the "smart farm" trick, eating at full health wastes the
food, and closing the window skips the last save (players use it to undo a loss). Worlds stay in beta's own format
and open in the original game.

**[Still Faithful](https://github.com/studio-quire/still-faithful)** is Old Faithful plus the polish later versions
brought while the game stayed beta: animals you've looked after stay, boats break only in a real crash, stairs drop
themselves, cut-off lava thins out, placed leaves stay, modern skins show whole, and dusk falls smoothly, each on a
switch.

## Download and play

You need a Minecraft account that owns the game, and [Prism Launcher](https://prismlauncher.org/).

1. Download **OldFaithful-&lt;version&gt;.zip** from [the latest release](https://github.com/studio-quire/old-faithful/releases/latest).
2. In Prism Launcher: **Add Instance > Import**, choose the zip (or paste the download link), **OK**.
3. Launch it. Prism downloads beta 1.7.3 and its sounds from Mojang, and, if you let it, a Java 21 runtime.

Everything else is inside the zip, and nothing comes from us. Besides Mojang (your login, skins and sounds) and the
servers you join, the game reaches one other place: on its first start it fetches beta's own bow and door sounds
(about 28 KB) from the archive of beta's files kept by Modification Station, a community of beta modders. To keep it
from doing so, before you first launch it set `resources.download=false` in
`.minecraft/config/purefaithful-hardening.properties`, creating the file if it isn't there. The title screen shows "Old
Faithful" and its version under beta's own version line.

## For existing instances

Already playing b1.7.3 on Fabric (Babric) in Prism? Old Faithful's mods work on their own: drop the jars from the
release into the instance's `.minecraft/mods`. They work without Pure Faithful.

- **oldfaithful-fixes** (client and server) and **oldfaithful-lighting** (client and server), each on its own.
- **oldfaithful-settings** (client), the settings screen: with it, the other mods' switches are in the game.
- **oldfaithful-options** (client), with oldfaithful-settings beside it. Its window settings need Pure Faithful's
  display mod; the rest works anywhere.

The whole of Old Faithful on its own is these four plus Pure Faithful's four mods and its LWJGL, from the Pure
Faithful release the Old Faithful release names; [Pure Faithful's README](https://github.com/studio-quire/pure-faithful#for-existing-instances)
says how to install them.

## Servers

`OldFaithful-server-<version>.zip` is beta 1.7.3's own server with Pure Faithful's hardening, saves and speed mods and
Old Faithful's fixes and lighting: bring Mojang's server jar (the scripts check it's genuine) and run `start.bat` or
`start.sh`. Players can join with vanilla beta 1.7.3, Pure Faithful or Old Faithful; its README says which fixes
need Old Faithful on the player's side too.

## Problems

Please [open an issue](https://github.com/studio-quire/old-faithful/issues) and attach
`.minecraft/logs/purefaithful/latest.log` from the instance folder. It never contains your access token. If a fix
clashes with another mod, its switch turns it off completely.

## Building

This repository holds all of Old Faithful's source, Pure Faithful's included. `./gw releaseFixed` builds everything
an Old Faithful release contains into `local/release/old-faithful-<version>/` (and `./gw release` Pure Faithful's).
[docs/building.md](docs/building.md) and [docs/release.md](docs/release.md) say how; the fixes are described in
[docs/fixes.md](docs/fixes.md), [docs/lighting.md](docs/lighting.md) and [docs/options.md](docs/options.md), with
every bug's cause in [docs/bugs.md](docs/bugs.md). Inside, the project is called by its working name, `oldfaithful`.

## Credits and licence

Made by Claude Opus 5.5, an AI model by Anthropic, at Studio Quire. Idea, direction and play-testing: Shoalstone.

Old Faithful's code is under the [MIT licence](LICENSE). It builds on Fabric Loader, Mixin, ASM, Babric's mappings,
LWJGL, SDL and OpenAL Soft, each under its own licence; the downloads carry their notices and licence texts, and the
source code their licences ask for.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
