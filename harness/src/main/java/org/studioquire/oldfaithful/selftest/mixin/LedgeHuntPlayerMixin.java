package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.selftest.LedgeHunt;

/** The ledge hunt's eyes on the real player: each movement tick, and each push-out call. Idle unless the hunt runs. */
@Mixin(ClientPlayerEntity.class)
abstract class LedgeHuntPlayerMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void oldfaithful$ledgeHead(CallbackInfo ci) {
		if (LedgeHunt.ENABLED) LedgeHunt.tickHead((ClientPlayerEntity) (Object) this);
	}

	@Inject(method = "tickMovement", at = @At("RETURN"))
	private void oldfaithful$ledgeEnd(CallbackInfo ci) {
		if (LedgeHunt.ENABLED) LedgeHunt.tickEnd((ClientPlayerEntity) (Object) this);
	}

	@Inject(method = "pushOutOfBlock", at = @At("HEAD"))
	private void oldfaithful$pushHead(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (LedgeHunt.ENABLED) LedgeHunt.pushHead((ClientPlayerEntity) (Object) this, x, y, z);
	}

	@Inject(method = "pushOutOfBlock", at = @At("RETURN"))
	private void oldfaithful$pushEnd(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (LedgeHunt.ENABLED) LedgeHunt.pushEnd((ClientPlayerEntity) (Object) this);
	}
}
