package org.studioquire.oldfaithful.lighting.scenario.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.lighting.scenario.Scenario;

/**
 * {@code -Doldfaithful.lighting.pace=N}: the server works through at most N lighting passes (500 updates each) per
 * world per tick, as the single player client does one pass a frame, instead of emptying the queue every tick.
 */
@Mixin(MinecraftServer.class)
abstract class ServerPaceMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/ServerWorld;doLightingUpdates()Z"))
	private boolean oldfaithful$pace(ServerWorld world, Operation<Boolean> original) {
		return original.call(world) && Scenario.morePasses(world);
	}
}
