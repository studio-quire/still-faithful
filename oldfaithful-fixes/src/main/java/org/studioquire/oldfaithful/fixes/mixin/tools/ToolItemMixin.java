package org.studioquire.oldfaithful.fixes.mixin.tools;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.fixes.ToolSpeeds;

/**
 * #10 ({@link ToolSpeeds}): a tool's speed on a block its list missed. Everything that times a break (both client
 * interaction managers, the server's checks) asks this through the player's inventory, so one place serves all.
 */
@Mixin(ToolItem.class)
abstract class ToolItemMixin {
	@Shadow
	private float miningSpeed;

	@ModifyReturnValue(method = "getMiningSpeedMultiplier", at = @At("RETURN"))
	private float oldfaithful$speedByMaterial(float speed, ItemStack stack, Block block) {
		return speed == 1.0F && ToolSpeeds.addedFor((ToolItem) (Object) this, block) ? this.miningSpeed : speed;
	}
}
