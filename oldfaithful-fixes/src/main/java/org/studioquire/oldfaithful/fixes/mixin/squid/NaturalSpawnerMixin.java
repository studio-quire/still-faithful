package org.studioquire.oldfaithful.fixes.mixin.squid;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.material.Material;
import net.minecraft.world.NaturalSpawner;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #66: a place for a water creature (squid) was any fluid, so squid spawned in lava lakes the spawner's walk reached
 * at the right height, and burnt there unseen. It now has to be water.
 */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
	// isValidSpawnPos's first isFluid is the water creatures' test; the second is the land mobs' (not in a fluid).
	@ModifyExpressionValue(method = "isValidSpawnPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/material/Material;isFluid()Z", ordinal = 0))
	private static boolean oldfaithful$waterOnly(boolean fluid, @Local(argsOnly = true) World world, @Local(ordinal = 0, argsOnly = true) int x,
			@Local(ordinal = 1, argsOnly = true) int y, @Local(ordinal = 2, argsOnly = true) int z) {
		return fluid && world.getMaterial(x, y, z) == Material.WATER;
	}
}
