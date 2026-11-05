package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lava and water animate by chance, so a scene compared pixel for pixel (`coreInertTest`) holds them still. */
@Mixin(TextureManager.class)
abstract class TextureManagerStillMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$still(CallbackInfo ci) {
		if (Boolean.getBoolean("oldfaithful.selftest.sceneStill")) ci.cancel();
	}
}
