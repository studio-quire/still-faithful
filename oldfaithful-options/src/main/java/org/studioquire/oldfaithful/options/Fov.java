package org.studioquire.oldfaithful.options;

/**
 * The field-of-view setting. Beta's view is 70 degrees on land, 60 under water, narrowing as the player dies; the
 * setting scales all of it by {@code degrees / 70}, as later versions did, so those changes keep their proportions.
 * The hand and held item stay at beta's own view (GameRendererMixin), as they do in later versions.
 */
public final class Fov {
	public static final int MIN = 30;
	public static final int MAX = 110;
	public static final int BETA = 70;
	/** The key in options.txt, in whole degrees (later versions' key and unit); b1.7.3 ignores lines it doesn't know. */
	public static final String KEY = "fov";

	/** The setting, in degrees. */
	public static volatile int degrees = BETA;

	private Fov() {
	}

	public static int clamp(int d) {
		return Math.max(MIN, Math.min(MAX, d));
	}

	/** A slider position (0 to 1) to whole degrees. */
	public static int fromSlider(float position) {
		return clamp(MIN + Math.round(position * (MAX - MIN)));
	}

	public static float toSlider(int d) {
		return (float) (clamp(d) - MIN) / (MAX - MIN);
	}

	public static String label(int d) {
		return "FOV: " + (d == BETA ? "Normal" : String.valueOf(d));
	}

	/** An options.txt value: whole or fractional degrees; null if unreadable. */
	public static Integer parse(String value) {
		try {
			float f = Float.parseFloat(value.trim());
			return Float.isNaN(f) ? null : clamp(Math.round(f));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Beta's view (in degrees, whatever it is at the moment) under the setting. */
	public static float apply(float betaFov) {
		return betaFov * degrees / BETA;
	}

	/** The mouse's x in GUI units to the pixel (see oldfaithful-fixes' SliderSteps; the same arithmetic). */
	public static float pointerX(int pixelX, int scale, int guiX) {
		float x = (float) pixelX / Math.max(1, scale);
		return Math.abs(x - guiX) <= 1.5F ? x : guiX;
	}
}
