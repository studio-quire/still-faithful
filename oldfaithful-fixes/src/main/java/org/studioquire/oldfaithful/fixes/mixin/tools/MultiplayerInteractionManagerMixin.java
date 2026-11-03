package org.studioquire.oldfaithful.fixes.mixin.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.MultiplayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.fixes.ServerMarker;
import org.studioquire.oldfaithful.fixes.ToolSpeeds;

/**
 * #10 on a multiplayer client ({@link ToolSpeeds}): beta's speeds unless the server has the fix too
 * ({@link ServerMarker}), since a vanilla server refuses breaks reported much sooner than its own timing.
 */
@Mixin(MultiplayerInteractionManager.class)
abstract class MultiplayerInteractionManagerMixin {
	@WrapOperation(method = {"attackBlock", "processBlockBreakingAction"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F"))
	private float oldfaithful$speedAsServer(Block block, PlayerEntity player, Operation<Float> original) {
		if (ToolSpeeds.serverAgrees) return original.call(block, player);
		ToolSpeeds.asBeta++;
		try {
			return original.call(block, player);
		} finally {
			ToolSpeeds.asBeta--;
		}
	}
}
