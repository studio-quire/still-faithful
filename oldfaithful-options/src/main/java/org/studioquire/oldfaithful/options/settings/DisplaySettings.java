package org.studioquire.oldfaithful.options.settings;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.studioquire.oldfaithful.display.DisplayConfig;
import org.studioquire.oldfaithful.display.DisplayMixinPlugin;
import org.studioquire.oldfaithful.display.FrameClock;
import org.studioquire.oldfaithful.lwjgl.Settings;
import org.studioquire.oldfaithful.lwjgl.Window;

/**
 * Pure Faithful Display's settings (purefaithful-display.properties, docs/lwjgl.md > The display mod). Loaded only when
 * that mod is installed. The ones the display mod reads as it goes take effect at once, set where it keeps them; the
 * window's opening place waits for the next start. The display mod itself knows nothing of this screen, so Pure
 * Faithful is unchanged.
 */
final class DisplaySettings {
	private static final String FILE = "purefaithful-display.properties";

	private DisplaySettings() {
	}

	static boolean working() {
		return DisplayMixinPlugin.layerPresent();
	}

	static void register(SettingsRegistry r) {
		r.add("window", Setting.onOff(FILE, "vsync", "VSync",
				"Frames wait for the screen's refresh, so nothing tears. Beta drew as fast as it could.", true)
				.applyNow(v -> {
					Settings.vsync = Boolean.parseBoolean(v);
					Display.setVSyncEnabled(Settings.vsync);
				}));
		r.add("window", Setting.onOff(FILE, "frame.smooth", "Even Frames",
				"With VSync, each frame moves by exactly one refresh, without beta's fine stutter.", true)
				.applyNow(v -> FrameClock.enabled = Boolean.parseBoolean(v)));
		r.add("window", Setting.onOff(FILE, "mouse.subpixel", "Smooth Look",
				"Turn by the mouse's exact movement, not whole pixels, so slow turns are smooth. Beta: OFF.", false)
				.applyNow(v -> DisplayConfig.subpixelLook = Boolean.parseBoolean(v)));
		r.add("window", Setting.choice(FILE, "mouse.input", "Mouse",
				"System: the desktop pointer's speed and acceleration, as beta had. Raw: the mouse's own movement.",
				"system", "system", "System", "raw", "Raw")
				.applyNow(v -> {
					Settings.rawMouse = "raw".equals(v);
					refresh();
				}));
		r.add("window", Setting.choice(FILE, "fullscreen.exclusive", "Fullscreen",
				"F11. Borderless covers the screen at once and alt-tabs cleanly; Exclusive switches its mode, as beta did.",
				"false", "false", "Borderless", "true", "Exclusive")
				.applyNow(v -> {
					Settings.exclusiveFullscreen = Boolean.parseBoolean(v);
					refresh();
				}));
		// Old Faithful asks for these two on (fabric.mod.json), so a file without them reads as on.
		r.add("window", Setting.onOff(FILE, "window.remember", "Remember Window",
				"The window opens where it was when the game last closed, fullscreen included.", true));
		r.add("window", Setting.onOff(FILE, "window.startMaximized", "Start Maximized",
				"The window opens maximized when neither the launcher nor a remembered window says otherwise.", true));
		r.add("window", Setting.onOff(FILE, "window.scaleToDisplay", "Scale to Display",
				"On a high-density screen the window opens scaled up to match, as the rest of the desktop is.", true));
	}

	private static void refresh() {
		try {
			Window.refreshSettings();
		} catch (LWJGLException e) {
			throw new IllegalStateException(e);
		}
	}
}
