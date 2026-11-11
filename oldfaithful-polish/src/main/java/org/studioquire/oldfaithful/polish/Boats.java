package org.studioquire.oldfaithful.polish;

/**
 * #12's numbers. Beta's boats go at most 0.4 blocks a tick along each axis (8 m/s). Beta breaks a boat whenever it has
 * hit something sideways in a tick and is still moving faster than 0.15 along the free axis: a boat sliding along a
 * shore, a floating item or another obstacle at an angle. Hitting a wall head-on, whatever the speed, zeroes the
 * blocked axis and leaves nothing to slide, so it never broke (beta players learnt to land head-on).
 * <p>
 * Sturdy: a break also needs a real crash, the boat's speed into what it hit at least {@link #CRASH} (over 60% of top
 * speed). So every break here would also have been one in beta, and scrapes and slow landings never are.
 * <p>
 * Handling: when the rider isn't paddling (or there's no rider), a boat in water loses speed faster: {@link #COAST}
 * a tick on top of beta's 0.99, so from top speed it stops in about eight blocks instead of about forty. While
 * paddling nothing changes, so top speed, acceleration and turning are beta's. A boat Boat Steering moves
 * ({@link BoatSteering}) has that switch's momentum instead of the coasting.
 * <p>
 * Handling also lets a boat come to rest at the surface. Beta pushes a boat up by {@link #PUSH} × (2 × wet − 1) a tick,
 * counting the wet share in fifths, from five slices of its box (slice i, 0.12 high, starts 0.12 i − 0.125 above the
 * box's bottom; it's wet if the water's surface is above that start). Near the surface the push is +0.008 with three
 * slices wet and −0.008 with two, never nothing, so the boat swings across the boundary between them for good: about
 * 0.1 blocks up and down, a high point every 8 ticks (measured idle and ridden, 2026-11-11; 0.036 to 0.081 paddled,
 * as the swing depends on what came before). Here, partly in the water, the share is worked out from where the surface
 * lies against the box ({@link #wetShare}): beta's steps smoothed through their middles, rising a fifth for every 0.12
 * deeper as beta's count does, so the push fades to nothing at one height, {@link #REST}, and the boat settles there
 * in two or three seconds, its rise and fall damped by {@link #BOB} a tick on top of beta's 0.95. Fully under water
 * and out of it, beta's push; the ground's halving stands.
 */
public final class Boats {
	public static final double CRASH = 0.25;
	public static final double COAST = 0.96;
	public static final double BOB = 0.8;
	/** A rider moving less than this a tick (their own input, which the boat takes a fifth of) isn't paddling. */
	public static final double PADDLING = 0.001;
	/** Beta's buoyancy, 0.04F as the double its tick adds. */
	public static final double PUSH = 0.04F;
	/**
	 * Where a boat floats: its y, the middle of its box, this far over the water's surface. Beta's boundary between two
	 * and three slices wet is 0.185 over, and its swing centres there (measured 0.185 idle, 0.180 to 0.184 paddled). This is
	 * 6/32 and a hair (0.0035 higher): a server sends entities' positions in 32nds and every 20 seconds floors its own
	 * entity's position to them (`EntityTrackerEntry`), so a boat a hair over a 32nd isn't moved by it and every client
	 * draws it at the same height. Over a 32nd only by a hair, so float rounding never puts it under one.
	 */
	public static final double REST = 6 / 32.0 + 1 / 1024.0;
	/** The boat's box is 0.6 high; beta's slices are fifths of it. */
	private static final double HEIGHT = 0.6;

	private Boats() {
	}

	/** The speed lost into what the boat hit, from what it asked to move and what its velocity is after. */
	public static double impact(double dx, double dz, double vxAfter, double vzAfter) {
		double x = dx != 0 && vxAfter == 0 ? dx : 0, z = dz != 0 && vzAfter == 0 ? dz : 0;
		return Math.sqrt(x * x + z * z);
	}

	/**
	 * The wet share a boat at {@code y} floats by with the water's surface at {@code surface}: a half at {@link #REST},
	 * a fifth more for every 0.12 deeper. Kept within a fifth of beta's own count of wet slices ({@code slices}, 1 to
	 * 4), as it always is when the surface is level across the box; where it isn't, the push stays near beta's.
	 */
	public static double wetShare(double surface, double y, int slices) {
		double share = 0.5 + (surface + REST - y) / HEIGHT;
		return Math.max((slices - 1) / 5.0, Math.min((slices + 1) / 5.0, share));
	}
}
