package org.studioquire.oldfaithful.selftest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.client.InteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.selftest.mixin.FishingBobberAccessor;
import org.studioquire.oldfaithful.selftest.mixin.MinecraftPickInvoker;
import org.studioquire.oldfaithful.selftest.mixin.RedstoneTorchInvoker;

/**
 * The Fixed edition's fixes (oldfaithful-fixes) in a running single-player world, from the start-up test once the
 * world runs, when that mod is loaded (`:harness:clientStartupTestFixed`). Each check sets up a small scene well
 * above the ground, away from the player and from the others, and reads the outcome some ticks later; on vanilla
 * every one of them comes out the other way. What only shows on screen or by ear (the sky, the cursor's item,
 * footsteps, undersides) and the bed check (it needs a sleeping player and a monster's path) are the user's or
 * reasoned (docs/fixes.md). Pending ticks saved with their chunk (#42) are checked on the real server
 * (`:oldfaithful-fixes:serverTicksTest`), as are the world fixes from the piston hunt. The player's own sneak step off
 * an inside corner (#57) is made first, synchronously; the last scene puts the player in a boat and out again.
 */
final class FixesCheck {
	private static final int SETTLE_TICKS = 40;
	private static final int Y = 110;
	// #27: the boat settles, the player gets in, gets out, and is watched standing on it.
	private static final int MOUNT_TICK = 40;
	private static final int DISMOUNT_TICK = 50;
	private static final int DONE_TICK = 75;

	private final SelfTest results;
	private int ticks;
	private int bx;
	private int bz;
	private MinecartEntity cart;
	private BoatEntity boat;
	private boolean wouldDrop;
	private double boatTop;
	private double lowest = Double.MAX_VALUE;

	FixesCheck(SelfTest results) {
		this.results = results;
	}

	/** Sets the scenes up and runs the immediate checks. */
	void start(Minecraft mc) {
		World world = mc.world;
		bx = (int) Math.floor(mc.player.x);
		bz = (int) Math.floor(mc.player.z);
		// Load every chunk near the scenes: the world schedules a block's tick (flowing water's) only with its
		// surroundings loaded, and the tiny render distance loads little.
		for (int x = bx - 32; x <= bx + 32; x += 16) {
			for (int z = bz - 32; z <= bz + 32; z += 16) world.getBlockId(x, Y, z);
		}

		sneakOffCorner(mc, world);
		stepUnderRoof(mc, world);
		boxAgainstWall(mc, world);
		squids(world);

		// #19: water right above a torch standing on stone, ticked once now: in this new world the scheduled tick would
		// wait behind the generator's backlog (some 9,000 liquid ticks, past the 1,000 a tick the world works through).
		world.setBlock(bx + 12, Y, bz, Block.STONE.id);
		world.setBlock(bx + 12, Y + 1, bz, Block.TORCH.id, 5);
		world.setBlock(bx + 12, Y + 2, bz, Block.FLOWING_WATER.id, 0);
		Block.FLOWING_WATER.onTick(world, bx + 12, Y + 2, bz, world.random);

		// #11: a furnace smelting cobblestone on a lava bucket.
		world.setBlock(bx - 12, Y, bz, Block.FURNACE.id, 2);
		FurnaceBlockEntity furnace = (FurnaceBlockEntity) world.getBlockEntity(bx - 12, Y, bz);
		furnace.setStack(0, new ItemStack(Block.COBBLESTONE, 4));
		furnace.setStack(1, new ItemStack(Item.LAVA_BUCKET));

		// #25: a big slime killed by far more than its health, on a floor so the smaller ones don't fall to their death.
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) world.setBlock(bx + dx, Y - 1, bz + 12 + dz, Block.STONE.id);
		}
		SlimeEntity slime = new SlimeEntity(world);
		slime.setSize(4);
		slime.setPositionAndAnglesKeepPrevAngles(bx + 0.5, Y, bz + 12.5, 0.0F, 0.0F);
		world.spawnEntity(slime);
		slime.damage(null, 100);

		// #29: a saddled pig, killed.
		PigEntity pig = new PigEntity(world);
		pig.setSaddled(true);
		pig.setPositionAndAnglesKeepPrevAngles(bx + 0.5, Y, bz - 11.5, 0.0F, 0.0F);
		world.spawnEntity(pig);
		pig.damage(null, 100);

		// #8: leaves cut with the shears' last use, through the single-player break as a player's click does.
		PlayerInventory inventory = mc.player.inventory;
		world.setBlock(bx + 12, Y, bz + 12, Block.LEAVES.id, 0);
		inventory.main[inventory.selectedSlot] = new ItemStack(Item.SHEARS, 1, Item.SHEARS.getMaxDamage());
		mc.interactionManager.breakBlock(bx + 12, Y, bz + 12, 0);
		results.check("fixes #8: the shears' last cut is used up", inventory.main[inventory.selectedSlot] == null);

		// #9: a wooden slab mined by hand, as holding the button does, tick by tick.
		int sx = bx - 12, sz = bz - 12;
		world.setBlock(sx, Y, sz, Block.SLAB.id, 2);
		float vanilla = Block.SLAB.getHardness(mc.player);
		float planks = Block.PLANKS.getHardness(mc.player);
		InteractionManager im = mc.interactionManager;
		im.cancelBlockBreaking();
		int calls = 0;
		while (world.getBlockId(sx, Y, sz) != 0 && calls < 2000) {
			im.processBlockBreakingAction(sx, Y, sz, 1);
			calls++;
		}
		int asPlanks = (int) Math.ceil(1.0F / planks) + 1;
		results.check("fixes #9: a wooden slab mined by hand in " + calls + " ticks, as planks (" + asPlanks + "; beta "
				+ ((int) Math.ceil(1.0F / vanilla) + 1) + ")", calls <= asPlanks + 1 && calls < Math.ceil(1.0F / vanilla));

		// #10: for every block, which tools mine it fast, through the game's own multiplier, against docs/bugs.md's table
		// (beta's lists plus every block of the tool's material, obsidian and redstone ore left slow).
		String wrong = "";
		for (Block block : Block.BLOCKS) {
			if (block != null && block.id >= 256) continue; // a library mod's
			if (block == null) continue;
			wrong += fastWrong(block, Item.GOLDEN_PICKAXE, PICKAXE_FAST) + fastWrong(block, Item.GOLDEN_AXE, AXE_FAST)
					+ fastWrong(block, Item.GOLDEN_SHOVEL, SHOVEL_FAST);
		}
		results.check("fixes #10: every block's fast tools match the table" + (wrong.isEmpty() ? "" : ":" + wrong), wrong.isEmpty());

		// #10: a crafting table mined with a wooden axe, tick by tick, against planks (the same hardness as beta's list
		// gave planks).
		int tx = bx + 12, tz = bz - 12;
		world.setBlock(tx, Y, tz, Block.CRAFTING_TABLE.id);
		inventory.main[inventory.selectedSlot] = new ItemStack(Item.WOODEN_AXE);
		float tableBeta = 1.0F / Block.CRAFTING_TABLE.getHardness() / 30.0F;
		int axeTicks = (int) Math.ceil(1.0F / (2.0F * tableBeta)) + 1;
		im.cancelBlockBreaking();
		calls = 0;
		while (world.getBlockId(tx, Y, tz) != 0 && calls < 2000) {
			im.processBlockBreakingAction(tx, Y, tz, 1);
			calls++;
		}
		results.check("fixes #10: a crafting table mined with a wooden axe in " + calls + " ticks (" + axeTicks + "; beta "
				+ ((int) Math.ceil(1.0F / tableBeta) + 1) + ")", calls <= axeTicks + 1);
		inventory.main[inventory.selectedSlot] = null;

		// #33: names of a slab and a dye with impossible data values.
		try {
			String slab = new ItemStack(Block.SLAB.id, 1, 7).getTranslationKey();
			String dye = new ItemStack(Item.DYE, 1, 40).getTranslationKey();
			results.check("fixes #33: names of a slab and a dye with bad data: " + slab + ", " + dye,
					slab.endsWith(".stone") && dye.endsWith(".black"));
		} catch (Throwable t) {
			results.fail("fixes #33: names of a slab and a dye with bad data", t);
		}

		// #41: a door beside a repeater that switches on.
		int rx = bx + 24, rz = bz + 6;
		world.setBlockWithoutNotifyingNeighbors(rx, Y - 1, rz, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(rx + 1, Y - 1, rz, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(rx + 1, Y, rz, Block.DOOR.id, 0);
		world.setBlockWithoutNotifyingNeighbors(rx + 1, Y + 1, rz, Block.DOOR.id, 8);
		world.setBlockWithoutNotifyingNeighbors(rx, Y, rz, Block.REPEATER.id, 1);
		world.setBlock(rx, Y, rz, Block.POWERED_REPEATER.id, 1);
		results.check("fixes #41: the door opens as the repeater beside it switches on", (world.getBlockMeta(rx + 1, Y, rz) & 4) != 0);

		// #44: a redstone torch with eight turn-offs recorded in the future (as after opening a world whose clock is
		// behind the last one's), unlit and unpowered: it relights on its tick.
		int ux = bx + 24, uz = bz + 12;
		world.setBlockWithoutNotifyingNeighbors(ux, Y - 1, uz, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(ux, Y, uz, Block.REDSTONE_TORCH.id, 5);
		long now = world.getTime();
		world.setTime(now + 1000);
		for (int i = 0; i < 8; i++) ((RedstoneTorchInvoker) Block.REDSTONE_TORCH).oldfaithful$isBurnedOut(world, ux, Y, uz, true);
		world.setTime(now);
		Block.REDSTONE_TORCH.onTick(world, ux, Y, uz, world.random);
		results.check("fixes #44: a torch with turn-offs from a later clock relights", world.getBlockId(ux, Y, uz) == Block.LIT_REDSTONE_TORCH.id);

		// #45: torches crafted by shift-click into an inventory with room for only two of the four.
		ScreenHandler screen = mc.player.playerScreenHandler;
		for (int i = 0; i < inventory.main.length; i++) inventory.main[i] = new ItemStack(Block.DIRT, 64);
		inventory.main[9] = new ItemStack(Block.TORCH, 62);
		slot(screen, 1).setStack(new ItemStack(Item.COAL, 1, 0));
		slot(screen, 3).setStack(new ItemStack(Item.STICK));
		ItemStack offered = slot(screen, 0).getStack();
		screen.onSlotClick(0, 0, true, mc.player);
		int torches = 0;
		for (ItemStack s : inventory.main) torches += s != null && s.itemId == Block.TORCH.id ? s.count : 0;
		ItemStack left = slot(screen, 0).getStack();
		results.check("fixes #45: a shift-clicked craft that doesn't fit stays put (" + torches + " torches in the inventory, " + left
				+ " offered)", offered != null && offered.count == 4 && torches == 62 && left != null && left.count == 4 && slot(screen, 1).hasStack());
		slot(screen, 1).setStack(null);
		slot(screen, 3).setStack(null);
		clear(inventory);

		// #46: pick block on redstone, on red wool with white wool first in the hotbar, and on a mob.
		int px = bx + 24, pz = bz - 6;
		world.setBlockWithoutNotifyingNeighbors(px, Y - 1, pz, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(px, Y, pz, Block.REDSTONE_WIRE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(px, Y, pz - 2, Block.WOOL.id, 14);
		inventory.main[1] = new ItemStack(Block.STONE);
		inventory.main[3] = new ItemStack(Item.REDSTONE, 5);
		inventory.main[5] = new ItemStack(Block.WOOL, 1, 0);
		inventory.main[6] = new ItemStack(Block.WOOL, 1, 14);
		HitResult aimed = mc.crosshairTarget;
		MinecraftPickInvoker pick = (MinecraftPickInvoker) mc;
		inventory.selectedSlot = 0;
		mc.crosshairTarget = new HitResult(px, Y, pz, 1, Vec3d.createCached(px + 0.5, Y + 0.05, pz + 0.5));
		pick.oldfaithful$pickBlock();
		int onWire = inventory.selectedSlot;
		mc.crosshairTarget = new HitResult(px, Y, pz - 2, 1, Vec3d.createCached(px + 0.5, Y + 1.0, pz - 1.5));
		pick.oldfaithful$pickBlock();
		int onWool = inventory.selectedSlot;
		inventory.selectedSlot = 0;
		mc.crosshairTarget = new HitResult(pig);
		pick.oldfaithful$pickBlock();
		int onMob = inventory.selectedSlot;
		mc.crosshairTarget = aimed;
		inventory.selectedSlot = 0;
		clear(inventory);
		results.check("fixes #46: pick block selects redstone, the red wool and nothing for a mob (slots " + onWire + ", " + onWool + ", "
				+ onMob + ")", onWire == 3 && onWool == 6 && onMob == 0);

		// #31: a minecart rolling along a rail towards a dropped item.
		int cz = bz - 24;
		for (int x = bx - 8; x <= bx + 8; x++) {
			world.setBlockWithoutNotifyingNeighbors(x, Y - 1, cz, Block.STONE.id, 0);
			world.setBlockWithoutNotifyingNeighbors(x, Y, cz, Block.RAIL.id, 1);
		}
		ItemEntity lying = new ItemEntity(world, bx - 2 + 0.5, Y + 0.1, cz + 0.5, new ItemStack(Block.DIRT));
		lying.velocityX = lying.velocityY = lying.velocityZ = 0.0;
		world.spawnEntity(lying);
		cart = new MinecartEntity(world, bx - 6 + 0.5, Y + 0.1, cz + 0.5, 0);
		world.spawnEntity(cart);
		cart.velocityX = 0.4;

		// #34, fishing: a fish reeled in from 12 blocks out, level with the player's feet, is thrown up only by the lift
		// for its distance (single player used to aim it 1.62 higher, at the eyes).
		FishingBobberEntity bobber = new FishingBobberEntity(world, mc.player);
		bobber.setPosition(mc.player.x + 12, mc.player.boundingBox.minY, mc.player.z);
		((FishingBobberAccessor) bobber).oldfaithful$setHookCountdown(5);
		bobber.use();
		Object last = world.entities.get(world.entities.size() - 1);
		double lift = last instanceof ItemEntity ? ((ItemEntity) last).velocityY : Double.NaN;
		double expected = Math.sqrt(12.0) * 0.08;
		results.check("fixes #34: a fish reeled in level with your feet is thrown at them (upward " + String.format("%.3f", lift) + ", "
				+ String.format("%.3f", expected) + " expected)", Math.abs(lift - expected) < 0.01);

		// #27: a boat on water with its surface at y 63, a block below sea level, in a basin of its own: the boat's top
		// lies between 62.4 and 64, where beta's rounding drops a rider about half the time (at sea level it's above 64,
		// where the rounding is exact).
		int wx = bx - 24, wz = bz - 24;
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				for (int y = 59; y <= 68; y++) {
					boolean inside = Math.abs(x) < 3 && Math.abs(z) < 3 && y >= 60;
					world.setBlockWithoutNotifyingNeighbors(wx + x, y, wz + z, !inside ? Block.STONE.id : y <= 62 ? Block.WATER.id : 0, 0);
				}
			}
		}
		boat = new BoatEntity(world, wx + 0.5, 63.0, wz + 0.5);
		world.spawnEntity(boat);
	}

	/**
	 * #57: the player, sneaking, on an L of three blocks, standing over the inside corner, steps diagonally towards the
	 * missing fourth: each of the step's parts alone keeps a block underfoot, the two together don't. The step is made
	 * through the player's own move, as walking makes it; then the player goes back where it was.
	 */
	private void sneakOffCorner(Minecraft mc, World world) {
		int cx = bx - 30, cz = bz + 30;
		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) world.setBlockWithoutNotifyingNeighbors(cx + dx, Y - 1, cz + dz, 0, 0);
		}
		world.setBlockWithoutNotifyingNeighbors(cx, Y - 1, cz, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(cx + 1, Y - 1, cz, Block.STONE.id, 0);
		world.setBlockWithoutNotifyingNeighbors(cx, Y - 1, cz + 1, Block.STONE.id, 0);
		double ox = mc.player.x, oy = mc.player.y, oz = mc.player.z;
		boolean wasSneaking = mc.player.input.sneaking, wasOnGround = mc.player.onGround;
		mc.player.setPosition(cx + 0.95, Y + mc.player.standingEyeHeight, cz + 0.95);
		double feet = mc.player.boundingBox.minY;
		mc.player.onGround = true;
		mc.player.input.sneaking = true;
		mc.player.move(0.4, 0.0, 0.4);
		boolean held = world.getEntityCollisions(mc.player, mc.player.boundingBox.offset(0.0, -1.0, 0.0)).size() > 0;
		String at = String.format("%.2f, %.2f", mc.player.x - cx, mc.player.z - cz);
		mc.player.input.sneaking = wasSneaking;
		mc.player.setPosition(ox, oy, oz);
		// The move left the player off the ground (it moved nothing vertically); mining checks below need it back.
		mc.player.onGround = wasOnGround;
		mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0.0;
		results.check("fixes #57: sneaking diagonally off an inside corner keeps a block underfoot (stopped at " + at + ", feet at "
				+ String.format("%.2f", feet - Y) + ")", held && Math.abs(feet - Y) < 0.01);
	}

	/**
	 * #76: a squid moved 8 blocks along a floor of redstone ore through its own move (beta: it steps on it, and the ore
	 * lights). #77: its water test at feet heights from the floor up a column of water 3 deep (beta: dry a quarter of the
	 * way up each block). Its movement over time is measured on the real server (`:oldfaithful-fixes:squidWatch`).
	 */
	private void squids(World world) {
		int x0 = bx + 40, z = bz + 30, floor = Y + 10;
		for (int x = x0 - 1; x <= x0 + 12; x++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.setBlockWithoutNotifyingNeighbors(x, floor, z + dz, Block.REDSTONE_ORE.id, 0);
				world.setBlockWithoutNotifyingNeighbors(x, floor + 1, z + dz, Block.WATER.id, 0);
			}
		}
		SquidEntity squid = new SquidEntity(world);
		squid.setPositionAndAnglesKeepPrevAngles(x0 + 0.5, floor + 1, z + 0.5, 0.0F, 0.0F);
		for (int t = 0; t < 40; t++) squid.move(0.2, -0.05, 0.0);
		int lit = 0;
		for (int x = x0 - 1; x <= x0 + 12; x++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (world.getBlockId(x, floor, z + dz) == Block.LIT_REDSTONE_ORE.id) lit++;
			}
		}
		results.check("fixes #76: a squid moved " + String.format("%.1f", squid.x - x0) + " blocks along redstone ore lights none (" + lit + ")",
				lit == 0 && squid.x - x0 > 7.0);

		int cz = z + 4;
		world.setBlockWithoutNotifyingNeighbors(x0, floor, cz, Block.STONE.id, 0);
		for (int y = floor + 1; y <= floor + 3; y++) world.setBlockWithoutNotifyingNeighbors(x0, y, cz, Block.WATER.id, 0);
		int dry = 0;
		for (int i = 0; i < 96; i++) {
			squid.setPosition(x0 + 0.5, floor + 1 + i * 0.025, cz + 0.5);
			if (!squid.isSubmergedInWater()) dry++;
		}
		squid.setPosition(x0 + 0.5, floor + 1 + 2.45, cz + 0.5);
		boolean over = squid.isSubmergedInWater();
		results.check("fixes #77: a squid's water test holds all the way up the water (" + dry + " of 96 heights dry) and not with its middle over the surface",
				dry == 0 && !over);
	}

	/**
	 * #74: the player, walking level across a one-wide trench under a roof 2 above the floor, a step from the far rim with
	 * onGround still true (as on the tick after leaving the near rim): the plain move falls and hits the rim, so the step is
	 * tried. The roof clips its rise to 0.2. The way down must end level with the rim, not 0.3 inside it.
	 */
	private void stepUnderRoof(Minecraft mc, World world) {
		int tx = bx - 40, cz = bz + 30, floor = Y + 10;
		for (int x = tx - 3; x <= tx + 3; x++) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				world.setBlockWithoutNotifyingNeighbors(x, floor, z, x == tx ? 0 : Block.STONE.id, 0);
				world.setBlockWithoutNotifyingNeighbors(x, floor + 3, z, Block.STONE.id, 0);
			}
		}
		double ox = mc.player.x, oy = mc.player.y, oz = mc.player.z;
		boolean wasOnGround = mc.player.onGround;
		float wasOffset = mc.player.cameraOffset;
		mc.player.cameraOffset = 0.0F;
		mc.player.setPosition(tx + 0.6, floor + 1 + (double) mc.player.standingEyeHeight, cz + 0.5);
		mc.player.onGround = true;
		mc.player.move(0.215, -0.0784, 0.0);
		double feet = mc.player.boundingBox.minY - (floor + 1), past = mc.player.boundingBox.maxX - (tx + 1);
		mc.player.setPosition(ox, oy, oz);
		mc.player.cameraOffset = wasOffset;
		mc.player.onGround = wasOnGround;
		mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0.0;
		results.check("fixes #74: a step under a low roof comes down level with the rim (feet " + String.format("%.3f", feet) + " from it, "
				+ String.format("%.3f", past) + " over it)", feet == 0.0 && past > 0.0);
	}

	/**
	 * #75: the player's box rebuilt from a position whose rounding lands its side a step past a wall it was touching, as a
	 * load can leave it, then a step towards the wall. It must stay out.
	 */
	private void boxAgainstWall(Minecraft mc, World world) {
		int wall = bx - 50, cz = bz + 30, floor = Y + 10;
		for (int x = wall - 2; x <= wall; x++) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				world.setBlockWithoutNotifyingNeighbors(x, floor, z, Block.STONE.id, 0);
				for (int y = floor + 1; y <= floor + 3; y++) world.setBlockWithoutNotifyingNeighbors(x, y, z, x == wall ? Block.STONE.id : 0, 0);
			}
		}
		double ox = mc.player.x, oy = mc.player.y, oz = mc.player.z;
		boolean wasOnGround = mc.player.onGround;
		float half = mc.player.width / 2.0F;
		double x = wall - (double) half;
		while (x + half <= wall) x = Math.nextUp(x);
		mc.player.setPosition(x, floor + 1 + (double) mc.player.standingEyeHeight, cz + 0.5);
		boolean out = world.getEntityCollisions(mc.player, mc.player.boundingBox).isEmpty();
		mc.player.move(0.2, 0.0, 0.0);
		double in = mc.player.boundingBox.maxX - wall;
		mc.player.setPosition(ox, oy, oz);
		mc.player.onGround = wasOnGround;
		mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0.0;
		results.check("fixes #75: a box rebuilt a rounding step past a wall stops at it (" + (out ? "outside" : "inside") + " after the rebuild, side "
				+ in + " past it after a step)", out && in <= 0.0);
	}

	private static Slot slot(ScreenHandler screen, int index) {
		return (Slot) screen.slots.get(index);
	}

	private static void clear(PlayerInventory inventory) {
		for (int i = 0; i < inventory.main.length; i++) inventory.main[i] = null;
	}

	/** True once the scenes have settled and been checked. */
	boolean tick(Minecraft mc) {
		++ticks;
		if (ticks == MOUNT_TICK) mc.player.setVehicle(boat);
		if (ticks == DISMOUNT_TICK) {
			// Beta's rounding leaves the feet a step inside the boat at about half the heights in this band; move the
			// boat up by the least amount (a few billionths of a block) to one of them, so the scene always tests it.
			float eye = mc.player.standingEyeHeight;
			boatTop = boat.boundingBox.minY + boat.height;
			for (int i = 0; i < 400 && !(boatTop + eye - eye < boatTop); i++) {
				boat.setPosition(boat.x, Math.nextUp(boat.y), boat.z);
				boatTop = boat.boundingBox.minY + boat.height;
			}
			wouldDrop = boatTop + eye - eye < boatTop;
			mc.player.setVehicle(boat);
		}
		if (ticks > DISMOUNT_TICK) lowest = Math.min(lowest, mc.player.boundingBox.minY - boat.boundingBox.maxY);
		if (ticks == DONE_TICK) {
			results.check("fixes #27: out of a boat (its top at " + String.format("%.3f", boatTop) + ", where beta's rounding "
					+ (wouldDrop ? "drops" : "wouldn't drop") + " the feet), the player stands on it (lowest " + String.format("%.4f", lowest)
					+ " from its top)", wouldDrop && lowest > -0.01);
			return true;
		}
		if (ticks != SETTLE_TICKS) return false;
		World world = mc.world;
		results.check("fixes #31: the minecart rolled past the item on the track (at x " + String.format("%.1f", cart.x - bx) + ")",
				cart.x > bx - 0.5);
		results.check("fixes #19: falling water drops the torch it washes away",
				dropped(world, bx + 12, bz, Block.TORCH.id, -1) && world.getBlockId(bx + 12, Y + 1, bz) != Block.TORCH.id);
		ItemStack fuel = ((FurnaceBlockEntity) world.getBlockEntity(bx - 12, Y, bz)).getStack(1);
		results.check("fixes #11: the furnace leaves the empty bucket (fuel slot: " + fuel + ")", fuel != null && fuel.itemId == Item.BUCKET.id);
		int smaller = 0;
		for (Object e : world.entities) {
			// `near` goes by x and z: slimes born in a slime chunk under the platform aren't these.
			if (e instanceof SlimeEntity && ((SlimeEntity) e).getSize() == 2 && near((Entity) e, bx, bz + 12) && Math.abs(((Entity) e).y - Y) < 6) smaller++;
		}
		results.check("fixes #25: the overkilled big slime split (" + smaller + " smaller slimes)", smaller == 4);
		results.check("fixes #29: the saddled pig dropped its saddle", dropped(world, bx, bz - 12, Item.SADDLE.id, -1));
		results.check("fixes #8: the leaves cut with the shears' last use dropped as leaves", dropped(world, bx + 12, bz + 12, Block.LEAVES.id, -1));
		results.check("fixes #9: the wooden slab mined by hand dropped", dropped(world, bx - 12, bz - 12, Block.SLAB.id, 2));
		return false;
	}

	// #10's table by block id (docs/bugs.md): beta's own lists, then what the fix adds. Obsidian (49) and redstone ore
	// (73, 74) stay off the pickaxe's.
	private static final Set<Integer> PICKAXE_FAST = ids(1, 4, 14, 15, 16, 21, 22, 24, 41, 42, 43, 44, 48, 56, 57, 79, 87,
			7, 23, 27, 28, 45, 52, 61, 62, 66, 67, 70, 71, 77, 89);
	private static final Set<Integer> AXE_FAST = ids(5, 17, 47, 54,
			25, 53, 58, 63, 64, 65, 68, 72, 84, 85, 86, 91, 95, 96);
	private static final Set<Integer> SHOVEL_FAST = ids(2, 3, 12, 13, 60, 78, 80, 82,
			88);

	private static Set<Integer> ids(int... ids) {
		Set<Integer> set = new HashSet<>();
		for (int id : ids) set.add(id);
		return set;
	}

	private static String fastWrong(Block block, Item tool, Set<Integer> fast) {
		boolean isFast = tool.getMiningSpeedMultiplier(new ItemStack(tool), block) > 1.0F;
		return isFast == fast.contains(block.id) ? "" : " " + block.id + (isFast ? " fast" : " slow") + " with " + tool.getTranslationKey();
	}

	private static boolean near(Entity e, int x, int z) {
		return Math.abs(e.x - (x + 0.5)) < 5 && Math.abs(e.z - (z + 0.5)) < 5;
	}

	private static boolean dropped(World world, int x, int z, int itemId, int meta) {
		List<?> entities = world.entities;
		for (Object e : entities) {
			if (e instanceof ItemEntity && near((Entity) e, x, z)) {
				ItemStack stack = ((ItemEntity) e).stack;
				if (stack.itemId == itemId && (meta < 0 || stack.getDamage() == meta)) return true;
			}
		}
		return false;
	}
}
