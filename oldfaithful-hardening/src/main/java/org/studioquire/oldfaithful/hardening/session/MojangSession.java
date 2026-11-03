package org.studioquire.oldfaithful.hardening.session;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.hardening.util.Json;

/**
 * Online-mode logins through Mojang's current session server, over HTTPS.
 *
 * <p>The game's client sent {@code joinserver.jsp?user=..&sessionId=..} to www.minecraft.net over plain HTTP. With a
 * modern launcher the session id carries the account's live access token, so joining any online-mode server
 * broadcast that token in cleartext. The server side asked {@code checkserver.jsp} over plain HTTP whether a player
 * had joined, and believed any "YES" it read back, so anyone on the path could let impostors in. Both endpoints are
 * gone; these are their replacements.
 */
public final class MojangSession {
	public static final String JOIN_URL = "https://sessionserver.mojang.com/session/minecraft/join";
	public static final String HAS_JOINED_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined";
	public static final String PROFILE_BY_NAME_URL = "https://api.mojang.com/users/profiles/minecraft/";

	private static final int MAX_RESPONSE = 64 * 1024;
	private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{32}");
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	/** Servers send a hex number; anything else is refused rather than passed on. */
	private static final Pattern SERVER_ID = Pattern.compile("-?[0-9a-zA-Z]{1,40}");

	private MojangSession() {
	}

	/** What a launcher put in the game's session id. */
	public static final class Credentials {
		public final String accessToken;
		/** Undashed profile UUID, or null if the launcher didn't pass one. */
		public final String uuid;

		Credentials(String accessToken, String uuid) {
			this.accessToken = accessToken;
			this.uuid = uuid;
		}
	}

	/**
	 * Modern launchers (Prism, MultiMC, the official one's legacy path) pass {@code token:<accessToken>:<uuid>};
	 * some pass the bare token. Offline sessions ("-", empty, or none) have no credentials.
	 */
	public static Credentials parse(String sessionId) {
		if (sessionId == null) return null;
		String s = sessionId.trim();
		if (s.isEmpty() || s.equals("-")) return null;
		if (s.startsWith("token:")) {
			String[] parts = s.split(":", 3);
			if (parts.length < 2 || parts[1].isEmpty()) return null;
			String uuid = parts.length == 3 ? undash(parts[2]) : null;
			return new Credentials(parts[1], uuid != null && UUID.matcher(uuid).matches() ? uuid : null);
		}
		return new Credentials(s, null);
	}

	/**
	 * The client side of an online-mode login.
	 *
	 * @return "ok" if the session server accepted the join, otherwise a message for the disconnect screen
	 */
	public static String join(Https.Client http, String username, String sessionId, String serverId) {
		if (serverId == null || !SERVER_ID.matcher(serverId).matches()) {
			return "The server sent an invalid login challenge.";
		}
		Credentials credentials = parse(sessionId);
		if (credentials == null) {
			return "This server checks accounts, and the game was started without one (offline).";
		}

		try {
			String uuid = credentials.uuid != null ? credentials.uuid : lookupUuid(http, username);
			if (uuid == null) return "No Minecraft account is named " + username + ".";

			String body = "{\"accessToken\":" + Json.quote(credentials.accessToken)
					+ ",\"selectedProfile\":" + Json.quote(uuid)
					+ ",\"serverId\":" + Json.quote(serverId) + "}";
			Https.Response response = http.request("POST", JOIN_URL, "application/json",
					body.getBytes(StandardCharsets.UTF_8), MAX_RESPONSE);
			if (Https.ok(response.status)) return "ok";
			return "The session server refused the login: " + errorMessage(response);
		} catch (IOException e) {
			return "Could not reach the session server: " + e.getMessage();
		}
	}

	/**
	 * The server side of an online-mode login: whether Mojang confirms that this player joined with this server id.
	 *
	 * @throws IOException if the session server couldn't be asked or answered unexpectedly
	 */
	public static boolean hasJoined(Https.Client http, String username, String serverId) throws IOException {
		if (username == null || !USERNAME.matcher(username).matches()) return false;
		Https.Response response = http.request("GET",
				HAS_JOINED_URL + "?username=" + encode(username) + "&serverId=" + encode(serverId), null, null,
				MAX_RESPONSE);
		if (response.status == 204) return false;
		if (response.status != 200) throw new IOException("Session server answered HTTP " + response.status);
		try {
			Map<String, Object> profile = Json.object(Json.parse(response.text()));
			return username.equals(Json.string(profile, "name"));
		} catch (IllegalArgumentException e) {
			throw new IOException("Session server sent a malformed profile", e);
		}
	}

	/** The undashed UUID of the account with this name, or null if there is none. */
	public static String lookupUuid(Https.Client http, String username) throws IOException {
		if (username == null || !USERNAME.matcher(username).matches()) return null;
		Https.Response response = http.request("GET", PROFILE_BY_NAME_URL + username, null, null, MAX_RESPONSE);
		if (response.status == 204 || response.status == 404) return null;
		if (response.status != 200) throw new IOException("Profile lookup answered HTTP " + response.status);
		try {
			String id = Json.string(Json.object(Json.parse(response.text())), "id");
			return id != null && UUID.matcher(id).matches() ? id : null;
		} catch (IllegalArgumentException e) {
			throw new IOException("Profile lookup sent malformed data", e);
		}
	}

	/** Decodes a URL query string; a repeated key keeps its first value. */
	public static Map<String, String> parseQuery(String query) {
		Map<String, String> result = new java.util.LinkedHashMap<String, String>();
		if (query == null) return result;
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq <= 0) continue;
			try {
				String key = java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8");
				if (!result.containsKey(key)) result.put(key, java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
			} catch (UnsupportedEncodingException | IllegalArgumentException e) {
				// Skip malformed pairs; a missing value fails the check later.
			}
		}
		return result;
	}

	private static String errorMessage(Https.Response response) {
		try {
			Map<String, Object> error = Json.object(Json.parse(response.text()));
			String message = Json.string(error, "errorMessage");
			if (message != null && !message.isEmpty()) return message;
		} catch (IllegalArgumentException ignored) {
			// Not JSON; fall through to the status.
		}
		return "HTTP " + response.status;
	}

	private static String undash(String uuid) {
		return uuid.replace("-", "");
	}

	private static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}
}
