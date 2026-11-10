package oldfaithful.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Builds the jars mods compile against: client and server each remapped from their own obfuscated names
 * ({@code clientOfficial}, {@code serverOfficial}) to Babric's intermediary, merged, then remapped to the
 * human-readable names. Everything it writes stays under local/ and never leaves the machine.
 *
 * <p>Several checkouts share local/cache, and their builds run at the same time, so the jars are never written where
 * they are read. They live in a folder named for everything that decides their content ({@link #key}), each is made
 * elsewhere and renamed into place whole, and one in place is never written again: a build that finds them there uses
 * them, whichever checkout made them, and a different recipe or input gets a folder of its own. (Written in place, a
 * remap in another build that read the named jar during the rewrite saw an empty zip and silently left every field
 * and method of the game unmapped in a mod's jar; and the rewrite itself failed while another build had the jar open.)
 */
public abstract class PrepareMinecraftTask extends DefaultTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getClientJar();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getIntermediaryMappings();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getNamedMappings();

	@OutputFile
	public abstract RegularFileProperty getMergedIntermediaryJar();

	@OutputFile
	public abstract RegularFileProperty getNamedJar();

	@TaskAction
	public void prepare() throws IOException {
		Path merged = path(getMergedIntermediaryJar());
		Path named = path(getNamedJar());
		// Gradle runs this again whenever buildSrc changes at all; the folder's name says whether the jars would.
		if (Files.exists(merged) && Files.exists(named)) return;

		Path folder = named.getParent();
		Files.createDirectories(folder);
		sweep(folder);
		// Beside where the jars go, so placing them is a rename within one volume; its own, as two builds may be here.
		Path making = Files.createTempDirectory(folder, MAKING);
		try {
			MemoryMappingTree intermediary = Remapping.readMappingsJar(path(getIntermediaryMappings()));

			Path client = making.resolve("client-intermediary.jar");
			Path server = making.resolve("server-intermediary.jar");
			Remapping.remap(path(getClientJar()), client,
					Remapping.provider(intermediary, "clientOfficial", "intermediary"), List.of(), true, false);
			Remapping.remap(path(getServerJar()), server,
					Remapping.provider(intermediary, "serverOfficial", "intermediary"), List.of(), true, false);

			Path madeMerged = making.resolve(merged.getFileName());
			JarMerger.merge(client, server, madeMerged);

			MemoryMappingTree names = Remapping.readMappingsJar(path(getNamedMappings()));
			Path madeNamed = making.resolve(named.getFileName());
			Remapping.remap(madeMerged, madeNamed, Remapping.provider(names, "intermediary", "named"), List.of(), true,
					false);

			place(madeMerged, merged);
			place(madeNamed, named);
		} finally {
			delete(making);
		}
	}

	/**
	 * The name of the folder the derived jars live in: a digest of the inputs' own bytes (the game jars by their pinned
	 * hashes, which the downloads are held to), the libraries that do the work, and the classes of this recipe. Anything
	 * else in buildSrc can change without the jars being made again.
	 */
	static String key(String clientSha1, String serverSha1, Path intermediaryMappings, Path namedMappings,
			List<String> libraries) throws IOException {
		MessageDigest digest = sha256();
		String inputs = "client " + clientSha1 + "\nserver " + serverSha1
				+ "\nintermediary " + Hashes.hash(intermediaryMappings, "SHA-256")
				+ "\nnamed " + Hashes.hash(namedMappings, "SHA-256")
				+ "\nlibraries " + String.join(" ", libraries) + "\n";
		digest.update(inputs.getBytes(StandardCharsets.UTF_8));
		for (Class<?> top : List.of(PrepareMinecraftTask.class, Remapping.class, JarMerger.class)) {
			// Nested and anonymous classes are separate class files; a nest holds them all.
			Class<?>[] nest = top.getNestMembers();
			Arrays.sort(nest, Comparator.comparing(Class::getName));
			for (Class<?> c : nest) {
				try (InputStream in = c.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class")) {
					if (in == null) throw new IOException("No class file for " + c.getName());
					digest.update(in.readAllBytes());
				}
			}
		}
		return HexFormat.of().formatHex(digest.digest(), 0, 8);
	}

	private static final String MAKING = "making-";

	/**
	 * Renames a finished jar into place unless one is already there: another build finished first, and its jar is the
	 * same thing under the same folder name. A plain move never replaces, so a jar some build is reading is never
	 * touched, which on Windows would fail as well.
	 */
	private static void place(Path made, Path target) throws IOException {
		for (int attempt = 0;; attempt++) {
			try {
				Files.move(made, target);
				return;
			} catch (FileAlreadyExistsException e) {
				return;
			} catch (IOException e) {
				// A virus scanner may hold the new file for a moment.
				if (Files.exists(target)) return;
				if (attempt == 50) throw e;
				try {
					Thread.sleep(100);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	/** Clears what a build that was killed half way left behind. */
	private static void sweep(Path folder) throws IOException {
		Instant old = Instant.now().minus(Duration.ofHours(1));
		List<Path> left;
		try (Stream<Path> entries = Files.list(folder)) {
			left = entries.filter(p -> p.getFileName().toString().startsWith(MAKING)).toList();
		}
		for (Path p : left) {
			try {
				if (Files.getLastModifiedTime(p).toInstant().isBefore(old)) delete(p);
			} catch (IOException gone) {
				// Another build swept it first.
			}
		}
	}

	private static void delete(Path dir) {
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		} catch (IOException e) {
			// Left for a later sweep.
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Path path(RegularFileProperty file) {
		return file.get().getAsFile().toPath();
	}
}
