package org.lwjgl.input;

import org.lwjgl.LWJGLException;

/**
 * LWJGL 2's Controllers, with none attached: b1.7.3 sets them up but never reads them, and LWJGL 2's came from
 * jinput's 2011 natives. SDL's gamepad API can back this later.
 */
public class Controllers {
	private static boolean created;

	private Controllers() {
	}

	public static void create() throws LWJGLException {
		created = true;
	}

	public static Controller getController(int index) {
		throw new IndexOutOfBoundsException("No controllers (index " + index + ")");
	}

	public static int getControllerCount() {
		return 0;
	}

	public static void poll() {
	}

	public static boolean next() {
		return false;
	}

	public static boolean isCreated() {
		return created;
	}

	public static void destroy() {
		created = false;
	}

	public static void clearEvents() {
	}

	public static Controller getEventSource() {
		return null;
	}

	public static int getEventControlIndex() {
		return 0;
	}

	public static boolean isEventButton() {
		return false;
	}

	public static boolean isEventAxis() {
		return false;
	}

	public static boolean isEventXAxis() {
		return false;
	}

	public static boolean isEventYAxis() {
		return false;
	}

	public static boolean isEventPovX() {
		return false;
	}

	public static boolean isEventPovY() {
		return false;
	}

	public static long getEventNanoseconds() {
		return 0;
	}

	public static boolean getEventButtonState() {
		return false;
	}

	public static float getEventXAxisValue() {
		return 0;
	}

	public static float getEventYAxisValue() {
		return 0;
	}
}
