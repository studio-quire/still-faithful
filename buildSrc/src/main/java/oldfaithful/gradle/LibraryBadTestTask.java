package oldfaithful.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/**
 * The harness on planted mistakes ({@code library/fixture-bad-*}, each on when {@code -Dfaithful.bad} names it): every rule must fire, and name the right mod. This is how the
 * harness itself is tested, so a run that goes green can be believed.
 */
public abstract class LibraryBadTestTask extends LibraryTestTask {
	private interface Run {
		void run() throws Exception;
	}

	private final List<String> missed = new ArrayList<>();

	private List<LibraryMod> only(String... ids) {
		List<LibraryMod> set = new ArrayList<>();
		for (String id : ids) {
			for (LibraryMod m : allMods) {
				if (m.id().equals(id)) set.add(m);
			}
		}
		return set;
	}

	private List<LibraryMod> allMods;

	/** {@code expected}: {@code <mod>|<rule>}, each of which has to be among the failures. */
	private void plant(String name, String mistakes, List<LibraryMod> mods, Run run, String... expected) throws Exception {
		getLogger().lifecycle("=== planted: {}", name);
		stage = name;
		failures.clear();
		boots.clear();
		subjects = mods;
		planted = mistakes;
		run.run();
		for (String e : expected) {
			String[] modRule = e.split("\\|");
			boolean seen = failures.stream().anyMatch(f -> f.mod().contains(modRule[0]) && f.rule().equals(modRule[1]));
			if (!seen) missed.add(name + ": " + e + "; reported: " + failures);
		}
		getLogger().lifecycle("{} failures reported", failures.size());
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		run = freshRunDir();
		allMods = new ArrayList<>();
		for (File jar : getSubjects().getFiles()) allMods.add(LibraryMod.read(jar.toPath()));
		allMods.sort(Comparator.comparing(LibraryMod::id));
		List<LibraryMod> both = only("fixture-bad-one", "fixture-bad-two");
		List<String> only = getStages().get();

		if (only.isEmpty() || only.contains("registry")) {
			plant("registry", "bad_name,no_lang,no_texture,split_atlas,lit_item,unsafe_stand_in,loose_block,loose_item,vanilla_command", both, () -> LibraryStages.boot(this, "all", subjects),
					"fixture-bad-one|names", "fixture-bad-one|lang", "fixture-bad-one|texture",
					"fixture-bad-one|tiles-in-one-atlas", "fixture-bad-one|item-stand-in", "fixture-bad-one|stand-in", "fixture-bad-one|unregistered-block", "fixture-bad-one|unregistered-item",
					"fixture-bad-one|command-shadows-vanilla");
			for (String twice : List.of("twice_block", "twice_channel", "twice_rule")) {
				plant(twice, twice, both, () -> LibraryStages.boot(this, "all", subjects), "fixture-bad-one|declared-twice");
			}
			plant("entity_stand_in", "entity_stand_in", both, () -> LibraryStages.boot(this, "all", subjects), "fixture-bad-one|stand-in");
			plant("twice_command", "twice_command", both, () -> LibraryStages.boot(this, "all", subjects), "fixture-bad-two|declared-twice");
		}
		if (only.isEmpty() || only.contains("combinations")) {
			plant("undeclared_dependency", "undeclared_dependency", both, () -> LibraryStages.boot(this, "alone-fixture-bad-two", only("fixture-bad-two")), "fixture-bad-two|failed-to-start");
			plant("selftest_fails", "selftest_fails", only("fixture-bad-one"), () -> new LibrarySelftestStage(this).run(), "fixture-bad-one|selftest");
			plant("selftest_silent", "selftest_silent", only("fixture-bad-one"), () -> new LibrarySelftestStage(this).run(), "fixture-bad-one|selftest-no-checks");
			plant("listener_throws", "listener_throws", both, () -> LibraryStages.boot(this, "all", subjects), "fixture-bad-one|listener-threw");
		}
		if (only.isEmpty() || only.contains("salts")) {
			plant("table_conflict", "table_conflict", both, () -> new LibraryWorldStages.Salts(this).tables(), "fixture-bad-one|two-mods-one-table-entry");
			plant("order_matters", "order_matters", both, () -> new LibraryWorldStages.Salts(this).orderSalts(), "fixture-bad|order-matters");
			plant("numbers", "number_in_nbt", only("fixture-bad-one"), () -> {
				Path arena = run.resolve("arena");
				if (Files.exists(arena)) deleteTree(arena);
				new LibraryWorldStages.Salts(this).numberSalt();
			}, "fixture-bad-one|number-in-nbt", "fixture-bad-one|number-in-static");
		}
		if (only.isEmpty() || only.contains("multiplayer")) {
			plant("channel", "greedy_channel", only("fixture-bad-one"), () -> new LibraryNetStage(this).fuzzOnly(), "fixture-bad-one|channel-handler-overran");
		}
		if (only.isEmpty() || only.contains("monkey")) {
			plant("monkey", "", only("fixture-bad-monkey"), () -> new LibraryNetStage.MonkeyStage(this).run(), "fixture-bad-monkey|ghost-block", "fixture-bad-monkey|inventory-differs",
					"fixture-bad-monkey|entity-differs", "fixture-bad-monkey|item-count-grew");
		}
		if (!missed.isEmpty()) throw new GradleException("The harness let these through:\n  " + String.join("\n  ", missed));
		getLogger().lifecycle("Every planted mistake was caught.");
	}

	private static void deleteTree(Path dir) throws java.io.IOException {
		try (var walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
		}
	}
}
