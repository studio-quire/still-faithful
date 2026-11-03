package org.studioquire.oldfaithful.speed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.speed.FramePacing;

/** FramePacing in renderFrame: vanilla's own chunk building timed, and the wait in making the culler. */
@Mixin(GameRenderer.class)
abstract class GameRendererPacingMixin {
	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z"))
	private boolean oldfaithful$timeCompile(WorldRenderer renderer, LivingEntity camera, boolean force, Operation<Boolean> original) {
		long t = System.nanoTime();
		boolean done = original.call(renderer, camera, force);
		FramePacing.called(System.nanoTime() - t);
		return done;
	}

	/** Making the culler reads the matrices: with a threaded driver, where the frame waits for the display. */
	@WrapOperation(method = "renderFrame", at = @At(value = "NEW", target = "()Lnet/minecraft/client/render/FrustumCuller;"))
	private FrustumCuller oldfaithful$timeCuller(Operation<FrustumCuller> original) {
		long t = System.nanoTime();
		FrustumCuller culler = original.call();
		FramePacing.waited(System.nanoTime() - t);
		FramePacing.culled();
		return culler;
	}
}
