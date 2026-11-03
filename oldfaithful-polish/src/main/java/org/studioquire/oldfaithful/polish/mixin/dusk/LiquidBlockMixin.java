package org.studioquire.oldfaithful.polish.mixin.dusk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.LiquidBlock;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.studioquire.oldfaithful.polish.DuskLight;

/**
 * #6: a liquid's luminance is the brighter of two readings, a choice that depends on the darkness; while a chunk is
 * built DuskLight reads the pair that choice comes to (the larger sky light and the larger block light).
 */
@Mixin(LiquidBlock.class)
abstract class LiquidBlockMixin {
	@WrapMethod(method = "getLuminance")
	private float oldfaithful$brighterPair(BlockView view, int x, int y, int z, Operation<Float> original) {
		return DuskLight.answers(view) ? DuskLight.liquidLuminance(x, y, z) : original.call(view, x, y, z);
	}
}
