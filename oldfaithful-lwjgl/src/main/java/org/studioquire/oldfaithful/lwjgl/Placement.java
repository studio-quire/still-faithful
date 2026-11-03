package org.studioquire.oldfaithful.lwjgl;

/**
 * Where and how the window opens ({@link Settings#placement}), or where it was ({@link Window#placement()}): its
 * windowed bounds, in SDL's window coordinates (pixels on Windows), whether it's maximised, and the display it was
 * on. A position may be anywhere; the window is fitted onto a display that exists when it opens.
 */
public final class Placement {
	public static final int CENTRED = Integer.MIN_VALUE;

	/** Top-left of the client area; {@link #CENTRED} centres the window on the primary display. */
	public int x = CENTRED, y = CENTRED;
	/** Client size; 0 takes the game's own size (scaled per {@link Settings#scaleInitialSize}). */
	public int width, height;
	public boolean maximized;
	/** The display the windowed bounds were on and its top-left then, to follow it if the layout changed; or null. */
	public String display;
	public int displayX, displayY;

	public boolean positioned() {
		return x != CENTRED && y != CENTRED;
	}

	public boolean sized() {
		return width > 0 && height > 0;
	}

	@Override
	public String toString() {
		return (sized() ? width + "x" + height : "game's size") + (positioned() ? " at " + x + "," + y : " centred")
				+ (display != null ? " on " + display : "") + (maximized ? ", maximised" : "");
	}
}
