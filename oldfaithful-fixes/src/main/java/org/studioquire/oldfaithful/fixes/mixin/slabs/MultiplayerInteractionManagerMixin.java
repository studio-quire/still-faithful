package org.studioquire.oldfaithful.fixes.mixin.slabs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.InteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MultiplayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.fixes.ServerMarker;
import org.studioquire.oldfaithful.fixes.WoodenSlabs;

/**
 * #9 on a multiplayer client ({@link WoodenSlabs}): the mining speed the client shows, when the server has the fix too
 * ({@link ServerMarker}). Drops are the server's. A server without it refuses a break reported much sooner than its own
 * timing and, while it finishes that one, drops a second early report outright (the block comes back 4 s later), so
 * then the client keeps beta's speed.
 */
@Mixin(MultiplayerInteractionManager.class)
abstract class MultiplayerInteractionManagerMixin extends InteractionManager {
	MultiplayerInteractionManagerMixin(Minecraft minecraft) {
		super(minecraft);
	}

	@WrapOperation(method = {"attackBlock", "processBlockBreakingAction"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F"))
	private float oldfaithful$woodenSlabSpeed(Block block, PlayerEntity player, Operation<Float> original, int x, int y, int z, int side) {
		float vanilla = original.call(block, player);
		return WoodenSlabs.serverAgrees ? WoodenSlabs.hardness(block, player, vanilla, this.minecraft.world, x, y, z) : vanilla;
	}
}
