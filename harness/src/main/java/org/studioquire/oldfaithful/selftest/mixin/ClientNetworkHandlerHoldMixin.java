package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.network.ClientNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.ClosedScreenCheck;

/**
 * :harness:fixesMultiplayerTest only: while {@link ClosedScreenCheck#holdIncoming} is set, the client reads nothing the
 * server sent (it waits in the connection's queue), as on a slow connection; what it sends goes out as usual.
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerHoldMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$hold(CallbackInfo ci) {
		if (ClosedScreenCheck.holdIncoming) ci.cancel();
	}
}
