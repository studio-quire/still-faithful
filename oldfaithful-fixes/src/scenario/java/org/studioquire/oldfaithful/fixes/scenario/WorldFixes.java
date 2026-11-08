package org.studioquire.oldfaithful.fixes.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
			squidSteps(world, ox + 20, oz - 20);
			squidInWater(world, ox + 20, oz - 30);
			falls(world, ox + 16, oz + 16);
			fallingSand(world, ox, oz + 8);
			stepsUnderRoof(world, ox + 70, oz + 40, true);
			stepsUnderRoof(world, ox + 70, oz + 50, false);
			againstWall(world);
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

	/** #76: a squid moved along a floor of redstone ore through its own move, 8 blocks, with water over it: the ore it lit. */
	private static void squidSteps(ServerWorld world, int x0, int z) {
		int floor = 100;
		for (int x = x0 - 1; x <= x0 + 12; x++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.setBlockWithoutNotifyingNeighbors(x, floor, z + dz, Block.REDSTONE_ORE.id, 0);
				world.setBlockWithoutNotifyingNeighbors(x, floor + 1, z + dz, Block.WATER.id, 0);
			}
		}
		SquidEntity squid = new SquidEntity(world);
		squid.setPositionAndAnglesKeepPrevAngles(x0 + 0.5, floor + 1, z + 0.5, 0, 0);
		for (int t = 0; t < 40; t++) squid.move(0.2, -0.05, 0.0);
		int lit = 0;
		for (int x = x0 - 1; x <= x0 + 12; x++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (world.getBlockId(x, floor, z + dz) == Block.LIT_REDSTONE_ORE.id) lit++;
			}
		}
		say("#76 squid along redstone ore, " + String.format(Locale.ROOT, "%.1f", squid.x - x0) + " blocks: " + lit + " lit");
	}

	/**
	 * #77: what a squid's test says at feet heights from the floor up a column of water 3 deep, in steps of 0.025, as long as
	 * its middle is under the surface; then with its middle over the surface, and out of the water altogether.
	 */
	private static void squidInWater(ServerWorld world, int x, int z) {
		int floor = 100;
		world.setBlockWithoutNotifyingNeighbors(x, floor, z, Block.STONE.id, 0);
		for (int y = floor + 1; y <= floor + 3; y++) world.setBlockWithoutNotifyingNeighbors(x, y, z, Block.WATER.id, 0);
		SquidEntity squid = new SquidEntity(world);
		int heights = 0, dry = 0;
		for (int i = 0; i < 96; i++) {
			squid.setPosition(x + 0.5, floor + 1 + i * 0.025, z + 0.5);
			heights++;
			if (!squid.isSubmergedInWater()) dry++;
		}
		squid.setPosition(x + 0.5, floor + 1 + 2.45, z + 0.5);
		boolean above = squid.isSubmergedInWater();
		squid.setPosition(x + 0.5, floor + 6, z + 0.5);
		boolean clear = squid.isSubmergedInWater();
		say("#77 squid's water test: " + dry + " of " + heights + " heights in the water dry; middle over the surface " + (above ? "wet" : "dry")
				+ ", in the air " + (clear ? "wet" : "dry"));
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

	/**
	 * #74, a mob: a zombie made to walk across a one-wide trench at walking speed through its own move, from 400 starts, with a
	 * roof 2 above the floor or none. Each walk counts as sunk if the box ever goes into a block.
	 */
	private static void stepsUnderRoof(ServerWorld world, int tx, int z0, boolean roof) {
		int floor = 100;
		for (int x = tx - 8; x <= tx + 8; x++) {
			for (int z = z0 - 2; z <= z0 + 2; z++) {
				world.setBlockWithoutNotifyingNeighbors(x, floor - 1, z, Block.STONE.id, 0);
				world.setBlockWithoutNotifyingNeighbors(x, floor, z, x == tx ? 0 : Block.STONE.id, 0);
				if (roof) world.setBlockWithoutNotifyingNeighbors(x, floor + 3, z, Block.STONE.id, 0);
			}
		}
		int sunk = 0, crossed = 0, walks = 400;
		for (int i = 0; i < walks; i++) {
			ZombieEntity zombie = new ZombieEntity(world);
			zombie.setPositionAndAnglesKeepPrevAngles(tx - 3.0 + i * 0.0025, floor + 1, z0 + 0.5, 0, 0);
			zombie.onGround = true;
			double down = -0.0784;
			boolean in = false;
			for (int t = 0; t < 40 && !in; t++) {
				zombie.move(0.2159, down, 0.0);
				down = zombie.onGround ? -0.0784 : (down - 0.08) * 0.98;
				in = !world.getEntityCollisions(zombie, zombie.boundingBox.contract(1.0E-6, 1.0E-6, 1.0E-6)).isEmpty();
			}
			if (in) sunk++;
			if (zombie.x > tx + 1.5 && zombie.boundingBox.minY == floor + 1) crossed++;
		}
		say("#74 zombie across a trench " + (roof ? "under a roof" : "in the open") + ": " + sunk + " of " + walks + " sank, " + crossed + " crossed");
	}

	/**
	 * #75, a mob: a zombie walked across x 1024 to a wall a block past it until touching, written and read as a chunk's load
	 * does, from 1000 starts. Counts those left inside the wall, and how far the next step then takes the deepest of them.
	 */
	private static void againstWall(ServerWorld world) {
		int wall = 1025, z0 = 40, floor = 100;
		load(world, wall - 16, z0 - 16, wall + 16, z0 + 16);
		for (int x = wall - 8; x <= wall; x++) {
			for (int z = z0 - 6; z <= z0 + 6; z++) {
				world.setBlockWithoutNotifyingNeighbors(x, floor, z, Block.STONE.id, 0);
				for (int y = floor + 1; y <= floor + 3; y++) world.setBlockWithoutNotifyingNeighbors(x, y, z, x == wall ? Block.STONE.id : 0, 0);
			}
		}
		java.util.Random random = new java.util.Random(75);
		int inside = 0, walks = 1000;
		double deepest = 0.0;
		for (int i = 0; i < walks; i++) {
			ZombieEntity zombie = new ZombieEntity(world);
			zombie.setPositionAndAnglesKeepPrevAngles(wall - 4.0 - random.nextDouble(), floor + 1, z0 - 4.0 + random.nextDouble() * 8.0, 0, 0);
			zombie.onGround = true;
			for (int t = 0; t < 40 && !zombie.horizontalCollision; t++) zombie.move(0.1 + random.nextDouble() * 0.15, -0.0784, 0.0);
			NbtCompound nbt = new NbtCompound();
			zombie.write(nbt);
			zombie.read(nbt);
			if (world.getEntityCollisions(zombie, zombie.boundingBox).isEmpty()) continue;
			inside++;
			zombie.move(0.2, -0.0784, 0.0);
			deepest = Math.max(deepest, zombie.boundingBox.maxX - wall);
		}
		say("#75 zombie read beside a wall: " + inside + " of " + walks + " inside it, the next step " + String.format(Locale.ROOT, "%.3f", deepest) + " in");
	}

	private static void say(String s) {
		System.out.println("[fixes-scenario] " + s);
	}
}
