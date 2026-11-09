package org.studioquire.oldfaithful.polish.mixin.chestlids;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resource.pack.TexturePacks;
import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.ChestLids;

/** terrain.png gets the joined lid's halves as it's first loaded, and again after a texture pack is chosen. */
@Mixin(TextureManager.class)
abstract class TextureManagerMixin {
	@Shadow
	private HashMap textures;
	@Shadow
	private TexturePacks texturePacks;

	@Shadow
	private BufferedImage readImage(InputStream stream) throws IOException {
		throw new AssertionError();
	}

	@Shadow
	public abstract void load(BufferedImage image, int id);

	@WrapOperation(method = "getTextureId(Ljava/lang/String;)I", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/TextureManager;load(Ljava/awt/image/BufferedImage;I)V"))
	private void oldfaithful$lids(TextureManager self, BufferedImage image, int id, Operation<Void> load, String path) {
		load.call(self, "/terrain.png".equals(path) ? ChestLids.withLids(image) : image, id);
	}

	@Inject(method = "reload", at = @At("TAIL"))
	private void oldfaithful$lidsAgain(CallbackInfo ci) {
		Object id = textures.get("/terrain.png");
		if (!(id instanceof Integer)) return;
		try {
			InputStream in = texturePacks.selected.getResource("/terrain.png");
			if (in != null) load(ChestLids.withLids(readImage(in)), (Integer) id);
		} catch (IOException e) {
			System.out.println("[stillfaithful-polish] WARN Couldn't read terrain.png again for the double chests' lids: " + e);
		}
	}
}
