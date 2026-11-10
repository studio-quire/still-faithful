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
 * gives. As shipped: every mod on the list, the edition's whole start-up test with all of it loaded, in a new world. With nothing ticked: the same test, none of the
 * library's code present, the login a vanilla client's, the Mods menu offering every mod, and ticks written for the next launch. With no list at all, the same. Then the
 * server package with every library jar taken out (the plain edition's: a raw vanilla client hears nothing new, the pack plays there both as shipped and with nothing on), with
 * the server-side mods but Water Bottles (still any client). Then as it comes, everything on (vanilla refused, a plain pack refused, a pack with a mod on told what's missing and
 * offered the server's set, the pack as shipped admitted), and with Water Bottles taken out again (the server refuses to start, then goes on when told). Prism itself isn't run here.
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
	private static final List<String> SERVER_MODS = List.of("world-rules", "hearty-food", "item-repair", "wild-saplings");

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

		Path shipped = game(run, "as-shipped", instance);
		expect("the pack ships every mod on", Files.readString(shipped.resolve(LIST)).equals(allOn()));
		lines.add("--- as shipped, everything on, a new world: the edition's whole start-up test");
		List<String> played = launch(shipped, false, args("-Doldfaithful.selftest.libraryPack=on", "-Doldfaithful.selftest.expectSize=1000x600", "-Dorg.prismlauncher.window.dimensions=1000x600", "-Doldfaithful.selftest.expectMaximized=false"), failures);
		lines.addAll(played);
		for (String id : List.of("faithful-core", "alchemy", "blue-flowers", "buried-towers", "explosive-arrows", "hearty-food", "item-repair", "no-toasts", "plain-f3", "water-bottles", "wild-saplings", "world-rules")) {
			expect("as shipped: the loader lists " + id, played.stream().anyMatch(l -> l.contains(id)));
		}
		expect("the new world's file has seen Water Bottles", Files.isRegularFile(shipped.resolve("saves/oldfaithful-startup/faithful-world.properties"))
				&& Files.readString(shipped.resolve("saves/oldfaithful-startup/faithful-world.properties")).contains("mod.water-bottles="));
		expect("a client with the core says so in its login", loginSeed(run, "login-all", instance, allOn()) == RawClient.CORE_MAGIC);

		Path plain = game(run, "nothing-ticked", instance);
		Files.writeString(plain.resolve(LIST), "");
		lines.add("--- nothing ticked: the edition's whole start-up test");
		played = launch(plain, false, args("-Doldfaithful.selftest.libraryPack=off", "-Doldfaithful.selftest.expectSize=1000x600", "-Dorg.prismlauncher.window.dimensions=1000x600", "-Doldfaithful.selftest.expectMaximized=false"), failures);
		lines.addAll(played);
		expect("nothing ticked: the loader lists no library mod", loadedNothing(played));
		expect("the ticks were written for the next launch: every mod and the core", java.util.Set.copyOf(Files.readAllLines(plain.resolve(LIST))).equals(java.util.Set.copyOf(allOn().lines().toList())));
		expect("a plain client's login is a vanilla client's", loginSeed(run, "login-plain", instance, "") == 0L);

		Path absent = game(run, "no-list", instance);
		Files.delete(absent.resolve(LIST));
		lines.add("--- no list at all");
		played = launch(absent, false, args("-Doldfaithful.selftest.libraryPack=absent", "-Doldfaithful.selftest.settingsOnly=true", "-Doldfaithful.selftest.stallMs=280000"), failures);
		lines.addAll(played);
		expect("no list: the loader lists no library mod", loadedNothing(played));

		server(run, instance);
		report("Library pack test (" + getLabel().get() + ")", lines, failures);
	}

	private void server(Path run, Path instance) throws Exception {
		int port = freePort();
		Path dir = serverFolder(run, "server", port);
		Path mods = dir.resolve("mods");
		expect("the server package has the core, the settings and every server-side mod on",
				nameOf(mods, "faithful-core") != null && nameOf(mods, "oldfaithful-settings") != null && nameOf(mods, "water-bottles") != null && nameOf(mods, "alchemy") != null && SERVER_MODS.stream().allMatch(m -> nameOf(mods, m) != null));
		expect("and no client-only mod", nameOf(mods, "plain-f3") == null && nameOf(mods, "no-toasts") == null);

		// Its own folder, so these worlds never see Water Bottles.
		Path plainDir = serverFolder(run, "server-plain", port);
		Path plainMods = plainDir.resolve("mods"), held = plainDir.resolve("held");
		Files.createDirectories(held);
		for (String mod : List.of("faithful-core", "oldfaithful-settings", "water-bottles", "alchemy", "blue-flowers", "world-rules", "buried-towers", "explosive-arrows", "hearty-food", "item-repair", "wild-saplings")) {
			Files.move(plainMods.resolve(nameOf(plainMods, mod)), held.resolve(nameOf(plainMods, mod)));
		}
		start(plainDir, "every library jar out");
		try {
			expect("every library jar out: the loader lists no library mod", loadedNothing(server.lines()));
			Thread.sleep(500);
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				expect("a vanilla client logs in", vanilla.login(60) == null);
				vanilla.stand(20, 50);
				expect("and is sent no 250 packet", vanilla.await(r -> r.id() == 250, 1) == null);
			}
			expect("the plain pack plays there", tryServer(run, "plain-on-plain", instance, "", port).startsWith("admitted"));
			expect("the pack as shipped plays there", tryServer(run, "shipped-on-plain", instance, allOn(), port).startsWith("admitted"));
		} finally {
			stop("every library jar out");
		}

		Files.move(held.resolve(nameOf(held, "faithful-core")), plainMods.resolve(nameOf(held, "faithful-core")));
		Files.move(held.resolve(nameOf(held, "oldfaithful-settings")), plainMods.resolve(nameOf(held, "oldfaithful-settings")));
		for (String mod : SERVER_MODS) Files.move(held.resolve(nameOf(held, mod)), plainMods.resolve(nameOf(held, mod)));
		start(plainDir, "all but the mods with content");
		try {
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				expect("the mods without content on (" + String.join(", ", SERVER_MODS) + "): a vanilla client logs in", vanilla.login(60) == null);
			}
			expect("the plain pack plays there", tryServer(run, "plain-on-others", instance, "", port).startsWith("admitted"));
			expect("a pack with everything on plays there", tryServer(run, "all-on-others", instance, allOn(), port).startsWith("admitted"));
		} finally {
			stop("all but the mods with content");
		}

		Path bottles = mods.resolve(nameOf(mods, "water-bottles"));
		start(dir, "as it comes");
		try {
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				String reason = vanilla.login(60);
				expect("Water Bottles on: a vanilla client is refused, plainly: " + reason, reason != null && reason.contains("Faithful Library"));
			}
			String shown = tryServer(run, "plain-on-all", instance, "", port);
			expect("a plain pack is refused the same way: " + shown, shown.contains("Faithful Library"));
			Path partial = game(run, "one-on-all", instance);
			Files.writeString(partial.resolve(LIST), "mods-library/faithful-core.jar\nmods-library/plain-f3.jar\n");
			List<String> played = launch(partial, false, args("-Doldfaithful.selftest.tryServer=127.0.0.1:" + port, "-Doldfaithful.selftest.recordText=true", "-Doldfaithful.selftest.useServerSet=true"), failures);
			lines.addAll(played);
			shown = said(played, "[library-harness] SCREEN ");
			expect("a pack with another mod on is told what's missing: " + shown, shown.contains("MismatchScreen") && shown.contains("The server needs: Water Bottles"));
			String set = said(played, "[library-harness] SET ");
			String list = Files.readString(partial.resolve(LIST));
			expect("Match This Server's Mods says to restart (" + set + ") and turns it on, keeping Plain F3: " + list.replace('\n', ' '),
					set.contains("restart") && list.contains("water-bottles") && list.contains("plain-f3") && list.contains("faithful-core"));
			expect("the pack as shipped plays there", tryServer(run, "all-on-all", instance, allOn(), port).startsWith("admitted"));
		} finally {
			stop("as it comes");
		}

		// Alchemy goes with it, as it needs it: the loader wouldn't start with one and not the other.
		Files.delete(bottles);
		Files.delete(mods.resolve(nameOf(mods, "alchemy")));
		Files.delete(mods.resolve(nameOf(mods, "buried-towers")));
		Files.delete(mods.resolve(nameOf(mods, "blue-flowers")));
		Files.delete(mods.resolve(nameOf(mods, "explosive-arrows")));
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
		Files.copy(getServerPackage().get().getAsFile().toPath().resolve("mods").resolve(bottles.getFileName()), bottles);
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

	/** The server package unpacked, with the server jar and a test's properties. */
	private Path serverFolder(Path run, String name, int port) throws IOException {
		Path dir = run.resolve(name);
		copyTree(getServerPackage().get().getAsFile().toPath(), dir);
		Files.copy(getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1",
				"server-port=" + port, "view-distance=3", "max-players=20", "spawn-monsters=false", "spawn-animals=true", "pvp=true", "allow-flight=true", ""));
		return dir;
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
		return output.stream().noneMatch(l -> l.contains("- faithful-core ") || l.contains("- world-rules ") || l.contains("- water-bottles ") || l.contains("- blue-flowers ") || l.contains("- buried-towers ") || l.contains("- hearty-food ") || l.contains("- item-repair ") || l.contains("- alchemy ") || l.contains("- wild-saplings ")
				|| l.contains("- explosive-arrows ") || l.contains("- plain-f3 ") || l.contains("- no-toasts "));
	}

	private String allOn() throws IOException {
		return String.join("\n", "mods-library/alchemy.jar", "mods-library/blue-flowers.jar", "mods-library/buried-towers.jar", "mods-library/explosive-arrows.jar", "mods-library/faithful-core.jar", "mods-library/hearty-food.jar", "mods-library/item-repair.jar", "mods-library/no-toasts.jar", "mods-library/plain-f3.jar",
				"mods-library/water-bottles.jar", "mods-library/wild-saplings.jar", "mods-library/world-rules.jar", "");
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
