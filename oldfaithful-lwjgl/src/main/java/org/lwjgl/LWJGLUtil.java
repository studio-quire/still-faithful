package org.lwjgl;

import java.util.Locale;

/** LWJGL 2's LWJGLUtil, the parts callers use: platform, debug flags, logging. */
public class LWJGLUtil {
	public static final int PLATFORM_LINUX = 1;
	public static final int PLATFORM_MACOSX = 2;
	public static final int PLATFORM_WINDOWS = 3;
	public static final String PLATFORM_LINUX_NAME = "linux";
	public static final String PLATFORM_MACOSX_NAME = "macosx";
	public static final String PLATFORM_WINDOWS_NAME = "windows";

	public static final boolean DEBUG = Boolean.getBoolean("org.lwjgl.util.Debug");
	public static final boolean CHECKS = !Boolean.getBoolean("org.lwjgl.util.NoChecks");

	private static final int PLATFORM;

	static {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.startsWith("windows")) PLATFORM = PLATFORM_WINDOWS;
		else if (os.startsWith("mac") || os.startsWith("darwin")) PLATFORM = PLATFORM_MACOSX;
		else PLATFORM = PLATFORM_LINUX;
	}

	public static int getPlatform() {
		return PLATFORM;
	}

	public static String getPlatformName() {
		switch (PLATFORM) {
			case PLATFORM_WINDOWS: return PLATFORM_WINDOWS_NAME;
			case PLATFORM_MACOSX: return PLATFORM_MACOSX_NAME;
			default: return PLATFORM_LINUX_NAME;
		}
	}

	public static String mapLibraryName(String name) {
		return System.mapLibraryName(name);
	}

	public static boolean getPrivilegedBoolean(String property) {
		return Boolean.getBoolean(property);
	}

	public static Integer getPrivilegedInteger(String property) {
		return Integer.getInteger(property);
	}

	public static Integer getPrivilegedInteger(String property, int defaultValue) {
		return Integer.getInteger(property, defaultValue);
	}

	public static void log(CharSequence msg) {
		if (DEBUG) System.err.println("[LWJGL] " + msg);
	}

	public static boolean isMacOSXEqualsOrBetterThan(int major, int minor) {
		if (PLATFORM != PLATFORM_MACOSX) return false;
		String[] v = System.getProperty("os.version", "0.0").split("\\.");
		int maj = Integer.parseInt(v[0]), min = v.length > 1 ? Integer.parseInt(v[1]) : 0;
		return maj > major || (maj == major && min >= minor);
	}

	public static String toHexString(int value) {
		return "0x" + Integer.toHexString(value).toUpperCase(Locale.ROOT);
	}
}
