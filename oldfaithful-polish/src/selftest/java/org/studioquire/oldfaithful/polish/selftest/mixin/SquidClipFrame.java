package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.selftest.SquidCheck;

/** For SquidCheck's clip: while it's taken, each frame drawn at the partial tick it asks for, and read back once drawn. */
@Mixin(GameRenderer.class)
abstract class SquidClipFrame {
	@ModifyVariable(method = "onFrameUpdate", at = @At("HEAD"), argsOnly = true)
	private float oldfaithful$clipPartial(float delta) {
		SquidCheck.Clip clip = SquidCheck.clip;
		return clip == null ? delta : clip.partial();
	}

	@Inject(method = "onFrameUpdate", at = @At("TAIL"))
	private void oldfaithful$clipFrame(float delta, CallbackInfo ci) {
		SquidCheck.Clip clip = SquidCheck.clip;
		if (clip != null) clip.grab();
	}
}
