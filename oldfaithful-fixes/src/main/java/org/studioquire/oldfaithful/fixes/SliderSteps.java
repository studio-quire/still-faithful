package org.studioquire.oldfaithful.fixes;

/**
 * #40: an options slider took its value from the mouse's position in whole GUI units along its 142-unit track: 143
 * positions at any window size, while the labels count in whole percentages, 101 for the volumes and 201 for
 * sensitivity (labelled 0 to 200%), so many sensitivities couldn't be chosen at all, and the stored value rarely
 * matched its label exactly. The fix reads the mouse in screen pixels, which a GUI scale of s divides into s per unit
 * (142s + 1 positions), and snaps to the label's steps. Every label is then reachable at GUI scale Normal (2) and up;
 * at Small (1) the volumes all are, and 143 of sensitivity's 201.
 * <p>
 * The game labels a value v as {@code (int)(v * steps)} percent (float arithmetic), so a step's value is the smallest
 * float that labels as that step.
 */
public final class SliderSteps {
	/** The track: the slider is 150 units wide and its knob 8. */
	public static final int TRACK = 142;

	private SliderSteps() {
	}

	/** The value labelled as step {@code k} of {@code steps}, k from 0 to steps (0 and 1 exactly at the ends). */
	public static float value(int k, int steps) {
		if (k <= 0) return 0.0F;
		if (k >= steps) return 1.0F;
		float v = (float) k / steps;
		while ((int) (v * steps) < k) v = Math.nextUp(v);
		while (v > 0.0F && (int) (Math.nextDown(v) * steps) >= k) v = Math.nextDown(v);
		return v;
	}

	/** The step a value is labelled as. */
	public static int step(float value, int steps) {
		return (int) (value * steps);
	}

	/** A slider position (0 to 1) snapped to the nearest labelled step. */
	public static float snap(float raw, int steps) {
		return value(Math.round(raw * steps), steps);
	}

	/**
	 * The mouse's x in GUI units to the pixel: the GUI is drawn at {@code scale} pixels a unit from the window's left
	 * edge. {@code guiX} is the whole-unit position the screen worked out from the same mouse; if the two disagree
	 * (a caller passing some other position), that one is kept.
	 */
	public static float pointerX(int pixelX, int scale, int guiX) {
		float x = (float) pixelX / Math.max(1, scale);
		return Math.abs(x - guiX) <= 1.5F ? x : guiX;
	}

	/** The slider position (0 to 1) for a pointer at {@code x} GUI units, on a track starting at {@code trackX}. */
	public static float position(float x, int trackX) {
		return Math.max(0.0F, Math.min(1.0F, (x - trackX) / TRACK));
	}

	/**
	 * How far right of where beta draws the knob (at whole units) it goes to sit on the pixel its value falls in, in GUI
	 * units: 0 at scale 1, so the knob stays exactly where beta put it there.
	 */
	public static float knobOffset(float value, int scale) {
		float units = value * TRACK;
		return (float) Math.floor(units * scale) / scale - (int) units;
	}
}
