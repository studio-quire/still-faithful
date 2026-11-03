package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.DuskLight;
import org.studioquire.oldfaithful.polish.selftest.DuskCheck;

/**
 * For DuskCheck: frames read back once drawn; the fog's brightness, which beta eases towards the camera's light a
 * tenth a tick, set at once to where it's heading (the same in both lightings, so pictures needn't wait for it); and
 * the block-breaking cracks (and the outline) on the glowstone in the middle of the view.
 */
@Mixin(GameRenderer.class)
abstract class DuskGameRendererMixin {
	@Shadow
	private Minecraft client;
	@Shadow
	private float lastViewBob;
	@Shadow
	private float viewBob;

	@Inject(method = "onFrameUpdate", at = @At("RETURN"))
	private void oldfaithful$grab(float tickDelta, CallbackInfo ci) {
		DuskCheck.frame();
	}

	@Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;updateTargetedEntity(F)V", shift = At.Shift.AFTER))
	private void oldfaithful$cracks(float tickDelta, long time, CallbackInfo ci) {
		int[] at = DuskCheck.crackAt;
		if (!DuskCheck.cracks || at == null) return;
		client.worldRenderer.miningProgress = 0.55F;
		client.crosshairTarget = new HitResult(at[0], at[1], at[2], 2, Vec3d.create(at[0] + 0.5, at[1] + 0.5, at[2]));
	}

	@Inject(method = "updateCamera", at = @At("RETURN"))
	private void oldfaithful$fogNow(CallbackInfo ci) {
		if (!DuskCheck.frozen || client.world == null || client.camera == null) return;
		// At the drawn darkness, as the game's own reading is (the dusk mod's GameRendererMixin).
		float light;
		DuskLight.entityScope++;
		try {
			light = client.world.method_1782(MathHelper.floor(client.camera.x), MathHelper.floor(client.camera.y), MathHelper.floor(client.camera.z));
		} finally {
			DuskLight.entityScope--;
		}
		float far = (3 - client.options.viewDistance) / 3.0F;
		lastViewBob = viewBob = light * (1.0F - far) + far;
	}
}
