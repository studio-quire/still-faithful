package org.studioquire.oldfaithful.display;

import org.lwjgl.opengl.Display;
import org.lwjgl.sdl.SDLTimer;
import org.studioquire.oldfaithful.lwjgl.MouseState;
import org.studioquire.oldfaithful.lwjgl.Window;

/**
 * The instant each frame stands for (frame.smooth, on by default). b1.7.3 reads the time at the top of its loop, in
 * whole milliseconds (Timer.advance), and the mouse when Display.update returns. Under vsync every frame is shown one
 * refresh after the last, but the top of the loop lands at a slightly different point of each refresh (the frame
 * before took a little more or less after the display released it), the millisecond rounding adds up to a millisecond
 * more, and the mouse read moves with the ticks before it: so walking, turning and everything interpolated moves by
 * uneven amounts per frame shown, a fine stutter. Here, with vsync on, a frame's instant is the previous one plus the
 * display's period, drawn gently towards the measured top of the loop so it stays locked to it; a frame that misses a
 * refresh (or any jump of more than half a period) takes the measured time as it is. Both the game's timer and the
 * mouse's cut-off use that instant, so motion and turning agree. Without vsync, or with frame.smooth=false, the
 * measured time is used, at full precision with the setting on and exactly as vanilla's with it off.
 */
public final class FrameClock {
	public static volatile boolean enabled = true;

	private static final int WINDOW = 31;
	private static final long[] intervals = new long[WINDOW], sorted = new long[WINDOW];
	private static int next, filled, refreshAge;
	private static long lastRaw, last, nominal;
	private static long frame;

	private FrameClock() {
	}

	/** The top of the game loop: fixes this frame's instant and gives the mouse its cut-off. */
	public static void frameStart() {
		long now = SDLTimer.SDL_GetTicksNS();
		long t = now;
		if (lastRaw != 0) {
			intervals[next] = now - lastRaw;
			next = (next + 1) % WINDOW;
			if (filled < WINDOW) filled++;
		}
		if (enabled && Window.swapInterval() > 0 && last != 0) {
			long period = period();
			if (period > 0) {
				long predicted = last + period;
				long err = now - predicted;
				if (Math.abs(err) <= period / 2) t = predicted + err / 8;
			}
		}
		lastRaw = now;
		last = t;
		frame = t;
		MouseState.frameStart(enabled ? t : now);
	}

	/** This frame's instant in seconds, for Timer.advance. */
	public static double seconds() {
		return frame / 1e9;
	}

	/** The display's period: the median frame interval when it's within a tenth of the refresh, else the refresh. */
	private static long period() {
		if (refreshAge-- <= 0) {
			refreshAge = 300;
			int hz = Display.getDesktopDisplayMode().getFrequency();
			nominal = hz > 0 ? 1_000_000_000L / hz : 0;
		}
		if (nominal <= 0) return 0;
		if (filled < WINDOW) return nominal;
		System.arraycopy(intervals, 0, sorted, 0, WINDOW);
		java.util.Arrays.sort(sorted);
		long median = sorted[WINDOW / 2];
		return Math.abs(median - nominal) * 10 <= nominal ? median : nominal;
	}
}
