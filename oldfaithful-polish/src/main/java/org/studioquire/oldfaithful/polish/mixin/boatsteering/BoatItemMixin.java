package org.studioquire.oldfaithful.polish.mixin.boatsteering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.BoatSteering;

/**
 * Boat Steering: a placed boat faces the way its placer looks, as in later versions, so W takes it there (beta's
 * started every boat facing west, which mattered to nobody while boats went where you looked). Turned after it's
 * spawned: a server starts tracking a boat from its yaw then, and a client spawns it facing west whatever that was, so
 * the turn goes out to clients as a change.
 */
@Mixin(BoatItem.class)
abstract class BoatItemMixin {
	@WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
	private boolean oldfaithful$facePlacer(World world, Entity boat, Operation<Boolean> original, @Local(argsOnly = true) PlayerEntity user) {
		boolean spawned = original.call(world, boat);
		boat.yaw = boat.prevYaw = BoatSteering.placedYaw(user.yaw);
		return spawned;
	}
}
