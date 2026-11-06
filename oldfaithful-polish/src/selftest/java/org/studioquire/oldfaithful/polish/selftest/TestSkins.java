package org.studioquire.oldfaithful.polish.selftest;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.studioquire.oldfaithful.hardening.session.MojangSession;
import org.studioquire.oldfaithful.hardening.skins.Skins;
import org.studioquire.oldfaithful.hardening.util.Https;

/**
 * Skins of our own for SkinCheck, answered at the one point where hardening meets the network (SkinHttpsMixin), so the
 * name lookup, the profile, the size-checked decode and the game's download thread all run as they do for a real
 * player. Nothing else is answered here; every other request goes out as usual.
 *
 * The loud skins give each part and layer its own hue ({@link #HEAD} to {@link #LEFT_TROUSER}, twelve steps round the
 * colour wheel) so SkinCheck can find them in a picture whatever the light does to their brightness. Each face is a
 * different brightness, and each has a black corner at its texture's top left and a white one at its top right, which
 * show a mirrored or upside-down face. Layers are a checkerboard, so the limb under them shows through.
 */
final class TestSkins {
	static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, HAT = 6, JACKET = 7,
			RIGHT_SLEEVE = 8, LEFT_SLEEVE = 9, RIGHT_TROUSER = 10, LEFT_TROUSER = 11;

	static final String WIDE = "QuireLoudWide", SLIM = "QuireLoudSlim", OLD = "QuireLoudOld", SUIT = "QuireSuit",
			SUIT_SLIM = "QuireSuitSlim", CAPED = "QuireCaped", SIEVE = "QuireSieve", LID = "QuireLid", GLASS = "QuireGlass",
			SOLID = "QuireSolid";
	/** In {@link #glass}: a pixel of the right leg's front, transparent in the file, whose colour is still there. */
	static final int HIDDEN_X = 5, HIDDEN_Y = 25, HIDDEN_RGB = 0xFF00FF;

	private static final class Skin {
		final String name, uuid, hash, capeHash;
		final byte[] png, cape;
		final boolean slim;

		Skin(int i, String name, BufferedImage image, boolean slim, BufferedImage cape) {
			this.name = name;
			this.uuid = String.format("%032x", i + 1);
			this.hash = String.format("%064x", i + 1);
			this.capeHash = String.format("%064x", i + 1001);
			this.png = png(image);
			this.cape = cape == null ? null : png(cape);
			this.slim = slim;
			save(image, name);
		}
	}

	private static final Map<String, Skin> SKINS = new LinkedHashMap<>();
	/** Requests answered per lower-case name, by kind: "name" and "profile". */
	static final Map<String, Integer> ASKED = new ConcurrentHashMap<>();

	static {
		BufferedImage cape = cape();
		add(WIDE, loud(false, true), false, null);
		add(SLIM, loud(true, true), true, null);
		add(OLD, loud(false, false), false, null);
		add(SUIT, suit(false), false, null);
		add(SUIT_SLIM, suit(true), true, null);
		add(CAPED, loud(false, true), false, cape);
		add(SIEVE, sieve(), false, null);
		add(LID, lid(), false, null);
		add(GLASS, glass(), false, null);
		add(SOLID, solid(), false, null);
		save(cape, "cape");
	}

	private TestSkins() {
	}

	private static void add(String name, BufferedImage image, boolean slim, BufferedImage cape) {
		SKINS.put(name.toLowerCase(Locale.ROOT), new Skin(SKINS.size(), name, image, slim, cape));
	}

	/** For Https: the answer to a request about our skins, or null for any other. */
	static Https.Response answer(String url) {
		if (url.startsWith(MojangSession.PROFILE_BY_NAME_URL)) {
			Skin s = SKINS.get(url.substring(MojangSession.PROFILE_BY_NAME_URL.length()).toLowerCase(Locale.ROOT));
			if (s == null) return null;
			ASKED.merge("name " + s.name, 1, Integer::sum);
			return json("{\"id\":\"" + s.uuid + "\",\"name\":\"" + s.name + "\"}");
		}
		for (Skin s : SKINS.values()) {
			if (url.equals(Skins.PROFILE_URL + s.uuid)) {
				ASKED.merge("profile " + s.name, 1, Integer::sum);
				return json(profile(s));
			}
			if (url.endsWith("/texture/" + s.hash)) return new Https.Response(200, s.png);
			if (s.cape != null && url.endsWith("/texture/" + s.capeHash)) return new Https.Response(200, s.cape);
		}
		return null;
	}

	private static Https.Response json(String text) {
		return new Https.Response(200, text.getBytes(StandardCharsets.UTF_8));
	}

	/** In the session server's shape (2026-11-05: jeb_ for a wide skin with a cape, MHF_Alex for a slim one). */
	private static String profile(Skin s) {
		String skin = "\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + s.hash + "\""
				+ (s.slim ? ",\"metadata\":{\"model\":\"slim\"}" : "") + "}";
		String cape = s.cape == null ? "" : ",\"CAPE\":{\"url\":\"http://textures.minecraft.net/texture/" + s.capeHash + "\"}";
		String textures = "{\"timestamp\":1793910923705,\"profileId\":\"" + s.uuid + "\",\"profileName\":\"" + s.name
				+ "\",\"textures\":{" + skin + cape + "}}";
		String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
		return "{\"id\":\"" + s.uuid + "\",\"name\":\"" + s.name + "\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value
				+ "\"}],\"profileActions\":[]}";
	}

	private static byte[] png(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void save(BufferedImage image, String name) {
		try {
			File dir = new File("skins/textures");
			dir.mkdirs();
			ImageIO.write(image, "png", new File(dir, name + ".png"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static int hue(int part) {
		return Color.HSBtoRGB(part / 12.0F, 1.0F, 1.0F) & 0xFFFFFF;
	}

	/** What a pixel of a face holds; x and y within the face, its size w by h. */
	private interface Paint {
		int at(int face, int x, int y, int w, int h);
	}

	private static final int TOP = 0, BOTTOM = 1, RIGHT = 2, FRONT = 3, LEFT = 4, BACK = 5;

	private static void box(BufferedImage image, int u, int v, int w, int h, int d, Paint paint) {
		int[][] faces = {{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + d + w + d, v + d, w, h}};
		for (int f = 0; f < faces.length; f++) {
			int[] r = faces[f];
			for (int y = 0; y < r[3]; y++) {
				for (int x = 0; x < r[2]; x++) image.setRGB(r[0] + x, r[1] + y, paint.at(f, x, y, r[2], r[3]));
			}
		}
	}

	private static Paint loudFace(int part, boolean layer) {
		float[] shade = {1.0F, 0.45F, 0.8F, 1.0F, 0.65F, 0.55F};
		return (face, x, y, w, h) -> {
			if (layer && (x / 2 + y / 2) % 2 == 1) return 0;
			if (y == 0 && x == 0) return 0xFF000000;
			if (y == 0 && x == w - 1) return 0xFFFFFFFF;
			return 0xFF000000 | Color.HSBtoRGB(part / 12.0F, 1.0F, shade[face]) & 0xFFFFFF;
		};
	}

	private static BufferedImage loud(boolean slim, boolean modern) {
		BufferedImage image = new BufferedImage(64, modern ? 64 : 32, BufferedImage.TYPE_INT_ARGB);
		int arm = slim ? 3 : 4;
		box(image, 0, 0, 8, 8, 8, loudFace(HEAD, false));
		box(image, 32, 0, 8, 8, 8, loudFace(HAT, true));
		box(image, 16, 16, 8, 12, 4, loudFace(BODY, false));
		box(image, 40, 16, arm, 12, 4, loudFace(RIGHT_ARM, false));
		box(image, 0, 16, 4, 12, 4, loudFace(RIGHT_LEG, false));
		if (!modern) return image;
		box(image, 32, 48, arm, 12, 4, loudFace(LEFT_ARM, false));
		box(image, 16, 48, 4, 12, 4, loudFace(LEFT_LEG, false));
		box(image, 16, 32, 8, 12, 4, loudFace(JACKET, true));
		box(image, 40, 32, arm, 12, 4, loudFace(RIGHT_SLEEVE, true));
		box(image, 48, 48, arm, 12, 4, loudFace(LEFT_SLEEVE, true));
		box(image, 0, 32, 4, 12, 4, loudFace(RIGHT_TROUSER, true));
		box(image, 0, 48, 4, 12, 4, loudFace(LEFT_TROUSER, true));
		return image;
	}

	/** A layer face's hue in {@link #sieve} and {@link #lid}: top red, then yellow, green, cyan, blue, magenta. */
	private static int faceColour(int face) {
		return Color.HSBtoRGB(face / 6.0F, 1.0F, 1.0F) & 0xFFFFFF;
	}

	private static void layers(BufferedImage image, Paint paint) {
		box(image, 32, 0, 8, 8, 8, paint);
		box(image, 16, 32, 8, 12, 4, paint);
		box(image, 40, 32, 4, 12, 4, paint);
		box(image, 48, 48, 4, 12, 4, paint);
		box(image, 0, 32, 4, 12, 4, paint);
		box(image, 0, 48, 4, 12, 4, paint);
	}

	private static void plainBase(BufferedImage image, int rgb) {
		Paint base = (face, x, y, w, h) -> 0xFF000000 | rgb;
		box(image, 0, 0, 8, 8, 8, base);
		box(image, 16, 16, 8, 12, 4, base);
		box(image, 40, 16, 4, 12, 4, base);
		box(image, 32, 48, 4, 12, 4, base);
		box(image, 0, 16, 4, 12, 4, base);
		box(image, 16, 48, 4, 12, 4, base);
	}

	/**
	 * For looking through a layer: a pale base, and on the layers a hue to each face, a checkerboard of holes, and rows
	 * going down through solid, half, a quarter and an alpha below the cut-off.
	 */
	private static BufferedImage sieve() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		plainBase(image, 0xB8B8B8);
		int[] alpha = {255, 128, 64, 20};
		layers(image, (face, x, y, w, h) -> (x / 2 + y / 2) % 2 == 1 ? 0 : alpha[y * 4 / h] << 24 | faceColour(face));
		return image;
	}

	/** Only the layers' tops and bottoms, solid: from outside you see them, and past the open sides their undersides. */
	private static BufferedImage lid() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		plainBase(image, 0xB8B8B8);
		layers(image, (face, x, y, w, h) -> face == TOP || face == BOTTOM ? 0xFF000000 | faceColour(face) : 0);
		return image;
	}

	/** The sort of skin semi-transparent pixels matter to: a bubble helmet with a solid rim, a thin coat. */
	private static BufferedImage glass() {
		BufferedImage image = suit(false);
		box(image, 32, 0, 8, 8, 8, (face, x, y, w, h) -> face != BOTTOM && face != TOP && y == h - 1 ? 0xFFD0D8E0 : 96 << 24 | 0x60D0F0);
		Paint coat = (face, x, y, w, h) -> 128 << 24 | 0xC04040;
		box(image, 16, 32, 8, 12, 4, coat);
		box(image, 40, 32, 4, 12, 4, coat);
		box(image, 48, 48, 4, 12, 4, coat);
		image.setRGB(HIDDEN_X, HIDDEN_Y, HIDDEN_RGB);
		return image;
	}

	/** Painted with no transparency anywhere, as by a tool that knows no layers. */
	private static BufferedImage solid() {
		BufferedImage image = loud(false, true);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if (image.getRGB(x, y) >>> 24 == 0) image.setRGB(x, y, 0xFF000000 | hue(x % 12));
			}
		}
		return image;
	}

	private static BufferedImage cape() {
		BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		box(image, 0, 0, 10, 16, 1, (face, x, y, w, h) -> {
			if (y < 2) return 0xFFFFFFFF;
			return 0xFF000000 | (x < w / 2 ? 0x101010 : 0xE0E0E0);
		});
		return image;
	}

	/** A person, for how the layers read: a coat over a shirt, rolled sleeves, boots, a watch on the left wrist. */
	private static BufferedImage suit(boolean slim) {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Random noise = new Random(1773);
		int arm = slim ? 3 : 4;
		int skinTone = 0xC89878, hair = 0x4A3020, shirt = 0xD8D4C8, coat = 0x2F5560, jeans = 0x3A4A70, boot = 0x503828;
		box(image, 0, 0, 8, 8, 8, (face, x, y, w, h) -> {
			if (face == TOP || face == BACK && y < 6 || (face == LEFT || face == RIGHT) && y < 3) return tone(hair, noise);
			if (face == FRONT && y == 3 && (x == 1 || x == 6)) return 0xFFFFFFFF;
			if (face == FRONT && y == 3 && (x == 2 || x == 5)) return 0xFF306040;
			if (face == FRONT && y == 6 && x > 2 && x < 5) return tone(0x905848, noise);
			return tone(skinTone, noise);
		});
		box(image, 32, 0, 8, 8, 8, (face, x, y, w, h) -> {
			if (face == TOP) return tone(hair, noise);
			if (face == FRONT) return y < 2 || y == 2 && x % 3 != 1 ? tone(hair, noise) : 0;
			return y < 3 + x % 2 ? tone(hair, noise) : 0;
		});
		box(image, 16, 16, 8, 12, 4, (face, x, y, w, h) -> tone(face == FRONT && y < 2 && x > 2 && x < 5 ? skinTone : shirt, noise));
		box(image, 16, 32, 8, 12, 4, (face, x, y, w, h) -> {
			if (face == FRONT) return x < 2 || x > 5 ? tone(coat, noise) : 0;
			return tone(coat, noise);
		});
		Paint armBase = (face, x, y, w, h) -> tone(y >= 10 && face != TOP ? skinTone : shirt, noise);
		box(image, 40, 16, arm, 12, 4, armBase);
		box(image, 32, 48, arm, 12, 4, (face, x, y, w, h) -> y == 9 && face != TOP && face != BOTTOM ? tone(0xC8A030, noise) : armBase.at(face, x, y, w, h));
		Paint sleeve = (face, x, y, w, h) -> face == BOTTOM || y >= 8 ? 0 : tone(y == 7 ? 0x3F7080 : coat, noise);
		box(image, 40, 32, arm, 12, 4, sleeve);
		box(image, 48, 48, arm, 12, 4, sleeve);
		Paint legBase = (face, x, y, w, h) -> tone(y >= 9 || face == BOTTOM ? boot : jeans, noise);
		box(image, 0, 16, 4, 12, 4, legBase);
		box(image, 16, 48, 4, 12, 4, legBase);
		box(image, 0, 32, 4, 12, 4, (face, x, y, w, h) -> y == 8 && face != TOP && face != BOTTOM ? tone(0x8A6A48, noise) : 0);
		box(image, 0, 48, 4, 12, 4, (face, x, y, w, h) -> {
			if (y == 8 && face != TOP && face != BOTTOM) return tone(0x8A6A48, noise);
			return face == FRONT && y > 3 && y < 7 && x > 0 && x < 3 ? tone(0x7A4030, noise) : 0;
		});
		return image;
	}

	private static int tone(int rgb, Random noise) {
		int d = noise.nextInt(17) - 8;
		int r = clamp((rgb >> 16 & 0xFF) + d), g = clamp((rgb >> 8 & 0xFF) + d), b = clamp((rgb & 0xFF) + d);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
