package org.studioquire.oldfaithful.polish.mixin.obsidian;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.Obsidian;

/** A pickaxe mines obsidian at its own speed, as from Beta 1.9 ({@link Obsidian}). */
@Mixin(ToolItem.class)
abstract class ToolItemMixin {
	@Shadow
	private float miningSpeed;

	@ModifyReturnValue(method = "getMiningSpeedMultiplier", at = @At("RETURN"))
	private float oldfaithful$pickaxeOnObsidian(float speed, ItemStack stack, Block block) {
		return speed == 1.0F && Obsidian.fastFor((ToolItem) (Object) this, block) ? this.miningSpeed : speed;
	}
}
