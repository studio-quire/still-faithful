package org.studioquire.oldfaithful.polish;

import net.minecraft.util.math.MathHelper;

/**
 * Boat Steering's physics and how the world learns a rider's keys, kept apart from the mixins so they could move
 * (to a More Faithful rule, say) without being rewritten. As later versions (1.9 on, `Boat.controlBoat` and
 * `floatBoat`, from memory of their code, not read from a jar here): A and D build up a turn rate, one degree a tick
 * each tick held, which decays with the boat's momentum, so at most 10 degrees a tick; W pushes the boat along the way
 * it faces, S backs it gently, and a turn key alone gives a small push forward. In water the boat keeps
 * {@link #MOMENTUM} of its speed and turn each tick, applied before the keys and then the move, so a held push settles
 * at push / (1 - momentum): W at {@link #TOP_SPEED}, the same in every direction. Later versions push 0.04 forward
 * (0.4 a tick, 8 blocks a second); here 0.0325, 6.5 blocks a second (aimed at 6.5 to 6.6, a
 * little faster than beta's measured 5.8). Out of the water beta's own friction
 * stands (halved each tick on the ground, 0.99 in the air); the turn there decays by {@link #GROUND} on the ground.
 * <p>
 * Beta's boat yaw points from where it's going to where it came from (`BoatEntity.tick` takes it from atan2 of the
 * last move, reversed), so its heading, the way W pushes, is (-cos yaw, -sin yaw), and a player looking along it has
 * yaw + 90.
 * <p>
 * Keys: in single player, the rider's own movement input. On a server the boat is ticked once per move packet of its
 * rider (`ServerWorld.updateEntity` skips a boat a player rides; `onPlayerMove` ticks it with the packet's motion), and
 * a riding client sends the motion its keys gave it this tick ({@code moveNonSolid}: the keys turned by its yaw, times
 * a speed and friction) but its look only when the look didn't change (beta's inverted test, Old Faithful's #83). So
 * the server reads the keys back from the motion and the look in the same packet ({@link #decode}), and only for a
 * client known to send its look every riding tick ({@link Rider}); for any other rider the boat paddles as beta's.
 * Claude Opus 5.5, 2026-11-10.
 */
public final class BoatSteering {
	public static final double MOMENTUM = 0.9;
	/** The turn's decay on the ground out of the water: beta's halving there (so at most 2 degrees a tick). */
	public static final double GROUND = 0.5;
	public static final double FORWARD_PUSH = 0.0325;
	public static final double BACK_PUSH = 0.005;
	public static final double TURN_PUSH = 0.005;
	/** Degrees a tick added to the turn rate each tick a turn key is held. */
	public static final float TURN = 1.0F;
	/** W's top speed, a tick, and the most any steered boat goes horizontally (beta's 0.4 an axis, lifted). */
	public static final double TOP_SPEED = FORWARD_PUSH / (1.0 - MOMENTUM);

	public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8;
	/** Not a steering rider: the boat paddles as beta's. */
	public static final int BETA = -1;
	/** {@link #decode}: the motion isn't keys alone (something pushed the rider). */
	public static final int UNCLEAR = -2;
	/** How many packets a server keeps the last clear keys through unclear ones (half a second). */
	public static final int HOLD = 10;
	/** How far from an axis or a diagonal a rider's motion may lie and still read as keys (float rounding is ~1e-7). */
	private static final double LATTICE = 1e-4;

	/** A boat, as the steering mixin adds to it: whether its last tick was steered (Boat Handling's coast steps aside). */
	public interface Boat {
		boolean oldfaithful$steered();

		float oldfaithful$turnRate();
	}

	/**
	 * A server's player, as the steering mixin adds to it: the keys read from the move packet being handled, or
	 * {@link #BETA}. Set and cleared around each {@code onPlayerMove}, inside which the ridden boat ticks.
	 */
	public interface Rider {
		int oldfaithful$boatKeys();

		void oldfaithful$setBoatKeys(int keys);
	}

	private BoatSteering() {
	}

	/** Single player: the rider's own input ({@code forwardSpeed}, {@code sidewaysSpeed}; A is positive). */
	public static int keys(float forward, float sideways) {
		int keys = 0;
		if (forward > 0.01F) keys |= FORWARD;
		else if (forward < -0.01F) keys |= BACK;
		if (sideways > 0.01F) keys |= LEFT;
		else if (sideways < -0.01F) keys |= RIGHT;
		return keys;
	}

	/**
	 * A server: the keys behind a riding client's motion this tick, from the motion and the look it sent with it. The
	 * client turned its keys by its yaw ({@code Entity.moveNonSolid}, with the same sine table), so turning back gives
	 * forward and strafe (A positive) times a speed. Keys alone lie on an axis or a diagonal to float rounding (sneaking
	 * only scales them); anything else had something else in it (another boat or a mob pushing the rider, a block
	 * stopping one axis), and is {@link #UNCLEAR} rather than read as a turn.
	 */
	public static int decode(double vx, double vz, float yaw) {
		if (!Double.isFinite(vx) || !Double.isFinite(vz) || !Float.isFinite(yaw)) return UNCLEAR;
		float s = MathHelper.sin(yaw * (float) Math.PI / 180.0F), c = MathHelper.cos(yaw * (float) Math.PI / 180.0F);
		double forward = -vx * s + vz * c, strafe = vx * c + vz * s;
		double m = Math.max(Math.abs(forward), Math.abs(strafe));
		if (m < 1e-6) return 0;
		int along = axis(forward, m, FORWARD, BACK), across = axis(strafe, m, LEFT, RIGHT);
		return along == UNCLEAR || across == UNCLEAR ? UNCLEAR : along | across;
	}

	private static int axis(double v, double m, int positive, int negative) {
		double r = Math.abs(v) / m;
		if (r < LATTICE) return 0;
		if (r > 1.0 - LATTICE) return v > 0 ? positive : negative;
		return UNCLEAR;
	}

	/** The turn rate after this tick, degrees a tick (positive turns right, as yaw grows): decayed, then the keys. */
	public static float turnRate(float rate, int keys, double momentum) {
		rate *= (float) momentum;
		if ((keys & LEFT) != 0) rate -= TURN;
		if ((keys & RIGHT) != 0) rate += TURN;
		return rate;
	}

	/** This tick's push along the heading. */
	public static double push(int keys) {
		boolean turning = ((keys & LEFT) != 0) != ((keys & RIGHT) != 0);
		double f = 0;
		if (turning && (keys & (FORWARD | BACK)) == 0) f += TURN_PUSH;
		if ((keys & FORWARD) != 0) f += FORWARD_PUSH;
		if ((keys & BACK) != 0) f -= BACK_PUSH;
		return f;
	}

	/** The heading's x and z for a boat's yaw (beta's boat convention, above). */
	public static double headingX(float yaw) {
		return -Math.cos(yaw * Math.PI / 180.0);
	}

	public static double headingZ(float yaw) {
		return -Math.sin(yaw * Math.PI / 180.0);
	}

	/** A placed boat's yaw, so its heading is where the player looks. */
	public static float placedYaw(float playerYaw) {
		return playerYaw - 90.0F;
	}
}
