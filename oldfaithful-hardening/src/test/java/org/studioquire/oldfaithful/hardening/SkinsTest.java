package org.studioquire.oldfaithful.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import org.studioquire.oldfaithful.hardening.session.MojangSession;
import org.studioquire.oldfaithful.hardening.skins.Skins;

class SkinsTest {
	private static final String UUID = "853c80ef3c3749fdaa49938b674adae6";
	private static final String HASH = "7fd9ba42a7c81eeea22f1524271ae85a8e045ce0af5a6ae16c6406ae917e68b5";

	private static String profile(String texturesJson) {
		String value = Base64.getEncoder().encodeToString(texturesJson.getBytes(StandardCharsets.UTF_8));
		return "{\"id\":\"" + UUID + "\",\"name\":\"jeb_\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\"}]}";
	}

	@Test
	void mapsLegacyUrlsToMojangOverHttps() throws IOException {
		FakeHttp http = new FakeHttp()
				.on(MojangSession.PROFILE_BY_NAME_URL + "jeb_", 200, "{\"id\":\"" + UUID + "\",\"name\":\"jeb_\"}")
				.on(Skins.PROFILE_URL + UUID, 200, profile("{\"textures\":{"
						+ "\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + HASH + "\"},"
						+ "\"CAPE\":{\"url\":\"http://textures.minecraft.net/texture/abc123\"}}}"));
		Skins skins = new Skins(http);

		assertEquals("https://textures.minecraft.net/texture/" + HASH, skins.resolve(Skins.LEGACY_SKINS + "jeb_.png"));
		assertEquals("https://textures.minecraft.net/texture/abc123", skins.resolve(Skins.LEGACY_CAPES + "jeb_.png"));
		assertEquals(2, http.requests.size(), "skin and cape share one lookup");
		for (var r : http.requests) assertTrue(r.url().startsWith("https://"));
	}

	@Test
	void refusesOtherPlainHttpAndOddNames() throws IOException {
		FakeHttp http = new FakeHttp();
		Skins skins = new Skins(http);
		assertNull(skins.resolve("http://example.com/x.png"));
		assertNull(skins.resolve(Skins.LEGACY_SKINS + "../x.png"));
		assertNull(skins.resolve(Skins.LEGACY_SKINS + "jeb_.png?x"));
		assertNull(skins.resolve(Skins.LEGACY_SKINS + "a_name_far_too_long.png"));
		assertTrue(http.requests.isEmpty());
		assertEquals("https://example.com/x.png", skins.resolve("https://example.com/x.png"));
	}

	@Test
	void keepsOnlyMojangTextureHosts() throws IOException {
		var t = Skins.parseProfile(profile("{\"textures\":{"
				+ "\"SKIN\":{\"url\":\"http://evil.example/texture/abc\"},"
				+ "\"CAPE\":{\"url\":\"http://textures.minecraft.net.evil.example/texture/abc\"}}}"));
		assertNull(t.skin);
		assertNull(t.cape);
	}

	/** A failed lookup (Mojang down) isn't kept for the session: after a minute the name is asked about again. */
	@Test
	void failedLookupsAreRetriedLater() throws IOException {
		FakeHttp http = new FakeHttp().on(MojangSession.PROFILE_BY_NAME_URL + "jeb_", 503, "");
		long[] clock = {1_000_000};
		Skins skins = new Skins(http, () -> clock[0]);
		assertThrows(IOException.class, () -> skins.resolve(Skins.LEGACY_SKINS + "jeb_.png"));
		int asked = http.requests.size();
		assertThrows(IOException.class, () -> skins.resolve(Skins.LEGACY_SKINS + "jeb_.png"));
		assertEquals(asked, http.requests.size(), "not asked again at once");

		clock[0] += Skins.RETRY_AFTER_MS + 1;
		http.on(MojangSession.PROFILE_BY_NAME_URL + "jeb_", 200, "{\"id\":\"" + UUID + "\",\"name\":\"jeb_\"}")
				.on(Skins.PROFILE_URL + UUID, 200, profile("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + HASH + "\"}}}"));
		assertEquals("https://textures.minecraft.net/texture/" + HASH, skins.resolve(Skins.LEGACY_SKINS + "jeb_.png"));
	}

	@Test
	void unknownPlayersGetNoSkin() throws IOException {
		Skins skins = new Skins(new FakeHttp().on(MojangSession.PROFILE_BY_NAME_URL + "nobody", 204, ""));
		assertNull(skins.resolve(Skins.LEGACY_SKINS + "nobody.png"));
	}

	@Test
	void decodesNormalSkinsAndRefusesHugeImagesBeforeDecoding() throws IOException {
		BufferedImage decoded = Skins.decode(png(64, 64));
		assertEquals(64, decoded.getWidth());
		assertThrows(IOException.class, () -> Skins.decode(png(4096, 8)));
		assertThrows(IOException.class, () -> Skins.decode("not an image".getBytes(StandardCharsets.UTF_8)));
	}

	private static byte[] png(int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
		return out.toByteArray();
	}
}
