package org.studioquire.oldfaithful.fixes.scenario;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.block.Block;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.ServerWorld;
import org.studioquire.oldfaithful.fixes.scenario.mixin.WorldQueueAccessor;

/**
 * Test-only (ServerTicksTest, #42): a repeater clock 400 blocks east of the spawn, four repeaters in a ring with wire at
 * the corners and one pulse running round. Both modes load the clock's chunks on the first overworld tick.
 * {@code -Doldfaithful.fixes.scenario=build} builds and starts it once the new world's backlog of generated liquid ticks
 * (some 240,000, worked through at 1,000 a tick) has drained, so the clock's own ticks come round promptly;
 * {@code watch}, once the backlog has drained there too, only watches. Either way it counts how often the repeaters
 * switch over the next {@link #WATCH} ticks and prints it. A clock whose pending tick was lost stands still: no switches.
 */
public final class Scenario {
	private static final int WATCH = 80;
	private static final int Y = 100;
	// The ring: repeaters at (1,0) east, (2,1) south, (1,2) west, (0,1) north; wire at the four corners.
	private static final int[][] REPEATERS = {{1, 0, 1}, {2, 1, 2}, {1, 2, 3}, {0, 1, 0}};
	private static final int[][] WIRES = {{0, 0}, {2, 0}, {2, 2}, {0, 2}};
	private static final int DELAY = 1 << 2;

	private static final int MAX_WAIT = 3000;

	/** In the server's folder: where the build put the clock. */
	private static final File CLOCK = new File("clock.txt");

	private static boolean placed;
	private static int waited;
	private static int ticks = -1;
	private static int ox;
	private static int oz;
	private static int[] last;
	private static int changes;

	private Scenario() {
	}

	public static void tick(ServerWorld world) {
		String mode = System.getProperty("oldfaithful.fixes.scenario", "");
		if (mode.isEmpty() || world.dimension.id != 0 || ticks >= WATCH) return;
		boolean build = mode.equals("build");
		if (!placed) {
			// The clock's place: east of the spawn when building, from the build when watching. Its chunks and 16 blocks
			// round them (so they're decorated, with room for the tick check's 8) are loaded as a player nearby would.
			// loadChunk, not getChunk: outside event processing a server's getChunk hands back an empty chunk (air) for
			// one not yet loaded. Beside the spawn the clock was in the area loaded at start, until a restart moved the
			// spawn (with the Nether on, vanilla's server saves the Nether's spawn over the overworld's, docs/bugs.md
			// #52) and left it out: one run in seven read air. Kept out of the spawn area, the loading is nearly always
			// tested.
			if (build) {
				Vec3i spawn = world.getSpawnPos();
				ox = spawn.x + 400;
				oz = spawn.z;
			} else {
				try {
					String[] p = new String(Files.readAllBytes(CLOCK.toPath()), StandardCharsets.UTF_8).trim().split(" ");
					ox = Integer.parseInt(p[0]);
					oz = Integer.parseInt(p[1]);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			boolean preloaded = world.chunkCache.isChunkLoaded(ox >> 4, oz >> 4);
			for (int x = ox - 16; x <= ox + 2 + 16; x += 8) {
				for (int z = oz - 16; z <= oz + 2 + 16; z += 8) world.chunkCache.loadChunk(x >> 4, z >> 4);
			}
			placed = true;
			say("spawn " + world.getSpawnPos().x + " " + world.getSpawnPos().z + "; the clock's chunk "
					+ (preloaded ? "was in the spawn area" : "loaded here"));
		}
		if (ticks < 0) {
			int pending = ((WorldQueueAccessor) world).oldfaithful$queue().size();
			if (pending > 2000 && ++waited < MAX_WAIT) return;
			say("pending ticks " + pending + " after " + waited + " ticks; time " + world.getTime());
			if (build) {
				build(world);
				try {
					Files.write(CLOCK.toPath(), (ox + " " + oz).getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			last = ids(world);
			say("watching the clock at " + ox + " " + Y + " " + oz + ": " + describe(last));
		} else {
			int[] now = ids(world);
			for (int i = 0; i < now.length; i++) if (now[i] != last[i]) changes++;
			last = now;
		}
		if (++ticks == WATCH) say("switches in " + WATCH + " ticks: " + changes);
	}

	private static void build(ServerWorld world) {
		for (int x = -1; x <= 3; x++) {
			for (int z = -1; z <= 3; z++) {
				world.setBlock(ox + x, Y - 1, oz + z, Block.STONE.id);
				for (int y = Y; y <= Y + 2; y++) world.setBlock(ox + x, y, oz + z, 0);
			}
		}
		for (int[] w : WIRES) world.setBlock(ox + w[0], Y, oz + w[1], Block.REDSTONE_WIRE.id);
		for (int[] r : REPEATERS) world.setBlock(ox + r[0], Y, oz + r[1], Block.REPEATER.id, r[2] | DELAY);
		// One pulse: the first repeater on, due to switch off again, which sets the next one going.
		int[] first = REPEATERS[0];
		world.setBlock(ox + first[0], Y, oz + first[1], Block.POWERED_REPEATER.id, first[2] | DELAY);
		world.scheduleBlockUpdate(ox + first[0], Y, oz + first[1], Block.POWERED_REPEATER.id, 4);
		say("built");
	}

	private static int[] ids(ServerWorld world) {
		int[] out = new int[REPEATERS.length];
		for (int i = 0; i < REPEATERS.length; i++) out[i] = world.getBlockId(ox + REPEATERS[i][0], Y, oz + REPEATERS[i][1]);
		return out;
	}

	private static String describe(int[] ids) {
		StringBuilder s = new StringBuilder();
		for (int id : ids) s.append(id).append(' ');
		return s.toString().trim();
	}

	private static void say(String s) {
		System.out.println("[fixes-scenario] " + s);
	}
}
