package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.login.LoginHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.TickRate;

/**
 * On joining a server, the client's ticks go at the server's pace: what a harness server running faster ticks says in
 * the login packet ({@code tickms=N}, the harness probe's ServerLoginTickRateMixin), else the game's own 20 a second.
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerTickRateMixin {
	@Shadow
	private Minecraft minecraft;

	@Inject(method = "onHello", at = @At("HEAD"))
	private void oldfaithful$serverTicks(LoginHelloPacket packet, CallbackInfo ci) {
		int ms = 50;
		if (packet.username != null && packet.username.startsWith("tickms=")) {
			try {
				ms = Integer.parseInt(packet.username.substring("tickms=".length()));
			} catch (NumberFormatException e) {
				ms = 50;
			}
		}
		TickRate.ms = Math.max(1, ms);
		((MinecraftTimerAccessor) minecraft).oldfaithful$timer().tps = 1000.0F / TickRate.ms;
		if (ms != 50) System.out.println("[tick-rate] the server ticks every " + ms + " ms, and so does this client");
	}
}
