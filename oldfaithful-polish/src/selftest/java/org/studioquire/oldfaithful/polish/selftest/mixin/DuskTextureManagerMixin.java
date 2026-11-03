package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.selftest.DuskCheck;

/** For DuskCheck: water, lava and fire hold their current frame while pictures are compared. */
@Mixin(TextureManager.class)
abstract class DuskTextureManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$still(CallbackInfo ci) {
		if (DuskCheck.frozen) ci.cancel();
	}
}
