package org.studioquire.oldfaithful.fixes.mixin.sleep;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #26: a monster sent to wake a sleeping player counts as arriving when the end of its path is within 1.5 blocks of
 * the player on each axis. The path's end is a block position (its corner), the player an exact one, so the test
 * leaned half a block toward +x and +z: and a sleeper lies at the head end of the bed, 0.1 from its edge, so with the
 * head toward +x or +z a monster one block beyond the wall behind the head passed (1.1), while one beside the foot
 * on the other side didn't. Measuring from the block's centre instead (the player's position shifted by half a block
 * here, in this comparison only) makes the test the same in every direction: a monster must reach a block next to
 * the sleeper's end of the bed or beside the bed. Paths treat closed doors as walls, as before.
 */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
	// Ordinal 1 of each: the arrival test (0 picks the spawn area, 2 the wake-up spot, both unchanged).
	@WrapOperation(method = "spawnMonstersAndWakePlayers",
			at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;x:D", ordinal = 1))
	private static double oldfaithful$fromBlockCentreX(PlayerEntity player, Operation<Double> original) {
		return original.call(player) - 0.5;
	}

	@WrapOperation(method = "spawnMonstersAndWakePlayers",
			at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;z:D", ordinal = 1))
	private static double oldfaithful$fromBlockCentreZ(PlayerEntity player, Operation<Double> original) {
		return original.call(player) - 0.5;
	}
}
