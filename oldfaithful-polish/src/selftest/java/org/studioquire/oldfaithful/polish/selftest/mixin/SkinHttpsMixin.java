package org.studioquire.oldfaithful.polish.selftest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.polish.selftest.SkinCheck;

/** For SkinCheck: the test skins' answers, at the one place hardening meets the network. */
@Mixin(value = Https.class, remap = false)
abstract class SkinHttpsMixin {
	@Inject(method = "send", at = @At("HEAD"), cancellable = true)
	private static void oldfaithful$testSkins(String method, String url, String contentType, byte[] body, int maxBytes, CallbackInfoReturnable<Https.Response> ci) {
		Https.Response answer = SkinCheck.answer(url);
		if (answer != null) ci.setReturnValue(answer);
	}
}
