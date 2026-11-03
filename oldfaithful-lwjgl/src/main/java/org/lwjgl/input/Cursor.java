package org.lwjgl.input;

import java.nio.IntBuffer;

import org.lwjgl.LWJGLException;

/**
 * LWJGL 2's Cursor. b1.7.3 makes a fully transparent one (and never shows it); images aren't drawn here, only
 * whether a cursor is empty is kept, so setting one hides or shows the system cursor.
 */
public class Cursor {
	public static final int CURSOR_ONE_BIT_TRANSPARENCY = 1;
	public static final int CURSOR_8_BIT_ALPHA = 2;
	public static final int CURSOR_ANIMATION = 4;

	private final boolean empty;
	private boolean destroyed;

	public Cursor(int width, int height, int xHotspot, int yHotspot, int numImages, IntBuffer images, IntBuffer delays) throws LWJGLException {
		boolean anyPixel = false;
		for (int i = images.position(); i < images.limit(); i++) {
			if ((images.get(i) >>> 24) != 0) {
				anyPixel = true;
				break;
			}
		}
		empty = !anyPixel;
	}

	public static int getMinCursorSize() {
		return 1;
	}

	public static int getMaxCursorSize() {
		return 256;
	}

	public static int getCapabilities() {
		return CURSOR_ONE_BIT_TRANSPARENCY | CURSOR_8_BIT_ALPHA;
	}

	boolean isEmpty() {
		return empty;
	}

	public void destroy() {
		destroyed = true;
	}
}
