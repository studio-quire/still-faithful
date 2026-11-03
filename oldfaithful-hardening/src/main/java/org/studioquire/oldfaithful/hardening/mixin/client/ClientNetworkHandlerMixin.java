package org.studioquire.oldfaithful.hardening.mixin.client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.client.util.Session;
import net.minecraft.network.packet.handshake.HandshakePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import org.studioquire.oldfaithful.hardening.session.MojangSession;
import org.studioquire.oldfaithful.hardening.util.Https;

/**
 * Joining an online-mode server, the game opens http://www.minecraft.net/game/joinserver.jsp with the session id in
 * the query: with a modern launcher that's the account's access token, sent in cleartext. That URL is never opened
 * now. The join goes to Mojang's session server over HTTPS, and the game's code gets back the one line it expects
 * ("ok", or the reason shown on the disconnect screen).
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Shadow private Minecraft minecraft;

	@WrapOperation(method = "onHandshake", at = @At(value = "INVOKE", target = "Ljava/net/URL;openStream()Ljava/io/InputStream;"))
	private InputStream oldfaithful$join(URL legacyUrl, Operation<InputStream> original,
			@Local(argsOnly = true) HandshakePacket packet) {
		Session session = minecraft.session;
		String answer = MojangSession.join(Https.REAL, session.username, session.sessionId, packet.name);
		return new ByteArrayInputStream((answer.replace('\n', ' ') + "\n").getBytes(StandardCharsets.UTF_8));
	}
}
