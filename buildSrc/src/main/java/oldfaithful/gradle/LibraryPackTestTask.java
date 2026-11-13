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
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * A release's content pack (More Faithful) and its server package, unpacked as they ship (gradle/library.gradle), each launched with the arguments the pack's own
 * component gives. As shipped: every mod on the list, the edition's whole start-up test with all of it loaded, in a new world. With nothing ticked: the same test, none
 * of the library's code present, the login a vanilla client's, the Mods menu offering every mod, and ticks written for the next launch. With no list at all, the same.
 * Then the server package with every library jar taken out (the plain edition's: a raw vanilla client hears nothing new, the pack plays there both as shipped and with
 * nothing on), with the server-side mods but those with content (still any client). Then as it comes, everything on (vanilla refused, a plain pack refused, a pack with a
 * mod on told what's missing and offered the server's set, the pack as shipped admitted), and with the mods with content taken out again (the server refuses to start,
 * then goes on when told). Which mods are which comes from the build, so the pack's own list is the one tested. With any of its mods on, the title screen and Options
 * name the pack, and with none, the edition below. Where the pack starts its new worlds with rules of its own, the pack and the server package carry them, the
 * new world made in each has them, and an older version's rules file carried over keeps its lines and gains the pack's only for a rule it lacked.
 * Where it has presets, the pack ships its default's mods on, and three launches choose another, turn a mod on beyond it, load that, go back and on, and
 * load that; the first one's record, carried into a fresh copy with the older rules file, puts that copy's list back to it for the next launch.
 * Prism itself isn't run here.
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

	/** Which pack, for its report. */
	@Input
	public abstract Property<String> getLabel();

	/** Every library mod the pack carries, by id, the core aside. */
	@Input
	public abstract ListProperty<String> getPackMods();

	/** Of those, the ones that run only on a client, so no server package has them. */
	@Input
	public abstract ListProperty<String> getClientOnlyMods();

	/** Of those, the ones every player on a server running them must have too (they add content): a vanilla client is refused there. */
	@Input
	public abstract ListProperty<String> getContentMods();

	/**
	 * What the game should call itself with any of the pack's mods on, as the start-up test's edition ({@code Name:=version}): the pack carries a file
	 * naming it, which Faithful Core reads. With none on it's the edition below ({@link #getBaseEdition}).
	 */
	@Input
	public abstract Property<String> getPackEdition();

	/** The edition the pack is with every mod off ({@code Name:mod id}, the mod whose version it shows). */
	@Input
	public abstract Property<String> getBaseEdition();

	/**
	 * The rules the pack starts its new worlds with, as the build has them ({@code release/<pack>/starting-rules.properties}): the pack and its server
	 * package should each carry them in their {@code pack.properties} and ship no rules file of the player's, a new world in either should start with
	 * them, and an install carried over from an older version should keep the starts it had and take the pack's only for a rule that's new to it.
	 * Not set for a pack with none.
	 */
	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getStartingRules();

	/** The pack's presets as the build has them, in order, each {@code id=mod,mod,...}; empty for a pack without. */
	@Input
	public abstract ListProperty<String> getPresets();

	/** The preset a fresh install starts on; unset without presets. */
	@Input
	@Optional
	public abstract Property<String> getFirstPreset();

	private static final String LIST = "mods-library/selection.txt";
	/** The install's preset and its changes to it, written by More Faithful's own jar (morefaithful-presets). */
	private static final String RECORD = "config/faithful-preset.properties";
	/** The install's own: what its new worlds start with, a line for each rule it has met, written by the core. */
	private static final String OWN_RULES = "config/faithful-rules.properties";
	private static final String WORLD_FILE = "saves/oldfaithful-startup/faithful-world.properties";
	private static final Pattern JVM_ARGS = Pattern.compile("\"\\+jvmArgs\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

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
		Path marker = instance.resolve(".minecraft/mods-library/pack.properties");
		java.util.Properties named = new java.util.Properties();
		if (Files.isRegularFile(marker)) try (java.io.Reader in = Files.newBufferedReader(marker)) { named.load(in); }
		String[] edition = getPackEdition().get().split(":=", 2);
		expect("the pack names itself, " + named, edition[0].equals(named.getProperty("name")) && edition[1].equals(named.getProperty("version")));
		java.util.Properties starting = properties(getStartingRules().isPresent() ? getStartingRules().get().getAsFile().toPath() : null);
		if (getStartingRules().isPresent()) {
			expect("the build's starting rules for new worlds name some: " + starting, !starting.isEmpty());
			expect("the pack carries them in its pack.properties", missing(starting, named).isEmpty());
			expect("and ships no " + OWN_RULES + ", which is the player's", !Files.exists(instance.resolve(".minecraft").resolve(OWN_RULES)));
		}

		if (!getPresets().get().isEmpty()) {
			List<String> unlike = new ArrayList<>();
			StringBuilder order = new StringBuilder();
			for (String preset : getPresets().get()) {
				String[] p = preset.split("=", 2);
				order.append(order.length() == 0 ? "" : ",").append(p[0]);
				if (!p[1].equals(named.getProperty("preset." + p[0] + ".mods"))) unlike.add(p[0] + " (" + named.getProperty("preset." + p[0] + ".mods") + ")");
			}
			expect("the pack's presets are the build's, " + order + ", " + getFirstPreset().get() + " first" + (unlike.isEmpty() ? "" : "; not " + unlike),
					unlike.isEmpty() && order.toString().equals(named.getProperty("presets")) && getFirstPreset().get().equals(named.getProperty("preset.default")));
			expect("and ships no " + RECORD + ", which is the player's", !Files.exists(instance.resolve(".minecraft").resolve(RECORD)));
		}

		Path shipped = game(run, "as-shipped", instance);
		expect("the pack ships its default preset's mods on (" + getFirstPreset().getOrElse("no presets: every mod") + ")", Files.readString(shipped.resolve(LIST)).equals(asShipped()));
		lines.add("--- as shipped, everything on, a new world: the edition's whole start-up test");
		List<String> played = launch(shipped, false, args("-Doldfaithful.selftest.libraryPack=on", "-Doldfaithful.selftest.expectSize=1000x600", "-Dorg.prismlauncher.window.dimensions=1000x600", "-Doldfaithful.selftest.expectMaximized=false"), failures);
		lines.addAll(played);
		for (String id : withCore()) {
			expect("as shipped: the loader lists " + id, played.stream().anyMatch(l -> l.contains(id)));
		}
		expect("the new world's file has seen Water Bottles", Files.isRegularFile(shipped.resolve("saves/oldfaithful-startup/faithful-world.properties"))
				&& Files.readString(shipped.resolve("saves/oldfaithful-startup/faithful-world.properties")).contains("mod.water-bottles="));
		startedWith("the pack's new world", shipped.resolve(WORLD_FILE), starting);
		if (!starting.isEmpty()) {
			List<String> unmet = missing(starting, properties(shipped.resolve(OWN_RULES)));
			expect("and the install has met them: each has its line in " + OWN_RULES + (unmet.isEmpty() ? "" : "; not " + unmet), unmet.isEmpty());
		}
		if (!getPresets().get().isEmpty()) {
			java.util.Properties first = properties(shipped.resolve(RECORD));
			expect("its first start recorded the default preset with nothing beyond it: " + first, first.getProperty("preset", "").equals(getFirstPreset().get())
					&& first.getProperty("on", "x").isEmpty() && first.getProperty("off", "x").isEmpty());
		}
		Path record = presets(run, instance);
		if (!starting.isEmpty() || record != null) carriedOver(run, instance, starting, record);
		expect("a client with the core says so in its login", loginSeed(run, "login-all", instance, asShipped()) == RawClient.CORE_MAGIC);

		Path plain = game(run, "nothing-ticked", instance);
		Files.writeString(plain.resolve(LIST), "");
		lines.add("--- nothing ticked: the edition's whole start-up test");
		played = launch(plain, false, args("-Doldfaithful.selftest.libraryPack=off", "-Doldfaithful.selftest.expectSize=1000x600", "-Dorg.prismlauncher.window.dimensions=1000x600", "-Doldfaithful.selftest.expectMaximized=false"), failures);
		lines.addAll(played);
		expect("nothing ticked: the loader lists no library mod", loadedNothing(played));
		expect("the ticks were written for the next launch: every mod and the core", java.util.Set.copyOf(Files.readAllLines(plain.resolve(LIST))).equals(java.util.Set.copyOf(everyMod().lines().toList())));
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
		List<String> serverSide = new ArrayList<>(getPackMods().get());
		serverSide.removeAll(getClientOnlyMods().get());
		List<String> withoutContent = new ArrayList<>(serverSide);
		withoutContent.removeAll(getContentMods().get());
		expect("the server package has the core, the settings and every server-side mod on (" + String.join(", ", serverSide) + ")",
				nameOf(mods, "faithful-core") != null && nameOf(mods, "oldfaithful-settings") != null && serverSide.stream().allMatch(m -> nameOf(mods, m) != null));
		expect("and no client-only mod", getClientOnlyMods().get().stream().allMatch(m -> nameOf(mods, m) == null));
		java.util.Properties starting = properties(getStartingRules().isPresent() ? getStartingRules().get().getAsFile().toPath() : null);
		if (getStartingRules().isPresent()) {
			expect("the server package carries the pack's starting rules in its pack.properties", missing(starting, properties(dir.resolve("pack.properties"))).isEmpty());
			expect("and ships no " + OWN_RULES + ", which is the owner's", !Files.exists(dir.resolve(OWN_RULES)));
		}

		// Its own folder, so these worlds never see Water Bottles.
		Path plainDir = serverFolder(run, "server-plain", port);
		Path plainMods = plainDir.resolve("mods"), held = plainDir.resolve("held");
		Files.createDirectories(held);
		List<String> allOut = new ArrayList<>(List.of("faithful-core", "oldfaithful-settings"));
		allOut.addAll(serverSide);
		for (String mod : allOut) {
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
			expect("the pack as shipped plays there", tryServer(run, "shipped-on-plain", instance, asShipped(), port).startsWith("admitted"));
		} finally {
			stop("every library jar out");
		}

		Files.move(held.resolve(nameOf(held, "faithful-core")), plainMods.resolve(nameOf(held, "faithful-core")));
		Files.move(held.resolve(nameOf(held, "oldfaithful-settings")), plainMods.resolve(nameOf(held, "oldfaithful-settings")));
		for (String mod : withoutContent) Files.move(held.resolve(nameOf(held, mod)), plainMods.resolve(nameOf(held, mod)));
		start(plainDir, "all but the mods with content");
		try {
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				expect("the mods without content on (" + String.join(", ", withoutContent) + "): a vanilla client logs in", vanilla.login(60) == null);
			}
			expect("the plain pack plays there", tryServer(run, "plain-on-others", instance, "", port).startsWith("admitted"));
			expect("a pack with everything on plays there", tryServer(run, "all-on-others", instance, everyMod(), port).startsWith("admitted"));
		} finally {
			stop("all but the mods with content");
		}

		Path bottles = mods.resolve(nameOf(mods, "water-bottles"));
		start(dir, "as it comes");
		try {
			startedWith("the server's new world", dir.resolve("world/faithful-world.properties"), starting);
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
			expect("the pack as shipped plays there", tryServer(run, "all-on-all", instance, asShipped(), port).startsWith("admitted"));
		} finally {
			stop("as it comes");
		}

		// Every mod with content out, so a vanilla client can join once the server goes on (Alchemy goes with Water Bottles in any case, as it
		// needs it: the loader wouldn't start with one and not the other).
		for (String mod : getContentMods().get()) Files.delete(mods.resolve(nameOf(mods, mod)));
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

	/** A properties file's lines, or none where there's no such file (or no path). */
	private static java.util.Properties properties(Path file) throws IOException {
		java.util.Properties p = new java.util.Properties();
		if (file != null && Files.isRegularFile(file)) try (java.io.Reader in = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) { p.load(in); }
		return p;
	}

	/**
	 * A world made here started with the pack's rules: its own file, which the core writes as a new world opens, has every one of them. A line the core
	 * didn't take (a rule no mod here has, a value that's the rule's own default) is missing there, so a mistake in the pack's file fails too.
	 */
	private void startedWith(String what, Path worldFile, java.util.Properties starting) throws IOException {
		if (starting.isEmpty()) return;
		List<String> missing = missing(starting, properties(worldFile));
		expect(what + " starts with the pack's rules, all " + starting.size() + (missing.isEmpty() ? "" : "; not " + missing), missing.isEmpty());
	}

	/** The lines of {@code wanted} that {@code in} lacks or has otherwise, each with what it has. */
	private static List<String> missing(java.util.Properties wanted, java.util.Properties in) {
		List<String> missing = new ArrayList<>();
		for (String key : new java.util.TreeSet<>(wanted.stringPropertyNames())) {
			if (!wanted.getProperty(key).equals(in.getProperty(key))) missing.add(key + "=" + wanted.getProperty(key) + " (there: " + in.getProperty(key) + ")");
		}
		return missing;
	}

	/**
	 * An install carried over from an older version of the pack: its {@code config/} copied into this version's instance, as an updater is told to. Its
	 * rules file has a line for each rule that version had, and here every one of them says the opposite of what the pack chooses now, as if the pack had
	 * changed its mind since; one of the pack's rules has no line, as a rule added since has none. A new world should start with the old lines as
	 * they are and the pack's choice for the new rule alone, and the file should gain that one line and change no other. The old lines are the on and
	 * off rules turned round (a rule of named values has no opposite to make up here, so those go without a line too, new with the first).
	 */
	private void carriedOver(Path run, Path instance, java.util.Properties starting, Path record) throws Exception {
		java.util.Properties kept = new java.util.Properties(), fresh = new java.util.Properties();
		StringBuilder old = new StringBuilder("# An older version's, carried over\nformat=1\n");
		for (String key : new java.util.TreeSet<>(starting.stringPropertyNames())) {
			String value = starting.getProperty(key);
			boolean onOff = value.equals("true") || value.equals("false");
			if (onOff && !fresh.isEmpty()) {
				kept.setProperty(key, String.valueOf(!Boolean.parseBoolean(value)));
				old.append(key).append('=').append(kept.getProperty(key)).append('\n');
			} else {
				fresh.setProperty(key, value);
			}
		}
		Path game = game(run, "carried-over", instance);
		Files.createDirectories(game.resolve("config"));
		Files.writeString(game.resolve(OWN_RULES), old.toString(), StandardCharsets.ISO_8859_1);
		if (record != null) Files.copy(record, game.resolve(RECORD));
		lines.add("--- an older version's config carried over: its rules file, " + kept + ", with no line for " + fresh.stringPropertyNames()
				+ (record != null ? ", and its preset: " + properties(record) : ""));
		List<String> played = launch(game, false, args("-Doldfaithful.selftest.settingsOnly=true", "-Doldfaithful.selftest.stallMs=280000",
				record != null ? "-Doldfaithful.selftest.presets=updated" : "-Doldfaithful.selftest.libraryPack=on"), failures);
		lines.addAll(played);
		if (record != null) {
			expect("carried over: the record is as it was, and the list put back to it said so in the log", properties(game.resolve(RECORD)).equals(properties(record))
					&& played.stream().anyMatch(l -> l.contains("The library's list wasn't what config/faithful-preset.properties says")));
		}
		if (starting.isEmpty()) return;
		java.util.Properties world = properties(game.resolve(WORLD_FILE)), own = properties(game.resolve(OWN_RULES));
		List<String> changed = missing(kept, own), unmet = missing(fresh, own), notStarted = missing(fresh, world), followedPack = new ArrayList<>();
		// A kept line that's the rule's own default leaves no line in the world, so what's checked is that the world hasn't the pack's value.
		for (String key : kept.stringPropertyNames()) {
			if (starting.getProperty(key).equals(world.getProperty(key))) followedPack.add(key + "=" + world.getProperty(key));
		}
		expect("carried over: the " + kept.size() + " starts the install had are as they were in its file, though the pack now chooses otherwise" + (changed.isEmpty() ? "" : "; not " + changed),
				!kept.isEmpty() && changed.isEmpty());
		expect("and its new world starts with them, not the pack's" + (followedPack.isEmpty() ? "" : "; the pack's: " + followedPack), Files.isRegularFile(game.resolve(WORLD_FILE)) && followedPack.isEmpty());
		expect("the rules new to it (" + String.join(", ", new java.util.TreeSet<>(fresh.stringPropertyNames())) + ") got the pack's choice in its file" + (unmet.isEmpty() ? "" : "; not " + unmet), unmet.isEmpty());
		expect("and its new world starts with those" + (notStarted.isEmpty() ? "" : "; not " + notStarted), notStarted.isEmpty());
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

	/** Every launch here names the edition the title screen and Options should show: the pack's while its list has anything on, else the edition below. */
	@Override
	protected List<String> launch(Path game, boolean linux, List<String> jvmArgs, List<String> failures) throws Exception {
		Path list = game.resolve(LIST);
		boolean anyOn = Files.isRegularFile(list) && Files.readAllLines(list).stream().anyMatch(l -> !l.isBlank());
		List<String> all = new ArrayList<>(jvmArgs);
		all.add("-Doldfaithful.selftest.edition=" + (anyOn ? getPackEdition().get() : getBaseEdition().get()));
		return super.launch(game, linux, all, failures);
	}

	/** The launch arguments of the pack's own net.minecraft component, as Prism would add them, and these. */
	private List<String> args(String... more) {
		List<String> all = new ArrayList<>(packArgs);
		all.addAll(List.of(more));
		return all;
	}

	private List<String> args(List<String> some, String... more) {
		List<String> all = args(more);
		all.addAll(some);
		return all;
	}

	private static List<String> jvmArgs(Path instance) throws IOException {
		Matcher m = JVM_ARGS.matcher(Files.readString(instance.resolve("patches/net.minecraft.json"), StandardCharsets.UTF_8));
		if (!m.find()) throw new IOException("no +jvmArgs in the pack's net.minecraft component");
		List<String> found = new ArrayList<>();
		for (Matcher q = QUOTED.matcher(m.group(1)); q.find(); ) found.add(q.group(1));
		return found;
	}

	/** The core and every mod of the pack, by id, in order. */
	private List<String> withCore() {
		List<String> ids = new ArrayList<>(getPackMods().get());
		ids.add("faithful-core");
		ids.sort(null);
		return ids;
	}

	/** What the loader's mod list said: nothing of the library's, by id. */
	private boolean loadedNothing(List<String> output) {
		List<String> ids = withCore();
		return output.stream().noneMatch(l -> ids.stream().anyMatch(id -> l.contains("- " + id + " ")));
	}

	/** A list with these mods on, and the core, as the build writes one. */
	private static String listOf(List<String> mods) {
		List<String> ids = new ArrayList<>(mods);
		ids.add("faithful-core");
		ids.sort(null);
		StringBuilder list = new StringBuilder();
		for (String id : ids) list.append("mods-library/").append(id).append(".jar\n");
		return list.toString();
	}

	/** Every mod of the pack on, the core too. */
	private String everyMod() {
		return listOf(getPackMods().get());
	}

	/** The list as the pack ships it: its default preset's mods, or with no presets every mod. */
	private String asShipped() {
		for (String preset : getPresets().get()) {
			String[] p = preset.split("=", 2);
			if (p[0].equals(getFirstPreset().getOrNull())) return listOf(p[1].isEmpty() ? List.of() : List.of(p[1].split(",")));
		}
		return everyMod();
	}

	/**
	 * Three launches of one copy of the pack, each checked in the game (the settings check's presets): as shipped, the next preset chosen and a mod turned on
	 * beyond it; relaunched, that loaded, then back to the preset as it comes and on to the next; relaunched, that loaded. The record the first left, for an
	 * update to carry in; null for a pack without presets.
	 */
	private Path presets(Path run, Path instance) throws Exception {
		if (getPresets().get().isEmpty()) return null;
		Path game = game(run, "presets", instance);
		List<String> settingsOnly = List.of("-Doldfaithful.selftest.settingsOnly=true", "-Doldfaithful.selftest.stallMs=280000");
		lines.add("--- presets: as shipped, the next one chosen, and a mod turned on beyond it");
		lines.addAll(launch(game, false, args(settingsOnly, "-Doldfaithful.selftest.presets=choose"), failures));
		Path record = run.resolve("preset-record.properties");
		if (Files.isRegularFile(game.resolve(RECORD))) Files.copy(game.resolve(RECORD), record);
		// Each launch in a new world: one that has seen a mod with content the next launch hasn't on would stop at the core's warning.
		deleteTree(game.resolve("saves"));
		lines.add("--- presets: relaunched, back to the preset as it comes, and on to the next");
		lines.addAll(launch(game, false, args(settingsOnly, "-Doldfaithful.selftest.presets=custom"), failures));
		deleteTree(game.resolve("saves"));
		lines.add("--- presets: relaunched on that one");
		lines.addAll(launch(game, false, args(settingsOnly, "-Doldfaithful.selftest.presets=loaded"), failures));
		expect("presets: the first launch left a record for an update to carry over", Files.isRegularFile(record));
		return Files.isRegularFile(record) ? record : null;
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

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
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
