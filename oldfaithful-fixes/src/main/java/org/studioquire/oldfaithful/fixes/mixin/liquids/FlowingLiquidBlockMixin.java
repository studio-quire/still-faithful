package org.studioquire.oldfaithful.fixes.mixin.liquids;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.FlowingLiquidBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #19: flowing sideways, a liquid replaces what it may wash away (torches, rails, redstone, flowers, crops) through
 * a step that drops it, or for lava fizzes; flowing down, it set the block below directly, so the same things
 * vanished. The downward flow now does what the sideways one does before setting the block.
 */
@Mixin(FlowingLiquidBlock.class)
abstract class FlowingLiquidBlockMixin extends LiquidBlock {
	FlowingLiquidBlockMixin(int id, Material material) {
		super(id, material);
	}

	// In this method, the only five-argument setBlock calls are the two of the downward flow.
	@WrapOperation(method = "onTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlock(IIIII)Z"))
	private boolean oldfaithful$washAway(World world, int x, int y, int z, int id, int meta, Operation<Boolean> original) {
		int below = world.getBlockId(x, y, z);
		if (below > 0) {
			if (this.material == Material.LAVA) {
				this.fizz(world, x, y, z);
			} else {
				Block.BLOCKS[below].dropStacks(world, x, y, z, world.getBlockMeta(x, y, z));
			}
		}
		return original.call(world, x, y, z, id, meta);
	}
}
