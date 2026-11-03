package org.studioquire.oldfaithful.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.studioquire.oldfaithful.hardening.assets.LegacyAssets;

class LegacyAssetsTest {
	@TempDir
	Path tmp;

	@Test
	void resolvesOnlyInsideTheFolder() throws IOException {
		File dir = tmp.resolve("resources").toFile();
		assertTrue(dir.mkdirs());
		assertEquals(new File(dir, "sound/step/grass1.ogg").getCanonicalFile(),
				LegacyAssets.resolveInside(dir, "sound/step/grass1.ogg"));
		assertEquals(new File(dir, "newsound/mob/ghast/affectionate scream.ogg").getCanonicalFile(),
				LegacyAssets.resolveInside(dir, "newsound/mob/ghast/affectionate scream.ogg"));

		for (String bad : new String[] {
				"../evil.ogg", "sound/../../evil.ogg", "music/../../../Startup/x.bat", "/etc/passwd", "C:/Windows/x",
				"C:evil", "sound\\..\\..\\evil", "sound//x.ogg", "sound/./x.ogg", "sound/x.ogg ", "sound/ x.ogg",
				"sound/x.", ".hidden", "", "sound/", "sound/con.ogg", "sound/NUL", "sound/lpt1.txt/x", "sound/x:stream",
		}) {
			assertThrows(IOException.class, () -> LegacyAssets.resolveInside(dir, bad), bad);
		}
	}

	@Test
	void refusesAPathThatLeavesThroughALink() throws IOException {
		Path dir = Files.createDirectories(tmp.resolve("resources"));
		Path outside = Files.createDirectories(tmp.resolve("outside"));
		try {
			Files.createSymbolicLink(dir.resolve("sound"), outside);
		} catch (IOException | UnsupportedOperationException e) {
			return; // Creating links needs extra rights on Windows; the canonical-path check is the same code.
		}
		assertThrows(IOException.class, () -> LegacyAssets.resolveInside(dir.toFile(), "sound/x.ogg"));
	}

	@Test
	void indexKeepsOnlyWhatTheGameUses() throws IOException {
		String sha = "0123456789abcdef0123456789abcdef01234567";
		String json = "{\"map_to_resources\":true,\"objects\":{"
				+ "\"sound/a.ogg\":{\"hash\":\"" + sha + "\",\"size\":3},"
				+ "\"icons/icon_16x16.png\":{\"hash\":\"" + sha + "\",\"size\":3},"
				+ "\"pack.mcmeta\":{\"hash\":\"" + sha + "\",\"size\":3}}}";
		var index = LegacyAssets.parseIndex(json.getBytes(StandardCharsets.UTF_8));
		assertEquals(1, index.size());
		assertTrue(index.containsKey("sound/a.ogg"));
	}

	@Test
	void indexWithAnEscapingNameIsRejectedWhole() {
		String sha = "0123456789abcdef0123456789abcdef01234567";
		String json = "{\"objects\":{\"sound/../../x.ogg\":{\"hash\":\"" + sha + "\",\"size\":3}}}";
		assertThrows(IOException.class, () -> LegacyAssets.parseIndex(json.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void indexMustMatchThePinnedHash() {
		FakeHttp http = new FakeHttp().on(LegacyAssets.INDEX_URL, 200, "{\"objects\":{}}");
		LegacyAssets assets = new LegacyAssets(http, tmp.resolve("cache"));
		IOException e = assertThrows(IOException.class, () -> assets.sync(tmp.resolve("resources").toFile(), () -> false));
		assertTrue(e.getMessage().contains("hash"));
		assertFalse(Files.exists(tmp.resolve("cache/pre-1.6.json")));
	}

	@Test
	void objectsAreVerifiedBeforeTheyAreWritten() throws IOException {
		byte[] good = "good sound".getBytes(StandardCharsets.UTF_8);
		String goodHash = LegacyAssets.sha1(good);
		byte[] tampered = "evil sound".getBytes(StandardCharsets.UTF_8); // same length, different bytes
		String otherHash = LegacyAssets.sha1("other file".getBytes(StandardCharsets.UTF_8));

		String json = "{\"objects\":{"
				+ "\"sound/good.ogg\":{\"hash\":\"" + goodHash + "\",\"size\":" + good.length + "},"
				+ "\"sound/bad.ogg\":{\"hash\":\"" + otherHash + "\",\"size\":" + tampered.length + "}}}";
		Path cache = Files.createDirectories(tmp.resolve("cache"));
		File resources = tmp.resolve("resources").toFile();
		FakeHttp http = new FakeHttp()
				.on(LegacyAssets.OBJECT_URL + goodHash.substring(0, 2) + "/" + goodHash, 200, good)
				.on(LegacyAssets.OBJECT_URL + otherHash.substring(0, 2) + "/" + otherHash, 200, tampered);
		var index = LegacyAssets.parseIndex(json.getBytes(StandardCharsets.UTF_8));
		int downloaded = new LegacyAssets(http, cache).sync(index, resources, () -> false);

		assertEquals(1, downloaded);
		assertTrue(new File(resources, "sound/good.ogg").isFile());
		assertFalse(new File(resources, "sound/bad.ogg").exists(), "a file failing its hash must not be written");
		File[] leftovers = new File(resources, "sound").listFiles((d, n) -> n.endsWith(".tmp"));
		assertEquals(0, leftovers.length);

		// Present with the right size: not fetched again (only the still-missing bad file is asked for).
		http.requests.clear();
		assertEquals(0, new LegacyAssets(http, cache).sync(index, resources, () -> false));
		assertEquals(1, http.requests.size());
		assertTrue(http.requests.get(0).url().endsWith(otherHash));
	}

	/** Each file is reported as it lands (the game loads it then); after three failures in a row the rest wait. */
	@Test
	void reportsEachDownloadAndGivesUpWhenMojangIsDown() throws IOException {
		StringBuilder json = new StringBuilder("{\"objects\":{");
		FakeHttp http = new FakeHttp();
		for (int i = 0; i < 10; i++) {
			byte[] data = ("sound " + i).getBytes(StandardCharsets.UTF_8);
			String hash = LegacyAssets.sha1(data);
			if (i > 0) json.append(',');
			json.append("\"sound/s").append(i).append(".ogg\":{\"hash\":\"").append(hash).append("\",\"size\":").append(data.length).append('}');
			// The first two answer; everything after is unreachable (no response registered).
			if (i < 2) http.on(LegacyAssets.OBJECT_URL + hash.substring(0, 2) + "/" + hash, 200, data);
		}
		json.append("}}");
		var index = LegacyAssets.parseIndex(json.toString().getBytes(StandardCharsets.UTF_8));
		java.util.List<String> reported = new java.util.ArrayList<>();
		int downloaded = new LegacyAssets(http, Files.createDirectories(tmp.resolve("cache2")))
				.sync(index, tmp.resolve("resources2").toFile(), () -> false, (path, file) -> {
					assertTrue(file.isFile(), "reported once in place");
					reported.add(path);
				});
		assertEquals(2, downloaded);
		assertEquals(java.util.List.of("sound/s0.ogg", "sound/s1.ogg"), reported);
		assertEquals(2 + LegacyAssets.GIVE_UP_AFTER, http.requests.size(), "stopped after three failures in a row");
	}
}
