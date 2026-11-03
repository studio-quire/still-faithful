package org.studioquire.oldfaithful.options.settings;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Our config files as the screen edits them: a value changed in place, every other byte of the file (the explanations,
 * other settings, the order, the line ends) kept as it was. The files are java.util.Properties files in ISO-8859-1,
 * as the mods read them, so the text is read as ISO-8859-1 too and nothing is lost whatever it holds.
 */
final class ConfigFile {
	private ConfigFile() {
	}

	/** The file's text, or "" if it doesn't exist. */
	static String read(Path file) throws IOException {
		return Files.isRegularFile(file) ? new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1) : "";
	}

	/** Written whole or not at all: to a file beside it, then moved over it. */
	static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.write(temp, text.getBytes(StandardCharsets.ISO_8859_1));
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** What the owning mod reads from the text (it loads it with java.util.Properties). */
	static Properties parse(String text) {
		Properties p = new Properties();
		try {
			p.load(new StringReader(text));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return p;
	}

	/**
	 * The text with {@code key} set to {@code value} (a plain value: letters, digits, dots, dashes). Every line that
	 * sets the key is rewritten (Properties takes the last), keeping its own indentation, key and separator; a key the
	 * text lacks is appended on a line of its own, with the file's own line end.
	 */
	static String set(String text, String key, String value) {
		StringBuilder out = new StringBuilder(text.length() + key.length() + value.length() + 2);
		boolean found = false;
		int i = 0, n = text.length();
		while (i < n) {
			int start = i;
			// A logical line: natural lines joined while one ends in an odd number of backslashes.
			int end = lineEnd(text, i);
			int next = afterBreak(text, end);
			while (continues(text, start, end) && next < n) {
				end = lineEnd(text, next);
				next = afterBreak(text, end);
			}
			String line = text.substring(start, end);
			int keyStart = 0;
			while (keyStart < line.length() && isBlank(line.charAt(keyStart))) keyStart++;
			if (keyStart < line.length() && line.charAt(keyStart) != '#' && line.charAt(keyStart) != '!') {
				int keyEnd = keyEnd(line, keyStart);
				if (unescape(line.substring(keyStart, keyEnd)).equals(key)) {
					int valueStart = valueStart(line, keyEnd);
					String separator = line.substring(keyEnd, valueStart);
					if (separator.isEmpty()) separator = "=";
					out.append(line, 0, keyEnd).append(separator).append(value);
					out.append(text, end, next);
					found = true;
					i = next;
					continue;
				}
			}
			out.append(text, start, next);
			i = next;
		}
		if (!found) {
			String newline = text.contains("\r\n") ? "\r\n" : "\n";
			if (n > 0 && text.charAt(n - 1) != '\n' && text.charAt(n - 1) != '\r') out.append(newline);
			out.append(key).append('=').append(value).append(newline);
		}
		return out.toString();
	}

	private static boolean isBlank(char c) {
		return c == ' ' || c == '\t' || c == '\f';
	}

	private static int lineEnd(String text, int from) {
		int i = from;
		while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
		return i;
	}

	private static int afterBreak(String text, int end) {
		if (end >= text.length()) return end;
		if (text.charAt(end) == '\r' && end + 1 < text.length() && text.charAt(end + 1) == '\n') return end + 2;
		return end + 1;
	}

	/** Whether the natural line ending at {@code end} continues onto the next (an odd run of backslashes). */
	private static boolean continues(String text, int start, int end) {
		int k = 0;
		for (int j = end - 1; j >= start && text.charAt(j) == '\\'; j--) k++;
		if (k % 2 == 0) return false;
		// A comment line never continues.
		int s = start;
		while (s < end && isBlank(text.charAt(s))) s++;
		return s < end && text.charAt(s) != '#' && text.charAt(s) != '!';
	}

	/** Where the key ends: the first unescaped '=', ':' or blank. */
	private static int keyEnd(String line, int from) {
		int i = from;
		while (i < line.length()) {
			char c = line.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == '=' || c == ':' || isBlank(c)) return i;
			i++;
		}
		return Math.min(i, line.length());
	}

	/** Where the value starts: past blanks, at most one '=' or ':', and blanks again. */
	private static int valueStart(String line, int keyEnd) {
		int i = keyEnd;
		while (i < line.length() && isBlank(line.charAt(i))) i++;
		if (i < line.length() && (line.charAt(i) == '=' || line.charAt(i) == ':')) {
			i++;
			while (i < line.length() && isBlank(line.charAt(i))) i++;
		}
		return i;
	}

	private static String unescape(String key) {
		if (key.indexOf('\\') < 0) return key;
		Properties p = parse(key + "=");
		return p.stringPropertyNames().isEmpty() ? key : p.stringPropertyNames().iterator().next();
	}
}
