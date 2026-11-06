package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.LedgeHunt;

/** The ledge hunt: every placing of the player from anywhere but its own script. Idle unless the hunt runs. */
@Mixin(Entity.class)
abstract class LedgeHuntEntityMixin {
	@Inject(method = "setPosition", at = @At("HEAD"))
	private void oldfaithful$placed(double x, double y, double z, CallbackInfo ci) {
		if (LedgeHunt.ENABLED) LedgeHunt.placed((Entity) (Object) this, x, y, z);
	}
}
