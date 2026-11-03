package oldfaithful.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

final class Remapping {
	private Remapping() {
	}

	/** Reads {@code mappings/mappings.tiny} from a mappings jar (the layout Fabric-family mapping jars use). */
	static MemoryMappingTree readMappingsJar(Path jar) throws IOException {
		MemoryMappingTree tree = new MemoryMappingTree();
		try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of());
				BufferedReader reader = Files.newBufferedReader(fs.getPath("mappings/mappings.tiny"))) {
			MappingReader.read(reader, tree);
		}
		return tree;
	}

	static IMappingProvider provider(MemoryMappingTree tree, String from, String to) {
		return TinyUtils.createMappingProvider(tree, from, to);
	}

	/**
	 * @param game     remapping the game itself: renames obfuscated locals and drops resources and signatures
	 * @param mixin    remap Mixin annotation targets in place (so mods need no refmap)
	 */
	static void remap(Path input, Path output, IMappingProvider mappings, Collection<Path> classpath, boolean game,
			boolean mixin) throws IOException {
		TinyRemapper.Builder builder = TinyRemapper.newRemapper()
				.withMappings(mappings)
				.threads(2);
		if (game) builder.renameInvalidLocals(true).rebuildSourceFilenames(true);
		if (mixin) builder.extension(new MixinExtension());
		TinyRemapper remapper = builder.build();

		Files.createDirectories(output.getParent());
		Files.deleteIfExists(output);
		try (OutputConsumerPath out = new OutputConsumerPath.Builder(output).assumeArchive(true).build()) {
			if (!game) out.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
			remapper.readClassPath(classpath.toArray(Path[]::new));
			remapper.readInputs(input);
			remapper.apply(out);
		} finally {
			remapper.finish();
		}
	}
}
