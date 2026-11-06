package org.studioquire.oldfaithful.hardening.mixin.server;

import net.minecraft.network.packet.login.LoginHelloPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.studioquire.oldfaithful.hardening.login.Usernames;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * The login packet, checked before the server acts on it:
 * <ul>
 * <li>Only one per connection. In online mode each one
 * costs the server a session check,
 * and an honest client sends exactly one.</li>
 * <li>The name must be usable as a file name ({@link Usernames}),
 * since player data is saved under it.</li>
 * </ul>
 */
@Mixin(ServerLoginNetworkHandler.class)
abstract class ServerLoginNetworkHandlerMixin {
	@Shadow private String username;
	@Shadow public boolean closed;

	@Shadow
	public abstract void disconnect(String reason);

	@Inject(method = "onHello", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$checkHello(LoginHelloPacket packet, CallbackInfo ci) {
		if (closed) {
			ci.cancel(); // already refused; the rest of what it sent is ignored
			return;
		}
		if (username != null) {
			ci.cancel();
			Log.info("Second login packet from " + username + "; disconnecting");
			disconnect("Protocol error");
			return;
		}
		String problem = Usernames.problem(packet.username);
		if (problem != null) {
			ci.cancel();
			Log.info("Refused login name " + quote(packet.username) + ": " + problem);
			disconnect("Invalid username");
		}
	}

	private static String quote(String s) {
		if (s == null) return "null";
		StringBuilder b = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x20 || c == 0x7f) b.append(String.format("\\u%04x", (int) c));
			else b.append(c);
		}
		return b.append('"').toString();
	}
}
