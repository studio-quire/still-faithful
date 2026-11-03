package org.studioquire.oldfaithful.hardening.util;

/**
 * Plain stdout logging with a fixed prefix. The game logs to stdout (the server also through java.util.logging),
 * and the loader's own logger isn't public API, so this keeps our lines easy to find in either.
 */
public final class Log {
	private static final String PREFIX = "[purefaithful-hardening] ";

	private Log() {
	}

	public static void info(String message) {
		System.out.println(PREFIX + message);
	}

	public static void warn(String message) {
		System.out.println(PREFIX + "WARN " + message);
	}
}
