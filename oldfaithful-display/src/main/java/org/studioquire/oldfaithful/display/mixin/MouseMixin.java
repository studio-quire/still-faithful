package org.studioquire.oldfaithful.display.mixin;

import java.awt.Component;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Releasing the mouse centres it in the canvas, which there no longer is: centre it in the window. */
@Mixin(Mouse.class)
abstract class MouseMixin {
	@WrapOperation(method = "unlockCursor", at = @At(value = "INVOKE", target = "Ljava/awt/Component;getWidth()I", remap = false))
	private int oldfaithful$width(Component parent, Operation<Integer> original) {
		return parent == null ? Display.getWidth() : original.call(parent);
	}

	@WrapOperation(method = "unlockCursor", at = @At(value = "INVOKE", target = "Ljava/awt/Component;getHeight()I", remap = false))
	private int oldfaithful$height(Component parent, Operation<Integer> original) {
		return parent == null ? Display.getHeight() : original.call(parent);
	}
}
