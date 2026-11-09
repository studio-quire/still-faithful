package org.studioquire.oldfaithful.polish;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.SmeltingRecipeManager;
import net.minecraft.screen.DispenserScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.studioquire.oldfaithful.polish.mixin.shiftclick.FurnaceBlockEntityInvoker;
import org.studioquire.oldfaithful.polish.mixin.shiftclick.FurnaceScreenHandlerAccessor;

/**
 * {@code inventory.shiftClick}: where beta's shift-click stopped short (docs/still-faithful.md > Shift-click). It moved a
 * stack between a container and the inventory, or between the inventory and the hotbar, but did nothing in a dispenser,
 * never put anything into a furnace and never put armour on. Those three moves are made here of beta's own clicks (pick
 * the stack up, put it down where it goes, put back what didn't fit), each checked after it's made; anything unexpected
 * ends the move where it stands, never with a swap. Every other shift-click is beta's own.
 * <p>
 * Clicks rather than a change to the screens' own shift-click: a server accepts a client's click by comparing only the
 * clicked slot, and then sends nothing back, so a client whose shift-click went somewhere other than the server's would
 * keep items that aren't there. Clicks are what any server already checks, so it works on any server, and a server
 * needs nothing.
 */
public final class ShiftClicks {
	private final Minecraft mc;
	private final ScreenHandler handler;

	private ShiftClicks(Minecraft mc, ScreenHandler handler) {
		this.mc = mc;
		this.handler = handler;
	}

	/** A shift-click on a slot with nothing held: one of the three moves where it applies, else beta's shift-click. */
	public static void quickMove(Minecraft mc, ScreenHandler handler, int id, int button) {
		ShiftClicks clicks = new ShiftClicks(mc, handler);
		ItemStack there = handler.getSlot(id).getStack();
		int[] targets = there == null ? null : clicks.targets(id, there);
		if (targets == null) clicks.click(id, button, true);
		else clicks.move(id, targets);
	}

	/** Where the move goes, in order; null where beta's shift-click is the one. Only vanilla's own screens. */
	private int[] targets(int id, ItemStack stack) {
		Class<?> type = handler.getClass();
		int n = handler.slots.size();
		if (type == PlayerScreenHandler.class && n == 45) {
			// 0 result, 1-4 grid, 5-8 armour (head first, as ArmorItem.equipmentSlot counts), 9-44 inventory
			if (id < 9 || !(stack.getItem() instanceof ArmorItem)) return null;
			int t = 5 + ((ArmorItem) stack.getItem()).equipmentSlot;
			return t <= 8 && stack(t) == null && slot(t).canInsert(stack) ? new int[] {t} : null;
		}
		if (type == FurnaceScreenHandler.class && n == 39) {
			// 0 what smelts, 1 fuel, 2 output, 3-38 inventory. Into its place or nowhere: if that holds something else,
			// nothing moves, as in later versions.
			if (id < 3) return null;
			if (SmeltingRecipeManager.getInstance().craft(stack.itemId) != null) return new int[] {0};
			FurnaceBlockEntityInvoker furnace = (FurnaceBlockEntityInvoker) ((FurnaceScreenHandlerAccessor) handler).polish$furnace();
			return furnace.polish$fuelTime(stack) > 0 ? new int[] {1} : null;
		}
		if (type == DispenserScreenHandler.class && n == 45) {
			// 0-8 the dispenser, 9-44 inventory: as a chest's, out to the hotbar's end first
			return id < 9 ? range(9, 45, true) : range(0, 9, false);
		}
		return null;
	}

	/**
	 * Beta's own shift-click into a run of slots (insertItem): onto the same item first, then into an empty slot, in order;
	 * what doesn't fit stays where it was. Picked up, put down slot by slot, the rest put back.
	 */
	private void move(int from, int[] targets) {
		ItemStack moving = stack(from);
		boolean any = false;
		for (int t : targets) any |= room(slot(t), moving) > 0;
		if (!any || !pickUp(from)) return;
		for (int pass = 0; pass < 2; pass++) {
			for (int t : targets) {
				ItemStack held = cursor();
				if (held == null) break;
				if ((stack(t) == null) == (pass == 0) || room(slot(t), held) <= 0) continue;
				if (!put(t)) {
					restore(from);
					return;
				}
			}
		}
		restore(from);
	}

	// ---- single clicks, checked

	private void click(int id, int button, boolean shift) {
		mc.interactionManager.clickSlot(handler.syncId, id, button, shift, mc.player);
	}

	/** An empty-handed left click taking the whole stack. */
	private boolean pickUp(int id) {
		ItemStack there = stack(id);
		if (cursor() != null || there == null) return false;
		ItemStack expect = there.copy();
		click(id, 0, false);
		ItemStack held = cursor();
		return held != null && same(held, expect) && held.count == expect.count && stack(id) == null;
	}

	/** A left click putting all that fits of what's held into the slot; never one that would swap or take. */
	private boolean put(int id) {
		ItemStack held = cursor();
		Slot slot = slot(id);
		if (held == null) return false;
		int room = room(slot, held);
		if (room <= 0) return false;
		int expect = Math.min(held.count, room);
		int before = slot.getStack() == null ? 0 : slot.getStack().count, had = held.count;
		ItemStack item = held.copy();
		click(id, 0, false);
		ItemStack now = slot.getStack(), after = cursor();
		return now != null && same(now, item) && now.count == before + expect && (after == null ? 0 : after.count) == had - expect
				&& (after == null || same(after, item));
	}

	/** What's held back where it came from, if that's still empty. */
	private void restore(int from) {
		if (cursor() != null && stack(from) == null) put(from);
	}

	// ---- reading

	private ItemStack cursor() {
		return mc.player.inventory.getCursorStack();
	}

	private Slot slot(int id) {
		return handler.getSlot(id);
	}

	private ItemStack stack(int id) {
		return slot(id).getStack();
	}

	/** Beta's own test for one stack going onto another (and Faithful Core's, where it puts data on stacks). */
	private static boolean same(ItemStack a, ItemStack b) {
		if (a.itemId != b.itemId || a.hasSubtypes() && a.getDamage() != b.getDamage()) return false;
		return CanMerge.HANDLE == null || CanMerge.test(a, b);
	}

	/** How many of the item a click could add to the slot: up to the slot's and the item's limit, empty or the same item. */
	private static int room(Slot slot, ItemStack item) {
		if (!slot.canInsert(item)) return 0;
		int cap = Math.min(item.getMaxCount(), slot.getMaxItemCount());
		ItemStack there = slot.getStack();
		if (there == null) return cap;
		return same(there, item) ? Math.max(0, cap - there.count) : 0;
	}

	private static int[] range(int from, int to, boolean backwards) {
		int[] r = new int[to - from];
		for (int i = 0; i < r.length; i++) r[i] = backwards ? to - 1 - i : from + i;
		return r;
	}

	/**
	 * Faithful Core's Stacks.canMerge where the core is installed: stacks with different data don't merge, and a click
	 * that seems to put one onto the other would swap them. It only works where content mods weave the core's stack data
	 * in, so it's tried once.
	 */
	private static final class CanMerge {
		static final MethodHandle HANDLE = find();

		static boolean test(ItemStack a, ItemStack b) {
			try {
				return (boolean) HANDLE.invokeExact(a, b);
			} catch (Throwable t) {
				throw new IllegalStateException(t);
			}
		}

		private static MethodHandle find() {
			try {
				Class<?> stacks = Class.forName("org.studioquire.oldfaithful.core.api.Stacks");
				MethodHandle h = MethodHandles.publicLookup().findStatic(stacks, "canMerge", MethodType.methodType(boolean.class, ItemStack.class, ItemStack.class));
				boolean ignored = (boolean) h.invokeExact(new ItemStack(1, 1, 0), new ItemStack(1, 1, 0));
				System.out.println("[stillfaithful-polish] Shift-click's moves merge stacks by Faithful Core's rule (data included)");
				return h;
			} catch (Throwable t) {
				return null;
			}
		}
	}
}
