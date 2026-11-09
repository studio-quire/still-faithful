package org.studioquire.oldfaithful.polish.mixin.fuel;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.WoodenFuel;

/** Wooden things beta's furnace wouldn't burn (WoodenFuel). Only where beta's answer is 0, so other mods' fuels stand. */
@Mixin(FurnaceBlockEntity.class)
abstract class FurnaceBlockEntityMixin {
	@ModifyReturnValue(method = "getFuelTime", at = @At("RETURN"))
	private int oldfaithful$woodenFuel(int ticks, ItemStack stack) {
		return ticks == 0 && stack != null ? WoodenFuel.ticks(stack) : ticks;
	}
}
