package org.studioquire.oldfaithful.hardening.mixin.client;

import java.io.File;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.ResourceDownloadThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.studioquire.oldfaithful.hardening.Config;
import org.studioquire.oldfaithful.hardening.Services;
import org.studioquire.oldfaithful.hardening.assets.LegacyAssets;
import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * Replaces the game's resource downloader, which took a file list over plain HTTP, parsed it with XML external
 * entities on, and wrote files wherever the list's names pointed (see {@link LegacyAssets}). The original download
 * code never runs; missing sounds come from Mojang over HTTPS, hash-checked, and then the game loads sounds from
 * disk exactly as it does when offline.
 * <p>
 * Loading from disk walks everything in resources/ and printed "Failed to add" for each file at its top level
 * (launchers put pack.mcmeta and a read-me there): the game only takes sounds and music from subfolders, and a name
 * without a folder made its path parsing throw. Those files are now skipped without a word; nothing else changes.
 */
@Mixin(ResourceDownloadThread.class)
abstract class ResourceDownloadThreadMixin {
	@Shadow public File resourcesDirectory;
	@Shadow private boolean cancelled;
	@Shadow private Minecraft minecraft;

	@Shadow
	public abstract void reload();

	@Inject(method = "run", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$run(CallbackInfo ci) {
		ci.cancel();
		// What's already here plays at once; what's missing is added file by file as it arrives, as vanilla's
		// downloader did, so a slow or unreachable Mojang never holds back the sounds the game already has.
		if (!cancelled) reload();
		if (Config.INSTANCE.resourcesDownload && !cancelled) {
			try {
				new LegacyAssets(Https.REAL, Services.cacheDir()).sync(resourcesDirectory, () -> cancelled, (path, file) -> {
					if (cancelled) return;
					try {
						minecraft.loadResource(path, file);
					} catch (Exception e) {
						Log.warn("Sound " + path + " downloaded but not loaded: " + e);
					}
				});
			} catch (Exception e) {
				Log.warn("Sounds not checked or downloaded: " + e);
			}
		}
	}

	@WrapOperation(method = "loadFromDirectory", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;loadResource(Ljava/lang/String;Ljava/io/File;)V"))
	private void oldfaithful$skipTopLevelFiles(Minecraft minecraft, String path, File file, Operation<Void> original) {
		if (path.indexOf('/') >= 0) original.call(minecraft, path, file);
	}
}
