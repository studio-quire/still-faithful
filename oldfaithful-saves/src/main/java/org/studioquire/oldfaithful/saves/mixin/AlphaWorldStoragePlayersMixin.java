package org.studioquire.oldfaithful.saves.mixin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.logging.Logger;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.storage.AlphaWorldStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.saves.SafeFiles;
import org.studioquire.oldfaithful.saves.WorldFiles;

/**
 * The server's player files: vanilla deletes {@code <name>.dat} and then renames a shared {@code _tmp_.dat} over it,
 * so a crash in between loses the player's inventory and position. Here the file is replaced in one rename after the
 * new one is on disk, and a file that can't be read is copied aside before it's replaced. Server only.
 */
@Mixin(AlphaWorldStorage.class)
abstract class AlphaWorldStoragePlayersMixin {
	@Shadow
	@Final
	private static Logger LOGGER;
	@Shadow
	@Final
	private File dir;
	@Shadow
	@Final
	private File playerDataDir;

	@Inject(method = "savePlayerData", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$save(PlayerEntity player, CallbackInfo ci) {
		ci.cancel();
		if (WorldFiles.isProtected(new File(playerDataDir, player.name + ".dat"))) return;
		try {
			NbtCompound nbt = new NbtCompound();
			player.write(nbt);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			NbtIo.writeCompressed(nbt, bytes);
			SafeFiles.replaceLater(new File(playerDataDir, player.name + ".dat"), bytes.toByteArray());
		} catch (Exception e) {
			LOGGER.warning("Failed to save player data for " + player.name);
		}
	}

	@Inject(method = "loadPlayerData(Ljava/lang/String;)Lnet/minecraft/nbt/NbtCompound;", at = @At("HEAD"))
	private void oldfaithful$load(String name, CallbackInfoReturnable<NbtCompound> cir) {
		SafeFiles.flush(new File(playerDataDir, name + ".dat"));
	}

	@Inject(method = "loadPlayerData(Ljava/lang/String;)Lnet/minecraft/nbt/NbtCompound;", at = @At("RETURN"))
	private void oldfaithful$loaded(String name, CallbackInfoReturnable<NbtCompound> cir) {
		if (cir.getReturnValue() == null) WorldFiles.playerUnreadable(dir, new File(playerDataDir, name + ".dat"), name);
	}
}
