package org.studioquire.oldfaithful.lwjgl;

/**
 * Choices LWJGL 2's API has no words for. Defaults keep LWJGL 2's behaviour where a game could notice; a mod
 * (Old Faithful's display mod) sets them before the window is created. System properties
 * {@code oldfaithful.lwjgl.<name>} set the initial values.
 */
public final class Settings {
	/** Fullscreen takes over the display's video mode (LWJGL 2's way) instead of covering it with a borderless window. */
	public static volatile boolean exclusiveFullscreen = Boolean.getBoolean("oldfaithful.lwjgl.exclusiveFullscreen");
	/**
	 * Grabbed-mouse motion straight from the device (raw input). Off by default: LWJGL 2 on Windows read the desktop
	 * pointer's motion, after the system's pointer speed and acceleration, and SDL applies the same to raw input.
	 */
	public static volatile boolean rawMouse = Boolean.getBoolean("oldfaithful.lwjgl.rawMouse");
	/**
	 * The first window's size, given in pixels by the game, is scaled by the display's content scale (150% Windows
	 * scaling: 854x480 opens as 1281x720), so it isn't tiny on high-density screens. LWJGL 2 didn't.
	 */
	public static volatile boolean scaleInitialSize = Boolean.getBoolean("oldfaithful.lwjgl.scaleInitialSize");
	/**
	 * Vsync from the start. Off by default, as LWJGL 2 set it on creation; the game or a mod can still change it
	 * through Display.setVSyncEnabled / setSwapInterval.
	 */
	public static volatile boolean vsync = Boolean.getBoolean("oldfaithful.lwjgl.vsync");
	/**
	 * Where the first window opens and at what size (exactly, unscaled), and whether maximised: a launcher's window
	 * settings, or where the window was last time. Null: the game's size, centred (LWJGL 2's behaviour).
	 */
	public static volatile Placement placement;
	/** The window is never shown (tests). */
	public static final boolean hidden = Boolean.getBoolean("oldfaithful.lwjgl.hidden");

	private Settings() {
	}
}
