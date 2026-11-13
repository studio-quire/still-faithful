package org.studioquire.oldfaithful.selftest;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.LegacyChunkCache;

/**
 * SceneShot's survey: the surface of an area of the world ({@code "x0 z0 x1 z1"}), for choosing views without
 * looking blind. Generated a tile of 8 by 8 chunks a tick, the client's chunk cache centred on each tile in turn (it
 * only serves chunks within 15 of its centre). Writes {@code survey.bin}: four little-endian ints (x0, z0, width,
 * depth), then per column, z-major, five bytes: the top block's height and id, the ground's height and id (the top
 * ignoring leaves, logs and plants), and the biome's index in {@code survey.txt}'s list. Any block of a mod's is id 255.
 * Beside it, {@code survey-climate.bin}: no header, per column in the same order three bytes, the temperature and downfall
 * beta's biomes and colours come from (0 to 1 as 0 to 255) and the top block's meta (a leaf's kind: 0 oak, 1 spruce, 2 birch).
 */
final class Survey {
	private static final int TILE = 8;
	private final int x0, z0, w, d;
	private final byte[] cells;
	/** survey-climate.bin's: per column as cells has them, three bytes: beta's temperature and downfall (0 to 1, as 0 to 255), and the top block's meta. */
	private final byte[] climate;
	private final File out;
	private final List<String> biomes = new ArrayList<>();
	private int tile;

	Survey(String area, String out) {
		String[] a = area.trim().split("\\s+");
		x0 = Integer.parseInt(a[0]) & ~15;
		z0 = Integer.parseInt(a[1]) & ~15;
		w = ((Integer.parseInt(a[2]) - x0) | 15) + 1;
		d = ((Integer.parseInt(a[3]) - z0) | 15) + 1;
		cells = new byte[w * d * 5];
		climate = new byte[w * d * 3];
		this.out = new File(out);
	}

	boolean tick(Minecraft mc, SelfTest results) {
		int tilesX = (w / 16 + TILE - 1) / TILE, tilesZ = (d / 16 + TILE - 1) / TILE;
		if (tile >= tilesX * tilesZ) {
			write();
			results.pass("scene: survey of " + w + " by " + d + " from " + x0 + "," + z0 + " in " + out + File.separator + "survey.bin");
			return true;
		}
		World world = mc.world;
		int cx0 = (x0 >> 4) + (tile % tilesX) * TILE, cz0 = (z0 >> 4) + (tile / tilesX) * TILE;
		int cx1 = Math.min(cx0 + TILE, (x0 + w) >> 4), cz1 = Math.min(cz0 + TILE, (z0 + d) >> 4);
		if (world.getChunkSource() instanceof LegacyChunkCache) {
			((LegacyChunkCache) world.getChunkSource()).setSpawnPoint(cx0 + TILE / 2, cz0 + TILE / 2);
		}
		// A ring around the tile too, so every chunk in it gets decorated (trees, lakes, snow).
		for (int cx = cx0 - 1; cx <= cx1; cx++) {
			for (int cz = cz0 - 1; cz <= cz1; cz++) world.getChunk(cx, cz);
		}
		for (int cx = cx0; cx < cx1; cx++) {
			for (int cz = cz0; cz < cz1; cz++) {
				Chunk c = world.getChunk(cx, cz);
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) column(world, c, cx * 16 + lx, cz * 16 + lz, lx, lz);
				}
			}
		}
		tile++;
		return false;
	}

	private void column(World world, Chunk c, int x, int z, int lx, int lz) {
		int top = 127;
		while (top > 0 && c.getBlockId(lx, top, lz) == 0) top--;
		int ground = top;
		while (ground > 0 && !isGround(c.getBlockId(lx, ground, lz))) ground--;
		Biome biome = world.method_1781().getBiome(x, z);
		int k = ((z - z0) * w + (x - x0)) * 3;
		climate[k] = (byte) Math.round(world.method_1781().temperatureMap[0] * 255);
		climate[k + 1] = (byte) Math.round(world.method_1781().downfallMap[0] * 255);
		climate[k + 2] = (byte) c.getBlockMeta(lx, top, lz);
		int b = biomes.indexOf(biome.name);
		if (b < 0) {
			b = biomes.size();
			biomes.add(biome.name);
		}
		int i = ((z - z0) * w + (x - x0)) * 5;
		cells[i] = (byte) top;
		cells[i + 1] = id(c.getBlockId(lx, top, lz));
		cells[i + 2] = (byte) ground;
		cells[i + 3] = id(c.getBlockId(lx, ground, lz));
		cells[i + 4] = (byte) b;
	}

	/** A mod's block (the library's start at 512) as 255: a byte holds beta's ids only. */
	private static byte id(int id) {
		return (byte) Math.min(id, 255);
	}

	private static boolean isGround(int id) {
		if (id == 0 || id == Block.LEAVES.id || id == Block.LOG.id) return false;
		Material m = Block.BLOCKS[id].material;
		return m.blocksMovement() || m.isFluid();
	}

	private void write() {
		try {
			out.mkdirs();
			try (DataOutputStream o = new DataOutputStream(Files.newOutputStream(new File(out, "survey.bin").toPath()))) {
				for (int v : new int[] {x0, z0, w, d}) o.writeInt(Integer.reverseBytes(v));
				o.write(cells);
			}
			Files.write(new File(out, "survey-climate.bin").toPath(), climate);
			Files.write(new File(out, "survey.txt").toPath(), String.join("\n", biomes).concat("\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
