package org.studioquire.oldfaithful.fixes.mixin.tpfall;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** #60: the player a network handler moves. */
@Mixin(ServerPlayNetworkHandler.class)
public interface ServerPlayNetworkHandlerAccessor {
	@Accessor("player")
	ServerPlayerEntity oldfaithful$player();
}
