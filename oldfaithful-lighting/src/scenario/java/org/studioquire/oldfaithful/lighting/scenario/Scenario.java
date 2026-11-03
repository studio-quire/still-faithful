package org.studioquire.oldfaithful.lighting.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.server.world.chunk.ServerChunkCache;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.ServerWorld;

/**
 * Test-only: on the running dedicated server, one step a tick, the kinds of change that leave light wrong in beta.
 * Deterministic (a fixed seed), so runs with and without the lighting mod do the same things to the same terrain.
 * <ol>
 * <li>Chunks outside the spawn area generated in a scattered order, so new chunks meet old ones on every side.</li>
 * <li>Some of them unloaded (saved), then loaded again from disk once their neighbours have changed.</li>
 * <li>Explosions: on the surface, big ones, and underground.</li>
 * <li>Light sources placed and taken away; slabs of stone laid over water and partly removed.</li>
 * </ol>
 * Prints {@code [lighting-scenario] done} when finished; the test then saves and stops the server.
 */
public final class Scenario {
	/** Each world's steps, by dimension; one step a tick. */
	private static final java.util.Map<Integer, List<Runnable>> plans = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Integer> next = new java.util.HashMap<>();
	private static boolean done;
	private static List<Runnable> steps;

	private Scenario() {
	}

	/**
	 * {@code -Doldfaithful.lighting.scenario=play} runs the scenario in the overworld; {@code heal} loads every chunk
	 * saved in each dimension's region files (so the whole saved world goes through the mod's checks); anything else
	 * does nothing.
	 */
	private static final int PACE = Integer.getInteger("oldfaithful.lighting.pace", 0);
	private static final java.util.Map<Integer, Integer> passes = new java.util.HashMap<>();

	/** With a pace set, whether the server may run another lighting pass for this world this tick. */
	public static boolean morePasses(ServerWorld world) {
		if (PACE <= 0) return true;
		int n = passes.merge(world.dimension.id, 1, Integer::sum);
		return n < PACE;
	}

	public static void tick(ServerWorld world) {
		passes.put(world.dimension.id, 0);
		if (done) return;
		String mode = System.getProperty("oldfaithful.lighting.scenario", "");
		int dim = world.dimension.id;
		if (!plans.containsKey(dim)) {
			steps = new ArrayList<>();
			if (mode.equals("play") && dim == 0) plan(world);
			else if (mode.equals("heal")) planHeal(world);
			else if (mode.equals("paced") && dim == -1) planPaced(world);
			plans.put(dim, steps);
			next.put(dim, 0);
			if (!steps.isEmpty()) System.out.println("[lighting-scenario] dimension " + dim + ": " + steps.size() + " steps");
		}
		List<Runnable> mine = plans.get(dim);
		int at = next.get(dim);
		if (at < mine.size()) {
			mine.get(at).run();
			next.put(dim, at + 1);
		}
		if (!mode.equals("play") && !mode.equals("heal") && !mode.equals("paced")) return;
		for (int d : new int[] {0, -1}) {
			if (mode.equals("play") && d != 0 || mode.equals("paced") && d != -1) continue;
			if (!plans.containsKey(d) || next.get(d) < plans.get(d).size()) return;
		}
		System.out.println("[lighting-scenario] done");
		done = true;
	}

	/**
	 * Exploring the Nether at a single player client's pace: a band of new chunks around its spawn area, two a tick,
	 * with the lighting queue worked through at {@code oldfaithful.lighting.pace} passes a tick; then, at once (no
	 * waiting for the queue), the loaded world checked against the rule in memory. Light a chunk needs when it enters
	 * mustn't wait behind the queue.
	 */
	private static void planPaced(ServerWorld world) {
		Vec3i spawn = world.getSpawnPos();
		int scx = spawn.x >> 4, scz = spawn.z >> 4;
		List<int[]> band = new ArrayList<>();
		for (int r = 13; r <= 17; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) if (Math.max(Math.abs(dx), Math.abs(dz)) == r) band.add(new int[] {scx + dx, scz + dz});
			}
		}
		ServerChunkCache cache = world.chunkCache;
		for (int i = 0; i < band.size(); i += 2) {
			List<int[]> group = band.subList(i, Math.min(band.size(), i + 2));
			steps.add(() -> {
				for (int[] c : group) cache.loadChunk(c[0], c[1]);
			});
		}
		steps.add(() -> { });
		steps.add(() -> System.out.println("[lighting-scenario] paced check: "
				+ org.studioquire.oldfaithful.lighting.Verify.all(world, cache) + " cells disagree"));
	}

	private static void planHeal(ServerWorld world) {
		java.io.File dir = new java.io.File(world.dimension.id == 0 ? "world/region" : "world/DIM-1/region");
		java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".mcr"));
		List<int[]> all = new ArrayList<>();
		if (files != null) {
			for (java.io.File f : files) {
				String[] parts = f.getName().split("[.]");
				int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
				net.minecraft.world.chunk.storage.RegionFile region = new net.minecraft.world.chunk.storage.RegionFile(f);
				try {
					for (int i = 0; i < 1024; i++) if (region.hasChunkData(i & 31, i >> 5)) all.add(new int[] {rx * 32 + (i & 31), rz * 32 + (i >> 5)});
				} finally {
					region.close();
				}
			}
		}
		ServerChunkCache cache = world.chunkCache;
		for (int i = 0; i < all.size(); i += 50) {
			List<int[]> group = all.subList(i, Math.min(all.size(), i + 50));
			steps.add(() -> {
				for (int[] c : group) cache.loadChunk(c[0], c[1]);
			});
		}
		for (int i = 0; i < 40; i++) steps.add(() -> { });
	}

	private static void plan(ServerWorld world) {
		Random random = new Random(1773);
		Vec3i spawn = world.getSpawnPos();
		int scx = spawn.x >> 4, scz = spawn.z >> 4;
		ServerChunkCache cache = world.chunkCache;

		// 1. A band of new chunks around the spawn area (which spans about 12 chunks each way), in a scattered order.
		List<int[]> band = new ArrayList<>();
		for (int dx = -17; dx <= 17; dx++) {
			for (int dz = -17; dz <= 17; dz++) {
				int r = Math.max(Math.abs(dx), Math.abs(dz));
				if (r >= 13 && r <= 17 && (dx > 0 || dz > 3)) band.add(new int[] {scx + dx, scz + dz});
			}
		}
		Collections.shuffle(band, random);
		for (int i = 0; i < band.size(); i += 4) {
			List<int[]> group = band.subList(i, Math.min(band.size(), i + 4));
			steps.add(() -> {
				for (int[] c : group) cache.loadChunk(c[0], c[1]);
			});
		}

		// 2. Unload a scattered half of the band (saved on unload), change things next to them, load them again.
		List<int[]> away = new ArrayList<>();
		for (int[] c : band) if (random.nextBoolean()) away.add(c);
		steps.add(() -> {
			for (int[] c : away) cache.isLoaded(c[0], c[1]);
		});
		for (int i = 0; i < 5; i++) steps.add(() -> { }); // the cache unloads 100 a tick

		// 3. Explosions, 4. light sources and water, all inside the spawn area.
		for (int i = 0; i < 60; i++) {
			final int x = spawn.x + random.nextInt(320) - 160, z = spawn.z + random.nextInt(320) - 160;
			final int kind = random.nextInt(10);
			final int depth = 10 + random.nextInt(40);
			steps.add(() -> {
				int top = world.getTopY(x, z);
				if (kind < 5) world.createExplosion(null, x, top, z, 4.0F, false);
				else if (kind < 7) world.createExplosion(null, x, top - 2, z, 9.0F, false);
				else world.createExplosion(null, x, Math.max(6, top - depth), z, 5.0F, false);
			});
		}
		List<int[]> lamps = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			final int x = spawn.x + random.nextInt(320) - 160, z = spawn.z + random.nextInt(320) - 160;
			final boolean deep = random.nextBoolean();
			final int depth = 5 + random.nextInt(30);
			steps.add(() -> {
				int y = world.getTopY(x, z);
				if (deep) y = Math.max(2, y - depth);
				world.setBlock(x, y, z, Block.GLOWSTONE.id);
				lamps.add(new int[] {x, y, z});
			});
		}
		for (int i = 0; i < 20; i++) {
			final int x0 = spawn.x + random.nextInt(320) - 160, z0 = spawn.z + random.nextInt(320) - 160;
			final boolean takeBack = random.nextBoolean();
			steps.add(() -> {
				// The water surface nearest below the top, if this column has one.
				int y = world.getTopY(x0, z0);
				if (world.getBlockId(x0, y - 1, z0) != Block.WATER.id && world.getBlockId(x0, y - 1, z0) != Block.FLOWING_WATER.id) return;
				for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) world.setBlock(x0 + dx, y + 1, z0 + dz, Block.STONE.id);
				if (takeBack) for (int dx = -1; dx <= 1; dx++) for (int dz = -3; dz <= 3; dz++) world.setBlock(x0 + dx, y + 1, z0 + dz, 0);
			});
		}
		steps.add(() -> {
			for (int i = 0; i < lamps.size(); i += 2) {
				int[] p = lamps.get(i);
				world.setBlock(p[0], p[1], p[2], 0);
			}
		});

		// 2, again: the unloaded chunks come back from disk.
		steps.add(() -> {
			for (int[] c : away) cache.loadChunk(c[0], c[1]);
		});
		for (int i = 0; i < Integer.getInteger("oldfaithful.lighting.idle", 40); i++) steps.add(() -> { });
	}
}
