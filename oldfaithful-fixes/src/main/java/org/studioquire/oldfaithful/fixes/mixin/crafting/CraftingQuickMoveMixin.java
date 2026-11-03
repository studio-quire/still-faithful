package org.studioquire.oldfaithful.fixes.mixin.crafting;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * #45: shift-clicking the crafting result moves it into the inventory, topping up stacks of it and then taking one
 * empty slot for the rest, and uses up the ingredients if any of it moved. With no empty slot and only part of the
 * result's room, the rest stayed in the result slot, the ingredients were used anyway, and the fresh result that follows
 * replaced what was left: four torches crafted into room for two lost two. Now the result is taken only when all of it
 * fits, as a plain click takes it only whole. Runs where the screen's clicks are decided: the server, or single player.
 */
@Mixin({CraftingScreenHandler.class, PlayerScreenHandler.class})
abstract class CraftingQuickMoveMixin extends ScreenHandler {
	@Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$wholeResultOnly(int slot, CallbackInfoReturnable<ItemStack> cir) {
		if (slot != 0) return;
		// The inventory's slots, both screens: after the result and grid, up to the hotbar's end.
		int first = (Object) this instanceof PlayerScreenHandler ? 9 : 10;
		@SuppressWarnings("unchecked")
		List<Slot> slots = this.slots;
		Slot result = slots.get(0);
		if (result == null || !result.hasStack()) return;
		ItemStack stack = result.getStack();
		int room = 0;
		boolean empty = false;
		for (int i = first; i < first + 36; i++) {
			ItemStack s = slots.get(i).getStack();
			if (s == null) empty = true;
			else if (stack.isStackable() && s.itemId == stack.itemId && (!stack.hasSubtypes() || s.getDamage() == stack.getDamage()))
				room += Math.max(0, stack.getMaxCount() - s.count);
		}
		if (!empty && stack.count > room) cir.setReturnValue(null);
	}
}
