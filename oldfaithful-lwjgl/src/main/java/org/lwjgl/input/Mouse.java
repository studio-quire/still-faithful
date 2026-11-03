package org.lwjgl.input;

import org.lwjgl.LWJGLException;
import org.studioquire.oldfaithful.lwjgl.MouseState;
import org.studioquire.oldfaithful.lwjgl.Window;

/**
 * LWJGL 2's Mouse on SDL3 (Old Faithful's implementation, written fresh): framebuffer pixels from the bottom-left;
 * when grabbed, SDL's relative mode, moving as the desktop pointer would (the system's speed and acceleration, as
 * LWJGL 2 read it on Windows) unless the player chose raw input.
 */
public class Mouse {
	public static final int EVENT_SIZE = 22;

	private static Cursor nativeCursor;

	private Mouse() {
	}

	public static Cursor getNativeCursor() {
		return nativeCursor;
	}

	/** Custom cursor images aren't drawn; an empty (invisible) cursor hides the system one, null shows it again. */
	public static Cursor setNativeCursor(Cursor cursor) throws LWJGLException {
		Cursor old = nativeCursor;
		nativeCursor = cursor;
		if (Window.created() && !MouseState.grabbed()) Window.cursorVisible(cursor == null || !cursor.isEmpty());
		return old;
	}

	public static boolean isClipMouseCoordinatesToWindow() {
		return MouseState.clip;
	}

	public static void setClipMouseCoordinatesToWindow(boolean clip) {
		MouseState.clip = clip;
	}

	public static void setCursorPosition(int x, int y) {
		if (!MouseState.created) throw new IllegalStateException("Mouse is not created");
		Window.warp(x, y);
	}

	public static void create() throws LWJGLException {
		if (!Window.created()) throw new IllegalStateException("Display must be created.");
		MouseState.created = true;
	}

	public static boolean isCreated() {
		return MouseState.created;
	}

	public static void destroy() {
		if (MouseState.grabbed()) Window.setGrabbed(false);
		MouseState.created = false;
		MouseState.reset();
	}

	/** Events are read by Display.update/processMessages; nothing more to fetch here. */
	public static void poll() {
		if (!MouseState.created) throw new IllegalStateException("Mouse must be created before you can poll it");
	}

	public static boolean isButtonDown(int button) {
		if (!MouseState.created) throw new IllegalStateException("Mouse must be created before you can poll the button state.");
		return MouseState.isButtonDown(button);
	}

	public static String getButtonName(int button) {
		return button >= 0 && button < 16 ? "BUTTON" + button : null;
	}

	public static int getButtonIndex(String name) {
		if (name != null && name.startsWith("BUTTON")) {
			try {
				int b = Integer.parseInt(name.substring(6));
				return b >= 0 && b < 16 ? b : -1;
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return -1;
	}

	public static boolean next() {
		if (!MouseState.created) throw new IllegalStateException("Mouse must be created before you can read events");
		return MouseState.next();
	}

	public static int getEventButton() {
		return MouseState.current().button;
	}

	public static boolean getEventButtonState() {
		return MouseState.current().state;
	}

	public static int getEventDX() {
		return MouseState.current().dx;
	}

	public static int getEventDY() {
		return MouseState.current().dy;
	}

	public static int getEventX() {
		return MouseState.current().x;
	}

	public static int getEventY() {
		return MouseState.current().y;
	}

	public static int getEventDWheel() {
		return MouseState.current().dwheel;
	}

	public static long getEventNanoseconds() {
		return MouseState.current().nanos;
	}

	public static int getX() {
		return MouseState.x();
	}

	public static int getY() {
		return MouseState.y();
	}

	public static int getDX() {
		return MouseState.takeDX();
	}

	public static int getDY() {
		return MouseState.takeDY();
	}

	public static int getDWheel() {
		return MouseState.takeDWheel();
	}

	/** Left, right, middle and two side buttons; SDL can't tell how many a device really has. */
	public static int getButtonCount() {
		return 5;
	}

	public static boolean hasWheel() {
		return true;
	}

	public static boolean isGrabbed() {
		return MouseState.grabbed();
	}

	public static void setGrabbed(boolean grab) {
		Window.setGrabbed(grab);
	}

	public static void updateCursor() {
	}

	public static boolean isInsideWindow() {
		return Window.insideWindow();
	}
}
