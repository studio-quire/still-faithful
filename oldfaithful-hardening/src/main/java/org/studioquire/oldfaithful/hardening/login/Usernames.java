package org.studioquire.oldfaithful.hardening.login;

import java.util.Locale;

/**
 * A b1.7.3 server saves each player to {@code world/players/<name>.dat}, the name exactly as the client sent it. On
 * an offline-mode server nothing else checks that name, so here it has to be
 * safe as a file name
 * on any system a server runs on.
 *
 * <p>Refused: names that can't be a plain file name. Everything else vanilla accepted still logs in, including
 * offline-mode names Mojang wouldn't allow (spaces, dots, accents); online-mode names are always letters, digits and
 * underscores, so they're never affected.
 */
public final class Usernames {
	private static final String FORBIDDEN = "/\\:*?\"<>|";

	private Usernames() {
	}

	/** Null if the name is safe, otherwise why it isn't. */
	public static String problem(String name) {
		if (name == null || name.trim().isEmpty()) return "empty";
		if (name.equals(".") || name.equals("..")) return "a directory name";
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c < 0x20 || c == 0x7f) return "a control character";
			if (FORBIDDEN.indexOf(c) >= 0) return "the character " + c;
		}
		if (isWindowsDevice(name)) return "a Windows device name";
		return null;
	}

	/** CON, PRN, AUX, NUL, COM0-9 and LPT0-9 (also with superscript digits), with or without an extension. */
	private static boolean isWindowsDevice(String name) {
		int dot = name.indexOf('.');
		String base = (dot < 0 ? name : name.substring(0, dot)).trim().toUpperCase(Locale.ROOT);
		if (base.equals("CON") || base.equals("PRN") || base.equals("AUX") || base.equals("NUL")) return true;
		if (base.length() == 4 && (base.startsWith("COM") || base.startsWith("LPT"))) {
			char d = base.charAt(3);
			return (d >= '0' && d <= '9') || d == '¹' || d == '²' || d == '³';
		}
		return false;
	}
}
