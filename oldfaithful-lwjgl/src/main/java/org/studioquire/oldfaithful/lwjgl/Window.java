package org.studioquire.oldfaithful.lwjgl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_KeyboardEvent;
import org.lwjgl.sdl.SDL_MouseButtonEvent;
import org.lwjgl.sdl.SDL_MouseMotionEvent;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.system.MemoryStack;

/**
 * The one window and GL context behind {@link org.lwjgl.opengl.Display}, on SDL3. Sizes LWJGL 2 callers see are
 * framebuffer pixels: SDL's window coordinates times the window's pixel density (1 on Windows, 2 on a Retina Mac),
 * so the game renders at the screen's real resolution and its mouse coordinates match.
 */
public final class Window {
	private static long window;
	private static long context;
	private static int windowId;
	private static GLCapabilities capabilities;
	private static ContextCapabilities legacyCapabilities;

	/** The mode LWJGL 2 callers set and read: the windowed size, or the exclusive fullscreen mode. */
	private static DisplayMode mode;
	private static String title = "Game";
	private static boolean fullscreen;
	private static boolean resizable;
	// LWJGL 2's Display sets swap interval 0 (no vsync) on creation unless asked otherwise; b1.7.3 never asks. We set
	// it explicitly either way (Settings.vsync), never leaving it to the driver. -1: not set through the API yet.
	private static int swapInterval = -1;
	private static ByteBuffer[] icons;
	private static int initialX = -1, initialY = -1;
	/** The windowed bounds and state last seen (sampleWindowed), for {@link #placement()}. */
	private static int windowedX = Placement.CENTRED, windowedY = Placement.CENTRED, windowedW, windowedH;
	private static boolean maximized;
	private static String windowedDisplay;
	private static int windowedDisplayX, windowedDisplayY;

	private static boolean closeRequested;
	private static boolean active;
	private static boolean visible;
	private static boolean dirty = true;
	/** A size change seen by a pump since the last {@link #latchResized}; and what Display.wasResized reports until the next. */
	private static boolean resized, resizedThisUpdate;
	private static boolean textInputWanted;
	private static int fbWidth, fbHeight;
	private static float density = 1f;
	private static SDL_Event event;

	private Window() {
	}

	public static boolean created() {
		return window != 0;
	}

	public static long handle() {
		return window;
	}

	// ---- creation -------------------------------------------------------------------------------------------------

	public static void create(PixelFormat pf, long shareWith) throws LWJGLException {
		if (window != 0) throw new IllegalStateException("Only one LWJGL context may be instantiated at any one time.");
		Sdl.initVideo();
		DisplayMode m = mode != null ? mode : desktopMode();
		int depth = Math.max(pf.getDepthBits(), 24);
		SDLVideo.SDL_GL_ResetAttributes();
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_RED_SIZE, 8);
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_GREEN_SIZE, 8);
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_BLUE_SIZE, 8);
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_ALPHA_SIZE, pf.getAlphaBits());
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_DEPTH_SIZE, depth);
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_STENCIL_SIZE, pf.getStencilBits());
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_DOUBLEBUFFER, 1);
		if (pf.getSamples() > 0) {
			SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_MULTISAMPLEBUFFERS, 1);
			SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_MULTISAMPLESAMPLES, pf.getSamples());
		}
		if (shareWith != 0) SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 1);

		long flags = SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY | SDLVideo.SDL_WINDOW_HIDDEN;
		if (resizable) flags |= SDLVideo.SDL_WINDOW_RESIZABLE;
		Placement place = Settings.placement;
		// A placement's size is the windowed size even when the game starts fullscreen: it's where fullscreen returns.
		boolean exactSize = place != null && place.sized();
		int w = m.getWidth(), h = m.getHeight();
		if (exactSize) {
			w = place.width;
			h = place.height;
		} else if (Settings.scaleInitialSize && !fullscreen) {
			float scale = SDLVideo.SDL_GetDisplayContentScale(SDLVideo.SDL_GetPrimaryDisplay());
			if (scale > 1f) {
				w = Math.round(w * scale);
				h = Math.round(h * scale);
			}
		}
		int[] at = place != null && place.positioned() ? followDisplay(place) : null;
		long win = createWindow(w, h, flags, at);
		if (win == 0 && depth > 16) {
			// Very old drivers: a 16-bit depth buffer is still enough for the game.
			SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_DEPTH_SIZE, 16);
			win = createWindow(w, h, flags, at);
		}
		Sdl.check(win != 0, "SDL_CreateWindow");
		window = win;
		windowId = SDLVideo.SDL_GetWindowID(win);
		try {
			// SDL's window coordinates are pixels on Windows; elsewhere the size may need converting.
			density = SDLVideo.SDL_GetWindowPixelDensity(win);
			if (!exactSize && density > 0 && density != 1f) SDLVideo.SDL_SetWindowSize(win, Math.round(w / density), Math.round(h / density));
			if (at == null) {
				if (initialX >= 0 && initialY >= 0) SDLVideo.SDL_SetWindowPosition(win, initialX, initialY);
				else SDLVideo.SDL_SetWindowPosition(win, SDLVideo.SDL_WINDOWPOS_CENTERED, SDLVideo.SDL_WINDOWPOS_CENTERED);
			}
			fitOnScreen();
			sampleWindowed();
			// Hidden, SDL keeps the maximise pending (and reports it in the window's flags) until the window is shown.
			if (place != null && place.maximized && !SDLVideo.SDL_MaximizeWindow(win)) {
				System.out.println("[purefaithful-lwjgl] The window couldn't open maximised: " + Sdl.error());
			}
			sampleWindowed();
			context = SDLVideo.SDL_GL_CreateContext(win);
			Sdl.check(context != 0, "SDL_GL_CreateContext");
			Sdl.check(SDLVideo.SDL_GL_MakeCurrent(win, context), "SDL_GL_MakeCurrent");
			capabilities = GL.createCapabilities();
			legacyCapabilities = new ContextCapabilities(capabilities);
			if (swapInterval < 0) swapInterval = Settings.vsync ? 1 : 0;
			applySwapInterval();
			if (icons != null) applyIcon();
			// Black, not whatever the driver leaves, for the first frame shown.
			GL11.glClearColor(0f, 0f, 0f, 0f);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			SDLVideo.SDL_GL_SwapWindow(win);
			if (fullscreen) applyFullscreen();
			if (!Settings.hidden) {
				SDLVideo.SDL_ShowWindow(win);
				SDLVideo.SDL_RaiseWindow(win);
			}
			event = SDL_Event.calloc();
			updateSize();
			visible = !Settings.hidden;
			active = (SDLVideo.SDL_GetWindowFlags(win) & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0;
			textInput(textInputWanted);
			pump();
			resized = false;
			resizedThisUpdate = false;
		} catch (LWJGLException | RuntimeException e) {
			destroy();
			throw e;
		}
	}

	public static void destroy() {
		if (MouseState.grabbed() && window != 0 && !Settings.hidden) SDLMouse.SDL_SetWindowRelativeMouseMode(window, false);
		MouseState.setGrabbedState(false);
		if (context != 0) {
			SDLVideo.SDL_GL_MakeCurrent(window, 0);
			GL.setCapabilities(null);
			SDLVideo.SDL_GL_DestroyContext(context);
			context = 0;
		}
		if (window != 0) {
			SDLVideo.SDL_DestroyWindow(window);
			window = 0;
		}
		if (event != null) {
			event.free();
			event = null;
		}
		capabilities = null;
		legacyCapabilities = null;
		closeRequested = false;
		resized = resizedThisUpdate = false;
		KeyboardState.reset();
		MouseState.reset();
	}

	// ---- per frame ------------------------------------------------------------------------------------------------

	public static void swap() {
		SDLVideo.SDL_GL_SwapWindow(window);
		dirty = false;
	}

	/** Reads every pending SDL event into LWJGL 2's state: keys, text, mouse, focus, size, close. */
	public static void pump() {
		if (window == 0) return;
		MouseState.pumpStarted();
		boolean textInput = SDLKeyboard.SDL_TextInputActive(window);
		while (SDLEvents.SDL_PollEvent(event)) {
			int type = event.type();
			switch (type) {
				case SDLEvents.SDL_EVENT_QUIT:
					closeRequested = true;
					break;
				case SDLEvents.SDL_EVENT_KEY_DOWN:
				case SDLEvents.SDL_EVENT_KEY_UP: {
					SDL_KeyboardEvent k = event.key();
					if (k.windowID() != 0 && k.windowID() != windowId) break;
					KeyboardState.key(k.scancode(), k.mod(), k.down(), k.repeat(), k.timestamp(), textInput);
					break;
				}
				case SDLEvents.SDL_EVENT_TEXT_INPUT:
					if (event.text().windowID() == 0 || event.text().windowID() == windowId) {
						KeyboardState.text(event.text().textString(), event.text().timestamp());
					}
					break;
				case SDLEvents.SDL_EVENT_MOUSE_MOTION: {
					SDL_MouseMotionEvent m = event.motion();
					if (m.windowID() != 0 && m.windowID() != windowId) break;
					MouseState.motion(m.x(), m.y(), m.xrel(), m.yrel(), m.timestamp());
					break;
				}
				case SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN:
				case SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP: {
					SDL_MouseButtonEvent b = event.button();
					if (b.windowID() != 0 && b.windowID() != windowId) break;
					MouseState.button(b.button() & 0xFF, b.down(), b.timestamp());
					break;
				}
				case SDLEvents.SDL_EVENT_KEYMAP_CHANGED:
					KeyMap.layoutChanged();
					break;
				case SDLEvents.SDL_EVENT_MOUSE_WHEEL:
					MouseState.wheel(event.wheel().y(), event.wheel().timestamp());
					break;
				default:
					if (type >= SDLEvents.SDL_EVENT_WINDOW_FIRST && type <= SDLEvents.SDL_EVENT_WINDOW_LAST && event.window().windowID() == windowId) {
						windowEvent(type, event.common().timestamp());
					}
			}
		}
		KeyboardState.endOfPump();
	}

	private static void windowEvent(int type, long nanos) {
		switch (type) {
			case SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED:
				closeRequested = true;
				break;
			case SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED:
				active = true;
				// Relative mode is dropped by some systems while away; ask again.
				if (MouseState.grabbed() && !Settings.hidden) SDLMouse.SDL_SetWindowRelativeMouseMode(window, true);
				break;
			case SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST:
				active = false;
				KeyboardState.releaseAll(nanos);
				MouseState.releaseAll(nanos);
				break;
			case SDLEvents.SDL_EVENT_WINDOW_SHOWN:
			case SDLEvents.SDL_EVENT_WINDOW_RESTORED:
			case SDLEvents.SDL_EVENT_WINDOW_MAXIMIZED:
				visible = !Settings.hidden;
				dirty = true;
				sampleWindowed();
				break;
			case SDLEvents.SDL_EVENT_WINDOW_MOVED:
				sampleWindowed();
				break;
			case SDLEvents.SDL_EVENT_WINDOW_HIDDEN:
			case SDLEvents.SDL_EVENT_WINDOW_MINIMIZED:
				visible = false;
				break;
			case SDLEvents.SDL_EVENT_WINDOW_EXPOSED:
				dirty = true;
				break;
			case SDLEvents.SDL_EVENT_WINDOW_RESIZED:
			case SDLEvents.SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED:
			case SDLEvents.SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED:
			case SDLEvents.SDL_EVENT_WINDOW_DISPLAY_CHANGED:
				updateSize();
				sampleWindowed();
				break;
			default:
		}
	}

	private static void updateSize() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
			// A minimised window can report nothing; the game keeps its last size until it's back.
			if (!SDLVideo.SDL_GetWindowSizeInPixels(window, w, h) || w.get(0) <= 0 || h.get(0) <= 0) return;
			float d = SDLVideo.SDL_GetWindowPixelDensity(window);
			if (d > 0) density = d;
			if (w.get(0) != fbWidth || h.get(0) != fbHeight) {
				fbWidth = w.get(0);
				fbHeight = h.get(0);
				resized = true;
				dirty = true;
			}
		}
	}

	private static long createWindow(int w, int h, long flags, int[] at) {
		if (at == null) return SDLVideo.SDL_CreateWindow(title, w, h, flags);
		// Created where it belongs, so it takes that display's scale from the start rather than moving there.
		int props = SDLProperties.SDL_CreateProperties();
		try {
			SDLProperties.SDL_SetStringProperty(props, SDLVideo.SDL_PROP_WINDOW_CREATE_TITLE_STRING, title);
			SDLProperties.SDL_SetNumberProperty(props, SDLVideo.SDL_PROP_WINDOW_CREATE_X_NUMBER, at[0]);
			SDLProperties.SDL_SetNumberProperty(props, SDLVideo.SDL_PROP_WINDOW_CREATE_Y_NUMBER, at[1]);
			SDLProperties.SDL_SetNumberProperty(props, SDLVideo.SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER, w);
			SDLProperties.SDL_SetNumberProperty(props, SDLVideo.SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER, h);
			SDLProperties.SDL_SetNumberProperty(props, SDLVideo.SDL_PROP_WINDOW_CREATE_FLAGS_NUMBER, flags);
			return SDLVideo.SDL_CreateWindowWithProperties(props);
		} finally {
			SDLProperties.SDL_DestroyProperties(props);
		}
	}

	/**
	 * The placement's position, moved along with its display if that display (found by name) is somewhere else in
	 * the desktop now. Where the display is gone, the position stands and {@link #fitOnScreen} brings it back.
	 */
	private static int[] followDisplay(Placement p) {
		int[] at = {p.x, p.y};
		if (p.display == null) return at;
		IntBuffer ids = SDLVideo.SDL_GetDisplays();
		if (ids == null) return at;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Rect bounds = SDL_Rect.malloc(stack);
			boolean moved = false;
			for (int i = 0; i < ids.remaining(); i++) {
				int id = ids.get(i);
				if (!p.display.equals(SDLVideo.SDL_GetDisplayName(id)) || !SDLVideo.SDL_GetDisplayBounds(id, bounds)) continue;
				if (bounds.x() == p.displayX && bounds.y() == p.displayY) return new int[] {p.x, p.y};
				if (!moved) {
					// Two displays of one name (a matched pair): the one still where it was wins, else the first.
					at = new int[] {p.x + bounds.x() - p.displayX, p.y + bounds.y() - p.displayY};
					moved = true;
				}
			}
			return at;
		} finally {
			SDLStdinc.SDL_free(ids);
		}
	}

	/**
	 * Keeps the whole window, frame and title bar included, on the usable area (without taskbars) of the display it's
	 * on, or nearest to: moved in, and shrunk only if it's larger than that area. A window that fits is left alone.
	 */
	private static void fitOnScreen() {
		if (fullscreen) return;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1), w = stack.mallocInt(1), h = stack.mallocInt(1);
			IntBuffer top = stack.callocInt(1), left = stack.callocInt(1), bottom = stack.callocInt(1), right = stack.callocInt(1);
			if (!SDLVideo.SDL_GetWindowPosition(window, x, y) || !SDLVideo.SDL_GetWindowSize(window, w, h)) return;
			// Borders a window system can't tell yet (some, before the window is shown) read as 0.
			SDLVideo.SDL_GetWindowBordersSize(window, top, left, bottom, right);
			int display = SDLVideo.SDL_GetDisplayForWindow(window);
			SDL_Rect area = SDL_Rect.malloc(stack);
			if (display == 0 || !SDLVideo.SDL_GetDisplayUsableBounds(display, area) || area.w() <= 0 || area.h() <= 0) return;
			int t = top.get(0), l = left.get(0), b = bottom.get(0), r = right.get(0);
			int cw = Math.max(1, Math.min(w.get(0), area.w() - l - r));
			int ch = Math.max(1, Math.min(h.get(0), area.h() - t - b));
			int cx = Math.max(area.x() + l, Math.min(x.get(0), area.x() + area.w() - r - cw));
			int cy = Math.max(area.y() + t, Math.min(y.get(0), area.y() + area.h() - b - ch));
			if (cw == w.get(0) && ch == h.get(0) && cx == x.get(0) && cy == y.get(0)) return;
			if (cw != w.get(0) || ch != h.get(0)) SDLVideo.SDL_SetWindowSize(window, cw, ch);
			if (cx != x.get(0) || cy != y.get(0)) SDLVideo.SDL_SetWindowPosition(window, cx, cy);
			SDLVideo.SDL_SyncWindow(window);
			System.out.println("[purefaithful-lwjgl] Window fitted onto its display: " + w.get(0) + "x" + h.get(0) + " at " + x.get(0) + ","
					+ y.get(0) + " -> " + cw + "x" + ch + " at " + cx + "," + cy);
		}
	}

	/**
	 * Notes the window's windowed bounds (and their display) and whether it's maximised. Fullscreen and minimised
	 * leave the last values standing; maximised keeps the windowed bounds from before, which it restores to.
	 */
	private static void sampleWindowed() {
		if (window == 0 || fullscreen) return;
		long flags = SDLVideo.SDL_GetWindowFlags(window);
		if ((flags & (SDLVideo.SDL_WINDOW_FULLSCREEN | SDLVideo.SDL_WINDOW_MINIMIZED)) != 0) return;
		maximized = (flags & SDLVideo.SDL_WINDOW_MAXIMIZED) != 0;
		if (maximized) return;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1), w = stack.mallocInt(1), h = stack.mallocInt(1);
			if (!SDLVideo.SDL_GetWindowPosition(window, x, y) || !SDLVideo.SDL_GetWindowSize(window, w, h) || w.get(0) <= 0 || h.get(0) <= 0) return;
			windowedX = x.get(0);
			windowedY = y.get(0);
			windowedW = w.get(0);
			windowedH = h.get(0);
			int display = SDLVideo.SDL_GetDisplayForWindow(window);
			SDL_Rect bounds = SDL_Rect.malloc(stack);
			if (display != 0 && SDLVideo.SDL_GetDisplayBounds(display, bounds)) {
				windowedDisplay = SDLVideo.SDL_GetDisplayName(display);
				windowedDisplayX = bounds.x();
				windowedDisplayY = bounds.y();
			} else {
				windowedDisplay = null;
			}
		}
	}

	/**
	 * Where the window is, as a {@link Placement} that opens it there again: windowed bounds, maximised or not, its
	 * display. Fullscreen isn't part of it (the windowed state under it is). Null before any window existed.
	 */
	public static Placement placement() {
		sampleWindowed();
		if (windowedW <= 0) return null;
		Placement p = new Placement();
		p.x = windowedX;
		p.y = windowedY;
		p.width = windowedW;
		p.height = windowedH;
		p.maximized = maximized;
		p.display = windowedDisplay;
		p.displayX = windowedDisplayX;
		p.displayY = windowedDisplayY;
		return p;
	}

	// ---- queries --------------------------------------------------------------------------------------------------

	/** The swap interval asked for: 0 no vsync, 1 vsync (-1 before the window exists). */
	public static int swapInterval() {
		return swapInterval;
	}

	/** SDL's reason the GL refused the swap interval asked for; null if it accepted it. */
	public static String swapIntervalRefused() {
		return swapIntervalRefused;
	}

	public static int framebufferWidth() {
		return window != 0 ? fbWidth : mode().getWidth();
	}

	public static int framebufferHeight() {
		return window != 0 ? fbHeight : mode().getHeight();
	}

	public static float pixelDensity() {
		return density;
	}

	public static boolean takeCloseRequested() {
		boolean r = closeRequested;
		closeRequested = false;
		return r;
	}

	public static boolean active() {
		return active;
	}

	public static boolean visible() {
		return visible;
	}

	public static boolean dirty() {
		return dirty;
	}

	/**
	 * Called by each Display.update after its pump: LWJGL 2's wasResized held whether the window changed size, the same
	 * answer for every caller, until the next update.
	 */
	public static void latchResized() {
		resizedThisUpdate = resized;
		resized = false;
	}

	public static boolean wasResized() {
		return resizedThisUpdate;
	}

	public static int x() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1);
			return window != 0 && SDLVideo.SDL_GetWindowPosition(window, x, y) ? x.get(0) : initialX;
		}
	}

	public static int y() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1);
			return window != 0 && SDLVideo.SDL_GetWindowPosition(window, x, y) ? y.get(0) : initialY;
		}
	}

	public static GLCapabilities capabilities() {
		return capabilities;
	}

	public static ContextCapabilities legacyCapabilities() {
		return legacyCapabilities;
	}

	public static long context() {
		return context;
	}

	// ---- modes and fullscreen -------------------------------------------------------------------------------------

	public static DisplayMode mode() {
		if (mode == null) {
			try {
				mode = desktopMode();
			} catch (LWJGLException e) {
				mode = new DisplayMode(640, 480);
			}
		}
		return mode;
	}

	private static int display() {
		int d = window != 0 ? SDLVideo.SDL_GetDisplayForWindow(window) : 0;
		return d != 0 ? d : SDLVideo.SDL_GetPrimaryDisplay();
	}

	public static DisplayMode desktopMode() throws LWJGLException {
		Sdl.initVideo();
		SDL_DisplayMode m = SDLVideo.SDL_GetDesktopDisplayMode(display());
		if (m == null) throw new LWJGLException("No desktop display mode: " + Sdl.error());
		return Modes.of(m);
	}

	public static DisplayMode[] availableModes() throws LWJGLException {
		Sdl.initVideo();
		PointerBuffer list = SDLVideo.SDL_GetFullscreenDisplayModes(display());
		if (list == null) throw new LWJGLException("No display modes: " + Sdl.error());
		try {
			java.util.LinkedHashSet<DisplayMode> modes = new java.util.LinkedHashSet<>();
			for (int i = 0; i < list.remaining(); i++) modes.add(Modes.of(SDL_DisplayMode.create(list.get(i))));
			return modes.toArray(new DisplayMode[0]);
		} finally {
			SDLStdinc.SDL_free(list);
		}
	}

	/** LWJGL 2's setDisplayMode: the windowed size when windowed, the video mode when exclusive fullscreen. */
	public static void setMode(DisplayMode m) throws LWJGLException {
		if (m == null) throw new NullPointerException("mode must be non-null");
		mode = m;
		if (window == 0) return;
		if (fullscreen) {
			if (Settings.exclusiveFullscreen) applyFullscreen();
			return;
		}
		SDLVideo.SDL_SetWindowSize(window, Math.round(m.getWidth() / density), Math.round(m.getHeight() / density));
		SDLVideo.SDL_SyncWindow(window);
		updateSize();
	}

	public static void setFullscreen(boolean fs) throws LWJGLException {
		if (fs && !fullscreen) sampleWindowed();
		fullscreen = fs;
		if (window != 0) applyFullscreen();
	}

	public static boolean fullscreen() {
		return fullscreen;
	}

	/**
	 * Borderless by default: the window covers its display at the desktop's own mode, so switching is instant and
	 * alt-tab is harmless. Exclusive (an option) sets the closest real video mode to the one asked for.
	 */
	private static void applyFullscreen() throws LWJGLException {
		if (fullscreen && Settings.exclusiveFullscreen) {
			SDL_DisplayMode closest = SDL_DisplayMode.calloc();
			try {
				// A mode the system offered (fullscreen capable), else the desktop's: a windowed size isn't a video mode.
				DisplayMode want = mode().isFullscreenCapable() ? mode() : desktopMode();
				boolean found = SDLVideo.SDL_GetClosestFullscreenDisplayMode(display(), want.getWidth(), want.getHeight(),
						want.getFrequency(), true, closest);
				SDLVideo.SDL_SetWindowFullscreenMode(window, found ? closest : null);
			} finally {
				closest.free();
			}
		} else {
			SDLVideo.SDL_SetWindowFullscreenMode(window, null);
		}
		Sdl.check(SDLVideo.SDL_SetWindowFullscreen(window, fullscreen), "SDL_SetWindowFullscreen");
		SDLVideo.SDL_SyncWindow(window);
		updateSize();
	}

	// ---- small settings -------------------------------------------------------------------------------------------

	public static void setTitle(String t) {
		title = t == null ? "" : t;
		if (window != 0) SDLVideo.SDL_SetWindowTitle(window, title);
	}

	public static String title() {
		return title;
	}

	public static void setResizable(boolean r) {
		resizable = r;
		if (window != 0) SDLVideo.SDL_SetWindowResizable(window, r);
	}

	public static boolean resizable() {
		return resizable;
	}

	public static void setSwapInterval(int interval) {
		swapInterval = interval;
		if (context != 0) applySwapInterval();
	}

	/** Why the GL refused the last swap interval asked for, or null if it took it. */
	private static volatile String swapIntervalRefused;

	private static void applySwapInterval() {
		if (SDLVideo.SDL_GL_SetSwapInterval(swapInterval)) {
			swapIntervalRefused = null;
		} else {
			// Some GLs have no swap control at all (a virtual X server's software GL, some remote desktops): the
			// frames then go out unsynchronised whatever is asked.
			swapIntervalRefused = Sdl.error();
			System.out.println("[purefaithful-lwjgl] This GL refused swap interval " + swapInterval + ": " + swapIntervalRefused);
		}
	}

	public static void setLocation(int x, int y) {
		initialX = x;
		initialY = y;
		if (window != 0 && !fullscreen) {
			if (x < 0 || y < 0) SDLVideo.SDL_SetWindowPosition(window, SDLVideo.SDL_WINDOWPOS_CENTERED, SDLVideo.SDL_WINDOWPOS_CENTERED);
			else SDLVideo.SDL_SetWindowPosition(window, x, y);
		}
	}

	/** LWJGL 2's setIcon: RGBA images of 16, 32 or 128 pixels square; SDL takes one, the largest. */
	public static int setIcons(ByteBuffer[] buffers) {
		icons = buffers;
		return window != 0 ? applyIcon() : 1;
	}

	private static int applyIcon() {
		ByteBuffer best = null;
		int size = 0;
		for (ByteBuffer b : icons) {
			int s = (int) Math.round(Math.sqrt(b.remaining() / 4.0));
			if (s * s * 4 == b.remaining() && s > size) {
				best = b;
				size = s;
			}
		}
		if (best == null) return 0;
		ByteBuffer copy = BufferUtils.createByteBuffer(best.remaining());
		copy.put(best.duplicate()).flip();
		SDL_Surface surface = SDLSurface.SDL_CreateSurfaceFrom(size, size, SDLPixels.SDL_PIXELFORMAT_ABGR8888, copy, size * 4);
		if (surface == null) return 0;
		try {
			return SDLVideo.SDL_SetWindowIcon(window, surface) ? 1 : 0;
		} finally {
			SDLSurface.SDL_DestroySurface(surface);
		}
	}

	/** Text input (and the system IME) on or off; remembered until the window exists. */
	public static void textInput(boolean on) {
		textInputWanted = on;
		if (window == 0) return;
		if (on) SDLKeyboard.SDL_StartTextInput(window);
		else SDLKeyboard.SDL_StopTextInput(window);
	}

	/** Re-reads {@link Settings} that can change while running. */
	public static void refreshSettings() throws LWJGLException {
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE, Settings.rawMouse ? "0" : "1");
		if (window != 0 && fullscreen) applyFullscreen();
	}

	// ---- mouse ----------------------------------------------------------------------------------------------------

	public static void setGrabbed(boolean grab) {
		if (window == 0) {
			MouseState.setGrabbedState(grab);
			return;
		}
		if (grab == MouseState.grabbed()) return;
		MouseState.setGrabbedState(grab);
		// A hidden window (tests and benchmarks only) never touches the real cursor: relative mode clips it and a warp
		// moves it, and the session's one cursor is the user's even from a private desktop. The grab's semantics
		// (MouseState) still apply, so tests see what the game would.
		if (Settings.hidden) return;
		SDLMouse.SDL_SetWindowRelativeMouseMode(window, grab);
		if (!grab) warp(MouseState.x(), MouseState.y());
	}

	/** Moves the cursor to LWJGL 2 pixel coordinates (bottom-left origin). */
	public static void warp(int x, int y) {
		MouseState.position(x, y);
		if (window == 0 || MouseState.grabbed() || Settings.hidden) return;
		SDLMouse.SDL_WarpMouseInWindow(window, x / density, (fbHeight - 1 - y) / density);
	}

	public static boolean insideWindow() {
		return window != 0 && (SDLVideo.SDL_GetWindowFlags(window) & SDLVideo.SDL_WINDOW_MOUSE_FOCUS) != 0;
	}

	public static void cursorVisible(boolean show) {
		if (show) SDLMouse.SDL_ShowCursor();
		else SDLMouse.SDL_HideCursor();
	}

	/** SDL's id for the window, which events carry (tests push events with it). */
	public static int windowId() {
		return windowId;
	}
}
