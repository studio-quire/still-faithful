package org.studioquire.oldfaithful.polish.selftest;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.InteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.BackgroundSaves;
import org.studioquire.oldfaithful.polish.KeptAnimals;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.scenario.CartScenes;
import org.studioquire.oldfaithful.polish.scenario.Tracks;
import org.studioquire.oldfaithful.polish.selftest.mixin.ChunkCacheStorage;
import org.studioquire.oldfaithful.polish.selftest.mixin.LivingEntityInvoker;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;

/**
 * Still Faithful's changes in a running single-player world, from the harness's start-up test
 * (`:harness:clientStartupTestStill`), after Old Faithful's fixes. Each scene is set up well above the ground, away
 * from the player and from FixesCheck's (which sit 12 blocks out). Every check expects the change where its switch was
 * on at start and beta's behaviour where it was off, so a run with every switch off checks the other side.
 * What's only seen (the bow, dropped item sizes, the boat's feel) is the user's.
 */
public final class StillCheck implements WorldCheck {
	private static final int Y = 110;
	private static final int BOATS_SETTLED = 20;
	private static final int CRASH_LOOK = BOATS_SETTLED + 5;
	private static final int COAST_LOOK = BOATS_SETTLED + 60;

	private SelfTest results;
	private World world;
	private int ticks;
	private int bx, bz;
	private BoatEntity slider, crasher, coaster;
	private double coastStart;
	private CartScenes eastWest, northSouth;
	private boolean cartsTold;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		bx = (int) Math.floor(mc.player.x);
		bz = (int) Math.floor(mc.player.z);
		for (int x = bx - 48; x <= bx + 48; x += 16) {
			for (int z = bz - 48; z <= bz + 48; z += 16) world.getBlockId(x, Y, z);
		}
		// Every chunk the scenes need is loaded before any is built: loading a chunk decorates its neighbours (a lake
		// can be carved into whatever stands in the sky there).
		loadCartChunks();
		stairs();
		leaves(mc);
		glowstone(mc);
		lava();
		water();
		animals(mc);
		boats();
		carts();
	}

	@Override
	public boolean tick(Minecraft mc) {
		ticks++;
		eastWest.tick();
		northSouth.tick();
		if (eastWest.done() && northSouth.done() && ticks < COAST_LOOK && !cartsTold) {
			cartsTold = true;
			boolean on = PolishConfig.applied("minecarts.cartCollisions");
			for (CartScenes scenes : new CartScenes[] {eastWest, northSouth}) {
				for (CartScenes.Check c : scenes.checks(on)) results.check("still #31: " + c.text, c.ok);
			}
		}
		if (ticks == BOATS_SETTLED) {
			// Sliding along a wall: fast along it, barely into it. Beta breaks the boat; sturdy boats don't.
			slider.velocityX = 0.35;
			slider.velocityZ = 0.05;
			// Into a wall at 45 degrees, fast: a real crash, which breaks it either way.
			crasher.velocityX = 0.3;
			crasher.velocityZ = 0.3;
			coaster.velocityX = 0.4;
			coaster.velocityZ = 0;
			coastStart = coaster.x;
		}
		if (ticks == CRASH_LOOK) {
			boolean sturdy = PolishConfig.applied("boats.sturdy");
			results.check("still #12: a boat sliding along a wall at 0.35 a tick " + (slider.dead ? "broke" : "held") + " ("
					+ (sturdy ? "sturdy: holds" : "beta: breaks") + ")", slider.dead != sturdy);
			results.check("still #12: a boat driven into a wall at 45 degrees, 0.42 a tick, broke, as in beta", crasher.dead);
		}
		if (ticks == COAST_LOOK - 1) savedInBackground(mc);
		if (ticks == COAST_LOOK) {
			boolean handling = PolishConfig.applied("boats.handling");
			double went = coaster.x - coastStart;
			double speed = Math.hypot(coaster.velocityX, coaster.velocityZ);
			results.check(String.format("still #12: an unpaddled boat from top speed went %.1f blocks in 60 ticks, now %.3f a tick (%s)", went,
					speed, handling ? "handling: stops within about 8" : "beta: still gliding, about 18"),
					handling ? went < 10 && speed < 0.03 : went > 14 && speed > 0.15);
			results.check("still #12: the coasting boat didn't break", !coaster.dead);
			return true;
		}
		return false;
	}

	/**
	 * #14: a block changed, then a full save (as leaving the world does): the chunk on disk, read back with the game's
	 * own reader, has it; and the writer thread did the writing (autosaves and this save), where the switch is on.
	 */
	private void savedInBackground(Minecraft mc) {
		boolean on = PolishConfig.applied("saves.backgroundAutosave");
		int x = bx + 40, y = Y, z = bz + 40;
		world.setBlock(x, y, z, Block.DIAMOND_BLOCK.id);
		long before = BackgroundSaves.written;
		world.saveWithLoadingDisplay(true, null);
		long after = BackgroundSaves.written;
		int onDisk = -1;
		try {
			java.io.File dir = ((org.studioquire.oldfaithful.saves.RegionStorageAccess) ((ChunkCacheStorage) world.getChunkSource()).oldfaithful$storage()).oldfaithful$dir();
			java.io.DataInputStream in = net.minecraft.world.chunk.storage.RegionIo.getChunkInputStream(dir, x >> 4, z >> 4);
			NbtCompound level = net.minecraft.nbt.NbtIo.read(in).getCompound("Level");
			onDisk = level.getByteArray("Blocks")[(x & 15) << 11 | (z & 15) << 7 | y] & 0xFF;
		} catch (Exception e) {
			results.fail("still #14: reading the saved chunk back", e);
		}
		results.check("still #14: a full save wrote the changed chunk (block on disk " + onDisk + ", a diamond block is "
				+ Block.DIAMOND_BLOCK.id + "); the writer thread wrote " + after + " saves so far, " + (after - before)
				+ " in this one" + (on ? "" : " (beta: none)"), onDisk == Block.DIAMOND_BLOCK.id && (on ? after > before && before > 0 : after == 0));
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) world.setBlock(x, y, z, id);
			}
		}
	}

	private List<?> items(int x, int y, int z) {
		return world.collectEntitiesByClass(ItemEntity.class, Box.create(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2));
	}

	private String dropped(int x, int y, int z) {
		StringBuilder s = new StringBuilder();
		for (Object o : items(x, y, z)) {
			ItemStack stack = ((ItemEntity) o).stack;
			s.append(s.length() == 0 ? "" : ", ").append(stack.count).append(" x ").append(stack.itemId);
		}
		return s.length() == 0 ? "nothing" : s.toString();
	}

	private boolean droppedOnly(int x, int y, int z, int itemId) {
		List<?> found = items(x, y, z);
		if (found.isEmpty()) return false;
		for (Object o : found) {
			if (((ItemEntity) o).stack.itemId != itemId) return false;
		}
		return true;
	}

	/** #13: wooden and cobblestone stairs broken as a player's break drops them (Block.dropStacks). */
	private void stairs() {
		boolean on = PolishConfig.applied("blocks.stairsDropStairs");
		int[][] kinds = {{Block.WOODEN_STAIRS.id, Block.PLANKS.id}, {Block.COBBLESTONE_STAIRS.id, Block.COBBLESTONE.id}};
		for (int i = 0; i < kinds.length; i++) {
			int x = bx + 24, y = Y, z = bz - 8 + i * 6;
			world.setBlock(x, y - 1, z, Block.STONE.id);
			world.setBlock(x, y, z, kinds[i][0], 0);
			Block.BLOCKS[kinds[i][0]].dropStacks(world, x, y, z, 0);
			world.setBlock(x, y, z, 0);
			int want = on ? kinds[i][0] : kinds[i][1];
			results.check("still #13: " + Block.BLOCKS[kinds[i][0]].getTranslationKey() + " dropped " + dropped(x, y, z) + " (expected "
					+ want + ")", droppedOnly(x, y, z, want));
		}
	}

	/** #24: leaves placed with the item, no log near, ticked as a random tick would; natural ones beside them. */
	private void leaves(Minecraft mc) {
		boolean on = PolishConfig.applied("leaves.placedStay");
		int x = bx + 24, y = Y, z = bz + 6;
		world.setBlock(x, y - 1, z, Block.STONE.id);
		new ItemStack(Block.LEAVES, 1, 2).useOnBlock(mc.player, world, x, y - 1, z, 1);
		int meta = world.getBlockMeta(x, y, z);
		results.check("still #24: birch leaves placed with the item: data " + meta + " (" + (on ? "kind 2 and the kept mark 4" : "beta: 2 | 8, marked to decay")
				+ ")", world.getBlockId(x, y, z) == Block.LEAVES.id && meta == (on ? 6 : 10));
		for (int i = 0; i < 4; i++) Block.LEAVES.onTick(world, x, y, z, world.random);
		results.check("still #24: the placed leaves, ticked with no log near, " + (world.getBlockId(x, y, z) == Block.LEAVES.id ? "stayed" : "decayed")
				+ " (" + (on ? "stay" : "beta: decay") + ")", (world.getBlockId(x, y, z) == Block.LEAVES.id) == on);
		// A log broken beside placed leaves marks them (bit 8) as beta does; they still stay.
		int px = x, pz = z + 3;
		world.setBlock(px, y - 1, pz, Block.STONE.id);
		new ItemStack(Block.LEAVES, 1, 0).useOnBlock(mc.player, world, px, y - 1, pz, 1);
		world.setBlockMetaWithoutNotifyingNeighbors(px, y, pz, world.getBlockMeta(px, y, pz) | 8);
		for (int i = 0; i < 4; i++) Block.LEAVES.onTick(world, px, y, pz, world.random);
		results.check("still #24: placed leaves marked by a broken log beside them " + (world.getBlockId(px, y, pz) == Block.LEAVES.id ? "stayed" : "decayed"),
				(world.getBlockId(px, y, pz) == Block.LEAVES.id) == on);
		// Natural leaves (a tree's, marked by a broken log) decay as in beta.
		int nx = x, nz = z + 6;
		world.setBlock(nx, y - 1, nz, Block.STONE.id);
		world.setBlock(nx, y, nz, Block.LEAVES.id, 8);
		Block.LEAVES.onTick(world, nx, y, nz, world.random);
		results.check("still #24: a tree's leaves with no log near decay, as in beta", world.getBlockId(nx, y, nz) == 0);
	}

	/** Glowstone mined by hand, tick by tick as holding the button does: what drops, and how long it took. */
	private void glowstone(Minecraft mc) {
		boolean on = PolishConfig.applied("glowstone.anyTool");
		PlayerInventory inventory = mc.player.inventory;
		inventory.main[inventory.selectedSlot] = null;
		int x = bx + 30, y = Y, z = bz;
		world.setBlock(x, y - 1, z, Block.STONE.id);
		world.setBlock(x, y, z, Block.GLOWSTONE.id);
		InteractionManager im = mc.interactionManager;
		im.cancelBlockBreaking();
		int calls = 0;
		while (world.getBlockId(x, y, z) != 0 && calls < 200) {
			im.processBlockBreakingAction(x, y, z, 1);
			calls++;
		}
		boolean dust = droppedOnly(x, y, z, Item.GLOWSTONE_DUST.id);
		results.check("still: glowstone mined by hand in " + calls + " ticks, dropped " + dropped(x, y, z) + " ("
				+ (on ? "dust, in about 10 ticks as in 1.0" : "beta: nothing, in about 31") + ")",
				on ? dust && calls <= 17 : items(x, y, z).isEmpty() && calls >= 28);
	}

	/** A Random whose every nextInt answers 1: the lava check's "refuse to thin" draw, every time. */
	private static final class Refusing extends Random {
		@Override
		public int nextInt(int bound) {
			return 1;
		}
	}

	/**
	 * #20, lava: a flowing lava cell of level 2 next to one of level 4 in a closed trough, its source gone; its tick
	 * made with beta's refusal drawn. Beta keeps it at 2 and schedules nothing (so it lingers); here it thins.
	 */
	private void lava() {
		boolean on = PolishConfig.applied("liquids.lavaThins");
		int x = bx - 24, y = Y, z = bz;
		fill(x - 1, y - 1, z - 1, x + 2, y + 1, z + 1, Block.STONE.id);
		world.setBlockWithoutNotifyingNeighbors(x, y, z, Block.FLOWING_LAVA.id, 2);
		world.setBlockWithoutNotifyingNeighbors(x + 1, y, z, Block.FLOWING_LAVA.id, 4);
		world.setBlockWithoutNotifyingNeighbors(x, y + 1, z, 0, 0);
		world.setBlockWithoutNotifyingNeighbors(x + 1, y + 1, z, 0, 0);
		Block.FLOWING_LAVA.onTick(world, x, y, z, new Refusing());
		int level = world.getBlockId(x, y, z) == Block.FLOWING_LAVA.id ? world.getBlockMeta(x, y, z) : -1;
		results.check("still #20: cut-off lava at level 2 beside level 4, refusal drawn: now level " + level + " ("
				+ (on ? "thins to 6" : "beta: stays at 2") + ")", level == (on ? 6 : 2));
	}

	/**
	 * #20, water: flowing water between two sources, over still water. Beta reads its own level (1) for the check
	 * meant for the water below, so it stays flowing; here it becomes a source.
	 */
	private void water() {
		boolean on = PolishConfig.applied("liquids.waterSources");
		int x = bx - 24, y = Y, z = bz + 8;
		fill(x - 2, y - 2, z - 1, x + 2, y + 1, z + 1, Block.STONE.id);
		for (int dx = -1; dx <= 1; dx++) {
			world.setBlockWithoutNotifyingNeighbors(x + dx, y - 1, z, Block.WATER.id, 0);
			world.setBlockWithoutNotifyingNeighbors(x + dx, y, z, dx == 0 ? Block.FLOWING_WATER.id : Block.WATER.id, dx == 0 ? 1 : 0);
			world.setBlockWithoutNotifyingNeighbors(x + dx, y + 1, z, 0, 0);
		}
		Block.FLOWING_WATER.onTick(world, x, y, z, world.random);
		int meta = world.getBlockMeta(x, y, z);
		results.check("still #20: water between two sources over still water: level " + meta + " (" + (on ? "a source, 0" : "beta: 1") + ")",
				meta == (on ? 0 : 1));
	}

	/** #4: which animals the despawn check removes with the player 200 blocks away, and the mark saved and read back. */
	private void animals(Minecraft mc) {
		boolean on = PolishConfig.applied("animals.keepTended");
		PlayerEntity player = mc.player;
		int x = bx - 30, y = Y, z = bz - 20;
		fill(x - 6, y - 1, z - 6, x + 6, y - 1, z + 6, Block.STONE.id);
		CowEntity wild = new CowEntity(world);
		CowEntity hit = new CowEntity(world);
		SheepEntity sheared = new SheepEntity(world);
		PigEntity saddled = new PigEntity(world);
		ChickenEntity chicken = new ChickenEntity(world);
		Object[] all = {wild, hit, sheared, saddled, chicken};
		for (int i = 0; i < all.length; i++) {
			net.minecraft.entity.LivingEntity e = (net.minecraft.entity.LivingEntity) all[i];
			e.setPositionAndAnglesKeepPrevAngles(x - 4 + i * 2 + 0.5, y, z + 0.5, 0.0F, 0.0F);
			world.spawnEntity(e);
		}
		hit.damage(player, 1);
		PlayerInventory inventory = player.inventory;
		inventory.main[inventory.selectedSlot] = new ItemStack(Item.SHEARS);
		player.interact(sheared);
		inventory.main[inventory.selectedSlot] = new ItemStack(Item.SADDLE);
		player.interact(saddled);
		inventory.main[inventory.selectedSlot] = null;
		results.check("still #4: the sheep was sheared and the pig saddled", sheared.isSheared() && saddled.isSaddled());

		// The chicken is untouched but read back from a save that carries the mark, as a kept one's would.
		NbtCompound saved = new NbtCompound();
		sheared.write(saved);
		results.check("still #4: a kept sheep is saved with " + KeptAnimals.TAG + (on ? "" : " (beta: not)"), saved.getBoolean(KeptAnimals.TAG) == on);
		NbtCompound chickenSave = new NbtCompound();
		chicken.write(chickenSave);
		chickenSave.putBoolean(KeptAnimals.TAG, true);
		chicken.read(chickenSave);

		double px = player.x, pz = player.z;
		player.x += 200;
		player.z += 200;
		try {
			for (Object o : all) {
				for (int i = 0; i < 20 && !((net.minecraft.entity.Entity) o).dead; i++) ((LivingEntityInvoker) o).oldfaithful$tryDespawn();
			}
		} finally {
			player.x = px;
			player.z = pz;
		}
		results.check("still #4: an untouched cow 200 blocks from the player despawned, as in beta", wild.dead);
		results.check("still #4: a cow the player hit despawned (hitting doesn't keep, the user's call)", hit.dead);
		results.check("still #4: a sheared sheep " + (sheared.dead ? "despawned" : "stayed"), sheared.dead != on);
		results.check("still #4: a saddled pig " + (saddled.dead ? "despawned" : "stayed"), saddled.dead != on);
		results.check("still #4: a chicken loaded with the mark " + (chicken.dead ? "despawned" : "stayed"), chicken.dead != on);
	}

	/**
	 * #31: carts meeting carts (CartScenes, shared with the real server's test), on an east-west and a north-south axis,
	 * higher than the other scenes (above hills animals could walk from), the north-south ones well away from x = 0.
	 */
	private void carts() {
		eastWest = new CartScenes(world, cartsEx(), Y + 10, cartsEz(), false);
		northSouth = new CartScenes(world, cartsNx(), Y + 10, cartsNz(), true);
	}

	// Both axes' scenes side by side, well away from the player's scenes and from x = 0 (where beta's slip doesn't fire).
	private int cartsEx() {
		return cartsNx();
	}

	private int cartsEz() {
		return bz - 50;
	}

	private int cartsNx() {
		return Tracks.awayFromZero(bx >= 0 ? bx + 70 : bx - 70 - CartScenes.WIDTH, CartScenes.WIDTH);
	}

	private int cartsNz() {
		return bz - 12;
	}

	/** An entity is ticked only with every chunk within 32 blocks of it loaded: those of both cart scenes and more. */
	private void loadCartChunks() {
		int x0 = Math.min(cartsEx(), cartsNx()) - 48, x1 = Math.max(cartsEx() + CartScenes.LENGTH, cartsNx() + CartScenes.WIDTH) + 48;
		int z0 = Math.min(cartsEz(), cartsNz()) - 48, z1 = Math.max(cartsEz() + CartScenes.WIDTH, cartsNz() + CartScenes.LENGTH) + 48;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) world.getBlockId(cx << 4, Y + 10, cz << 4);
		}
	}

	/**
	 * #12: three boats on still water in walled basins; given their speeds once they've settled (tick()). The basins
	 * are roofed: still water under open sky freezes in a cold biome, and ice stopped the coasting boat in some worlds.
	 */
	private void boats() {
		int y = Y;
		// The sliding and crashing boats: a basin 30 long, 4 wide, its +z wall the one they meet.
		for (int b = 0; b < 2; b++) {
			int x0 = bx - 12, z0 = bz + 28 + b * 8;
			fill(x0 - 1, y - 2, z0 - 1, x0 + 30, y + 1, z0 + 4, Block.STONE.id);
			fill(x0, y - 1, z0, x0 + 29, y - 1, z0 + 3, Block.WATER.id);
			fill(x0, y, z0, x0 + 29, y + 1, z0 + 3, 0);
			fill(x0 - 1, y + 2, z0 - 1, x0 + 30, y + 2, z0 + 4, Block.STONE.id);
			// Its box (1.5 wide) a hair short of the wall at z0 + 4.
			BoatEntity boat = new BoatEntity(world, x0 + 2.5, y - 0.3, z0 + 4 - 0.76);
			world.spawnEntity(boat);
			if (b == 0) slider = boat;
			else crasher = boat;
		}
		// The coasting boat: a basin 40 long.
		int x0 = bx - 20, z0 = bz - 40;
		fill(x0 - 1, y - 2, z0 - 1, x0 + 40, y + 1, z0 + 4, Block.STONE.id);
		fill(x0, y - 1, z0, x0 + 39, y - 1, z0 + 3, Block.WATER.id);
		fill(x0, y, z0, x0 + 39, y + 1, z0 + 3, 0);
		fill(x0 - 1, y + 2, z0 - 1, x0 + 40, y + 2, z0 + 4, Block.STONE.id);
		coaster = new BoatEntity(world, x0 + 2.5, y - 0.3, z0 + 2.0);
		world.spawnEntity(coaster);
	}
}
