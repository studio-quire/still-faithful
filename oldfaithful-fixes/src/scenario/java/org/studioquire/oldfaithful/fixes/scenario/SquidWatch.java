package org.studioquire.oldfaithful.fixes.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.world.ServerWorld;

/**
 * Test-only (SquidWatchRun, #76 and #77): real squid, ticked by the real server, in tanks built in the sky, with
 * {@code -Doldfaithful.fixes.scenario=squids}. A measurement, not a pass or fail: every {@link #EVERY} ticks one line of
 * each tank's mean height over its floor; at the end what each tank's squid did, near the surface above all.
 * <ul>
 * <li>floor30, water 30 deep, 16 squid starting on the floor; mixed30, the same with 16 starting at random heights;</li>
 * <li>surf3, water 3 deep, 16 squid, to see plenty of the surface; pool1, water 1 deep, 8 squid;</li>
 * <li>flow, a trough 3 wide of flowing water going from full to its thinnest over 8 blocks, then dry stone, 6 squid;</li>
 * <li>land, a walled stone floor, 4 squid.</li>
 * </ul>
 */
public final class SquidWatch {
	private static final int TICKS = Integer.getInteger("oldfaithful.fixes.squidTicks", 12000);
	private static final int EVERY = 600;
	private static final int FLOOR = 90;
	private static final double BODY = 0.95F;
	/** The visible surface of still water, under the top of its block. */
	private static final double SURFACE_GAP = 1.0 / 9.0;

	private static int ticks = -1;
	private static final List<Tank> tanks = new ArrayList<>();
	private static Tank flow, land;
	private static int flowX;
	private static final Random random = new Random(77);

	private static final class Tank {
		final String name;
		final int x0, z0, width, depth;
		final List<SquidEntity> squids = new ArrayList<>();
		final boolean[] wasDry;
		final int[] dryRun;
		long steps, splashes, flips, dryTicks, squidTicks;
		// Near the surface: the middle within a block of it.
		long nearTicks, nearDryTicks, wholeOutTicks;
		double outSum, outMost, feetMost = Double.NEGATIVE_INFINITY;
		int longestDry;
		double settledSum;
		long settledCount;

		Tank(String name, int x0, int z0, int width, int depth, int squids) {
			this.name = name;
			this.x0 = x0;
			this.z0 = z0;
			this.width = width;
			this.depth = depth;
			wasDry = new boolean[squids];
			dryRun = new int[squids];
		}

		double floorTop() {
			return FLOOR + 1;
		}

		double surface() {
			return FLOOR + 1 + depth - SURFACE_GAP;
		}

		boolean holds(double x, double z) {
			return x >= x0 - 1 && x <= x0 + width + 1 && z >= z0 - 1 && z <= z0 + width + 1;
		}

		double meanHeight() {
			double sum = 0;
			for (SquidEntity s : squids) sum += s.boundingBox.minY - floorTop();
			return sum / squids.size();
		}

		double maxHeight() {
			double most = 0;
			for (SquidEntity s : squids) most = Math.max(most, s.boundingBox.minY - floorTop());
			return most;
		}

		void sample(boolean settled) {
			for (int i = 0; i < squids.size(); i++) {
				SquidEntity s = squids.get(i);
				squidTicks++;
				boolean dry = !s.isSubmergedInWater();
				if (dry) {
					dryTicks++;
					longestDry = Math.max(longestDry, ++dryRun[i]);
				} else {
					dryRun[i] = 0;
				}
				if (dry != wasDry[i] && squidTicks > squids.size()) flips++;
				wasDry[i] = dry;
				if (settled) {
					settledSum += s.boundingBox.minY - floorTop();
					settledCount++;
				}
				double middle = (s.boundingBox.minY + s.boundingBox.maxY) / 2.0;
				if (Math.abs(middle - surface()) < 1.0) {
					nearTicks++;
					if (dry) nearDryTicks++;
					double out = Math.max(0.0, Math.min(1.0, (s.boundingBox.maxY - surface()) / BODY));
					outSum += out;
					outMost = Math.max(outMost, out);
					feetMost = Math.max(feetMost, s.boundingBox.minY - surface());
					if (s.boundingBox.minY > surface()) wholeOutTicks++;
				}
			}
		}

		String summary() {
			double minutes = squidTicks / 1200.0;
			return String.format(Locale.ROOT, "%s: mean height over the floor in the second half %.2f, highest now %.2f; "
							+ "per squid-minute %.1f footsteps, %.2f splashes, %.1f changes of branch; %.1f%% of ticks in the out-of-water branch, longest run %d; "
							+ "near the surface %.1f%% of ticks, of those %.1f%% out-of-water, body out on average %.0f%%, at most %.0f%%, feet at most %.2f from the surface, wholly out %d ticks",
					name, settledSum / Math.max(1, settledCount), maxHeight(), steps / minutes, splashes / minutes, flips / minutes,
					100.0 * dryTicks / squidTicks, longestDry, 100.0 * nearTicks / squidTicks, nearTicks == 0 ? 0.0 : 100.0 * nearDryTicks / nearTicks,
					nearTicks == 0 ? 0.0 : 100.0 * outSum / nearTicks, 100.0 * outMost, nearTicks == 0 ? 0.0 : feetMost, wholeOutTicks);
		}
	}

	private SquidWatch() {
	}

	public static void heard(Entity source, String sound) {
		if (!(source instanceof SquidEntity)) return;
		for (Tank t : tanks) {
			if (!t.holds(source.x, source.z)) continue;
			if (sound.startsWith("step.")) t.steps++;
			else if (sound.equals("random.splash")) t.splashes++;
			return;
		}
		if (flow != null && flow.holds(source.x, source.z)) {
			if (sound.startsWith("step.")) flow.steps++;
			else if (sound.equals("random.splash")) flow.splashes++;
		}
	}

	public static void tick(ServerWorld world) {
		if (world.dimension.id != 0 || ticks >= TICKS) return;
		if (ticks < 0) {
			build(world);
			ticks = 0;
			return;
		}
		ticks++;
		for (Tank t : tanks) t.sample(ticks > TICKS / 2);
		if (ticks % EVERY == 0) {
			StringBuilder line = new StringBuilder("t=" + ticks);
			for (Tank t : tanks) line.append(String.format(Locale.ROOT, " %s %.2f (max %.2f)", t.name, t.meanHeight(), t.maxHeight()));
			say(line.toString());
		}
		if (ticks == TICKS) finish(world);
	}

	private static void build(ServerWorld world) {
		int ox = world.getSpawnPos().x + 300, oz = world.getSpawnPos().z;
		// A server ticks an entity only with every chunk within 32 blocks of it loaded.
		for (int x = (ox - 48) >> 4; x <= (ox + 128) >> 4; x++) {
			for (int z = (oz - 48) >> 4; z <= (oz + 96) >> 4; z++) world.chunkCache.loadChunk(x, z);
		}
		tank(world, "floor30", ox, oz, 16, 30, 16, false);
		tank(world, "mixed30", ox + 24, oz, 16, 30, 16, true);
		tank(world, "surf3", ox + 48, oz, 12, 3, 16, true);
		tank(world, "pool1", ox + 66, oz, 12, 1, 8, false);
		trough(world, ox, oz + 24);
		landPad(world, ox + 40, oz + 24);
	}

	private static void tank(ServerWorld world, String name, int x0, int z0, int width, int depth, int count, boolean anyHeight) {
		for (int x = x0 - 1; x <= x0 + width; x++) {
			for (int z = z0 - 1; z <= z0 + width; z++) {
				boolean wall = x == x0 - 1 || x == x0 + width || z == z0 - 1 || z == z0 + width;
				world.setBlockWithoutNotifyingNeighbors(x, FLOOR, z, Block.STONE.id, 0);
				for (int y = FLOOR + 1; y <= FLOOR + depth + 3; y++) {
					world.setBlockWithoutNotifyingNeighbors(x, y, z, wall ? Block.STONE.id : y <= FLOOR + depth ? Block.WATER.id : 0, 0);
				}
			}
		}
		Tank tank = new Tank(name, x0, z0, width, depth, count);
		for (int i = 0; i < count; i++) {
			double y = FLOOR + 1 + (anyHeight ? random.nextDouble() * (depth - 1) : 0.0);
			tank.squids.add(squid(world, x0 + 1 + random.nextDouble() * (width - 2), y, z0 + 1 + random.nextDouble() * (width - 2)));
		}
		tanks.add(tank);
	}

	private static SquidEntity squid(ServerWorld world, double x, double y, double z) {
		SquidEntity squid = new SquidEntity(world);
		squid.setPositionAndAnglesKeepPrevAngles(x, y, z, random.nextFloat() * 360.0F, 0.0F);
		world.spawnEntity(squid);
		return squid;
	}

	/** Placed without updates, so the water stays as it is: full at the west end, thinning eastward, then dry stone. */
	private static void trough(ServerWorld world, int x0, int z0) {
		flowX = x0;
		for (int x = x0 - 1; x <= x0 + 24; x++) {
			for (int z = z0 - 1; z <= z0 + 3; z++) {
				boolean wall = x == x0 - 1 || x == x0 + 24 || z == z0 - 1 || z == z0 + 3;
				world.setBlockWithoutNotifyingNeighbors(x, FLOOR, z, Block.STONE.id, 0);
				for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
					boolean wet = !wall && y == FLOOR + 1 && x < x0 + 8;
					world.setBlockWithoutNotifyingNeighbors(x, y, z, wall ? Block.STONE.id : wet ? Block.FLOWING_WATER.id : 0, wet ? x - x0 : 0);
				}
			}
		}
		flow = new Tank("flow", x0, z0, 24, 1, 6);
		for (int i = 0; i < 6; i++) flow.squids.add(squid(world, x0 + 0.6 + i * 0.5, FLOOR + 1, z0 + 0.6 + (i % 2) * 1.8));
	}

	private static void landPad(ServerWorld world, int x0, int z0) {
		for (int x = x0 - 1; x <= x0 + 8; x++) {
			for (int z = z0 - 1; z <= z0 + 8; z++) {
				boolean wall = x == x0 - 1 || x == x0 + 8 || z == z0 - 1 || z == z0 + 8;
				world.setBlockWithoutNotifyingNeighbors(x, FLOOR, z, Block.STONE.id, 0);
				for (int y = FLOOR + 1; y <= FLOOR + 3; y++) world.setBlockWithoutNotifyingNeighbors(x, y, z, wall ? Block.STONE.id : 0, 0);
			}
		}
		land = new Tank("land", x0, z0, 8, 1, 4);
		for (int i = 0; i < 4; i++) land.squids.add(squid(world, x0 + 2.5 + i, FLOOR + 3, z0 + 2.5 + i));
	}

	private static void finish(ServerWorld world) {
		for (Tank t : tanks) say(t.summary());
		StringBuilder bins = new StringBuilder("floor30 and mixed30 at the end, squid per 5 blocks of height, from the floor:");
		int[] bin = new int[7];
		for (Tank t : tanks) {
			if (t.depth != 30) continue;
			for (SquidEntity s : t.squids) bin[(int) Math.min(6, (s.boundingBox.minY - t.floorTop()) / 5.0)]++;
		}
		for (int b : bin) bins.append(' ').append(b);
		say(bins.toString());

		int inWater = 0, onStone = 0;
		StringBuilder at = new StringBuilder();
		for (SquidEntity s : flow.squids) {
			boolean wet = world.getMaterial((int) Math.floor(s.x), FLOOR + 1, (int) Math.floor(s.z)) == Material.WATER;
			if (wet) inWater++;
			else onStone++;
			at.append(String.format(Locale.ROOT, " %.1f@%.2f", s.x - flowX, s.boundingBox.minY - flow.floorTop()));
		}
		say("flow: " + inWater + " over water, " + onStone + " over dry stone; each squid's blocks along the trough@height:" + at + "; footsteps " + flow.steps + ", splashes " + flow.splashes);

		StringBuilder pad = new StringBuilder("land:");
		for (int i = 0; i < land.squids.size(); i++) {
			SquidEntity s = land.squids.get(i);
			pad.append(String.format(Locale.ROOT, " moved %.2f, height %.2f, health %d, dead %b;", Math.hypot(s.x - (land.x0 + 2.5 + i), s.z - (land.z0 + 2.5 + i)),
					s.boundingBox.minY - land.floorTop(), s.health, s.dead));
		}
		say(pad.toString());
		say("done");
	}

	private static void say(String s) {
		System.out.println("[fixes-scenario] squids " + s);
	}
}
