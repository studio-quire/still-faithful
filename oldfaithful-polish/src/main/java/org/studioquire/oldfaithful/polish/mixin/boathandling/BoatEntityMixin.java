package org.studioquire.oldfaithful.polish.mixin.boathandling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Boats;

/**
 * #12, handling ({@link Boats}): where the world runs (the server in multiplayer), a boat in water that nobody is
 * paddling slows to a stop in a few blocks, and its rise and fall at the surface are damped. How much of the boat is
 * in water is read from beta's own five slice tests at the start of its tick.
 */
@Mixin(BoatEntity.class)
abstract class BoatEntityMixin extends Entity {
	BoatEntityMixin(World world) {
		super(world);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isFluidInBox(Lnet/minecraft/util/math/Box;Lnet/minecraft/block/material/Material;)Z"))
	private boolean oldfaithful$countWetSlices(World world, Box box, Material fluid, Operation<Boolean> original,
			@Share("wet") LocalIntRef wet) {
		boolean in = original.call(world, box, fluid);
		if (in) wet.set(wet.get() + 1);
		return in;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void oldfaithful$handling(CallbackInfo ci, @Share("wet") LocalIntRef wet) {
		if (this.world.isRemote || this.dead || wet.get() == 0) return;
		Entity rider = this.passenger;
		boolean paddling = rider != null
				&& rider.velocityX * rider.velocityX + rider.velocityZ * rider.velocityZ > Boats.PADDLING * Boats.PADDLING;
		if (!paddling) {
			this.velocityX *= Boats.COAST;
			this.velocityZ *= Boats.COAST;
		}
		// Partly in the water: at the surface, where beta's boat bobs.
		if (wet.get() < 5) this.velocityY *= Boats.BOB;
	}
}
