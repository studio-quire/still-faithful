package org.studioquire.oldfaithful.saves.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ReadOnlyServerWorld;
import net.minecraft.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.saves.UntickedNether;

/**
 * The server's Nether (the only ReadOnlyServerWorld): with allow-nether=false the tick loop skips it, so its copy of
 * the level's data never advances and must never be saved over the overworld's (UntickedNether). Same check as
 * MinecraftServer's tick loop. Server only.
 */
@Mixin(ReadOnlyServerWorld.class)
abstract class ReadOnlyServerWorldMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void oldfaithful$unticked(MinecraftServer server, WorldStorage storage, String saveName, int dimension, long seed, ServerWorld delegate,
			CallbackInfo ci) {
		if (!server.properties.getProperty("allow-nether", true)) UntickedNether.markStale(((World) (Object) this).getProperties());
	}
}
