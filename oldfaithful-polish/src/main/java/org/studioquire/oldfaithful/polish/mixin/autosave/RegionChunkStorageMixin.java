package org.studioquire.oldfaithful.polish.mixin.autosave;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AlphaChunkStorage;
import net.minecraft.world.chunk.storage.RegionChunkStorage;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionIo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.BackgroundSaves;
import org.studioquire.oldfaithful.saves.ChunkLoad;

/**
 * #14 ({@link BackgroundSaves}): a chunk save makes the chunk's save data here, on the game thread, as beta does (its
 * session check, the same NBT, built by the same code other mods add to), and hands the uncompressed bytes to the
 * writer instead of compressing and writing them. A chunk the saves mod keeps from being saved over (its record on
 * disk couldn't be read) isn't queued. The storage's flush, at the end of every full save, waits for the queue.
 */
@Mixin(RegionChunkStorage.class)
abstract class RegionChunkStorageMixin {
	@Shadow
	@Final
	private File dir;

	@Inject(method = "saveChunk", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$saveInBackground(World world, Chunk chunk, CallbackInfo ci) {
		ci.cancel();
		world.checkSessionLock();
		if (ChunkLoad.isProtected(dir, chunk.x, chunk.z)) return;
		try {
			NbtCompound root = new NbtCompound();
			NbtCompound level = new NbtCompound();
			root.put("Level", (NbtElement) level);
			AlphaChunkStorage.saveChunkToNbt(chunk, world, level);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(96 * 1024);
			DataOutputStream out = new DataOutputStream(bytes);
			NbtIo.write(root, out);
			out.close();
			RegionFile region = RegionIo.getRegionFile(dir, chunk.x, chunk.z);
			BackgroundSaves.enqueue(region, dir, chunk.x, chunk.z, bytes.toByteArray(), world.getProperties());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Inject(method = "flush", at = @At("HEAD"))
	private void oldfaithful$fullSaveWaits(CallbackInfo ci) {
		BackgroundSaves.drain();
	}
}
