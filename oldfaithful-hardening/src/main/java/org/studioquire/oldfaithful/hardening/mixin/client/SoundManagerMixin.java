package org.studioquire.oldfaithful.hardening.mixin.client;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundEntry;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.studioquire.oldfaithful.hardening.Services;
import org.studioquire.oldfaithful.hardening.assets.BetaSounds;

/**
 * Beta's own bow, door and explosion sounds ({@link BetaSounds}): once one is here, the later recording a launcher
 * gives for it never joins its pool, and the pool is made to hold beta's alone.
 */
@Mixin(SoundManager.class)
abstract class SoundManagerMixin implements BetaSounds.Pools {
	@Shadow
	private SoundEntry sounds;

	@Inject(method = "loadSound", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$keepOutLaterRecordings(String id, File file, CallbackInfo ci) {
		if (Services.BETA_SOUNDS.keepsOut(id)) ci.cancel();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void oldfaithful$useBeta(String pool, String id, File file) throws IOException {
		Map<String, List<Sound>> pools = ((SoundEntryAccessor) sounds).oldfaithful$pools();
		BetaSounds.replace(pools, pool, new Sound(id, file.toURI().toURL()));
	}
}
