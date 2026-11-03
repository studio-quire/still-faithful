package org.studioquire.oldfaithful.options.mixin.fov;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.options.Fov;

/**
 * The FOV setting ({@link Fov}). Beta works out its view in getFov (70, 60 under water, narrowing in death) and both
 * of renderWorld's projections take it from there; the setting scales that. Frustum culling reads the projection
 * back from GL, so it follows.
 * <p>
 * The hand and held item are drawn later in the frame through the world's projection, so a wide setting would
 * stretch them. Before they're drawn the projection is set again as renderWorld sets it, at beta's own view: they look
 * as they always did whatever the setting (later versions do the same). Beta draws them only when the view isn't
 * zoomed (the big-screenshot zoom), and nothing after them in the frame uses the projection.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Shadow
	private Minecraft client;
	@Shadow
	private float viewDistance;

	@Shadow
	private float getFov(float tickDelta) {
		throw new AssertionError();
	}

	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float oldfaithful$fovSetting(float betaFov) {
		return Fov.apply(betaFov);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderFirstPersonHand(FI)V"))
	private void oldfaithful$handAtBetasView(GameRenderer renderer, float tickDelta, int eye, Operation<Void> original) {
		int degrees = Fov.degrees;
		if (degrees != Fov.BETA) {
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			if (this.client.options.anaglyph3d) GL11.glTranslatef(-(eye * 2 - 1) * 0.07F, 0.0F, 0.0F);
			GLU.gluPerspective(getFov(tickDelta) * Fov.BETA / degrees, (float) this.client.displayWidth / this.client.displayHeight,
					0.05F, this.viewDistance * 2.0F);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
		}
		original.call(renderer, tickDelta, eye);
	}
}
