package org.studioquire.oldfaithful.fixes.mixin.lastuse;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.fixes.LastUse;

/**
 * #8 on a server ({@link LastUse}): there the harvest check itself came after the tool was gone, so the block mined
 * with a pickaxe's last use (stone, ore, obsidian) dropped nothing.
 */
@Mixin(ServerPlayerInteractionManager.class)
abstract class ServerPlayerInteractionManagerMixin {
	@WrapOperation(method = "tryBreakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;clearStackInHand()V"))
	private void oldfaithful$keepForDrops(PlayerEntity player, Operation<Void> original,
			@Share("player") LocalRef<PlayerEntity> keptBy, @Share("tool") LocalRef<ItemStack> kept) {
		keptBy.set(player);
		kept.set(player.getHand());
	}

	@Inject(method = "tryBreakBlock", at = @At("RETURN"))
	private void oldfaithful$thenRemove(int x, int y, int z, CallbackInfoReturnable<Boolean> cir,
			@Share("player") LocalRef<PlayerEntity> keptBy, @Share("tool") LocalRef<ItemStack> kept) {
		LastUse.finish(keptBy.get(), kept.get());
	}
}
