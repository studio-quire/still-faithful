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
 * Faithful Core's hooks and attachments, with every hook listened to by the test-only {@code hook-check} mod. A
 * server without the core writes a world, for the byte check; the real server with the core runs each hook's scene against raw
 * clients and the hidden client, is restarted, and runs again under an order salt; the hidden client runs them in single player
 * and reopens its world; then all of it again beside Still Faithful's mods.
 */
public abstract class LibraryHooksTestTask extends ClientStartupTask {
	/** What {@code Core.orderKey} sorts the two test mods the other way round with: checked by the run, which says which order it saw. */
	private static final String FLIPPING_SALT = "a";

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** The core, settings and the test mods, for the server. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getServerMods();

	/** Still Faithful's whole set with the core and the test mods, for the client. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getStillMods();

	/** The same for a server. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getStillServerMods();

	/** What the start-up test's title check expects with those mods: {@code <name>:<mod id>}. */
	@org.gradle.api.tasks.Input
	public abstract org.gradle.api.provider.Property<String> getStillEdition();

	private final List<String> lines = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private GameProcess server;

	private void expect(String what, boolean ok) {
		getLogger().lifecycle("{}: {}", ok ? "ok  " : "FAIL", what);
		if (!ok) failures.add(what);
	}

	private void scene(String command) throws Exception {
		int from = server.lineCount();
		server.send(command);
		String name = command.split(" ")[1];
		if (server.awaitFrom(from, l -> l.contains("[faithful-test] DONE " + name), 60) == null) failures.add("the scene " + command + " didn't finish");
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		Path dir = run.resolve("server");
		copyTree(getServerPackage().get().getAsFile().toPath(), dir);
		Files.copy(getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
		int port = freePort();
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "level-seed=31337", "online-mode=false", "server-ip=127.0.0.1",
				"server-port=" + port, "view-distance=3", "max-players=20", "spawn-monsters=false", "spawn-animals=false", "pvp=true", "allow-flight=true", ""));
		Files.writeString(dir.resolve("ops.txt"), "OldFaithfulTest\n");

		start(dir, "without the core", "");
		server.send("save-all");
		server.await(l -> l.contains("Save complete"), 60);
		stop("without the core");

		for (File mod : getServerMods().getFiles()) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()));
		// Without the core the world stays free of animals, for the byte check; from here the scenes need cows to last.
		Path properties = dir.resolve("server.properties");
		Files.writeString(properties, Files.readString(properties).replace("spawn-animals=false", "spawn-animals=true"));
		start(dir, "with the core", "");
		try {
			scene("hooktest rewrite");
			scene("hooktest start");
			scene("hooktest chunks");
			scenes(port);
			clientOnServer(run, port);
		} finally {
			stop("with the core");
		}

		start(dir, "restarted", "");
		try (RawClient p = new RawClient(port, "HookTester")) {
			expect("the player logs back in after the restart", p.login(60) == null);
			p.stand(20, 50);
			scene("hooktest relogged HookTester restart");
			scene("hooktest restarted");
		} finally {
			stop("restarted");
		}

		start(dir, "salted", "-Dfaithful.test.orderSalt=" + FLIPPING_SALT);
		try (RawClient p = new RawClient(port, "SaltTester")) {
			p.login(60);
			p.stand(20, 50);
			scene("hooktest order SaltTester");
		} finally {
			stop("salted");
		}
		expect("the salt turned the order round, so the rules were seen to hold both ways", lines.stream().anyMatch(l -> l.contains("[faithful-test] ORDER salted")));

		singlePlayer(run.resolve("single-player"), getMods().getFiles().stream().toList(), List.of());
		stillFaithful(run, port);
		report("Library hooks test", lines, failures);
	}

	private void scenes(int port) throws Exception {
		try (RawClient p = new RawClient(port, "HookTester")) {
			expect("the scenes' player logs in", p.login(60) == null);
			p.stand(20, 50);
			String who = " HookTester";
			for (String scene : List.of("players", "blocks", "drops", "growth", "entities", "fire")) scene("hooktest " + scene + who);
			scene("hooktest lightning");
			scene("hooktest order" + who);
			scene("hooktest throws" + who);
			scene("hooktest attach" + who);
			scene("hooktest kill" + who + " false");
			p.respawn();
			p.stand(20, 50);
			scene("hooktest respawned" + who + " false");
			scene("hooktest kill" + who + " true");
			p.respawn();
			p.stand(20, 50);
			scene("hooktest respawned" + who + " true");
			scene("hooktest dimension" + who);
			p.stand(20, 50);
			scene("hooktest chunkdata");

			p.chat("/hookhi");
			p.chat("/hookop");
			p.chat("/hookthrow");
			p.stand(20, 50);
			expect("a command for everyone answers a player who isn't an op", p.sawChat("hello HookTester"));
			expect("an ops' command doesn't", !p.sawChat("hello op"));
			expect("a command that throws says so, and the game goes on", p.sawChat("That command failed"));
			server.send("hookhi");
			scene("hooktest endthrows");
			expect("the player is still in", !p.isClosed());
		}
		Thread.sleep(5500); // the server's per-address login throttle
		try (RawClient p = new RawClient(port, "HookTester")) {
			expect("the player logs back in", p.login(60) == null);
			p.stand(20, 50);
			scene("hooktest relogged HookTester relog");
		}
		Thread.sleep(5500);
		try (RawClient plain = new RawClient(port, "PlainPlayer")) {
			expect("a vanilla client logs in", plain.login(60) == null);
			plain.stand(20, 50);
			scene("hooktest plain PlainPlayer");
			expect("a vanilla client is sent no 250 packet", plain.await(r -> r.id() == 250, 1) == null);
			Thread.sleep(5500);
			try (RawClient core = new RawClient(port, "CoreHook").core(true)) {
				expect("a client with the core logs in", core.login(60) == null);
				core.stand(20, 50);
				expect("the attachments channels are in its hello", core.helloChannels() != null && core.helloChannels().containsValue("faithful-core:attachments"));
				scene("hooktest attach CoreHook");
				expect("it's sent its own values", core.await(r -> r.id() == 250 && r.a() != 0, 5) != null);
				expect("a vanilla client still isn't sent anything", plain.await(r -> r.id() == 250, 1) == null);
			}
		}
		Thread.sleep(5500);
	}

	private void clientOnServer(Path run, int port) throws Exception {
		Path game = run.resolve("client-on-server");
		Files.createDirectories(game.resolve("mods"));
		for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		lines.add("--- client on the server");
		List<String> out = launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true"), failures);
		lines.addAll(out);
	}

	private void singlePlayer(Path game, List<File> mods, List<String> jvmArgs) throws Exception {
		Files.createDirectories(game.resolve("mods"));
		for (File mod : mods) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		lines.add("--- single player, " + game.getFileName());
		List<String> first = launch(game, false, jvmArgs, failures);
		lines.addAll(first);
		expect("single player: closing the save is its stop, in order", first.stream().anyMatch(l -> l.contains("[faithful-test] PASS every player left before the stop"))
				&& first.stream().anyMatch(l -> l.contains("[faithful-test] PASS a world unloads after the stop")));
		for (String l : first) {
			if (l.contains("[faithful-test] FAIL")) failures.add(l);
		}
		lines.add("--- single player reopened, " + game.getFileName());
		List<String> reopened = new ArrayList<>(jvmArgs);
		reopened.add("-Dhookcheck.reopened=true");
		lines.addAll(launch(game, false, reopened, failures));
	}

	/** Still Faithful's mods hook many of the same methods. */
	private void stillFaithful(Path run, int port) throws Exception {
		Path dir = run.resolve("server");
		for (File mod : getStillServerMods().getFiles()) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()), StandardCopyOption.REPLACE_EXISTING);
		start(dir, "beside Still Faithful", "");
		try (RawClient p = new RawClient(port, "StillTester")) {
			expect("a player logs in beside Still Faithful's mods", p.login(60) == null);
			p.stand(20, 50);
			for (String scene : List.of("blocks", "drops", "growth", "entities", "fire", "order")) scene("hooktest " + scene + " StillTester");
		} finally {
			stop("beside Still Faithful");
		}
		singlePlayer(run.resolve("still-single-player"), getStillMods().getFiles().stream().toList(), List.of("-Doldfaithful.selftest.edition=" + getStillEdition().get()));
	}

	private void start(Path dir, String label, String extraJavaOpts) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		server = new GameProcess(command, dir, dir.resolve("harness-output-" + label.replace(' ', '-') + ".log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", ("-Xmx768m " + extraJavaOpts).trim()));
		if (server.await(l -> l.contains("Done ("), 300) == null) throw new IOException("the server never finished starting");
	}

	private void stop(String label) throws Exception {
		server.send("stop");
		Integer exit = server.waitFor(120);
		if (exit == null) failures.add(label + ": the server didn't stop within 2 minutes");
		List<String> out = withoutPurposefulThrows(server.lines());
		server.close();
		for (String line : out) {
			if (line.contains("[faithful-test] FAIL")) failures.add(line);
			if (line.contains("[faithful-test] PASS")) getLogger().lifecycle(line);
		}
		for (String p : problems(out)) failures.add(label + ", server: " + p);
		lines.add("--- server: " + label);
		lines.addAll(out);
	}

	/** The isolation scene throws on purpose: its stack traces are what's expected there, and nowhere else. */
	private static List<String> withoutPurposefulThrows(List<String> all) {
		List<String> out = new ArrayList<>();
		boolean inside = false;
		for (String line : all) {
			if (line.contains("[faithful-test] THROWS BEGIN")) inside = true;
			if (!inside || line.contains("[faithful-test] PASS") || line.contains("[faithful-test] FAIL")) out.add(line);
			if (line.contains("[faithful-test] THROWS END")) inside = false;
		}
		return out;
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path p : walk.toList()) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
