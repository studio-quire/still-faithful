package org.studioquire.oldfaithful.hardening.mixin.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.studioquire.oldfaithful.hardening.session.MojangSession;
import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * An online-mode server asks http://www.minecraft.net/game/checkserver.jsp whether a player really joined and lets
 * them in on a "YES". Over plain HTTP anyone on the path can answer that, and the endpoint is gone anyway, so no one
 * could log in. The question now goes to Mojang's session server over HTTPS; the game's code gets "YES" or "NO".
 */
@Mixin(targets = "net.minecraft.server.network.ServerLoginNetworkHandler$AuthThread")
abstract class ServerLoginAuthThreadMixin {
	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Ljava/net/URL;openStream()Ljava/io/InputStream;"))
	private InputStream oldfaithful$hasJoined(URL legacyUrl, Operation<InputStream> original) throws IOException {
		// The game built that URL with both values URL-encoded; read them back rather than reaching into its fields.
		Map<String, String> query = MojangSession.parseQuery(legacyUrl.getQuery());
		String username = query.get("user");
		boolean joined = MojangSession.hasJoined(Https.REAL, username, query.get("serverId"));
		if (!joined) Log.info("Session server: " + username + " has not joined this server; refusing");
		return new ByteArrayInputStream((joined ? "YES\n" : "NO\n").getBytes(StandardCharsets.UTF_8));
	}
}
