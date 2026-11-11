package org.studioquire.oldfaithful.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import net.minecraft.client.resource.ResourceDownloadThread;
import net.minecraft.client.texture.ImageDownload;
import net.minecraft.client.texture.SkinImageProcessor;

import org.studioquire.oldfaithful.hardening.Services;
import org.studioquire.oldfaithful.hardening.assets.BetaSounds;
import org.studioquire.oldfaithful.hardening.assets.LegacyAssets;
import org.studioquire.oldfaithful.hardening.session.MojangSession;
import org.studioquire.oldfaithful.hardening.skins.Skins;
import org.studioquire.oldfaithful.hardening.util.Https;

/**
 * Client checks that go through the game's own (patched) classes and Mojang's real services: no window is needed
 * for any of them. Kept apart from {@link SelfTest} so the server never loads client classes.
 */
final class ClientChecks {
	private final SelfTest t;

	ClientChecks(SelfTest t) {
		this.t = t;
	}

	void run() {
		session();
		skins();
		sounds();
	}

	private void session() {
		String offline = MojangSession.join(Https.REAL, "OldFaithfulTest", "-", "1a2b3c");
		t.check("join: an offline session is refused without a request", offline.contains("offline"));

		String answer = MojangSession.join(Https.REAL, "OldFaithfulTest",
				"token:not-a-real-token:00000000000000000000000000000000", "1a2b3c");
		t.check("join: session server reached over HTTPS and refuses a fake token (" + answer + ")",
				answer.startsWith("The session server refused the login"));
	}

	/** The game's own download object, as the renderer creates it, for a player with a skin and a cape. */
	private void skins() {
		BufferedImage skin = await(new ImageDownload(Skins.LEGACY_SKINS + "jeb_.png", new SkinImageProcessor()));
		t.check("skin: fetched from Mojang over HTTPS and processed by the game",
				skin != null && skin.getWidth() == 64 && skin.getHeight() == 32);
		BufferedImage cape = await(new ImageDownload(Skins.LEGACY_CAPES + "jeb_.png", null));
		t.check("cape: fetched from Mojang over HTTPS", cape != null);
	}

	private static BufferedImage await(ImageDownload download) {
		long deadline = System.currentTimeMillis() + 30_000;
		while (download.image == null && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				return null;
			}
		}
		return download.image;
	}

	/**
	 * Runs the game's resource download thread (patched) against a folder kept between runs: the first run fetches
	 * every sound from Mojang, later runs find them all present and download nothing, as with a launcher that already
	 * provides them. Without a game instance, registering each sound fails harmlessly ("Failed to add" lines).
	 */
	private void sounds() {
		String dir = System.getProperty("oldfaithful.selftest.soundsGameDir");
		if (dir == null) {
			t.fail("sounds", new IllegalStateException("oldfaithful.selftest.soundsGameDir not set"));
			return;
		}
		try {
			File gameDir = new File(dir);
			// What Prism puts at the top of resources/: skipped without a "Failed to add" line (the task checks).
			File resources = new File(gameDir, "resources");
			resources.mkdirs();
			for (String name : new String[] {"pack.mcmeta", "READ_ME_I_AM_VERY_IMPORTANT"}) {
				Files.write(new File(resources, name).toPath(), new byte[] {'{', '}'});
			}
			new ResourceDownloadThread(gameDir, null).run();

			byte[] index = Files.readAllBytes(Services.cacheDir().resolve("pre-1.6.json"));
			t.check("sounds: asset index cached and intact", LegacyAssets.INDEX_SHA1.equals(LegacyAssets.sha1(index)));
			Map<String, LegacyAssets.Entry> entries = LegacyAssets.parseIndex(index);
			int missing = 0;
			for (Map.Entry<String, LegacyAssets.Entry> e : entries.entrySet()) {
				File f = new File(new File(gameDir, "resources"), e.getKey());
				if (!f.isFile() || f.length() != e.getValue().size) missing++;
			}
			t.check("sounds: all " + entries.size() + " present with the right sizes (missing " + missing + ")",
					missing == 0 && entries.size() > 300);
			betaSounds(gameDir);
		} catch (Throwable e) {
			t.fail("sounds", e);
		}
	}

	/**
	 * Beta's own bow, door and explosion sounds, fetched by the same run into this run's fresh cache (the bow and doors
	 * from Modification Station, so over the network every time), each checked against its pin; then copied into the
	 * kept folder's cache, which the game tests copy into their game folders, so they start with them and ask nobody.
	 */
	private void betaSounds(File gameDir) throws java.io.IOException {
		Map<String, File> ready = Services.BETA_SOUNDS.ready();
		File keep = new File(gameDir, "cache/purefaithful-hardening/beta-sounds");
		keep.mkdirs();
		for (BetaSounds.Pinned p : BetaSounds.PINNED) {
			File file = ready.get(p.pool);
			boolean ok = file != null && p.sha256.equals(BetaSounds.sha256(Files.readAllBytes(file.toPath())));
			t.check("sounds: beta's own " + p.file + " fetched or found, and the pinned file", ok);
			if (ok) Files.copy(file.toPath(), new File(keep, p.file).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
