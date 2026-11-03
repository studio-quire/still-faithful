package org.studioquire.oldfaithful.lighting;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.chunk.storage.RegionFile;

/**
 * Light as b1.7.3's own rule says it should be, computed from scratch for a saved world, against what's stored.
 *
 * <p>The rule is the one {@code LightUpdate.updateLight} applies to a cell: sky light is 15 at or above the column's
 * height-map value, block light starts at the block's luminance; otherwise a cell takes the brightest of its six
 * neighbours less its own opacity (at least 1), never below 0 or its own source. That rule has exactly one fixed
 * point (each step costs at least 1, so light can't hold itself up in a loop), and it's what vanilla's updates
 * converge to when none is lost. Here it's computed by a flood from the sources over every chunk in the region files,
 * missing chunks reading 0 as the game reads them, and compared only for chunks whose eight neighbours were all saved:
 * light travels at most 15 blocks, so their values can't depend on anything beyond the files.
 *
 * <p>Written independently of the mod: it shares no code with it.
 */
public final class LightCheck {
	public static final class Result {
		public int chunksLoaded;
		public int chunksCompared;
		public long cells;
		/** [type][0 too dark, 1 too bright] */
		public final long[][] wrong = new long[2][2];
		/** Mismatches in a column on a chunk's edge (x or z 0 or 15), per type. */
		public final long[] onEdge = new long[2];
		public final long[] chunksWithErrors = new long[2];
		public long heightMapWrong;
		public final List<String> samples = new ArrayList<>();
		final int[] sampleCount = new int[2];
		public final int[][] byBlock = new int[2][256];

		public long total(int type) {
			return wrong[type][0] + wrong[type][1];
		}

		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append(String.format("%d chunks read, %d compared (%,d cells each type)%n", chunksLoaded, chunksCompared, cells));
			String[] names = {"sky", "block"};
			for (int t = 0; t < 2; t++) {
				s.append(String.format("  %-5s light: %,d wrong (%,d too dark, %,d too bright), %,d on chunk edges, in %d chunks%n", names[t],
						total(t), wrong[t][0], wrong[t][1], onEdge[t], chunksWithErrors[t]));
				StringBuilder blocks = new StringBuilder();
				for (int id = 0; id < 256; id++) if (byBlock[t][id] > 0) blocks.append(' ').append(id).append(':').append(byBlock[t][id]);
				if (blocks.length() > 0) s.append("        by block id:").append(blocks).append('\n');
			}
			s.append(String.format("  height maps disagreeing with the blocks: %,d columns%n", heightMapWrong));
			for (String sample : samples) s.append("    ").append(sample).append('\n');
			return s.toString();
		}
	}

	private static final class ChunkData {
		final int cx, cz;
		final byte[] blocks;
		final byte[] sky, block; // nibbles, as saved
		final byte[] height;
		final byte[][] expected = new byte[2][32768];

		ChunkData(int cx, int cz, byte[] blocks, byte[] sky, byte[] block, byte[] height) {
			this.cx = cx;
			this.cz = cz;
			this.blocks = blocks;
			this.sky = sky;
			this.block = block;
			this.height = height;
		}

		int stored(int type, int i) {
			byte[] a = type == 0 ? sky : block;
			return (i & 1) == 0 ? a[i >> 1] & 15 : a[i >> 1] >> 4 & 15;
		}
	}

	private LightCheck() {
	}

	/** Checks the chunks in {@code regionDir}; {@code hasSky} false for the Nether (sky light is never updated there). */
	public static Result check(File regionDir, boolean hasSky) throws IOException {
		return check(regionDir, hasSky, false);
	}

	/**
	 * {@code betaLava}: the rule as the mod keeps it with {@code lava.lightGenerated=false}. Lava whose stored block
	 * light is 0 gives no light until light reaches it: a neighbour lit to 1 or more makes it a source (15), as
	 * vanilla's update lights lava next to a cell whose light changes. The least light satisfying that.
	 */
	public static Result check(File regionDir, boolean hasSky, boolean betaLava) throws IOException {
		int[] opacity = Block.BLOCKS_LIGHT_OPACITY.clone();
		int[] luminance = Block.BLOCKS_LIGHT_LUMINANCE.clone();

		List<ChunkData> chunks = new ArrayList<>();
		File[] files = regionDir.listFiles((d, n) -> n.endsWith(".mcr"));
		if (files == null) throw new IOException("no region folder " + regionDir);
		for (File f : files) {
			RegionFile region = new RegionFile(f);
			try {
				for (int i = 0; i < 1024; i++) {
					if (!region.hasChunkData(i & 31, i >> 5)) continue;
					DataInputStream in = region.getChunkInputStream(i & 31, i >> 5);
					NbtCompound level = NbtIo.read(in).getCompound("Level");
					chunks.add(new ChunkData(level.getInt("xPos"), level.getInt("zPos"), level.getByteArray("Blocks"),
							level.getByteArray("SkyLight"), level.getByteArray("BlockLight"), level.getByteArray("HeightMap")));
				}
			} finally {
				region.close();
			}
		}
		Result r = new Result();
		lastChunks = chunks;
		r.chunksLoaded = chunks.size();
		if (chunks.isEmpty()) return r;

		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (ChunkData c : chunks) {
			minX = Math.min(minX, c.cx);
			minZ = Math.min(minZ, c.cz);
			maxX = Math.max(maxX, c.cx);
			maxZ = Math.max(maxZ, c.cz);
		}
		int w = maxX - minX + 1, d = maxZ - minZ + 1;
		int[] grid = new int[w * d];
		java.util.Arrays.fill(grid, -1);
		for (int k = 0; k < chunks.size(); k++) grid[(chunks.get(k).cx - minX) * d + chunks.get(k).cz - minZ] = k;

		for (int type = 0; type < 2; type++) {
			if (type == 0 && !hasSky) continue;
			flood(type, chunks, grid, minX, minZ, w, d, opacity, luminance, betaLava && type == 1);
		}

		for (ChunkData c : chunks) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					int h = 127;
					int base = x << 11 | z << 7;
					while (h > 0 && opacity[c.blocks[base + h - 1] & 255] == 0) h--;
					if (h != (c.height[z << 4 | x] & 255) && fullNeighbourhood(c, grid, minX, minZ, w, d)) r.heightMapWrong++;
				}
			}
		}

		for (ChunkData c : chunks) {
			if (!fullNeighbourhood(c, grid, minX, minZ, w, d)) continue;
			r.chunksCompared++;
			r.cells += 32768;
			for (int type = 0; type < 2; type++) {
				if (type == 0 && !hasSky) continue;
				boolean any = false;
				for (int i = 0; i < 32768; i++) {
					int have = c.stored(type, i), want = c.expected[type][i];
					if (have == want) continue;
					any = true;
					r.wrong[type][have < want ? 0 : 1]++;
					int x = i >> 11, z = i >> 7 & 15, y = i & 127;
					if (x == 0 || x == 15 || z == 0 || z == 15) r.onEdge[type]++;
					r.byBlock[type][c.blocks[i] & 255]++;
					if (r.sampleCount[type]++ < 10) {
						r.samples.add(String.format("%s at %d %d %d (block %d, column height %d): stored %d, expected %d", type == 0 ? "sky" : "block",
								c.cx * 16 + x, y, c.cz * 16 + z, c.blocks[i] & 255, c.height[z << 4 | x] & 255, have, want));
					}
				}
				if (any) r.chunksWithErrors[type]++;
			}
		}
		return r;
	}

	private static boolean fullNeighbourhood(ChunkData c, int[] grid, int minX, int minZ, int w, int d) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int gx = c.cx + dx - minX, gz = c.cz + dz - minZ;
				if (gx < 0 || gz < 0 || gx >= w || gz >= d || grid[gx * d + gz] < 0) return false;
			}
		}
		return true;
	}

	/** The fixed point of the rule over all chunks: a flood from the sources, brightest first. */
	private static boolean unlitLava(ChunkData c, int i) {
		int id = c.blocks[i] & 255;
		return (id == 10 || id == 11) && c.stored(1, i) == 0;
	}

	private static void flood(int type, List<ChunkData> chunks, int[] grid, int minX, int minZ, int w, int d, int[] opacity, int[] luminance,
			boolean betaLava) {
		// Positions are chunk number * 32768 + the cell's index in the chunk.
		IntQueue[] levels = new IntQueue[16];
		for (int l = 0; l < 16; l++) levels[l] = new IntQueue();
		for (int k = 0; k < chunks.size(); k++) {
			ChunkData c = chunks.get(k);
			byte[] e = c.expected[type];
			for (int i = 0; i < 32768; i++) {
				int source;
				if (type == 0) source = (i & 127) >= (c.height[(i >> 7 & 15) << 4 | i >> 11] & 255) ? 15 : 0;
				else source = betaLava && unlitLava(c, i) ? 0 : luminance[c.blocks[i] & 255];
				if (source > 0) {
					e[i] = (byte) source;
					levels[source].add(k * 32768 + i);
				}
			}
		}
		for (int level = 15; level >= 1; level--) {
			IntQueue q = levels[level];
			boolean lit = false;
			for (int n = 0; n < q.size; n++) {
				int pos = q.data[n];
				int k = pos >>> 15, i = pos & 32767;
				ChunkData c = chunks.get(k);
				if (c.expected[type][i] != level) continue; // raised since it was queued
				int x = i >> 11, z = i >> 7 & 15, y = i & 127;
				for (int dir = 0; dir < 6; dir++) {
					int nx = x, ny = y, nz = z;
					switch (dir) {
						case 0: nx--; break;
						case 1: nx++; break;
						case 2: ny--; break;
						case 3: ny++; break;
						case 4: nz--; break;
						default: nz++; break;
					}
					if (ny < 0 || ny > 127) continue;
					ChunkData nc = c;
					int nk = k;
					if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
						int gx = c.cx + (nx >> 4) - minX, gz = c.cz + (nz >> 4) - minZ;
						if (gx < 0 || gz < 0 || gx >= w || gz >= d || (nk = grid[gx * d + gz]) < 0) continue;
						nc = chunks.get(nk);
						nx &= 15;
						nz &= 15;
					}
					int ni = nx << 11 | nz << 7 | ny;
					int cost = Math.max(opacity[nc.blocks[ni] & 255], 1);
					int v = level - cost;
					if (betaLava && unlitLava(nc, ni) && nc.expected[type][ni] < 15) {
						v = 15; // reached by light: a source from now on
						lit = true;
					}
					if (v > nc.expected[type][ni]) {
						nc.expected[type][ni] = (byte) v;
						levels[v].add(nk * 32768 + ni);
					}
				}
			}
			levels[level] = new IntQueue();
			if (lit) level = 16; // newly lit lava is brighter than this level: start again from the top
		}
	}

	private static final class IntQueue {
		int[] data = new int[1024];
		int size;

		void add(int v) {
			if (size == data.length) data = java.util.Arrays.copyOf(data, size * 2);
			data[size++] = v;
		}
	}

	/** {@code LightCheck <world folder>}: the overworld and, if there is one, the Nether. */
	private static List<ChunkData> lastChunks;

	/** Stored and expected light around a cell of the last world checked: one layer per y, x across, z down. */
	static void probe(int type, int px, int py, int pz, int r) {
		for (int y = py - 1; y <= py + 1; y++) {
			System.out.println("y " + y + " (block id:stored/expected)");
			for (int z = pz - r; z <= pz + r; z++) {
				StringBuilder line = new StringBuilder();
				for (int x = px - r; x <= px + r; x++) {
					ChunkData c = null;
					for (ChunkData k : lastChunks) if (k.cx == x >> 4 && k.cz == z >> 4) c = k;
					if (c == null) { line.append("      -     "); continue; }
					int i = (x & 15) << 11 | (z & 15) << 7 | y;
					line.append(String.format("%4d:%2d/%2d  ", c.blocks[i] & 255, c.stored(type, i), c.expected[type][i]));
				}
				System.out.println(line);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		File world = new File(args[0]);
		if (args.length > 1) {
			check(new File(world, "region"), true);
			probe(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
			return;
		}
		System.out.println("Overworld: " + check(new File(world, "region"), true));
		File nether = new File(world, "DIM-1/region");
		if (nether.isDirectory()) System.out.println("Nether: " + check(nether, false));
	}

	static Map<String, Result> checkWorld(File world) throws IOException {
		return checkWorld(world, false);
	}

	static Map<String, Result> checkWorld(File world, boolean betaLava) throws IOException {
		Map<String, Result> out = new HashMap<>();
		out.put("overworld", check(new File(world, "region"), true, betaLava));
		File nether = new File(world, "DIM-1/region");
		if (nether.isDirectory()) out.put("nether", check(nether, false, betaLava));
		return out;
	}
}
