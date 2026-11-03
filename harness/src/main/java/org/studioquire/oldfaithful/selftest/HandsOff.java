package org.studioquire.oldfaithful.selftest;

/**
 * Launches by our tests run the game on a private desktop while the user works on theirs. The session has one real
 * cursor, and grabbing the mouse reaches it from any desktop: LWJGL 2 grabbed calls ClipCursor (the window's rect)
 * and SetCursorPos (re-centring each poll; b1.7.3's parented display skips its foreground check), and SDL's relative
 * mode clips with focus. A benchmark run on LWJGL 2 took the user's mouse this way (2026-11-01). So in those
 * launches the game never grabs or warps the mouse (mixin.MouseHandsOffMixin); the game still believes it did.
 * tools/private_desktop.py watches the cursor as well.
 */
public final class HandsOff {
	public static final boolean CURSOR = Bench.ENABLED || Boolean.getBoolean("oldfaithful.selftest.startup");

	private HandsOff() {
	}

	/** Logs each blocked call, so a run's output shows the cursor was left alone. */
	public static void blocked(String call) {
		System.out.println("[oldfaithful-handsoff] blocked " + call + " (the game believes it ran)");
	}
}
