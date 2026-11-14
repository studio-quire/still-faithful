package org.studioquire.oldfaithful.polish.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.DuskLight;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.SmoothWater;
import org.studioquire.oldfaithful.selftest.ScreenCapture;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor;

/**
 * Smooth water ({@code render.smoothWater}, Shaded, Even or Beta): small scenes on floating islands where the light varies
 * across the water, each pictured with Smooth Lighting on in each look, chosen as the settings screen chooses it (its
 * registry steps the setting: the file written, then applied), the scene's chunks rebuilt between: a pond under a tree
 * at noon with a bank along one shore, a pond by two torches at midnight (in Fancy and in Fast graphics), a flooded
 * tunnel with a waterfall in it, a lava pool in shade, and a pond against a wall with a log standing in it. The game
 * starts in the file's choice. With the change in, Shaded's water differs from Beta's and the lava doesn't; Even is
 * Beta's exactly by the wall at noon, where only the wall varies the light, and differs from Shaded there; every choice
 * takes at once, and a choice rebuilds the chunks in view (checked once, at the start: between a
 * scene's pictures only its own chunks are rebuilt, so the rest of the view stays as it was). With it off
 * (-Pstill.beta), every picture of a scene is the same (none of the change
 * is in), and choosing Shaded or Even waits for a restart. Smooth dusk takes every vertex apart (none falls back).
 * Animation, particles and clouds held still meanwhile (DuskCheck's). The file's choice is put back at the end. Pictures
 * in local/run/smooth-water/ (<scene>-beta.png, -shaded.png, -even.png). -Doldfaithful.selftest.waterLooks=true
 * (-Pstill.waterLooks) adds lava lit smoothly too (lava-lava.png), a look not taken, and times rebuilding a sheet of open
 * water both ways.
 */
public final class SmoothWaterCheck implements WorldCheck {
	private static final String KEY = PolishConfig.SMOOTH_WATER_KEY, FILE = "stillfaithful-polish.properties";
	private static final boolean LOOKS = Boolean.getBoolean("oldfaithful.selftest.waterLooks");
	private static final File OUT = new File("../smooth-water");
	/** The islands' grass, and the water's surface cells, are at G - 1. */
	private static final int G = 100;
	private static final int SPACING = 40;

	private interface Step {
		boolean tick(Minecraft mc);
	}

	/** A scene: where it is, the time of day, and the camera. */
	private static final class Scene {
		final String name;
		final int index;
		final long time;
		final double[] eye, at;
		final boolean water;

		Scene(String name, int index, long time, double[] eye, double[] at, boolean water) {
			this.name = name;
			this.index = index;
			this.time = time;
			this.eye = eye;
			this.at = at;
			this.water = water;
		}
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private final List<Scene> scenes = new ArrayList<>();
	private final Map<String, BufferedImage> taken = new HashMap<>();
	private SelfTest results;
	private World world;
	private boolean on;
	private int bx, bz;
	private Scene current;
	private Object[] saved;
	private long fallbackBefore;
	private Choice choice;
	/** The file's choice when the check began, put back at the end. */
	private String start;
	/** Choices that didn't take as expected: at once with the change in, waiting for a restart without it. */
	private final List<String> mistimed = new ArrayList<>();

	/** The Smooth Water setting as the settings screen holds it, reached by name (package-private there). */
	private static final class Choice {
		private static final String PKG = "org.studioquire.oldfaithful.options.settings.";
		private final Object registry, setting;
		private final Method value, step, waiting;

		Choice() throws ReflectiveOperationException {
			Class<?> registryType = Class.forName(PKG + "SettingsRegistry"), settingType = Class.forName(PKG + "Setting");
			registry = registryType.getMethod("get").invoke(null);
			Method everything = registryType.getDeclaredMethod("everything");
			value = registryType.getDeclaredMethod("value", settingType);
			step = registryType.getDeclaredMethod("step", settingType);
			waiting = registryType.getDeclaredMethod("waitingForRestart", settingType);
			Field fileField = settingType.getDeclaredField("file"), keyField = settingType.getDeclaredField("key");
			for (AccessibleObject a : new AccessibleObject[] {everything, value, step, waiting, fileField, keyField}) a.setAccessible(true);
			Object found = null;
			for (Object group : (List<?>) everything.invoke(registry)) {
				Method all = group.getClass().getDeclaredMethod("all");
				all.setAccessible(true);
				for (Object s : (List<?>) all.invoke(group)) {
					if (FILE.equals(fileField.get(s)) && KEY.equals(keyField.get(s))) found = s;
				}
			}
			if (found == null) throw new IllegalStateException("No " + KEY + " on the settings screen");
			setting = found;
		}

		String value() throws ReflectiveOperationException {
			return (String) value.invoke(registry, setting);
		}

		/** Steps it, as clicks on its button do, until it reads {@code to}. */
		void set(String to) throws ReflectiveOperationException {
			for (int i = 0; i < 4 && !to.equals(value()); i++) step.invoke(registry, setting);
			if (!to.equals(value())) throw new IllegalStateException(KEY + " never reads " + to);
		}

		boolean waiting() throws ReflectiveOperationException {
			return (Boolean) waiting.invoke(registry, setting);
		}
	}

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(KEY);
		try {
			choice = new Choice();
			start = choice.value();
		} catch (ReflectiveOperationException | RuntimeException e) {
			results.fail("smooth water: the setting on the settings screen", e);
			choice = null;
		}
		String atStart = PolishConfig.smoothWaterAtStart();
		results.check("smooth water: the game started in the file's choice (" + start + "): " + atStart + ", the look " + SmoothWater.look
				+ ", the change " + (on ? "in" : "out"), atStart.equals(start) && SmoothWater.look.equals(atStart) && on == !atStart.equals(SmoothWater.BETA));
		saved = new Object[] {mc.options.hideHud, mc.options.bobView, mc.options.viewDistance, mc.options.ao, mc.options.fancyGraphics,
				mc.player.x, mc.player.y - mc.player.standingEyeHeight, mc.player.z};
		mc.options.hideHud = true;
		mc.options.bobView = false;
		mc.options.viewDistance = 2;
		mc.options.ao = true;
		mc.options.fancyGraphics = true;
		world.getProperties().setRaining(false);
		world.getProperties().setThundering(false);
		world.setRainGradient(0.0F);
		int px = (int) Math.floor(mc.player.x), pz = (int) Math.floor(mc.player.z);
		// Somewhere water doesn't freeze, away from the other checks' scenes.
		bx = px - 80;
		bz = pz + 160;
		for (int i = 0; i < 256 && snowy(); i++) {
			bx = px - 1024 + (i % 16) * 128;
			bz = pz - 1024 + (i / 16) * 128;
		}
		results.check("smooth water: a place for the scenes where water doesn't freeze, at " + bx + "," + bz, !snowy());
		build();
		fallbackBefore = DuskLight.verticesFallback;
		SmoothWater.lavaForTest = false;
		current = scenes.get(0);
		if (choice == null) return;
		// Everything rebuilt for the scenes: with the change in, by a choice's own redraw (GameRendererMixin), so it's checked.
		Object[] before = {null};
		int[] ticks = {0};
		plan.add(c -> {
			before[0] = ((WorldRendererAccessor) c.worldRenderer).oldfaithful$chunks();
			if (on) SmoothWater.choose(SmoothWater.look);
			else c.worldRenderer.reload();
			return true;
		});
		plan.add(c -> {
			boolean redrawn = ((WorldRendererAccessor) c.worldRenderer).oldfaithful$chunks() != before[0];
			if (!on || redrawn || ++ticks[0] > 100) {
				if (on) results.check("smooth water: a choice rebuilds the chunks in view before the next frame", redrawn);
				return true;
			}
			return false;
		});
		plan.add(settle(200));
		for (Scene s : scenes) {
			shoot(s, "beta", SmoothWater.BETA, false);
			shoot(s, "shaded", SmoothWater.SHADED, false);
			if (s.water) shoot(s, "even", SmoothWater.EVEN, false);
			if (LOOKS && on && !s.water) shoot(s, "lava", SmoothWater.SHADED, true);
		}
		// Fast graphics, where beta draws water's layer without blending colours across a face.
		plan.add(c -> {
			c.options.fancyGraphics = false;
			return true;
		});
		shoot(scenes.get(1), "fast-beta", SmoothWater.BETA, false);
		shoot(scenes.get(1), "fast-shaded", SmoothWater.SHADED, false);
		if (LOOKS && on) timing();
		plan.add(c -> {
			compare();
			return true;
		});
	}

	@Override
	public boolean tick(Minecraft mc) {
		Scene s = current;
		double ex = bx + s.index * SPACING + s.eye[0], ey = G + s.eye[1], ez = bz + s.eye[2];
		double dx = bx + s.index * SPACING + s.at[0] - ex, dy = G + s.at[1] - ey, dz = bz + s.at[2] - ez;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
		PlayerEntity p = mc.player;
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.setPositionAndAnglesKeepPrevAngles(ex, ey - p.standingEyeHeight, ez, yaw, pitch);
		p.prevYaw = p.yaw;
		p.prevPitch = p.pitch;
		world.setTime(s.time);
		world.setRainGradient(0.0F);
		for (Object o : new ArrayList<Object>(world.entities)) {
			Entity e = (Entity) o;
			if (e instanceof PlayerEntity) continue;
			if (Math.abs(e.x - (bx + 120)) < 160 && Math.abs(e.z - bz) < 40) e.markDead();
		}
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
		}
		DuskCheck.frozen = false;
		SmoothWater.lavaForTest = false;
		if (choice != null && start != null) {
			try {
				choice.set(start);
			} catch (ReflectiveOperationException | RuntimeException e) {
				results.fail("smooth water: putting the file's choice back", e);
			}
		}
		// What the game started with (the timing sets the look directly); the reload below draws it.
		SmoothWater.look = PolishConfig.smoothWaterAtStart();
		SmoothWater.redraw = false;
		mc.options.hideHud = (Boolean) saved[0];
		mc.options.bobView = (Boolean) saved[1];
		mc.options.viewDistance = (Integer) saved[2];
		mc.options.ao = (Boolean) saved[3];
		mc.options.fancyGraphics = (Boolean) saved[4];
		p.noClip = false;
		p.setPositionAndAnglesKeepPrevAngles((Double) saved[5], (Double) saved[6], (Double) saved[7], 0, 0);
		mc.worldRenderer.reload();
		return true;
	}

	// ---- The scenes

	private boolean snowy() {
		for (int x = bx - 16; x <= bx + 6 * SPACING + 16; x += 8) {
			for (int z = bz - 16; z <= bz + 16; z += 8) {
				if (world.method_1781().getBiome(x, z).canSnow()) return true;
			}
		}
		return false;
	}

	private int sx;

	private void set(int x, int y, int z, int id, int meta) {
		world.setBlock(bx + sx + x, G + y, bz + z, id, meta);
	}

	private void set(int x, int y, int z, int id) {
		set(x, y, z, id, 0);
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) set(x, y, z, id);
			}
		}
	}

	/** An island of grass on dirt on stone, 21 across, with the air cleared above and below it. */
	private void island() {
		fill(-12, -6, -12, 12, 14, 12, 0);
		fill(-10, -3, -10, 10, -3, 10, 1);
		fill(-10, -2, -10, 10, -2, 10, 3);
		fill(-10, -1, -10, 10, -1, 10, 2);
	}

	/**
	 * Dirt under every solid block on the island, where beta would turn the grass to dirt on a random tick between two
	 * pictures (seen 2026-11-14: a sliver of a grass block's side by the water went brown).
	 */
	private void groundUnderSolid() {
		for (int x = -12; x <= 12; x++) {
			for (int z = -12; z <= 12; z++) {
				for (int y = -1; y <= 0; y++) {
					int at = world.getBlockId(bx + sx + x, G + y, bz + z), above = world.getBlockId(bx + sx + x, G + y + 1, bz + z);
					if (at == 2 && Block.BLOCKS_OPAQUE[above]) set(x, y, z, 3);
				}
			}
		}
	}

	private void build() {
		for (int x = bx - 32; x <= bx + 6 * SPACING + 32; x += 16) {
			for (int z = bz - 32; z <= bz + 32; z += 16) world.getBlockId(x, 64, z);
		}
		// A pond under a tree at noon: the canopy's shade over its west half, and a bank a block high along its south shore.
		sx = 0;
		island();
		fill(-3, -2, -3, 3, -1, 3, 9);
		fill(-4, 0, 4, 4, 0, 5, 2);
		fill(-6, 0, 0, -6, 3, 0, 17);
		for (int x = -9; x <= -1; x++) {
			for (int y = 3; y <= 5; y++) {
				for (int z = -4; z <= 4; z++) {
					boolean corner = (x == -9 || x == -1) && (z == -4 || z == 4) || y == 5 && (x < -8 || x > -3 || Math.abs(z) > 3);
					if (!(x == -6 && z == 0 && y == 3) && !corner) set(x, y, z, 18);
				}
			}
		}
		groundUnderSolid();
		scenes.add(new Scene("shore", 0, 6000, new double[] {4.5, 2.8, 6.5}, new double[] {-1.0, -1, 0.0}, true));
		// A pond at midnight, a torch on its shore and one on a post in it.
		sx = SPACING;
		island();
		fill(-4, -2, -4, 4, -1, 4, 9);
		set(1, 0, -5, 50, 5);
		fill(-2, -2, 1, -2, 0, 1, 4);
		set(-2, 1, 1, 50, 5);
		groundUnderSolid();
		scenes.add(new Scene("torch", 1, 18000, new double[] {2.5, 3.0, 7.5}, new double[] {-0.5, -1, -1.0}, true));
		// A tunnel into a hill at noon, flooded, with a waterfall partway in: the sky's light falling off inward.
		sx = 2 * SPACING;
		island();
		fill(-5, 0, -10, 5, 5, 0, 1);
		fill(-2, 0, -9, 2, 2, 0, 0);
		fill(-2, -2, -9, 2, -1, 3, 9);
		set(-2, 2, -5, 8, 0);
		groundUnderSolid();
		scenes.add(new Scene("cave", 2, 6000, new double[] {1.0, 1.4, 4.5}, new double[] {-0.2, -0.6, -6.0}, true));
		// A lava pool at noon, half under a stone overhang with walls behind it.
		sx = 3 * SPACING;
		island();
		fill(-6, -1, -6, 6, -1, 6, 1);
		fill(-2, -2, -2, 2, -1, 2, 11);
		fill(-5, 0, -5, 5, 1, -5, 1);
		fill(-5, 0, -5, -5, 1, 0, 1);
		fill(5, 0, -5, 5, 1, 0, 1);
		fill(-5, 2, -5, 5, 2, 0, 1);
		groundUnderSolid();
		scenes.add(new Scene("lava", 3, 6000, new double[] {0.5, 3.4, 7.5}, new double[] {0.5, -1, -1.0}, false));
		// A pond at noon against a cobblestone wall two high on its north shore, with a log standing in it.
		sx = 4 * SPACING;
		island();
		fill(-4, -2, -4, 4, -1, 4, 9);
		fill(-5, 0, -5, 5, 1, -5, 4);
		fill(-1, -2, -2, -1, 1, -2, 17);
		groundUnderSolid();
		scenes.add(new Scene("wall", 4, 6000, new double[] {3.5, 2.2, 3.5}, new double[] {-0.5, -1, -2.5}, true));
		if (LOOKS) {
			// For timing: a sheet of open water 48 across, nine chunks' worth of surface.
			sx = 5 * SPACING + 16;
			fill(-24, -6, -24, 23, 6, 23, 0);
			fill(-24, -3, -24, 23, -3, 23, 1);
			fill(-24, -2, -24, 23, -1, 23, 9);
		}
	}

	// ---- Steps

	/** No render chunk of the scenes waits to be built. */
	private boolean built(Minecraft mc) {
		for (ChunkBuilder c : ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$chunks()) {
			if (!c.dirty) continue;
			if (c.x + 16 > bx - 16 && c.x < bx + 6 * SPACING + 16 && c.z + 16 > bz - 24 && c.z < bz + 24 && c.y + 16 > G - 8 && c.y < G + 16) return false;
		}
		return true;
	}

	/** At least {@code ticks}, then until the scenes are built and lit (the waterfall has fallen by then). */
	private Step settle(int ticks) {
		int[] waited = {0}, quiet = {0};
		return mc -> {
			waited[0]++;
			quiet[0] = built(mc) ? quiet[0] + 1 : 0;
			return waited[0] >= ticks && quiet[0] >= 20 || waited[0] > 1500;
		};
	}

	/**
	 * The scene's water lit in one of the choices (chosen through the settings screen's registry, so the file is written
	 * and the choice applied as a click does it), its chunks rebuilt, then a picture once it's still.
	 */
	private void shoot(Scene s, String look, String value, boolean lava) {
		int[] waited = {0}, quiet = {0};
		plan.add(mc -> {
			if (waited[0]++ == 0) {
				current = s;
				DuskCheck.frozen = true;
				try {
					choice.set(value);
					// With the change in, every choice takes at once (Beta by the mixins standing aside); without it, only Beta.
					boolean expected = !on && !value.equals(SmoothWater.BETA);
					if (choice.waiting() != expected || on && !SmoothWater.look.equals(value)) {
						mistimed.add(value + (choice.waiting() ? " waits for a restart" : " doesn't wait") + ", the look " + SmoothWater.look);
					}
					// Only the scene's chunks are rebuilt (below), so the rest of the view stays as it was between a scene's
					// pictures; the redraw a choice asks for is checked once, at the start.
					SmoothWater.redraw = false;
				} catch (ReflectiveOperationException | RuntimeException e) {
					results.fail("smooth water: choosing " + value, e);
				}
				SmoothWater.lavaForTest = lava;
				int x0 = bx + s.index * SPACING;
				world.setBlocksDirty(x0 - 12, G - 8, bz - 12, x0 + 12, G + 16, bz + 12);
				return false;
			}
			quiet[0] = built(mc) ? quiet[0] + 1 : 0;
			if (quiet[0] >= 8) return true;
			if (waited[0] > 600) {
				results.check("smooth water: " + s.name + ", " + look + ": built", false);
				return true;
			}
			return false;
		});
		File file = new File(OUT, s.name + "-" + look + ".png");
		ScreenCapture[] capture = {null};
		plan.add(c -> {
			if (capture[0] == null) capture[0] = ScreenCapture.request(file, -1, -1);
			if (!capture[0].done()) return false;
			try {
				taken.put(s.name + "-" + look, ImageIO.read(file));
			} catch (IOException e) {
				results.fail("smooth water: reading " + file, e);
			}
			return true;
		});
	}

	/**
	 * The open water's chunks rebuilt twenty times, beta's way and smoothly in turn, the rebuilds timed (DuskCheck's
	 * counters, which time every chunk rebuild in the test).
	 */
	private void timing() {
		long[] nanos = new long[2];
		int[] chunks = new int[2];
		int x0 = bx + 5 * SPACING + 16;
		for (int i = 0; i < 20; i++) {
			boolean beta = i % 2 == 0;
			int[] idle = {0}, waited = {0}, quiet = {0};
			// Nothing else in the view waiting to be built, so only the water's chunks are timed.
			plan.add(mc -> ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().isEmpty() || ++idle[0] > 1200);
			plan.add(mc -> {
				if (waited[0]++ == 0) {
					// Set directly: choosing redraws everything in view, which would be timed too.
					SmoothWater.look = beta ? SmoothWater.BETA : SmoothWater.SHADED;
					DuskCheck.rebuildNanos = 0;
					DuskCheck.rebuilds = 0;
					world.setBlocksDirty(x0 - 24, G - 4, bz - 24, x0 + 23, G + 2, bz + 23);
					return false;
				}
				quiet[0] = built(mc) ? quiet[0] + 1 : 0;
				if (quiet[0] < 3 && waited[0] < 600) return false;
				nanos[beta ? 0 : 1] += DuskCheck.rebuildNanos;
				chunks[beta ? 0 : 1] += DuskCheck.rebuilds;
				return true;
			});
		}
		plan.add(c -> {
			// Per pass, as the passes rebuild the same area (the chunk counts differ a little with what's beside it).
			double b = nanos[0] / 1e6 / 10, m = nanos[1] / 1e6 / 10;
			results.pass(String.format(Locale.ROOT, "smooth water: rebuilding open water's chunks (48 across, 2,304 surfaces, ten times each way): beta's "
					+ "lighting %.1f ms a time (%d chunks in all), smooth %.1f ms (%d): %+.2f microseconds a surface", b, chunks[0], m, chunks[1],
					(m - b) * 1000 / 2304));
			return true;
		});
	}

	private static final String CHANGED = "changed", SAME = "the same", EITHER = "either";

	private void compare() {
		// Name, the two pictures, and what's expected with the change in; without it, every pair is the same picture.
		List<String[]> pairs = new ArrayList<>();
		for (Scene s : scenes) {
			pairs.add(new String[] {s.name + ", Shaded against Beta" + (s.water ? "" : " (lava, no water)"), s.name + "-beta", s.name + "-shaded",
					s.water ? CHANGED : SAME});
			if (!s.water) continue;
			// By the wall at noon only the wall varies the light, so Even is beta's water there, and Shaded's darkening is what
			// differs. (The shore's bank hides the water's row beside it from this camera, so there they come out alike.)
			boolean wall = s.name.equals("wall");
			pairs.add(new String[] {s.name + ", Even against Beta", s.name + "-beta", s.name + "-even", wall ? SAME : EITHER});
			pairs.add(new String[] {s.name + ", Even against Shaded", s.name + "-shaded", s.name + "-even", wall ? CHANGED : EITHER});
		}
		pairs.add(new String[] {"torch in Fast graphics, Shaded against Beta", "torch-fast-beta", "torch-fast-shaded", CHANGED});
		for (String[] pair : pairs) {
			String name = pair[0];
			BufferedImage a = taken.get(pair[1]), b = taken.get(pair[2]);
			if (a == null || b == null) {
				results.check("smooth water: " + name + ": both pictures taken", false);
				continue;
			}
			int differ = 0, by4 = 0;
			for (int y = 0; y < a.getHeight(); y++) {
				for (int x = 0; x < a.getWidth(); x++) {
					int d = diff(a.getRGB(x, y), b.getRGB(x, y));
					if (d > 0) differ++;
					if (d > 4) by4++;
				}
			}
			int total = a.getWidth() * a.getHeight();
			String expect = on ? pair[3] : SAME;
			String said = String.format(Locale.ROOT, "smooth water: %s, %s: %d of %d pixels differ, %d by more than 4/255", name,
					on ? "the change in" : "the change out", differ, total, by4);
			if (expect.equals(EITHER)) results.pass(said);
			else results.check(said + " (expected " + expect + ")", expect.equals(CHANGED) ? by4 > total / 500 : differ == 0);
		}
		results.check("smooth water: every choice took " + (on ? "at once" : "at once for Beta, after a restart for Shaded and Even")
				+ (mistimed.isEmpty() ? "" : ": " + mistimed), mistimed.isEmpty());
		if (on) {
			long fallback = DuskLight.verticesFallback - fallbackBefore;
			results.check("smooth water: smooth dusk took every vertex apart (" + fallback + " fell back to beta's baking)", fallback == 0);
		}
	}

	private static int diff(int a, int b) {
		int m = 0;
		for (int s = 0; s < 24; s += 8) m = Math.max(m, Math.abs((a >> s & 255) - (b >> s & 255)));
		return m;
	}
}
