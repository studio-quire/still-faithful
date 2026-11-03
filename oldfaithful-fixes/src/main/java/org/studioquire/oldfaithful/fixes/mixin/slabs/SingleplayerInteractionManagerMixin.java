package org.studioquire.oldfaithful.fixes.mixin.slabs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.block.Block;
import net.minecraft.client.InteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SingleplayerInteractionManager;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.fixes.WoodenSlabs;

/** #9 in single player ({@link WoodenSlabs}): the mining speed, and the drop. */
@Mixin(SingleplayerInteractionManager.class)
abstract class SingleplayerInteractionManagerMixin extends InteractionManager {
	SingleplayerInteractionManagerMixin(Minecraft minecraft) {
		super(minecraft);
	}

	@WrapOperation(method = {"attackBlock", "processBlockBreakingAction"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F"))
	private float oldfaithful$woodenSlabSpeed(Block block, PlayerEntity player, Operation<Float> original, int x, int y, int z, int side) {
		return WoodenSlabs.hardness(block, player, original.call(block, player), this.minecraft.world, x, y, z);
	}

	// The block is gone by the time the game asks whether it drops, so its kind is read first.
	@Inject(method = "breakBlock", at = @At("HEAD"))
	private void oldfaithful$readKind(int x, int y, int z, int direction, CallbackInfoReturnable<Boolean> cir,
			@Share("wooden") LocalBooleanRef wooden) {
		wooden.set(WoodenSlabs.isWooden(this.minecraft.world, x, y, z));
	}

	@WrapOperation(method = "breakBlock",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ClientPlayerEntity;canHarvest(Lnet/minecraft/block/Block;)Z"))
	private boolean oldfaithful$woodenSlabDrops(ClientPlayerEntity player, Block block, Operation<Boolean> original,
			@Share("wooden") LocalBooleanRef wooden) {
		return original.call(player, block) || wooden.get();
	}
}
