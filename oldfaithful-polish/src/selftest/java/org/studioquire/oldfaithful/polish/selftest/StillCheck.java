package org.studioquire.oldfaithful.polish.selftest;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
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
import org.studioquire.oldfaithful.polish.BoatSteering;
import org.studioquire.oldfaithful.polish.ChestLids;
import org.studioquire.oldfaithful.polish.Chests;
import org.studioquire.oldfaithful.polish.KeptAnimals;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.scenario.CartScenes;
import org.studioquire.oldfaithful.polish.scenario.Tracks;
import org.studioquire.oldfaithful.polish.selftest.mixin.ChunkCacheStorage;
import org.studioquire.oldfaithful.polish.selftest.mixin.FurnaceFuel;
import org.studioquire.oldfaithful.polish.selftest.mixin.LivingEntityInvoker;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;

/**
 * Still Faithful's changes in a running single-player world, from the harness's start-up test
 * (`:harness:clientStartupTestStill`), after Old Faithful's fixes. Each scene is set up well above the ground, away
 * from the player. FixesCheck's scenes were laid out round where the player stood before it moved them to its boat, so
 * here they lie some 24 blocks east and south, among these: its slimes' floor a block from two chest scenes, its rail
 * through glowstone's cell. It removes its slimes once counted, as a mob in a cell stops a chest being placed there.
 * Every check expects the change where its switch was on at start and beta's behaviour where it was off, so a run with
 * every switch off checks the other side.
 * What's only seen (the bow, dropped item sizes, the boat's feel) is checked by eye, in play.
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
		switches();
		stairs();
		fuel();
		leaves(mc);
		chests(mc);
		chestPairs(mc);
		chestLids(mc);
		glowstone(mc);
		obsidian(mc);
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
			// Steeply into a wall, fast: a real crash, which breaks it either way. Steeply enough that a boat Boat Steering
			// holds to its top speed (0.325) still hits it at over 0.25 and slides on at over 0.15 (at 45 degrees it
			// couldn't: 0.23 into the wall).
			crasher.velocityX = 0.2;
			crasher.velocityZ = 0.32;
			coaster.velocityX = 0.4;
			coaster.velocityZ = 0;
			coastStart = coaster.x;
		}
		if (ticks == CRASH_LOOK) {
			boolean sturdy = PolishConfig.applied("boats.sturdy");
			results.check("still #12: a boat sliding along a wall at 0.35 a tick " + (slider.dead ? "broke" : "held") + " ("
					+ (sturdy ? "sturdy: holds" : "beta: breaks") + ")", slider.dead != sturdy);
			results.check("still #12: a boat driven steeply into a wall, 0.32 a tick into it and 0.2 along, broke, as in beta", crasher.dead);
		}
		if (ticks == COAST_LOOK - 1) savedInBackground(mc);
		if (ticks == COAST_LOOK) {
			// Boat Steering's momentum, where it's on, moves every boat in water that a player isn't paddling as beta's,
			// in place of handling's coasting, held to its top speed: from 0.4, min(0.4 * 0.9, 0.325) / (1 - 0.9) = 3.25.
			boolean handling = PolishConfig.applied("boats.handling"), steering = PolishConfig.applied("boats.steering");
			double went = coaster.x - coastStart;
			double speed = Math.hypot(coaster.velocityX, coaster.velocityZ);
			double steered = Math.min(0.4 * BoatSteering.MOMENTUM, BoatSteering.TOP_SPEED) / (1 - BoatSteering.MOMENTUM);
			results.check(String.format(Locale.ROOT, "still #12: an unpaddled boat from top speed went %.1f blocks in 60 ticks, now %.3f a tick (%s)", went,
					speed, steering ? String.format(Locale.ROOT, "steering: stops within about %.2f", steered) : handling ? "handling: stops within about 8" : "beta: still gliding, about 18"),
					steering ? Math.abs(went - steered) < 0.25 && speed < 0.01 : handling ? went < 10 && speed < 0.03 : went > 14 && speed > 0.15);
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

	/**
	 * The test runs with every switch on, or with every one off (-Pstill.beta, whose list in harness/build.gradle is
	 * kept by hand): a switch missing from that list stays on, and its check would pass on the wrong side unnoticed.
	 */
	private void switches() {
		List<String> on = new java.util.ArrayList<>(), off = new java.util.ArrayList<>();
		for (String key : PolishConfig.keys()) (PolishConfig.applied(key) ? on : off).add(key);
		results.check("still: every switch on or every one off (on: " + on + ", off: " + off + ")", on.isEmpty() || off.isEmpty());
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

	/**
	 * Wooden fuel: what the furnace's own table says each wooden thing burns for (later versions' times; 0, beta's, where
	 * the switch is off), and that beta's own fuels and non-fuels stay as they were either way.
	 */
	private void fuel() {
		boolean on = PolishConfig.applied("furnace.woodenFuel");
		FurnaceFuel furnace = (FurnaceFuel) new net.minecraft.block.entity.FurnaceBlockEntity();
		Object[][] added = {
				{Item.WOODEN_SWORD, 200}, {Item.WOODEN_SHOVEL, 200}, {Item.WOODEN_PICKAXE, 200}, {Item.WOODEN_AXE, 200}, {Item.WOODEN_HOE, 200},
				{Item.SIGN, 200}, {Item.WOODEN_DOOR, 200}, {Item.BOW, 300}, {Item.FISHING_ROD, 300}, {Block.LADDER, 300}, {Item.BOAT, 1200},
				{Item.BOWL, 100}, {new ItemStack(Block.SLAB, 1, 2), 150},
		};
		Object[][] beta = {
				{Block.PLANKS, 300}, {Block.LOG, 300}, {Block.CHEST, 300}, {Block.FENCE, 300}, {Block.TRAPDOOR, 300}, {Item.STICK, 100},
				{Block.SAPLING, 100}, {Item.COAL, 1600}, {new ItemStack(Block.SLAB, 1, 0), 0}, {Block.COBBLESTONE, 0}, {Item.STONE_SWORD, 0},
				{Item.PAPER, 0},
		};
		StringBuilder wrong = new StringBuilder();
		for (Object[][] list : new Object[][][] {added, beta}) {
			for (Object[] row : list) {
				ItemStack stack = row[0] instanceof ItemStack ? (ItemStack) row[0]
						: row[0] instanceof Item ? new ItemStack((Item) row[0]) : new ItemStack((Block) row[0]);
				int want = list == added && !on ? 0 : (Integer) row[1];
				int got = furnace.oldfaithful$fuelTime(stack);
				if (got != want) wrong.append(wrong.length() == 0 ? "" : ", ").append(stack.itemId).append(':').append(stack.getDamage())
						.append(' ').append(got).append(" not ").append(want);
			}
		}
		results.check("still: furnace fuel times of " + added.length + " wooden things (" + (on ? "later versions'" : "beta: none") + ") and "
				+ beta.length + " of beta's own" + (wrong.length() == 0 ? "" : ": " + wrong), wrong.length() == 0);
	}

	/** #69: chests placed with the item at a set yaw, alone and in pairs, and a chest of beta's beside a wall. */
	private void chests(Minecraft mc) {
		boolean on = PolishConfig.applied("blocks.chestsFaceYou"), pairs = PolishConfig.applied("blocks.chestPairs");
		int y = Y, x0 = bx + 20, z0 = bz + 20, tex = Block.CHEST.textureId;
		float yaw = mc.player.yaw;
		// Alone, the player looking west: faces east (5); beta: south (3), data 0.
		int x = x0, z = z0;
		placeChest(mc, x, y, z, 90.0F);
		int meta = world.getBlockMeta(x, y, z);
		int east = Block.CHEST.getTextureId(world, x, y, z, 5), south = Block.CHEST.getTextureId(world, x, y, z, 3);
		results.check("still #69: a chest placed looking west: data " + meta + ", front " + (east == tex + 1 ? "east" : south == tex + 1 ? "south" : "?")
				+ " (" + (on ? "5, east, toward the player" : "beta: 0, south") + ")",
				on ? meta == 5 && east == tex + 1 && south == tex : meta == 0 && south == tex + 1 && east == tex);
		// A pair: the first looking south faces north (2); the second, placed looking west along the pair, joins it.
		x = x0 + 4;
		placeChest(mc, x, y, z, 0.0F);
		placeChest(mc, x + 1, y, z, 90.0F);
		int a = world.getBlockMeta(x, y, z), b = world.getBlockMeta(x + 1, y, z);
		int an = Block.CHEST.getTextureId(world, x, y, z, 2), bn = Block.CHEST.getTextureId(world, x + 1, y, z, 2);
		int as = Block.CHEST.getTextureId(world, x, y, z, 3), be = Block.CHEST.getTextureId(world, x + 1, y, z, 5);
		if (pairs) {
			results.check("still pairs: a chest placed looking along one facing north doesn't join it: data " + a + " and " + b + ", fronts north and east ("
					+ an + ", " + be + ")", a == 2 && b >= 6 && b <= 13 && Chests.partner(world, x, y, z, Block.CHEST.id) == 0 && an == tex + 1 && be == tex + 1);
		} else {
			results.check("still #69: a double chest, the second half placed looking along it: data " + a + " and " + b + ", north faces "
					+ an + " and " + bn + " (" + (on ? "2 and 2, the double front on the north" : "beta: 0 and 0, the front on the south") + ")",
					on ? a == 2 && b == 2 && an == tex + 16 && bn == tex + 15 && as == tex + 31
							: a == 0 && b == 0 && as == tex + 15 && an == tex + 32);
		}
		// Beta's chest (data 0) against a wall on the south: beta's rule turns it north, either way.
		x = x0 + 8;
		world.setBlock(x, y, z + 1, Block.STONE.id);
		world.setBlock(x, y, z, Block.CHEST.id);
		results.check("still #69: a chest from before (data 0) against a wall on the south still faces north by beta's rule",
				world.getBlockMeta(x, y, z) == 0 && Block.CHEST.getTextureId(world, x, y, z, 2) == tex + 1 && Block.CHEST.getTextureId(world, x, y, z, 3) == tex);
		// A chest placed beside that one, looking along the pair (yaw 80, a little south of west): the old half has no
		// facing to join, so both turn to the side across the pair the player is on, north.
		placeChest(mc, x + 1, y, z, 80.0F);
		a = world.getBlockMeta(x, y, z);
		b = world.getBlockMeta(x + 1, y, z);
		if (pairs) {
			// It faces east, the old one on its back: no join, and the old one keeps the north it was drawn with, now in its data.
			results.check("still pairs: a chest placed beside one from before, looking along them: data " + a + " and " + b + " (2, and joined to nobody)",
					a == 2 && b >= 6 && b <= 13 && Chests.partner(world, x, y, z, Block.CHEST.id) == 0 && Block.CHEST.getTextureId(world, x, y, z, 2) == tex + 1);
		} else {
			results.check("still #69: a chest placed beside one from before: data " + a + " and " + b + " (" + (on ? "2 and 2" : "beta: 0 and 0") + ")",
					on ? a == 2 && b == 2 : a == 0 && b == 0);
		}
		mc.player.yaw = yaw;
	}

	/**
	 * Chest pairs (blocks.chestPairs, later versions' rule): four chests in a row placed facing north make two doubles;
	 * a sneaking placement stays single; one facing another way beside a single doesn't join; a pair from before, two
	 * chests of beta's, stays one with a chest placed beside it. Beta: the third chest can't be placed at all.
	 */
	private void chestPairs(Minecraft mc) {
		boolean on = PolishConfig.applied("blocks.chestPairs");
		int y = Y, x = bx + 20, z = bz + 26, id = Block.CHEST.id, tex = Block.CHEST.textureId;
		float yaw = mc.player.yaw;
		for (int i = 0; i < 4; i++) placeChest(mc, x + i, y, z, 0.0F);
		boolean third = world.getBlockId(x + 2, y, z) == id, fourth = world.getBlockId(x + 3, y, z) == id;
		if (!on) {
			results.check("still pairs: beta: a chest can't go beside a double chest (the third of a row wasn't placed)", !third);
		} else {
			int[] metas = new int[4], partners = new int[4];
			for (int i = 0; i < 4; i++) {
				metas[i] = world.getBlockMeta(x + i, y, z);
				partners[i] = Chests.partner(world, x + i, y, z, id);
			}
			results.check("still pairs: four chests in a row facing north are two doubles side by side: data " + java.util.Arrays.toString(metas) + ", partners "
					+ java.util.Arrays.toString(partners) + " (east, west, east, west)", third && fourth && partners[0] == 5 && partners[1] == 4 && partners[2] == 5 && partners[3] == 4);
			int n1 = Block.CHEST.getTextureId(world, x + 1, y, z, 2), n2 = Block.CHEST.getTextureId(world, x + 2, y, z, 2);
			results.check("still pairs: where the two doubles meet, each shows the end of its own front (" + n1 + ", " + n2 + ")", n1 == tex + 15 && n2 == tex + 16);
		}
		// Sneaking beside a single chest facing the same way: stays single.
		int sx = x, sz = z + 4;
		placeChest(mc, sx, y, sz, 0.0F);
		mc.player.input.sneaking = true;
		placeChest(mc, sx + 1, y, sz, 0.0F);
		mc.player.input.sneaking = false;
		if (on) {
			results.check("still pairs: a chest placed sneaking beside one facing the same way stays single (" + chestData(sx, y, sz) + ", " + chestData(sx + 1, y, sz) + ")",
					Chests.partner(world, sx, y, sz, id) == 0 && Chests.partner(world, sx + 1, y, sz, id) == 0 && Block.CHEST.getTextureId(world, sx + 1, y, sz, 2) == tex + 1);
		}
		// Facing another way, beside a single one: no join; the old one stays single.
		int fx = x + 6, fz = z + 4;
		placeChest(mc, fx, y, fz, 0.0F);
		placeChest(mc, fx + 1, y, fz, 90.0F);
		if (on) {
			results.check("still pairs: a chest facing east beside one facing north doesn't join it (" + chestData(fx, y, fz) + ", " + chestData(fx + 1, y, fz) + ")",
					Chests.partner(world, fx, y, fz, id) == 0 && Chests.partner(world, fx + 1, y, fz, id) == 0 && Block.CHEST.getTextureId(world, fx + 1, y, fz, 5) == tex + 1);
		}
		// A pair from before (two chests of beta's) with a chest placed beside it: still one pair, and the new one alone.
		int ox = x + 10, oz = z + 4;
		world.setBlock(ox, y - 1, oz, Block.STONE.id);
		world.setBlock(ox + 1, y - 1, oz, Block.STONE.id);
		world.setBlock(ox, y, oz, id);
		world.setBlock(ox + 1, y, oz, id);
		int before = Block.CHEST.getTextureId(world, ox, y, oz, 3);
		placeChest(mc, ox + 2, y, oz, 0.0F);
		if (on) {
			results.check("still pairs: a pair from before stays a pair with a chest placed beside it, which stays alone, and its front doesn't move ("
					+ before + ", " + Block.CHEST.getTextureId(world, ox, y, oz, 3) + ")",
					world.getBlockId(ox + 2, y, oz) == id && Chests.partner(world, ox, y, oz, id) == 5 && Chests.partner(world, ox + 1, y, oz, id) == 4
							&& Chests.partner(world, ox + 2, y, oz, id) == 0 && Block.CHEST.getTextureId(world, ox, y, oz, 3) == before);
		} else {
			results.check("still pairs: beta: no chest beside a pair from before", world.getBlockId(ox + 2, y, oz) == 0);
		}
		mc.player.yaw = yaw;
	}

	/** render.chestLids: a pair's top and bottom from the joined lid's halves, which terrain.png holds as the pack's lid rearranged. */
	private void chestLids(Minecraft mc) {
		boolean on = PolishConfig.applied("render.chestLids");
		int y = Y, x = bx + 20, z = bz + 34, id = Block.CHEST.id, tex = Block.CHEST.textureId;
		world.setBlock(x, y, z, id);
		world.setBlock(x + 1, y, z, id);
		world.setBlock(x + 4, y, z, id);
		world.setBlock(x + 4, y, z + 1, id);
		int[] got = {Block.CHEST.getTextureId(world, x, y, z, 1), Block.CHEST.getTextureId(world, x + 1, y, z, 1), Block.CHEST.getTextureId(world, x + 4, y, z, 0),
				Block.CHEST.getTextureId(world, x + 4, y, z + 1, 0)};
		int[] want = on ? new int[] {Chests.LIDS, Chests.LIDS + 1, Chests.LIDS + 2, Chests.LIDS + 3} : new int[] {tex - 1, tex - 1, tex - 1, tex - 1};
		results.check("still lids: a pair along x's tops and one along z's bottoms are " + java.util.Arrays.toString(got) + " (" + (on ? "the joined lid's halves" : "beta: the lid")
				+ ")", java.util.Arrays.equals(got, want));
		if (!on) return;
		try {
			BufferedImage pack = ImageIO.read(mc.texturePacks.selected.getResource("/terrain.png"));
			BufferedImage expected = ChestLids.withLids(pack);
			int size = pack.getWidth(), n = size / 16;
			ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
			mc.textureManager.bindTexture(mc.textureManager.getTextureId("/terrain.png"));
			GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			int wrong = 0;
			for (int slot = Chests.LIDS; slot < Chests.LIDS + 4; slot++) {
				for (int v = 0; v < n; v++) {
					for (int u = 0; u < n; u++) {
						int px = slot % 16 * n + u, py = slot / 16 * n + v, at = (py * size + px) * 4;
						int argb = expected.getRGB(px, py);
						int r = pixels.get(at) & 255, g = pixels.get(at + 1) & 255, b = pixels.get(at + 2) & 255;
						if (r != (argb >> 16 & 255) || g != (argb >> 8 & 255) || b != (argb & 255)) wrong++;
					}
				}
			}
			int seam = expected.getRGB(Chests.LIDS % 16 * n + n - 1, Chests.LIDS / 16 * n + n / 2), border = pack.getRGB(25 % 16 * n + n - 1, 25 / 16 * n + n / 2);
			results.check("still lids: terrain.png in the game holds the joined lid's halves (" + wrong + " pixels differ), the west half without the lid's east border",
					wrong == 0 && seam != border);
		} catch (java.io.IOException e) {
			results.check("still lids: the pack's terrain.png read: " + e, false);
		}
	}

	/** For a check's message: a chest's data, or that none went in (a mob or cart in the cell stops a placement). */
	private String chestData(int x, int y, int z) {
		return world.getBlockId(x, y, z) == Block.CHEST.id ? "data " + world.getBlockMeta(x, y, z) : "no chest";
	}

	private void placeChest(Minecraft mc, int x, int y, int z, float yaw) {
		world.setBlock(x, y - 1, z, Block.STONE.id);
		mc.player.yaw = yaw;
		new ItemStack(Block.CHEST, 1).useOnBlock(mc.player, world, x, y - 1, z, 1);
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

	/**
	 * Obsidian mined with a diamond pickaxe, tick by tick: 1.0's 188 ticks (9.4 s) or beta's 300 (15 s); by hand, the
	 * break's progress a tick: 1.0's 1/5000, or beta's 1/1000 (Beta and Diamond Only).
	 */
	private void obsidian(Minecraft mc) {
		boolean on = PolishConfig.applied(PolishConfig.OBSIDIAN_KEY), full = on && PolishConfig.obsidianAtStart().equals("true");
		PlayerInventory inventory = mc.player.inventory;
		inventory.main[inventory.selectedSlot] = null;
		float byHand = Block.OBSIDIAN.getHardness(mc.player);
		inventory.main[inventory.selectedSlot] = new ItemStack(Item.DIAMOND_PICKAXE);
		int x = bx + 30, y = Y, z = bz + 3;
		world.setBlock(x, y - 1, z, Block.STONE.id);
		world.setBlock(x, y, z, Block.OBSIDIAN.id);
		InteractionManager im = mc.interactionManager;
		im.cancelBlockBreaking();
		int calls = 0;
		while (world.getBlockId(x, y, z) != 0 && calls < 400) {
			im.processBlockBreakingAction(x, y, z, 1);
			calls++;
		}
		inventory.main[inventory.selectedSlot] = null;
		float handWanted = full ? 1.0F / 5000.0F : 1.0F / 1000.0F;
		results.check("still: obsidian (" + PolishConfig.obsidianAtStart() + ") mined with a diamond pickaxe in " + calls + " ticks, dropped "
				+ dropped(x, y, z) + ", and by hand " + Math.round(1.0F / byHand) + " (" + (on ? "1.0: about 189" : "beta: about 301")
				+ ", and " + (full ? "1.0's 5000" : "beta's 1000") + ")",
				droppedOnly(x, y, z, Block.OBSIDIAN.id) && (on ? calls >= 186 && calls <= 195 : calls >= 298 && calls <= 307)
						&& Math.abs(byHand - handWanted) < handWanted * 0.01F);
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
		results.check("still #4: a cow the player hit despawned (hitting doesn't keep)", hit.dead);
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
