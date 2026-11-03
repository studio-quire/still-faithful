package org.studioquire.oldfaithful.hardening.skins;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.studioquire.oldfaithful.hardening.session.MojangSession;
import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.hardening.util.Json;

/**
 * Skins and capes from Mojang over HTTPS. The game asks for {@code http://s3.amazonaws.com/MinecraftSkins/<name>.png}
 * (and MinecraftCloaks), buckets that are long gone, over plain HTTP, and hands whatever comes back to ImageIO. Here
 * the name is looked up through Mojang's profile API, the texture is fetched from textures.minecraft.net over HTTPS,
 * and images are size-checked before decoding. Any other plain-HTTP image request is refused.
 */
public final class Skins {
	public static final String LEGACY_SKINS = "http://s3.amazonaws.com/MinecraftSkins/";
	public static final String LEGACY_CAPES = "http://s3.amazonaws.com/MinecraftCloaks/";
	public static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern TEXTURE_URL = Pattern.compile("https?://textures\\.minecraft\\.net/texture/([0-9a-f]{1,128})");
	private static final int MAX_JSON = 64 * 1024;
	private static final int MAX_IMAGE_BYTES = 1 << 20;
	/** Classic skins are 64x32, current ones 64x64; capes 64x32. Generous, but no decompression bombs. */
	private static final int MAX_IMAGE_SIDE = 1024;

	/** A player's textures as HTTPS URLs; either may be null. */
	public static final class Textures {
		static final Textures NONE = new Textures(null, null);

		public final String skin;
		public final String cape;

		Textures(String skin, String cape) {
			this.skin = skin;
			this.cape = cape;
		}
	}

	private final Https.Client http;
	/**
	 * One lookup per name per session. A name without an account is an answer and is kept; a lookup that failed (a
	 * timeout, Mojang down) is forgotten after {@link #RETRY_AFTER_MS}, so the skin appears once Mojang answers again.
	 */
	private final ConcurrentMap<String, FutureTask<Textures>> lookups = new ConcurrentHashMap<String, FutureTask<Textures>>();
	private final ConcurrentMap<String, Long> failedAt = new ConcurrentHashMap<String, Long>();
	public static final long RETRY_AFTER_MS = 60_000;
	private final java.util.function.LongSupplier clock;

	public Skins(Https.Client http) {
		this(http, System::currentTimeMillis);
	}

	/** With a clock of the caller's, for tests of the retry delay. */
	public Skins(Https.Client http, java.util.function.LongSupplier clock) {
		this.http = http;
		this.clock = clock;
	}

	/**
	 * The HTTPS URL to fetch for an image URL the game asked for, or null to fetch nothing (no such player, no cape,
	 * or a plain-HTTP URL we don't recognise).
	 */
	public String resolve(String gameUrl) throws IOException {
		if (gameUrl == null) return null;
		boolean skin = gameUrl.startsWith(LEGACY_SKINS);
		if (skin || gameUrl.startsWith(LEGACY_CAPES)) {
			String rest = gameUrl.substring((skin ? LEGACY_SKINS : LEGACY_CAPES).length());
			if (!rest.endsWith(".png")) return null;
			String name = rest.substring(0, rest.length() - 4);
			if (!NAME.matcher(name).matches()) return null;
			Textures textures = textures(name);
			return skin ? textures.skin : textures.cape;
		}
		return gameUrl.startsWith("https://") ? gameUrl : null;
	}

	Textures textures(String name) throws IOException {
		String key = name.toLowerCase(java.util.Locale.ROOT);
		Long failed = failedAt.get(key);
		if (failed != null && now() - failed < RETRY_AFTER_MS) throw new IOException("Skin lookup for " + name + " failed recently");
		FutureTask<Textures> task = new FutureTask<Textures>(() -> lookup(name));
		FutureTask<Textures> existing = lookups.putIfAbsent(key, task);
		if (existing == null) {
			task.run();
		} else {
			task = existing;
		}
		try {
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted");
		} catch (ExecutionException e) {
			failedAt.put(key, now());
			lookups.remove(key, task);
			throw new IOException("Skin lookup failed for " + name + ": " + e.getCause());
		}
	}

	private long now() {
		return clock.getAsLong();
	}

	private Textures lookup(String name) throws IOException {
		String uuid = MojangSession.lookupUuid(http, name);
		if (uuid == null) return Textures.NONE;

		Https.Response response = http.request("GET", PROFILE_URL + uuid, null, null, MAX_JSON);
		if (response.status == 204 || response.status == 404) return Textures.NONE;
		if (response.status != 200) throw new IOException("Profile answered HTTP " + response.status);
		return parseProfile(response.text());
	}

	/** Reads the skin and cape URLs from a session-server profile, keeping only textures.minecraft.net ones. */
	public static Textures parseProfile(String json) throws IOException {
		try {
			Map<String, Object> profile = Json.object(Json.parse(json));
			Object properties = profile.get("properties");
			if (!(properties instanceof List)) return Textures.NONE;
			for (Object p : (List<?>) properties) {
				Map<String, Object> property = Json.object(p);
				if (!"textures".equals(Json.string(property, "name"))) continue;
				String value = Json.string(property, "value");
				if (value == null) return Textures.NONE;
				String decoded = new String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
				Object textures = Json.object(Json.parse(decoded)).get("textures");
				if (!(textures instanceof Map)) return Textures.NONE;
				Map<String, Object> t = Json.object(textures);
				return new Textures(textureUrl(t.get("SKIN")), textureUrl(t.get("CAPE")));
			}
			return Textures.NONE;
		} catch (IllegalArgumentException e) {
			throw new IOException("Malformed profile", e);
		}
	}

	private static String textureUrl(Object texture) {
		if (!(texture instanceof Map)) return null;
		String url = Json.string(Json.object(texture), "url");
		if (url == null) return null;
		Matcher m = TEXTURE_URL.matcher(url);
		return m.matches() ? "https://textures.minecraft.net/texture/" + m.group(1) : null;
	}

	/** Fetches and decodes an image, refusing large downloads and large dimensions before decoding pixels. */
	public BufferedImage download(String httpsUrl) throws IOException {
		Https.Response response = http.request("GET", httpsUrl, null, null, MAX_IMAGE_BYTES);
		if (response.status != 200) return null;
		return decode(response.body);
	}

	public static BufferedImage decode(byte[] bytes) throws IOException {
		ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
		if (in == null) throw new IOException("Unreadable image");
		try {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) throw new IOException("Unknown image format");
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				int w = reader.getWidth(0);
				int h = reader.getHeight(0);
				if (w <= 0 || h <= 0 || w > MAX_IMAGE_SIDE || h > MAX_IMAGE_SIDE) {
					throw new IOException("Image too large: " + w + "x" + h);
				}
				return reader.read(0);
			} finally {
				reader.dispose();
			}
		} finally {
			in.close();
		}
	}
}
