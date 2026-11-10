package org.studioquire.oldfaithful.polish.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.Timer;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.Squids;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.TickRate;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.MinecraftTimerAccessor;

/**
 * Squid Surfacing ({@code mobs.squidSurface}): ten squid in a pool 10 by 10 and 3 deep, open to the sky where water doesn't
 * freeze (in a snowy biome its top turns to ice, which is no water to a squid), left to swim as single player runs them.
 * For {@link #WATCH} ticks, measured where a squid's middle is within a block of the
 * surface (as Old Faithful's SquidWatch measures #77 on the real server): how far squid rise and fall a second there,
 * how often they turn between up and down, how often their water test says out (the out-of-water branch, gravity) and
 * their tentacles jump, how much of the body is out; every squid's tick in {@code ticks-<side>.csv}. With the switch,
 * no squid at the top is ever out of the water, and none rises past {@link Squids#DEPTH} under the surface; without
 * it, Old Faithful's bounce: some out-of-water ticks there.
 * <p>
 * Then, with {@code -Pstill.squidClip}, a clip: five seconds from the pool's shore as a player standing there sees it, and
 * five from against its west wall outside, eyes 0.73 over the surface, looking across it, at 60 frames a second: game time
 * slowed to {@link #CLIP_TPS} ticks a second, and each tick drawn three times, at a third, two thirds and the whole of
 * the way from the tick before (SquidClipFrame), into {@code local/run/squid-surface/<side>-shore/} and
 * {@code <side>-low/} (side: polished or beta). The camera stands on a block out of the squid's reach, so nothing moves
 * it.
 */
public final class SquidCheck implements WorldCheck {
	private static final String KEY = "mobs.squidSurface";
	private static final File OUT = new File("../squid-surface");
	/** The pool's floor: high enough to clear most land, low enough that the cameras sit under the clouds (108). */
	private static final int Y = 96, SIDE = 10, DEEP = 3, SQUIDS = 10, WATCH = 1200, CLIP = 100, SETTLE = 12;
	/** {@code -Pstill.squidClip}: the clip too (about 70 s more); otherwise the measurement alone. */
	private static final boolean CLIPS = Boolean.getBoolean("oldfaithful.selftest.squidClip");
	private static final float CLIP_TPS = 3.0F;
	private static final double BODY = 0.95F;

	/** The clip being taken, read by SquidClipFrame each frame; null otherwise. */
	public static volatile Clip clip;

	private SelfTest results;
	private World world;
	private boolean on;
	private int x0, z0, tick;
	private double surface;
	private final List<SquidEntity> squids = new ArrayList<>();
	private Object[] saved;
	private float savedTps;
	private PrintWriter log;

	// Near the surface, over the watch.
	private long nearTicks, outTicks, flips, reversals, twitches;
	private double travel, outSum, outMost, highest = Double.NEGATIVE_INFINITY, heightSum;
	private long heightCount;
	private boolean[] wasWet;
	private double[] lastDy;

	private enum Stage { WATCH, SHORE, LOW, DONE }

	private Stage stage = Stage.WATCH;
	private int stageTicks;
	private double cameraDrift;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(KEY);
		saved = new Object[] {mc.options.hideHud, mc.options.bobView, mc.player.x, mc.player.y - mc.player.standingEyeHeight, mc.player.z, mc.player.noClip};
		mc.options.hideHud = true;
		mc.options.bobView = false;
		world.getProperties().setRaining(false);
		world.getProperties().setThundering(false);
		world.getProperties().setRainTime(1_000_000);
		world.getProperties().setThunderTime(1_000_000);
		world.setRainGradient(0.0F);
		world.setTime(6000);
		int px = MathHelper.floor(mc.player.x), pz = MathHelper.floor(mc.player.z);
		x0 = px + 24;
		z0 = pz - 5;
		// As DuskCheck: nearby first, then further out, 2 km across.
		for (int i = 0; i < 64 && snowy(); i++) {
			x0 = px + 24 + (i % 8) * 64;
			z0 = pz + (i / 8 - 4) * 64;
		}
		for (int i = 0; i < 256 && snowy(); i++) {
			x0 = px - 1024 + (i % 16) * 128;
			z0 = pz - 1024 + (i / 16) * 128;
		}
		results.check("still squid surfacing: a place for the pool where water doesn't freeze, at " + x0 + "," + z0, !snowy());
		surface = Y + DEEP + 1 - 1.0 / 9.0;
		shore(mc.player);
		for (int x = x0 - 48; x <= x0 + SIDE + 48; x += 16) {
			for (int z = z0 - 48; z <= z0 + SIDE + 48; z += 16) world.getBlockId(x, 64, z);
		}
		build();
		Random random = new Random(77);
		for (int i = 0; i < SQUIDS; i++) {
			SquidEntity squid = new SquidEntity(world);
			squid.setPositionAndAnglesKeepPrevAngles(x0 + 1 + random.nextDouble() * (SIDE - 2), Y + 1 + random.nextDouble() * (DEEP - 1),
					z0 + 1 + random.nextDouble() * (SIDE - 2), random.nextFloat() * 360.0F, 0.0F);
			world.spawnEntity(squid);
			squids.add(squid);
		}
		wasWet = new boolean[SQUIDS];
		lastDy = new double[SQUIDS];
		try {
			OUT.mkdirs();
			log = new PrintWriter(new File(OUT, "ticks-" + side() + ".csv"), "UTF-8");
			log.println("tick,squid,middle_minus_surface,dy,in_water,tentacle_angle");
		} catch (IOException e) {
			results.fail("still squid surfacing: opening the tick log", e);
		}
	}

	private boolean snowy() {
		for (int x = x0 - 8; x <= x0 + SIDE + 8; x += 3) {
			for (int z = z0 - 8; z <= z0 + SIDE + 8; z += 3) {
				if (world.method_1781().getBiome(x, z).canSnow()) return true;
			}
		}
		return false;
	}

	private String side() {
		return on ? "polished" : "beta";
	}

	/**
	 * Stone floor and walls; the walls' top is the shore, 0.11 over the water as a lake's; a block against the west wall
	 * outside, its top level with the water's top block, for the low view's camera; air above.
	 */
	private void build() {
		for (int x = x0 - 4; x <= x0 + SIDE + 3; x++) {
			for (int z = z0 - 3; z <= z0 + SIDE + 3; z++) {
				for (int y = Y; y <= Y + DEEP + 8; y++) {
					if (world.getBlockId(x, y, z) != 0) world.setBlock(x, y, z, 0);
				}
			}
		}
		for (int x = x0 - 1; x <= x0 + SIDE; x++) {
			for (int z = z0 - 1; z <= z0 + SIDE; z++) {
				world.setBlock(x, Y, z, Block.STONE.id);
				boolean wall = x == x0 - 1 || x == x0 + SIDE || z == z0 - 1 || z == z0 + SIDE;
				for (int y = Y + 1; y <= Y + DEEP; y++) world.setBlock(x, y, z, wall ? Block.STONE.id : Block.WATER.id);
			}
		}
		world.setBlock(x0 - 2, Y + DEEP - 1, z0 + SIDE / 2, Block.STONE.id);
	}

	/** On the south shore at the middle, facing north, looking down at the water as a player standing there would. */
	private void shore(PlayerEntity p) {
		place(p, x0 + SIDE / 2.0, Y + DEEP + 1, z0 + SIDE + 0.5, 180.0F, 32.0F);
	}

	/** Against the west wall, eyes 0.73 over the surface (0.62 over the wall's top, out of sight below), looking east across it. */
	private void lowView(PlayerEntity p) {
		place(p, x0 - 1.5, Y + DEEP, z0 + SIDE / 2 + 0.5, 270.0F, 3.0F);
	}

	private void place(PlayerEntity p, double x, double feet, double z, float yaw, float pitch) {
		camYaw = yaw;
		camPitch = pitch;
		p.noClip = false;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.setPositionAndAnglesKeepPrevAngles(x, feet, z, yaw, pitch);
		p.prevYaw = yaw;
		p.prevPitch = pitch;
	}

	private float camYaw, camPitch;

	@Override
	public boolean tick(Minecraft mc) {
		PlayerEntity p = mc.player;
		p.yaw = p.prevYaw = camYaw;
		p.pitch = p.prevPitch = camPitch;
		world.setRainGradient(0.0F);
		if (clip != null) cameraDrift = Math.max(cameraDrift, Math.abs(p.y - p.prevY) + Math.abs(p.x - p.prevX) + Math.abs(p.z - p.prevZ));
		stageTicks++;
		switch (stage) {
			case WATCH:
				if (stageTicks > 1) sample();
				if (stageTicks >= WATCH) {
					report();
					next(CLIPS ? Stage.SHORE : Stage.DONE, mc);
				}
				return false;
			case SHORE:
			case LOW:
				if (stageTicks == SETTLE) startClip(mc, stage == Stage.SHORE ? "shore" : "low");
				if (stageTicks >= SETTLE + CLIP) {
					endClip(mc, stage == Stage.SHORE ? "shore" : "low");
					next(stage == Stage.SHORE ? Stage.LOW : Stage.DONE, mc);
				}
				return false;
			default:
				return finish(mc);
		}
	}

	private void next(Stage to, Minecraft mc) {
		stage = to;
		stageTicks = 0;
		if (to == Stage.LOW) lowView(mc.player);
	}

	private void sample() {
		tick++;
		for (int i = 0; i < squids.size(); i++) {
			SquidEntity s = squids.get(i);
			if (s.dead) continue;
			double middle = (s.boundingBox.minY + s.boundingBox.maxY) / 2.0;
			double dy = s.y - s.prevY;
			boolean wet = s.isSubmergedInWater();
			if (log != null) log.printf(Locale.ROOT, "%d,%d,%.4f,%.4f,%d,%.3f%n", tick, i, middle - surface, dy, wet ? 1 : 0, s.tentacleAngle);
			heightSum += s.boundingBox.minY - (Y + 1);
			heightCount++;
			if (Math.abs(middle - surface) < 1.0) {
				nearTicks++;
				travel += Math.abs(dy);
				if (!wet) outTicks++;
				if (tick > 1 && wet != wasWet[i]) flips++;
				if (dy * lastDy[i] < 0 && Math.abs(dy) > 1e-3 && Math.abs(lastDy[i]) > 1e-3) reversals++;
				if (Math.abs(s.tentacleAngle - s.lastTentacleAngle) > 0.4F) twitches++;
				double out = Math.max(0.0, Math.min(1.0, (s.boundingBox.maxY - surface) / BODY));
				outSum += out;
				outMost = Math.max(outMost, out);
				highest = Math.max(highest, middle - surface);
			}
			wasWet[i] = wet;
			lastDy[i] = dy;
		}
	}

	private void report() {
		if (log != null) log.close();
		int alive = 0;
		for (SquidEntity s : squids) {
			if (!s.dead) alive++;
		}
		double seconds = nearTicks / 20.0;
		String seen = String.format(Locale.ROOT, "%d squid, %d squid-ticks of %d within a block of the surface (%.0f squid-seconds); there, a second: %.3f blocks of rise"
						+ " and fall, %.2f turns between up and down, %.2f changes of the water test, %.2f tentacle jumps; out of the water %d ticks (%.1f%%);"
						+ " body out on average %.0f%%, at most %.0f%%; middle at most %.2f from the surface; mean height over the floor %.2f",
				alive, nearTicks, heightCount, seconds, travel / seconds, reversals / seconds, flips / seconds, twitches / seconds, outTicks,
				100.0 * outTicks / Math.max(1, nearTicks), 100.0 * outSum / Math.max(1, nearTicks), 100.0 * outMost, highest, heightSum / Math.max(1, heightCount));
		System.out.println("[oldfaithful-selftest] (squid) " + side() + ": " + seen);
		if (on) {
			results.check("still squid surfacing: at the surface squid ease off and stay in the water (" + seen + "; want none out, the middle "
					+ Squids.DEPTH + " under at most)", alive == SQUIDS && nearTicks > 400 && outTicks == 0 && highest <= -Squids.DEPTH + 1e-3);
		} else {
			results.check("still squid surfacing (beta): at the surface squid bounce in and out of the water (" + seen + ")",
					alive == SQUIDS && nearTicks > 400 && outTicks > 0);
		}
	}

	private void startClip(Minecraft mc, String view) {
		Timer timer = ((MinecraftTimerAccessor) mc).oldfaithful$timer();
		savedTps = timer.tps;
		timer.tps = CLIP_TPS;
		TickRate.ms = Math.round(1000 / CLIP_TPS);
		clip = new Clip(world, new File(OUT, side() + "-" + view));
	}

	private void endClip(Minecraft mc, String view) {
		Clip c = clip;
		clip = null;
		Timer timer = ((MinecraftTimerAccessor) mc).oldfaithful$timer();
		timer.tps = savedTps;
		TickRate.ms = Math.round(1000 / savedTps);
		c.close();
		results.check(String.format(Locale.ROOT, "still squid surfacing: a clip from the %s, %d frames, %d short, the camera still (moved %.4f)", view, c.frames,
				c.missing, cameraDrift), c.frames >= CLIP * 3 - 6 && c.missing <= 3 && cameraDrift < 1e-6 && c.failed == null);
		cameraDrift = 0;
	}

	private boolean finish(Minecraft mc) {
		for (SquidEntity s : squids) s.markDead();
		mc.options.hideHud = (Boolean) saved[0];
		mc.options.bobView = (Boolean) saved[1];
		PlayerEntity p = mc.player;
		p.noClip = (Boolean) saved[5];
		p.setPositionAndAnglesKeepPrevAngles((Double) saved[2], (Double) saved[3], (Double) saved[4], 0, 0);
		return true;
	}

	/**
	 * A clip's frames: three a tick, each tick drawn at a third, two thirds and the whole of the way from the one before,
	 * read back after the frame is drawn and written as PNGs on a thread of their own.
	 */
	public static final class Clip {
		private final World world;
		private final File dir;
		private final ExecutorService writer = Executors.newFixedThreadPool(2);
		private long tick = Long.MIN_VALUE;
		private int taken;
		int frames, missing;
		volatile Throwable failed;

		Clip(World world, File dir) {
			this.world = world;
			this.dir = dir;
			dir.mkdirs();
			File[] old = dir.listFiles();
			if (old != null) {
				for (File f : old) f.delete();
			}
		}

		/** This frame's partial tick: the next third of the way to the latest tick, or the whole way once three are taken. */
		public float partial() {
			long t = world.getTime();
			if (t != tick) {
				if (tick != Long.MIN_VALUE && taken < 3) missing += 3 - taken;
				tick = t;
				taken = 0;
			}
			return taken < 3 ? (taken + 1) / 3.0F : 1.0F;
		}

		/** After the frame is drawn: kept, if it's one of the tick's three. */
		public void grab() {
			if (tick == Long.MIN_VALUE || taken >= 3) return;
			taken++;
			int w = Display.getWidth(), h = Display.getHeight();
			ByteBuffer px = BufferUtils.createByteBuffer(w * h * 3);
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
			GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, px);
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
			File file = new File(dir, String.format(Locale.ROOT, "f%04d.png", frames++));
			writer.execute(() -> {
				try {
					BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							int i = ((h - 1 - y) * w + x) * 3;
							img.setRGB(x, y, (px.get(i) & 0xFF) << 16 | (px.get(i + 1) & 0xFF) << 8 | px.get(i + 2) & 0xFF);
						}
					}
					ImageIO.write(img, "png", file);
				} catch (Throwable t) {
					failed = t;
				}
			});
		}

		void close() {
			writer.shutdown();
			try {
				writer.awaitTermination(2, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
