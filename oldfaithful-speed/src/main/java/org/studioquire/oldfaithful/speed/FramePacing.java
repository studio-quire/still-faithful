package org.studioquire.oldfaithful.speed;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

import org.lwjgl.opengl.Display;

/**
 * Chunk building in time the frame would otherwise spend waiting for the display.
 *
 * b1.7.3 builds chunks in renderFrame: one compileChunks call per frame at Max FPS, and at Balanced more calls until
 * a deadline of 1/120 s after the previous frame's render. With vsync on, each frame waits for the refresh, so by the
 * time renderFrame runs that deadline has long passed and Balanced, like Max FPS, makes one call a frame: 60 a second
 * at 60 Hz, where without vsync it made hundreds. The far view then takes minutes to fill.
 *
 * Where the frame waits depends on the driver: in the buffer swap at the top of the game loop (or the "Pre render"
 * error check just before it), or, with a threaded driver such as NVIDIA's, in the first call after the swap that has
 * to synchronise with the driver: the frustum's matrix read when renderFrame makes its FrustumCuller, or any GL call
 * placed before it, chunk building included. So extra compileChunks calls (vanilla's own, unchanged, choosing chunks
 * as it always does) run at the top of the loop, after the ticks and before the error check and the swap, while the
 * driver is idle; the wait that follows only gets shorter. Everything after the wait, the moment the next frame
 * samples the game's time and input, and the moment it's shown, stays where it was. (Built after the wait instead,
 * they'd shift the next frame's sampling by however long they took, which varies, and moving things would judder
 * slightly; measured: a fifth of frames standing still went over 25 ms, at the same 60 fps.)
 *
 * How long: the wait ends ("release", measured where the culler is made) at the display's rhythm, so the next release
 * is due one period after the last: the frames' median interval, but never more than one refresh of the display, so
 * frames running late (vanilla's own lighting bursts, say) can't stretch the period and invite more building. Building stops early enough for the work still to come before it (measured each
 * frame, the most of recent frames) and a margin more. A long tick this frame leaves no room, rather than
 * being added to. A call that would make the game load or generate a chunk (NextPicks) is left to vanilla's own call
 * in renderFrame, as it would have been: it can take tens of milliseconds, more than any frame has spare. Without vsync and CPU-bound, the period is the frame's own work and the rule converges on building
 * nothing extra, so the game does exactly what it did. Nothing here decides what gets built or how, only how many of
 * vanilla's calls fit into a frame: the result is what a faster machine would show.
 *
 * Game thread only.
 */
public final class FramePacing {
	/**
	 * Left before the expected release: a quarter of the period, at least {@link #MARGIN_NS}. Covers what the game
	 * thread can't see: a threaded driver still compiling the display lists just built before it can take the swap,
	 * a call that runs long, a lighting burst, GC. (2 ms flat still let about one frame in 150 miss a refresh.)
	 */
	static final long MARGIN_NS = 3_000_000L;
	private static final int WINDOW = 16;

	private static final long[] periods = new long[WINDOW], tails = new long[WINDOW], sorted = new long[WINDOW];
	private static int filled, next;
	private static long lastRelease, buildEnd, waited;
	/** One refresh of the window's display, re-read every few seconds (the window may move); 0 if unknown. */
	private static long refreshNs;
	private static int refreshAge;
	/** Whether the world was rendered (the culler made) since the last loop; if not (menus, loading), nothing builds. */
	private static boolean culled;
	/** The longest of the last {@link #CALLS} compileChunks calls, as the estimate for the next one. */
	private static long callEstimate;
	private static final int CALLS = 32;
	private static final long[] calls = new long[CALLS];
	private static int nextCall;

	/** -Doldfaithful.speed.pacingLog=true: wait, room and extra calls, every 300 frames, in the log. */
	private static final boolean LOG = Boolean.getBoolean("oldfaithful.speed.pacingLog");
	private static boolean idle;
	private static int logFrames;
	private static long logWait, logRoom, logSpent, logCalls, logSkipped;

	private FramePacing() {
	}

	/**
	 * At the top of the loop, after the ticks: compileOnce (one vanilla compileChunks call, true when nothing is left)
	 * while there's room and safe says the next call is safe to make (it won't load or generate chunks: NextPicks).
	 * Null when there's no world.
	 */
	public static void build(BooleanSupplier safe, BooleanSupplier compileOnce) {
		waited = 0;
		boolean slept = idle;
		idle = false;
		if (compileOnce == null || !culled || slept) {
			culled = false;
			filled = 0;
			lastRelease = 0;
			buildEnd = 0;
			return;
		}
		culled = false;
		long now = System.nanoTime();
		if (filled == WINDOW) {
			long period = refreshNs > 0 ? Math.min(refreshNs, median(periods)) : min(periods);
			long deadline = lastRelease + period - max(tails) - Math.max(MARGIN_NS, period / 4);
			if (LOG) logRoom += Math.max(0L, deadline - now);
			while (now + callEstimate <= deadline) {
				if (!safe.getAsBoolean()) {
					if (LOG) logSkipped++;
					now = System.nanoTime();
					break;
				}
				boolean done = compileOnce.getAsBoolean();
				long t = System.nanoTime();
				estimate(t - now);
				if (LOG) {
					logSpent += t - now;
					logCalls++;
				}
				now = t;
				if (done) break;
			}
		}
		buildEnd = now;
	}

	/** Vanilla slept this frame (no focus): the next build does nothing and the timing window starts over. */
	public static void idle() {
		idle = true;
	}

	/** Time in a call where the frame may wait for the display. */
	public static void waited(long ns) {
		waited += ns;
	}

	/** Vanilla's own compileChunks call took this long. */
	public static void called(long ns) {
		estimate(ns);
	}

	private static void estimate(long ns) {
		calls[nextCall] = ns;
		nextCall = (nextCall + 1) % CALLS;
		callEstimate = max(calls);
	}

	/** After the culler is made: the frame's wait is over. Only the first culler after a loop counts (anaglyph renders twice). */
	public static void culled() {
		if (culled) return;
		culled = true;
		if (refreshAge-- <= 0) {
			refreshAge = 300;
			int hz = Display.getDesktopDisplayMode().getFrequency();
			refreshNs = hz > 0 ? 1_000_000_000L / hz : 0;
		}
		long release = System.nanoTime();
		if (lastRelease != 0 && buildEnd != 0) {
			periods[next] = release - lastRelease;
			tails[next] = Math.max(0L, release - buildEnd - waited);
			next = (next + 1) % WINDOW;
			if (filled < WINDOW) filled++;
		}
		lastRelease = release;
		if (LOG) {
			logWait += waited;
			if (++logFrames == 300) {
				System.out.printf("[purefaithful-speed] pacing per frame: wait %.2f ms, room %.2f ms, built in %.2f ms over %.1f calls, left to vanilla %d (would load); period %.2f ms (refresh %.2f), tail %.2f ms, call estimate %.2f ms%n",
						logWait / 3e8, logRoom / 3e8, logSpent / 3e8, logCalls / 300.0, logSkipped, filled == WINDOW ? median(periods) / 1e6 : 0.0, refreshNs / 1e6,
						filled == WINDOW ? max(tails) / 1e6 : 0.0, callEstimate / 1e6);
				logFrames = 0;
				logWait = logRoom = logSpent = logCalls = logSkipped = 0;
			}
		}
		waited = 0;
	}

	private static long median(long[] values) {
		System.arraycopy(values, 0, sorted, 0, WINDOW);
		Arrays.sort(sorted);
		return sorted[WINDOW / 2];
	}

	private static long min(long[] values) {
		long m = Long.MAX_VALUE;
		for (long v : values) m = Math.min(m, v);
		return m;
	}

	private static long max(long[] values) {
		long m = 0;
		for (long v : values) m = Math.max(m, v);
		return m;
	}
}
