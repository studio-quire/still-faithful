package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Shared parts of the headless game tests: a fresh run folder with our mods, the production launch (Fabric Loader
 * remapping the official jar to intermediary, exactly as a player's launcher does), and the log checks.
 */
public abstract class GameTestTask extends DefaultTask {
	private static final Pattern SELFTEST_DONE = Pattern.compile("\\[oldfaithful-selftest] DONE pass=(\\d+) fail=(\\d+)");

	/** The official game jar, unmodified. */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getGameJar();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getIntermediaryMappings();

	/** Fabric Loader and its libraries (Mixin, ASM). */
	@Classpath
	public abstract ConfigurableFileCollection getLoaderClasspath();

	/** Remapped mod jars to put in mods/. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getMods();

	@Internal
	public abstract DirectoryProperty getRunDir();

	/** Extra JVM arguments for the game process. */
	@Input
	public abstract org.gradle.api.provider.ListProperty<String> getExtraJvmArgs();

	/** The java executable to run the game with; defaults to the one running Gradle. */
	@Input
	public abstract Property<String> getJavaExecutable();

	public GameTestTask() {
		getOutputs().upToDateWhen(t -> false);
		getExtraJvmArgs().convention(List.of());
		getJavaExecutable().convention(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath());
	}

	/** Recreates the run folder with only our mods in it. */
	protected Path freshRunDir() throws IOException {
		Path run = getRunDir().get().getAsFile().toPath();
		// Only ever wipe a folder of our own under local/run.
		if (!run.toString().replace('\\', '/').contains("/local/run/")) throw new GradleException("Unexpected run dir " + run);
		if (Files.exists(run)) {
			try (Stream<Path> walk = Files.walk(run)) {
				for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
			}
		}
		Path mods = Files.createDirectories(run.resolve("mods"));
		for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), mods.resolve(mod.getName()));
		return run;
	}

	protected List<String> javaCommand(String mainClass, List<String> jvmArgs, List<String> args) {
		List<String> classpath = new ArrayList<>();
		for (File f : getLoaderClasspath().getFiles()) classpath.add(f.getAbsolutePath());
		classpath.add(getIntermediaryMappings().get().getAsFile().getAbsolutePath());
		classpath.add(getGameJar().get().getAsFile().getAbsolutePath());

		List<String> command = new ArrayList<>();
		command.add(getJavaExecutable().get());
		// Report any injector that found nothing, including require 0 ones (the speed mod's) and MixinExtras'.
		command.add("-Dmixin.debug.countInjections=true");
		command.addAll(jvmArgs);
		command.addAll(getExtraJvmArgs().get());
		command.add("-cp");
		command.add(String.join(File.pathSeparator, classpath));
		command.add(mainClass);
		command.addAll(args);
		return command;
	}

	/**
	 * Problems in the output that point at our mods or at Mixin: self-test failures, our own warnings (the speed mod's
	 * injections are require 0 and report one that didn't apply this way), Mixin errors
	 * and stack frames in our code.
	 */
	/** A skin or sound a server didn't send because the network didn't answer, not the game. Any other failure still counts. */
	protected static boolean offline(String line) {
		return (line.contains("Skin or cape not loaded") || line.contains("Sounds not checked or downloaded") || line.matches(".*Beta's \\S+ not fetched from .*"))
				&& (line.contains("SocketTimeoutException") || line.contains("ConnectException") || line.contains("UnknownHostException"));
	}

	protected static List<String> problems(List<String> lines) {
		List<String> problems = new ArrayList<>();
		for (String line : lines) {
			if (offline(line)) continue;
			String l = line.toLowerCase(Locale.ROOT);
			boolean mixinError = l.contains("mixin") && (l.contains("error") || l.contains("exception") || l.contains("failed")
					|| l.contains("could not") || l.contains("critical"));
			if (line.contains("[oldfaithful-selftest] FAIL") || line.contains("[purefaithful-hardening] WARN") || line.contains("[purefaithful-speed] WARN") || mixinError
					|| l.contains("at org.studioquire.") || l.contains("injection")) {
				problems.add(line);
			}
		}
		return problems;
	}

	/** Lines worth a look even if they aren't ours: anything that reads like an error or a warning. */
	protected static List<String> notable(List<String> lines) {
		List<String> notable = new ArrayList<>();
		for (String line : lines) {
			String l = line.toLowerCase(Locale.ROOT);
			if (l.contains("exception") || l.contains("error") || l.contains("severe") || l.contains("warn")) notable.add(line);
		}
		return notable;
	}

	/** Checks the self-test's summary line: it ran, and nothing failed. */
	protected static void checkSelfTest(List<String> lines, List<String> failures) {
		for (String line : lines) {
			Matcher m = SELFTEST_DONE.matcher(line);
			if (m.find()) {
				if (Integer.parseInt(m.group(1)) == 0) failures.add("self-test ran no checks");
				if (Integer.parseInt(m.group(2)) != 0) failures.add("self-test failures: " + m.group(2));
				return;
			}
		}
		failures.add("self-test didn't run (no DONE line)");
	}

	protected void report(String title, List<String> lines, List<String> failures) {
		getLogger().lifecycle("{}: {} lines of output", title, lines.size());
		for (String line : lines) {
			if (line.contains("[oldfaithful-selftest]") || line.contains("[purefaithful-hardening]")) getLogger().lifecycle("  {}", line);
		}
		List<String> notable = notable(lines);
		if (!notable.isEmpty()) {
			getLogger().lifecycle("Other notable lines:");
			notable.stream().limit(40).forEach(l -> getLogger().lifecycle("  {}", l));
		}
		if (!failures.isEmpty()) {
			throw new GradleException(title + " FAILED:\n  " + String.join("\n  ", failures)
					+ "\nFull output: " + getRunDir().get().getAsFile());
		}
		getLogger().lifecycle("{}: PASSED", title);
	}
}
