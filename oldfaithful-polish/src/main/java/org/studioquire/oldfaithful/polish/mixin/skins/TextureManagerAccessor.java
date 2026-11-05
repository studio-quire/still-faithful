package org.studioquire.oldfaithful.polish.mixin.skins;

import java.util.Map;

import net.minecraft.client.texture.ImageDownload;
import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureManager.class)
public interface TextureManagerAccessor {
	@Accessor("downloadedImages")
	Map<String, ImageDownload> oldfaithful$downloads();
}
