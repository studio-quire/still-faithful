Source code included with @NAME@ @VERSION@
=================================================

Some software in this pack is under licences that ask for its source code to come with it. Here it is.

openal-soft-@OPENAL_VERSION@-sources.zip
  OpenAL Soft 1.25.2 (GNU Library General Public License 2 or later), exactly the source the pack's OpenAL.dll was
  built from: upstream's tag 1.25.2 (commit b2c48f7718ef3fcf67921a8b6534c4914e328970), unmodified.
  build-natives.py is the script that built it (its build_openal function); the exact settings, compiler and
  output hash are in META-INF/build-info.json inside libraries/openal-soft-@OPENAL_VERSION@-natives-windows-lwjgl3.jar.
  Upstream: https://github.com/kcat/openal-soft

tiny-remapper-@TINY_REMAPPER@-sources.jar, fabric-loader-@FABRIC_LOADER_VERSION@-sources.jar
  tiny-remapper (GNU LGPL 3 or later) is built into Fabric Loader (Apache 2.0). Both sources, so the loader can be
  rebuilt with a changed tiny-remapper. Upstream: https://github.com/FabricMC/tiny-remapper,
  https://github.com/FabricMC/fabric-loader (tag @FABRIC_LOADER_VERSION@).

org.ow2.sat4j.core-@SAT4J@-sources.jar, org.ow2.sat4j.pb-@SAT4J@-sources.jar
  SAT4J (Eclipse Public License 1.0, or GNU LGPL 2.1 or later), built into Fabric Loader. https://www.sat4j.org/

@NAME@'s own source: https://github.com/@REPO@ (tag v@VERSION@).
