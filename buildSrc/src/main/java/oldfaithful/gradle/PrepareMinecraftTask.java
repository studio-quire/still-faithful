package oldfaithful.gradle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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
		Path tmp = getTemporaryDir().toPath();
		MemoryMappingTree intermediary = Remapping.readMappingsJar(path(getIntermediaryMappings()));

		Path client = tmp.resolve("client-intermediary.jar");
		Path server = tmp.resolve("server-intermediary.jar");
		Remapping.remap(path(getClientJar()), client,
				Remapping.provider(intermediary, "clientOfficial", "intermediary"), List.of(), true, false);
		Remapping.remap(path(getServerJar()), server,
				Remapping.provider(intermediary, "serverOfficial", "intermediary"), List.of(), true, false);

		Path merged = path(getMergedIntermediaryJar());
		JarMerger.merge(client, server, merged);

		MemoryMappingTree named = Remapping.readMappingsJar(path(getNamedMappings()));
		Remapping.remap(merged, path(getNamedJar()), Remapping.provider(named, "intermediary", "named"), List.of(),
				true, false);
	}

	private static Path path(RegularFileProperty file) {
		return file.get().getAsFile().toPath();
	}
}
