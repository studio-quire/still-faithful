package org.studioquire.oldfaithful.options.settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Every switch the settings screen shows, and their values in the config files. A mod's switches go on its own page in the Mods menu, unless it's an
 * edition's, which puts them in a section of the Fixes or Changes menu. Mods add theirs through {@link SettingsProvider}; anything with nothing in it isn't
 * shown. Values are read from the files each time a screen opens (so edits by hand show), and a click writes the file at once.
 */
public final class SettingsRegistry {
	/** The fabric.mod.json entrypoint a mod lists its {@link SettingsProvider} under. */
	public static final String ENTRYPOINT = "oldfaithful-options:settings";

	/** Settings shown together: a section of a menu, or a mod's page. */
	static final class Group {
		final String id;
		final String title;
		final int order;
		Supplier<String> note;
		/** On a mod's page: what turns the mod on or off, where something can. */
		Setting modSwitch;
		/** On a mod's page: a button beside the switch that does something rather than holding a value. */
		PageButton pageButton;
		final List<Setting> settings = new ArrayList<>();

		Group(String id, String title, int order, Supplier<String> note) {
			this.id = id;
			this.title = title;
			this.order = order;
			this.note = note;
		}

		List<Setting> all() {
			List<Setting> all = new ArrayList<>();
			if (modSwitch != null) all.add(modSwitch);
			all.addAll(settings);
			return all;
		}

		/** Whether it has a row at the top, of the switch and the page's button, when they're there. */
		boolean hasTopRow() {
			return modSwitch != null || pageButton != null;
		}
	}

	private final Path configDir;
	private final Map<String, Group> fixes = new LinkedHashMap<>();
	private final Map<String, Group> changes = new LinkedHashMap<>();
	private final Map<String, Group> mods = new LinkedHashMap<>();
	private final Map<String, String> texts = new HashMap<>();
	private final Map<String, Properties> parsed = new HashMap<>();
	/** For settings waiting on a restart without a {@code running} of their own: the value when first read. */
	private final Map<Setting, String> firstSeen = new HashMap<>();
	/** By mod id: why it has to stay as it is just now, or null when it needn't. */
	private final Map<String, Supplier<String>> holds = new HashMap<>();
	/** The mod whose provider is running, and so whose page {@link #add(Setting)} means. */
	private String owner, ownerName;
	private boolean sealed;

	SettingsRegistry(Path configDir) {
		this.configDir = configDir;
	}

	private void declare(Map<String, Group> where, String id, String title, int order, Supplier<String> note) {
		if (sealed) throw new IllegalStateException("registered after the screen was built");
		where.putIfAbsent(id, new Group(id, title, order, note));
	}

	/** Declares a section of the Fixes menu; the first declaration of an id stands, so two mods sharing one should name it alike. Sections go by {@code order}. */
	public void fixes(String id, String title, int order) {
		fixes(id, title, order, null);
	}

	/** A section with a line shown on its page when no button is pointed at. */
	public void fixes(String id, String title, int order, String note) {
		declare(fixes, id, title, order, note == null ? null : () -> note);
	}

	/** Declares a section of the Changes menu: what an edition does that isn't fixing beta's mistakes. As {@link #fixes}. */
	public void changes(String id, String title, int order) {
		changes(id, title, order, null);
	}

	public void changes(String id, String title, int order, String note) {
		declare(changes, id, title, order, note == null ? null : () -> note);
	}

	/** Declares a mod's page, for adding to a page that isn't the provider's own. */
	public void mod(String id, String name) {
		mod(id, name, null);
	}

	/** A page with a line, which may depend on the moment, shown when no button is pointed at. */
	public void mod(String id, String name, Supplier<String> note) {
		declare(mods, id, name, 0, note);
		// Whoever declares first, a page keeps the line: providers run by mod id.
		if (note != null) mods.get(id).note = note;
	}

	/** On the page of the mod this provider belongs to. */
	public void add(Setting setting) {
		if (owner == null) throw new IllegalStateException("no mod's provider is running: " + setting);
		mod(owner, ownerName);
		addToMod(owner, setting);
	}

	/** In a section declared by {@link #fixes} or {@link #changes}. */
	public void add(String section, Setting setting) {
		Group g = fixes.containsKey(section) ? fixes.get(section) : changes.get(section);
		if (g == null) {
			System.out.println("[oldfaithful-options] WARN " + setting + " names a section nobody declared: " + section);
			add(setting);
			return;
		}
		put(g, setting);
	}

	/** On a page declared by {@link #mod}, as the core does with each mod's rules. */
	public void addToMod(String id, Setting setting) {
		put(mods.get(id), setting);
	}

	/** What turns a mod on or off, first on its page. */
	public void modSwitch(String id, Setting setting) {
		if (sealed) throw new IllegalStateException("registered after the screen was built");
		mods.get(id).modSwitch = setting;
	}

	/** A button beside the mod's switch at the top of its page; one to a page, and the first stands. */
	public void addButtonToMod(String id, PageButton button) {
		if (sealed) throw new IllegalStateException("registered after the screen was built");
		Group g = mods.get(id);
		if (g.pageButton == null) g.pageButton = button;
		else System.out.println("[oldfaithful-options] WARN a second button for " + id + "'s page; keeping the first");
	}

	/**
	 * While {@code reason} gives one, nothing on the screen may switch this mod: not its own Enabled, nor another's whose going off would take it too.
	 * For whoever knows why, which needn't be whoever registers the switch: the core, for a world's locked rules.
	 */
	public void holdMod(String id, Supplier<String> reason) {
		if (sealed) throw new IllegalStateException("registered after the screen was built");
		holds.put(id, reason);
	}

	/** Why this mod is held just now; null if it isn't. */
	public String heldReason(String id) {
		Supplier<String> reason = holds.get(id);
		return reason == null ? null : reason.get();
	}

	private void put(Group group, Setting setting) {
		if (sealed) throw new IllegalStateException("registered after the screen was built");
		for (Group g : everything()) {
			for (Setting s : g.settings) {
				if (s.store == null && s.file.equals(setting.file) && s.key.equals(setting.key)) {
					System.out.println("[oldfaithful-options] WARN " + setting + " registered twice; keeping the first");
					return;
				}
			}
		}
		group.settings.add(setting);
	}

	/** Runs a mod's provider, for that mod's page. */
	void register(String modId, String modName, SettingsProvider provider) {
		owner = modId;
		ownerName = modName;
		try {
			provider.register(this);
		} finally {
			owner = null;
		}
	}

	/** Called once the providers are done: reads the files and notes what's running. */
	void seal() {
		sealed = true;
		reload();
		for (Group g : everything()) {
			for (Setting s : g.all()) {
				if (s.applyNow == null && s.running == null && s.store == null) firstSeen.put(s, value(s));
			}
		}
	}

	private List<Group> everything() {
		List<Group> all = new ArrayList<>(fixes.values());
		all.addAll(changes.values());
		all.addAll(mods.values());
		return all;
	}

	private static List<Group> sections(Map<String, Group> menu) {
		List<Group> shown = new ArrayList<>();
		for (Group g : menu.values()) {
			if (!g.settings.isEmpty()) shown.add(g);
		}
		shown.sort(Comparator.comparingInt(g -> g.order));
		return shown;
	}

	/** The Fixes menu's sections with something in them, in order. */
	List<Group> fixes() {
		return sections(fixes);
	}

	List<Group> changes() {
		return sections(changes);
	}

	/** The pages with something on them, by name. */
	List<Group> mods() {
		List<Group> shown = new ArrayList<>();
		for (Group g : mods.values()) {
			if (!g.all().isEmpty()) shown.add(g);
		}
		shown.sort(Comparator.comparing(g -> g.title, String.CASE_INSENSITIVE_ORDER));
		return shown;
	}

	/** Reads every file again. */
	void reload() {
		texts.clear();
		parsed.clear();
		for (Group g : everything()) {
			for (Setting s : g.all()) {
				if (s.store != null || texts.containsKey(s.file)) continue;
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
		if (s.store != null) return s.normalize(s.store.read());
		Properties p = parsed.computeIfAbsent(s.file, f -> ConfigFile.parse(texts.getOrDefault(f, "")));
		return s.normalize(p.getProperty(s.key));
	}

	/** Whether the file now holds something the game will only take up after a restart. */
	boolean waitingForRestart(Setting s) {
		if (s.store != null && s.running == null) return false;
		// With both, some changes apply at once and some wait: what's running tells which.
		if (s.applyNow != null && s.running == null) return false;
		String running = s.running != null ? s.normalize(s.running.get()) : firstSeen.get(s);
		return running != null && !running.equals(value(s));
	}

	boolean waitingForRestart(Group group) {
		for (Setting s : group.all()) {
			if (waitingForRestart(s)) return true;
		}
		return false;
	}

	boolean anyWaitingForRestart() {
		for (Group g : everything()) {
			if (waitingForRestart(g)) return true;
		}
		return false;
	}

	/** Steps the setting to its next value: written to its file, then applied if it takes effect at once. */
	boolean step(Setting s) {
		String next = s.next(value(s));
		if (s.store != null) return s.store.write(next);
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

	/** Built the first time a screen asks: each mod's provider, by mod id. */
	public static SettingsRegistry get() {
		if (instance == null) {
			net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
			SettingsRegistry r = new SettingsRegistry(loader.getConfigDir());
			List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<SettingsProvider>> providers = new ArrayList<>();
			try {
				providers.addAll(loader.getEntrypointContainers(ENTRYPOINT, SettingsProvider.class));
			} catch (RuntimeException e) {
				System.out.println("[oldfaithful-options] WARN Other mods' settings left out: " + e);
			}
			providers.sort(Comparator.comparing(c -> c.getProvider().getMetadata().getId()));
			for (net.fabricmc.loader.api.entrypoint.EntrypointContainer<SettingsProvider> c : providers) {
				net.fabricmc.loader.api.metadata.ModMetadata mod = c.getProvider().getMetadata();
				try {
					r.register(mod.getId(), mod.getName(), c.getEntrypoint());
				} catch (RuntimeException | LinkageError e) {
					System.out.println("[oldfaithful-options] WARN " + mod.getId() + "'s settings left out: " + e);
				}
			}
			r.seal();
			instance = r;
		}
		return instance;
	}
}
