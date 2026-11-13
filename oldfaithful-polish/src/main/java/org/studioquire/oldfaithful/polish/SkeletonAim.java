package org.studioquire.oldfaithful.polish;

/**
 * Skeleton Arrows ({@code mobs.skeletonArrows}): a skeleton's arrow leaves from its eyes, as in later versions, instead
 * of 1.4 blocks higher, over its head, where beta's {@code SkeletonEntity.attack} lifts it; and it's aimed so it hits as
 * often as beta's did (beta's efficiency from a different place).
 * <p>
 * Beta aims at the target's eyes less 0.2 from wherever the arrow starts, plus 0.2 up for every block across, which is
 * what makes up for gravity. So from the eyes, aimed beta's way, the arrow still arrives about where beta's did. What
 * the start changes is the slope: at a player above, the arrow climbs more steeply than beta's, so it crosses more
 * slowly, is longer in the air and has spread wider by the time it gets there; at a player below, the other way. Moving
 * the start alone, a skeleton hit a player 4 blocks up a cliff about 52% of the time where beta's hit 69%, and one 4
 * below 67% where beta's hit 59% (standing, ranges 2 to 9, `tools/skeleton_arrows.py`). So here the arrow is aimed to
 * cross the target's distance at the height beta's would have, unspread, and its spread is scaled by how much quicker or
 * slower across it is than beta's, so it has spread as widely as beta's by the time it arrives. Against a standing
 * player that gives beta's hit rate within a few points at every range and height measured, and the same on average.
 */
public final class SkeletonAim {
	/** What beta adds to the arrow's height (the 1.4F of {@code attack}, as the double it's added as). */
	public static final double LIFT = 1.4F;
	/** ArrowEntity's drag and fall a tick in air. */
	private static final double DRAG = 0.99, FALL = 0.03;
	private static final double STEEPEST = Math.toRadians(80.0);

	private SkeletonAim() {
	}

	/**
	 * What to hand {@code ArrowEntity.setVelocity} for an arrow starting at the eyes, from the y beta's aim gives from
	 * there ({@code across} is the horizontal distance to the target): {vertical part, spread}.
	 */
	public static double[] aim(double across, double vy, double speed, double spread) {
		double betaVy = vy - LIFT;
		double want = LIFT + rise(across, betaVy, speed);
		if (across < 0.5 || Double.isInfinite(want) || Double.isNaN(want)) return new double[] {vy, spread};
		// Height at the target's distance has one peak over the angle: find it, then the lowest angle reaching beta's
		// height below it (the flat arc, as beta's is). Out of reach, the peak.
		double lo = -STEEPEST, hi = STEEPEST;
		for (int i = 0; i < 40; i++) {
			double a = lo + (hi - lo) / 3, b = hi - (hi - lo) / 3;
			if (riseAt(across, a, speed) < riseAt(across, b, speed)) lo = a;
			else hi = b;
		}
		double angle = (lo + hi) / 2;
		if (riseAt(across, angle, speed) > want) {
			lo = -STEEPEST;
			hi = angle;
			for (int i = 0; i < 40; i++) {
				double mid = (lo + hi) / 2;
				if (riseAt(across, mid, speed) < want) lo = mid;
				else hi = mid;
			}
			angle = (lo + hi) / 2;
		}
		double aimed = across * Math.tan(angle);
		// Time across goes as 1 / cos of the angle, and spread at the target as the time.
		return new double[] {aimed, spread * Math.hypot(across, betaVy) / Math.hypot(across, aimed)};
	}

	private static double riseAt(double across, double angle, double speed) {
		return rise(across, across * Math.tan(angle), speed);
	}

	/**
	 * How far above where it started an unspread arrow handed (across, vy) is once it has gone {@code across}, by the
	 * arrow's own steps run continuously: at u across a tick it has gone u * S(n) after n ticks, S(n) = (1 - 0.99^n) /
	 * 0.01, and fallen 0.03 * (n - S(n)) / 0.01.
	 */
	static double rise(double across, double vy, double speed) {
		double u = speed * across / Math.hypot(across, vy);
		double s = across / u;
		if (s * (1 - DRAG) >= 1) return Double.NEGATIVE_INFINITY;
		double n = Math.log(1 - (1 - DRAG) * s) / Math.log(DRAG);
		return vy - FALL * (n - s) / (1 - DRAG);
	}
}
