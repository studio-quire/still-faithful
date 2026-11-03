package org.studioquire.oldfaithful.saves.mixin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.storage.AlphaWorldStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.saves.SafeFiles;

/** Renaming a world in the menu rewrote its level.dat in place; now it's replaced whole. */
@Mixin(AlphaWorldStorageSource.class)
abstract class AlphaWorldStorageSourceMixin {
	@WrapOperation(method = "rename", at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileOutputStream;"))
	private FileOutputStream oldfaithful$replacing(File file, Operation<FileOutputStream> original) throws IOException {
		SafeFiles.flush(file);
		return SafeFiles.replacingStreamNow(file);
	}
}
