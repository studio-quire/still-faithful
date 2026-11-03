package org.studioquire.oldfaithful.speed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * renderFrame computes the frustum (two glGetFloatv, each a sync with the driver) and drops the result; a few lines on,
 * the FrustumCuller it makes computes it again, from the matrices as they are then, and that's the one culling uses.
 * Nothing reads the frustum in between (sky and fog setup), so the first computation is skipped: the culler's frustum
 * is the same, one sync fewer per frame.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererFrustumMixin {
	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Frustum;getInstance()Lnet/minecraft/client/render/FrustumData;"))
	private FrustumData oldfaithful$unusedFrustum(Operation<FrustumData> original) {
		return FrustumAccessor.oldfaithful$instance();
	}
}
