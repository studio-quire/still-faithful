package org.studioquire.oldfaithful.options.settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Every switch the settings screen shows, in groups, and their values in the config files. Mods add theirs through
 * {@link SettingsProvider}; a group is shown only when something in it is. Values are read from the files each time a
 * screen opens (so edits by hand show), and a click writes the file at once.
 */
public final class SettingsRegistry {
	/** The fabric.mod.json entrypoint a mod lists its {@link SettingsProvider} under. */
	public static final String ENTRYPOINT = "oldfaithful-options:settings";
	private static final String MORE = "more";

	static final class Group {
		final String id;
		final String title;
		final int order;
		final String note;
		final List<Setting> settings = new ArrayList<>();

		Group(String id, String title, int order, String note) {
			this.id = id;
			this.title = title;
			this.order = order;
			this.note = note;
		}
	}

	private final Path configDir;
	private final Map<String, Group> groups = new LinkedHashMap<>();
	private final Map<String, String> texts = new HashMap<>();
	private final Map<String, Properties> parsed = new HashMap<>();
	/** For settings waiting on a restart without a {@code running} of their own: the value when first read. */
	private final Map<Setting, String> firstSeen = new HashMap<>();
	private boolean sealed;

	SettingsRegistry(Path configDir) {
		this.configDir = configDir;
	}

	/** Declares a group; the first declaration of an id stands, so two mods sharing one should name it alike. */
	public void group(String id, String title, int order) {
		group(id, title, order, null);
	}

	/** A group with a line shown on its page when no button is pointed at. */
	public void group(String id, String title, int order, String note) {
		if (sealed) throw new IllegalStateException("registered after the screen was built");
		groups.putIfAbsent(id, new Group(id, title, order, note));
	}

	public void add(Setting setting) {
		if (sealed) throw new IllegalStateException("registered after the screen was built");
		for (Group g : groups.values()) {
			for (Setting s : g.settings) {
				if (s.file.equals(setting.file) && s.key.equals(setting.key)) {
					System.out.println("[oldfaithful-options] WARN " + setting + " registered twice; keeping the first");
					return;
				}
			}
		}
		Group g = groups.get(setting.group);
		if (g == null) {
			groups.putIfAbsent(MORE, new Group(MORE, "More", 1000, null));
			g = groups.get(MORE);
		}
		g.settings.add(setting);
	}

	/** Called once the providers are done: reads the files and notes what's running. */
	void seal() {
		sealed = true;
		reload();
		for (Group g : groups.values()) {
			for (Setting s : g.settings) {
				if (s.applyNow == null && s.running == null) firstSeen.put(s, value(s));
			}
		}
	}

	/** The groups with something in them, in order. */
	List<Group> groups() {
		List<Group> shown = new ArrayList<>();
		for (Group g : groups.values()) {
			if (!g.settings.isEmpty()) shown.add(g);
		}
		shown.sort(Comparator.comparingInt(g -> g.order));
		return Collections.unmodifiableList(shown);
	}

	/** Reads every file again. */
	void reload() {
		texts.clear();
		parsed.clear();
		for (Group g : groups.values()) {
			for (Setting s : g.settings) {
				if (texts.containsKey(s.file)) continue;
				try {
					texts.put(s.file, ConfigFile.read(configDir.resolve(s.file)));
				} catch (IOException e) {
					System.out.println("[oldfaithful-options] WARN Could not read " + s.file + ": " + e);
					texts.put(s.file, "");
				}
			}
		}
	}

	/** The value as the owning mod would read it from the file now. */
	String value(Setting s) {
		Properties p = parsed.computeIfAbsent(s.file, f -> ConfigFile.parse(texts.getOrDefault(f, "")));
		return s.normalize(p.getProperty(s.key));
	}

	/** Whether the file now holds something the game will only take up after a restart. */
	boolean waitingForRestart(Setting s) {
		// With both, some changes apply at once and some wait: what's running tells which.
		if (s.applyNow != null && s.running == null) return false;
		String running = s.running != null ? s.normalize(s.running.get()) : firstSeen.get(s);
		return running != null && !running.equals(value(s));
	}

	boolean anyWaitingForRestart() {
		for (Group g : groups()) {
			for (Setting s : g.settings) {
				if (waitingForRestart(s)) return true;
			}
		}
		return false;
	}

	/** Steps the setting to its next value: written to its file, then applied if it takes effect at once. */
	boolean step(Setting s) {
		String next = s.next(value(s));
		String text = ConfigFile.set(texts.getOrDefault(s.file, ""), s.key, next);
		try {
			ConfigFile.write(configDir.resolve(s.file), text);
		} catch (IOException e) {
			System.out.println("[oldfaithful-options] WARN Could not save " + s + ": " + e);
			return false;
		}
		texts.put(s.file, text);
		parsed.remove(s.file);
		if (s.applyNow != null) {
			try {
				s.applyNow.accept(next);
			} catch (RuntimeException e) {
				System.out.println("[oldfaithful-options] WARN Saved " + s + " as " + next + " but could not apply it now: " + e);
			}
		}
		return true;
	}

	// ---- the game's registry ----------------------------------------------------------------------------------------

	private static SettingsRegistry instance;

	/** Built the first time a screen asks: this mod's own switches, then each mod's provider, by mod id. */
	public static SettingsRegistry get() {
		if (instance == null) {
			net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
			SettingsRegistry r = new SettingsRegistry(loader.getConfigDir());
			OwnSettings.register(r);
			List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<SettingsProvider>> providers = new ArrayList<>();
			try {
				providers.addAll(loader.getEntrypointContainers(ENTRYPOINT, SettingsProvider.class));
			} catch (RuntimeException e) {
				System.out.println("[oldfaithful-options] WARN Other mods' settings left out: " + e);
			}
			providers.sort(Comparator.comparing(c -> c.getProvider().getMetadata().getId()));
			for (net.fabricmc.loader.api.entrypoint.EntrypointContainer<SettingsProvider> c : providers) {
				try {
					c.getEntrypoint().register(r);
				} catch (RuntimeException | LinkageError e) {
					System.out.println("[oldfaithful-options] WARN " + c.getProvider().getMetadata().getId() + "'s settings left out: " + e);
				}
			}
			r.seal();
			instance = r;
		}
		return instance;
	}
}
