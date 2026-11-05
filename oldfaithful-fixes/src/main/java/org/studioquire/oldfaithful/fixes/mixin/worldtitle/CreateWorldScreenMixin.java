package org.studioquire.oldfaithful.fixes.mixin.worldtitle;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** #73: this screen's fields sit at fixed heights but its title went by the window's, so in a big window the title came down among them. 20 is where it sits in the default window. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin {
	@ModifyArg(method = "render", index = 3, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
	private int oldfaithful$titleAtTop(int y) {
		return 20;
	}
}
