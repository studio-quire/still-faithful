Old Faithful @VERSION@
@TAGLINE@

This folder is a Prism Launcher instance. Prism downloads Minecraft beta 1.7.3 itself, from Mojang, the first time
you launch it; everything else Old Faithful needs is already here. You need a Minecraft account that owns the game.

Old Faithful is beta 1.7.3 as it was meant to work. It is Pure Faithful @PURE_VERSION@ (beta exactly as it was,
made to run well and safely on today's computers) plus fixes for beta's own clear mistakes. What players built
their game around stays as beta had it: animals still despawn, boats still break, lava still lingers.

From Pure Faithful:
- Its own window again, resizable and sharp on high-resolution screens; F11 for borderless fullscreen; Ctrl+V
  pastes. Sound keeps working when you switch speakers or headphones. Vsync is on.
- Faster: the same picture with less work, and the far view fills in steadily.
- Safe online: logins and skins go to Mojang over HTTPS, sounds come over HTTPS and are checked, and your account's
  access token is never sent in the clear or written to logs. The game and the server are hardened against
  malicious servers and clients.
- The bow, doors and explosions sound as in beta, where launchers today give later recordings. Beta's bow and doors,
  which Mojang no longer offers, are fetched once from Modification Station's archive of beta's files. To keep the
  game from fetching them, set resources.download=false in .minecraft/config/purefaithful-hardening.properties
  before you first launch it, creating the file if it isn't there.
- Saves that survive crashes and power cuts. A part of a world that can't be read is kept in the world's corrupt/
  folder and reported, never silently replaced with new terrain.

Old Faithful's own:
- Fixes, each on its own switch: the grey sky with a chest open; tools whose last use drops nothing; wooden slabs
  that need a pickaxe; axes, pickaxes and shovels slow on blocks of their own material; the furnace eating the lava
  bucket; water washing away torches and rails without dropping them; repeaters that don't work doors, note blocks,
  dispensers, TNT or rail junctions; repeater clocks that stop when the world is reloaded; footsteps saved up on the
  way down stairs; pick block selecting nothing; dots of sky along block edges; mobs vanishing at the edges of the
  screen while part of them still shows; world screen titles slipping down in a big window; and more, in single
  player and multiplayer.
- Lighting as beta's own rule says it should be: no dark patches under overhangs or at chunk edges, and lava from
  world generation lights its surroundings. Existing worlds heal as you play.
- A field-of-view slider (Options, beside Difficulty), the GUI at Normal size on a first start, Quit Game on the
  title screen, and a window that opens maximised the first time and where you left it after.
- Options > Old Faithful...: every switch above, and the display settings, in the game, in menus of Fixes and
  Changes.
- The title screen says "Old Faithful @VERSION@" under beta's own version line.

Your worlds are in .minecraft/saves, in beta 1.7.3's own format: they open in the original game and in Pure
Faithful too.

Settings: in the game, Options > Old Faithful... has every switch, in beta's own style; a fix switched there takes
effect after a restart. The same settings are files in .minecraft/config, each explaining its settings:
- oldfaithful-fixes.properties: a switch for each fix, all on. Switch one off to have beta's behaviour back.
- oldfaithful-lighting.properties and oldfaithful-options.properties: the lighting and the options above.
- purefaithful-display.properties (vsync, raw mouse input, fullscreen, the window) and
  purefaithful-hardening.properties.

If something goes wrong, the game's log is .minecraft/logs/purefaithful/latest.log. Please include it in a report:
https://github.com/@REPO@/issues

More: @HOMEPAGE@
Source code: https://github.com/@REPO@

Made by Claude Opus 5.5, an AI model by Anthropic, at Studio Quire. Idea, direction and play-testing: Shoalstone.
Open source under the MIT licence. Software by others included here, and its licences: THIRD-PARTY-NOTICES.txt,
licenses/ and sources/.
Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
