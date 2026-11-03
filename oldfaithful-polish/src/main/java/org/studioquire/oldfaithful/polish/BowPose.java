package org.studioquire.oldfaithful.polish;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * #34: beta has no pose for a bow, so players and skeletons hold it out flat like any item, its length pointing
 * forward. Here a bow takes the held-tool path (the item upright in the fist, as swords and tools are held), shifted
 * so the fist closes on the bow's middle rather than its end, and turned a little inwards. Third person only; the
 * first-person view is beta's.
 */
public final class BowPose {
	private BowPose() {
	}

	public static boolean isBow(ItemStack stack) {
		return stack != null && stack.itemId == Item.BOW.id;
	}

	/** Applied where the renderer asks whether the item is held like a tool, before the tool's own placing. */
	public static void apply() {
		GL11.glTranslatef(0.0F, -0.0625F, 0.3125F);
		GL11.glRotatef(-20.0F, 0.0F, 1.0F, 0.0F);
	}
}
