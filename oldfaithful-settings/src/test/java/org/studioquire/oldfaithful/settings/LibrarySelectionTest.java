package org.studioquire.oldfaithful.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibrarySelectionTest {
	private static void jar(Path file, String id) throws IOException {
		jar(file, id, "");
	}

	/** {@code more}: the rest of the file, from a comma. */
	private static void jar(Path file, String id, String more) throws IOException {
		try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"id\": \"" + id + "\", \"name\": \"" + id + "\", \"description\": \"What it does. More.\"" + more + "}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}

	private static String needs(String... ids) {
		StringBuilder s = new StringBuilder(", \"depends\": {\"faithful-core\": \"*\"");
		for (String id : ids) s.append(", \"").append(id).append("\": \"*\"");
		return s + "}";
	}

	private static final String CONTENT = ", \"custom\": {\"faithful\": {\"content\": true, \"network\": 1}}";

	/** materials <- tools <- armoury, and a client-side map that needs materials' colours; a lone content mod; a lone map. */
	private static Path chain(Path game) throws IOException {
		Path folder = Files.createDirectories(game.resolve("mods-library"));
		jar(folder.resolve("faithful-core.jar"), "faithful-core");
		jar(folder.resolve("materials.jar"), "materials", needs() + CONTENT);
		jar(folder.resolve("tools.jar"), "tools", needs("materials") + CONTENT);
		jar(folder.resolve("armoury.jar"), "armoury", needs("tools") + CONTENT);
		jar(folder.resolve("ore-map.jar"), "ore-map", needs("materials"));
		jar(folder.resolve("farm.jar"), "farm", needs() + CONTENT);
		jar(folder.resolve("minimap.jar"), "minimap", needs());
		return folder;
	}

	private static LibrarySelection.Entry entry(LibrarySelection library, String id) {
		return library.entries().stream().filter(e -> e.id.equals(id)).findFirst().get();
	}

	private static List<String> on(LibrarySelection library) {
		return library.entries().stream().filter(library::isOn).map(e -> e.id).collect(java.util.stream.Collectors.toList());
	}

	private static List<String> list(Path game) throws IOException {
		return Files.readAllLines(game.resolve("mods-library").resolve("selection.txt"));
	}

	@Test
	void aLongFirstSentenceIsCutToAHintAtAWord() {
		assertEquals("What it does.", LibrarySelection.hint("What it does."));
		String cut = LibrarySelection.hint("Word, ".repeat(40).trim() + ".");
		assertTrue(cut.length() <= 96 && cut.endsWith("Word..."), cut);
	}

	@Test
	void theCoreFollowsTheRest(@TempDir Path game) throws IOException {
		Path folder = Files.createDirectories(game.resolve("mods-library"));
		jar(folder.resolve("faithful-core.jar"), "faithful-core");
		jar(folder.resolve("world-rules.jar"), "world-rules");
		LibrarySelection library = new LibrarySelection(game);
		assertEquals(1, library.entries().size());
		LibrarySelection.Entry rules = library.entries().get(0);
		assertEquals("What it does.", rules.description);
		assertFalse(library.isOn(rules));
		assertTrue(library.set(rules, true).saved);
		assertEquals(List.of("mods-library/world-rules.jar", "mods-library/faithful-core.jar"), list(game));
		assertTrue(library.set(rules, false).saved);
		assertEquals(List.of(), list(game));
	}

	@Test
	void aModComesOnWithWhatItDependsOn(@TempDir Path game) throws IOException {
		chain(game);
		LibrarySelection library = new LibrarySelection(game);
		LibrarySelection.Change change = library.set(entry(library, "armoury"), true);
		assertEquals(List.of("armoury", "materials", "tools"), on(library));
		assertEquals(List.of("materials", "tools"), change.on);
		assertEquals("Also on: materials, tools.", change.also());
		assertTrue(list(game).contains("mods-library/faithful-core.jar"));
		assertEquals(null, library.set(entry(library, "farm"), true).also());
	}

	@Test
	void aHeldModHoldsWhatItDependsOnButNotWhatDependsOnIt(@TempDir Path game) throws IOException {
		chain(game);
		LibrarySelection library = new LibrarySelection(game);
		java.util.function.Function<String, String> holdingTools = id -> id.equals("tools") ? "Held." : null;
		assertEquals("Held.", library.heldBy(entry(library, "tools"), holdingTools), "its own switch");
		assertEquals("Held.", library.heldBy(entry(library, "materials"), holdingTools), "going off would take it");
		assertEquals(null, library.heldBy(entry(library, "armoury"), holdingTools), "going off leaves it");
		assertEquals(null, library.heldBy(entry(library, "ore-map"), holdingTools), "a neighbour on the same dependency");
		assertEquals(null, library.heldBy(entry(library, "farm"), holdingTools), "nothing to do with it");
		assertEquals(null, library.heldBy(entry(library, "tools"), id -> null), "nothing held");
	}

	@Test
	void aDependencyGoingOffTakesWhatNeedsIt(@TempDir Path game) throws IOException {
		chain(game);
		LibrarySelection library = new LibrarySelection(game);
		library.set(entry(library, "armoury"), true);
		library.set(entry(library, "ore-map"), true);
		library.set(entry(library, "minimap"), true);
		LibrarySelection.Change change = library.set(entry(library, "materials"), false);
		assertEquals(List.of("minimap"), on(library));
		assertEquals(List.of("armoury", "ore-map", "tools"), change.off);
		assertEquals("Also off: armoury, ore-map, tools.", change.also());
		library.set(entry(library, "minimap"), false);
		assertEquals(List.of(), list(game));
	}

	@Test
	void aServersSetLeavesOneSidedModsAlone(@TempDir Path game) throws IOException {
		chain(game);
		LibrarySelection library = new LibrarySelection(game);
		library.set(entry(library, "farm"), true);
		library.set(entry(library, "minimap"), true);
		LibrarySelection.Change change = library.useServerSet(List.of("tools", "strange-mod"));
		assertEquals(List.of("materials", "minimap", "tools"), on(library));
		assertEquals(List.of("materials", "tools"), change.on);
		assertEquals(List.of("farm"), change.off);
		assertEquals(List.of("strange-mod"), change.absent);
	}

	@Test
	void aServersSetTakesAOneSidedModWhoseDependencyWent(@TempDir Path game) throws IOException {
		chain(game);
		LibrarySelection library = new LibrarySelection(game);
		library.set(entry(library, "ore-map"), true);
		library.set(entry(library, "minimap"), true);
		library.useServerSet(List.of("farm"));
		assertEquals(List.of("farm", "minimap"), on(library));
	}

	@Test
	void aLineWhoseJarWasRenamedFollowsItsModId(@TempDir Path game) throws IOException {
		Path folder = Files.createDirectories(game.resolve("mods-library"));
		jar(folder.resolve("faithful-core-0.2.0.jar"), "faithful-core");
		jar(folder.resolve("world-rules-0.2.0.jar"), "world-rules");
		Files.write(folder.resolve("selection.txt"), List.of("mods-library/world-rules-0.1.0.jar", "mods-library/faithful-core-0.1.0.jar", "mods-library/gone-1.0.jar"));
		new LibrarySelection(game);
		assertEquals(List.of("mods-library/world-rules-0.2.0.jar", "mods-library/faithful-core-0.2.0.jar"), list(game));
		assertTrue(LibrarySelection.notice().contains("world-rules"));
	}

	@Test
	void aNameOnlyLikeAModsIsntItsJar(@TempDir Path game) throws IOException {
		Path folder = Files.createDirectories(game.resolve("mods-library"));
		jar(folder.resolve("world.jar"), "world");
		Files.write(folder.resolve("selection.txt"), List.of("mods-library/world-rules-0.1.0.jar"));
		new LibrarySelection(game);
		assertEquals(List.of(), list(game));
		assertTrue(LibrarySelection.notice().contains("world-rules-0.1.0.jar"));
	}
}
