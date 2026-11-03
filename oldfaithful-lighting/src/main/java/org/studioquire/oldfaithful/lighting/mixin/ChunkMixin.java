package org.studioquire.oldfaithful.lighting.mixin;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.lighting.Relight;

/** A chunk generated or read from disk has just been put in its world: check it at the next lighting pass. */
@Mixin(Chunk.class)
abstract class ChunkMixin {
	@Shadow
	public World world;

	@Inject(method = "load", at = @At("TAIL"))
	private void oldfaithful$entered(CallbackInfo ci) {
		Relight.entered(world, (Chunk) (Object) this);
	}
}
