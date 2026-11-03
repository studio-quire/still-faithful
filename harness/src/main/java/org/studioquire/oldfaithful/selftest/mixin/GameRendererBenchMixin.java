package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.selftest.Bench;

/** Times the frame's chunk building, for the benchmark. */
@Mixin(GameRenderer.class)
abstract class GameRendererBenchMixin {
	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z"))
	private boolean oldfaithful$timeCompile(WorldRenderer renderer, LivingEntity camera, boolean force, Operation<Boolean> original) {
		if (!Bench.ENABLED) return original.call(renderer, camera, force);
		long t = System.nanoTime();
		try {
			return original.call(renderer, camera, force);
		} finally {
			Bench.compileNs += System.nanoTime() - t;
		}
	}
}
