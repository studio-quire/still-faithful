package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import oldfaithful.gradle.lwjgl.LwjglBaker;

/** Bakes the component jar: LWJGL 3 plus our LWJGL 2 API (see {@link LwjglBaker}), and writes its report. */
public abstract class BakeLwjglTask extends DefaultTask {
	/** LWJGL 3's jars (core, opengl, openal, sdl). */
	@Classpath
	public abstract ConfigurableFileCollection getLwjgl3();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getLegacyJar();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getLegacyUtilJar();

	/** LWJGL 2 classes taken as they are (internal names). */
	@Input
	public abstract ListProperty<String> getLegacyClasses();

	@Input
	public abstract ListProperty<String> getLegacyUtilPrefixes();

	@Input
	public abstract ListProperty<String> getLegacyUtilExcludes();

	/** Our compiled classes and resources. */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getClasses();

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getResources();

	/**
	 * Our natives jars (libraries at the root, Windows and Linux). LWJGL 3 compares each library it loads with a .sha1 its jar carries and
	 * complains on a mismatch; ours replace Central's, so the jar carries their hashes instead.
	 */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getNatives();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@OutputFile
	public abstract RegularFileProperty getReport();

	@TaskAction
	public void bake() throws IOException {
		List<Path> lwjgl3 = new ArrayList<>();
		for (File f : getLwjgl3().getFiles()) lwjgl3.add(f.toPath());
		Path legacy = getLegacyJar().get().getAsFile().toPath();
		Map<String, byte[]> extra = new LinkedHashMap<>();
		try (ZipFile z = new ZipFile(legacy.toFile())) {
			var licence = z.getEntry("META-INF/licenses/LICENSE");
			if (licence == null) throw new IllegalStateException("no licence in " + legacy);
			extra.put("META-INF/licenses/LWJGL2-LICENSE", z.getInputStream(licence).readAllBytes());
		}
		// Each library: where LWJGL 3 looks for its .sha1 (META-INF/<platform>/<arch>/<module>/<file>.sha1).
		Map<String, String> libraries = Map.of(
				"lwjgl.dll", "windows/x64/org/lwjgl/", "lwjgl_opengl.dll", "windows/x64/org/lwjgl/opengl/",
				"SDL3.dll", "windows/x64/org/lwjgl/sdl/", "OpenAL.dll", "windows/x64/org/lwjgl/openal/",
				"liblwjgl.so", "linux/x64/org/lwjgl/", "liblwjgl_opengl.so", "linux/x64/org/lwjgl/opengl/",
				"libSDL3.so", "linux/x64/org/lwjgl/sdl/", "libopenal.so", "linux/x64/org/lwjgl/openal/");
		for (File jar : getNatives().getFiles()) {
			try (ZipFile z = new ZipFile(jar)) {
				for (var e : java.util.Collections.list(z.entries())) {
					String path = libraries.get(e.getName());
					if (path == null) continue;
					byte[] library = z.getInputStream(e).readAllBytes();
					String sha1 = java.util.HexFormat.of().formatHex(sha1(library));
					extra.put("META-INF/" + path + e.getName() + ".sha1",(sha1 + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
				}
			}
		}
		Path resources = getResources().get().getAsFile().toPath();
		try (var walk = Files.walk(resources)) {
			for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				extra.put(resources.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
			}
		}
		LwjglBaker.Inputs in = new LwjglBaker.Inputs(lwjgl3, legacy, getLegacyUtilJar().get().getAsFile().toPath(),
				getLegacyClasses().get(), getLegacyUtilPrefixes().get(), getLegacyUtilExcludes().get(),
				getClasses().get().getAsFile().toPath(), extra);
		String report = LwjglBaker.bake(in, getOutput().get().getAsFile().toPath());
		Files.writeString(getReport().get().getAsFile().toPath(), report);
		for (String line : report.split("\n")) {
			if (!line.startsWith("  ")) getLogger().lifecycle(line.length() > 300 ? line.substring(0, 300) + "..." : line);
		}
	}

	private static byte[] sha1(byte[] data) {
		try {
			return java.security.MessageDigest.getInstance("SHA-1").digest(data);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
