package org.studioquire.oldfaithful.polish.mixin.shiftclick;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.InteractionManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.ShiftClicks;

/** A shift-click on a slot with nothing held goes through ShiftClicks, which makes beta's own one where nothing's changed. */
@Mixin(HandledScreen.class)
abstract class HandledScreenMixin extends Screen {
	@Shadow
	public ScreenHandler handler;

	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/InteractionManager;clickSlot(IIIZLnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/item/ItemStack;"))
	private ItemStack polish$shiftClick(InteractionManager manager, int syncId, int slot, int button, boolean shift, PlayerEntity player,
			Operation<ItemStack> original) {
		if (!shift || slot < 0 || player.inventory.getCursorStack() != null) return original.call(manager, syncId, slot, button, shift, player);
		ShiftClicks.quickMove(minecraft, handler, slot, button);
		return null;
	}
}
