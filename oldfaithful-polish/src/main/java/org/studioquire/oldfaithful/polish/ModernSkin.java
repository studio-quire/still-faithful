package org.studioquire.oldfaithful.polish;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.texture.ImageDownload;
import net.minecraft.client.texture.ImageProcessor;
import net.minecraft.client.texture.SkinImageProcessor;
import net.minecraft.client.texture.TextureManager;
import org.studioquire.oldfaithful.hardening.util.Json;
import org.studioquire.oldfaithful.polish.mixin.skins.TextureManagerAccessor;

/**
 * Skins as later versions draw them (docs/still-faithful.md > Modern skins). A 64x64 skin comes out of {@link #processor}
 * as one of these, whole; anything else goes to beta's own processor, so a classic skin is beta's to the pixel and takes
 * beta's model. The image is the mark: what's drawn with it is decided by what the texture manager holds for the
 * player at that moment ({@link #of}), so a player waiting for a download, or whose skin was released, stays on
 * beta's model with beta's char.png.
 *
 * Slim arms are in the profile's textures metadata. {@link #profileRead} takes the flag from the response Pure
 * Faithful's hardening has already fetched (a mixin at Skins.parseProfile), so there's no request of our own: its
 * HTTPS, its size limits and its one lookup per name are all there is. A second lookup would double the calls to the
 * session server, which limits them; reading the arm's unused pixels only guesses, and guesses wrong for a skin
 * painted there.
 */
public final class ModernSkin extends BufferedImage {
	/** Lower-case names whose profile said slim: only what's been looked up, so as bounded as hardening's own cache. */
	private static final Set<String> SLIM = ConcurrentHashMap.newKeySet();

	private static final int[] HEAD = {0, 0, 32, 16}, BODY = {0, 16, 64, 32}, LOWER_LIMBS = {16, 48, 48, 64};

	public final boolean slim;

	private ModernSkin(BufferedImage image, boolean slim) {
		super(64, 64, TYPE_INT_ARGB);
		this.slim = slim;
		// The file's pixels as they are, not drawn over transparency as beta does: that would turn a pixel that's
		// transparent in the file black, and later versions make it opaque in the colour it has there.
		int[] data = ((DataBufferInt) getRaster().getDataBuffer()).getData();
		image.getRGB(0, 0, 64, 64, data, 0, 64);
		opaque(data, HEAD);
		opaque(data, BODY);
		opaque(data, LOWER_LIMBS);
	}

	private static void opaque(int[] data, int[] r) {
		for (int y = r[1]; y < r[3]; y++) {
			for (int x = r[0]; x < r[2]; x++) data[x + y * 64] |= 0xFF000000;
		}
	}

	/** For the skin at this URL, in place of beta's processor. */
	public static ImageProcessor processor(String url) {
		return image -> image != null && image.getWidth() == 64 && image.getHeight() == 64
				? new ModernSkin(image, SLIM.contains(name(url)))
				: new SkinImageProcessor().process(image);
	}

	/** What the texture manager holds for this skin URL, if it's a modern skin. */
	public static ModernSkin of(TextureManager textures, String url) {
		if (url == null || textures == null) return null;
		ImageDownload download = ((TextureManagerAccessor) textures).oldfaithful$downloads().get(url);
		return download != null && download.image instanceof ModernSkin ? (ModernSkin) download.image : null;
	}

	private static String name(String url) {
		return url.substring(url.lastIndexOf('/') + 1).replace(".png", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * A session-server profile, just read by hardening. Anything unexpected in it is simply no slim flag: this mustn't
	 * cost anyone their skin.
	 */
	public static void profileRead(String json) {
		try {
			Map<String, Object> profile = Json.object(Json.parse(json));
			String name = Json.string(profile, "name");
			for (Object p : (List<?>) profile.get("properties")) {
				Map<String, Object> property = Json.object(p);
				if (!"textures".equals(Json.string(property, "name"))) continue;
				String decoded = new String(Base64.getDecoder().decode(Json.string(property, "value")), StandardCharsets.UTF_8);
				Map<String, Object> skin = Json.object(Json.object(Json.object(Json.parse(decoded)).get("textures")).get("SKIN"));
				Object metadata = skin.get("metadata");
				if (name != null && metadata != null && "slim".equals(Json.string(Json.object(metadata), "model"))) {
					SLIM.add(name.toLowerCase(Locale.ROOT));
				}
			}
		} catch (RuntimeException e) {
			// A wide arm, then.
		}
	}
}
