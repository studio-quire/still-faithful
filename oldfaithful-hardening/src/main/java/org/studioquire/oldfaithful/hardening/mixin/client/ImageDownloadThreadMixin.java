package org.studioquire.oldfaithful.hardening.mixin.client;

import java.awt.image.BufferedImage;

import net.minecraft.client.texture.ImageDownload;
import net.minecraft.client.texture.ImageProcessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.studioquire.oldfaithful.hardening.Config;
import org.studioquire.oldfaithful.hardening.Services;
import org.studioquire.oldfaithful.hardening.skins.Skins;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * Takes over the skin and cape download thread for the game's own skin and cape URLs, which point at dead plain-HTTP
 * S3 buckets: those now come from Mojang over HTTPS through {@link Skins}, size-checked before decoding. With skins off
 * in the config, nothing is requested for them at all. Any other URL (a skin mod's own server, say) is left to the
 * thread as it is, so mods that change where skins come from keep working.
 */
@Mixin(targets = "net.minecraft.client.texture.ImageDownload$Thread")
abstract class ImageDownloadThreadMixin {
	@Shadow @Final String url;
	@Shadow @Final ImageProcessor processor;
	@Shadow @Final ImageDownload data;

	@Inject(method = "run", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$run(CallbackInfo ci) {
		if (url == null || !(url.startsWith(Skins.LEGACY_SKINS) || url.startsWith(Skins.LEGACY_CAPES))) return;
		ci.cancel();
		if (!Config.INSTANCE.skins) return;
		try {
			String source = Services.SKINS.resolve(url);
			if (source == null) return;
			BufferedImage image = Services.SKINS.download(source);
			if (image == null) return;
			data.image = processor == null ? image : processor.process(image);
		} catch (Exception e) {
			Log.warn("Skin or cape not loaded: " + e.getMessage());
		}
	}
}
