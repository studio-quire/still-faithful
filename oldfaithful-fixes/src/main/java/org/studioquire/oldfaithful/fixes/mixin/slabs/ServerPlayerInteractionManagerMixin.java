package org.studioquire.oldfaithful.fixes.mixin.slabs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.fixes.WoodenSlabs;

/** #9 on a server ({@link WoodenSlabs}): the mining time it accepts, and the drop. */
@Mixin(ServerPlayerInteractionManager.class)
abstract class ServerPlayerInteractionManagerMixin {
	@Shadow
	private ServerWorld world;
	@Shadow
	private int miningX;
	@Shadow
	private int miningY;
	@Shadow
	private int miningZ;

	@WrapOperation(method = "onBlockBreakingAction",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F"))
	private float oldfaithful$woodenSlabSpeedAtStart(Block block, PlayerEntity player, Operation<Float> original, int x, int y, int z, int direction) {
		return WoodenSlabs.hardness(block, player, original.call(block, player), this.world, x, y, z);
	}

	@WrapOperation(method = "continueMining",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F"))
	private float oldfaithful$woodenSlabSpeed(Block block, PlayerEntity player, Operation<Float> original, int x, int y, int z) {
		return WoodenSlabs.hardness(block, player, original.call(block, player), this.world, x, y, z);
	}

	// The server finishing, on its own timing, a break the client reported early.
	@WrapOperation(method = "update",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F"))
	private float oldfaithful$woodenSlabSpeedLater(Block block, PlayerEntity player, Operation<Float> original) {
		return WoodenSlabs.hardness(block, player, original.call(block, player), this.world, this.miningX, this.miningY, this.miningZ);
	}

	@Inject(method = "tryBreakBlock", at = @At("HEAD"))
	private void oldfaithful$readKind(int x, int y, int z, CallbackInfoReturnable<Boolean> cir, @Share("wooden") LocalBooleanRef wooden) {
		wooden.set(WoodenSlabs.isWooden(this.world, x, y, z));
	}

	@WrapOperation(method = "tryBreakBlock",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;canHarvest(Lnet/minecraft/block/Block;)Z"))
	private boolean oldfaithful$woodenSlabDrops(PlayerEntity player, Block block, Operation<Boolean> original,
			@Share("wooden") LocalBooleanRef wooden) {
		return original.call(player, block) || wooden.get();
	}
}
