package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.Bench;
import org.studioquire.oldfaithful.selftest.StartupCheck;

/**
 * The benchmark's timing points in the game's run loop (docs/speed.md): the start of each pass, the ticks, the
 * lighting updates, the frame's rendering and the buffer swap. Each is a pass-through unless the benchmark launched
 * the game. Also, in the benchmark, the loop doesn't sleep 10 ms a frame for lacking focus (its window never
 * has focus, unseen), which would otherwise cap it near 100 frames per second; nor in a start-up test while render
 * chunks wait to be built ({@link StartupCheck#chunksWaiting}) or a culling scene reads frame after frame.
 */
@Mixin(Minecraft.class)
abstract class MinecraftBenchMixin {
	@Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;resetCacheCount()V"))
	private void oldfaithful$frameStart(CallbackInfo ci) {
		if (Bench.ENABLED) Bench.frameStart((Minecraft) (Object) this);
	}

	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"))
	private void oldfaithful$timeTick(Minecraft mc, Operation<Void> original) {
		if (!Bench.ENABLED) {
			original.call(mc);
			return;
		}
		long t = System.nanoTime();
		try {
			original.call(mc);
		} finally {
			Bench.tickNs += System.nanoTime() - t;
		}
	}

	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;doLightingUpdates()Z"))
	private boolean oldfaithful$timeLight(World world, Operation<Boolean> original) {
		if (!Bench.ENABLED) return original.call(world);
		long t = System.nanoTime();
		try {
			return original.call(world);
		} finally {
			Bench.lightNs += System.nanoTime() - t;
		}
	}

	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;onFrameUpdate(F)V"))
	private void oldfaithful$timeRender(GameRenderer renderer, float tickDelta, Operation<Void> original) {
		if (!Bench.ENABLED) {
			original.call(renderer, tickDelta);
			return;
		}
		long t = System.nanoTime();
		try {
			original.call(renderer, tickDelta);
		} finally {
			Bench.renderNs += System.nanoTime() - t;
		}
	}

	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", remap = false))
	private void oldfaithful$timeSwap(Operation<Void> original) {
		if (!Bench.ENABLED) {
			original.call();
			return;
		}
		Bench.beforeSwap();
		Bench.frameInstant();
		long t = System.nanoTime();
		try {
			original.call();
		} finally {
			long end = System.nanoTime();
			Bench.swapNs += end - t;
			Bench.pumpAt = end;
		}
	}

	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;isActive()Z", remap = false))
	private boolean oldfaithful$activeInBench(Operation<Boolean> original) {
		return Bench.ENABLED || StartupCheck.chunksWaiting((Minecraft) (Object) this) || org.studioquire.oldfaithful.selftest.Culling.scene != null || original.call();
	}
}
