package org.studioquire.oldfaithful.polish.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.world.World;

/**
 * Test-only: #31's carts meeting carts, on one axis, shared by the client start-up test (StillCheck, single player) and
 * the real server (CartScenario). One track per scene, side by side: a cart running into a standing one, a furnace cart
 * behind a cart, two overlapping carts rolling together (a booster), two carts head-on, a cart passing a standing one
 * on the next track, and a cart running into one standing on a curve. Built away from x = 0 by the caller (beta's slip
 * doesn't fire within a few blocks of it). {@link #tick()} once a world tick from the build; each scene is measured at
 * its own tick and {@link #checks} reads them once {@link #done()}.
 * <p>
 * Where the carts meet (the change on, or beta on east-west track): the standing cart is knocked on, the furnace cart
 * pushes the train, the booster pair speeds up, the head-on pair bounces apart. Beta on north-south track: none of
 * those. Carts side by side on parallel tracks never meet (beta's gate does fire for them on east-west track). On
 * a curve beta's carts are walls to each other on either axis.
 */
public final class CartScenes {
	/** The scenes' extent: along the axis (the end blocks included) and across it. */
	public static final int LENGTH = 26, WIDTH = 26;
	private static final int BOOST_LOOK = 10, LOOK = 40, FURNACE_LOOK = 60;

	private final Tracks t;
	private int ticks;
	private final MinecartEntity bumped, pushed, boostA, boostB, headA, headB, parked, onCurve;
	private final double bumpedStart, pushedStart, parkedStart;
	private double bumpedMoved, pushedMoved, boostSpeed, headGap, parkedMoved, curveMoved;
	private double curveX, curveZ;

	/** The scenes from (ox, oz) along the axis, rails at height y; the tracks start one block in. */
	public CartScenes(World world, int ox, int y, int oz, boolean alongZ) {
		t = new Tracks(world, alongZ ? ox : ox + 1, y, alongZ ? oz + 1 : oz, alongZ);
		t.lay(LENGTH - 2, 0, 4, 8, 12, 16, 17);
		t.cart(0, 1.5, 0, 0.2);
		bumped = t.cart(0, 4.5, 0, 0);
		MinecartEntity furnace = t.cart(4, 1.5, 2, 0);
		furnace.fuel = 1200;
		furnace.pushX = alongZ ? 0 : 1;
		furnace.pushZ = alongZ ? 1 : 0;
		pushed = t.cart(4, 2.5, 0, 0);
		// Half a block apart: in beta the gate lets an exchange through when the carts are nearly level across it
		// (|dz * x| under 2.2), as when the one behind lands where the other was, which a step the size of the gap does.
		boostA = t.cart(8, 1.5, 0, 0.4);
		boostB = t.cart(8, 2.0, 0, 0.4);
		headA = t.cart(12, 2.5, 0, 0.15);
		headB = t.cart(12, 6.5, 0, -0.15);
		t.cart(16, 1.5, 0, 0.4);
		parked = t.cart(17, 5.5, 0, 0);
		// A standing cart on a curve, and a cart running into it from the straight before it.
		t.curve(20, 8, 4);
		t.cart(20, 3.5, 0, 0.25);
		onCurve = t.cart(20, 8.5, 0, 0);
		bumpedStart = t.along(bumped);
		pushedStart = t.along(pushed);
		parkedStart = t.along(parked);
	}

	public String axis() {
		return t.axis();
	}

	public void tick() {
		ticks++;
		if (Boolean.getBoolean("oldfaithful.polish.scenario.debug") && ticks <= 12) {
			for (MinecartEntity c : new MinecartEntity[] {boostA, boostB}) {
				int bx = (int) Math.floor(c.x), by = (int) Math.floor(c.y), bz = (int) Math.floor(c.z);
				System.out.println(String.format(Locale.ROOT, "[polish-debug] %s %d: at %.3f %.3f %.3f v %.4f,%.4f persistent %s chunk %d,%d dead %s; block %d below %d",
						axis(), ticks, c.x, c.y, c.z, c.velocityX, c.velocityZ, c.isPersistent, c.chunkX, c.chunkZ, c.dead,
						c.world.getBlockId(bx, by, bz), c.world.getBlockId(bx, by - 1, bz)));
			}
		}
		if (ticks == 1) {
			// Where the standing cart settled on the curve's chord in its first tick.
			curveX = onCurve.x;
			curveZ = onCurve.z;
		}
		if (ticks == BOOST_LOOK) boostSpeed = Math.max(Tracks.speed(boostA), Tracks.speed(boostB));
		if (ticks == LOOK) {
			bumpedMoved = Math.abs(t.along(bumped) - bumpedStart);
			headGap = Math.abs(t.along(headB) - t.along(headA));
			parkedMoved = Math.abs(t.along(parked) - parkedStart);
			curveMoved = Math.hypot(onCurve.x - curveX, onCurve.z - curveZ);
		}
		if (ticks == FURNACE_LOOK) pushedMoved = Math.abs(t.along(pushed) - pushedStart);
	}

	public boolean done() {
		return ticks >= FURNACE_LOOK;
	}

	/** One line per scene: what was seen, and whether it's what this setup should show. */
	public List<Check> checks(boolean polished) {
		boolean meet = polished || !t.alongZ;
		String how = (polished ? "polished" : "beta") + ", " + axis() + ": ";
		List<Check> out = new ArrayList<>();
		out.add(new Check(String.format(Locale.ROOT, "%sa cart at 0.2 into a standing one moved it %.2f blocks (%s)", how, bumpedMoved,
				meet ? "knocked on" : "a wall: stays"), meet ? bumpedMoved > 1 : bumpedMoved < 0.05));
		out.add(new Check(String.format(Locale.ROOT, "%sa furnace cart behind a cart moved it %.2f blocks in 60 ticks (%s)", how, pushedMoved,
				meet ? "pushes the train" : "stalls"), meet ? pushedMoved > 3 : pushedMoved < 0.05));
		out.add(new Check(String.format(Locale.ROOT, "%stwo overlapping carts rolling at 0.4: stored speed %.3g after 10 ticks (%s)", how,
				boostSpeed, meet ? "a booster: grows" : "no exchange: friction only"), meet ? boostSpeed > 2 : boostSpeed < 0.3));
		out.add(new Check(String.format(Locale.ROOT, "%stwo carts head-on at 0.15: %.2f apart after 40 ticks (%s)", how, headGap,
				meet ? "bounce apart" : "stop touching"), meet ? headGap > 1.5 : headGap < 1.25));
		out.add(new Check(String.format(Locale.ROOT, "%sa cart at 0.4 past a standing one on the next track moved it %.2f blocks (left alone)", how,
				parkedMoved), parkedMoved < 0.05));
		out.add(new Check(String.format(Locale.ROOT, "%sa cart at 0.25 into one standing on a curve moved it %.2f blocks (%s)", how, curveMoved,
				polished ? "knocked on" : "a wall on either axis: stays"), polished ? curveMoved > 0.3 : curveMoved < 0.05));
		return out;
	}

	public static final class Check {
		public final String text;
		public final boolean ok;

		Check(String text, boolean ok) {
			this.text = text;
			this.ok = ok;
		}
	}
}
