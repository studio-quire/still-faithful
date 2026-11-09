package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The furnace's own fuel table, the one gate its tick uses to light and consume fuel. */
@Mixin(FurnaceBlockEntity.class)
public interface FurnaceFuel {
	@Invoker("getFuelTime")
	int oldfaithful$fuelTime(ItemStack stack);
}
