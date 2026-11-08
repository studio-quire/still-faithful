package org.studioquire.oldfaithful.settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Which of a library pack's mods are switched on: every jar sits in {@code mods-library/}, and the launch reads
 * {@code mods-library/selection.txt} ({@code -Dfabric.addMods=@mods-library/selection.txt}; a path is relative to the game
 * folder, where Prism starts the game). Jars in the folder have no version in their names, so a pack update that replaces one leaves
 * the list right. A line whose jar has gone anyway (replaced by hand under a versioned name) can't be mended for the launch that finds it, which the loader
 * has already read the list for: it's repointed for the next one, and {@link #notice} tells the player. A change takes effect at the next launch.
 * Faithful Core follows the rest: on while anything is. A list the loader would stop on is never written: a mod comes on with what it
 * {@code depends} on, and a mod going off takes what depends on it. Only names ever come from elsewhere; nothing here fetches anything.
 */
public final class LibrarySelection {
	public static final String FOLDER = "mods-library";
	private static final String LIST = "selection.txt";
	private static final String CORE = "faithful-core";
	private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
	/** What fits the settings screen's two lines of hint, in the game's font, with room for how the words fall. */
	private static final int HINT_LETTERS = 96;
	private static final Pattern DESCRIPTION = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+?)\\.");
	private static final Pattern DEPENDS = Pattern.compile("\"depends\"\\s*:\\s*\\{([^}]*)\\}");
	private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");
	private static final Pattern MUST_MATCH = Pattern.compile("\"(content|match)\"\\s*:\\s*true");

	/** Said on the title screen of the launch that found a stale list; null if there's nothing to say. */
	private static volatile String notice;

	/** One jar in the folder. */
	public static final class Entry {
		public final String id;
		public final String name;
		public final String description;
		final String path;
		final Set<String> depends;
		/** Content, or {@code "match"}: a server decides whether this one is on. */
		final boolean mustMatch;

		Entry(String id, String name, String description, String path, Set<String> depends, boolean mustMatch) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.path = path;
			this.depends = depends;
			this.mustMatch = mustMatch;
		}
	}

	/** What a change did, beyond what was asked. */
	public static final class Change {
		/** False if the list couldn't be saved, and nothing changed. */
		public final boolean saved;
		public final List<String> on = new ArrayList<>(), off = new ArrayList<>();
		/** Mods a server has that the folder doesn't. */
		public final List<String> absent = new ArrayList<>();

		Change(boolean saved) {
			this.saved = saved;
		}

		/** For a screen: what else moved. Null if nothing did. */
		public String also() {
			String turned = on.isEmpty() ? "" : "Also on: " + String.join(", ", on) + ". ";
			if (!off.isEmpty()) turned += "Also off: " + String.join(", ", off) + ".";
			return turned.isEmpty() ? null : turned.trim();
		}
	}

	private final Path folder;
	private final List<Entry> entries = new ArrayList<>();
	private Entry core;

	public LibrarySelection(Path gameDir) {
		folder = gameDir.resolve(FOLDER);
		if (!Files.isDirectory(folder)) return;
		try (DirectoryStream<Path> jars = Files.newDirectoryStream(folder, "*.jar")) {
			for (Path jar : jars) {
				Entry e = read(jar);
				if (e == null) continue;
				if (e.id.equals(CORE)) core = e;
				else entries.add(e);
			}
		} catch (IOException e) {
			System.out.println("[oldfaithful-settings] WARN Could not read " + folder + ": " + e);
		}
		entries.sort(Comparator.comparing(e -> e.name));
		repoint();
	}

	/** A line with no jar goes to the jar whose mod it's named for: {@code mods-library/world-rules-0.1.0.jar}, the id and a version, as the build names them. */
	private void repoint() {
		Set<String> lines = selected(), mended = new LinkedHashSet<>();
		List<Entry> all = new ArrayList<>(entries);
		if (core != null) all.add(core);
		boolean changed = false;
		List<String> gone = new ArrayList<>(), moved = new ArrayList<>();
		for (String line : lines) {
			String kept = line;
			if (!Files.exists(folder.getParent().resolve(line))) {
				String file = line.substring(line.lastIndexOf('/') + 1);
				Entry match = null;
				for (Entry e : all) {
					if (file.matches(Pattern.quote(e.id) + "(-\\d.*)?\\.jar")) match = e;
				}
				if (match != null) {
					kept = match.path;
					changed = true;
					moved.add(match.name);
					System.out.println("[oldfaithful-settings] " + line + " is gone: the library's list now names " + kept + ", from the next launch");
				} else {
					gone.add(file);
					changed = true;
					System.out.println("[oldfaithful-settings] WARN The library's list named " + line + ", which isn't there: it's off the list");
					continue;
				}
			}
			mended.add(kept);
		}
		if (changed) save(mended);
		if (!moved.isEmpty()) notice = "Not loaded, its jar renamed: " + brief(moved) + ". Restart the game to load it.";
		else if (!gone.isEmpty()) notice = "Not loaded, its jar gone: " + brief(gone) + ".";
	}

	private static String brief(List<String> names) {
		return names.size() <= 3 ? String.join(", ", names) : names.get(0) + " and " + (names.size() - 1) + " more";
	}

	/** For the title screen. */
	public static String notice() {
		return notice;
	}

	private static Entry read(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry json = zip.getEntry("fabric.mod.json");
			if (json == null) return null;
			String text;
			try (InputStream in = zip.getInputStream(json)) {
				text = new String(readAll(in), StandardCharsets.UTF_8);
			}
			Matcher id = ID.matcher(text), name = NAME.matcher(text), description = DESCRIPTION.matcher(text), depends = DEPENDS.matcher(text);
			if (!id.find()) return null;
			Set<String> needs = new LinkedHashSet<>();
			if (depends.find()) {
				for (Matcher key = KEY.matcher(depends.group(1)); key.find(); ) needs.add(key.group(1));
			}
			return new Entry(id.group(1), name.find() ? name.group(1) : id.group(1), description.find() ? hint(description.group(1) + ".") : "", FOLDER + "/" + jar.getFileName(),
					needs, MUST_MATCH.matcher(text).find());
		} catch (IOException e) {
			System.out.println("[oldfaithful-settings] WARN Could not read " + jar + ": " + e);
			return null;
		}
	}

	/** A mod's first sentence, cut at a word if it would run past the hint's two lines. */
	static String hint(String sentence) {
		if (sentence.length() <= HINT_LETTERS) return sentence;
		String cut = sentence.substring(0, sentence.lastIndexOf(' ', HINT_LETTERS - 3));
		return cut.replaceAll("[,;:]$", "") + "...";
	}

	private static byte[] readAll(InputStream in) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) out.write(buffer, 0, n);
		return out.toByteArray();
	}

	/** The mods to choose from; the core isn't one, it follows them. */
	public List<Entry> entries() {
		return entries;
	}

	private Set<String> selected() {
		Set<String> lines = new LinkedHashSet<>();
		try {
			Path list = folder.resolve(LIST);
			if (Files.isRegularFile(list)) {
				for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
					if (!line.trim().isEmpty()) lines.add(line.trim());
				}
			}
		} catch (IOException e) {
			System.out.println("[oldfaithful-settings] WARN Could not read the library's selection: " + e);
		}
		return lines;
	}

	public boolean isOn(Entry entry) {
		return selected().contains(entry.path);
	}

	private Entry find(String id) {
		for (Entry e : entries) {
			if (e.id.equals(id)) return e;
		}
		return null;
	}

	private Set<Entry> chosen() {
		Set<String> lines = selected();
		Set<Entry> chosen = new LinkedHashSet<>();
		for (Entry e : entries) {
			if (lines.contains(e.path)) chosen.add(e);
		}
		return chosen;
	}

	/** {@code entry} and everything it depends on in the folder. */
	private Set<Entry> withNeeds(Entry entry) {
		Set<Entry> found = new LinkedHashSet<>();
		List<Entry> waiting = new ArrayList<>();
		waiting.add(entry);
		while (!waiting.isEmpty()) {
			Entry e = waiting.remove(waiting.size() - 1);
			if (!found.add(e)) continue;
			for (String id : e.depends) {
				Entry needed = find(id);
				if (needed != null) waiting.add(needed);
			}
		}
		return found;
	}

	/** {@code entry} and everything that depends on it, near or far. */
	private Set<Entry> withDependents(Entry entry) {
		Set<Entry> found = new LinkedHashSet<>();
		for (Entry e : entries) {
			if (withNeeds(e).contains(entry)) found.add(e);
		}
		return found;
	}

	/** Why {@code entry}'s switch can't be pressed: what {@code held} says of it, or of anything its going off would take. Null if nothing's held. */
	public String heldBy(Entry entry, Function<String, String> held) {
		for (Entry e : withDependents(entry)) {
			String reason = held.apply(e.id);
			if (reason != null) return reason;
		}
		return null;
	}

	/** Chooses a mod or not, for the next launch, along with whatever that takes. */
	public Change set(Entry entry, boolean on) {
		Set<Entry> wanted = chosen();
		if (on) wanted.addAll(withNeeds(entry));
		else wanted.removeAll(withDependents(entry));
		Change change = write(wanted);
		change.on.remove(entry.name);
		change.off.remove(entry.name);
		return change;
	}

	/**
	 * A server's set, by the ids in its hello that must match: those on, every other mod that must match off. Mods that needn't match stay as the
	 * player had them, unless what one depends on just went.
	 */
	public Change useServerSet(Iterable<String> mustMatch) {
		Set<Entry> wanted = chosen();
		wanted.removeIf(e -> e.mustMatch);
		List<String> absent = new ArrayList<>();
		for (String id : mustMatch) {
			Entry e = find(id);
			if (e != null) wanted.addAll(withNeeds(e));
			else absent.add(id);
		}
		for (Entry e : new ArrayList<>(wanted)) {
			if (!wanted.containsAll(withNeeds(e))) wanted.remove(e);
		}
		Change change = write(wanted);
		change.absent.addAll(absent);
		return change;
	}

	private Change write(Set<Entry> wanted) {
		Set<String> lines = selected();
		Set<Entry> before = chosen();
		if (core != null) lines.remove(core.path);
		for (Entry e : entries) {
			if (wanted.contains(e)) lines.add(e.path);
			else lines.remove(e.path);
		}
		if (core != null && !lines.isEmpty()) lines.add(core.path);
		Change change = new Change(save(lines));
		if (!change.saved) return change;
		for (Entry e : entries) {
			if (wanted.contains(e) && !before.contains(e)) change.on.add(e.name);
			if (!wanted.contains(e) && before.contains(e)) change.off.add(e.name);
		}
		return change;
	}

	private boolean save(Set<String> lines) {
		Path temporary = folder.resolve(LIST + ".tmp");
		try {
			Files.write(temporary, lines, StandardCharsets.UTF_8);
			Files.move(temporary, folder.resolve(LIST), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			System.out.println("[oldfaithful-settings] WARN Could not save the library's selection: " + e);
			return false;
		}
	}
}
