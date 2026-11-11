Still Faithful server
=====================

Minecraft beta 1.7.3's own server, run through Fabric Loader with Pure Faithful's hardening, saves and speed mods,
Old Faithful's fixes and lighting mods, and Still Faithful's polish mod.

From Pure Faithful: it's hardened against malicious clients, and a crash or power cut can't damage the world: saves
are written whole or not at all, and a chunk that can't be read is kept in the world's corrupt/ folder and reported,
not silently regenerated. Online-mode logins are checked with Mojang over HTTPS. New terrain is generated faster, the
same terrain in less time.

From Old Faithful: beta's clear mistakes fixed, each on a switch in config/oldfaithful-fixes.properties, and lighting
as beta's own rule says it should be. Still Faithful's own: what later versions improved while the game stayed beta
(stairs that drop themselves, placed leaves that stay, glowstone by hand, lava that thins out, sturdier boats
that steer as in later versions, minecarts that meet on every track, animals that stay once a player has looked
after them, fences shaped as in later versions, chests that pair as in later versions, wooden tools, boats and other
wooden things that burn in a furnace, skeletons shooting from their eyes, squid that swim on under the surface
instead of bouncing against it),
each on a switch in config/stillfaithful-polish.properties.

Players can join with vanilla beta 1.7.3 or any of the three editions. The server's changes work for everyone;
walking close past fence posts and faster glowstone mining need Still Faithful on both sides (other clients walk into
fences as whole blocks, and mine glowstone at beta's speed and still get the drop, which the server accepts), and
players without it see chests paired the new way drawn oddly, though they open as they should. Boats steer for
players whose game has Old Faithful's Riding Look (Old Faithful, Still Faithful, More Faithful); others' boats paddle
as in beta.

What it can't change, because it's how beta 1.7.3's online mode works: a server a player joins could pass that
player's login on to another server, so online mode isn't proof against a hostile server operator. Players should
only join servers they trust with their name, as with vanilla.

Setting up
1. Install Java 21 or newer.
2. Put Minecraft beta 1.7.3's server jar in this folder, named minecraft_server.jar. Mojang no longer offers it
   for download, so use a copy you have; the start script checks it's the genuine one (SHA-1 @SERVER_SHA1@).
   It isn't included: it's Mojang's.
3. Run start.bat (Windows) or start.sh (macOS, Linux). The first start writes server.properties and the world, as
   vanilla does. JAVA picks the java executable and JAVA_OPTS its options (default -Xmx1G).

A vanilla beta 1.7.3 server's world and server.properties work as they are: copy them in. The world stays in beta's
format: vanilla ignores what the mods add (waiting block ticks, kept animals' mark, placed leaves' mark).

Still Faithful is open source under the MIT licence, made by Claude Opus 5.5 (Anthropic's AI model) at Studio Quire.
Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
