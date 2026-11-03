package org.studioquire.oldfaithful.display;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;
import org.studioquire.oldfaithful.lwjgl.Placement;
import org.studioquire.oldfaithful.lwjgl.Window;

/**
 * Where the game's window opens.
 * <p>
 * <b>The launcher's window settings.</b> Prism (and its kin) passes its "Window size" and "Start Minecraft maximized"
 * to b1.7.3 only through its own applet frame, which a Fabric instance never uses: the instance has the
 * {@code noapplet} trait, so Prism starts Fabric's main class "without size parameters" and Fabric's frame opens at
 * 854x480 whatever was set. Prism does put the setting in the system property
 * {@code org.prismlauncher.window.dimensions} ({@code <w>x<h>} or {@code maximized}) for mods, and this reads it: the
 * window opens at that size, exactly (as vanilla in Prism's frame did), or maximised over the game's usual size, as
 * Prism's frame maximised. Prism's default, 854x480, is the game's own size and opens as it always has (scaled to the
 * display, {@code window.scaleToDisplay}). Launchers that pass {@code --width}/{@code --height} reach the game through
 * Fabric's frame as before.
 * <p>
 * <b>Remembering</b> ({@code window.remember}, off in Pure Faithful, on in Old Faithful): the window's windowed
 * bounds, maximised state and display are kept when the game closes and restored next time, unless the launcher's
 * setting has changed since, in which case the launcher's wins (the last thing changed wins). Fullscreen (borderless or
 * exclusive, as {@code fullscreen.exclusive} says now) is kept too, over the windowed placement, on whichever display
 * the window opens on: its own, or the one it was fitted onto if that's gone.
 * <p>
 * <b>Starting maximised</b> ({@code window.startMaximized}, off in Pure Faithful, on in Old Faithful): with neither of
 * those to say otherwise, the window opens maximised over the game's usual size. With remembering on, that's the
 * first start; after it, the window is wherever the player left it.
 */
public final class WindowPlacement {
	public static final String LAUNCHER_PROPERTY = "org.prismlauncher.window.dimensions";
	private static final String GAME_SIZE = "854x480";

	private static volatile boolean remember;
	private static volatile File file;
	private static volatile String launcherRequest;
	private static volatile boolean startFullscreen;

	private WindowPlacement() {
	}

	/** Decides the first window's placement (into Settings.placement) before the game starts. */
	static Placement choose(boolean rememberOn, boolean startMaximized, File stateFile) {
		remember = rememberOn;
		startFullscreen = false;
		file = stateFile;
		launcherRequest = launcherRequest();
		Placement fromLauncher = fromLauncher(launcherRequest);
		if (rememberOn) {
			Properties state = load(stateFile);
			if (state != null) {
				Placement remembered = parse(state);
				if (remembered == null) {
					log("Ignoring " + stateFile.getName() + ": not a window placement");
				} else if (!state.getProperty("launcher", "").equals(launcherRequest)) {
					log("Launcher's window setting changed since last time (" + describe(state.getProperty("launcher", "")) + " -> "
							+ describe(launcherRequest) + "): opening as the launcher asks");
				} else {
					startFullscreen = Boolean.parseBoolean(state.getProperty("fullscreen", "false").trim());
					log("Window opens where it was: " + remembered + (startFullscreen ? ", fullscreen over it" : ""));
					return remembered;
				}
			}
		}
		if (fromLauncher != null) {
			log("Window opens as the launcher asks: " + fromLauncher);
			return fromLauncher;
		}
		if (startMaximized && !sizedByArguments()) {
			Placement maximized = new Placement();
			maximized.maximized = true;
			log("Window opens maximised (window.startMaximized)");
			return maximized;
		}
		return null;
	}

	/** Another launcher's --width/--height, which reach the game through Fabric's frame: they say what the window is. */
	private static boolean sizedByArguments() {
		for (String arg : FabricLoader.getInstance().getLaunchArguments(false)) {
			if (arg.equals("--width") || arg.equals("--height")) return true;
		}
		return false;
	}

	/** The launcher's request as it was given, or "" if none. */
	private static String launcherRequest() {
		String v = System.getProperty(LAUNCHER_PROPERTY);
		return v == null ? "" : v.trim();
	}

	/** Prism's {@code <w>x<h>} or {@code maximized}; null for none, the game's own size, or anything unreadable. */
	static Placement fromLauncher(String request) {
		if (request == null || request.isEmpty() || request.equals(GAME_SIZE)) return null;
		Placement p = new Placement();
		if (request.equals("maximized")) {
			p.maximized = true;
			return p;
		}
		String[] wh = request.split("x", 2);
		try {
			if (wh.length == 2) {
				p.width = Integer.parseInt(wh[0].trim());
				p.height = Integer.parseInt(wh[1].trim());
				if (p.sized()) return p;
			}
		} catch (NumberFormatException e) {
			// Below.
		}
		log("Launcher's window setting not understood, ignored: " + request);
		return null;
	}

	/**
	 * Whether the game goes fullscreen as its window opens: it was fullscreen when it last stopped, and the window
	 * opens where it was (MinecraftMixin). The windowed placement under it is what F11 comes back to.
	 */
	public static boolean startFullscreen() {
		return startFullscreen;
	}

	/** Keeps where the window is, if remembering; called as the game stops, while the window still exists. */
	public static void save() {
		if (!remember || file == null || !Window.created()) return;
		try {
			write(Window.placement(), Window.fullscreen());
		} catch (RuntimeException e) {
			// The game is stopping: nothing here may keep it from closing its window.
			log("WARN Could not keep the window's placement: " + e);
		}
	}

	private static void write(Placement p, boolean fullscreen) {
		if (p == null) return;
		Properties state = new Properties();
		state.setProperty("x", String.valueOf(p.x));
		state.setProperty("y", String.valueOf(p.y));
		state.setProperty("width", String.valueOf(p.width));
		state.setProperty("height", String.valueOf(p.height));
		state.setProperty("maximized", String.valueOf(p.maximized));
		if (p.display != null) {
			state.setProperty("display", p.display);
			state.setProperty("display.x", String.valueOf(p.displayX));
			state.setProperty("display.y", String.valueOf(p.displayY));
		}
		state.setProperty("fullscreen", String.valueOf(fullscreen));
		state.setProperty("launcher", launcherRequest);
		File tmp = new File(file.getPath() + ".tmp");
		try {
			file.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(tmp)) {
				state.store(out, " Where the game's window was when it last closed (window.remember in purefaithful-display.properties).\n"
						+ " Delete this file to forget it. launcher: the launcher's window setting then.");
			}
			Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			log("WARN Could not keep the window's placement in " + file + ": " + e);
		}
	}

	private static Properties load(File f) {
		if (!f.isFile()) return null;
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(f)) {
			p.load(in);
			return p;
		} catch (IOException | IllegalArgumentException e) {
			log("WARN Could not read " + f + ": " + e);
			return null;
		}
	}

	private static Placement parse(Properties s) {
		try {
			Placement p = new Placement();
			p.x = Integer.parseInt(s.getProperty("x", "").trim());
			p.y = Integer.parseInt(s.getProperty("y", "").trim());
			p.width = Integer.parseInt(s.getProperty("width", "").trim());
			p.height = Integer.parseInt(s.getProperty("height", "").trim());
			p.maximized = Boolean.parseBoolean(s.getProperty("maximized", "false").trim());
			p.display = s.getProperty("display");
			if (p.display != null) {
				p.displayX = Integer.parseInt(s.getProperty("display.x", "").trim());
				p.displayY = Integer.parseInt(s.getProperty("display.y", "").trim());
			}
			if (!p.sized() || !p.positioned() || p.width > 65536 || p.height > 65536) return null;
			return p;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String describe(String request) {
		return request.isEmpty() ? "none" : request;
	}

	private static void log(String line) {
		System.out.println("[purefaithful-display] " + line);
	}
}
