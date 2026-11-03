package org.studioquire.oldfaithful.speed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.world.WorldRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.speed.LightCache;
import org.studioquire.oldfaithful.speed.LightCached;

/**
 * The region a rebuild reads the world through caches light levels for that rebuild (WorldRegionMixin). Nothing
 * changes the world while a chunk is meshed; the cache is taken off the region when the rebuild returns, so a region
 * kept past it (no vanilla code does) reads the world again.
 */
@Mixin(ChunkBuilder.class)
abstract class ChunkBuilderMixin {
	@Shadow
	public int x;
	@Shadow
	public int y;
	@Shadow
	public int z;
	@Shadow
	public int sizeX;
	@Shadow
	public int sizeY;
	@Shadow
	public int sizeZ;

	@Unique
	private WorldRegion oldfaithful$region;

	@ModifyExpressionValue(method = "rebuild", at = @At(value = "NEW", target = "Lnet/minecraft/world/WorldRegion;"))
	private WorldRegion oldfaithful$cacheLight(WorldRegion region) {
		// The region the rebuild asks for: its blocks plus one all round.
		((LightCached) region).oldfaithful$cacheLight(LightCache.begin(x - 1, y - 1, z - 1, x + sizeX, y + sizeY, z + sizeZ));
		oldfaithful$region = region;
		return region;
	}

	@Inject(method = "rebuild", at = @At("RETURN"))
	private void oldfaithful$uncacheLight(CallbackInfo ci) {
		WorldRegion region = oldfaithful$region;
		if (region == null) return;
		oldfaithful$region = null;
		((LightCached) region).oldfaithful$cacheLight(null);
	}
}
