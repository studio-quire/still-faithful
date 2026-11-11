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
import org.studioquire.oldfaithful.hardening.assets.BetaSounds;
import org.studioquire.oldfaithful.hardening.assets.LegacyAssets;
import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * Replaces the game's resource downloader, which took a file list over plain HTTP, parsed it with XML external
 * entities on, and wrote files wherever the list's names pointed (see {@link LegacyAssets}). The original download
 * code never runs; missing sounds come from Mojang over HTTPS, hash-checked, and then the game loads sounds from
 * disk exactly as it does when offline.
 * <p>
 * Beta's own bow, door and explosion sounds ({@link BetaSounds}) take the place of the later recordings launchers
 * give: those already on disk before the game's sounds load, the rest as they arrive.
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
		BetaSounds beta = Services.BETA_SOUNDS;
		// Before the first load, so the later recordings of those already here never join a pool.
		beta.findLocal(resourcesDirectory);
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
			try {
				beta.fetchMissing(resourcesDirectory, () -> cancelled, (pool, file) -> {
					if (cancelled || minecraft == null) return;
					try {
						BetaSounds.install(minecraft.soundManager, pool, file);
					} catch (Exception e) {
						Log.warn("Beta's " + pool + " fetched but not loaded: " + e);
					}
				});
			} catch (Exception e) {
				Log.warn("Beta's sounds not fetched: " + e);
			}
		} else if (!Config.INSTANCE.resourcesDownload && !beta.complete()) {
			Log.info("Downloads are off (resources.download): " + (BetaSounds.PINNED.size() - beta.ready().size())
					+ " of beta's own sounds aren't here, and their later recordings play");
		}
		beta.settle();
	}

	/** Every load from disk, the first and any the game forces later (which starts from an empty sound manager). */
	@Inject(method = "reload", at = @At("TAIL"))
	private void oldfaithful$betaSounds(CallbackInfo ci) {
		if (minecraft == null) return; // the client audit runs the thread without a game
		try {
			Services.BETA_SOUNDS.installAll(minecraft.soundManager);
		} catch (Exception e) {
			Log.warn("Beta's sounds not loaded: " + e);
		}
	}

	@WrapOperation(method = "loadFromDirectory", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;loadResource(Ljava/lang/String;Ljava/io/File;)V"))
	private void oldfaithful$skipTopLevelFiles(Minecraft minecraft, String path, File file, Operation<Void> original) {
		if (path.indexOf('/') >= 0) original.call(minecraft, path, file);
	}
}
