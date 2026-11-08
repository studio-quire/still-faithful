package org.studioquire.oldfaithful.polish.mixin.chat;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Chat's dark bars as long as each line's own text. Beta draws every line's bar 320 wide (the width lines wrap at),
 * from x 2, where the text starts, and 1 above the text to the bottom of its shadow. Here a line's bar runs from 1
 * before the text to the end of its text (whose width counts the column its last letter's shadow falls in) and 1 further,
 * a gap each side like the 1 beta leaves above (the user's call, 2026-11-08), so each line has its own length; a line with no text has none. Height, colour, fade and
 * wrapping are beta's, and the open chat screen's history is drawn here too (its typing bar is its own, and stays).
 * The line is told by the bar's place: beta puts line i's top at -9i - 1 in the chat's own coordinates; a bar anywhere
 * else (another mod moving the chat) is left as beta draws it.
 */
@Mixin(InGameHud.class)
abstract class InGameHudMixin {
	@Shadow private List messages;
	@Shadow private Minecraft minecraft;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;fill(IIIII)V"))
	private void oldfaithful$chatBarToText(InGameHud hud, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
		int line = -(y1 + 1) / 9;
		if (x2 - x1 != 320 || y1 != -9 * line - 1 || line < 0 || line >= messages.size()) {
			original.call(hud, x1, y1, x2, y2, color);
			return;
		}
		int width = minecraft.textRenderer.getWidth(((ChatHudLine) messages.get(line)).text);
		if (width > 0) original.call(hud, x1 - 1, y1, x1 + width + 1, y2, color);
	}
}
