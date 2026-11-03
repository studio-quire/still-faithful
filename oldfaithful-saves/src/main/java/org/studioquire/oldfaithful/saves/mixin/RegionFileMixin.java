package org.studioquire.oldfaithful.saves.mixin;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import net.minecraft.world.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.saves.RegionFileAccess;
import org.studioquire.oldfaithful.saves.RegionWrites;
import org.studioquire.oldfaithful.saves.SaveWarnings;

/**
 * Chunk saves go through {@link RegionWrites} (never in place; the header only ever points at complete records) and
 * closing a region commits first. Vanilla's own fields stay the truth other code reads: the in-memory header and the
 * free-sector flags are kept current.
 */
@Mixin(RegionFile.class)
abstract class RegionFileMixin implements RegionFileAccess {
	@Shadow
	@Final
	private File file;
	@Shadow
	private RandomAccessFile randomAccessFile;
	@Shadow
	@Final
	private int[] chunkBlockInfo;
	@Shadow
	@Final
	private int[] chunkSaveTimes;
	@Shadow
	private ArrayList blockEmptyFlags;
	@Shadow
	private int bytesWritten;

	@Unique
	private RegionWrites oldfaithful$writes;

	@Override
	public RegionWrites oldfaithful$writes() {
		return oldfaithful$writes;
	}

	/** Before the constructor's own (broken) padding of an unaligned file can zero the chunk index. */
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/io/File;exists()Z", ordinal = 0))
	private void oldfaithful$align(File f, CallbackInfo ci) {
		RegionWrites.alignBeforeOpen(f);
	}

	@SuppressWarnings("unchecked")
	@Inject(method = "<init>", at = @At("RETURN"))
	private void oldfaithful$open(File f, CallbackInfo ci) {
		// The constructor prints and carries on if the file couldn't be opened; vanilla's behaviour then stands.
		if (randomAccessFile != null) {
			oldfaithful$writes = new RegionWrites(this, file, randomAccessFile, chunkBlockInfo, chunkSaveTimes, blockEmptyFlags);
		}
	}

	@Inject(method = "writeChunkData(II[BI)V", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$write(int x, int z, byte[] data, int size, CallbackInfo ci) {
		RegionWrites writes = oldfaithful$writes;
		if (writes == null) return;
		ci.cancel();
		int index = x + z * 32;
		try {
			int grown = writes.write(index, data, size);
			if (grown >= 0) {
				bytesWritten += grown;
			} else {
				writes.writeOversized(index, data, size);
				SaveWarnings.oversized(file, x, z, size);
			}
		} catch (IOException e) {
			// Vanilla prints the trace and carries on; the previous record stays, untouched.
			e.printStackTrace();
			SaveWarnings.writeFailed(file, x, z, e);
		}
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void oldfaithful$close(CallbackInfo ci) {
		if (oldfaithful$writes != null) oldfaithful$writes.commitAndClose();
	}
}
