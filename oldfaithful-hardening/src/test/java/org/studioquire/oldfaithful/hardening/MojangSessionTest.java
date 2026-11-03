package org.studioquire.oldfaithful.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.studioquire.oldfaithful.hardening.session.MojangSession;

class MojangSessionTest {
	private static final String UUID = "853c80ef3c3749fdaa49938b674adae6";

	@Test
	void parsesLauncherSessions() {
		var prism = MojangSession.parse("token:eyJ.abc.def:853c80ef-3c37-49fd-aa49-938b674adae6");
		assertEquals("eyJ.abc.def", prism.accessToken);
		assertEquals(UUID, prism.uuid);

		var bare = MojangSession.parse("eyJ.abc.def");
		assertEquals("eyJ.abc.def", bare.accessToken);
		assertNull(bare.uuid);

		assertNull(MojangSession.parse("-"));
		assertNull(MojangSession.parse(""));
		assertNull(MojangSession.parse(null));
		assertNull(MojangSession.parse("token::" + UUID));
		assertNull(MojangSession.parse("token:abc:not-a-uuid").uuid);
	}

	@Test
	void joinsOverHttpsWithTheTokenInTheBody() {
		FakeHttp http = new FakeHttp().on(MojangSession.JOIN_URL, 204, "");
		assertEquals("ok", MojangSession.join(http, "jeb_", "token:secret\"token:" + UUID, "-1a2b3c"));

		assertEquals(1, http.requests.size());
		var request = http.requests.get(0);
		assertEquals("POST", request.method());
		assertTrue(request.url().startsWith("https://"));
		assertFalse(request.url().contains("secret"), "the token must never be in a URL");
		assertEquals("{\"accessToken\":\"secret\\\"token\",\"selectedProfile\":\"" + UUID + "\",\"serverId\":\"-1a2b3c\"}",
				request.body());
	}

	@Test
	void looksUpTheProfileWhenTheLauncherPassedOnlyAToken() {
		FakeHttp http = new FakeHttp()
				.on(MojangSession.PROFILE_BY_NAME_URL + "jeb_", 200, "{\"id\":\"" + UUID + "\",\"name\":\"jeb_\"}")
				.on(MojangSession.JOIN_URL, 204, "");
		assertEquals("ok", MojangSession.join(http, "jeb_", "bare-token", "abc"));
		assertTrue(http.requests.get(1).body().contains(UUID));
	}

	@Test
	void reportsRefusals() {
		FakeHttp http = new FakeHttp().on(MojangSession.JOIN_URL, 403,
				"{\"error\":\"ForbiddenOperationException\",\"errorMessage\":\"Invalid token\"}");
		assertEquals("The session server refused the login: Invalid token",
				MojangSession.join(http, "jeb_", "token:t:" + UUID, "abc"));
	}

	@Test
	void refusesOfflineSessionsAndOddChallengesWithoutAsking() {
		FakeHttp http = new FakeHttp();
		assertTrue(MojangSession.join(http, "jeb_", "-", "abc").contains("offline"));
		assertTrue(MojangSession.join(http, "jeb_", "token:t:" + UUID, "abc&x=1").contains("invalid"));
		assertTrue(MojangSession.join(http, "jeb_", "token:t:" + UUID, null).contains("invalid"));
		assertTrue(http.requests.isEmpty());
	}

	@Test
	void serverChecksHasJoined() throws IOException {
		String url = MojangSession.HAS_JOINED_URL + "?username=jeb_&serverId=-1a2b";
		assertTrue(MojangSession.hasJoined(new FakeHttp().on(url, 200, "{\"id\":\"" + UUID + "\",\"name\":\"jeb_\"}"),
				"jeb_", "-1a2b"));
		assertFalse(MojangSession.hasJoined(new FakeHttp().on(url, 200, "{\"id\":\"" + UUID + "\",\"name\":\"Notch\"}"),
				"jeb_", "-1a2b"));
		assertFalse(MojangSession.hasJoined(new FakeHttp().on(url, 204, ""), "jeb_", "-1a2b"));
		assertThrows(IOException.class, () -> MojangSession.hasJoined(new FakeHttp().on(url, 500, ""), "jeb_", "-1a2b"));
		assertFalse(MojangSession.hasJoined(new FakeHttp(), "bad name&x", "-1a2b"));
	}

	@Test
	void parsesTheGameUrlQuery() {
		var q = MojangSession.parseQuery("user=jeb_&serverId=-1a2b&user=other");
		assertEquals("jeb_", q.get("user"));
		assertEquals("-1a2b", q.get("serverId"));
		assertEquals("a b&c", MojangSession.parseQuery("user=a+b%26c").get("user"));
		assertTrue(MojangSession.parseQuery(null).isEmpty());
	}
}
