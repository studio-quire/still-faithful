package org.studioquire.oldfaithful.speed.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.WorldRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.speed.LightCache;
import org.studioquire.oldfaithful.speed.LightCached;

/**
 * getRawBrightness(x, y, z) answers from {@link LightCache} when the region was made by a chunk rebuild
 * (ChunkBuilderMixin turns it on there and off after): the same value, computed once per position per rebuild.
 * Regions made anywhere else, by vanilla or mods, never cache.
 */
@Mixin(WorldRegion.class)
abstract class WorldRegionMixin implements LightCached {
	@Unique
	private LightCache oldfaithful$light;

	@Override
	public void oldfaithful$cacheLight(LightCache cache) {
		oldfaithful$light = cache;
	}

	@Inject(method = "getRawBrightness(III)I", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$cached(int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
		LightCache c = oldfaithful$light;
		if (c == null) return;
		int slot = c.slot(x, y, z);
		if (slot < 0) return;
		int level = c.get(slot);
		if (level >= 0) cir.setReturnValue(level);
	}

	@ModifyReturnValue(method = "getRawBrightness(III)I", at = @At("RETURN"))
	private int oldfaithful$remember(int level, int x, int y, int z) {
		LightCache c = oldfaithful$light;
		if (c != null && level >= 0 && level <= 127) {
			int slot = c.slot(x, y, z);
			if (slot >= 0) c.put(slot, level);
		}
		return level;
	}
}
