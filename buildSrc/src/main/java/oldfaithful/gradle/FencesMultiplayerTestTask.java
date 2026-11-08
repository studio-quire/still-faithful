package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Still Faithful's fences (#30, docs/still-faithful.md) between a real client and a real server, in every mix: the
 * server package with and without the polish mod, each joined once by the hidden client with Still Faithful's mods and
 * once with them but the polish mod. The client walks into the fences of a scene the server builds
 * (the self-test's FenceWalk and FenceScene): thin only where both sides have the change, beta's whole blocks
 * otherwise, and never sent back by the server or kicked (flying is off, so beta's floating check runs). Any "moved
 * wrongly" in the server's log fails it too.
 */
public abstract class FencesMultiplayerTestTask extends ClientStartupTask {
	/** local/server-package/OldFaithful-server, as :serverPackage writes it. */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** The self-test mod, for the server (its scenes and test commands). */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getHarnessMod();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getPolishMod();

	/** The client's mods without the polish mod ({@code mods} has them with it). */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getClientModsWithoutPolish();

	/** "<name>:<mod id>" of Still Faithful, whose title line the client with the polish mod shows. */
	@org.gradle.api.tasks.Input
	public abstract org.gradle.api.provider.Property<String> getStillEdition();

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		List<String> failures = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		for (boolean thinServer : new boolean[] {true, false}) {
			for (boolean thinClient : new boolean[] {true, false}) {
				String label = (thinServer ? "still-server" : "plain-server") + "-" + (thinClient ? "still-client" : "plain-client");
				lines.add("=== " + label);
				getLogger().lifecycle("--- {}", label);
				lines.addAll(serverRun(run.resolve(label), thinServer, thinClient, failures));
			}
		}
		report("Fences multiplayer test", lines, failures);
	}

	private List<String> serverRun(Path dir, boolean thinServer, boolean thinClient, List<String> failures) throws Exception {
		Path server = dir.resolve("server");
		copyTree(getServerPackage().get().getAsFile().toPath(), server);
		Files.copy(getServerJar().get().getAsFile().toPath(), server.resolve("minecraft_server.jar"));
		Files.copy(getHarnessMod().get().getAsFile().toPath(), server.resolve("mods").resolve(getHarnessMod().get().getAsFile().getName()));
		if (thinServer) Files.copy(getPolishMod().get().getAsFile().toPath(), server.resolve("mods").resolve(getPolishMod().get().getAsFile().getName()));
		int port = freePort();
		Files.writeString(server.resolve("server.properties"), String.join("\n",
				"level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port,
				"view-distance=3", "max-players=20", "spawn-monsters=false", "spawn-animals=true", "pvp=true",
				// Off, so beta's kick for floating runs against a player standing on the edge of a post's top.
				"allow-flight=false", ""));
		Files.writeString(server.resolve("ops.txt"), "OldFaithfulTest\n");

		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", server.resolve("start.bat").toString()) : List.of("sh", server.resolve("start.sh").toString());
		Map<String, String> env = Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m -Doldfaithful.selftest.multiplayer=true");
		String label = dir.getFileName().toString();
		List<String> lines = new ArrayList<>();
		try (GameProcess process = new GameProcess(command, server, server.resolve("harness-output.log"), env)) {
			if (process.await(l -> l.contains("Done ("), 300) == null) {
				failures.add(label + ": the server never finished starting");
			} else {
				try {
					clientRun(dir, port, thinServer, thinClient, lines, failures);
				} catch (Exception e) {
					failures.add(label + ": stopped: " + e);
					getLogger().lifecycle("Stopped", e);
				}
				process.send("stop");
			}
			Integer exit = process.waitFor(120);
			if (exit == null) failures.add(label + ": the server didn't stop within 2 minutes");
			List<String> out = process.lines();
			checkSelfTest(out, failures);
			for (String p : problems(out)) failures.add(label + ", server: " + p);
			for (String l : out) {
				if (l.contains("moved wrongly") || l.contains("floating too long")) failures.add(label + ", server: " + l.trim());
			}
			lines.add("--- server");
			lines.addAll(out);
		}
		return lines;
	}

	private void clientRun(Path dir, int port, boolean thinServer, boolean thinClient, List<String> lines, List<String> failures) throws Exception {
		Path game = dir.resolve("client");
		Files.createDirectories(game.resolve("mods"));
		Iterable<File> mods = thinClient ? getMods().getFiles() : getClientModsWithoutPolish().getFiles();
		for (File mod : mods) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		List<String> args = new ArrayList<>(List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.fenceWalk=true",
				"-Doldfaithful.selftest.mp.thinClient=" + thinClient, "-Doldfaithful.selftest.mp.thinServer=" + thinServer));
		if (thinClient) args.add("-Doldfaithful.selftest.edition=" + getStillEdition().get());
		List<String> client = launch(game, false, args, failures);
		lines.add("--- client");
		lines.addAll(client);
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path p : walk.toList()) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}
}
