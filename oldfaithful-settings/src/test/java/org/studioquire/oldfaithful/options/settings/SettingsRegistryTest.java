package org.studioquire.oldfaithful.options.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsRegistryTest {
	private static String read(Path p) throws Exception {
		return new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
	}

	private static List<String> titles(List<SettingsRegistry.Group> groups) {
		List<String> titles = new ArrayList<>();
		for (SettingsRegistry.Group g : groups) titles.add(g.title + ":" + g.all().size());
		return titles;
	}

	@Test
	void sectionsInOrderAndOnlyThoseWithSomethingInThem(@TempDir Path dir) {
		SettingsRegistry r = new SettingsRegistry(dir);
		r.register("edition", "An Edition", s -> {
			s.fixes("b", "Second", 20);
			s.fixes("a", "First", 10);
			s.fixes("empty", "Nothing here", 5);
			s.fixes("a", "Declared again, ignored", 99);
			s.changes("c", "A Change", 1);
			s.add("b", Setting.fix("f.properties", "two", "Two", "."));
			s.add("a", Setting.fix("f.properties", "one", "One", "."));
			s.add("a", Setting.fix("f.properties", "one", "Twice", "."));
			s.add("c", Setting.onOff("f.properties", "three", "Three", ".", true));
		});
		r.seal();
		assertEquals(List.of("First:1", "Second:1"), titles(r.fixes()));
		assertEquals(List.of("A Change:1"), titles(r.changes()));
		assertEquals(List.of(), titles(r.mods()), "an edition's switches are in its menus, so it has no page of its own");
	}

	@Test
	void aModsSwitchesGoOnItsOwnPageByName(@TempDir Path dir) {
		SettingsRegistry r = new SettingsRegistry(dir);
		r.register("zed", "Zed", s -> s.add(Setting.onOff("z.properties", "a", "A", ".", true)));
		r.register("alpha", "alpha", s -> {
			s.add(Setting.onOff("a.properties", "a", "A", ".", true));
			s.add("nobody-declared", Setting.onOff("a.properties", "b", "B", ".", true));
		});
		r.seal();
		assertEquals(List.of("alpha:2", "Zed:1"), titles(r.mods()));
	}

	@Test
	void aPageGathersItsSwitchItsRulesAndItsOwnSettings(@TempDir Path dir) {
		SettingsRegistry r = new SettingsRegistry(dir);
		Setting.Store store = new Setting.Store() {
			@Override
			public String read() {
				return "true";
			}

			@Override
			public boolean write(String value) {
				return true;
			}
		};
		Setting enabled = Setting.stored("library.my-mod", "Enabled", "What it does.", false, store);
		r.register("the-library", "The Library", s -> {
			s.mod("my-mod", "My Mod");
			s.modSwitch("my-mod", enabled);
			s.mod("no-settings", "No Settings");
			s.modSwitch("no-settings", Setting.stored("library.no-settings", "Enabled", ".", false, store));
			s.mod("nothing", "Nothing There");
		});
		r.register("the-core", "The Core", s -> {
			s.mod("my-mod", "My Mod");
			s.addToMod("my-mod", Setting.stored("my-mod:rule", "A Rule", ".", false, store));
		});
		r.register("my-mod", "My Mod", s -> s.add(Setting.onOff("m.properties", "a", "A", ".", true)));
		r.seal();
		assertEquals(List.of("My Mod:3", "No Settings:1"), titles(r.mods()));
		SettingsRegistry.Group page = r.mods().get(0);
		assertEquals(enabled, page.all().get(0), "the switch comes first");
		assertEquals(2, page.settings.size());
	}

	@Test
	void stepsWriteTheFileAndWaitForARestart(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("fixes.properties");
		String original = "# header\n\n# explained\nrepeaters=true\n\n# more\nticks=TRUE\n";
		Files.write(file, original.getBytes(StandardCharsets.ISO_8859_1));
		SettingsRegistry r = new SettingsRegistry(dir);
		r.fixes("g", "G", 1);
		Setting repeaters = Setting.fix("fixes.properties", "repeaters", "Repeaters", ".").running(() -> "true");
		Setting ticks = Setting.fix("fixes.properties", "ticks", "Ticks", ".");
		Setting missing = Setting.fix("fixes.properties", "missing", "Missing", ".");
		r.add("g", repeaters);
		r.add("g", ticks);
		r.add("g", missing);
		r.seal();

		assertEquals("true", r.value(ticks), "read as Boolean.parseBoolean reads it");
		assertEquals("true", r.value(missing), "a missing fix is on, as the mod has it");
		assertFalse(r.anyWaitingForRestart());

		assertTrue(r.step(repeaters));
		assertEquals(original.replace("repeaters=true", "repeaters=false"), read(file));
		assertEquals("false", r.value(repeaters));
		assertTrue(r.waitingForRestart(repeaters));
		assertTrue(r.anyWaitingForRestart());
		assertTrue(r.step(repeaters));
		assertEquals(original, read(file));
		assertFalse(r.waitingForRestart(repeaters));

		// Without a running value of its own, the value first seen stands for what's running.
		r.step(ticks);
		assertTrue(r.waitingForRestart(ticks));
		r.step(missing);
		assertTrue(read(file).endsWith("missing=false\n"));
		assertTrue(r.waitingForRestart(missing));
	}

	@Test
	void handEditsAreReadOnReload(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("x.properties");
		Files.write(file, "a=true\n".getBytes(StandardCharsets.ISO_8859_1));
		SettingsRegistry r = new SettingsRegistry(dir);
		r.fixes("g", "G", 1);
		Setting a = Setting.onOff("x.properties", "a", "A", ".", true);
		r.add("g", a);
		r.seal();
		Files.write(file, "a=false\n".getBytes(StandardCharsets.ISO_8859_1));
		assertEquals("true", r.value(a));
		r.reload();
		assertEquals("false", r.value(a));
	}

	@Test
	void appliedAtOnceNeverWaits(@TempDir Path dir) throws Exception {
		SettingsRegistry r = new SettingsRegistry(dir);
		r.changes("g", "G", 1);
		AtomicReference<String> applied = new AtomicReference<>();
		Setting mouse = Setting.choice("d.properties", "mouse.input", "Mouse", ".", "system", "system", "System", "raw", "Raw")
				.applyNow(applied::set);
		r.add("g", mouse);
		r.seal();
		assertEquals("system", r.value(mouse), "no file: the default");
		r.step(mouse);
		assertEquals("raw", applied.get());
		assertEquals("mouse.input=raw\n", read(dir.resolve("d.properties")));
		assertFalse(r.waitingForRestart(mouse));
		r.step(mouse);
		assertEquals("system", applied.get());
	}

	@Test
	void partlyAtOnceWaitsOnlyWhereWhatsRunningDiffers(@TempDir Path dir) throws Exception {
		// Still Faithful's dusk: stepped and smooth switch at once, beta (no mixins) only after a restart.
		SettingsRegistry r = new SettingsRegistry(dir);
		r.changes("g", "G", 1);
		AtomicReference<String> live = new AtomicReference<>("stepped");
		Setting dusk = Setting.choice("p.properties", "sky.dusk", "Dusk", ".", "stepped", "stepped", "Stepped", "smooth", "Smooth", "beta", "Beta")
				.running(live::get)
				.applyNow(v -> {
					if (!v.equals("beta")) live.set(v);
				});
		r.add("g", dusk);
		r.seal();
		r.step(dusk);
		assertEquals("smooth", live.get());
		assertFalse(r.waitingForRestart(dusk));
		r.step(dusk);
		assertEquals("beta", r.value(dusk));
		assertTrue(r.waitingForRestart(dusk));
		r.step(dusk);
		assertEquals("stepped", live.get());
		assertFalse(r.waitingForRestart(dusk));
	}

	@Test
	void valuesReadAsTheOwningModReadsThem() {
		Setting fix = Setting.fix("f", "k", "K", ".");
		assertEquals("true", fix.normalize(" True "));
		assertEquals("false", fix.normalize("yes"), "Boolean.parseBoolean: anything but true is false");
		assertEquals("true", fix.normalize(null));
		assertEquals("Fixed", fix.word("true"));
		assertEquals("Beta", fix.word("false"));
		Setting mouse = Setting.choice("f", "mouse.input", "Mouse", ".", "system", "system", "System", "raw", "Raw");
		assertEquals("raw", mouse.normalize("RAW"));
		assertEquals("system", mouse.normalize("relative"), "anything but raw is the system's");
		Setting fullscreen = Setting.choice("f", "fullscreen.exclusive", "Fullscreen", ".", "false", "false", "Borderless", "true", "Exclusive");
		assertEquals("Exclusive", fullscreen.word(fullscreen.normalize("TRUE")));
		assertEquals("Borderless", fullscreen.word(fullscreen.normalize("on")));
	}
}
