package org.studioquire.oldfaithful.polish.mixin.skins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.ImageDownload;
import net.minecraft.client.texture.ImageProcessor;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.ModernSkin;

/** Skins are kept whole ({@link ModernSkin}); capes come through the same call and stay beta's. */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@WrapOperation(method = "notifyEntityAdded", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/texture/TextureManager;downloadImage(Ljava/lang/String;Lnet/minecraft/client/texture/ImageProcessor;)Lnet/minecraft/client/texture/ImageDownload;"))
	private ImageDownload oldfaithful$wholeSkin(TextureManager textures, String url, ImageProcessor processor, Operation<ImageDownload> original, Entity entity) {
		return original.call(textures, url, url.equals(entity.skinUrl) ? ModernSkin.processor(url) : processor);
	}
}
