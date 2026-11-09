package org.studioquire.oldfaithful.polish.mixin.shiftclick;

import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.screen.FurnaceScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FurnaceScreenHandler.class)
public interface FurnaceScreenHandlerAccessor {
	@Accessor("furnaceBlockEntity")
	FurnaceBlockEntity polish$furnace();
}
