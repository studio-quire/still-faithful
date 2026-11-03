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
 * paddling nothing changes, so top speed, acceleration and turning are beta's. Its rise and fall at the surface are
 * damped ({@link #BOB} a tick on top of beta's 0.95), so it rides steadily instead of bobbing.
 */
public final class Boats {
	public static final double CRASH = 0.25;
	public static final double COAST = 0.96;
	public static final double BOB = 0.8;
	/** A rider moving less than this a tick (their own input, which the boat takes a fifth of) isn't paddling. */
	public static final double PADDLING = 0.001;

	private Boats() {
	}

	/** The speed lost into what the boat hit, from what it asked to move and what its velocity is after. */
	public static double impact(double dx, double dz, double vxAfter, double vzAfter) {
		double x = dx != 0 && vxAfter == 0 ? dx : 0, z = dz != 0 && vzAfter == 0 ? dz : 0;
		return Math.sqrt(x * x + z * z);
	}
}
