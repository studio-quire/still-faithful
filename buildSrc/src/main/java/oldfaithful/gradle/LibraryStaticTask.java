package oldfaithful.gradle;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * The conflict harness's first stage: {@link LibraryStatic} over the library's jars, with the editions' read for their mixin targets
 * (docs/library-harness.md). With {@code expected} set, the jars are a fixture of mistakes: each expected text must appear among the failures,
 * which is how the rules themselves are checked.
 */
public abstract class LibraryStaticTask extends DefaultTask {
	/** The merged named game jar, with its side marks. */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getGameJar();

	/** The library's named jars: every rule. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getLibrary();

	/** Mods' own tests, named: read for what each side's code reaches. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getSelftests();

	/** The editions' named jars: read for what they hook. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getEditions();

	@Input
	@Optional
	public abstract ListProperty<String> getExpected();

	/** With {@code expected}: what must not be among the failures. */
	@Input
	@Optional
	public abstract ListProperty<String> getAbsent();

	@OutputFile
	public abstract RegularFileProperty getReport();

	@TaskAction
	public void check() throws Exception {
		LibraryStatic check = new LibraryStatic(getGameJar().get().getAsFile().toPath());
		for (File jar : getLibrary().getFiles()) check.read(new LibraryStatic.Subject(jar.toPath(), true));
		for (File jar : getSelftests().getFiles()) check.read(new LibraryStatic.Subject(jar.toPath(), false, true));
		for (File jar : getEditions().getFiles()) check.read(new LibraryStatic.Subject(jar.toPath(), false));

		StringBuilder report = new StringBuilder();
		List<String> shared = new ArrayList<>();
		List<String> failures = check.finish(report, shared);
		Files.writeString(getReport().get().getAsFile().toPath(), report.toString(), StandardCharsets.UTF_8);

		List<String> expected = getExpected().getOrElse(List.of());
		if (!expected.isEmpty()) {
			StringBuilder missed = new StringBuilder();
			for (String e : expected) {
				if (failures.stream().noneMatch(f -> f.contains(e))) missed.append("\n  ").append(e);
			}
			if (missed.length() > 0) throw new GradleException("The static rules let these mistakes through:" + missed + "\nReported:\n  " + String.join("\n  ", failures));
			for (String a : getAbsent().getOrElse(List.of())) {
				for (String f : failures) {
					if (f.contains(a)) throw new GradleException("The static rules caught what isn't a mistake: " + f);
				}
			}
			getLogger().lifecycle("Every planted mistake was caught ({} failures).", failures.size());
			return;
		}
		if (!shared.isEmpty()) getLogger().lifecycle("Methods more than one mod changes, for information:\n  {}", String.join("\n  ", shared));
		getLogger().lifecycle("Library static check: {} failures. Report: {}", failures.size(), getReport().get().getAsFile());
		if (!failures.isEmpty()) {
			throw new GradleException("Library static check FAILED:\n  " + String.join("\n  ", failures.subList(0, Math.min(60, failures.size())))
					+ (failures.size() > 60 ? "\n  ... " + (failures.size() - 60) + " more" : ""));
		}
	}
}
