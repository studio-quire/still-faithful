@NAME@ @VERSION@
@TAGLINE@

This folder is a Prism Launcher instance. Prism downloads Minecraft beta 1.7.3 itself, from Mojang, the first time
you launch it; everything else @NAME@ needs is already here. You need a Minecraft account that owns the game.

@NAME@ is beta 1.7.3 exactly as it was, with only the problems fixed that come from its age: it runs on today's
computers and Java, it's safe to use online, it's faster without changing anything you see, and it can't lose your
world. Beta's own bugs and quirks are left as they were. (Old Faithful is @NAME@ plus fixes for those:
https://studioquire.org/old-faithful/)

What's different from playing beta 1.7.3 as it came:
- Its own window again, resizable and sharp on high-resolution screens; F11 for borderless fullscreen; Ctrl+V
  pastes. Sound keeps working when you switch speakers or headphones.
- Vsync is on, so the picture doesn't tear on today's displays (the original drew as fast as it could).
- Faster: the same picture with less work, and the far view fills in steadily.
- Safe online: logins, skins and sounds go to Mojang over HTTPS and are checked, and your account's access token is
  never sent in the clear or written to logs. The game and the server are hardened against malicious servers and
  clients.
- Saves that survive crashes and power cuts. A part of a world that can't be read is kept in the world's corrupt/
  folder and reported, never silently replaced with new terrain.
- Screenshots (F2) work after you enlarge the window.
- The title screen says "@NAME@ @VERSION@" under beta's own version line.

Your worlds are in .minecraft/saves, in beta 1.7.3's own format: they open in the original game too.

Settings: .minecraft/config/purefaithful-display.properties (vsync, raw mouse input, exclusive fullscreen) and
.minecraft/config/purefaithful-hardening.properties. Each file explains its settings. Restart the game after a
change.

If something goes wrong, the game's log is .minecraft/logs/purefaithful/latest.log. Please include it in a report:
https://github.com/@REPO@/issues

More: @HOMEPAGE@
Source code: https://github.com/@REPO@

Made by Claude Opus 5.5, an AI model by Anthropic, at Studio Quire. Idea, direction and play-testing: Shoalstone.
Open source under the MIT licence. Software by others included here, and its licences: THIRD-PARTY-NOTICES.txt,
licenses/ and sources/.
Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
