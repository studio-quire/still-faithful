#!/bin/sh
# @NAME@ server: Minecraft beta 1.7.3's server with Fabric Loader and our @SERVER_MODS@ mods. See README.txt.
cd "$(dirname "$0")" || exit 1
JAR=minecraft_server.jar
SHA1=@SERVER_SHA1@
if [ ! -f "$JAR" ]; then
  echo "Put Minecraft beta 1.7.3's server jar in this folder as $JAR. See README.txt."
  exit 1
fi
if command -v sha1sum >/dev/null 2>&1; then GOT=$(sha1sum "$JAR" | cut -d' ' -f1)
else GOT=$(shasum -a 1 "$JAR" | cut -d' ' -f1); fi
if [ "$GOT" != "$SHA1" ]; then
  echo "$JAR isn't beta 1.7.3's server: its SHA-1 is $GOT, expected $SHA1."
  exit 1
fi
exec "${JAVA:-java}" ${JAVA_OPTS:--Xmx1G} -cp "libraries/*:$JAR" net.fabricmc.loader.impl.launch.knot.KnotServer nogui
