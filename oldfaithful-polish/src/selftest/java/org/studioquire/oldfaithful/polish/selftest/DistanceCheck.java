package org.studioquire.oldfaithful.polish.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.selftest.ScreenCapture;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor;

/**
 * Particle Distance and Entity Distance ({@code render.particleDistance}, {@code render.entityDistance}). First the
 * rules at their edges: whether an item, an arrow, a chicken, a zombie and a spider would be drawn just inside and
 * just outside beta's distances and 64, and whether a puff and a smoke particle are made at 15, 17, 31, 33, 63 and 65
 * blocks. Then in pictures, over a grey platform above the clouds, the camera 12 above it looking down 40 degrees with
 * nothing but the platform in the middle of the picture: apples dropped 10, 22, 36 and 50 blocks out (15.6 to 51.4 from
 * the eye; beta draws only the first), and an Explosive Arrow's blast (power 1.5) at the same places, each picture
 * against one of the empty platform: the middle band's pixels that changed. Every switch's way is checked from the
 * switch as the game started. Last, what drawing them costs: the game thread's time in drawing things and in drawing
 * and moving particles, with a pile of 400 apples 30 to 50 blocks out and for 20 ticks after four TNT-sized blasts 40 to
 * 52 out (beta's cap of 4000 particles reached),
 * against the same moments with the platform empty (logged, not checked: a hidden window on a shared PC).
 * Pictures in local/run/distance/ ("-polished", "-beta").
 */
public final class DistanceCheck implements WorldCheck {
	private static final String ENTITIES = "render.entityDistance", PARTICLES = "render.particleDistance";
	private static final File OUT = new File("../distance");
	private static final int Y = 118, HALF = 20, LENGTH = 140, EYE = 12;
	private static final float PITCH = 40;
	/** How far out each apple cluster and blast is from the camera, along the platform. */
	private static final int[] OUT_AT = {10, 22, 36, 50};
	/** The middle of the picture, where there's nothing but platform: half its width, in pixels. */
	private static final int BAND = 50;

	/** For DistanceTimingMixin: the game thread's time in each, while {@link #timing}. */
	public static volatile boolean timing;
	public static long entityNs, entityCalls, particleRenderNs, particleRenderCalls, particleTickNs, particleTicks;

	private interface Step {
		boolean tick(Minecraft mc);
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private SelfTest results;
	private World world;
	private boolean entitiesOn, particlesOn;
	private int cx, z0;
	private Object[] saved;
	private BufferedImage empty;
	private final List<Entity> placed = new ArrayList<>();

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		entitiesOn = PolishConfig.applied(ENTITIES);
		particlesOn = PolishConfig.applied(PARTICLES);
		saved = new Object[] {mc.options.hideHud, mc.options.bobView, mc.options.viewDistance, mc.player.x, mc.player.y - mc.player.standingEyeHeight, mc.player.z};
		cx = MathHelper.floor(mc.player.x) - 120;
		z0 = MathHelper.floor(mc.player.z) - 70;
		mc.options.hideHud = true;
		mc.options.bobView = false;
		// Normal at least: the test's Tiny fogs everything past 32 blocks out.
		if (mc.options.viewDistance > 1) mc.options.viewDistance = 1;
		world.getProperties().setRaining(false);
		world.getProperties().setThundering(false);
		world.getProperties().setRainTime(1_000_000);
		world.getProperties().setThunderTime(1_000_000);
		world.setRainGradient(0.0F);
		for (int x = cx - HALF; x <= cx + HALF; x++) {
			for (int z = z0; z < z0 + LENGTH; z++) {
				// Grey wool: white puffs, dark smoke and red apples all stand out on it.
				world.setBlock(x, Y, z, Block.WOOL.id, 7);
				for (int y = Y + 1; y <= Y + EYE + 2; y++) {
					if (world.getBlockId(x, y, z) != 0) world.setBlock(x, y, z, 0);
				}
			}
		}
		entityRules();
		particleRules(mc);
		plan.add(settle());
		plan.add(wait(10));
		shoot("empty", img -> empty = img);
		for (int d : OUT_AT) {
			plan.add(c -> {
				apples(d, 1, 3);
				return true;
			});
			plan.add(wait(15));
			shoot("apples-" + d, img -> seen("apples", d, img, entitiesOn ? true : d == OUT_AT[0]));
			plan.add(clear());
		}
		plan.add(c -> {
			for (int d : OUT_AT) apples(d, 1, 3);
			return true;
		});
		plan.add(wait(15));
		shoot("apples", null);
		plan.add(clear());
		for (int d : OUT_AT) {
			int[] made = {0};
			plan.add(c -> {
				made[0] = blast(c, d);
				return true;
			});
			plan.add(wait(4));
			shoot("blast-" + d, img -> {
				seen("a blast", d, img, particlesOn || d == OUT_AT[0]);
				blastCount(d, made[0]);
			});
			plan.add(clear());
		}
		plan.add(c -> {
			for (int d : OUT_AT) blast(c, d);
			return true;
		});
		plan.add(wait(4));
		shoot("blasts", null);
		plan.add(clear());
		cost();
	}

	@Override
	public boolean tick(Minecraft mc) {
		PlayerEntity p = mc.player;
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.setPositionAndAnglesKeepPrevAngles(cx + 0.5, Y + 1 + EYE - p.standingEyeHeight, z0 + 0.5, 0, PITCH);
		p.prevYaw = p.yaw;
		p.prevPitch = p.pitch;
		world.setTime(6000);
		world.setRainGradient(0.0F);
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
		}
		timing = false;
		mc.particleManager.setWorld(world);
		mc.options.hideHud = (Boolean) saved[0];
		mc.options.bobView = (Boolean) saved[1];
		mc.options.viewDistance = (Integer) saved[2];
		p.noClip = false;
		p.setPositionAndAnglesKeepPrevAngles((Double) saved[3], (Double) saved[4], (Double) saved[5], 0, 0);
		return true;
	}

	private String side(boolean on) {
		return on ? "polished" : "beta";
	}

	// ---- The rules at their edges

	/** Beta's distance by box (item 16, arrow 32, chicken 21.3, zombie 64, spider 78.9), or 64 at least. */
	private void entityRules() {
		Object[][] things = {
				{"a dropped item", new ItemEntity(world, 0, 0, 0, new ItemStack(Item.APPLE)), 16.0, new double[] {15, 17, 63, 65}},
				{"an arrow", new ArrowEntity(world, 0, 0, 0), 32.0, new double[] {31, 33, 63, 65}},
				{"a chicken", new ChickenEntity(world), 64.0 / 3, new double[] {21, 22, 63, 65}},
				{"a zombie", new ZombieEntity(world), 64.0, new double[] {63, 65}},
				{"a spider", new SpiderEntity(world), 64.0 * 3.7 / 3, new double[] {70, 80}},
		};
		for (Object[] t : things) {
			Entity e = (Entity) t[1];
			e.setPosition(0, 64, 0);
			double beta = (Double) t[2];
			List<String> got = new ArrayList<>(), want = new ArrayList<>();
			for (double d : (double[]) t[3]) {
				boolean drawn = d < beta || entitiesOn && d < 64;
				got.add(fmt(d) + (e.shouldRender(d * d) ? " drawn" : " not"));
				want.add(fmt(d) + (drawn ? " drawn" : " not"));
			}
			results.check("entity distance (" + side(entitiesOn) + "): " + t[0] + " at " + got + " (" + want + ")", got.equals(want));
		}
	}

	/** A puff and a smoke particle offered to the renderer straight above the camera at each distance: made or not. */
	private void particleRules(Minecraft mc) {
		LivingEntity cam = mc.camera;
		String[][] kinds = {{"explode", "64"}, {"smoke", "32"}};
		for (String[] k : kinds) {
			double far = particlesOn ? Double.parseDouble(k[1]) : 16;
			List<String> got = new ArrayList<>(), want = new ArrayList<>();
			for (double d : new double[] {15, 17, 31, 33, 63, 65}) {
				mc.particleManager.setWorld(world);
				int before = particles(mc);
				mc.worldRenderer.addParticle(k[0], cam.x, cam.y + d, cam.z, 0, 0, 0);
				got.add(fmt(d) + (particles(mc) > before ? " made" : " not"));
				want.add(fmt(d) + (d < far ? " made" : " not"));
			}
			mc.particleManager.setWorld(world);
			results.check("particle distance (" + side(particlesOn) + "): \"" + k[0] + "\" at " + got + " (" + want + ")", got.equals(want));
		}
	}

	private static int particles(Minecraft mc) {
		return Integer.parseInt(mc.particleManager.toString());
	}

	private static String fmt(double d) {
		return String.format(Locale.ROOT, "%.0f", d);
	}

	// ---- The scene

	/** n by n apples, a block apart, d out from the camera along the platform; spacing 1. */
	private void apples(int d, int spacing, int n) {
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				ItemEntity apple = new ItemEntity(world, cx + 0.5 + (i - n / 2) * spacing, Y + 1.2, z0 + 0.5 + d + (j - n / 2) * spacing, new ItemStack(Item.APPLE));
				apple.velocityX = apple.velocityY = apple.velocityZ = 0;
				world.spawnEntity(apple);
				placed.add(apple);
			}
		}
	}

	/** An Explosive Arrow's blast (power 1.5) 3.5 above the platform, d out: it reaches 2.4 blocks, so no block. */
	private int blast(Minecraft mc, int d) {
		int before = particles(mc);
		Explosion e = world.createExplosion(null, cx + 0.5, Y + 4.5, z0 + 0.5 + d, 1.5F);
		places = e.damagedBlocks.size();
		return particles(mc) - before;
	}

	private int places;

	/**
	 * Every place the blast reached makes a puff within 1.7 blocks of its middle and a smoke particle within 3.4. With the
	 * switch: both 13.8 and 24 out, only the puffs 37.2 and 50.9 out (smoke's 32 is passed). Beta: within 16 only, so the
	 * nearest makes every puff and most of the smoke, the others nothing.
	 */
	private void blastCount(int d, int made) {
		double out = Math.hypot(d, EYE + 1 - 4.5);
		boolean near = d == OUT_AT[0];
		String seen = "particle distance (" + side(particlesOn) + "): a blast " + fmt(out) + " out reaching " + places + " places made " + made + " particles";
		if (particlesOn) {
			int expected = d <= 22 ? 2 * places : places;
			results.check(seen + " (" + expected + ": " + (d <= 22 ? "a puff and smoke" : "a puff") + " a place)", places > 10 && made == expected);
		} else {
			results.check(seen + " (" + (near ? "beta: every puff and most smoke" : "beta: none") + ")", near ? places > 10 && made >= places && made <= 2 * places : made == 0);
		}
	}

	private Step clear() {
		return c -> {
			for (Entity e : placed) e.markDead();
			placed.clear();
			c.particleManager.setWorld(world);
			return true;
		};
	}

	private Step wait(int ticks) {
		int[] left = {ticks};
		return c -> --left[0] <= 0;
	}

	/** Until no render chunk under the camera's view of the platform waits (a minute at most). */
	private Step settle() {
		int[] waited = {0}, quiet = {0};
		return mc -> {
			boolean built = true;
			for (ChunkBuilder c : ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$chunks()) {
				if (c.dirty && c.x + 16 > cx - HALF && c.x < cx + HALF && c.z + 16 > z0 && c.z < z0 + LENGTH && c.y + 16 > Y - 16 && c.y < Y + 16) built = false;
			}
			quiet[0] = built ? quiet[0] + 1 : 0;
			return quiet[0] >= 20 || ++waited[0] > 1200;
		};
	}

	private interface Seen {
		void picture(BufferedImage img);
	}

	private void shoot(String name, Seen seen) {
		File file = new File(OUT, name + "-" + (name.startsWith("blast") ? side(particlesOn) : side(entitiesOn)) + ".png");
		ScreenCapture[] capture = {null};
		plan.add(c -> {
			if (capture[0] == null) capture[0] = ScreenCapture.request(file, -1, -1);
			if (!capture[0].done()) return false;
			if (seen != null) {
				try {
					seen.picture(ImageIO.read(file));
				} catch (IOException e) {
					results.fail("distance: reading " + file, e);
				}
			}
			return true;
		});
	}

	/** The middle band's pixels that differ from the empty platform's picture by more than a little. */
	private void seen(String what, int d, BufferedImage img, boolean drawn) {
		int changed = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = img.getWidth() / 2 - BAND; x < img.getWidth() / 2 + BAND; x++) {
				int a = img.getRGB(x, y), b = empty.getRGB(x, y);
				int diff = Math.max(Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)), Math.max(Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
				if (diff > 24) changed++;
			}
		}
		boolean apples = what.equals("apples");
		double out = apples ? Math.hypot(d, EYE) : Math.hypot(d, EYE + 1 - 4.5);
		results.check("distance (" + side(apples ? entitiesOn : particlesOn) + "): " + what + " " + fmt(out) + " out changed " + changed
				+ " pixels of the picture (" + (drawn ? "drawn: 20 or more" : "not drawn: none") + ")", drawn ? changed >= 20 : changed == 0);
	}

	// ---- What it costs

	private void cost() {
		long[] base = new long[6];
		plan.add(c -> {
			timing = true;
			entityNs = entityCalls = particleRenderNs = particleRenderCalls = particleTickNs = particleTicks = 0;
			return true;
		});
		plan.add(wait(120));
		plan.add(c -> {
			base[0] = entityNs;
			base[1] = entityCalls;
			base[2] = particleRenderNs;
			base[3] = particleRenderCalls;
			base[4] = particleTickNs;
			base[5] = particleTicks;
			entityNs = entityCalls = particleRenderNs = particleRenderCalls = particleTickNs = particleTicks = 0;
			for (int i = 0; i < 20; i++) {
				for (int j = 0; j < 20; j++) {
					ItemEntity apple = new ItemEntity(world, cx + 0.5 + i - 10, Y + 1.2, z0 + 30.5 + j, new ItemStack(Item.APPLE));
					apple.velocityX = apple.velocityY = apple.velocityZ = 0;
					world.spawnEntity(apple);
					placed.add(apple);
				}
			}
			return true;
		});
		plan.add(wait(120));
		int[] made = {0};
		plan.add(c -> {
			log(String.format(Locale.ROOT, "%s: drawing things %.2f ms a frame with 400 apples 30 to 50 blocks out, %.2f ms with the platform empty (%d and %d frames)",
					side(entitiesOn), ms(entityNs, entityCalls), ms(base[0], base[1]), entityCalls, base[1]));
			for (Entity e : placed) e.markDead();
			placed.clear();
			entityNs = entityCalls = particleRenderNs = particleRenderCalls = particleTickNs = particleTicks = 0;
			for (int i = 0; i < 4; i++) {
				int before = particles(c);
				world.createExplosion(null, cx + 0.5 + (i % 2 == 0 ? -6 : 6), Y + 8.5, z0 + 40.5 + 4 * i, 4.0F);
				made[0] += particles(c) - before;
			}
			return true;
		});
		plan.add(wait(20));
		plan.add(c -> {
			log(String.format(Locale.ROOT, "%s: four TNT-sized blasts 40 to 52 blocks out made %d particles; over the next 20 ticks drawing them took %.2f ms a frame"
					+ " and moving them %.2f ms a tick, against %.2f and %.2f with none", side(particlesOn), made[0], ms(particleRenderNs, particleRenderCalls),
					ms(particleTickNs, particleTicks), ms(base[2], base[3]), ms(base[4], base[5])));
			timing = false;
			return true;
		});
	}

	private static double ms(long ns, long n) {
		return n == 0 ? 0 : ns / 1e6 / n;
	}

	private static void log(String s) {
		System.out.println("[oldfaithful-selftest] (distance) " + s);
	}
}
