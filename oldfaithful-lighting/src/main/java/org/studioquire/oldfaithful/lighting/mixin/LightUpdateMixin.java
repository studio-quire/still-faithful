package org.studioquire.oldfaithful.lighting.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * An update skips a column unless the chunks of all eight columns around it are loaded, though the rule only reads
 * the four beside it: a column in a chunk's corner was skipped while the diagonal chunk was missing, and nothing
 * came back for it (the diagonal chunk shares no edge with it). Here a column is skipped only while a chunk holding
 * one of its own neighbours is missing, and that chunk's check covers it when it enters (Relight).
 */
@Mixin(LightUpdate.class)
abstract class LightUpdateMixin {
	@org.spongepowered.asm.mixin.injection.Inject(method = "updateLight", at = @At("HEAD"))
	private void oldfaithful$count(World world, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (org.studioquire.oldfaithful.lighting.Stats.ENABLED) org.studioquire.oldfaithful.lighting.Stats.update();
	}

	@WrapOperation(method = "updateLight", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isRegionLoaded(IIII)Z"))
	private boolean oldfaithful$besideLoaded(World world, int x, int y, int z, int range, Operation<Boolean> original) {
		if (range != 1) return original.call(world, x, y, z, range);
		int lx = x & 15, lz = z & 15;
		if (!world.isPosLoaded(x, 64, z)) return false;
		if (lx == 0 && !world.isPosLoaded(x - 1, 64, z) || lx == 15 && !world.isPosLoaded(x + 1, 64, z)) return false;
		return !(lz == 0 && !world.isPosLoaded(x, 64, z - 1) || lz == 15 && !world.isPosLoaded(x, 64, z + 1));
	}
}
