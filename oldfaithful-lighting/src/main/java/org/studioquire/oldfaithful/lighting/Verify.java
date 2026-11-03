package org.studioquire.oldfaithful.lighting;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSource;

/**
 * {@code -Doldfaithful.lighting.verify=true}: at each full save, once the queue is finished, every loaded cell whose
 * neighbours are loaded is checked against the rule, and what disagrees is logged. For testing; off by default.
 */
public final class Verify {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.lighting.verify");
	/** {@code -Doldfaithful.lighting.verifyEvery=<seconds>}: the same check now and then while playing, by distance. */
	public static final int EVERY_S = Integer.getInteger("oldfaithful.lighting.verifyEvery", 0);
	private static long last = System.nanoTime();

	public static void periodic(World world, ChunkSource source, int queued) {
		long now = System.nanoTime();
		if (now - last < EVERY_S * 1_000_000_000L) return;
		last = now;
		Stats.log("verify: queue " + queued);
		all(world, source);
	}

	private Verify() {
	}

	/** Returns how many cells disagree (-1 if the loaded chunks can't be listed). */
	public static int all(World world, ChunkSource source) {
		List<Chunk> chunks = loadedChunks(source);
		if (chunks == null) {
			Stats.log("verify: can't list the loaded chunks of " + source.getClass().getName());
			return -1;
		}
		int wrong = 0, logged = 0;
		int[] byDistance = new int[5];
		net.minecraft.entity.player.PlayerEntity player = world.players.isEmpty() ? null : (net.minecraft.entity.player.PlayerEntity) world.players.get(0);
		boolean sky = !world.dimension.hasCeiling;
		for (Chunk c : chunks) {
			if (c.isEmpty()) continue;
			Chunk w = Relight.loaded(world, c.x - 1, c.z), e = Relight.loaded(world, c.x + 1, c.z);
			Chunk n = Relight.loaded(world, c.x, c.z - 1), s = Relight.loaded(world, c.x, c.z + 1);
			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					if (lx == 0 && w == null || lx == 15 && e == null || lz == 0 && n == null || lz == 15 && s == null) continue;
					for (int type = sky ? 0 : 1; type < 2; type++) {
						for (int y = 0; y < 128; y++) {
							if (!Relight.disagrees(type, c, w, e, n, s, lx, y, lz)) continue;
							wrong++;
							if (player != null) {
								int d = Math.max(Math.abs(c.x - ((int) Math.floor(player.x) >> 4)), Math.abs(c.z - ((int) Math.floor(player.z) >> 4)));
								byDistance[Math.min(d / 4, 4)]++;
							}
							if (logged++ < (EVERY_S > 0 ? 3 : 20)) {
								int x = c.x * 16 + lx, z = c.z * 16 + lz;
								Stats.log(String.format("verify: %s light at %d %d %d (block %d, height %d) is %d; neighbours -x %d +x %d -y %d +y %d -z %d +z %d",
										type == 0 ? "sky" : "block", x, y, z, c.blocks[lx << 11 | lz << 7 | y] & 255, c.heightmap[lz << 4 | lx] & 255,
										get(world, type, x, y, z), get(world, type, x - 1, y, z), get(world, type, x + 1, y, z),
										get(world, type, x, y - 1, z), get(world, type, x, y + 1, z), get(world, type, x, y, z - 1), get(world, type, x, y, z + 1)));
							}
						}
					}
				}
			}
		}
		Stats.log("verify: " + chunks.size() + " chunks, " + wrong + " cells disagree with the rule"
				+ (player == null ? "" : "; by chunk distance 0-3, 4-7, 8-11, 12-15, 16+: " + java.util.Arrays.toString(byDistance)));
		return wrong;
	}

	private static int get(World world, int type, int x, int y, int z) {
		return world.getBrightness(type == 0 ? net.minecraft.world.LightType.SKY : net.minecraft.world.LightType.BLOCK, x, y, z);
	}

	@SuppressWarnings("unchecked")
	private static List<Chunk> loadedChunks(ChunkSource source) {
		try {
			for (Field f : source.getClass().getDeclaredFields()) {
				if (f.getType() == List.class) {
					f.setAccessible(true);
					return new ArrayList<>((List<Chunk>) f.get(source));
				}
			}
		} catch (ReflectiveOperationException e) {
			return null;
		}
		return null;
	}
}
