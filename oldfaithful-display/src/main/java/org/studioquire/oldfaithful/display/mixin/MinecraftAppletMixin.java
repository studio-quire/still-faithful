package org.studioquire.oldfaithful.display.mixin;

import java.applet.Applet;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MinecraftApplet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.display.WindowTitle;

/**
 * Takes the game off the applet. Fabric starts b1.7.3 as an applet in an AWT frame, and the game draws into an AWT
 * canvas through LWJGL 2's Display.setParent. On our LWJGL the game gets its own SDL window instead, so: the canvas
 * isn't added (adding it is what started the game thread) and the game is told it has none, which is the path
 * b1.7.3 already has for running without one; the game thread starts when the applet starts; and Fabric's frame,
 * which would sit empty, is made invisible before it's shown and disposed of after; its title goes to the game's
 * window.
 */
@Mixin(MinecraftApplet.class)
abstract class MinecraftAppletMixin extends Applet {
	@Shadow private Minecraft minecraft;

	@Shadow
	public abstract void startThread();

	@Inject(method = "init", at = @At("HEAD"))
	private void oldfaithful$hideFrame(CallbackInfo ci) {
		Window frame = javax.swing.SwingUtilities.getWindowAncestor(this);
		if (!(frame instanceof Frame)) return;
		Frame f = (Frame) frame;
		WindowTitle.fromFrame(f.getTitle());
		// Fabric has packed the frame and will show it after init; it can only change style while not displayable.
		f.dispose();
		try {
			f.setUndecorated(true);
			f.setType(Window.Type.UTILITY);
			f.setFocusableWindowState(false);
			f.setAutoRequestFocus(false);
			f.setBounds(0, 0, 1, 1);
			f.setOpacity(0f);
		} catch (RuntimeException e) {
			// No translucency: the 1x1 undecorated, unfocusable frame is all that can show, and only for a moment.
		}
		// Runs after Fabric's launch (which ends by showing the frame), on the same event thread.
		EventQueue.invokeLater(f::dispose);
	}

	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftApplet;add(Ljava/awt/Component;Ljava/lang/Object;)V"))
	private void oldfaithful$noCanvas(MinecraftApplet applet, Component canvas, Object where, Operation<Void> original) {
		minecraft.canvas = null;
	}

	@Inject(method = "start", at = @At("TAIL"))
	private void oldfaithful$startGame(CallbackInfo ci) {
		startThread();
	}
}
