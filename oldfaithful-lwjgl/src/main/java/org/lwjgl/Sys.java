package org.lwjgl;

import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLMessageBox;
import org.lwjgl.sdl.SDLMisc;
import org.studioquire.oldfaithful.lwjgl.Sdl;
import org.studioquire.oldfaithful.lwjgl.Window;

/** LWJGL 2's Sys (Old Faithful's implementation, written fresh): time, version, and a few system calls. */
public final class Sys {
	/** Microseconds: fine enough for frame timing, and far from overflow when callers multiply by 1000. */
	private static final long RESOLUTION = 1_000_000L;
	private static final long START = System.nanoTime();

	private Sys() {
	}

	public static String getVersion() {
		return "2.9.4 (Pure Faithful, on LWJGL " + org.lwjgl.Version.getVersion() + " and SDL3)";
	}

	public static void initialize() {
	}

	public static boolean is64Bit() {
		String model = System.getProperty("sun.arch.data.model");
		return model != null ? model.equals("64") : System.getProperty("os.arch").contains("64");
	}

	public static long getTimerResolution() {
		return RESOLUTION;
	}

	public static long getTime() {
		return (System.nanoTime() - START) / 1000L;
	}

	public static void alert(String title, String message) {
		boolean grabbed = org.lwjgl.input.Mouse.isCreated() && org.lwjgl.input.Mouse.isGrabbed();
		if (grabbed) org.lwjgl.input.Mouse.setGrabbed(false);
		SDLMessageBox.SDL_ShowSimpleMessageBox(SDLMessageBox.SDL_MESSAGEBOX_WARNING, title == null ? "" : title,
				message == null ? "" : message, Window.handle());
		if (grabbed) org.lwjgl.input.Mouse.setGrabbed(true);
	}

	public static boolean openURL(String url) {
		if (SDLMisc.SDL_OpenURL(url)) return true;
		try {
			java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
			return true;
		} catch (Exception | LinkageError e) {
			return false;
		}
	}

	public static String getClipboard() {
		if (Sdl.videoReady()) return SDLClipboard.SDL_GetClipboardText();
		try {
			return (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
		} catch (Exception | LinkageError e) {
			return null;
		}
	}
}
