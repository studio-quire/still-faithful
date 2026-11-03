package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.selftest.MultiplayerCheck;

/**
 * MultiplayerCheck (#36) only: while it plays the mouse button itself, the game's own per-tick handling of the (never
 * pressed) button would cancel every break in between, so it's left out then.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMouseDriveMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleMouseDown(IZ)V"))
	private void oldfaithful$testDrivesMouse(Minecraft mc, int button, boolean holding, Operation<Void> original) {
		if (!MultiplayerCheck.drivingMouse) original.call(mc, button, holding);
	}
}
