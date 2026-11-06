package oldfaithful.gradle;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * A release's library pack and server package, unpacked as they ship (gradle/library.gradle), each launched with the arguments the pack's own component
 * gives. With nothing ticked: the edition's whole start-up test, none of the library's code present, the login a vanilla client's, the Mods menu offering
 * every mod, and ticks written for the next launch. With no list at all, the same. With everything ticked: all of it loaded, in a new world. Then the server package
 * as it comes (a raw vanilla client hears nothing new, the pack's plain client plays), with the server-side mods but Water Bottles (still any client),
 * with all of them (vanilla refused, a plain pack refused, a pack with a mod on told what's missing and offered the server's set, a pack with everything
 * admitted), and with Water Bottles taken out again (the server refuses to start, then goes on when told). Prism itself isn't run here.
 */
public abstract class LibraryPackTestTask extends ClientStartupTask {
	/** The pack, unzipped: {@code .minecraft/}, {@code patches/} and the rest. */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getInstance();

	/** The server package, unzipped: its one top folder's contents. */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** Which edition, for its report. */
	@Input
	public abstract Property<String> getLabel();

	private static final String LIST = "mods-library/selection.txt";
	private static final Pattern JVM_ARGS = Pattern.compile("\"\\+jvmArgs\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
	private static final List<String> SERVER_MODS = List.of("world-rules", "better-dungeons");

	private final List<String> lines = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private GameProcess server;
	private List<String> packArgs;

	private void expect(String what, boolean ok) {
		getLogger().lifecycle("{}: {}", ok ? "ok  " : "FAIL", what);
		if (!ok) failures.add(what);
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		Path instance = getInstance().get().getAsFile().toPath();
		packArgs = jvmArgs(instance);
		expect("the pack's component launches with the list: " + packArgs, packArgs.contains("-Dfabric.addMods=@" + LIST));

		Path plain = game(run, "nothing-ticked", instance);
		expect("the pack ships an empty list", Files.size(plain.resolve(LIST)) == 0);
		lines.add("--- as shipped, nothing ticked: the edition's whole start-up test");
		List<String> played = launch(plain, false, args("-Doldfaithful.selftest.libraryPack=off", "-Doldfaithful.selftest.expectSize=1000x600", "-Dorg.prismlauncher.window.dimensions=1000x600", "-Doldfaithful.selftest.expectMaximized=false"), failures);
		lines.addAll(played);
		expect("nothing ticked: the loader lists no library mod", loadedNothing(played));
		expect("the ticks were written for the next launch", Files.readAllLines(plain.resolve(LIST)).size() == 6);
		expect("a plain client's login is a vanilla client's", loginSeed(run, "login-plain", instance, "") == 0L);

		Path absent = game(run, "no-list", instance);
		Files.delete(absent.resolve(LIST));
		lines.add("--- no list at all");
		played = launch(absent, false, args("-Doldfaithful.selftest.libraryPack=absent", "-Doldfaithful.selftest.settingsOnly=true", "-Doldfaithful.selftest.stallMs=280000"), failures);
		lines.addAll(played);
		expect("no list: the loader lists no library mod", loadedNothing(played));

		Path all = game(run, "everything-ticked", instance);
		Files.copy(plain.resolve(LIST), all.resolve(LIST), StandardCopyOption.REPLACE_EXISTING);
		lines.add("--- everything ticked, a new world");
		played = launch(all, false, args("-Doldfaithful.selftest.libraryPack=on", "-Doldfaithful.selftest.settingsOnly=true", "-Doldfaithful.selftest.stallMs=280000"), failures);
		lines.addAll(played);
		for (String id : List.of("faithful-core", "better-dungeons", "no-toasts", "plain-f3", "water-bottles", "world-rules")) {
			expect("everything ticked: the loader lists " + id, played.stream().anyMatch(l -> l.contains(id)));
		}
		expect("the new world's file has seen Water Bottles", Files.isRegularFile(all.resolve("saves/oldfaithful-startup/faithful-world.properties"))
				&& Files.readString(all.resolve("saves/oldfaithful-startup/faithful-world.properties")).contains("mod.water-bottles="));
		expect("a client with the core says so in its login", loginSeed(run, "login-all", instance, Files.readString(plain.resolve(LIST))) == RawClient.CORE_MAGIC);

		server(run, instance);
		report("Library pack test (" + getLabel().get() + ")", lines, failures);
	}

	private void server(Path run, Path instance) throws Exception {
		Path dir = run.resolve("server");
		copyTree(getServerPackage().get().getAsFile().toPath(), dir);
		Files.copy(getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
		int port = freePort();
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1",
				"server-port=" + port, "view-distance=3", "max-players=20", "spawn-monsters=false", "spawn-animals=true", "pvp=true", "allow-flight=true", ""));
		Path mods = dir.resolve("mods"), library = dir.resolve("mods-library");
		expect("the server package has the core, the settings and three mods waiting",
				nameOf(library, "faithful-core") != null && nameOf(library, "oldfaithful-settings") != null && nameOf(library, "water-bottles") != null && SERVER_MODS.stream().allMatch(m -> nameOf(library, m) != null));
		expect("and no client-only mod", nameOf(library, "plain-f3") == null && nameOf(library, "no-toasts") == null);

		start(dir, "as it comes");
		try {
			expect("as it comes: the loader lists no library mod", loadedNothing(server.lines()));
			Thread.sleep(500);
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				expect("a vanilla client logs in", vanilla.login(60) == null);
				vanilla.stand(20, 50);
				expect("and is sent no 250 packet", vanilla.await(r -> r.id() == 250, 1) == null);
			}
			Thread.sleep(5500);
			expect("the plain pack plays there", tryServer(run, "plain-on-plain", instance, "", port).startsWith("admitted"));
		} finally {
			stop("as it comes");
		}

		Files.move(library.resolve(nameOf(library, "faithful-core")), mods.resolve(nameOf(library, "faithful-core")));
		Files.move(library.resolve(nameOf(library, "oldfaithful-settings")), mods.resolve(nameOf(library, "oldfaithful-settings")));
		for (String mod : SERVER_MODS) Files.move(library.resolve(nameOf(library, mod)), mods.resolve(nameOf(library, mod)));
		start(dir, "all but Water Bottles");
		try {
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				expect("World Rules and Better Dungeons on: a vanilla client logs in", vanilla.login(60) == null);
			}
			Thread.sleep(5500);
			expect("the plain pack plays there", tryServer(run, "plain-on-others", instance, "", port).startsWith("admitted"));
			Thread.sleep(5500);
			expect("a pack with everything on plays there", tryServer(run, "all-on-others", instance, allOn(), port).startsWith("admitted"));
		} finally {
			stop("all but Water Bottles");
		}

		Path bottles = mods.resolve(nameOf(library, "water-bottles"));
		Files.move(library.resolve(bottles.getFileName()), bottles);
		start(dir, "all");
		try {
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				String reason = vanilla.login(60);
				expect("Water Bottles on: a vanilla client is refused, plainly: " + reason, reason != null && reason.contains("Faithful Library"));
			}
			Thread.sleep(5500);
			String shown = tryServer(run, "plain-on-all", instance, "", port);
			expect("a plain pack is refused the same way: " + shown, shown.contains("Faithful Library"));
			Thread.sleep(5500);
			Path partial = game(run, "one-on-all", instance);
			Files.writeString(partial.resolve(LIST), "mods-library/faithful-core.jar\nmods-library/plain-f3.jar\n");
			List<String> played = launch(partial, false, args("-Doldfaithful.selftest.tryServer=127.0.0.1:" + port, "-Doldfaithful.selftest.recordText=true", "-Doldfaithful.selftest.useServerSet=true"), failures);
			lines.addAll(played);
			shown = said(played, "[library-harness] SCREEN ");
			expect("a pack with another mod on is told what's missing: " + shown, shown.contains("MismatchScreen") && shown.contains("water-bottles"));
			String set = said(played, "[library-harness] SET ");
			String list = Files.readString(partial.resolve(LIST));
			expect("Use This Server's Set says to restart (" + set + ") and turns it on, keeping Plain F3: " + list.replace('\n', ' '),
					set.contains("restart") && list.contains("water-bottles") && list.contains("plain-f3") && list.contains("faithful-core"));
			Thread.sleep(5500);
			expect("a pack with everything on plays there", tryServer(run, "all-on-all", instance, allOn(), port).startsWith("admitted"));
		} finally {
			stop("all");
		}

		Files.delete(bottles);
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		List<String> refused;
		try (GameProcess p = new GameProcess(command, dir, dir.resolve("harness-output-refused.log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m"))) {
			// It says so and goes no further, though the process stays until it's closed.
			p.await(l -> l.contains("accept-missing-content") || l.contains("Done ("), 120);
			refused = p.lines();
		}
		lines.add("--- server: Water Bottles taken out");
		lines.addAll(refused);
		expect("Water Bottles taken out: the server refuses to start and says how to go on", refused.stream().anyMatch(l -> l.contains("accept-missing-content")));
		Path worldFile = dir.resolve("world/faithful-world.properties");
		Files.writeString(worldFile, Files.readString(worldFile) + "accept-missing-content=true\n");
		start(dir, "told to go on");
		try {
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				expect("told to go on, it starts, and a vanilla client logs in again", vanilla.login(60) == null);
			}
		} finally {
			stop("told to go on");
		}
		expect("and being told is used up", Files.readString(worldFile).contains("accept-missing-content=false"));
		// Back in, and out again: asked again.
		Files.copy(getServerPackage().get().getAsFile().toPath().resolve("mods-library").resolve(bottles.getFileName()), bottles);
		start(dir, "Water Bottles back");
		stop("Water Bottles back");
		Files.delete(bottles);
		try (GameProcess p = new GameProcess(command, dir, dir.resolve("harness-output-refused-again.log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m"))) {
			p.await(l -> l.contains("accept-missing-content") || l.contains("Done ("), 120);
			refused = p.lines();
		}
		lines.add("--- server: Water Bottles taken out a second time");
		lines.addAll(refused);
		expect("taken out a second time: refused again", refused.stream().anyMatch(l -> l.contains("accept-missing-content=true")));
	}

	/** A game folder laid out as the pack lays out {@code .minecraft}, with the self-test beside the mods. */
	private Path game(Path run, String name, Path instance) throws IOException {
		Path game = run.resolve(name);
		copyTree(instance.resolve(".minecraft"), game);
		for (java.io.File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		return game;
	}

	/** The launch arguments of the pack's own net.minecraft component, as Prism would add them, and these. */
	private List<String> args(String... more) {
		List<String> all = new ArrayList<>(packArgs);
		all.addAll(List.of(more));
		return all;
	}

	private static List<String> jvmArgs(Path instance) throws IOException {
		Matcher m = JVM_ARGS.matcher(Files.readString(instance.resolve("patches/net.minecraft.json"), StandardCharsets.UTF_8));
		if (!m.find()) throw new IOException("no +jvmArgs in the pack's net.minecraft component");
		List<String> found = new ArrayList<>();
		for (Matcher q = QUOTED.matcher(m.group(1)); q.find(); ) found.add(q.group(1));
		return found;
	}

	/** What the loader's mod list said: nothing of the library's, by id. */
	private static boolean loadedNothing(List<String> output) {
		return output.stream().noneMatch(l -> l.contains("- faithful-core ") || l.contains("- world-rules ") || l.contains("- water-bottles ") || l.contains("- better-dungeons ")
				|| l.contains("- plain-f3 ") || l.contains("- no-toasts "));
	}

	private String allOn() throws IOException {
		return String.join("\n", "mods-library/better-dungeons.jar", "mods-library/faithful-core.jar", "mods-library/no-toasts.jar", "mods-library/plain-f3.jar",
				"mods-library/water-bottles.jar", "mods-library/world-rules.jar", "");
	}

	/** Joins from a game folder of its own with this list; what the client ended up looking at. */
	private String tryServer(Path run, String name, Path instance, String list, int port) throws Exception {
		Path game = game(run, name, instance);
		Files.writeString(game.resolve(LIST), list);
		lines.add("--- " + name);
		List<String> played = launch(game, false, args("-Doldfaithful.selftest.tryServer=127.0.0.1:" + port, "-Doldfaithful.selftest.recordText=true"), failures);
		lines.addAll(played);
		return said(played, "[library-harness] SCREEN ");
	}

	private static String said(List<String> output, String prefix) {
		for (String line : output) {
			if (line.startsWith(prefix)) return line.substring(prefix.length());
		}
		return "nothing";
	}

	/** Plays the server's side of a login, to read what a client from this pack sends: its seed, which is where the core would say it's there. */
	private long loginSeed(Path run, String name, Path instance, String list) throws Exception {
		long[] seed = {-1};
		int[] after = {-1};
		String[] problem = {null};
		try (ServerSocket socket = new ServerSocket()) {
			socket.bind(new InetSocketAddress("127.0.0.1", 0));
			socket.setSoTimeout(120_000);
			Thread listener = new Thread(() -> {
				try (Socket s = socket.accept()) {
					DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
					DataOutputStream out = new DataOutputStream(s.getOutputStream());
					if (in.read() != 2) throw new IOException("no handshake first");
					RawClient.readString(in);
					out.write(2);
					RawClient.writeString(out, "-");
					out.flush();
					if (in.read() != 1) throw new IOException("no login after the handshake");
					in.readInt();
					RawClient.readString(in);
					seed[0] = in.readLong();
					int dimension = in.readByte();
					if (dimension != 0) throw new IOException("a login with dimension " + dimension);
					out.write(255);
					RawClient.writeString(out, RawServer.DONE);
					out.flush();
					s.setSoTimeout(5000);
					try {
						after[0] = in.read();
					} catch (java.net.SocketTimeoutException | java.net.SocketException e) {
						after[0] = -1;
					}
				} catch (IOException e) {
					problem[0] = e.toString();
				}
			}, "login-listener");
			listener.setDaemon(true);
			listener.start();
			tryServer(run, name, instance, list, socket.getLocalPort());
			listener.join(10_000);
		}
		if (problem[0] != null) failures.add(name + ": " + problem[0]);
		if (after[0] != -1) failures.add(name + ": the client sent packet " + after[0] + " after being turned away");
		getLogger().lifecycle("{}: the login's seed was {}", name, Long.toHexString(seed[0]));
		return seed[0];
	}

	private void start(Path dir, String label) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		server = new GameProcess(command, dir, dir.resolve("harness-output-" + label.replace(' ', '-') + ".log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m"));
		if (server.await(l -> l.contains("Done ("), 300) == null) {
			server.close();
			throw new IOException("the server never finished starting (" + label + "): " + dir);
		}
	}

	private void stop(String label) throws Exception {
		server.send("stop");
		Integer exit = server.waitFor(120);
		if (exit == null) failures.add(label + ": the server didn't stop within 2 minutes");
		List<String> out = server.lines();
		server.close();
		for (String p : problems(out)) failures.add(label + ", server: " + p);
		lines.add("--- server: " + label);
		lines.addAll(out);
	}

	private static String nameOf(Path folder, String id) {
		try (Stream<Path> jars = Files.list(folder)) {
			return jars.map(p -> p.getFileName().toString()).filter(n -> n.matches(Pattern.quote(id) + "-\\d.*\\.jar")).findFirst().orElse(null);
		} catch (IOException e) {
			return null;
		}
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
