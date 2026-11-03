package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.selftest.HandsOff;

/**
 * b1.7.3's only calls that grab, release or move the real cursor (lockCursor, unlockCursor) do nothing in test and
 * benchmark launches, so neither LWJGL ever clips, centres or warps the session's cursor (HandsOff).
 */
@Mixin(Mouse.class)
abstract class MouseHandsOffMixin {
	@WrapOperation(method = {"lockCursor", "unlockCursor"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;setGrabbed(Z)V", remap = false))
	private void oldfaithful$noGrab(boolean grab, Operation<Void> original) {
		if (!HandsOff.CURSOR) original.call(grab);
		else HandsOff.blocked("Mouse.setGrabbed(" + grab + ")");
	}

	@WrapOperation(method = "unlockCursor", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;setCursorPosition(II)V", remap = false))
	private void oldfaithful$noWarp(int x, int y, Operation<Void> original) {
		if (!HandsOff.CURSOR) original.call(x, y);
		else HandsOff.blocked("Mouse.setCursorPosition");
	}
}
