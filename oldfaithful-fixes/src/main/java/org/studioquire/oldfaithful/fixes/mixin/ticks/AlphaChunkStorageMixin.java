package org.studioquire.oldfaithful.fixes.mixin.ticks;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AlphaChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.fixes.PendingTicks;

/** #42, the save: a chunk's pending ticks written as TileTicks, and read back onto the chunk when it's read. */
@Mixin(AlphaChunkStorage.class)
abstract class AlphaChunkStorageMixin {
	@Inject(method = "saveChunkToNbt", at = @At("TAIL"))
	private static void oldfaithful$writeTicks(Chunk chunk, World world, NbtCompound nbt, CallbackInfo ci) {
		// An unloading chunk has taken its ticks off the queue already; otherwise they're read from the queue.
		NbtList ticks = ((PendingTicks.Carrier) chunk).oldfaithful$ticks();
		if (ticks == null) ticks = PendingTicks.collect(world, chunk.x, chunk.z, false);
		if (ticks.size() > 0) nbt.put(PendingTicks.TAG, ticks);
	}

	@Inject(method = "loadChunkFromNbt", at = @At("RETURN"))
	private static void oldfaithful$readTicks(World world, NbtCompound nbt, CallbackInfoReturnable<Chunk> cir) {
		NbtList ticks = PendingTicks.read(nbt);
		if (ticks != null && cir.getReturnValue() != null) ((PendingTicks.Carrier) cir.getReturnValue()).oldfaithful$setTicks(ticks);
	}
}
