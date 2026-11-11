package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.studioquire.oldfaithful.selftest.Culling;

/**
 * For Culling: every frame drawn at the same point between ticks while a scene runs ({@link Culling#frameDelta}). Beta's
 * models sway with age plus the frame's share of a tick (a biped's arms, a ghast's tentacles, a dropped item's bob), and
 * at the furthest point a probe still shows, its last pixels came and went from frame to frame.
 */
@Mixin(Minecraft.class)
abstract class MinecraftCullingMixin {
	@ModifyArg(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;onFrameUpdate(F)V"))
	private float oldfaithful$cullingStill(float tickDelta) {
		return Culling.scene != null ? Culling.frameDelta : tickDelta;
	}
}
