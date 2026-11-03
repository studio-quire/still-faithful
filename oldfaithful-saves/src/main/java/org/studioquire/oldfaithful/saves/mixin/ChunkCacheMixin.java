package org.studioquire.oldfaithful.saves.mixin;

import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.storage.ChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.saves.ChunkLoad;
import org.studioquire.oldfaithful.saves.Committer;

/** A full save (leaving the world) is on disk when it returns. Single player. */
@Mixin(ChunkCache.class)
abstract class ChunkCacheMixin {
	@Shadow
	private ChunkStorage storage;

	/** A chunk shown in place of one still on disk (ChunkLoad) isn't populated, so nothing spills into its neighbours. */
	@Inject(method = "decorate", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$noDecorate(ChunkSource source, int x, int z, CallbackInfo ci) {
		if (ChunkLoad.isProtectedIn(storage, x, z)) ci.cancel();
	}

	@Inject(method = "save", at = @At("RETURN"))
	private void oldfaithful$commit(boolean saveEntities, LoadingDisplay display, CallbackInfoReturnable<Boolean> cir) {
		if (saveEntities) Committer.commitAll();
	}
}
