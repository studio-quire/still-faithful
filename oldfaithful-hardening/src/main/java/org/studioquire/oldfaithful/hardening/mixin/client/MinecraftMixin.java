package org.studioquire.oldfaithful.hardening.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.hardening.Config;

/**
 * Startup: the game sets up LWJGL's game controllers and never reads them. That loads jinput, whose 2011 native
 * libraries enumerate every input device (and can hang startup doing it), so the call is skipped unless the config
 * asks for it, and our instance ships no jinput (docs/runtime-inventory.md).
 * <p>
 * After five minutes of play the game sends the username and session id (with a modern launcher, the access token)
 * to https://login.minecraft.net/session, a host that no longer exists, and would show "invalid session" if it
 * answered 400. The launcher has already checked the session, so the token now stays on this machine.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Controllers;create()V", remap = false))
	private void oldfaithful$skipControllers(Operation<Void> original) {
		if (Config.INSTANCE.controllers) original.call();
	}

	@Inject(method = "startSessionCheck", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$skipSessionCheck(CallbackInfo ci) {
		ci.cancel();
	}
}
