Still Faithful server
=====================

Minecraft beta 1.7.3's own server, run through Fabric Loader with Pure Faithful's hardening and saves mods, Old
Faithful's fixes and lighting mods, and Still Faithful's polish mod.

From Pure Faithful: it's hardened against malicious clients, and a crash or power cut can't damage the world: saves
are written whole or not at all, and a chunk that can't be read is kept in the world's corrupt/ folder and reported,
not silently regenerated. Online-mode logins are checked with Mojang over HTTPS.

From Old Faithful: beta's clear mistakes fixed, each on a switch in config/oldfaithful-fixes.properties, and lighting
as beta's own rule says it should be. Still Faithful's own: what later versions improved while the game stayed beta
(stairs that drop themselves, placed leaves that stay, glowstone by hand, lava that thins out, sturdier boats,
minecarts that meet on every track, animals players have dealt with that stay), each on a switch in config/stillfaithful-polish.properties.

Players can join with vanilla beta 1.7.3 or any of the three editions. The server's changes work for everyone;
faster glowstone mining needs Still Faithful on both sides (a vanilla client mines at beta's speed, which the server
accepts, and still gets the drop).

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
