package org.studioquire.oldfaithful.fixes.mixin.deathscreen;

import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * #34, the death screen: the score's label carried "&e" where the font's colour code is "§e", so it showed
 * "Score: &e0". Now "Score: " and the score in yellow, as meant.
 */
@Mixin(DeathScreen.class)
abstract class DeathScreenMixin {
	@ModifyConstant(method = "render", constant = @Constant(stringValue = "Score: &e"))
	private String oldfaithful$colourCode(String label) {
		return "Score: §e";
	}
}
