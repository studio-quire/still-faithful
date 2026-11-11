package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * The conflict harness, stages 2 to 8 (docs/library-harness.md), cheap first, stopping at the first stage that fails unless told
 * to go on. {@code mods} (inherited) is what every client carries: Pure Faithful's mods, the self-test, settings and the core.
 * A failure names the stage, the mod and the rule. Boots are parallel, a few at a time: each is a game of its own.
 */
public abstract class LibraryTestTask extends ClientStartupTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** Pure Faithful's server side, settings and the core. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getServerBase();

	/** The mods under test. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getSubjects();

	/** Their own tests, each a mod of its own: {@code selftestRemapJar}. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getSelftests();

	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getProbe();

	/** What each edition adds on the client: Old Faithful's mods, Still Faithful's. On a server, the same less what's client only. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getOldMods();

	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getStillMods();

	@Input
	public abstract Property<String> getStillEdition();

	/** The self-tests on an edition's mods as well ("old", "still"), as a player's pack has them: blank for Pure Faithful's alone. */
	@Input
	public abstract Property<String> getSelftestEdition();

	/** {@code library/tags.txt}. */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getTagVocabulary();

	/** {@code library/together.txt}: table entries two of our mods change only together, by design. */
	@InputFile
	@org.gradle.api.tasks.Optional
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getTogether();

	/** Stage names to run; empty for all. */
	@Input
	public abstract ListProperty<String> getStages();

	@Input
	public abstract Property<Long> getSeed();

	@Input
	public abstract Property<Integer> getParallel();

	@Input
	public abstract Property<Boolean> getKeepGoing();

	/** The selftest stage without a game client: mods' world tests on a server, a raw client for a player. */
	@Input
	public abstract Property<Boolean> getServerOnly();

	/** The selftest stage in single player only, fresh and reopened: no servers, two hidden clients instead of six. */
	@Input
	public abstract Property<Boolean> getSinglePlayerOnly();

	/** Hidden clients in the combinations stage, beyond the whole set. */
	@Input
	public abstract Property<Integer> getClientSamples();

	public LibraryTestTask() {
		getStages().convention(List.of());
		getSeed().convention(1733L);
		getParallel().convention(1);
		getKeepGoing().convention(false);
		getServerOnly().convention(false);
		getSinglePlayerOnly().convention(false);
		getClientSamples().convention(2);
		getStillEdition().convention("");
		getSelftestEdition().convention("");
	}

	private static final Pattern FAIL = Pattern.compile("\\[library-harness] FAIL (.*?) \\| (.*?) \\| (.*)");
	private static final Pattern NOTE = Pattern.compile("\\[library-harness] NOTE (.*?) \\| (.*?) \\| (.*)");
	private static final Pattern LISTENER = Pattern.compile("\\[faithful-core] WARN (\\S+)'s listener on (.*?) threw.*?: (.*)");
	private static final Pattern START = Pattern.compile("The mod (\\S+) failed while starting: (.*)");
	/** The registry's own refusals, which come while numbering rather than in a mod's start. */
	private static final Pattern REGISTRY = Pattern.compile("^java.lang.IllegalStateException: (The (?:stand-in|item stand-in|block|item|family member) (?:of )?([a-z][a-z0-9_-]*):.*)");
	private static final Pattern CONFIG = Pattern.compile("([a-z0-9_-]+)\\.mixins\\.json");
	/** A network that didn't answer, not a mod: skins and sounds are fetched from Mojang, and a test run shouldn't fail on a timeout there. */
	private static final Pattern OFFLINE = Pattern.compile("\\[purefaithful-hardening] WARN (Sounds not checked|Skin or cape not loaded|Beta's \\S+ not fetched from).*(Timeout|UnknownHost|Connect|Socket)\\w*Exception");
	/** A mod's own test: {@code Checks} on either side, or a client check run under its name. */
	private static final Pattern TEST = Pattern.compile("\\[faithful-test] (PASS|FAIL|NOTE) (.*?) \\| (.*)");

	record Failure(String stage, String mod, String rule, String detail) {
		@Override
		public String toString() {
			return stage + ": " + mod + ": " + rule + ": " + detail;
		}
	}

	protected final List<Failure> failures = new ArrayList<>();
	protected final Set<String> notes = new LinkedHashSet<>();
	protected String stage = "";
	protected Path run;
	/** What's turned on in the planted mods, in {@code libraryBadTest}. */
	protected String planted = "";
	protected List<LibraryMod> subjects;
	/** Outputs of the boots made so far, by label: their registry and tables. */
	protected final Map<String, Path> boots = new LinkedHashMap<>();
	/** Checks passed in what's been scanned, by mod: the selftest stage's count. */
	protected final Map<String, Integer> testsPassed = new LinkedHashMap<>();
	/** For a launch that runs longer than the task's usual, or 0. */
	protected int longLaunch;

	/** Stages by name, in order. */
	protected Map<String, Stage> stages() {
		Map<String, Stage> s = new LinkedHashMap<>();
		s.put("registry", new LibraryStages.Registry(this));
		s.put("combinations", new LibraryStages.Combinations(this));
		s.put("selftest", new LibrarySelftestStage(this));
		s.put("salts", new LibraryWorldStages.Salts(this));
		s.put("round-trips", new LibraryWorldStages.RoundTrips(this));
		s.put("multiplayer", new LibraryNetStage(this));
		s.put("monkey", new LibraryNetStage.MonkeyStage(this));
		s.put("inert", new LibraryInertStage(this));
		return s;
	}

	interface Stage {
		void run() throws Exception;
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		run = freshRunDir();
		subjects = new ArrayList<>();
		for (File jar : getSubjects().getFiles()) subjects.add(LibraryMod.read(jar.toPath()));
		subjects.sort(Comparator.comparing(LibraryMod::id));
		getLogger().lifecycle("Library test: {} mods: {}", subjects.size(), subjects.stream().map(LibraryMod::id).toList());
		runStages();
		finish();
	}

	@Override
	protected int launchTimeout() {
		return longLaunch > 0 ? longLaunch : super.launchTimeout();
	}

	protected void runStages() throws Exception {
		List<String> chosen = getStages().get();
		for (Map.Entry<String, Stage> e : stages().entrySet()) {
			// The core's own check: it doesn't change with the mods, so it runs by name (`coreInertTest`).
			if (chosen.isEmpty() ? e.getKey().equals("inert") : !chosen.contains(e.getKey())) continue;
			stage = e.getKey();
			long started = System.currentTimeMillis();
			int before = failures.size();
			getLogger().lifecycle("=== {}", stage);
			try {
				e.getValue().run();
			} catch (IOException ex) {
				fail("harness", "stage-stopped", ex.toString());
			}
			getLogger().lifecycle("=== {}: {} failures, {} s", stage, failures.size() - before, (System.currentTimeMillis() - started) / 1000);
			if (failures.size() > before && !getKeepGoing().get()) break;
		}
	}

	protected void finish() throws IOException {
		List<String> report = new ArrayList<>();
		for (Failure f : failures) report.add("FAIL " + f);
		for (String n : notes) report.add("NOTE " + n);
		Files.write(run.resolve("report.txt"), report, StandardCharsets.UTF_8);
		notes.forEach(n -> getLogger().lifecycle("note: {}", n));
		if (!failures.isEmpty()) {
			List<String> lines = failures.stream().map(Failure::toString).distinct().limit(60).toList();
			throw new GradleException("Library test FAILED, " + failures.size() + " failures:\n  " + String.join("\n  ", lines) + "\nReport: " + run.resolve("report.txt"));
		}
		getLogger().lifecycle("Library test: PASSED");
	}

	synchronized void fail(String mod, String rule, String detail) {
		getLogger().lifecycle("FAIL {}: {}: {}", mod, rule, detail);
		failures.add(new Failure(stage, mod, rule, detail));
	}

	/** Everything a run's output says went wrong. {@code where} goes into the detail. */
	synchronized void scan(String where, List<String> lines) {
		boolean frames = false;
		Set<String> seen = new LinkedHashSet<>();
		for (String line : lines) {
			Matcher m;
			if ((m = TEST.matcher(line)).find()) {
				switch (m.group(1)) {
					case "PASS" -> testsPassed.merge(m.group(2), 1, Integer::sum);
					case "FAIL" -> fail(m.group(2), "selftest", m.group(3) + " [" + where + "]");
					default -> notes.add(m.group(2) + ": selftest: " + m.group(3) + " [" + where + "]");
				}
			} else if ((m = FAIL.matcher(line)).find()) {
				if (seen.add(m.group())) fail(m.group(1), m.group(2), m.group(3) + " [" + where + "]");
			} else if ((m = NOTE.matcher(line)).find()) {
				notes.add(m.group(1) + ": " + m.group(2) + ": " + m.group(3));
			} else if ((m = LISTENER.matcher(line)).find()) {
				if (seen.add(m.group())) fail(m.group(1), "listener-threw", "on " + m.group(2) + ": " + m.group(3) + " [" + where + "]");
			} else if ((m = REGISTRY.matcher(line)).find()) {
				if (seen.add(m.group(1))) fail(m.group(2), m.group(1).contains("stand-in") ? "stand-in" : "failed-to-start", m.group(1) + " [" + where + "]");
			} else if ((m = START.matcher(line)).find()) {
				String rule = m.group(2).contains("twice") ? "declared-twice" : "failed-to-start";
				if (seen.add(m.group(1) + m.group(2))) fail(m.group(1), rule, m.group(2) + " [" + where + "]");
			}
		}
		for (String p : problems(lines)) {
			if (OFFLINE.matcher(p).find()) {
				notes.add("network: offline: " + p.trim());
				continue;
			}
			if (p.contains("at org.studioquire.")) {
				if (frames) continue;
				frames = true;
			}
			Matcher m = CONFIG.matcher(p);
			fail(m.find() ? m.group(1) : "unknown", "log", p.trim() + " [" + where + "]");
		}
		for (String line : lines) {
			if (line.contains("[oldfaithful-options] WARN")) fail("unknown", "settings", line.trim() + " [" + where + "]");
			if (line.contains("[faithful-core] WARN") && !LISTENER.matcher(line).find() && !line.contains("is turned off")) fail("faithful-core", "warning", line.trim() + " [" + where + "]");
		}
	}

	/** Runs the jobs a few at a time, in order of submission. */
	<T> List<T> parallel(Collection<Callable<T>> jobs) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(getParallel().get());
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> job : jobs) futures.add(pool.submit(job));
			List<T> results = new ArrayList<>();
			for (Future<T> f : futures) {
				try {
					results.add(f.get());
				} catch (ExecutionException e) {
					throw e.getCause() instanceof Exception ex ? ex : e;
				}
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}

	/** The server's command line: as a launcher would, without what only the client needs. */
	List<String> serverCommand(List<String> jvm) {
		List<String> classpath = new ArrayList<>();
		for (File f : getLoaderClasspath().getFiles()) {
			if (!f.getName().contains("lwjgl")) classpath.add(f.getAbsolutePath());
		}
		classpath.add(getIntermediaryMappings().get().getAsFile().getAbsolutePath());
		classpath.add(getServerJar().get().getAsFile().getAbsolutePath());
		List<String> command = new ArrayList<>(List.of(getJavaExecutable().get(), "-Dmixin.debug.countInjections=true", "-Xmx768m", "-Djava.awt.headless=true"));
		// The harness probe runs the server's ticks this fast and tells joining clients so; a server without it ignores this.
		if (getTickMs().get() != 50) command.add("-Doldfaithful.test.tickMs=" + getTickMs().get());
		command.addAll(jvm);
		command.addAll(List.of("-cp", String.join(File.pathSeparator, classpath), "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui"));
		return command;
	}

	/** The jars of a server with these mods on it. */
	List<Path> serverMods(Collection<LibraryMod> mods, boolean probe) {
		List<Path> jars = new ArrayList<>();
		getServerBase().getFiles().forEach(f -> jars.add(f.toPath()));
		if (probe) getProbe().getFiles().forEach(f -> jars.add(f.toPath()));
		mods.forEach(m -> jars.add(m.jar()));
		return jars;
	}

	/** The jars of a client with these mods on it. */
	List<Path> clientMods(Collection<LibraryMod> mods, boolean probe) {
		List<Path> jars = new ArrayList<>();
		getMods().getFiles().forEach(f -> jars.add(f.toPath()));
		if (probe) getProbe().getFiles().forEach(f -> jars.add(f.toPath()));
		mods.forEach(m -> jars.add(m.jar()));
		return jars;
	}

	/** A hidden client from {@code game}, with these jars; its output scanned. */
	List<String> client(String where, Path game, Collection<Path> jars, List<String> jvm) throws Exception {
		List<String> started = new ArrayList<>();
		List<String> lines = launchIn(game, jars, jvm, started);
		judge(where, started, lines);
		return lines;
	}

	/**
	 * A hidden client that will join a server, started now on a thread of its own: it takes a few seconds to reach its main menu, which
	 * needn't be spent after the server is ready, nor after the one before has stopped. At the menu it waits to be {@link Joining#admit
	 * admitted} (StartupCheck's joinWhen), so it never tries a server that isn't ready. One game client at a time, as before: this
	 * starts only once the last has ended.
	 */
	Joining joining(String where, Path game, Collection<Path> jars, int port, List<String> jvm) throws IOException {
		return new Joining(where, game, jars, port, jvm);
	}

	final class Joining implements AutoCloseable {
		private final String where;
		private final Path gate;
		private final List<String> started = new ArrayList<>();
		private final ExecutorService thread = Executors.newSingleThreadExecutor();
		private final Future<List<String>> lines;

		private Joining(String where, Path game, Collection<Path> jars, int port, List<String> jvm) throws IOException {
			this.where = where;
			Files.createDirectories(game);
			gate = game.resolve("join-now");
			Files.deleteIfExists(gate);
			List<String> all = new ArrayList<>(List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, flag("oldfaithful.selftest.joinWhen", gate.toAbsolutePath())));
			all.addAll(jvm);
			lines = thread.submit(() -> launchIn(game, jars, all, started));
		}

		/** The server is ready now ({@link HarnessServer#awaitStart}): the client connects when it gets to its menu, or at once if it's there. */
		void admit() throws IOException {
			if (!Files.exists(gate)) Files.createFile(gate);
		}

		/** Waits for the client to end, and reads its output as {@link #client} does: on this thread, so what it counts lands where the caller is. */
		List<String> await() throws Exception {
			List<String> out;
			try {
				out = lines.get();
			} catch (ExecutionException e) {
				throw e.getCause() instanceof Exception ex ? ex : e;
			}
			judge(where, started, out);
			return out;
		}

		/** Ends a client that's still running (its server never came up, or the stage is stopping), unjudged. */
		@Override
		public void close() throws InterruptedException {
			lines.cancel(true);
			thread.shutdownNow();
			// The launch's own clean-up ends the game; the folder is free again after it.
			thread.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
		}
	}

	private void judge(String where, List<String> started, List<String> lines) {
		for (String s : started) {
			// Stack frames and what scan() reads anyway only bury the line that says what happened.
			String body = s.replace("error in the output:", "").trim();
			if (!s.startsWith("log: ") && !body.startsWith("at ") && !body.startsWith("Caused by") && !body.startsWith("...") && !OFFLINE.matcher(body).find()) fail("unknown", "client", s + " [" + where + "]");
		}
		scan(where, lines);
	}

	/** As {@link #client}, for a run that's refused or attacked, where the log is full of what it turned away: only that it ran and stopped counts. */
	List<String> quietClient(String where, Path game, Collection<Path> jars, List<String> jvm) throws Exception {
		List<String> started = new ArrayList<>();
		List<String> lines = launchIn(game, jars, jvm, started);
		for (String s : started) {
			if (!s.startsWith("log: ") && !s.startsWith("error in the output:")) fail("unknown", "client", s + " [" + where + "]");
		}
		return lines;
	}

	private List<String> launchIn(Path game, Collection<Path> jars, List<String> jvm, List<String> started) throws Exception {
		if (Files.exists(game.resolve("mods"))) {
			try (Stream<Path> old = Files.list(game.resolve("mods"))) {
				for (Path p : old.toList()) Files.delete(p);
			}
		}
		Files.createDirectories(game.resolve("mods"));
		Set<String> names = new LinkedHashSet<>();
		for (Path jar : jars) {
			if (names.add(jar.getFileName().toString())) Files.copy(jar, game.resolve("mods").resolve(jar.getFileName()));
		}
		if (!Files.exists(game.resolve("alsoft.ini"))) prepare(game, false);
		return launch(game, false, jvm, started);
	}

	/** What every run of the probe is told: where to leave its output. */
	List<String> probeFlags(Path out) {
		return List.of(flag("faithful.harness.out", out), flag("faithful.harness.tags", getTagVocabulary().get().getAsFile()), flag("faithful.bad", planted));
	}

	static List<String> problemsOf(List<String> lines) {
		return problems(lines);
	}

	static String flag(String name, Object value) {
		return "-D" + name + "=" + value;
	}
}
