package org.studioquire.oldfaithful.polish.mixin.glowstone;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.Glowstone;

/** Glowstone harvestable by anything ({@link Glowstone}): where the game decides whether a player can harvest a block. */
@Mixin(PlayerInventory.class)
abstract class PlayerInventoryMixin {
	@Shadow
	public PlayerEntity player;

	@ModifyReturnValue(method = "isUsingEffectiveTool", at = @At("RETURN"))
	private boolean oldfaithful$glowstoneByHand(boolean vanilla, Block block) {
		return vanilla || Glowstone.harvestable(this.player, block);
	}
}
