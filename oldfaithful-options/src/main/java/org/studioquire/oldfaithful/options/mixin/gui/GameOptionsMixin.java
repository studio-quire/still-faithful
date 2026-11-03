package org.studioquire.oldfaithful.options.mixin.gui;

import java.io.File;

import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A first start has GUI Scale at Normal (2) rather than beta's Auto (0): set just before the game's options load, so
 * it holds only when there's no options.txt yet; one that exists keeps whatever it says (or Auto, if it says nothing).
 */
@Mixin(GameOptions.class)
abstract class GameOptionsMixin {
	@Shadow
	private File file;
	@Shadow
	public int guiScale;

	@Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;load()V"))
	private void oldfaithful$normalOnFirstStart(CallbackInfo ci) {
		if (!this.file.exists()) this.guiScale = 2;
	}
}
