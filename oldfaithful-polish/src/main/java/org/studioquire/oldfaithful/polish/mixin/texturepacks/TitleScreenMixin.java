package org.studioquire.oldfaithful.polish.mixin.texturepacks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.resource.language.TranslationStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The button has only ever opened texture packs, and later betas renamed it to say so. */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/language/TranslationStorage;get(Ljava/lang/String;)Ljava/lang/String;"))
	private String stillfaithful$texturePacks(TranslationStorage strings, String key, Operation<String> get) {
		return key.equals("menu.mods") ? "Texture Packs" : get.call(strings, key);
	}
}
