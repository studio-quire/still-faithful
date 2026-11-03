package org.studioquire.oldfaithful.hardening.packet;

/**
 * How fast a player's own movement may make the server load chunks (S6 in docs/multiplayer.md).
 *
 * <p>Vanilla loads (or generates) the chunks a move brings into view right away, on the server thread, and accepts up
 * to 101 move packets a tick of up to 10 blocks each. A client sending moves as fast as that allows travels about
 * 1,000 blocks a tick and has the server generate dozens of chunk rows every tick: the whole server stalls.
 *
 * <p>Here each player has a budget of chunks their movement may bring into view, refilled every tick. When a move
 * needs more than is left, the chunk map's update waits (the player keeps the chunks they had) until the budget
 * covers it. The numbers are set so that no honest b1.7.3 movement reaches them:
 * <ul>
 * <li>Refill: a quarter of a row a tick, which is what travelling 4 blocks a tick in a straight line needs (2.8 on a
 * diagonal). The fastest sustained travel in b1.7.3 is a minecart or boat, clamped to 0.4 blocks a tick on each axis
 * (0.57 diagonally); walking is about 0.2, and falling doesn't cross chunk columns.</li>
 * <li>Capacity: a whole view area plus 64 rows, banked while standing still or travelling slower. The largest honest
 * burst is a lagged client's moves arriving at once; the server drops a client silent for a minute, and a minute at
 * the fastest honest speed, diagonally, is about 60 rows. The refill outpaces honest travel, so the bank is there
 * when the burst comes. A player starts with eight rows (they just loaded their whole view by logging in).</li>
 * <li>The update that brings the view to where the server teleported the player (a command, rubber-banding) is free:
 * the server chose the place.
 * Logins, respawns and portals don't go through this at all.</li>
 * </ul>
 */
public final class MoveLoading {
	/** Rows of view per tick the budget refills: 4 blocks a tick of straight travel. */
	public static final double ROWS_PER_TICK = 0.25;
	/** Extra rows the budget holds beyond a whole view area. */
	public static final int EXTRA_ROWS = 64;
	/** Rows a player starts with. */
	public static final int START_ROWS = 8;

	private MoveLoading() {
	}

	/** Side of the square of chunks a player sees: 2r + 1 for view distance r. */
	public static int side(int viewDistance) {
		return 2 * viewDistance + 1;
	}

	public static double refill(int viewDistance) {
		return side(viewDistance) * ROWS_PER_TICK;
	}

	public static double start(int viewDistance) {
		return (double) START_ROWS * side(viewDistance);
	}

	public static double capacity(int viewDistance) {
		int s = side(viewDistance);
		return (double) s * s + (double) EXTRA_ROWS * s;
	}

	/** Chunks that come into view when the view's centre moves by (dx, dz) chunks. */
	public static int newChunks(int viewDistance, int dx, int dz) {
		int s = side(viewDistance);
		long overlap = (long) Math.max(0, s - Math.abs(dx)) * Math.max(0, s - Math.abs(dz));
		return (int) ((long) s * s - overlap);
	}
}
