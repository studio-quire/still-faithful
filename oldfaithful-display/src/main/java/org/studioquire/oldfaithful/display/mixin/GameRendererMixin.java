package org.studioquire.oldfaithful.display.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.display.DisplayConfig;
import org.studioquire.oldfaithful.lwjgl.MouseState;

/**
 * mouse.subpixel (off by default): mouse look turns by the frame's motion before it was rounded to whole pixels.
 * b1.7.3 turns by integer deltas per frame, each axis rounded on its own, so a slow diagonal turn steps one axis, then
 * the other; the unrounded motion has the same speed and acceleration without the steps. Cinematic mode keeps
 * vanilla's path, its smoothing being built around it.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Shadow
	private Minecraft client;

	@WrapOperation(method = "onFrameUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ClientPlayerEntity;changeLookDirection(FF)V"))
	private void oldfaithful$subpixelLook(ClientPlayerEntity player, float dx, float dy, Operation<Void> original) {
		if (!DisplayConfig.subpixelLook || client.options.cinematicMode) {
			original.call(player, dx, dy);
			return;
		}
		// As vanilla scales the integer deltas just before this call.
		float s = client.options.mouseSensitivity * 0.6F + 0.2F;
		s = s * s * s * 8.0F;
		original.call(player, MouseState.takenFX() * s, MouseState.takenFY() * s * (client.options.invertYMouse ? -1 : 1));
	}
}
