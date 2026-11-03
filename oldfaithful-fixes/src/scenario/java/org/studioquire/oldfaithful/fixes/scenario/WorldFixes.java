package org.studioquire.oldfaithful.fixes.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.ServerWorld;
import org.studioquire.oldfaithful.fixes.scenario.mixin.NaturalSpawnerInvoker;

/**
 * Test-only (ServerWorldFixesTest): Old Faithful's world fixes from the piston hunt and the read after it, on the real
 * server, with {@code -Doldfaithful.fixes.scenario=world}. On the first overworld tick it builds its scenes in the sky
 * (y 90 to 125, above the terrain), well away from the spawn and from each other, and prints one line per check,
 * "[fixes-scenario] #NN ...", with what it saw; the test reads them with the fixes and without (beta's bugs). The
 * checks that need time (falls, falling sand) print after {@link #SETTLE} ticks.
 */
public final class WorldFixes {
	private static final int SETTLE = 100;
	private static final int[] HEIGHTS = {12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};

	private static int ticks = -1;
	private static int ox, oz;
	private static final List<PigEntity> fallers = new ArrayList<>();

	private WorldFixes() {
	}

	public static void tick(ServerWorld world) {
		if (world.dimension.id != 0) return;
		if (ticks < 0) {
			ox = world.getSpawnPos().x + 300;
			oz = world.getSpawnPos().z;
			// A server ticks an entity only with every chunk within 32 blocks of it loaded.
			load(world, ox - 48, oz - 48, ox + 96, oz + 64);
			doorTop(world, ox, oz);
			lookUp(world);
			spawnBesideWater(world);
			squid(world, ox + 8, oz);
			falls(world, ox + 16, oz + 16);
			fallingSand(world, ox, oz + 8);
			ticks = 0;
			return;
		}
		if (++ticks != SETTLE) return;
		StringBuilder damage = new StringBuilder();
		int hurt = 0;
		for (int i = 0; i < fallers.size(); i++) {
			PigEntity pig = fallers.get(i);
			int d = 1000 - pig.health;
			if (d > 0) hurt++;
			damage.append(i == 0 ? "" : ", ").append(HEIGHTS[i]).append(':').append(d);
			// Where a hurt one ended up, and on what: a pig that drifted onto the rim isn't the water's.
			if (d > 0) damage.append(String.format(Locale.ROOT, " (at %.2f %.2f %.2f on %d)", pig.x - ox, pig.boundingBox.minY, pig.z - oz,
					world.getBlockId((int) Math.floor(pig.x), (int) Math.floor(pig.boundingBox.minY) - 1, (int) Math.floor(pig.z))));
		}
		say("#56 one-deep water: " + hurt + " of " + fallers.size() + " drops hurt (height:damage " + damage + ")");
		int sx = ox, sz = oz + 8;
		int blocks = world.getBlockId(sx, 100, sz) == Block.SAND.id ? 1 : 0;
		int items = 0;
		for (Object o : world.entities) {
			// The dropped sand is pushed out of the block it's in, and slides a few blocks.
			if (o instanceof ItemEntity && Math.abs(((ItemEntity) o).x - (sx + 0.5)) < 10 && Math.abs(((ItemEntity) o).z - (sz + 0.5)) < 10
					&& ((ItemEntity) o).stack.itemId == Block.SAND.id) items += ((ItemEntity) o).stack.count;
		}
		say("#67 falling sand into sand: " + (blocks + items) + " sand left of 2 (" + blocks + " block, " + items + " item)");
		say("done");
	}

	private static void load(ServerWorld world, int x0, int z0, int x1, int z1) {
		for (int x = x0 >> 4; x <= x1 >> 4; x++) {
			for (int z = z0 >> 4; z <= z1 >> 4; z++) world.chunkCache.loadChunk(x, z);
		}
	}

	/** #55: a lone door top on a chest, a redstone torch placed beside it. */
	private static void doorTop(ServerWorld world, int x, int z) {
		int y = 100;
		world.setBlock(x, y, z, Block.CHEST.id);
		((ChestBlockEntity) world.getBlockEntity(x, y, z)).setStack(0, new ItemStack(Item.DIAMOND));
		world.setBlockWithoutNotifyingNeighbors(x, y + 1, z, Block.DOOR.id, 8);
		world.setBlock(x + 1, y, z, Block.STONE.id);
		world.setBlock(x + 1, y + 1, z, Block.LIT_REDSTONE_TORCH.id, 5);
		say("#55 lone door top: the block below is " + world.getBlockId(x, y, z) + " (chest " + Block.CHEST.id + "), the top "
				+ world.getBlockId(x, y + 1, z));
	}

	/** #59: a pig turning its head to something 45 degrees above its eyes, six times. */
	private static void lookUp(ServerWorld world) {
		PigEntity pig = new PigEntity(world);
		pig.setPositionAndAnglesKeepPrevAngles(0.5, 100, 0.5, 0, 0);
		PigEntity target = new PigEntity(world);
		target.setPositionAndAnglesKeepPrevAngles(10.5, 110, 0.5, 0, 0);
		StringBuilder pitches = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			pig.lookAt(target, 10.0F, 40.0F);
			pitches.append(i == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.1f", pig.pitch));
		}
		say("#59 pitch looking 45 degrees up: " + pitches);
	}

	/**
	 * #64: pigs on grass with water beside their west or north side, east and south of 0, 0 and west and north of it,
	 * mirrored: whether each may spawn there.
	 */
	private static void spawnBesideWater(ServerWorld world) {
		StringBuilder seen = new StringBuilder();
		int[][] places = {{1000, 1000, -1, 0}, {-1000, -1000, -1, 0}, {1000, 1000, 0, -1}, {-1000, -1000, 0, -1}};
		String[] names = {"east of 0,0, water west", "west of 0,0, water west", "south of 0,0, water north", "north of 0,0, water north"};
		for (int i = 0; i < places.length; i++) {
			int[] p = places[i];
			int x = p[0] + 6 * i, z = p[1], y = 100;
			load(world, x - 2, z - 2, x + 2, z + 2);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) world.setBlockWithoutNotifyingNeighbors(x + dx, y - 1, z + dz, Block.GRASS_BLOCK.id, 0);
			}
			world.setBlockWithoutNotifyingNeighbors(x + p[2], y, z + p[3], Block.WATER.id, 0);
			PigEntity pig = new PigEntity(world);
			pig.setPositionAndAnglesKeepPrevAngles(x + 0.5, y, z + 0.5, 0, 0);
			seen.append(i == 0 ? "" : "; ").append(names[i]).append(": ").append(pig.canSpawn() ? "spawns" : "no");
		}
		say("#64 pigs beside water: " + seen);
	}

	/** #66: whether a squid may spawn in a cell of lava, and (the same test) one of water. */
	private static void squid(ServerWorld world, int x, int z) {
		int y = 100;
		world.setBlockWithoutNotifyingNeighbors(x, y, z, Block.LAVA.id, 0);
		world.setBlockWithoutNotifyingNeighbors(x + 2, y, z, Block.WATER.id, 0);
		boolean lava = NaturalSpawnerInvoker.oldfaithful$isValidSpawnPos(SpawnGroup.WATER_CREATURE, world, x, y, z);
		boolean water = NaturalSpawnerInvoker.oldfaithful$isValidSpawnPos(SpawnGroup.WATER_CREATURE, world, x + 2, y, z);
		say("#66 squid: in lava " + (lava ? "yes" : "no") + ", in water " + (water ? "yes" : "no"));
	}

	/** #56: pigs (1000 health) dropped from 12 to 31 blocks into a pool one block deep, two blocks apart, down its middle. */
	private static void falls(ServerWorld world, int x0, int z0) {
		int floor = 90;
		int n = HEIGHTS.length;
		// Seven wide: a pig wanders a little even while it falls, and one that reaches the rim isn't the water's test.
		for (int x = -4; x <= 2 * n + 3; x++) {
			for (int z = -4; z <= 4; z++) {
				boolean edge = x == -4 || x == 2 * n + 3 || Math.abs(z) == 4;
				world.setBlockWithoutNotifyingNeighbors(x0 + x, floor, z0 + z, Block.STONE.id, 0);
				world.setBlockWithoutNotifyingNeighbors(x0 + x, floor + 1, z0 + z, edge ? Block.STONE.id : Block.WATER.id, 0);
			}
		}
		for (int i = 0; i < n; i++) {
			PigEntity pig = new PigEntity(world);
			pig.setPositionAndAnglesKeepPrevAngles(x0 + 2 * i + 0.5, floor + 1 + HEIGHTS[i], z0 + 0.5, 0, 0);
			pig.health = 1000;
			world.spawnEntity(pig);
			fallers.add(pig);
		}
	}

	/**
	 * #67: a falling sand already some ticks into its fall (as one a piston pushes into a column is) in a cell that
	 * holds sand, on stone: two sand in all, as blocks or dropped.
	 */
	private static void fallingSand(ServerWorld world, int x, int z) {
		int y = 100;
		world.setBlockWithoutNotifyingNeighbors(x, y - 1, z, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(x, y, z, Block.SAND.id, 0);
		FallingBlockEntity sand = new FallingBlockEntity(world, x + 0.5, y + 0.5, z + 0.5, Block.SAND.id);
		sand.timeFalling = 5;
		world.spawnEntity(sand);
	}

	private static void say(String s) {
		System.out.println("[fixes-scenario] " + s);
	}
}
