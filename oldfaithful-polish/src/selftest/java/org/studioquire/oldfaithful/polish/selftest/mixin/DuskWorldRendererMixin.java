package org.studioquire.oldfaithful.polish.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.selftest.DuskCheck;

/** For DuskCheck: clouds (which drift with the ticks) off while pictures are compared; chunks a darkness step marks counted. */
@Mixin(WorldRenderer.class)
abstract class DuskWorldRendererMixin {
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$noClouds(float tickDelta, CallbackInfo ci) {
		if (DuskCheck.frozen) ci.cancel();
	}

	@WrapOperation(method = "notifyAmbientDarknessChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;invalidate()V"))
	private void oldfaithful$countMarks(ChunkBuilder chunk, Operation<Void> original) {
		DuskCheck.notifyMarks++;
		original.call(chunk);
	}
}
