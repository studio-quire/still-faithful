package org.studioquire.oldfaithful.fixes.mixin.burnout;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** #44: the tick a redstone torch's turn-off was recorded at. */
@Mixin(targets = "net.minecraft.block.RedstoneTorchBurnoutEntry")
public interface BurnoutEntryAccessor {
	@Accessor("time")
	long oldfaithful$time();
}
