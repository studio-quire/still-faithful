package org.studioquire.oldfaithful.polish.mixin.shiftclick;

import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The furnace's own fuel rule, so whatever another mod makes burnable counts too. */
@Mixin(FurnaceBlockEntity.class)
public interface FurnaceBlockEntityInvoker {
	@Invoker("getFuelTime")
	int polish$fuelTime(ItemStack stack);
}
