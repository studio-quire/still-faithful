package org.studioquire.oldfaithful.polish.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.studioquire.oldfaithful.polish.DuskLight;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor;

/**
 * #6, smooth dusk, against beta's own baking. A scene of most kinds of block (torches, lava, glowstone, water, leaves,
 * tinted grass, slabs, stairs, fences, rails, redstone, plants, a bed, a door, a piston, a powered repeater, fire, a
 * cave under the open sky, a beam's and a fence's undersides, a chest and a minecart) is drawn in the same hidden window at several
 * whole darknesses, first with beta's lighting (DuskLight.betaForTest: baked, rebuilt at each step), then with smooth
 * dusk at the same darknesses, and the frames are compared pixel by pixel, in smooth and flat lighting, Fancy and
 * Fast. Also: halfway between two steps every pixel lies between beta's two pictures; a darkness step marks no chunk
 * for rebuilding (beta's marks every sky-lit one); no vertex fell back to beta's baking. Everything that changes
 * from frame to frame on its own is held still meanwhile (texture animation, particles, clouds, the time of day, the
 * fog's lag, wandering animals), the same for both. Pictures in dusk/ in the run folder.
 * With -Doldfaithful.selftest.duskFull=true: four modes and five steps instead of two and three.
 */
public final class DuskCheck implements WorldCheck {
	private static final boolean FULL = Boolean.getBoolean("oldfaithful.selftest.duskFull");
	/** Smooth lighting, fancy graphics. */
	private static final boolean[][] MODES = FULL ? new boolean[][] {{true, true}, {false, true}, {true, false}, {false, false}}
			: new boolean[][] {{true, true}, {false, false}};
	private static final int[] STEPS = FULL ? new int[] {0, 3, 4, 7, 11} : new int[] {0, 4, 5, 11};
	/** Halfway between this step and the next (both in STEPS). */
	private static final int MID = FULL ? 3 : 4;
	private static final int G = 100;
	private static final File OUT = new File("dusk");

	// Read by the selftest mixins.
	public static volatile boolean frozen, cracks;
	public static volatile int[] crackAt;
	public static int notifyMarks;
	public static long rebuildNanos;
	public static int rebuilds;
	private static volatile boolean grabWanted;
	private static volatile int[] grabbed;
	private static int width, height;

	private SelfTest results;
	private World world;
	private boolean on;
	private int ox, oz;
	private final long[] times = new long[12];
	private long time, duskFrom = -1;
	private Object[] saved;
	private final long[] timingNanos = new long[2];
	private final int[] timingChunks = new int[2];

	// The run: a list of actions, one after another.
	private final List<Step> plan = new ArrayList<>();
	private int at;
	private int waited, quiet;
	private final int[][] beta = new int[12][], smooth = new int[12][], stepped = new int[12][];
	private boolean smoothAtStart;
	private final float[] steppedDarkness = new float[12];
	private int[] mid;
	private String mode;
	private int captures;

	private interface Step {
		/** One tick; true when done. */
		boolean tick(Minecraft mc);
	}

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(PolishConfig.DUSK_KEY);
		smoothAtStart = DuskLight.smooth;
		if (on) results.check("still #6: the light shader compiled and is in use (OpenGL 2.0)", DuskLight.active());
		saved = new Object[] {mc.options.difficulty, mc.options.hideHud, mc.options.bobView, mc.options.viewDistance, mc.options.ao,
				mc.options.fancyGraphics, mc.player.x, mc.player.y - mc.player.standingEyeHeight, mc.player.z};
		mc.options.difficulty = 0;
		mc.options.hideHud = true;
		mc.options.bobView = false;
		mc.options.viewDistance = 2;
		world.getProperties().setRaining(false);
		world.getProperties().setThundering(false);
		world.getProperties().setRainTime(1_000_000);
		world.getProperties().setThunderTime(1_000_000);
		world.setRainGradient(0.0F);
		// Somewhere water doesn't freeze (a snowy biome turns the pool to ice between pictures).
		int px = (int) Math.floor(mc.player.x), pz = (int) Math.floor(mc.player.z);
		ox = px + 96;
		oz = pz;
		for (int i = 0; i < 64 && snowy(); i++) {
			ox = px + 96 + (i % 8) * 64;
			oz = pz + (i / 8 - 4) * 64;
		}
		// The spawn wanders until it finds sand, so it differs every run, and a cold region can cover all of those
		// (2026-11-09: spawned near -470,300, every place snowy): then further out, 2 km across.
		for (int i = 0; i < 256 && snowy(); i++) {
			ox = px - 1024 + (i % 16) * 128;
			oz = pz - 1024 + (i / 16) * 128;
		}
		results.check("still #6: a place for the scene where water doesn't freeze, at " + ox + "," + oz, !snowy());
		build();
		findTimes();
		frozen = true;
		time = times[0];
		plan.add(settle(200));
		if (!on) {
			// Switched off: beta's own lighting, whose steps still mark the sky-lit chunks.
			plan.add(stepsRebuild(true, false));
			plan.add(c -> {
				frozen = false;
				restore(mc);
				return true;
			});
			return;
		}
		for (int m = 0; m < MODES.length; m++) {
			boolean ao = MODES[m][0], fancy = MODES[m][1], first = m == 0;
			String name = (ao ? "smooth" : "flat") + "-" + (fancy ? "fancy" : "fast");
			plan.add(apply(ao, fancy, true, name));
			for (int n : STEPS) plan.add(capture(n, n, true));
			plan.add(apply(ao, fancy, false, name));
			for (int n : STEPS) plan.add(capture(n, n, false));
			plan.add(capture(MID, MID + 0.5F, false));
			if (first) {
				// Stepped dusk as the game runs it: no darkness set by the test, the time in the middle of each step
				// (where smooth dusk is between two steps), drawn at the world's own whole darkness.
				plan.add(c -> {
					DuskLight.smooth = false;
					DuskLight.testDarkness = Float.NaN;
					return true;
				});
				for (int n : STEPS) plan.add(capture(n, Float.NaN, false));
			}
			plan.add(c -> {
				compare(name, first, STEPS, true);
				return true;
			});
		}
		plan.add(stepsRebuild(true, false));
		plan.add(apply(true, true, false, "rebuilds"));
		plan.add(stepsRebuild(false, true));
		plan.add(stepsRebuild(false, false));
		if (FULL) {
			// The view rebuilt three times with each lighting, in turn: what a chunk's mesh costs either way.
			for (int i = 0; i < 3; i++) {
				plan.add(apply(true, true, true, "timing"));
				plan.add(apply(true, true, false, "timing"));
			}
			plan.add(c -> {
				double b = timingNanos[0] / 1e6 / Math.max(1, timingChunks[0]), m = timingNanos[1] / 1e6 / Math.max(1, timingChunks[1]);
				results.pass(String.format(Locale.ROOT, "still #6: building a chunk's mesh (smooth lighting, Fancy), three rebuilds of the view each: "
						+ "beta's lighting %.3f ms over %d chunks, smooth dusk %.3f ms over %d (%+.0f%%)", b, timingChunks[0], m, timingChunks[1],
						(m / b - 1) * 100));
				return true;
			});
			nether();
		}
		plan.add(c -> {
			frozen = false;
			cracks = false;
			DuskLight.testDarkness = Float.NaN;
			DuskLight.smooth = smoothAtStart;
			restore(mc);
			results.check("still #6: no vertex in the scene fell back to beta's baking (blocks drawn in one pass " + DuskLight.blocksOnePass
					+ ", with weights decoded " + DuskLight.blocksCoded + " in " + DuskLight.codePasses + " code passes, kept as drawn "
					+ DuskLight.blocksStatic + ")", DuskLight.verticesFallback == 0);
			return true;
		});
	}

	@Override
	public boolean tick(Minecraft mc) {
		hold(mc);
		while (at < plan.size()) {
			if (!plan.get(at).tick(mc)) return false;
			at++;
			waited = 0;
			quiet = 0;
		}
		return true;
	}

	/**
	 * The Nether: its own luminance table, no sky light (every pair keeps beta's colours), at its fixed darkness (11).
	 * The same scene built there, compared both ways in the default graphics, then back to the overworld.
	 */
	private void nether() {
		int[] eleven = {11};
		int[] home = new int[2];
		plan.add(c -> {
			home[0] = ox;
			home[1] = oz;
			c.changeDimension();
			world = c.world;
			ox = (int) Math.floor(c.player.x);
			oz = (int) Math.floor(c.player.z) + 24;
			build();
			return true;
		});
		plan.add(settle(200));
		plan.add(c -> {
			results.check("still #6: in the Nether (dimension " + c.player.dimensionId + "), darkness " + world.getAmbientDarkness(1.0F),
					c.player.dimensionId == -1 && world.getAmbientDarkness(1.0F) == 11);
			return true;
		});
		plan.add(apply(true, true, true, "nether"));
		plan.add(capture(11, 11, true));
		plan.add(apply(true, true, false, "nether"));
		plan.add(capture(11, 11, false));
		plan.add(c -> {
			compare("nether", true, eleven, false);
			return true;
		});
		plan.add(c -> {
			c.changeDimension();
			world = c.world;
			ox = home[0];
			oz = home[1];
			return true;
		});
	}

	/** Options and the player as they were before. */
	private void restore(Minecraft mc) {
		mc.options.difficulty = (Integer) saved[0];
		mc.options.hideHud = (Boolean) saved[1];
		mc.options.bobView = (Boolean) saved[2];
		mc.options.viewDistance = (Integer) saved[3];
		mc.options.ao = (Boolean) saved[4];
		mc.options.fancyGraphics = (Boolean) saved[5];
		mc.player.noClip = false;
		mc.player.setPositionAndAnglesKeepPrevAngles((Double) saved[6], (Double) saved[7], (Double) saved[8], 0.0F, 0.0F);
		mc.worldRenderer.reload();
	}

	/** The camera at the scene, the time and weather still, animals and items out of the way. */
	private void hold(Minecraft mc) {
		PlayerEntity p = mc.player;
		double x = ox + 0.5, y = G, z = oz + 1.5;
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.setPositionAndAnglesKeepPrevAngles(x, y, z, 0.0F, 9.0F);
		p.prevYaw = p.yaw;
		p.prevPitch = p.pitch;
		p.inventory.main[p.inventory.selectedSlot] = null;
		world.setTime(time);
		world.setRainGradient(0.0F);
		for (Object o : new ArrayList<Object>(world.entities)) {
			Entity e = (Entity) o;
			if (e instanceof PlayerEntity || e instanceof MinecartEntity) continue;
			if (Math.abs(e.x - ox) < 40 && Math.abs(e.z - oz - 8) < 40) e.markDead();
		}
	}

	// ---- The scene

	private boolean snowy() {
		for (int x = ox - 16; x <= ox + 16; x += 4) {
			for (int z = oz - 8; z <= oz + 24; z += 4) {
				if (world.method_1781().getBiome(x, z).canSnow()) return true;
			}
		}
		return false;
	}

	private void set(int x, int y, int z, int id, int meta) {
		world.setBlock(ox + x, G + y, oz + z, id, meta);
	}

	private void set(int x, int y, int z, int id) {
		set(x, y, z, id, 0);
	}

	private void build() {
		// Every chunk around generated and decorated first, so nothing (trees, snow) is added to the scene later.
		for (int x = ox - 64; x <= ox + 64; x += 16) {
			for (int z = oz - 56; z <= oz + 72; z += 16) world.getBlockId(x, 64, z);
		}
		// Clear the space and lay a floating field: grass on dirt on stone.
		for (int x = -14; x <= 14; x++) {
			for (int z = -8; z <= 24; z++) {
				for (int y = 14; y >= -4; y--) set(x, y, z, 0);
				if (x >= -10 && x <= 10 && z >= 0 && z <= 20) {
					set(x, -3, z, 1);
					set(x, -2, z, 3);
					set(x, -1, z, 2);
				}
			}
		}
		// Near row: slabs, stairs, a fence, a rail with a minecart, redstone, a lever.
		set(-7, 0, 4, 44);
		set(-6, 0, 4, 44, 3);
		set(-5, 0, 4, 53, 2);
		set(-4, 0, 4, 67, 0);
		for (int x = -3; x <= -1; x++) set(x, 0, 4, 85);
		set(-2, 0, 5, 85);
		for (int z = 3; z <= 6; z++) set(2, 0, z, 66, 0);
		MinecartEntity cart = new MinecartEntity(world, ox + 2.5, G + 0.5, oz + 5.5, 0);
		world.spawnEntity(cart);
		set(4, 0, 4, 76, 5);
		for (int z = 5; z <= 7; z++) set(4, 0, z, 55);
		set(6, 0, 4, 69, 5 | 8);
		// Middle row: a pool, glowstone, a torch, wheat, cactus, a chest, snow, a powered repeater.
		for (int x = -6; x <= -4; x++) {
			for (int z = 7; z <= 9; z++) set(x, -1, z, 9);
		}
		set(0, 0, 8, 89);
		crackAt = new int[] {ox, G, oz + 8};
		set(2, 0, 8, 50, 5);
		set(4, -1, 8, 60, 7);
		set(5, -1, 8, 60, 7);
		set(4, 0, 8, 59, 7);
		set(5, 0, 8, 59, 7);
		set(8, -1, 8, 12);
		for (int y = 0; y <= 2; y++) set(8, y, 8, 81);
		set(-8, 0, 8, 54);
		set(7, 0, 5, 78);
		set(-8, 0, 5, 38);
		set(-8, 0, 6, 37);
		set(6, 0, 7, 31, 1);
		set(7, 0, 7, 31, 1);
		// A fence in the air, its bars' undersides above the camera (lit from the fence's own space, Old Faithful's #30).
		set(2, 2, 4, 85);
		set(3, 2, 4, 85);
		// A beam overhead, its underside towards the camera; glass and ice.
		for (int x = -3; x <= 3; x++) set(x, 3, 6, 5);
		set(3, 0, 10, 20);
		set(-7, -1, 1, 79);
		// Far row: a stone room open to the front with a torch and glowstone inside, a ladder on it, a tree, a bed, a
		// door, a piston, a jack-o'-lantern; on the left, lava in stone and fire on netherrack, away from anything that burns.
		for (int x = -5; x <= 1; x++) {
			for (int y = 0; y <= 3; y++) {
				for (int z = 11; z <= 15; z++) {
					boolean inside = x > -5 && x < 1 && y < 3 && z > 11 && z < 15;
					boolean door = z == 11 && x >= -3 && x <= -1 && y <= 1;
					set(x, y, z, inside || door ? 0 : 1);
				}
			}
		}
		set(-2, 0, 13, 50, 5);
		set(-4, 0, 14, 89);
		set(-4, 1, 10, 65, 2);
		for (int y = 0; y <= 3; y++) set(5, y, 12, 17);
		for (int x = 4; x <= 6; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 11; z <= 13; z++) {
					if (x != 5 || z != 12 || y == 4) set(x, y, z, 18);
				}
			}
		}
		set(2, 0, 12, 26, 0);
		set(2, 0, 13, 26, 8);
		set(8, 0, 12, 64, 0);
		set(8, 1, 12, 64, 8);
		set(6, 0, 9, 76, 5);
		set(6, 0, 10, 94, 0);
		set(6, 0, 11, 76, 5);
		set(-7, 0, 12, 33, 1);
		set(7, 0, 15, 91, 2);
		set(-9, -1, 15, 87);
		set(-9, 0, 15, 51);
		set(-9, -1, 12, 11);
		set(-9, -2, 12, 1);
	}

	/** For each darkness, a time of day in the middle of its step (the first such stretch of the day). */
	private void findTimes() {
		long saved = world.getTime();
		int[] first = new int[12], last = new int[12];
		java.util.Arrays.fill(first, -1);
		int prev = -1, runStart = 0;
		int[] bestLen = new int[12];
		for (int t = 0; t <= 24000; t += 5) {
			world.setTime(t);
			int d = t == 24000 ? -2 : world.getAmbientDarkness(1.0F);
			if (prev == 0 && d == 1 && duskFrom < 0) duskFrom = t;
			if (d != prev) {
				if (prev >= 0 && t - runStart > bestLen[prev]) {
					bestLen[prev] = t - runStart;
					first[prev] = runStart;
					last[prev] = t - 5;
				}
				prev = d;
				runStart = t;
			}
		}
		world.setTime(saved);
		for (int n = 0; n < 12; n++) times[n] = first[n] < 0 ? -1 : (first[n] + last[n]) / 2;
		StringBuilder s = new StringBuilder();
		for (int n : STEPS) s.append(n).append(" at ").append(times[n]).append(' ');
		results.check("still #6: a time of day found for each darkness to compare (" + s.toString().trim() + ")", allFound());
	}

	private boolean allFound() {
		for (int n : STEPS) {
			if (times[n] < 0) return false;
		}
		return times[MID + 1] >= 0 && duskFrom >= 0;
	}

	// ---- Steps

	/** No render chunk near the scene waits to be built (the far terrain may still be settling its light). */
	private boolean built(Minecraft mc) {
		for (ChunkBuilder c : ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$chunks()) {
			if (!c.dirty) continue;
			if (c.x + 16 > ox - 24 && c.x < ox + 24 && c.z + 16 > oz - 12 && c.z < oz + 40 && c.y + 16 > G - 16 && c.y < G + 24) return false;
		}
		return true;
	}

	private static void log(String s) {
		System.out.println("[oldfaithful-selftest] (dusk) " + s);
	}

	private Step settle(int ticks) {
		return mc -> {
			waited++;
			quiet = built(mc) ? quiet + 1 : 0;
			if (waited % 100 == 0) log("settling: " + waited + " ticks, quiet " + quiet + ", waiting chunks "
					+ ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().size());
			return waited >= ticks && quiet >= 20 || waited > 1200;
		};
	}

	/** Graphics and lighting set, beta's lighting or smooth dusk, the view rebuilt. */
	private Step apply(boolean ao, boolean fancy, boolean betaLight, String name) {
		return mc -> {
			if (waited++ == 0) {
				mode = name;
				mc.options.ao = ao;
				mc.options.fancyGraphics = fancy;
				DuskLight.betaForTest = betaLight;
				DuskLight.testDarkness = betaLight ? Float.NaN : 0.0F;
				DuskLight.smooth = true;
				cracks = true;
				rebuildNanos = 0;
				rebuilds = 0;
				mc.worldRenderer.reload();
				return false;
			}
			quiet = built(mc) ? quiet + 1 : 0;
			if (quiet < 10 && waited < 1200) return false;
			if (quiet < 10) {
				results.check("still #6: " + name + " built after a reload", false);
				return true;
			}
			if (name.equals("timing")) {
				timingNanos[betaLight ? 0 : 1] += rebuildNanos;
				timingChunks[betaLight ? 0 : 1] += rebuilds;
			}
			results.pass(String.format(Locale.ROOT, "still #6: %s, %s: the view built, %d chunks at %.2f ms each", name,
					betaLight ? "beta's lighting" : "smooth dusk", rebuilds, rebuilds == 0 ? 0.0 : rebuildNanos / 1e6 / rebuilds));
			return true;
		};
	}

	/**
	 * The time set for darkness {@code n}, smooth dusk drawn at {@code d} (NaN: stepped dusk, at the darkness it takes
	 * from the world itself); once still, a frame read back.
	 */
	private Step capture(int n, float d, boolean betaLight) {
		boolean step = !betaLight && Float.isNaN(d);
		return mc -> {
			if (waited++ == 0) {
				time = times[n];
				world.setTime(time);
				if (!betaLight && !step) DuskLight.testDarkness = d;
				grabbed = null;
				return false;
			}
			quiet = built(mc) && world.ambientDarkness == n ? quiet + 1 : 0;
			if (quiet < 12 && waited < 400) return false;
			if (quiet < 12) {
				results.check("still #6: " + mode + " darkness " + n + " settled", false);
				return true;
			}
			if (!grabWanted && grabbed == null) {
				grabWanted = true;
				return false;
			}
			if (grabbed == null) return false;
			int[] px = grabbed;
			grabbed = null;
			captures++;
			log(mode + (betaLight ? ", beta's lighting" : step ? ", stepped dusk" : ", smooth dusk") + " at " + (step ? DuskLight.darkness : d)
					+ ": frame taken after " + waited + " ticks");
			if (step) {
				stepped[n] = px;
				steppedDarkness[n] = DuskLight.darkness;
			} else if (d != n) mid = px;
			else if (betaLight) beta[n] = px;
			else smooth[n] = px;
			return true;
		};
	}

	/**
	 * Each step's pictures, and the halfway picture against the steps either side. Halfway, a pixel can lie a little
	 * outside the two (4/255 allowed): an entity's shadow darkens the ground by an amount that follows the light too,
	 * and the product of two straight changes bows away from a straight one.
	 */
	private void compare(String name, boolean save, int[] steps, boolean halfway) {
		for (int n : steps) {
			int[] a = beta[n], b = smooth[n];
			if (a == null || b == null || a.length != b.length) {
				results.check("still #6: " + name + " darkness " + n + ": both pictures taken", false);
				continue;
			}
			int max = 0, over1 = 0, over2 = 0, differ = 0;
			for (int i = 0; i < a.length; i++) {
				int diff = diff(a[i], b[i]);
				if (diff > 0) differ++;
				if (diff > 1) over1++;
				if (diff > 2) over2++;
				max = Math.max(max, diff);
			}
			results.check(String.format(Locale.ROOT,
					"still #6: %s at darkness %d, smooth dusk against beta's baking: %d of %d pixels differ, %d by more than 1/255, "
							+ "%d by more than 2 (largest %d/255)", name, n, differ, a.length, over1, over2, max), over2 == 0);
			if (save) {
				save(a, "beta-" + name + "-" + n);
				save(b, "smooth-" + name + "-" + n);
			}
			if (over1 > 0) saveDiff(a, b, "diff-" + name + "-" + n);
		}
		for (int n : steps) {
			int[] a = beta[n], b = stepped[n];
			if (a == null || b == null || a.length != b.length) continue;
			int max = 0, differ = 0;
			for (int i = 0; i < a.length; i++) {
				int diff = diff(a[i], b[i]);
				if (diff > 0) differ++;
				max = Math.max(max, diff);
			}
			// Where smooth dusk would be at this time: between the step and the next (but for full day and night).
			float would = smoothDarkness(times[n]);
			results.check(String.format(Locale.ROOT,
					"still #6: %s, stepped dusk at a time of day where smooth would draw darkness %.2f: drawn at %.1f, against beta's "
							+ "baking at %d: %d of %d pixels differ (largest %d/255)", name, would, steppedDarkness[n], n, differ, a.length, max),
					steppedDarkness[n] == n && max <= 1 && (n == 0 || n == 11 || would > n + 0.05F));
			if (save) save(b, "stepped-" + name + "-" + n);
			if (max > 1) saveDiff(a, b, "diff-stepped-" + name + "-" + n);
		}
		int[] lo = beta[MID], hi = beta[MID + 1];
		if (halfway) {
			results.check("still #6: " + name + ": pictures halfway between steps " + MID + " and " + (MID + 1) + " taken", mid != null && lo != null && hi != null);
		}
		if (halfway && mid != null && lo != null && hi != null) {
			int outside = 0, between = 0;
			for (int i = 0; i < mid.length; i++) {
				boolean in = true, apart = false;
				for (int s = 0; s < 24; s += 8) {
					int m = mid[i] >> s & 255, p = lo[i] >> s & 255, q = hi[i] >> s & 255;
					if (m < Math.min(p, q) - 4 || m > Math.max(p, q) + 4) in = false;
					if (Math.abs(m - p) > 2 && Math.abs(m - q) > 2) apart = true;
				}
				if (!in) outside++;
				if (apart) between++;
			}
			results.check(String.format(Locale.ROOT, "still #6: %s at darkness %.1f lies between beta's %d and %d: %d pixels outside (by more than "
					+ "4/255), %d strictly between both", name, MID + 0.5, MID, MID + 1, outside, between), outside == 0 && between > 0);
			if (save) save(mid, "smooth-" + name + "-" + MID + "-half");
		}
		java.util.Arrays.fill(beta, null);
		java.util.Arrays.fill(smooth, null);
		java.util.Arrays.fill(stepped, null);
		mid = null;
	}

	private static int diff(int a, int b) {
		int m = 0;
		for (int s = 0; s < 24; s += 8) m = Math.max(m, Math.abs((a >> s & 255) - (b >> s & 255)));
		return m;
	}

	/**
	 * Dusk run through as the game plays it: from just before a step, the time advanced 40 ticks a tick for five
	 * seconds, crossing several steps. Beta's lighting marks the sky-lit chunks for rebuilding at each; smooth dusk none.
	 */
	private Step stepsRebuild(boolean betaLight, boolean smoothDusk) {
		int[] steps = new int[1];
		int[] last = new int[1];
		boolean[] fractional = new boolean[1];
		String which = betaLight ? "beta's lighting" : smoothDusk ? "smooth dusk" : "stepped dusk";
		return mc -> {
			if (waited++ == 0) {
				DuskLight.smooth = smoothDusk;
				if (betaLight) {
					DuskLight.betaForTest = true;
					DuskLight.testDarkness = Float.NaN;
					mc.worldRenderer.reload();
				} else {
					DuskLight.testDarkness = Float.NaN;
				}
				time = duskFrom - 400;
				return false;
			}
			if (waited < 100) {
				if (!built(mc)) waited = 2;
				return false;
			}
			if (waited == 100) {
				notifyMarks = 0;
				steps[0] = 0;
				last[0] = world.ambientDarkness;
				return false;
			}
			if (DuskLight.darkness != (int) DuskLight.darkness) fractional[0] = true;
			time += 40;
			if (world.ambientDarkness != last[0]) {
				steps[0]++;
				last[0] = world.ambientDarkness;
			}
			if (waited < 200) return false;
			results.check("still #6: " + which + ", dusk played through: " + steps[0] + " darkness steps marked " + notifyMarks
					+ " render chunks for rebuilding (" + (betaLight ? "beta: every sky-lit one" : "none") + ")"
					+ (betaLight ? "" : ", frames drawn at " + (fractional[0] ? "darknesses between steps" : "whole steps only")),
					steps[0] >= 3 && (betaLight ? notifyMarks > 0 : notifyMarks == 0 && fractional[0] == smoothDusk));
			return true;
		};
	}

	/** Smooth dusk's darkness at a time of day, as DuskLight.frame works it out (clear weather). */
	private float smoothDarkness(long t) {
		long saved = world.getTime();
		boolean was = DuskLight.smooth;
		float d = DuskLight.darkness;
		try {
			world.setTime(t);
			DuskLight.smooth = true;
			DuskLight.frame(world, 1.0F);
			return DuskLight.darkness;
		} finally {
			world.setTime(saved);
			DuskLight.smooth = was;
			DuskLight.darkness = d;
		}
	}

	// ---- Frames

	/** At the end of each frame (FrameMixin): the frame read back if asked for. */
	public static void frame() {
		if (!grabWanted) return;
		grabWanted = false;
		int w = Display.getWidth(), h = Display.getHeight();
		ByteBuffer px = BufferUtils.createByteBuffer(w * h * 4);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = ((h - 1 - y) * w + x) * 4;
				out[y * w + x] = (px.get(i) & 0xFF) << 16 | (px.get(i + 1) & 0xFF) << 8 | px.get(i + 2) & 0xFF;
			}
		}
		width = w;
		height = h;
		grabbed = out;
	}

	private void save(int[] px, String name) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, width, height, px, 0, width);
		write(img, name);
	}

	/** Differences scaled up: grey where equal, brighter the larger. */
	private void saveDiff(int[] a, int[] b, String name) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < a.length; i++) {
			int d = diff(a[i], b[i]);
			int v = d == 0 ? 40 : Math.min(255, 80 + d * 40);
			img.setRGB(i % width, i / width, d == 0 ? v << 16 | v << 8 | v : v << 16);
		}
		write(img, name);
	}

	private static void write(BufferedImage img, String name) {
		try {
			OUT.mkdirs();
			ImageIO.write(img, "png", new File(OUT, name + ".png"));
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** Kept for the rebuild counts (ChunkBuilderTimingMixin). */
	public static void rebuilt(ChunkBuilder chunk, long nanos) {
		rebuilds++;
		rebuildNanos += nanos;
	}
}
