package org.studioquire.oldfaithful.speed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.speed.FramePacing;
import org.studioquire.oldfaithful.speed.NextPicks;

/**
 * FramePacing at the top of the loop: extra chunk building, then the "Pre render" error check and the swap, timed.
 * None on Power saver or after a frame where vanilla slept for lacking focus: those ask for less CPU, not more.
 */
@Mixin(Minecraft.class)
abstract class MinecraftPacingMixin {
	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;logGlError(Ljava/lang/String;)V", ordinal = 0))
	private void oldfaithful$buildBeforeLoopWait(Minecraft mc, String phase, Operation<Void> original) {
		if (mc.world != null && mc.worldRenderer != null && mc.camera != null && mc.options.fpsLimit != 2) {
			FramePacing.build(() -> !NextPicks.needLoading(mc.worldRenderer, mc.camera), () -> mc.worldRenderer.compileChunks(mc.camera, false));
		} else {
			FramePacing.build(null, null);
		}
		long t = System.nanoTime();
		original.call(mc, phase);
		FramePacing.waited(System.nanoTime() - t);
	}

	/** Vanilla sleeps here when the window isn't active: the next frame builds nothing extra (FramePacing.idle). */
	@Inject(method = "run", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V"))
	private void oldfaithful$idle(CallbackInfo ci) {
		FramePacing.idle();
	}

	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", remap = false))
	private void oldfaithful$timeSwap(Operation<Void> original) {
		long t = System.nanoTime();
		original.call();
		FramePacing.waited(System.nanoTime() - t);
	}
}
