package org.studioquire.oldfaithful.saves.scenario;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.NoteBlockBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorld;
import net.minecraft.world.chunk.Chunk;

/**
 * Test-only (ServerSaveTest): on the first overworld tick, for each block listed in
 * {@code -Doldfaithful.saves.scenario=x,y,z;x,y,z;...}, prints what block entity the loaded chunk holds there (read
 * from the chunk's map, so vanilla's lazy creation can't stand in for a missing one), uses the block (an item into a
 * container, a record into a jukebox, a note block played), then breaks it with setBlock, as a player, an explosion or
 * water would, and prints the items that fell out. Without a block entity, vanilla's chest, furnace, dispenser and
 * jukebox throw in onBreak and the server stops.
 */
public final class Scenario {
	private static boolean done;
	/** #65: {@code -Doldfaithful.saves.scenario.spawner=x,y,z}, a spawner that ticks as if a player stood beside it. */
	private static final int[] SPAWNER = parse(System.getProperty("oldfaithful.saves.scenario.spawner", ""));
	private static int after = -1;

	private Scenario() {
	}

	public static void tick(ServerWorld world) {
		if (world.dimension.id == 0 && SPAWNER != null && after >= 0 && ++after == 40) {
			// Forty ticks of a spawner whose delay was 0: it has tried to spawn; an id that isn't a mob would have thrown.
			BlockEntity be = world.getBlockEntity(SPAWNER[0], SPAWNER[1], SPAWNER[2]);
			NbtCompound nbt = new NbtCompound();
			if (be != null) be.writeNbt(nbt);
			say("spawner at " + SPAWNER[0] + " " + SPAWNER[1] + " " + SPAWNER[2] + " after 40 ticks: " + kind(be) + " of " + nbt.getString("EntityId"));
		}
		String spec = System.getProperty("oldfaithful.saves.scenario", "");
		if (done || spec.isEmpty() || world.dimension.id != 0) return;
		done = true;
		List<int[]> blocks = new ArrayList<>();
		for (String s : spec.split(";")) {
			String[] p = s.split(",");
			blocks.add(new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])});
		}
		for (int[] b : blocks) {
			Chunk chunk = world.getChunk(b[0] >> 4, b[2] >> 4);
			BlockEntity be = (BlockEntity) chunk.blockEntities.get(new BlockPos(b[0] & 15, b[1], b[2] & 15));
			say("at " + b[0] + " " + b[1] + " " + b[2] + ": block " + world.getBlockId(b[0], b[1], b[2]) + " meta "
					+ world.getBlockMeta(b[0], b[1], b[2]) + " entity " + kind(be));
		}
		for (int[] b : blocks) {
			int id = world.getBlockId(b[0], b[1], b[2]);
			if (id == Block.JUKEBOX.id) {
				if (world.getBlockMeta(b[0], b[1], b[2]) == 0) ((JukeboxBlock) Block.JUKEBOX).insertRecord(world, b[0], b[1], b[2], Item.RECORD_THIRTEEN.id);
			} else if (id == Block.NOTE_BLOCK.id) {
				Block.NOTE_BLOCK.onUse(world, b[0], b[1], b[2], null);
			} else if (world.getBlockEntity(b[0], b[1], b[2]) instanceof Inventory) {
				((Inventory) world.getBlockEntity(b[0], b[1], b[2])).setStack(0, new ItemStack(Item.DIAMOND.id, 1, 0));
			}
		}
		int before = drops(world).size();
		for (int[] b : blocks) world.setBlock(b[0], b[1], b[2], 0);
		List<Integer> dropped = drops(world);
		say("broke " + blocks.size() + " blocks; dropped " + dropped.subList(before, dropped.size()));
		say("done");
		after = 0;
	}

	/**
	 * #78, with {@code -Doldfaithful.saves.scenario.beingbuilt=true}: called at the start of a new overworld's spawn
	 * search, still inside World's constructor, so ServerWorld's own fields are null. In place of the search's chance of
	 * about 1 in 900 of decorating a spring beside a plant: a rose with flowing water ticked beside it, which breaks it at
	 * once. Without a fix the server stops here.
	 */
	public static void beingBuilt(ServerWorld world) {
		if (!Boolean.getBoolean("oldfaithful.saves.scenario.beingbuilt") || world.dimension.id != 0) return;
		int x = 8, y = 120, z = 8;
		world.eventProcessingEnabled = true;
		world.getChunk(x >> 4, z >> 4);
		world.setBlock(x, y - 1, z, Block.DIRT.id);
		world.setBlock(x + 1, y - 1, z, Block.STONE.id);
		world.setBlock(x + 2, y, z, Block.STONE.id);
		world.setBlock(x + 1, y, z - 1, Block.STONE.id);
		world.setBlock(x + 1, y, z + 1, Block.STONE.id);
		world.setBlock(x, y, z, Block.ROSE.id);
		world.setBlock(x + 1, y, z, Block.FLOWING_WATER.id);
		world.instantBlockUpdateEnabled = true;
		Block.FLOWING_WATER.onTick(world, x + 1, y, z, world.random);
		world.instantBlockUpdateEnabled = false;
		world.eventProcessingEnabled = false;
		int roses = 0, alive = 0;
		for (Object o : world.entities) {
			if (o instanceof ItemEntity && ((ItemEntity) o).stack.itemId == Block.ROSE.id) {
				roses++;
				if (!((ItemEntity) o).dead) alive++;
			}
		}
		say("being built: " + roses + " rose dropped, " + alive + " alive, water where it stood " + (world.getBlockId(x, y, z) == Block.FLOWING_WATER.id));
	}

	public static boolean isTestSpawner(BlockEntity be) {
		return SPAWNER != null && be.x == SPAWNER[0] && be.y == SPAWNER[1] && be.z == SPAWNER[2];
	}

	private static int[] parse(String s) {
		if (s.isEmpty()) return null;
		String[] p = s.split(",");
		return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
	}

	/** Item ids of the item entities above y 100 (where the test's blocks are, and nothing else drops). */
	private static List<Integer> drops(ServerWorld world) {
		List<Integer> out = new ArrayList<>();
		for (Object e : world.entities) {
			if (e instanceof ItemEntity && ((ItemEntity) e).y > 100) out.add(((ItemEntity) e).stack.itemId);
		}
		return out;
	}

	/** The class's name as the test knows it (at runtime the game's classes have intermediary names). */
	private static String kind(BlockEntity be) {
		if (be == null) return "none";
		if (be instanceof ChestBlockEntity) return "ChestBlockEntity";
		if (be instanceof FurnaceBlockEntity) return "FurnaceBlockEntity";
		if (be instanceof DispenserBlockEntity) return "DispenserBlockEntity";
		if (be instanceof JukeboxBlockEntity) return "JukeboxBlockEntity";
		if (be instanceof NoteBlockBlockEntity) return "NoteBlockBlockEntity";
		if (be instanceof SignBlockEntity) return "SignBlockEntity";
		if (be instanceof MobSpawnerBlockEntity) return "MobSpawnerBlockEntity";
		return "other";
	}

	private static void say(String s) {
		System.out.println("[saves-scenario] " + s);
	}
}
