package org.studioquire.oldfaithful.polish.selftest.mixin;

import java.util.Map;

import net.minecraft.client.texture.ImageDownload;
import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For SkinCheck, the downloads with the switch off too (the skins package's own accessor isn't in then). */
@Mixin(TextureManager.class)
public interface SkinDownloads {
	@Accessor("downloadedImages")
	Map<String, ImageDownload> oldfaithful$skinCheckDownloads();
}
