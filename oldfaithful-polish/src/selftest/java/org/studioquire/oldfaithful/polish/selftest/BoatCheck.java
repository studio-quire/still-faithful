package org.studioquire.oldfaithful.polish.selftest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.Input;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.BoatSteering;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;

/**
 * Boat Steering in single player, the player riding a boat on keys put in as the game's input (the game's own physics
 * on both sides of it): a boat placed from the hand faces the player's look; then in a long roofed pool, its heading
 * west and the player looking south, W for 20 ticks (steered: west, along the heading; beta: south, along the look);
 * from the start again, looking west, W for 100 ticks (the speed then: steered, {@link BoatSteering#TOP_SPEED}; beta's
 * measured, and its top speed from it); W and A for 20 ticks (steered: the turn the rate adds up to, the view turned
 * with it); then nothing for 40 (steered: the turn and the speed die away). Beta's side checks only the first two and
 * reports the rest.
 * <p>
 * Boat Handling's float, in the same pool: an empty boat beside the ridden one, both settled {@link #SETTLE} ticks,
 * then their heights and the rider's eye over {@link #IDLE} ticks; the empty one pushed under, then dropped from a
 * block up; and the ridden boat's height over the last {@link #PADDLED} of the 100 ticks of W. With the switch on each
 * moves less than {@link #STEADY}, the boat floats where beta's swings around and comes back to rest there; with it off,
 * beta's bob is measured and has to be there.
 */
public final class BoatCheck implements WorldCheck {
	private static final String KEY = "boats.steering";
	private static final int Y = 104, LENGTH = 120, WIDTH = 24;
	private static final int SETTLE = 60, IDLE = 100, PADDLED = 60, RETURN = 100, RESTING = 20;
	/** The most a floating boat's height may move over a span once settled, in blocks. */
	private static final double STEADY = 0.001;
	/**
	 * Beta's boat's height over the surface, the middle of its bob: 0.185 idle, ridden or empty, and 0.180 to 0.184 paddled
	 * (measured here 2026-11-11, {@code -Pstill.beta}; 0.185 is where two slices wet become three).
	 */
	private static final double BETA_FLOAT = 0.185;

	private interface Step {
		boolean tick(Minecraft mc);
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private final ScriptInput input = new ScriptInput();
	private SelfTest results;
	private World world;
	private boolean steering, handling;
	private int x0, z0, ticks;
	private double startX, startZ;
	private BoatEntity boat, empty;
	private final Track idleBoat = new Track(IDLE), idleEmpty = new Track(IDLE), idleView = new Track(IDLE), paddled = new Track(PADDLED);
	private Input savedInput;
	private Object[] saved;
	private double markX, markZ;
	private float markYaw, markView;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		steering = PolishConfig.applied(KEY);
		handling = PolishConfig.applied("boats.handling");
		PlayerEntity p = mc.player;
		saved = new Object[] {p.x, p.y - p.standingEyeHeight, p.z};
		x0 = MathHelper.floor(p.x) + 48;
		z0 = MathHelper.floor(p.z) + 90;
		for (int x = x0 - 48; x <= x0 + LENGTH + 48; x += 16) {
			for (int z = z0 - 48; z <= z0 + WIDTH + 48; z += 16) world.getBlockId(x, Y, z);
		}
		build();
		startX = x0 + LENGTH - 3.5;
		startZ = z0 + WIDTH / 2.0;
		plan.add(this::placing);
		plan.add(this::mounting);
		plan.add(c -> wait(c, SETTLE));
		plan.add(c -> {
			idleBoat.add(boat.y);
			idleEmpty.add(empty.y);
			idleView.add(c.player.y);
			return wait(c, IDLE);
		});
		plan.add(this::floatingIdle);
		// The empty boat pushed under, its box's bottom just over the pool's floor, then dropped from a block up.
		backToTheSurface("pushed under", Y - 0.95);
		backToTheSurface("dropped from a block up", Y + 1.0);
		plan.add(c -> {
			setLook(c, 0);
			mark(c);
			return true;
		});
		plan.add(c -> keys(c, 20, 1, 0));
		plan.add(this::alongTheLookOrHeading);
		plan.add(c -> {
			reset(c, 90);
			return true;
		});
		plan.add(c -> {
			if (ticks >= 100 - PADDLED) paddled.add(boat.y);
			return keys(c, 100, 1, 0);
		});
		plan.add(this::topSpeed);
		plan.add(this::floatingPaddled);
		plan.add(c -> {
			mark(c);
			return true;
		});
		plan.add(c -> keys(c, 20, 1, 1));
		plan.add(this::turned);
		plan.add(c -> {
			mark(c);
			return true;
		});
		plan.add(c -> keys(c, 40, 0, 0));
		plan.add(this::stopped);
	}

	@Override
	public boolean tick(Minecraft mc) {
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
			ticks = 0;
		}
		PlayerEntity p = mc.player;
		if (savedInput != null) ((ClientPlayerEntity) p).input = savedInput;
		if (p.vehicle != null) p.setVehicle(p.vehicle);
		if (boat != null) boat.markDead();
		if (empty != null) empty.markDead();
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.setPositionAndAnglesKeepPrevAngles((Double) saved[0], (Double) saved[1], (Double) saved[2], 0, 0);
		return true;
	}

	/** Stone round a pool of still water one deep, two of air over it and a stone roof (open water freezes in cold places). */
	private void build() {
		for (int x = x0 - 1; x <= x0 + LENGTH; x++) {
			for (int z = z0 - 1; z <= z0 + WIDTH; z++) {
				boolean wall = x == x0 - 1 || x == x0 + LENGTH || z == z0 - 1 || z == z0 + WIDTH;
				world.setBlockWithoutNotifyingNeighbors(x, Y - 2, z, Block.STONE.id, 0);
				world.setBlockWithoutNotifyingNeighbors(x, Y - 1, z, wall ? Block.STONE.id : Block.WATER.id, 0);
				world.setBlockWithoutNotifyingNeighbors(x, Y, z, wall ? Block.STONE.id : 0, 0);
				world.setBlockWithoutNotifyingNeighbors(x, Y + 1, z, wall ? Block.STONE.id : 0, 0);
				world.setBlockWithoutNotifyingNeighbors(x, Y + 2, z, Block.STONE.id, 0);
			}
		}
		// For placing: a block of water under the open sky beside the pool, with nothing over it.
		world.setBlockWithoutNotifyingNeighbors(x0 - 6, Y - 2, z0, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(x0 - 6, Y - 1, z0, Block.WATER.id, 0);
		for (int y = Y; y <= Y + 4; y++) world.setBlockWithoutNotifyingNeighbors(x0 - 6, y, z0, 0, 0);
	}

	/** A boat from the hand, the player over the water looking straight down and to one side: its yaw. */
	private boolean placing(Minecraft mc) {
		PlayerEntity p = mc.player;
		float look = 37.0F;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.setPositionAndAnglesKeepPrevAngles(x0 - 5.5, Y + 2, z0 + 0.5, look, 90.0F);
		p.prevYaw = p.yaw;
		p.prevPitch = p.pitch;
		int before = world.entities.size();
		Item.BOAT.use(new ItemStack(Item.BOAT, 1), world, p);
		BoatEntity placed = null;
		for (Object o : world.entities) {
			if (o instanceof BoatEntity && Math.abs(((BoatEntity) o).x - (x0 - 5.5)) < 0.01 && Math.abs(((BoatEntity) o).z - (z0 + 0.5)) < 0.01) placed = (BoatEntity) o;
		}
		if (placed == null) {
			results.check("still boat steering: a boat placed from the hand (none spawned, " + (world.entities.size() - before) + " entities more)", false);
			return true;
		}
		float expected = steering ? BoatSteering.placedYaw(look) : 0.0F;
		results.check(String.format(Locale.ROOT, "still boat steering: a boat placed looking along yaw %.0f has yaw %.1f (%s: %.0f)", look, placed.yaw,
				steering ? "steering: the look's" : "beta: always", expected), Math.abs(placed.yaw - expected) < 0.01);
		placed.markDead();
		return true;
	}

	/** Into a boat at the pool's east end, facing west (yaw 0, beta's start), by the boat's own right-click. */
	private boolean mounting(Minecraft mc) {
		boat = new BoatEntity(world, startX, Y - 0.3, startZ);
		world.spawnEntity(boat);
		// Nobody's: by the north wall, out of the ridden boat's way (it goes west, and south when it turns).
		empty = new BoatEntity(world, startX, Y - 0.3, z0 + 3.5);
		world.spawnEntity(empty);
		boat.interact(mc.player);
		savedInput = ((ClientPlayerEntity) mc.player).input;
		((ClientPlayerEntity) mc.player).input = input;
		results.check("still boat steering: the player rides the boat", mc.player.vehicle == boat);
		return true;
	}

	private boolean wait(Minecraft mc, int n) {
		input.forward = input.sideways = 0;
		return ++ticks >= n;
	}

	/** Holds the keys n ticks: forward 1 for W, sideways 1 for A. */
	private boolean keys(Minecraft mc, int n, float forward, float sideways) {
		input.forward = forward;
		input.sideways = sideways;
		if (++ticks < n) return false;
		input.forward = input.sideways = 0;
		return true;
	}

	private void setLook(Minecraft mc, float yaw) {
		mc.player.yaw = mc.player.prevYaw = yaw;
		mc.player.pitch = mc.player.prevPitch = 0;
	}

	private void mark(Minecraft mc) {
		markX = boat.x;
		markZ = boat.z;
		markYaw = boat.yaw;
		markView = mc.player.yaw;
	}

	/** Back to the start, still, facing west, the player looking along yaw. */
	private void reset(Minecraft mc, float look) {
		boat.velocityX = boat.velocityY = boat.velocityZ = 0;
		boat.setPositionAndAngles(startX, boat.y, startZ, 0, 0);
		boat.prevYaw = 0;
		setLook(mc, look);
		mark(mc);
	}

	/** W, heading west, looking south: steered, it goes west; beta's goes south, the way the player looks. */
	private boolean alongTheLookOrHeading(Minecraft mc) {
		double dx = boat.x - markX, dz = boat.z - markZ;
		String seen = String.format(Locale.ROOT, "went %.2f west and %.2f south in 20 ticks", -dx, dz);
		if (Math.abs(dx) + Math.abs(dz) < 0.01) {
			PlayerEntity p = mc.player;
			System.out.println(String.format(Locale.ROOT,
					"[oldfaithful-selftest] boat check: still; boat %.2f %.2f %.2f dead %b, player %.2f %.2f %.2f riding it %b, region loaded %b, screen %s, input ours %b",
					boat.x, boat.y, boat.z, boat.dead, p.x, p.y, p.z, p.vehicle == boat,
					world.isRegionLoaded(MathHelper.floor(boat.x) - 32, 0, MathHelper.floor(boat.z) - 32, MathHelper.floor(boat.x) + 32, 128, MathHelper.floor(boat.z) + 32),
					mc.currentScreen, ((ClientPlayerEntity) p).input == input));
		}
		if (steering) {
			results.check("still boat steering: heading west and looking south, W takes the boat west, the way it faces (" + seen + ")",
					dx < -5 * BoatSteering.TOP_SPEED && Math.abs(dz) < 0.01 && Math.abs(boat.yaw - markYaw) < 0.01);
		} else {
			results.check("still boat steering, beta: W takes the boat south, the way the player looks (" + seen + ")", dz > 0.2 && Math.abs(dx) < dz);
		}
		return true;
	}

	/** W for 100 ticks heading and looking west: the speed then. */
	private boolean topSpeed(Minecraft mc) {
		double speed = Math.hypot(boat.x - boat.prevX, boat.z - boat.prevZ);
		double went = markX - boat.x;
		if (steering) {
			results.check(String.format(Locale.ROOT, "still boat steering: W for 100 ticks: %.4f a tick (top speed %.2f), %.1f blocks",
					speed, BoatSteering.TOP_SPEED, went), Math.abs(speed - BoatSteering.TOP_SPEED) < 0.002 && boat.yaw == markYaw);
		} else {
			// Beta's paddling adds a share of the rider's motion each tick and keeps 0.99 of the speed: after n ticks
			// top * (1 - 0.99^n).
			double top = speed / (1 - Math.pow(0.99, 100));
			results.check(String.format(Locale.ROOT, "still boat steering, beta: W for 100 ticks: %.4f a tick, %.1f blocks; top speed from it %.3f a tick (%.1f blocks a second)",
					speed, went, top, top * 20), speed > 0.1 && speed < 0.4);
		}
		return true;
	}

	/**
	 * W and A for 20 ticks: steered, the boat turned left by what the rate adds up to, 1 more each tick decayed by the
	 * momentum (the boat sees the keys a tick or two after they change, so 17 to 21 ticks of it), and the view with it
	 * (all but what's still to come, up to 20). How far the way it moves trails the way it faces is reported: the
	 * momentum carries it on, so it drifts through a turn, as later versions' boats do.
	 */
	private boolean turned(Minecraft mc) {
		double boatTurn = wrap(boat.yaw - markYaw), viewTurn = wrap(mc.player.yaw - markView);
		int held = 0;
		double turn = 0, rate = 0;
		for (int n = 1; n <= 21; n++) {
			rate = rate * BoatSteering.MOMENTUM - BoatSteering.TURN;
			turn += rate;
			if (n >= 17 && Math.abs(boatTurn - turn) < 0.01) held = n;
		}
		double moving = Math.toDegrees(Math.atan2(boat.z - boat.prevZ, boat.x - boat.prevX));
		double facing = Math.toDegrees(Math.atan2(BoatSteering.headingZ(boat.yaw), BoatSteering.headingX(boat.yaw)));
		String seen = String.format(Locale.ROOT, "boat turned %.2f (%s), view %.1f, moving %.1f off its heading, turn rate %.2f",
				boatTurn, held > 0 ? "as " + held + " ticks of the rate add up to" : "not as 17 to 21 ticks of the rate add up to", viewTurn,
				Math.abs(wrap(moving - facing)), turnRate());
		if (steering) {
			results.check("still boat steering: W and A for 20 ticks turn the boat left and the view with it (" + seen + ")",
					held > 0 && viewTurn < 0 && Math.abs(viewTurn - boatTurn) <= 20.01);
		} else {
			System.out.println("[oldfaithful-selftest] still boat steering, beta: W and A for 20 ticks: " + seen);
		}
		return true;
	}

	/** Nothing for 40 ticks after that: steered, the turn and the speed die away within a few blocks. */
	private boolean stopped(Minecraft mc) {
		double went = Math.hypot(boat.x - markX, boat.z - markZ);
		double speed = Math.hypot(boat.x - boat.prevX, boat.z - boat.prevZ);
		double rate = turnRate();
		String seen = String.format(Locale.ROOT, "went %.2f blocks, now %.4f a tick, turning %.3f degrees a tick, turned %.1f more",
				went, speed, rate, wrap(boat.yaw - markYaw));
		if (steering) {
			results.check("still boat steering: let go, the boat slows and stops turning within a few blocks (" + seen + ")",
					went < 5.5 && speed < 0.01 && Math.abs(rate) < 0.2);
		} else {
			System.out.println("[oldfaithful-selftest] still boat steering, beta: nothing for 40 ticks: " + seen);
		}
		return true;
	}

	/**
	 * Settled, nobody paddling: with Boat Handling the ridden boat, the empty one and the rider's eye hold their
	 * height, and the boat floats where beta's swings around; without it, beta's bob.
	 */
	private boolean floatingIdle(Minecraft mc) {
		String seen = "ridden: " + idleBoat + "; empty: " + idleEmpty + "; the rider's eye: " + idleView;
		boolean steady = idleBoat.range() < STEADY && idleEmpty.range() < STEADY && idleView.range() < STEADY;
		if (handling) {
			results.check("still boat handling: settled, a floating boat holds its height, ridden or empty, and the rider's view with it (" + seen + ")", steady);
			double height = idleBoat.mean() - Y;
			results.check(String.format(Locale.ROOT, "still boat handling: it floats %.4f over the surface, where beta's swings around (%.3f)", height, BETA_FLOAT),
					Math.abs(height - BETA_FLOAT) < 0.01 && Math.abs(idleEmpty.mean() - idleBoat.mean()) < STEADY);
		} else {
			results.check("still boat handling, beta: a floating boat bobs at the surface (" + seen + ")",
					idleBoat.range() > 5 * STEADY && idleEmpty.range() > 5 * STEADY);
		}
		if (!handling || !steady) {
			idleBoat.print("ridden, idle");
			idleEmpty.print("empty, idle");
			idleView.print("the rider's eye, idle");
		}
		return true;
	}

	/**
	 * The empty boat put with its box's bottom at {@code bottom}, still, then left {@link #RETURN} ticks: it comes back to
	 * the surface, and with Boat Handling is at rest there over the last {@link #RESTING}, at the height it floated at.
	 */
	private void backToTheSurface(String what, double bottom) {
		Track end = new Track(RESTING);
		plan.add(c -> {
			empty.velocityX = empty.velocityY = empty.velocityZ = 0;
			empty.setPosition(empty.x, bottom + 0.3, empty.z);
			empty.prevY = empty.y;
			return true;
		});
		plan.add(c -> {
			if (ticks >= RETURN - RESTING) end.add(empty.y);
			return wait(c, RETURN);
		});
		plan.add(c -> {
			String seen = String.format(Locale.ROOT, "%.2f over the surface at first; %d ticks on, ", bottom + 0.3 - Y, RETURN - RESTING) + end;
			if (handling) {
				results.check("still boat handling: " + what + ", the empty boat comes back to the surface and rests where it floated (" + seen + ")",
						end.range() < STEADY && Math.abs(end.mean() - idleEmpty.mean()) < STEADY);
			} else {
				results.check("still boat handling, beta: " + what + ", the empty boat comes back to the surface (" + seen + ")",
						Math.abs(end.mean() - Y - BETA_FLOAT) < 0.1);
			}
			return true;
		});
	}

	/** Over the last of the 100 ticks of W: the paddled boat holds its height too (beta's bobs on). */
	private boolean floatingPaddled(Minecraft mc) {
		double speed = Math.hypot(boat.x - boat.prevX, boat.z - boat.prevZ);
		String seen = String.format(Locale.ROOT, "at %.3f a tick: ", speed) + paddled;
		boolean steady = paddled.range() < STEADY;
		if (handling) {
			results.check("still boat handling: paddled, the boat holds its height (" + seen + ")", steady && Math.abs(paddled.mean() - idleBoat.mean()) < STEADY);
		} else {
			results.check("still boat handling, beta: a paddled boat bobs (" + seen + ")", paddled.range() > 5 * STEADY);
		}
		if (!handling || !steady) paddled.print("paddled");
		return true;
	}

	/** A boat's or the rider's height tick by tick, over the water's surface. */
	private static final class Track {
		private final double[] ys;
		private int n;

		Track(int capacity) {
			ys = new double[capacity];
		}

		void add(double y) {
			if (n < ys.length) ys[n++] = y - Y;
		}

		double range() {
			if (n == 0) return Double.NaN;
			double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				lo = Math.min(lo, ys[i]);
				hi = Math.max(hi, ys[i]);
			}
			return hi - lo;
		}

		double mean() {
			double sum = 0;
			for (int i = 0; i < n; i++) sum += ys[i];
			return sum / n + Y;
		}

		/** Ticks from one high point to the next, on average; 0 with fewer than two. */
		double period() {
			int first = -1, last = -1, peaks = 0;
			for (int i = 1; i < n - 1; i++) {
				if (ys[i] > ys[i - 1] && ys[i] >= ys[i + 1]) {
					if (first < 0) first = i;
					last = i;
					peaks++;
				}
			}
			return peaks < 2 ? 0 : (last - first) / (double) (peaks - 1);
		}

		@Override
		public String toString() {
			double period = period();
			return String.format(Locale.ROOT, "moved %.5f over %d ticks, around %.4f over the surface%s", range(), n, mean() - Y,
					period > 0 ? String.format(Locale.ROOT, ", a high point every %.1f ticks", period) : "");
		}

		void print(String what) {
			StringBuilder line = new StringBuilder("[oldfaithful-selftest] boat float, " + what + ", over the surface by tick:");
			for (int i = 0; i < n; i++) line.append(String.format(Locale.ROOT, " %.4f", ys[i]));
			System.out.println(line);
		}
	}

	/** The boat's turn rate, where the switch's mixin is in (beta's boat has none). */
	private double turnRate() {
		return boat instanceof BoatSteering.Boat ? ((BoatSteering.Boat) boat).oldfaithful$turnRate() : Double.NaN;
	}

	private static double wrap(double degrees) {
		double d = degrees % 360.0;
		if (d < -180) d += 360;
		if (d >= 180) d -= 360;
		return d;
	}

	/** The script's keys, as KeyboardInput would set them. */
	private static final class ScriptInput extends Input {
		float forward, sideways;

		@Override
		public void update(PlayerEntity player) {
			movementForward = forward;
			movementSideways = sideways;
			jumping = false;
			sneaking = false;
		}
	}
}
