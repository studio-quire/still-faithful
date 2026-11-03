package org.lwjgl.opengl;

import java.awt.Canvas;
import java.nio.ByteBuffer;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.studioquire.oldfaithful.lwjgl.Settings;
import org.studioquire.oldfaithful.lwjgl.Window;

/**
 * LWJGL 2's Display, on an SDL3 window (Old Faithful's implementation, written fresh). Sizes are framebuffer pixels.
 * Differences on purpose: fullscreen is borderless unless {@link Settings#exclusiveFullscreen}; {@link #setParent}
 * can't embed in an AWT canvas, so the window is always its own (Old Faithful's display mod takes b1.7.3 off the
 * applet's canvas).
 */
public final class Display {
	private static Canvas parent;
	private static boolean parentWarned;
	private static long lastSync;
	private static long syncSleepError;

	private Display() {
	}

	public static Drawable getDrawable() {
		return Window.created() ? DisplayDrawable.INSTANCE : null;
	}

	public static DisplayMode[] getAvailableDisplayModes() throws LWJGLException {
		return Window.availableModes();
	}

	public static DisplayMode getDesktopDisplayMode() {
		try {
			return Window.desktopMode();
		} catch (LWJGLException e) {
			throw new IllegalStateException(e);
		}
	}

	public static DisplayMode getDisplayMode() {
		return Window.mode();
	}

	public static void setDisplayMode(DisplayMode mode) throws LWJGLException {
		Window.setMode(mode);
	}

	public static void setDisplayConfiguration(float gamma, float brightness, float contrast) throws LWJGLException {
		// Gamma ramps aren't something SDL3 offers; nothing in b1.7.3 calls this.
	}

	/**
	 * Waits so that calls come {@code fps} times a second at most, as LWJGL 2's did: sleeping most of the gap and
	 * yielding the rest, with the sleep's overshoot learnt as it goes.
	 */
	public static void sync(int fps) {
		if (fps <= 0) return;
		long interval = 1_000_000_000L / fps;
		long next = lastSync + interval;
		long now = System.nanoTime();
		try {
			while (next - now > syncSleepError) {
				long t0 = System.nanoTime();
				Thread.sleep(1);
				long slept = System.nanoTime() - t0;
				syncSleepError = Math.max(syncSleepError * 7 / 8, slept - 1_000_000L);
				now = System.nanoTime();
			}
			while (next - now > 0) {
				Thread.yield();
				now = System.nanoTime();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		lastSync = next - now > -interval ? next : now;
	}

	public static String getTitle() {
		return Window.title();
	}

	public static Canvas getParent() {
		return parent;
	}

	public static void setParent(Canvas canvas) throws LWJGLException {
		parent = canvas;
		if (canvas != null && !parentWarned) {
			parentWarned = true;
			System.out.println("[purefaithful-lwjgl] Display.setParent: embedding in an AWT canvas isn't supported; the game gets its own window");
		}
	}

	public static void setFullscreen(boolean fullscreen) throws LWJGLException {
		Window.setFullscreen(fullscreen);
	}

	public static void setDisplayModeAndFullscreen(DisplayMode mode) throws LWJGLException {
		Window.setMode(mode);
		Window.setFullscreen(mode.isFullscreenCapable());
	}

	public static boolean isFullscreen() {
		return Window.fullscreen();
	}

	public static void setTitle(String title) {
		Window.setTitle(title);
	}

	public static boolean isCloseRequested() {
		requireCreated();
		return Window.takeCloseRequested();
	}

	public static boolean isVisible() {
		requireCreated();
		return Window.visible();
	}

	public static boolean isActive() {
		requireCreated();
		return Window.active();
	}

	public static boolean isDirty() {
		requireCreated();
		return Window.dirty();
	}

	public static void processMessages() {
		requireCreated();
		Window.pump();
	}

	public static void swapBuffers() throws LWJGLException {
		requireCreated();
		Window.swap();
	}

	public static void update() {
		update(true);
	}

	/** Swap, read the system's events, then poll the devices: LWJGL 2's order. */
	public static void update(boolean processMessages) {
		requireCreated();
		Window.swap();
		if (processMessages) Window.pump();
		Window.latchResized();
		if (Mouse.isCreated()) Mouse.poll();
		if (Keyboard.isCreated()) Keyboard.poll();
	}

	public static void releaseContext() throws LWJGLException {
		DisplayDrawable.INSTANCE.releaseContext();
	}

	public static boolean isCurrent() throws LWJGLException {
		return DisplayDrawable.INSTANCE.isCurrent();
	}

	public static void makeCurrent() throws LWJGLException {
		DisplayDrawable.INSTANCE.makeCurrent();
	}

	public static void create() throws LWJGLException {
		create(new PixelFormat());
	}

	public static void create(PixelFormat pf) throws LWJGLException {
		Window.create(pf, 0);
	}

	public static void create(PixelFormat pf, Drawable shared) throws LWJGLException {
		Window.create(pf, 0);
	}

	public static void setInitialBackground(float red, float green, float blue) {
		// The first frame is cleared to black; nothing in b1.7.3 sets another colour.
	}

	public static void destroy() {
		if (Mouse.isCreated()) Mouse.destroy();
		if (Keyboard.isCreated()) Keyboard.destroy();
		Window.destroy();
	}

	public static boolean isCreated() {
		return Window.created();
	}

	public static void setSwapInterval(int value) {
		Window.setSwapInterval(value);
	}

	public static void setVSyncEnabled(boolean sync) {
		Window.setSwapInterval(sync ? 1 : 0);
	}

	public static void setLocation(int x, int y) {
		Window.setLocation(x, y);
	}

	public static String getAdapter() {
		return null;
	}

	public static String getVersion() {
		return null;
	}

	public static int setIcon(ByteBuffer[] icons) {
		return Window.setIcons(icons);
	}

	public static void setResizable(boolean resizable) {
		Window.setResizable(resizable);
	}

	public static boolean isResizable() {
		return Window.resizable();
	}

	/** Whether the window changed size by the last {@link #update}; the same until the next. */
	public static boolean wasResized() {
		return Window.wasResized();
	}

	public static int getX() {
		return Window.x();
	}

	public static int getY() {
		return Window.y();
	}

	public static int getWidth() {
		return Window.framebufferWidth();
	}

	public static int getHeight() {
		return Window.framebufferHeight();
	}

	/** 1: {@link #getWidth} and {@link #getHeight} are already pixels. */
	public static float getPixelScaleFactor() {
		return 1f;
	}

	private static void requireCreated() {
		if (!Window.created()) throw new IllegalStateException("Display not created");
	}
}
