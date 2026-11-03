package org.studioquire.oldfaithful.options.settings;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One switch in Old Faithful's settings screen: a key in one of the config files, the words its button shows, and when
 * a change takes effect. Made by the mod that owns the file ({@link SettingsProvider}); the screen reads and writes the
 * file, keeping everything else in it as it was.
 */
public final class Setting {
	final String group;
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

	private Setting(String group, String file, String key, String label, String hint, String[] values, String[] words, String defaultValue,
			String fallback) {
		for (String v : values) {
			if (!v.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("A value the file can't hold plainly: " + v);
		}
		this.group = group;
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
	public static Setting fix(String group, String file, String key, String label, String hint) {
		return new Setting(group, file, key, label, hint, new String[]{"true", "false"}, new String[]{"Fixed", "Beta"}, "true", "false");
	}

	/** ON or OFF, as beta's own options say it. Anything but {@code true} reads as off ({@link Boolean#parseBoolean}). */
	public static Setting onOff(String group, String file, String key, String label, String hint, boolean on) {
		return new Setting(group, file, key, label, hint, new String[]{"true", "false"}, new String[]{"ON", "OFF"}, String.valueOf(on), "false");
	}

	/**
	 * A choice between named values, given as value, word, value, word... The first value is also what the owning mod
	 * reads anything it doesn't recognise as.
	 */
	public static Setting choice(String group, String file, String key, String label, String hint, String defaultValue, String... valuesAndWords) {
		if (valuesAndWords.length < 4 || valuesAndWords.length % 2 != 0) throw new IllegalArgumentException("value, word pairs: " + key);
		String[] values = new String[valuesAndWords.length / 2], words = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			values[i] = valuesAndWords[2 * i];
			words[i] = valuesAndWords[2 * i + 1];
		}
		return new Setting(group, file, key, label, hint, values, words, defaultValue, values[0]);
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

	@Override
	public String toString() {
		return file + " " + key + " (" + label.toLowerCase(Locale.ROOT) + ")";
	}
}
