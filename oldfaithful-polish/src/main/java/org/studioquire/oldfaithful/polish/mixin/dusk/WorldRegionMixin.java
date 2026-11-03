package org.studioquire.oldfaithful.polish.mixin.dusk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.WorldRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.studioquire.oldfaithful.polish.DuskLight;

/**
 * #6: while a chunk is built, the luminances its renderers read from the rebuild's region are DuskLight's (1, or a
 * code standing for the light there); everywhere else, and in the pass that keeps beta's colours, the game's own.
 */
@Mixin(WorldRegion.class)
abstract class WorldRegionMixin {
	@WrapMethod(method = "getNaturalBrightness")
	private float oldfaithful$pairs(int x, int y, int z, int blockLight, Operation<Float> original) {
		return DuskLight.answers(this) ? DuskLight.luminance(x, y, z, blockLight) : original.call(x, y, z, blockLight);
	}

	@WrapMethod(method = "method_1782")
	private float oldfaithful$pairs(int x, int y, int z, Operation<Float> original) {
		return DuskLight.answers(this) ? DuskLight.luminance(x, y, z, -1) : original.call(x, y, z);
	}
}
