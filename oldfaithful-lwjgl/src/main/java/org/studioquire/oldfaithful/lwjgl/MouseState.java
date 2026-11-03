package org.studioquire.oldfaithful.lwjgl;

import java.util.ArrayDeque;

/**
 * The state behind {@link org.lwjgl.input.Mouse}, in LWJGL 2's terms: positions in framebuffer pixels from the
 * bottom-left, wheel in Windows' units (120 per notch, fractions kept for smooth wheels and touchpads), buttons
 * numbered left, right, middle, then extras. While grabbed, motion is SDL's relative motion and the position
 * moves by it within the window, as LWJGL 2's grabbed position did.
 */
public final class MouseState {
	public static final class Event {
		public int button = -1;
		public boolean state;
		public int dx, dy, dwheel, x, y;
		public long nanos;

		boolean motionOnly() {
			return button == -1 && dwheel == 0;
		}
	}

	private static final int QUEUE_LIMIT = 4096;

	public static boolean created;
	public static boolean clip = true;
	private static boolean grabbed;
	private static int x, y;
	private static int accumDX, accumDY, accumWheel;
	private static float wheelRemainder, relRemainderX, relRemainderY;
	/**
	 * The frame's input cut-off (SDL's clock), set by {@link #frameStart()}: grabbed motion stamped after it, but before
	 * the pump that read it began, belongs to the next frame. Without a cut-off (another game, or no display mod) every
	 * event counts as soon as it's pumped, as in LWJGL 2.
	 */
	private static long cutoff = Long.MAX_VALUE, pumpStart;
	private static int pendingDX, pendingDY;
	/** The same motion unrounded: what the deltas taken last came to before whole pixels (sub-pixel look). */
	private static float accumFX, accumFY, pendingFX, pendingFY, takenFX, takenFY;
	private static final boolean[] buttons = new boolean[16];
	private static final ArrayDeque<Event> queue = new ArrayDeque<>();
	private static Event current = new Event();
	/** The last queued event, while nothing has read it; consecutive motion merges into it. */
	private static Event tail;

	private MouseState() {
	}

	/** Motion in SDL window coordinates (top-left origin) and, for a grabbed mouse, relative device motion. */
	public static void motion(float winX, float winY, float relX, float relY, long nanos) {
		int dx, dy;
		boolean later = grabbed && nanos > cutoff && nanos < pumpStart;
		if (grabbed) {
			if (later) {
				pendingFX += relX;
				pendingFY -= relY;
			} else {
				accumFX += relX;
				accumFY -= relY;
			}
			float rx = relX + relRemainderX, ry = relY + relRemainderY;
			dx = (int) rx;
			dy = -(int) ry;
			relRemainderX = rx - (int) rx;
			relRemainderY = ry - (int) ry;
			x = clampX(x + dx);
			y = clampY(y + dy);
		} else {
			int nx = Math.round(winX * Window.pixelDensity());
			int ny = Window.framebufferHeight() - 1 - Math.round(winY * Window.pixelDensity());
			if (clip) {
				nx = clampX(nx);
				ny = clampY(ny);
			}
			dx = nx - x;
			dy = ny - y;
			x = nx;
			y = ny;
		}
		if (dx == 0 && dy == 0) return;
		if (later) {
			pendingDX += dx;
			pendingDY += dy;
		} else {
			accumDX += dx;
			accumDY += dy;
		}
		if (tail != null && tail.motionOnly() && tail == queue.peekLast()) {
			tail.dx += dx;
			tail.dy += dy;
			tail.x = x;
			tail.y = y;
			tail.nanos = nanos;
			return;
		}
		Event e = event(nanos);
		e.dx = dx;
		e.dy = dy;
		add(e);
	}

	/** SDL button numbers (1 left, 2 middle, 3 right, 4 and 5 extras) to LWJGL 2's (0 left, 1 right, 2 middle, 3, 4). */
	public static int toLwjglButton(int sdlButton) {
		switch (sdlButton) {
			case 1: return 0;
			case 2: return 2;
			case 3: return 1;
			default: return sdlButton - 1;
		}
	}

	public static void button(int sdlButton, boolean down, long nanos) {
		int b = toLwjglButton(sdlButton);
		if (b < 0 || b >= buttons.length) return;
		buttons[b] = down;
		Event e = event(nanos);
		e.button = b;
		e.state = down;
		add(e);
	}

	public static void wheel(float notches, long nanos) {
		float w = notches * 120f + wheelRemainder;
		int units = (int) w;
		wheelRemainder = w - units;
		if (units == 0) return;
		accumWheel += units;
		Event e = event(nanos);
		e.dwheel = units;
		add(e);
	}

	private static Event event(long nanos) {
		Event e = new Event();
		e.x = x;
		e.y = y;
		e.nanos = nanos;
		return e;
	}

	private static void add(Event e) {
		// A game that never reads events (or a stall) mustn't grow the queue without bound; LWJGL 2 dropped too.
		if (queue.size() >= QUEUE_LIMIT) queue.poll();
		queue.add(e);
		tail = e;
	}

	private static int clampX(int v) {
		return Math.max(0, Math.min(Window.framebufferWidth() - 1, v));
	}

	private static int clampY(int v) {
		return Math.max(0, Math.min(Window.framebufferHeight() - 1, v));
	}

	public static boolean next() {
		Event e = queue.poll();
		if (e == null) return false;
		current = e;
		if (e == tail) tail = null;
		return true;
	}

	public static Event current() {
		return current;
	}

	public static int takeDX() {
		int v = accumDX;
		accumDX = 0;
		takenFX = accumFX;
		accumFX = 0;
		return v;
	}

	public static int takeDY() {
		int v = accumDY;
		accumDY = 0;
		takenFY = accumFY;
		accumFY = 0;
		return v;
	}

	/** The grabbed motion behind the last takeDX/takeDY, before rounding to whole pixels. */
	public static float takenFX() {
		return takenFX;
	}

	public static float takenFY() {
		return takenFY;
	}

	/**
	 * The game's frame begins (b1.7.3 samples its timer here): grabbed motion up to now is this frame's, and what an
	 * earlier pump read from after the previous cut-off joins it. Under vsync the pump (Display.update) comes at a
	 * different point of each frame, depending on the ticks before it and where the driver makes the frame wait, so
	 * motion read up to the pump turns the view by uneven amounts per frame shown; motion up to the frame's start is
	 * one loop's worth each time, at the instant the frame's interpolation also uses.
	 */
	public static void frameStart() {
		frameStart(org.lwjgl.sdl.SDLTimer.SDL_GetTicksNS());
	}

	/** As {@link #frameStart()}, with the frame's instant given (SDL's clock, not later than now). */
	public static void frameStart(long nanos) {
		cutoff = nanos;
		accumDX += pendingDX;
		accumDY += pendingDY;
		accumFX += pendingFX;
		accumFY += pendingFY;
		pendingDX = pendingDY = 0;
		pendingFX = pendingFY = 0;
	}

	/** Back to counting every event as soon as it's pumped (LWJGL 2's way), until the next frameStart. */
	public static void clearCutoff() {
		frameStart();
		cutoff = Long.MAX_VALUE;
	}

	/** Window.pump begins: events stamped from here on were stamped as they were read, not as they happened. */
	static void pumpStarted() {
		pumpStart = org.lwjgl.sdl.SDLTimer.SDL_GetTicksNS();
	}

	public static int takeDWheel() {
		int v = accumWheel;
		accumWheel = 0;
		return v;
	}

	public static int x() {
		return x;
	}

	public static int y() {
		return y;
	}

	/** Sets the position (LWJGL 2 pixels), as setCursorPosition does, without an event. */
	public static void position(int nx, int ny) {
		x = clip ? clampX(nx) : nx;
		y = clip ? clampY(ny) : ny;
	}

	public static boolean isButtonDown(int b) {
		return b >= 0 && b < buttons.length && buttons[b];
	}

	public static boolean grabbed() {
		return grabbed;
	}

	public static void setGrabbedState(boolean g) {
		grabbed = g;
		accumDX = accumDY = 0;
		relRemainderX = relRemainderY = 0;
		pendingDX = pendingDY = 0;
		accumFX = accumFY = pendingFX = pendingFY = takenFX = takenFY = 0;
	}

	/** Focus lost: buttons held when the window lost focus are released. */
	public static void releaseAll(long nanos) {
		for (int b = 0; b < buttons.length; b++) {
			if (!buttons[b]) continue;
			buttons[b] = false;
			Event e = event(nanos);
			e.button = b;
			add(e);
		}
	}

	public static void reset() {
		queue.clear();
		java.util.Arrays.fill(buttons, false);
		current = new Event();
		tail = null;
		accumDX = accumDY = accumWheel = 0;
		wheelRemainder = 0;
		pendingDX = pendingDY = 0;
		accumFX = accumFY = pendingFX = pendingFY = takenFX = takenFY = 0;
		cutoff = Long.MAX_VALUE;
	}
}
