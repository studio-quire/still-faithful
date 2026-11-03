package org.studioquire.oldfaithful.saves;

/** Lines in the game's output (and our log file through oldfaithful-hardening's copy), prefixed like our other mods. */
final class Log {
	private Log() {
	}

	static void info(String message) {
		System.out.println("[purefaithful-saves] " + message);
	}

	static void warn(String message) {
		System.out.println("[purefaithful-saves] WARN " + message);
	}
}
