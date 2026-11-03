package org.studioquire.oldfaithful.display;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import org.studioquire.oldfaithful.lwjgl.Settings;

/**
 * config/purefaithful-display.properties, read before the game starts and handed to our LWJGL's {@link Settings}.
 * Written with defaults and explanations if missing; a setting the file lacks (it was written by an older version) is
 * appended with its explanation.
 */
public final class DisplayConfig implements PreLaunchEntrypoint {
	private static final String HEADER = "# purefaithful-display. Restart the game after changing anything here.\n";
	/** Key, default, explanation. */
	private static final String[][] SETTINGS = {
			{"fullscreen.exclusive", "false",
					"# Fullscreen (F11) switches the screen's video mode, as the original game did, instead of covering the\n"
					+ "# screen with a borderless window. Borderless switches instantly and alt-tabs cleanly.\n"},
			{"mouse.input", "system",
					"# Mouse look. system: the view turns as the desktop pointer would move, with the system's pointer speed\n"
					+ "# and acceleration (Windows' \"Enhance pointer precision\"), as the original game did.\n"
					+ "# raw: the mouse's own movement, unaffected by those settings.\n"},
			{"window.scaleToDisplay", "true",
					"# The window opens scaled to the display (854x480 at 150% scaling opens as 1281x720).\n"},
			{"mouse.subpixel", "false",
					"# Mouse look turns by the mouse's exact movement instead of whole pixels per frame, so slow and diagonal\n"
					+ "# turns are smooth. The original game turned by whole pixels (false).\n"},
			{"frame.smooth", "true",
					"# With vsync, each frame moves the world and the view by exactly one refresh's worth, as it's shown,\n"
					+ "# instead of by the time the game happened to measure (in whole milliseconds, at a varying point of the\n"
					+ "# frame), which makes a fine stutter. false: the original game's timing.\n"},
			{"window.remember", "false",
					"# The window opens where it was when the game last closed: its size, position, display, and maximised or\n"
					+ "# not. Changing the launcher's window settings (Prism's Window size, Start Minecraft maximized) wins over\n"
					+ "# it the next time. false: the window opens as the launcher asks, as the original game did.\n"},
			{"window.startMaximized", "false",
					"# The window opens maximised when neither the launcher nor a remembered placement (window.remember) says\n"
					+ "# otherwise. false: at the game's own size, as the original game did.\n"},
			{"vsync", "true",
					"# Frames wait for the screen's refresh: no tearing, even pacing. The original game ran without it\n"
					+ "# (false), drawing as fast as it could, which tears and judders on today's displays.\n"},
	};

	@Override
	public void onPreLaunch() {
		if (!DisplayMixinPlugin.layerPresent()) return;
		Properties defaults = modDefaults();
		Properties p = new Properties(defaults);
		File file = FabricLoader.getInstance().getConfigDir().resolve("purefaithful-display.properties").toFile();
		try {
			StringBuilder add = new StringBuilder();
			if (file.isFile()) {
				try (InputStream in = new FileInputStream(file)) {
					p.load(in);
				}
			} else {
				file.getParentFile().mkdirs();
				add.append(HEADER);
			}
			for (String[] s : SETTINGS) {
				if (!p.containsKey(s[0])) add.append('\n').append(s[2]).append(s[0]).append('=').append(defaults.getProperty(s[0], s[1])).append('\n');
			}
			if (add.length() > 0) {
				try (OutputStream out = new FileOutputStream(file, true)) {
					out.write(add.toString().getBytes(StandardCharsets.ISO_8859_1));
				}
			}
		} catch (IOException e) {
			System.out.println("[purefaithful-display] WARN Could not read or write " + file + ", using defaults: " + e);
		}
		Settings.exclusiveFullscreen = Boolean.parseBoolean(value(p, "fullscreen.exclusive"));
		Settings.rawMouse = "raw".equalsIgnoreCase(value(p, "mouse.input"));
		Settings.scaleInitialSize = Boolean.parseBoolean(value(p, "window.scaleToDisplay"));
		Settings.vsync = Boolean.parseBoolean(value(p, "vsync"));
		subpixelLook = Boolean.parseBoolean(value(p, "mouse.subpixel"));
		FrameClock.enabled = Boolean.parseBoolean(value(p, "frame.smooth"));
		Settings.placement = WindowPlacement.choose(Boolean.parseBoolean(value(p, "window.remember")),
				Boolean.parseBoolean(value(p, "window.startMaximized")),
				FabricLoader.getInstance().getConfigDir().resolve("purefaithful-display-window.properties").toFile());
	}

	/** mouse.subpixel: mouse look by the unrounded motion (GameRendererMixin). */
	public static volatile boolean subpixelLook;

	private static String value(Properties p, String key) {
		for (String[] s : SETTINGS) {
			if (s[0].equals(key)) return p.getProperty(key, s[1]).trim();
		}
		throw new IllegalArgumentException(key);
	}

	/**
	 * Defaults other mods ask for in their fabric.mod.json, {@code "custom": {"purefaithful-display:defaults":
	 * {"window.remember": "true"}}} (Old Faithful's options mod does, for its edition's window). They stand in for
	 * ours wherever the file says nothing, and are what's written for a setting the file lacks; what the file says
	 * wins. Two mods asking for different values: the first by mod id wins, and the clash is logged.
	 */
	private static Properties modDefaults() {
		Properties d = new Properties();
		List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
		mods.sort(Comparator.comparing(m -> m.getMetadata().getId()));
		for (ModContainer mod : mods) {
			String id = mod.getMetadata().getId();
			CustomValue asked = mod.getMetadata().getCustomValue("purefaithful-display:defaults");
			if (asked == null || asked.getType() != CustomValue.CvType.OBJECT) continue;
			for (Map.Entry<String, CustomValue> e : asked.getAsObject()) {
				String key = e.getKey();
				boolean known = false;
				for (String[] s : SETTINGS) known |= s[0].equals(key);
				if (!known || e.getValue().getType() != CustomValue.CvType.STRING) {
					System.out.println("[purefaithful-display] WARN " + id + " asks for a default of a setting this version lacks, or not as text: " + key);
					continue;
				}
				String value = e.getValue().getAsString();
				if (!d.containsKey(key)) {
					d.setProperty(key, value);
					System.out.println("[purefaithful-display] Default " + key + "=" + value + ", asked by " + id);
				} else if (!d.getProperty(key).equals(value)) {
					System.out.println("[purefaithful-display] Default " + key + " asked as " + d.getProperty(key) + " and as " + value
							+ " (" + id + "): keeping the first");
				}
			}
		}
		return d;
	}
}
