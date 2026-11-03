package org.studioquire.oldfaithful.fixes.mixin.ticks;

import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.PendingTicks;

/**
 * #42, a chunk's ticks: read from its save, they join the world's queue when the chunk loads; when it unloads, they
 * leave the queue and ride with the chunk to its last save (both chunk caches unload before they save).
 */
@Mixin(Chunk.class)
abstract class ChunkMixin implements PendingTicks.Carrier {
	@Shadow
	public World world;
	@Shadow
	@Final
	public int x;
	@Shadow
	@Final
	public int z;

	@Unique
	private NbtList oldfaithful$ticks;

	@Override
	public NbtList oldfaithful$ticks() {
		return this.oldfaithful$ticks;
	}

	@Override
	public void oldfaithful$setTicks(NbtList ticks) {
		this.oldfaithful$ticks = ticks;
	}

	@Inject(method = "load", at = @At("TAIL"))
	private void oldfaithful$queueSavedTicks(CallbackInfo ci) {
		if (this.oldfaithful$ticks != null && !this.world.isRemote) PendingTicks.restore(this.world, this.x, this.z, this.oldfaithful$ticks);
		this.oldfaithful$ticks = null;
	}

	@Inject(method = "unload", at = @At("HEAD"))
	private void oldfaithful$takeTicks(CallbackInfo ci) {
		if (!this.world.isRemote) this.oldfaithful$ticks = PendingTicks.collect(this.world, this.x, this.z, true);
	}
}
