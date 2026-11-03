package org.studioquire.oldfaithful.display.mixin;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import net.minecraft.client.CrashReportPanel;
import net.minecraft.client.MinecraftApplet$AppletMinecraft;
import net.minecraft.util.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game shows a crash in the applet, which is now hidden; it gets a window of its own instead, with the game's
 * own crash panel. Closing it ends the game, as closing the game's window would have.
 */
@Mixin(MinecraftApplet$AppletMinecraft.class)
abstract class AppletMinecraftMixin {
	@Inject(method = "handleCrash", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$ownCrashWindow(CrashReport report, CallbackInfo ci) {
		ci.cancel();
		Frame frame = new Frame("Minecraft has crashed");
		frame.setLayout(new BorderLayout());
		frame.add(new CrashReportPanel(report), "Center");
		frame.setSize(854, 480);
		frame.setLocationRelativeTo(null);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				frame.dispose();
				System.exit(1);
			}
		});
		frame.setVisible(true);
	}
}
