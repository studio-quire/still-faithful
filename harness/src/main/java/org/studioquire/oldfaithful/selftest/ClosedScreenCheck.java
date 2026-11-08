package org.studioquire.oldfaithful.selftest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;

/**
 * #79 in `:harness:fixesMultiplayerTest`, on the hidden client, with what it receives held back for half a second at
 * a time ({@link #holdIncoming}) so the order of things is the same on every run:
 * <ol>
 * <li>A chest 2 blocks north holding 8 cobblestone, the player's inventory empty. The client opens it and clicks the
 * cobblestone into hotbar slot 0 (the server takes both clicks without a word, as the client made them).</li>
 * <li>Holding back what comes in, the client closes the chest, and half a second later places a cobblestone 2 blocks
 * south from that slot (8 to 7 at once). Then it reads again. Beta's server, on the tick after the close, sends the
 * player's own slots that changed while the chest was open (hotbar slot 0: 8), which the client now reads after its own
 * 7; the server's placement makes 7 there and, as the client's packet said 7, sends nothing: the client shows 8.
 * With the fix nothing is resent, and the client shows 7. Then a full resend of the inventory confirms the server's 7.</li>
 * <li>The other side of the fix: the chest opened again, and in one tick, holding back what comes in, the client asks
 * for 3 dirt in slot 9 ("!oftest give", sent to it on the chest's window) and closes the chest. The client drops that
 * packet (its window is closed), so the dirt must come again on the player's own window: by beta's resend, or the
 * fix's resend of a slot the closed screen sent within the last second.</li>
 * </ol>
 */
public final class ClosedScreenCheck {
	public static volatile boolean holdIncoming;

	private static final int HOLD_TICKS = 10;

	private final SelfTest results;
	private final boolean fixedServer;
	private final String label;
	private final int px, y, pz;
	private int step, ticks;
	private int predicted = -1;
	private final List<Integer> seen = new ArrayList<>();

	ClosedScreenCheck(SelfTest results, boolean fixedServer, String label, int px, int y, int pz) {
		this.results = results;
		this.fixedServer = fixedServer;
		this.label = label;
		this.px = px;
		this.y = y;
		this.pz = pz;
	}

	/** One tick; true when done. */
	boolean tick(Minecraft mc) {
		ticks++;
		ItemStack hand = mc.player.inventory.main[0];
		switch (step) {
			case 0: // the chest and an empty inventory
				if (ticks == 1) {
					mc.player.inventory.selectedSlot = 0;
					mc.player.sendChatMessage("!oftest chest " + px + " " + y + " " + (pz - 2));
				}
				if (ticks < 20 || mc.world.getBlockId(px, y, pz - 2) != 54 || hand != null || mc.world.getBlockId(px, y, pz + 2) != 0) return timeout(400, "the chest");
				next();
				return false;
			case 1: // open it
				if (ticks == 1) mc.interactionManager.interactBlock(mc.player, mc.world, null, px, y, pz - 2, 1);
				if (!chestOpen(mc) || !count(mc.player.currentScreenHandler.getSlot(0).getStack(), 4, 8)) return timeout(200, "the chest opening with 8 cobblestone");
				next();
				return false;
			case 2: { // cobblestone into hotbar slot 0 (the chest's slot 54)
				int syncId = mc.player.currentScreenHandler.syncId;
				if (ticks == 1) mc.interactionManager.clickSlot(syncId, 0, 0, false, mc.player);
				if (ticks == 2) mc.interactionManager.clickSlot(syncId, 54, 0, false, mc.player);
				if (ticks < 20) return false;
				if (!count(hand, 4, 8) || mc.player.inventory.getCursorStack() != null) return fail("the clicks: hand " + hand + ", cursor " + mc.player.inventory.getCursorStack());
				next();
				return false;
			}
			case 3: // close, place half a second later, then read what came meanwhile
				if (ticks == 1) {
					holdIncoming = true;
					mc.player.closeHandledScreen();
				}
				if (ticks == 1 + HOLD_TICKS) {
					mc.player.inventory.selectedSlot = 0;
					mc.interactionManager.interactBlock(mc.player, mc.world, hand, px, y - 1, pz + 2, 1);
					predicted = countOf(mc.player.inventory.main[0]);
					seen.add(predicted);
				}
				if (ticks == 2 + HOLD_TICKS) holdIncoming = false;
				if (ticks > 2 + HOLD_TICKS && countOf(hand) != seen.get(seen.size() - 1)) seen.add(countOf(hand));
				if (ticks < 60) return false;
				placeResults(mc, hand);
				mc.player.sendChatMessage("!oftest sync");
				next();
				return false;
			case 4: // the server's own count, by a full resend
				if (ticks < 20) return false;
				results.check("#79 (" + label + "): the server holds 7 after the placement (a full resend shows " + countOf(hand) + ")", countOf(hand) == 7);
				next();
				return false;
			case 5: // open again
				if (ticks == 1) mc.interactionManager.interactBlock(mc.player, mc.world, hand, px, y, pz - 2, 1);
				if (!chestOpen(mc) || ticks < 10) return timeout(200, "the chest opening again");
				next();
				return false;
			case 6: // a slot sent on the chest's window as it closes
				if (ticks == 1) {
					holdIncoming = true;
					mc.player.sendChatMessage("!oftest give");
					mc.player.closeHandledScreen();
				}
				if (ticks == 1 + HOLD_TICKS) holdIncoming = false;
				if (ticks < 50) return false;
				ItemStack given = mc.player.inventory.main[9];
				results.check("#79 (" + label + "): dirt given as the chest closed reaches the client (slot 9: " + given + ")", count(given, 3, 3));
				return true;
			default:
				return true;
		}
	}

	private void placeResults(Minecraft mc, ItemStack hand) {
		boolean placed = mc.world.getBlockId(px, y, pz + 2) == 4;
		String what = "predicted " + predicted + ", then " + seen + " as the client read on, ending at " + countOf(hand) + (placed ? "" : "; the block isn't there");
		if (fixedServer) {
			results.check("#79 (" + label + "): a block placed just after closing a chest leaves 7 of 8 (" + what + ")", placed && predicted == 7 && seen.size() == 1 && countOf(hand) == 7);
		} else {
			results.check("#79 control (" + label + "): beta's resend after the close puts the placed block back in the hand (" + what + ")", placed && predicted == 7 && countOf(hand) == 8);
		}
	}

	private static boolean chestOpen(Minecraft mc) {
		ScreenHandler h = mc.player.currentScreenHandler;
		return h instanceof GenericContainerScreenHandler && h != mc.player.playerScreenHandler;
	}

	private static boolean count(ItemStack stack, int id, int count) {
		return stack != null && stack.itemId == id && stack.count == count;
	}

	private static int countOf(ItemStack stack) {
		return stack == null ? 0 : stack.count;
	}

	private void next() {
		step++;
		ticks = 0;
	}

	private boolean timeout(int limit, String what) {
		if (ticks < limit) return false;
		return fail("timed out waiting for " + what);
	}

	private boolean fail(String what) {
		holdIncoming = false;
		results.fail("#79 (" + label + "), step " + step + ": " + what, new AssertionError());
		return true;
	}
}
