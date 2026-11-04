package org.studioquire.oldfaithful.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Pictures of the real game for the site and listings (`:harness:sceneShot`, docs/release.md), taken in the start-up
 * test's hidden window: with {@code -Doldfaithful.selftest.scene=<dir>}, the test's world (on
 * {@code oldfaithful.selftest.sceneSeed}) is searched around the spawn for lava lying in a cave, the kind beta's
 * world generation leaves unlit and Old Faithful's lighting mod lights. For the best few, the player is held in the
 * cave's air a few blocks off, looking at the lava, with the HUD and hand hidden, monsters away (peaceful); once the
 * view has built, the frame is read back and saved as {@code lava-<n>.png}, with where it was in {@code lava-<n>.txt}.
 * Nothing is built or placed: the scene is the world as generated.
 *
 * <p>With {@code -Doldfaithful.selftest.sceneViews="x y z yaw pitch time;..."} instead: landscapes from given places,
 * no search. For each, the player is held with the eye at x+0.5, y, z+0.5 (x and z a block, y the eye's height),
 * facing yaw and pitch, the time of day held at {@code time}, at the Far render distance; after
 * {@code oldfaithful.selftest.sceneViewTicks} (default 900, for the far view to fill in) the frame is saved as
 * {@code view-<n>.png}, with the view in {@code view-<n>.txt}. Optionally after the time: the render distance (0 Far,
 * the default, to 3 Tiny), then {@code rain} for weather.
 *
 * <p>With {@code -Doldfaithful.selftest.sceneSurvey="x0 z0 x1 z1"}: no pictures, a map of the world's surface over
 * that area for choosing views ({@link Survey}).
 */
public final class SceneShot {
	public static final String OUT = System.getProperty("oldfaithful.selftest.scene");
	public static final boolean ENABLED = OUT != null;
	public static final long SEED = Long.getLong("oldfaithful.selftest.sceneSeed", 7373L);
	private static final int RADIUS = Integer.getInteger("oldfaithful.selftest.sceneRadius", 96);
	private static final int SHOTS = Integer.getInteger("oldfaithful.selftest.sceneShots", 6);
	private static final String VIEWS = given("oldfaithful.selftest.sceneViews");
	private static final String SURVEY = given("oldfaithful.selftest.sceneSurvey");
	private Survey survey;
	private SeamCheck seams;
	private static final int VIEW_TICKS = Integer.getInteger("oldfaithful.selftest.sceneViewTicks", 900);
	private static final int SETTLE_TICKS = 100, HOLD_TICKS = 200;
	private static final int LAVA = Block.LAVA.id, FLOWING_LAVA = Block.FLOWING_LAVA.id;

	/** Set by a tick, taken by the next frame's end (GameRendererSceneMixin). */
	private static volatile File pending;
	private static volatile boolean taken;

	private final SelfTest results;
	private final List<Site> sites = new ArrayList<>();
	private int ticks;
	private int site = -1;
	private int held;

	private static final class Site {
		final double tx, ty, tz;
		final int cx, cy, cz;
		final int lava, open;

		Site(double tx, double ty, double tz, int cx, int cy, int cz, int lava, int open) {
			this.tx = tx; this.ty = ty; this.tz = tz;
			this.cx = cx; this.cy = cy; this.cz = cz;
			this.lava = lava; this.open = open;
		}

		/** A roomy cave with a pool that lights it without filling the view. */
		int score() {
			return open - Math.abs(lava - 40) * 4;
		}
	}

	private static String given(String property) {
		String v = System.getProperty(property);
		return v == null || v.trim().isEmpty() ? null : v;
	}

	SceneShot(SelfTest results) {
		this.results = results;
	}

	/** One game tick; true when every shot is taken. */
	boolean tick(Minecraft mc) {
		mc.options.hideHud = true;
		mc.options.difficulty = 0;
		ticks++;
		if (SeamCheck.ENABLED) return (seams == null ? seams = new SeamCheck(OUT, results) : seams).tick(mc);
		if (SURVEY != null) return survey == null ? (survey = new Survey(SURVEY, OUT)).tick(mc, results) : survey.tick(mc, results);
		if (VIEWS != null) return view(mc);
		if (site < 0) {
			if (ticks < SETTLE_TICKS) return false;
			search(mc.world, (int) Math.floor(mc.player.x), (int) Math.floor(mc.player.z));
			results.check("scene: lava lying in caves found near the spawn (" + sites.size() + " places)", !sites.isEmpty());
			if (sites.isEmpty()) return true;
			site = 0;
			held = 0;
		}
		if (site >= sites.size() || site >= SHOTS) return true;
		Site s = sites.get(site);
		hold(mc.player, s);
		held++;
		if (held == HOLD_TICKS) {
			taken = false;
			pending = new File(OUT, "lava-" + site + ".png");
		} else if (held > HOLD_TICKS && taken) {
			results.pass(String.format(Locale.ROOT, "scene: lava-%d.png, from %d,%d,%d at the lava around %.1f,%.1f,%.1f (%d cells, %d open)",
					site, s.cx, s.cy, s.cz, s.tx, s.ty, s.tz, s.lava, s.open));
			try {
				java.nio.file.Files.write(new File(OUT, "lava-" + site + ".txt").toPath(), String.format(Locale.ROOT,
						"seed %d%ncamera %d %d %d%nlooking at %.2f %.2f %.2f%nlava cells %d, open cells %d%n",
						SEED, s.cx, s.cy, s.cz, s.tx, s.ty, s.tz, s.lava, s.open).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
			site++;
			held = 0;
		}
		return false;
	}

	private int view;

	/** Landscape mode: the views in VIEWS one after another. */
	private boolean view(Minecraft mc) {
		String[] views = VIEWS.split(";");
		if (view >= views.length) return true;
		String[] v = views[view].trim().split("\\s+");
		double x = Integer.parseInt(v[0]) + 0.5, y = Double.parseDouble(v[1]), z = Integer.parseInt(v[2]) + 0.5;
		float yaw = Float.parseFloat(v[3]), pitch = Float.parseFloat(v[4]);
		long time = Long.parseLong(v[5]);
		mc.options.viewDistance = v.length > 6 ? Integer.parseInt(v[6]) : 0;
		boolean rain = v.length > 7 && v[7].equals("rain");
		mc.world.setTime(time);
		ClientPlayerEntity p = mc.player;
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.setPositionAndAngles(x, y, z, yaw, pitch);
		held++;
		mc.world.getProperties().setRaining(rain);
		mc.world.getProperties().setRainTime(Integer.MAX_VALUE / 2);
		if (held == VIEW_TICKS) {
			taken = false;
			pending = new File(OUT, "view-" + view + ".png");
		} else if (held > VIEW_TICKS && taken) {
			String where = String.format(Locale.ROOT, "seed %d, camera at %d %.2f %d, yaw %.0f, pitch %.0f, time %d, render distance %d%s",
					SEED, (int) Math.floor(x), y, (int) Math.floor(z), yaw, pitch, time, mc.options.viewDistance, rain ? ", raining" : "");
			results.pass("scene: view-" + view + ".png, " + where);
			try {
				java.nio.file.Files.write(new File(OUT, "view-" + view + ".txt").toPath(), (where + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
			view++;
			held = 0;
		}
		return false;
	}

	/** The player in the cave's air at the site, looking at the lava, kept from falling, burning or drowning. */
	private static void hold(ClientPlayerEntity p, Site s) {
		double eyeX = s.cx + 0.5, eyeY = s.cy + 1.62, eyeZ = s.cz + 0.5;
		double dx = s.tx - eyeX, dy = s.ty - eyeY, dz = s.tz - eyeZ;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.setPositionAndAngles(eyeX, eyeY, eyeZ, yaw, pitch);
	}

	/**
	 * Lava with air above it, below sea level and out of the sky's light, grouped into pools; for each pool, a place
	 * to look from: two blocks of air 7 to 12 blocks off and 2 to 5 above, out of the sky's light, with nothing between
	 * it and the pool's middle. Pools ranked by size and how open the cave around them is.
	 */
	private void search(World w, int px, int pz) {
		Set<Long> seen = new HashSet<>();
		List<Site> found = new ArrayList<>();
		for (int x = px - RADIUS; x <= px + RADIUS; x++) {
			for (int z = pz - RADIUS; z <= pz + RADIUS; z++) {
				for (int y = 4; y < 48; y++) {
					if (!surface(w, x, y, z) || !seen.add(key(x, y, z))) continue;
					// The pool: lava surface cells joined side by side at this height.
					List<int[]> pool = new ArrayList<>();
					ArrayDeque<int[]> queue = new ArrayDeque<>();
					queue.add(new int[] {x, z});
					while (!queue.isEmpty() && pool.size() < 400) {
						int[] c = queue.poll();
						pool.add(c);
						int[][] next = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
						for (int[] n : next) {
							if (Math.abs(n[0] - px) > RADIUS + 16 || Math.abs(n[1] - pz) > RADIUS + 16) continue;
							if (surface(w, n[0], y, n[1]) && seen.add(key(n[0], y, n[1]))) queue.add(n);
						}
					}
					if (pool.size() < 10) continue;
					double sx = 0, sz = 0;
					for (int[] c : pool) { sx += c[0]; sz += c[1]; }
					double tx = sx / pool.size() + 0.5, tz = sz / pool.size() + 0.5, ty = y + 0.9;
					Site best = null;
					for (int d = 7; d <= 12; d++) {
						for (int a = 0; a < 16; a++) {
							for (int h = 2; h <= 5; h++) {
								int cx = (int) Math.floor(tx + d * Math.cos(a * Math.PI / 8)), cz = (int) Math.floor(tz + d * Math.sin(a * Math.PI / 8));
								int cy = y + h;
								if (w.getBlockId(cx, cy, cz) != 0 || w.getBlockId(cx, cy + 1, cz) != 0) continue;
								if (w.getBrightness(LightType.SKY, cx, cy + 1, cz) > 0) continue;
								if (!clear(w, cx + 0.5, cy + 1.62, cz + 0.5, tx, ty, tz)) continue;
								int open = open(w, cx, cy, cz);
								Site s = new Site(tx, ty, tz, cx, cy, cz, pool.size(), open);
								if (best == null || s.open > best.open) best = s;
							}
						}
					}
					if (best != null) found.add(best);
				}
			}
		}
		found.sort((a, b) -> Integer.compare(b.score(), a.score()));
		sites.addAll(found);
	}

	private static boolean surface(World w, int x, int y, int z) {
		int id = w.getBlockId(x, y, z);
		return (id == LAVA || id == FLOWING_LAVA) && w.getBlockId(x, y + 1, z) == 0 && w.getBrightness(LightType.SKY, x, y + 1, z) == 0;
	}

	/** Nothing but air on the line from the eye to just above the lava. */
	private static boolean clear(World w, double x0, double y0, double z0, double x1, double y1, double z1) {
		double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		for (double t = 0; t < len - 0.6; t += 0.2) {
			double f = t / len;
			if (w.getBlockId((int) Math.floor(x0 + (x1 - x0) * f), (int) Math.floor(y0 + (y1 - y0) * f), (int) Math.floor(z0 + (z1 - z0) * f)) != 0) {
				return false;
			}
		}
		return true;
	}

	/** Air in a box around the eye: a roomy cave reads better than a crack. */
	private static int open(World w, int x, int y, int z) {
		int n = 0;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = -2; dy <= 4; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					if (w.getBlockId(x + dx, y + dy, z + dz) == 0) n++;
				}
			}
		}
		return n;
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
	}

	/** At the end of a frame (the world drawn, the HUD hidden, before the buffers swap): the asked-for shot. */
	public static void frame(Minecraft mc) {
		File file = pending;
		if (file == null) return;
		pending = null;
		int w = mc.displayWidth, h = mc.displayHeight;
		ByteBuffer px = BufferUtils.createByteBuffer(w * h * 3);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadBuffer(GL11.GL_BACK);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, px);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = ((h - 1 - y) * w + x) * 3;
				img.setRGB(x, y, (px.get(i) & 0xff) << 16 | (px.get(i + 1) & 0xff) << 8 | (px.get(i + 2) & 0xff));
			}
		}
		try {
			file.getParentFile().mkdirs();
			ImageIO.write(img, "png", file);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		taken = true;
	}
}
