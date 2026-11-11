package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.selftest.Culling;

/** For Culling: the probe isn't ticked, so no AI, gravity or animation moves it between the poses a scene sets. */
@Mixin(World.class)
abstract class WorldProbeTickMixin {
	@WrapOperation(method = "updateEntity(Lnet/minecraft/entity/Entity;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
	private void oldfaithful$probeStill(Entity entity, Operation<Void> original) {
		if (entity != Culling.frozen) original.call(entity);
	}
}
