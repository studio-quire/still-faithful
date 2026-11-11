package org.studioquire.oldfaithful.selftest;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.world.World;

/**
 * #84 in the Fixed edition's start-up test (from {@link FixesCheck}): mobs placed with their box just out of view while
 * part of them is still on screen, each drawn once regardless of culling (the pixels it has on screen) and once the game's
 * own way. With render.entityCulling the second frame must be the first; without it, empty. The camera is held 180
 * blocks up, so the depth buffer's near pixels are the mob's alone ({@link Culling}).
 * <p>
 * Each case sits where only its part of the fix draws it, by beta's culler's own measure of how far the box is out (the
 * growth on every side that would bring it in): a cow 0.1 out, for any growth at all; a squid lying as out of water 1.15
 * out, past the block everything gets, for the squid's own 1.5; a ghast below the view 1.8 out, for its 3; a zombie fallen
 * over dying 1.4 out, for its height added while it dies; and a pig moving a block a tick, drawn where it was at the last
 * tick (0.1 out) while its box is where it is now (1.25 out), for the box tested where the mob is drawn.
 */
final class CullingCheck implements Culling.Scene {
	private static final double EYE = 180, NEAR = 30;

	private static final class Case {
		final String what;
		final Function<World, Entity> make;
		final Consumer<Entity> pose;
		final float yaw;
		final char toward;
		final boolean square;
		final double distance, out, drawnOut;

		/** {@code drawnOut} below 0: drawn where it is; else drawn where it was a tick ago, that far out. */
		Case(String what, Function<World, Entity> make, Consumer<Entity> pose, float yaw, char toward, boolean square, double distance, double out, double drawnOut) {
			this.what = what;
			this.make = make;
			this.pose = pose;
			this.yaw = yaw;
			this.toward = toward;
			this.square = square;
			this.distance = distance;
			this.out = out;
			this.drawnOut = drawnOut;
		}
	}

	private static final List<Case> CASES = Arrays.asList(
			new Case("a cow, its head on screen", CowEntity::new, e -> { }, 67.5F, 'L', false, 4, 0.1, -1),
			new Case("a squid lying as out of water, its tentacles on screen", SquidEntity::new, e -> {
				SquidEntity s = (SquidEntity) e;
				s.tiltAngle = s.lastTiltAngle = -90;
			}, 225, 'L', true, 4, 1.15, -1),
			new Case("a ghast below the view, its top on screen", GhastEntity::new, e -> { }, 135, 'D', false, 10, 1.8, -1),
			new Case("a zombie fallen over dying, its head on screen", ZombieEntity::new, e -> ((LivingEntity) e).deathTime = 20, 135, 'L', true, 4, 1.4, -1),
			new Case("a pig moving a block a tick, drawn a tick behind", PigEntity::new, e -> { }, 67.5F, 'L', false, 4, 1.25, 0.1));

	private final SelfTest results;
	private final boolean fixOn = CullingMeasure.fixOn();
	private int index, frame, forced, ticks;
	private Entity probe;
	private double cx, cz, now, drawn;
	private double outNow;
	private FrustumCuller culler;
	private Object[] saved;
	private volatile boolean done;

	CullingCheck(SelfTest results) {
		this.results = results;
	}

	/** One game tick; true when every case is checked. */
	boolean tick(Minecraft mc) {
		ClientPlayerEntity p = mc.player;
		if (saved == null) {
			saved = new Object[] {mc.options.bobView, p.x, p.y, p.z, p.yaw, p.pitch};
			mc.options.bobView = false;
			cx = Math.floor(p.x) + 0.5;
			cz = Math.floor(p.z) + 0.5;
		}
		if (++ticks < 20) {
			hold(mc);
			return false;
		}
		if (Culling.scene == null && !done) {
			Culling.frameDelta = frameDelta();
			Culling.scene = this;
		}
		if (!done && ticks < 600) return false;
		if (!done) results.fail("fixes #84: the culling scene never finished (case " + index + ")", new AssertionError());
		Culling.scene = null;
		Culling.frameDelta = 1.0F;
		removeProbe();
		mc.options.bobView = (Boolean) saved[0];
		p.noClip = false;
		p.setPositionAndAngles((Double) saved[1], (Double) saved[2], (Double) saved[3], (Float) saved[4], (Float) saved[5]);
		p.lastTickX = p.x;
		p.lastTickY = p.y;
		p.lastTickZ = p.z;
		return true;
	}

	private float frameDelta() {
		return index < CASES.size() && CASES.get(index).drawnOut >= 0 ? 0.0F : 1.0F;
	}

	private void hold(Minecraft mc) {
		Case c = CASES.get(Math.min(index, CASES.size() - 1));
		Culling.hold(mc.player, cx, EYE, cz, c.square ? (float) Culling.halfWide : 0, 0);
	}

	@Override
	public void frameStart(Minecraft mc) {
		if (!done) hold(mc);
	}

	@Override
	public void entities(Minecraft mc, FrustumCuller culler, float tickDelta) {
		if (done) return;
		this.culler = culler;
		Case c = CASES.get(index);
		if (probe == null) {
			probe = c.make.apply(mc.world);
			mc.world.entities.add(probe);
			Culling.frozen = probe;
			now = slideFor(c, c.out);
			drawn = c.drawnOut >= 0 ? slideFor(c, c.drawnOut) : now;
		}
		place(c, now);
		outNow = Culling.padNeeded(culler, probe.boundingBox);
		if (c.drawnOut >= 0) {
			// Where it was at the last tick: the frame, drawn at the last tick's position (frameDelta 0), shows it there.
			double[] then = at(c, drawn);
			probe.lastTickX = probe.prevX = then[0];
			probe.lastTickY = probe.prevY = then[1];
			probe.lastTickZ = probe.prevZ = then[2];
		}
		probe.ignoreFrustumCull = frame == 0;
	}

	@Override
	public void drawn(Minecraft mc) {
		if (done) return;
		int n = Culling.nearPixels(mc.displayWidth, mc.displayHeight, NEAR);
		if (frame == 0) {
			forced = n;
			frame = 1;
			return;
		}
		Case c = CASES.get(index);
		boolean outOfView = outNow > 0;
		String seen = String.format(Locale.ROOT, "fixes #84: %s, its box %.2f out of view by beta's culler: %d pixels on screen; the game drew %d", c.what, outNow, forced, n);
		if (fixOn) {
			results.check(seen + " (with the fix: all of them)", outOfView && forced >= 20 && n == forced);
		} else {
			results.check(seen + " (beta: none, though some are on screen)", outOfView && forced >= 20 && n == 0);
		}
		removeProbe();
		frame = 0;
		if (++index >= CASES.size()) {
			done = true;
		} else {
			Culling.frameDelta = frameDelta();
		}
	}

	private double[] at(Case c, double slide) {
		float yaw = c.square ? (float) Culling.halfWide : 0;
		return Culling.ahead(cx, EYE, cz, yaw, 0, c.toward, c.distance, slide);
	}

	private void place(Case c, double slide) {
		double[] at = at(c, slide);
		float yaw = (c.square ? (float) Culling.halfWide : 0) + c.yaw;
		Culling.place(probe, at[0], at[1], at[2], yaw, 0);
		Culling.walk((LivingEntity) probe, 0, 0, Culling.frameDelta);
		c.pose.accept(probe);
	}

	/** The slide at which beta's culler would need the box grown by {@code out} on every side to draw it. */
	private double slideFor(Case c, double out) {
		double lo = 0, hi = c.distance * 3 + 12;
		for (int i = 0; i < 48; i++) {
			double mid = (lo + hi) / 2;
			place(c, mid);
			if (Culling.padNeeded(culler, probe.boundingBox) < out) lo = mid;
			else hi = mid;
		}
		return (lo + hi) / 2;
	}

	private void removeProbe() {
		if (probe == null) return;
		probe.ignoreFrustumCull = false;
		Culling.place(probe, cx, -1000, cz, 0, 0);
		probe.markDead();
		Culling.frozen = null;
		probe = null;
	}
}
