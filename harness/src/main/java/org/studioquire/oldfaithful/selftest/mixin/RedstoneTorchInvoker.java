package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** FixesCheck (#44): records a redstone torch's turn-off as the game does. */
@Mixin(RedstoneTorchBlock.class)
public interface RedstoneTorchInvoker {
	@Invoker("isBurnedOut")
	boolean oldfaithful$isBurnedOut(World world, int x, int y, int z, boolean addNew);
}
