package oldfaithful.gradle;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Turns a mod compiled against named classes into the jar players install: every reference to the game, including
 * Mixin targets, rewritten to intermediary names, which Fabric Loader maps onto the player's own game jar. The jar is
 * then rewritten with its entries sorted and one fixed timestamp, so the same source gives the same bytes (the
 * remapper writes in thread order with the current time).
 *
 * <p>The jar is made beside its place and moved in whole, and one that would come out the same is left as it is:
 * another build in this checkout may be reading it (a test copying its mods), and Gradle runs this again whenever
 * buildSrc changes at all, when the same source gives the same bytes.
 */
public abstract class RemapJarTask extends DefaultTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getInput();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getNamedMappings();

	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@TaskAction
	public void remap() throws IOException {
		List<Path> classpath = getClasspath().getFiles().stream().map(f -> f.toPath()).toList();
		Path output = getOutput().get().getAsFile().toPath();
		Path made = output.resolveSibling(output.getFileName() + ".making-" + ProcessHandle.current().pid());
		try {
			Remapping.remap(getInput().get().getAsFile().toPath(), made,
					Remapping.provider(Remapping.readMappingsJar(getNamedMappings().get().getAsFile().toPath()),
							"named", "intermediary"),
					classpath, false, true);
			normalize(made);
			if (Files.isRegularFile(output) && Files.mismatch(made, output) == -1) return;
			replace(made, output);
		} finally {
			Files.deleteIfExists(made);
		}
	}

	/** Windows refuses to replace a file that is open, so a reader gets a few seconds to finish. */
	private static void replace(Path made, Path output) throws IOException {
		for (int attempt = 0;; attempt++) {
			try {
				Files.move(made, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				return;
			} catch (AtomicMoveNotSupportedException e) {
				throw e;
			} catch (IOException e) {
				if (attempt == 50) throw new IOException(output + " can't be replaced: is a game or another build still reading it?", e);
				try {
					Thread.sleep(200);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	private static final LocalDateTime EPOCH = LocalDateTime.of(1980, 2, 1, 0, 0);

	static void normalize(Path jar) throws IOException {
		Map<String, byte[]> entries = new TreeMap<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			for (ZipEntry e : java.util.Collections.list(zip.entries())) {
				if (!e.isDirectory()) entries.put(e.getName(), zip.getInputStream(e).readAllBytes());
			}
		}
		// The manifest first, as jar readers expect.
		byte[] manifest = entries.remove("META-INF/MANIFEST.MF");
		Path tmp = jar.resolveSibling(jar.getFileName() + ".tmp");
		try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
			if (manifest != null) put(zip, "META-INF/MANIFEST.MF", manifest);
			for (Map.Entry<String, byte[]> e : entries.entrySet()) put(zip, e.getKey(), e.getValue());
		}
		Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
		ZipEntry e = new ZipEntry(name);
		e.setTimeLocal(EPOCH);
		zip.putNextEntry(e);
		zip.write(data);
		zip.closeEntry();
	}
}
