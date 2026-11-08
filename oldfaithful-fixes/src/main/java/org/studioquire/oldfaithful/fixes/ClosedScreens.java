package org.studioquire.oldfaithful.fixes;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * #79 on a server: a player's own screen (inventory and hotbar, window 0) sends only while it's the open one, and each
 * slot only when it differs from its copy of what it last sent. While a chest, furnace, dispenser or workbench is open,
 * that screen sends the player's slots instead, and window 0's copies keep what it sent before the other one opened.
 * So on the tick after the client closes it, window 0 sends again every slot that changed meanwhile, though the client
 * already has them: a use in between (a block placed within about a round trip) is undone on the client by that
 * resend, and as the server's hand then ends as the use's packet said, nothing corrects it: one item too many shows
 * until the slot next changes.
 * <p>
 * When the client closes a screen, window 0 takes the closed screen's copies for the slots both show (the 36 slots of
 * the player's inventory), so it sends only what the client wasn't told. Except a slot the closed screen sent within
 * {@link #RECENT_NANOS}: that packet may have crossed the close, and the client drops a packet for a window it has
 * closed, so its value is sent again at once on window 0 (beta's resend covered that by accident). Armour and the
 * crafting grid aren't on the other screens and keep beta's copies, so armour worn down meanwhile is still sent.
 * A screen the server closes (out of reach, a portal) keeps beta's resend: the client's clicks in flight are dropped
 * there, so the client's slots aren't known.
 */
public final class ClosedScreens {
	/** More than a round trip with beta's network threads, which wait up to 100 ms on each side. */
	static final long RECENT_NANOS = 1_000_000_000L;

	/** Mixed into every screen handler: when it last sent each slot to its player, sends while it opens excepted. */
	public interface Sends {
		void oldfaithful$sent(int slot);

		boolean oldfaithful$sentWithin(int slot, long nanos);
	}

	private ClosedScreens() {
	}

	/** Called after the client closed {@code closed}, once the player's own screen is the open one again. */
	public static void carry(ServerPlayerEntity player, ScreenHandler closed) {
		ScreenHandler own = player.playerScreenHandler;
		Sends sends = (Sends) closed;
		for (int i = 0; i < player.inventory.size(); i++) {
			Slot mine = own.getSlot(player.inventory, i);
			Slot theirs = closed.getSlot(player.inventory, i);
			if (mine == null || theirs == null) continue;
			if (sends.oldfaithful$sentWithin(theirs.id, RECENT_NANOS)) {
				ItemStack now = ItemStack.clone(mine.getStack());
				own.trackedStacks.set(mine.id, now);
				player.onSlotUpdate(own, mine.id, now);
			} else {
				own.trackedStacks.set(mine.id, ItemStack.clone((ItemStack) closed.trackedStacks.get(theirs.id)));
			}
		}
	}
}
