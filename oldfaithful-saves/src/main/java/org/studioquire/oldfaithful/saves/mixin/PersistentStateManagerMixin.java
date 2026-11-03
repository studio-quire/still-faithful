package org.studioquire.oldfaithful.saves.mixin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.storage.PersistentStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.saves.SafeFiles;

/**
 * Map files and idcounts.dat (data/): vanilla overwrites them in place, so a crash mid-write leaves a damaged map, or
 * map numbering that starts again and overwrites existing maps. Writes now replace the file whole.
 */
@Mixin(PersistentStateManager.class)
abstract class PersistentStateManagerMixin {
	@WrapOperation(method = {"save(Lnet/minecraft/world/PersistentState;)V", "getIdCount"},
			at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileOutputStream;"))
	private FileOutputStream oldfaithful$replacing(File file, Operation<FileOutputStream> original) throws IOException {
		return SafeFiles.replacingStream(file);
	}

	@WrapOperation(method = {"getOrCreate", "loadIdCounts"},
			at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileInputStream;"))
	private FileInputStream oldfaithful$read(File file, Operation<FileInputStream> original) {
		SafeFiles.flush(file);
		return original.call(file);
	}
}
