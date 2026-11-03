package org.studioquire.oldfaithful.saves.mixin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.storage.AlphaWorldStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.saves.SafeFiles;
import org.studioquire.oldfaithful.saves.UntickedNether;
import org.studioquire.oldfaithful.saves.WorldFiles;

/**
 * level.dat: the same data and the same rotation (level.dat_old kept), but the new file is forced to disk before it
 * replaces the old, off the game thread; loading falls back to a complete level.dat_new left by a crash between the
 * renames, and level.dat files that can't be read are copied aside before the game replaces them. A server's unticked
 * Nether never writes it (UntickedNether).
 */
@Mixin(AlphaWorldStorage.class)
abstract class AlphaWorldStorageMixin {
	@Shadow
	@Final
	private File dir;

	@Inject(method = "save(Lnet/minecraft/world/WorldProperties;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$save(WorldProperties properties, List players, CallbackInfo ci) {
		ci.cancel();
		if (UntickedNether.isStale(properties)) return;
		oldfaithful$queue(properties.asNbt(players));
	}

	@Inject(method = "save(Lnet/minecraft/world/WorldProperties;)V", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$save(WorldProperties properties, CallbackInfo ci) {
		ci.cancel();
		oldfaithful$queue(properties.asNbt());
	}

	private void oldfaithful$queue(NbtCompound data) {
		if (WorldFiles.isProtected(new File(dir, "level.dat"))) return;
		try {
			NbtCompound root = new NbtCompound();
			root.put("Data", (NbtElement) data);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			NbtIo.writeCompressed(root, bytes);
			SafeFiles.rotateLevelLater(dir, bytes.toByteArray());
		} catch (Exception e) {
			// As vanilla: the save is skipped and the files on disk stay as they are.
			e.printStackTrace();
		}
	}

	@Inject(method = "loadProperties", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$load(CallbackInfoReturnable<WorldProperties> cir) {
		SafeFiles.flush(new File(dir, "level.dat"));
		NbtCompound data = WorldFiles.interruptedLevel(dir);
		if (data != null) cir.setReturnValue(new WorldProperties(data));
	}

	@Inject(method = "loadProperties", at = @At("RETURN"))
	private void oldfaithful$loaded(CallbackInfoReturnable<WorldProperties> cir) {
		if (cir.getReturnValue() == null) WorldFiles.levelUnreadable(dir);
	}
}
