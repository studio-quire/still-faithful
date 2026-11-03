package org.studioquire.oldfaithful.polish.mixin.autosave;

import net.minecraft.world.chunk.storage.RegionIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.BackgroundSaves;

/**
 * #14 ({@link BackgroundSaves}): region files are closed only once every queued save is written (the game closes
 * them all on leaving a world and when 256 are open). The writer never takes RegionIo's lock, so waiting for it here,
 * inside that lock, can't deadlock.
 */
@Mixin(RegionIo.class)
abstract class RegionIoMixin {
	@Inject(method = "flush", at = @At("HEAD"))
	private static void oldfaithful$writtenBeforeClosing(CallbackInfo ci) {
		BackgroundSaves.drain();
	}
}
