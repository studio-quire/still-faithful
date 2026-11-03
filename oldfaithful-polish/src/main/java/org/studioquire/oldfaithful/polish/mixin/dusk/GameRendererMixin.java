package org.studioquire.oldfaithful.polish.mixin.dusk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.DuskLight;
import org.studioquire.oldfaithful.polish.DuskShader;

/**
 * #6: each frame's darkness, from the time of day as it's drawn (DuskLight.frame); the fog distance mode beta asks for,
 * which the chunks' vertex program has to compute itself; and the hand, the falling rain and snow and the fog's
 * brightness lit at the same darkness as the terrain.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Shadow
	private Minecraft client;

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void oldfaithful$darkness(float tickDelta, long time, CallbackInfo ci) {
		DuskLight.frame(client.world, tickDelta);
	}

	@WrapOperation(method = "applyFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogi(II)V"))
	private void oldfaithful$fogMode(int name, int value, Operation<Void> original) {
		// GL_FOG_DISTANCE_MODE_NV, set to GL_EYE_RADIAL_NV or a plane distance.
		if (name == 0x855A) DuskShader.radialFog = value == 0x855B;
		original.call(name, value);
	}

	/**
	 * The fog's brightness, which beta eases each tick towards the light at the camera (below Far view distance it
	 * darkens the fog and the sky's horizon): read at the same darkness as the terrain, so in smooth dusk the fog
	 * glides with it instead of stepping and easing after each of beta's steps. Stepped dusk reads beta's own.
	 */
	@WrapOperation(method = "updateCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;method_1782(III)F"))
	private float oldfaithful$fogLight(World world, int x, int y, int z, Operation<Float> original) {
		DuskLight.entityScope++;
		try {
			return original.call(world, x, y, z);
		} finally {
			DuskLight.entityScope--;
		}
	}

	@WrapMethod(method = "renderFirstPersonHand")
	private void oldfaithful$hand(float tickDelta, int eye, Operation<Void> original) {
		DuskLight.entityScope++;
		try {
			original.call(tickDelta, eye);
		} finally {
			DuskLight.entityScope--;
		}
	}

	@WrapMethod(method = "renderSnow")
	private void oldfaithful$weather(float tickDelta, Operation<Void> original) {
		DuskLight.entityScope++;
		try {
			original.call(tickDelta);
		} finally {
			DuskLight.entityScope--;
		}
	}
}
