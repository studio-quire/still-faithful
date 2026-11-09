package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.Bench;
import org.studioquire.oldfaithful.selftest.StartupCheck;

/**
 * The start-up test's window never has focus, and the game pauses itself half a second after losing it; in that
 * test and in the benchmark only, it doesn't, so the world actually runs.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererStartupMixin {
	/** A pass of the game's loop, counted for the start-up test's waits on the window's events (StartupCheck.pause). */
	@Inject(method = "onFrameUpdate", at = @At("HEAD"))
	private void oldfaithful$countFrame(float tickDelta, CallbackInfo ci) {
		StartupCheck.frames++;
	}

	@WrapOperation(method = "onFrameUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseGame()V"))
	private void oldfaithful$noPauseInStartupTest(Minecraft minecraft, Operation<Void> original) {
		if (!StartupCheck.ENABLED && !Bench.ENABLED) original.call(minecraft);
	}
}
