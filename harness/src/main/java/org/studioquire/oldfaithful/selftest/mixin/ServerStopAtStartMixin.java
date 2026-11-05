package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server that saves and stops before its first tick: what generation made, with none of the running world's chance (`coreInertTest`). */
@Mixin(MinecraftServer.class)
abstract class ServerStopAtStartMixin {
	@Shadow
	private boolean running;

	@Inject(method = "loadWorld", at = @At("TAIL"))
	private void oldfaithful$stop(CallbackInfo ci) {
		if (Boolean.getBoolean("oldfaithful.selftest.stopAtStart")) running = false;
	}
}
