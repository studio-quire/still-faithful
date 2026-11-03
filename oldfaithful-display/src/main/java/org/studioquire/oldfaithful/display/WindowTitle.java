package org.studioquire.oldfaithful.display;

/**
 * The title the game's window showed on LWJGL 2: the title of the frame the launcher (Fabric) put the applet in,
 * "Minecraft". The game's own Display title, "Minecraft Minecraft Beta 1.7.3", was never seen there, since the
 * display lived inside that frame; with the game in a window of its own, the frame's title carries over to it.
 */
public final class WindowTitle {
	public static final String DEFAULT = "Minecraft";
	private static volatile String frameTitle;

	private WindowTitle() {
	}

	public static void fromFrame(String title) {
		if (title != null && !title.trim().isEmpty()) frameTitle = title;
	}

	public static String get() {
		String t = frameTitle;
		return t != null ? t : DEFAULT;
	}
}
