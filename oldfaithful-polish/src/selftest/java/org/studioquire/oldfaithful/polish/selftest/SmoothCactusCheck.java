package org.studioquire.oldfaithful.polish.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.DuskLight;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.SmoothCactus;
import org.studioquire.oldfaithful.selftest.ScreenCapture;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor;

/**
 * Smooth cactus ({@code render.smoothCactus}): cactus one to three high on a sand island, one with a sand mound off its
 * corner, pictured with Smooth Lighting on at noon, and again at midnight with torches off two of them, each lit as beta
 * lit it (SmoothCactus.betaForTest) and as the change lights it, the scene's chunks rebuilt between. With the switch on
 * the pictures differ, with it off (-Pstill.beta) they're the same (none of the change is in). Smooth dusk takes every
 * vertex apart (none falls back). Pictures in local/run/smooth-cactus/ (<scene>-beta.png, <scene>-polished.png).
 */
public final class SmoothCactusCheck implements WorldCheck {
	private static final String KEY = "render.smoothCactus";
	private static final File OUT = new File("../smooth-cactus");
	/** The islands' floor is at G - 1. */
	private static final int G = 100;
	private static final int SPACING = 40, SCENES = 2;
	private static final int STONE = 1, SAND = 12, TORCH = 50, CACTUS = 81;

	private interface Step {
		boolean tick(Minecraft mc);
	}

	private static final class Scene {
		final String name;
		final int index;
		final long time;
		final double[] eye, at;

		Scene(String name, int index, long time, double[] eye, double[] at) {
			this.name = name;
			this.index = index;
			this.time = time;
			this.eye = eye;
			this.at = at;
		}
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private final List<Scene> scenes = new ArrayList<>();
	private final Map<String, BufferedImage> taken = new HashMap<>();
	private SelfTest results;
	private World world;
	private int bx, bz;
	private Scene current;
	private Object[] saved;
	private long fallbackBefore;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
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
		// Away from the other checks' scenes (SmoothWaterCheck's islands end at z + 176, DuskCheck's furthest at about 224).
		bx = (int) Math.floor(mc.player.x) - 80;
		bz = (int) Math.floor(mc.player.z) + 260;
		build();
		fallbackBefore = DuskLight.verticesFallback;
		SmoothCactus.betaForTest = false;
		// Held still from the start: a torch flame made while the scenes settled was still burning in the first picture
		// and gone from the second (seen 2026-11-14, with the change off).
		DuskCheck.frozen = true;
		current = scenes.get(0);
		plan.add(c -> {
			c.worldRenderer.reload();
			return true;
		});
		plan.add(settle(200));
		for (Scene s : scenes) {
			shoot(s, "beta", true);
			shoot(s, "polished", false);
		}
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
			if (e.x > bx - 24 && e.x < bx + SCENES * SPACING + 24 && Math.abs(e.z - bz) < 32) e.markDead();
		}
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
		}
		DuskCheck.frozen = false;
		SmoothCactus.betaForTest = false;
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

	/** Cactus on a sand island 21 across at (0, 0), (-3, 2), (3, 1) and (2, -3), three, two, one and two high, the last with a sand mound off its corner. */
	private void cacti() {
		fill(-12, -6, -12, 12, 14, 12, 0);
		fill(-10, -3, -10, 10, -2, 10, STONE);
		fill(-10, -1, -10, 10, -1, 10, SAND);
		fill(0, 0, 0, 0, 2, 0, CACTUS);
		fill(-3, 0, 2, -3, 1, 2, CACTUS);
		set(3, 0, 1, CACTUS);
		fill(2, 0, -3, 2, 1, -3, CACTUS);
		fill(3, 0, -4, 4, 1, -5, SAND);
	}

	private void build() {
		for (int x = bx - 32; x <= bx + SCENES * SPACING + 32; x += 16) {
			for (int z = bz - 32; z <= bz + 32; z += 16) world.getBlockId(x, 64, z);
		}
		sx = 0;
		cacti();
		scenes.add(new Scene("cactus-day", 0, 6000, new double[] {5.0, 2.2, 6.0}, new double[] {0.0, 1.0, -0.5}));
		// The same at midnight, with torches on the sand off two of them.
		sx = SPACING;
		cacti();
		set(1, 0, 1, TORCH, 5);
		set(-2, 0, 3, TORCH, 5);
		scenes.add(new Scene("cactus-torch", 1, 18000, new double[] {5.0, 2.2, 6.0}, new double[] {0.0, 1.0, -0.5}));
	}

	// ---- Steps

	/** No render chunk of the scenes waits to be built. */
	private boolean built(Minecraft mc) {
		for (ChunkBuilder c : ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$chunks()) {
			if (!c.dirty) continue;
			if (c.x + 16 > bx - 16 && c.x < bx + SCENES * SPACING + 16 && c.z + 16 > bz - 24 && c.z < bz + 24 && c.y + 16 > G - 8 && c.y < G + 16) return false;
		}
		return true;
	}

	/** No light update waits, worked here some milliseconds a tick (DuskCheck's lit). */
	private static boolean lit(Minecraft mc) {
		long until = System.nanoTime() + 20_000_000L;
		boolean more;
		do {
			more = mc.world.doLightingUpdates();
		} while (more && System.nanoTime() < until);
		return !more;
	}

	/** At least {@code ticks}, then until the scenes are lit and built. */
	private Step settle(int ticks) {
		int[] waited = {0}, quiet = {0};
		return mc -> {
			waited[0]++;
			quiet[0] = lit(mc) && built(mc) ? quiet[0] + 1 : 0;
			return waited[0] >= ticks && quiet[0] >= 20 || waited[0] > 1500;
		};
	}

	/** The scene lit one way, its chunks rebuilt, then a picture once it's still. */
	private void shoot(Scene s, String look, boolean beta) {
		int[] waited = {0}, quiet = {0};
		plan.add(mc -> {
			if (waited[0]++ == 0) {
				current = s;
				DuskCheck.frozen = true;
				SmoothCactus.betaForTest = beta;
				int x0 = bx + s.index * SPACING;
				world.setBlocksDirty(x0 - 12, G - 8, bz - 12, x0 + 12, G + 16, bz + 12);
				return false;
			}
			quiet[0] = lit(mc) && built(mc) ? quiet[0] + 1 : 0;
			if (quiet[0] >= 8) return true;
			if (waited[0] > 600) {
				results.check("smooth cactus: " + s.name + ", " + look + ": built", false);
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
				results.fail("smooth cactus: reading " + file, e);
			}
			return true;
		});
	}

	private void compare() {
		boolean on = PolishConfig.applied(KEY);
		for (Scene s : scenes) {
			BufferedImage a = taken.get(s.name + "-beta"), b = taken.get(s.name + "-polished");
			if (a == null || b == null) {
				results.check("smooth cactus: " + s.name + ": both pictures taken", false);
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
			boolean expected = on ? by4 > total / 500 : differ == 0;
			results.check(String.format(Locale.ROOT, "smooth cactus: %s, %s %s: the picture against beta's lighting: %d of %d pixels differ, %d by more than 4/255 (expected %s)",
					s.name, KEY, on ? "on" : "off", differ, total, by4, on ? "changed" : "the same"), expected);
		}
		if (on) {
			long fallback = DuskLight.verticesFallback - fallbackBefore;
			results.check("smooth cactus: smooth dusk took every vertex apart (" + fallback + " fell back to beta's baking)", fallback == 0);
		}
	}

	private static int diff(int a, int b) {
		int m = 0;
		for (int s = 0; s < 24; s += 8) m = Math.max(m, Math.abs((a >> s & 255) - (b >> s & 255)));
		return m;
	}
}
