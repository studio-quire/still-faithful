package org.studioquire.oldfaithful.hardening.packet;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Blocks and items a server names must exist in this game. b1.7.3 looks them up without checking ({@code
 * Block.BLOCKS[id].material}, an item's renderer), so one block or item id the client doesn't know, from a malicious
 * server or a mismatched mod set, crashes the game later, in a tick or while drawing, far from the packet. Checked
 * where the packet is handled instead, so the client disconnects with the reason (ConnectionMixin turns the error
 * into a disconnect). A vanilla server never sends such ids; a modded server's blocks and items exist on a client
 * with the same mods.
 *
 * <p>Skipped when StationAPI is present: it replaces the block and item registries and their network format.
 */
public final class ServerContent {
	private static final boolean ENABLED = !FabricLoader.getInstance().isModLoaded("stationapi");

	private ServerContent() {
	}

	public static void checkItem(int id) {
		if (!ENABLED || id < 0) return; // negative: no item
		if (id >= Item.ITEMS.length || Item.ITEMS[id] == null) throw new UnknownContentException("item", id);
	}

	/**
	 * An item with its damage value, as drawn or named: the id must exist, and the item must be able to show that
	 * damage. Many items index arrays by it (a dye's name for damage 16 or -1, a slab's for 4), and fail only when
	 * the item is hovered or drawn, a crash far from the packet. Rather than list those items, the same calls the
	 * game makes to name and draw an item are tried here, so any item that can't take the value is refused, mods'
	 * items included.
	 */
	public static void checkItem(int id, int damage) {
		checkItem(id);
		if (ENABLED && id >= 0) probe(id, damage, new ItemStack(id, 1, damage));
	}

	/** An item a slot packet carries; vanilla reads a negative id as no item, so a stack here is never one. */
	public static void checkStack(ItemStack stack) {
		if (stack == null) return;
		checkItem(stack.itemId);
		if (ENABLED) probe(stack.itemId, stack.getDamage(), stack);
	}

	private static void probe(int id, int damage, ItemStack stack) {
		try {
			Item item = Item.ITEMS[id];
			item.getTranslationKey(stack); // the tooltip's name
			item.getTextureId(damage); // the icon
			item.getColorMultiplier(damage);
			if (id < Block.BLOCKS.length && Block.BLOCKS[id] != null) { // a block item is drawn as its block
				Block block = Block.BLOCKS[id];
				for (int side = 0; side < 6; side++) block.getTexture(side, damage);
				block.getColor(damage);
			}
		} catch (RuntimeException e) {
			throw new UnknownContentException("item " + id + " with damage value " + damage + ", which it can't show ("
					+ e + ")");
		}
	}

	/**
	 * Most a server-set position may be from the world's centre, in blocks: where vanilla's own server stops players
	 * ("Illegal position"). Far beyond it, positions overflow the game's integer block coordinates: past about
	 * 3.4e10 the entity search's loop over chunk columns never ends, on the next tick, outside packet handling.
	 */
	public static final double MAX_POSITION = 3.2e7;

	public static void checkPosition(String what, double x, double y, double z) {
		if (!(Math.abs(x) <= MAX_POSITION && Math.abs(y) <= MAX_POSITION && Math.abs(z) <= MAX_POSITION)) {
			throw new IllegalStateException("The server sent a " + what + " outside the world: " + x + ", " + y + ", " + z);
		}
	}

	public static void checkAngles(String what, float yaw, float pitch) {
		if (Float.isNaN(yaw) || Float.isInfinite(yaw) || Float.isNaN(pitch) || Float.isInfinite(pitch)) {
			throw new IllegalStateException("The server sent a " + what + " with angles " + yaw + ", " + pitch);
		}
	}

	public static void checkStacks(ItemStack[] stacks) {
		if (stacks == null) return;
		for (ItemStack stack : stacks) checkStack(stack);
	}

	public static void checkBlock(int id) {
		if (!ENABLED || id == 0) return;
		if (id < 0 || id >= Block.BLOCKS.length || Block.BLOCKS[id] == null) throw new UnknownContentException("block", id);
	}

	/**
	 * The block ids of one chunk section just loaded from the server (the chunk's own layout: index x << 11 | z << 7
	 * | y, ids as unsigned bytes).
	 */
	public static void checkBlocks(byte[] blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		if (!ENABLED || blocks == null) return;
		Block[] known = Block.BLOCKS;
		for (int x = minX; x < maxX; x++) {
			for (int z = minZ; z < maxZ; z++) {
				int column = x << 11 | z << 7;
				for (int y = minY; y < maxY; y++) {
					int index = column | y;
					if (index < 0 || index >= blocks.length) continue;
					int id = blocks[index] & 0xff;
					if (id != 0 && known[id] == null) throw new UnknownContentException("block", id);
				}
			}
		}
	}

	/**
	 * An entity set riding itself, or riding something that (through its own vehicles) rides it: every tick then
	 * walks the loop until the stack overflows, crashing the game. A real server never sends one.
	 */
	public static void checkRiding(Object entity, net.minecraft.entity.Entity vehicle) {
		int steps = 0;
		for (net.minecraft.entity.Entity v = vehicle; v != null && steps < 4096; v = v.vehicle, steps++) {
			if (v == entity) throw new IllegalStateException("The server set an entity riding itself (a loop of " + (steps + 1) + ")");
		}
	}

	/** Tracked entity values may change but not change type: the entity's code casts them to the type it set. */
	public static void checkTrackedValue(int index, Object current, Object incoming) {
		if (current != null && incoming != null && current.getClass() != incoming.getClass()) {
			throw new IllegalStateException("The server sent tracked value " + index + " as " + incoming.getClass().getSimpleName()
					+ " where the entity has " + current.getClass().getSimpleName());
		}
	}

	/** Bounds a list read from the network: entries per tracked-data packet (entities have at most 32 values). */
	public static final int MAX_TRACKED_ENTRIES = 256;

	public static void checkTrackedCount(List<?> entries) throws java.io.IOException {
		if (entries != null && entries.size() >= MAX_TRACKED_ENTRIES) {
			throw new java.io.IOException("Tracked entity data with more than " + MAX_TRACKED_ENTRIES + " entries");
		}
	}

	public static final class UnknownContentException extends IllegalStateException {
		UnknownContentException(String what, int id) {
			super("The server sent a " + what + " this game doesn't have (id " + id + ")");
		}

		UnknownContentException(String what) {
			super("The server sent an " + what);
		}
	}
}
