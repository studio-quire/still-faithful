package org.studioquire.oldfaithful.options.settings;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One switch on the settings screen: a key in one of the config files, the words its button shows, and when a change
 * takes effect. Made by the mod that owns the file ({@link SettingsProvider}), which says where it goes as it adds it to
 * the {@link SettingsRegistry}; the screen reads and writes the file, keeping everything else in it as it was.
 */
public final class Setting {
	final String file;
	final String key;
	final String label;
	final String hint;
	/** What the file holds, and the words the button shows for each, in the order a click steps through them. */
	final String[] values;
	final String[] words;
	/** What the owning mod uses when the key is missing. */
	final String defaultValue;
	/** What the owning mod reads a value it doesn't recognise as. */
	final String fallback;

	boolean serverDecides;
	Consumer<String> applyNow;
	Supplier<String> running;
	/** Where the value lives when it isn't a config file's (a world rule's); null otherwise. */
	Store store;
	/** Says why the button can't be pressed just now, or null when it can. */
	Supplier<String> locked;

	/** A value kept somewhere other than a config file. {@code read} may return null: nothing to show here. */
	public interface Store {
		String read();

		/** Takes the new value; false if it couldn't be kept. */
		boolean write(String value);

		/** What the last write did beyond this setting, for the screen to say; other settings' buttons are read again after it. */
		default String said() {
			return null;
		}
	}

	private Setting(String file, String key, String label, String hint, String[] values, String[] words, String defaultValue,
			String fallback) {
		for (String v : values) {
			if (!v.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("A value the file can't hold plainly: " + v);
		}
		this.file = file;
		this.key = key;
		this.label = label;
		this.hint = hint;
		this.values = values;
		this.words = words;
		this.defaultValue = defaultValue;
		this.fallback = fallback;
	}

	/** A fix of beta's: {@code true} shows as Fixed, {@code false} as Beta. On by default, as the fix mods have them. */
	public static Setting fix(String file, String key, String label, String hint) {
		return new Setting(file, key, label, hint, new String[]{"true", "false"}, new String[]{"Fixed", "Beta"}, "true", "false");
	}

	/** ON or OFF, as beta's own options say it. Anything but {@code true} reads as off ({@link Boolean#parseBoolean}). */
	public static Setting onOff(String file, String key, String label, String hint, boolean on) {
		return new Setting(file, key, label, hint, new String[]{"true", "false"}, new String[]{"ON", "OFF"}, String.valueOf(on), "false");
	}

	/**
	 * A choice between named values, given as value, word, value, word... The first value is also what the owning mod
	 * reads anything it doesn't recognise as.
	 */
	public static Setting choice(String file, String key, String label, String hint, String defaultValue, String... valuesAndWords) {
		if (valuesAndWords.length < 4 || valuesAndWords.length % 2 != 0) throw new IllegalArgumentException("value, word pairs: " + key);
		String[] values = new String[valuesAndWords.length / 2], words = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			values[i] = valuesAndWords[2 * i];
			words[i] = valuesAndWords[2 * i + 1];
		}
		return new Setting(file, key, label, hint, values, words, defaultValue, values[0]);
	}

	/** ON or OFF, kept in a {@link Store}: takes effect at once, whatever the store does with it. */
	public static Setting stored(String id, String label, String hint, boolean on, Store store) {
		Setting s = new Setting("", id, label, hint, new String[]{"true", "false"}, new String[]{"ON", "OFF"}, String.valueOf(on), "false");
		s.store = store;
		return s;
	}

	/** A choice, as {@link #choice}, kept in a {@link Store}: takes effect at once, whatever the store does with it. */
	public static Setting stored(String id, String label, String hint, String defaultValue, Store store, String... valuesAndWords) {
		Setting s = choice("", id, label, hint, defaultValue, valuesAndWords);
		s.store = store;
		return s;
	}

	/** When this gives a reason, the button can't be pressed, and the reason shows as its hint. */
	public Setting lockedWhen(Supplier<String> reason) {
		this.locked = reason;
		return this;
	}

	/**
	 * Decided where the world runs: in single player by this game, on a server by the server's own setting. In
	 * multiplayer the button says so and can't be changed there.
	 */
	public Setting serverDecides() {
		this.serverDecides = true;
		return this;
	}

	/** Takes effect at once: called with the new value after it's saved. Without this, a change waits for a restart. */
	public Setting applyNow(Consumer<String> apply) {
		this.applyNow = apply;
		return this;
	}

	/**
	 * For a setting that waits for a restart: the value the game is running with, so the button can show a change is
	 * waiting. Without this, the value the file held when the screen was first opened stands for it. With
	 * {@link #applyNow} too, for one that takes some changes at once and waits for others: a change waits while this
	 * still differs from the file after applyNow has run.
	 */
	public Setting running(Supplier<String> running) {
		this.running = running;
		return this;
	}

	/** The value as the owning mod reads it: one of {@link #values}. */
	String normalize(String raw) {
		if (raw == null) return defaultValue;
		String v = raw.trim();
		for (String known : values) {
			if (known.equalsIgnoreCase(v)) return known;
		}
		return fallback;
	}

	String word(String value) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(value)) return words[i];
		}
		return value;
	}

	String next(String value) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(value)) return values[(i + 1) % values.length];
		}
		return values[0];
	}

	String lockedReason() {
		return locked == null ? null : locked.get();
	}

	@Override
	public String toString() {
		return file + " " + key + " (" + label.toLowerCase(Locale.ROOT) + ")";
	}
}
