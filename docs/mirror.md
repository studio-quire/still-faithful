# The artifact mirror

Claude Opus 5.5, 2026-11-01. Every jar the build and the game resolve, in one Maven repository we hold, so nothing we
build or ship can rot, move or be swapped upstream. Local only for now; nothing is published.

## Now: `local/mirror/maven/`

- **What's in it** (about 55 MB, SDL's source archive a third of it): everything Gradle resolves for the build, the
  harness and the instance, copied byte for byte from upstream (Fabric Loader, Mixin, MixinExtras, ASM, tiny-remapper, mapping-io,
  Vineflower, JUnit, Babric's intermediary, Biny), and our own builds (`org.studioquire.oldfaithful.natives`: LWJGL 2
  and OpenAL Soft, and for phase 3 SDL3, LWJGL 3's natives and OpenAL Soft as `OpenAL.dll`, `docs/natives.md`) with
  their source archives. Each file has `.sha1` and `.sha256` beside it, as Maven
  repositories serve them. Never Minecraft, and never anything derived from it.
- **The pins** are Gradle's `gradle/verification-metadata.xml` (SHA-256 of every artifact and metadata file, our own
  included). `tools/mirror.py` reads the same file, so there is one list.
- **Resolution**: `settings.gradle` and `buildSrc/build.gradle` resolve only from the mirror, Gradle module metadata
  first (the mirror holds exactly the pinned files; a `.pom` that only points Gradle to a `.module` isn't one of them).
  `-Poldfaithful.upstream` switches to the original repositories (HTTPS, each limited to its groups as before), with our own
  group still only from the mirror, so a public repository can never answer for `org.studioquire`.
- **The instance** carries its libraries inside the zip, from the same configurations (`build.gradle`
  `prismDevInstance`), so it resolves from the mirror too.
- **Phase 3's natives** (Claude Opus 5.5, 2026-11-01): `sdl3:3.4.18+oldfaithful.1:natives-windows` (`SDL3.dll`),
  `lwjgl3:3.4.3+oldfaithful.1:natives-windows` (`lwjgl.dll`, `lwjgl_opengl.dll`), and
  `openal-soft:1.25.2+oldfaithful.1:natives-windows-lwjgl3` (`OpenAL.dll`, the same bytes as `OpenAL-amd64.dll`, sharing
  that version's pom and sources). The DLLs sit at the jar root, since Prism extracts natives jars flat onto
  `java.library.path`; `META-INF/` holds `build-info.json` and the licences. Each has a `-sources.zip`: SDL's is
  `git archive` of the pinned commit; LWJGL 3's holds the C sources and build configuration the two DLLs come from
  (the whole tree is 128 MB of other bindings), libffi's whole tree, and our `fficonfig.h`. Linux x64 beside them:
  `natives-linux` jars of the same three (`liblwjgl.so` and `liblwjgl_opengl.so`, `libSDL3.so`, `libopenal.so`),
  sharing the poms and sources archives, with a few more notices (`docs/natives.md` > Linux x64).

Commands:

    python tools/mirror.py sync      # fetch what's pinned and missing, from upstream; refuses a hash mismatch
    python tools/mirror.py natives   # package our builds (local/natives) as artifacts, deterministically
    python tools/mirror.py verify    # re-hash every pinned file

A fresh clone: `sync`, then `python natives/build.py all` and `natives` (or copy the natives from a machine that has
them; the hashes will tell), then build as usual.

Adding or updating a dependency: change `gradle.properties`, run
`./gw -Poldfaithful.upstream --write-verification-metadata sha256 build :harness:nativesTest prismDevInstance decompileMinecraft`,
review the new hashes (against a second source where one exists), then `python tools/mirror.py sync`. Rebuilding our
natives changes their hashes only if the sources, flags or toolchain changed; then delete our group's entries from
the verification file and write them again the same way.

## Later: public, under studio-quire

For when the edition is public (MIT for our code; publishing is still a separate step):

- **Where**: a static Maven repository, a `studio-quire/maven` repository served by GitHub Pages at a subdomain of
  studioquire.org (`maven.studioquire.org`, DNS on Cloudflare as the site is). Maven layout needs paths, which GitHub
  Releases can't give (flat assets per tag), so Releases instead carry the finished pack and the standalone mods, each
  with its SHA-256 in the release notes. Size is no concern (Pages allows 1 GB; we're at 55 MB).
- **What**: only what we ship or what someone needs to build us: the loader, Mixin, ASM, the intermediary and names,
  our LWJGL and OpenAL builds with source archives, our mods. Build-only tools (JUnit, Vineflower) can stay
  upstream-only if size or noise matters.
- **How it's published**: the same files, plus `maven-metadata.xml` per artifact for tools that want it; a CI job
  re-hashes everything against the verification file before a push, and the repository's history is the record of
  what was served when.
- **The pack** (phase 7) keeps carrying its libraries inside the zip with Prism's `local` hint, as the dev instance
  does: one download, nothing fetched but Minecraft, no dependency on our host staying up. Prism doesn't hash-check
  local libraries, so the zip's own SHA-256 (published with the release) is the check. Standalone users who'd rather
  fetch: an importable component file with `url` + `sha1` + `size` for each library, pointing at our Maven, which
  Prism does verify on download (`docs/runtime-inventory.md` > How Prism handles libraries).

## Licence obligations when it's public

None of this applies while the mirror is local. Before publishing the mirror or the pack:
- **OpenAL Soft** (LGPL-2.1-or-later): the licence text ships in our natives jar, the matching source archive sits
  beside it, and the DLL stays a separate, replaceable file. Also in the jar: its BSD-3-Clause, pffft and fmt notices.
- **LWJGL** (BSD-3-Clause): our jars carry the licence in `META-INF/licenses/`; the LWJGL 3 natives jar also carries
  libffi's (MIT), which is linked into `lwjgl.dll`.
- **SDL** (zlib, asks nothing of binaries): the jar carries its licence anyway, plus those of the code compiled into
  `SDL3.dll` that asks for its notice with binaries: HIDAPI (used under its BSD or original licence, not GPL-3.0) and
  yuv2rgb (BSD-3-Clause).
- **Fabric Loader** shades tiny-remapper (**LGPL-3.0**), mapping-io and class-tweaker (Apache-2.0) and SAT4J
  (EPL-1.0/LGPL-2.1), but its jar carries only its own licence and SAT4J's page. The mirror and the pack should add
  those licence texts and tiny-remapper's sources (`tiny-remapper-0.15.0-sources.jar` from Fabric's maven).
- **ASM** (BSD-3-Clause, no licence file in the jar), **Mixin** and **MixinExtras** (MIT; Mixin's shaded Guava and
  Gson are Apache-2.0): a `licenses/` folder in the pack with each text covers them.
- The mappings are CC0 and need nothing.
