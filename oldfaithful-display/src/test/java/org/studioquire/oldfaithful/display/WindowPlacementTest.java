package org.studioquire.oldfaithful.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.studioquire.oldfaithful.lwjgl.Placement;

/** Fullscreen kept with the window: restored only with the remembered placement it was kept over. */
class WindowPlacementTest {
	@TempDir
	Path dir;

	@AfterEach
	void clearLauncher() {
		System.clearProperty(WindowPlacement.LAUNCHER_PROPERTY);
	}

	private File kept(String extra, String launcher) throws IOException {
		Path f = dir.resolve("window.properties");
		Files.write(f, ("x=100\ny=120\nwidth=950\nheight=550\nmaximized=true\nlauncher=" + launcher + "\n" + extra).getBytes(StandardCharsets.ISO_8859_1));
		return f.toFile();
	}

	@Test
	void stoppedFullscreenOpensFullscreenOverItsWindow() throws IOException {
		Placement p = WindowPlacement.choose(true, false, kept("fullscreen=true\n", ""));
		assertEquals(950, p.width);
		assertTrue(p.maximized);
		assertTrue(WindowPlacement.startFullscreen());
	}

	@Test
	void windowedOrOlderFilesOpenWindowed() throws IOException {
		WindowPlacement.choose(true, false, kept("fullscreen=false\n", ""));
		assertFalse(WindowPlacement.startFullscreen());
		WindowPlacement.choose(true, false, kept("", ""));
		assertFalse(WindowPlacement.startFullscreen());
	}

	@Test
	void aChangedLauncherSettingWinsOverFullscreenToo() throws IOException {
		System.setProperty(WindowPlacement.LAUNCHER_PROPERTY, "1000x600");
		Placement p = WindowPlacement.choose(true, false, kept("fullscreen=true\n", "maximized"));
		assertEquals(1000, p.width);
		assertFalse(WindowPlacement.startFullscreen());
	}

	@Test
	void notRememberingNeverRestoresFullscreen() throws IOException {
		WindowPlacement.choose(true, false, kept("fullscreen=true\n", ""));
		WindowPlacement.choose(false, false, kept("fullscreen=true\n", ""));
		assertFalse(WindowPlacement.startFullscreen());
	}
}
