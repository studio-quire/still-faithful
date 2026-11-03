package org.studioquire.oldfaithful.lighting.mixin;

import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.gen.chunk.NetherChunkGenerator;
import net.minecraft.world.gen.chunk.OverworldChunkGenerator;
import net.minecraft.world.gen.chunk.SkyChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.lighting.Relight;

/**
 * While a generator decorates (trees, lakes, lava, glowstone, ores), the light updates it asks for are also noted for
 * {@link Relight}, which lights what they make too dark at the next pass instead of leaving it to the queue.
 */
@Mixin({OverworldChunkGenerator.class, NetherChunkGenerator.class, SkyChunkGenerator.class})
abstract class GeneratorDecorateMixin {
	@Inject(method = "decorate", at = @At("HEAD"))
	private void oldfaithful$decorating(ChunkSource source, int x, int z, CallbackInfo ci) {
		Relight.decorating(true);
	}

	@Inject(method = "decorate", at = @At("RETURN"))
	private void oldfaithful$decorated(ChunkSource source, int x, int z, CallbackInfo ci) {
		Relight.decorating(false);
	}
}
