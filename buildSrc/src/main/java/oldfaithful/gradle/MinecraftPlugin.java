package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;

/**
 * Applied to the root project: obtains the game jars, builds the merged named jar mods compile against, and
 * offers a decompile for reading. Downloads and derived jars live in local/cache (ignored, survives `clean`), which
 * checkouts share: the derived jars in a folder named for what decides them ({@link PrepareMinecraftTask}).
 */
public class MinecraftPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		if (project != project.getRootProject()) throw new IllegalStateException("oldfaithful.minecraft is for the root project");

		String version = prop(project, "minecraft_version");
		File cache = new File(project.getRootDir(), "local/cache/minecraft");

		Configuration intermediary = detached(project, "intermediaryMappings", prop(project, "intermediary"));
		Configuration named = detached(project, "namedMappings", prop(project, "names"));
		Configuration vineflower = detached(project, "vineflower", "org.vineflower:vineflower:" + prop(project, "vineflower_version"));

		TaskProvider<DownloadTask> client = project.getTasks().register("downloadMinecraftClient", DownloadTask.class, t -> {
			t.setGroup("oldfaithful");
			t.getUrl().set(prop(project, "minecraft_client_url"));
			t.getSha1().set(prop(project, "minecraft_client_sha1"));
			t.getOutput().set(new File(cache, version + "-client.jar"));
		});
		TaskProvider<DownloadTask> server = project.getTasks().register("downloadMinecraftServer", DownloadTask.class, t -> {
			t.setGroup("oldfaithful");
			t.getUrl().set(prop(project, "minecraft_server_url"));
			t.getSha1().set(prop(project, "minecraft_server_sha1"));
			t.getOutput().set(new File(cache, version + "-server.jar"));
		});

		// Worked out when first asked for, once: it reads the mappings, which resolves them.
		String[] key = new String[1];
		Provider<Directory> derived = project.getLayout().dir(project.provider(() -> {
			if (key[0] == null) {
				try {
					key[0] = PrepareMinecraftTask.key(prop(project, "minecraft_client_sha1"), prop(project, "minecraft_server_sha1"),
							intermediary.getSingleFile().toPath(), named.getSingleFile().toPath(),
							List.of("tiny-remapper " + prop(project, "tiny_remapper_version"),
									"mapping-io " + prop(project, "mapping_io_version"), "asm " + prop(project, "asm_version")));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			return new File(cache, version + "-merged-" + key[0]);
		}));

		TaskProvider<PrepareMinecraftTask> prepare = project.getTasks().register("prepareMinecraft", PrepareMinecraftTask.class, t -> {
			t.setGroup("oldfaithful");
			t.getClientJar().set(client.flatMap(DownloadTask::getOutput));
			t.getServerJar().set(server.flatMap(DownloadTask::getOutput));
			t.getIntermediaryMappings().fileProvider(project.provider(intermediary::getSingleFile));
			t.getNamedMappings().fileProvider(project.provider(named::getSingleFile));
			t.getMergedIntermediaryJar().set(derived.map(d -> d.file(version + "-merged-intermediary.jar")));
			t.getNamedJar().set(derived.map(d -> d.file(version + "-merged-named.jar")));
		});

		// For reading only: decompiled source never leaves local/.
		project.getTasks().register("decompileMinecraft", JavaExec.class, t -> {
			t.setGroup("oldfaithful");
			t.setDescription("Decompiles the named game jar into local/decomp for reading.");
			Provider<RegularFile> input = prepare.flatMap(PrepareMinecraftTask::getNamedJar);
			File output = new File(project.getRootDir(), "local/decomp/" + version + "-named");
			t.getInputs().file(input);
			t.getOutputs().dir(output);
			t.classpath(vineflower);
			t.getMainClass().set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
			t.setMaxHeapSize("1g");
			t.getArgumentProviders().add(() -> java.util.List.of("-dgs=1", "-rsy=1", "-ind=    ", "-log=WARN", "-thr=2",
					input.get().getAsFile().getAbsolutePath(), output.getAbsolutePath()));
		});

		project.getExtensions().create("oldfaithfulMinecraft", MinecraftFiles.class);
		MinecraftFiles files = project.getExtensions().getByType(MinecraftFiles.class);
		files.getClientJar().set(client.flatMap(DownloadTask::getOutput));
		files.getServerJar().set(server.flatMap(DownloadTask::getOutput));
		files.getNamedJar().set(prepare.flatMap(PrepareMinecraftTask::getNamedJar));
		files.getMergedIntermediaryJar().set(prepare.flatMap(PrepareMinecraftTask::getMergedIntermediaryJar));
		files.getIntermediaryMappings().fileProvider(project.provider(intermediary::getSingleFile));
		files.getNamedMappings().fileProvider(project.provider(named::getSingleFile));
	}

	static String prop(Project project, String name) {
		Object value = project.getRootProject().findProperty(name);
		if (value == null) throw new IllegalStateException("Missing property " + name + " in gradle.properties");
		return value.toString();
	}

	private static Configuration detached(Project project, String name, String notation) {
		return project.getConfigurations().create(name, c -> {
			c.setTransitive(false);
			c.setCanBeConsumed(false);
			c.getDependencies().add(project.getDependencies().create(notation));
		});
	}
}
