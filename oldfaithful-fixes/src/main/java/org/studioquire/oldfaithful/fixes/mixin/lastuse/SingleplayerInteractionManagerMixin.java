package org.studioquire.oldfaithful.fixes.mixin.lastuse;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.SingleplayerInteractionManager;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.fixes.LastUse;

/** #8 in single player ({@link LastUse}): shears' last cut of leaves drops the leaves. */
@Mixin(SingleplayerInteractionManager.class)
abstract class SingleplayerInteractionManagerMixin {
	@WrapOperation(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ClientPlayerEntity;clearStackInHand()V"))
	private void oldfaithful$keepForDrops(ClientPlayerEntity player, Operation<Void> original,
			@Share("player") LocalRef<PlayerEntity> keptBy, @Share("tool") LocalRef<ItemStack> kept) {
		keptBy.set(player);
		kept.set(player.getHand());
	}

	@Inject(method = "breakBlock", at = @At("RETURN"))
	private void oldfaithful$thenRemove(int x, int y, int z, int direction, CallbackInfoReturnable<Boolean> cir,
			@Share("player") LocalRef<PlayerEntity> keptBy, @Share("tool") LocalRef<ItemStack> kept) {
		LastUse.finish(keptBy.get(), kept.get());
	}
}
