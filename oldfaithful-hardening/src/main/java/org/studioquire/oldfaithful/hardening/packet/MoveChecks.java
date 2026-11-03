package org.studioquire.oldfaithful.hardening.packet;

/**
 * What a b1.7.3 server takes from a client's movement packets without checking:
 * <ul>
 * <li>NaN and infinite numbers. Every comparison with NaN is false, so a NaN position slips past the "moved too
 * quickly", "illegal stance" and world-border checks; the player is then wherever the next packet says, and NaN
 * ends up in the saved player file.</li>
 * <li>A riding player's motion. While riding, the client sends its motion in place of a position (y and stance -999),
 * and the server moves the player by it with no limit at all. Moving by ten million blocks makes the collision search
 * walk every block column in between: one packet hangs the server.</li>
 * </ul>
 * A client that does either is disconnected as vanilla disconnects one that leaves the world ("Illegal position").
 * Honest clients send finite numbers, and a rider's motion is a fraction of a block a tick.
 */
public final class MoveChecks {
	/** Most a riding player's motion may be, in blocks per packet: the same distance vanilla allows a walking move. */
	public static final double MAX_RIDING_MOTION = 10.0;

	private MoveChecks() {
	}

	/** Null if the move is acceptable, otherwise what's wrong with it. */
	public static String problem(boolean changePosition, double x, double y, double eyeHeight, double z,
			boolean changeLook, float yaw, float pitch) {
		if (changePosition) {
			if (!finite(x) || !finite(y) || !finite(eyeHeight) || !finite(z)) {
				return "position " + x + ", " + y + " (stance " + eyeHeight + "), " + z;
			}
			if (y == -999.0 && eyeHeight == -999.0
					&& (Math.abs(x) > MAX_RIDING_MOTION || Math.abs(z) > MAX_RIDING_MOTION)) {
				return "riding motion " + x + ", " + z;
			}
		}
		if (changeLook && (!finite(yaw) || !finite(pitch))) return "look " + yaw + ", " + pitch;
		return null;
	}

	/** Null if a riding client's input is acceptable: every number finite. */
	public static String inputProblem(float sideways, float forward, float pitch, float yaw) {
		if (finite(sideways) && finite(forward) && finite(pitch) && finite(yaw)) return null;
		return "input " + sideways + ", " + forward + ", look " + yaw + ", " + pitch;
	}

	private static boolean finite(double d) {
		return !Double.isNaN(d) && !Double.isInfinite(d);
	}
}
