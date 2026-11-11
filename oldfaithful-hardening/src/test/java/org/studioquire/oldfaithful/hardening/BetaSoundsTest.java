package org.studioquire.oldfaithful.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.studioquire.oldfaithful.hardening.assets.BetaSounds;
import org.studioquire.oldfaithful.hardening.assets.LegacyAssets;

class BetaSoundsTest {
	@TempDir
	Path tmp;

	private static final String MIRROR = "https://mirror.example/MinecraftResources/";
	private static final String SECOND = "https://second.example/";
	private static final byte[] BOW = "beta's own bow".getBytes(StandardCharsets.UTF_8);
	private static final byte[] DOOR = "beta's own door".getBytes(StandardCharsets.UTF_8);
	private static final byte[] BLAST = "beta's own blast".getBytes(StandardCharsets.UTF_8);

	private static BetaSounds.Pinned pin(String pool, String file, byte[] bytes, List<String> local, String... urls) {
		return new BetaSounds.Pinned(pool, file, bytes.length, BetaSounds.sha256(bytes), local, List.of(urls));
	}

	private static final List<BetaSounds.Pinned> PINS = List.of(
			pin("random.explode", "explode.ogg", BLAST, List.of("newsound/random/old_explode.ogg"), MIRROR + "explode.ogg"),
			pin("random.bow", "bow.ogg", BOW, List.of("newsound/random/bow.ogg"), MIRROR + "bow.ogg", SECOND + "bow.ogg"),
			pin("random.door_open", "door_open.ogg", DOOR, List.of("newsound/random/door_open.ogg"), MIRROR + "door_open.ogg"));

	/** The game's pool table, filled as SoundManager.loadSound does with the hardening's mixin in place. */
	private static final class Game {
		final Map<String, List<String>> pools = new HashMap<>();
		final BetaSounds beta;

		Game(BetaSounds beta) {
			this.beta = beta;
		}

		void load(String id) {
			if (beta.keepsOut(id)) return;
			pools.computeIfAbsent(BetaSounds.poolOf(id), p -> new ArrayList<>()).add(id);
		}

		/** What the mixin's install does, with the file standing for the game's Sound. */
		void install(String pool, File file) {
			BetaSounds.replace(pools, pool, BetaSounds.idFor(pool) + " <- " + file.getName());
		}

		void installAll() {
			beta.ready().forEach(this::install);
		}
	}

	private File resources() {
		return tmp.resolve("resources").toFile();
	}

	@Test
	void poolsAreNamedAsTheGameNamesThem() {
		assertEquals("random.explode", BetaSounds.poolOf("random/explode1.ogg"));
		assertEquals("random.explode", BetaSounds.poolOf("random/explode.ogg"));
		assertEquals("random.old_explode", BetaSounds.poolOf("random/old_explode.ogg"));
		assertEquals("random.bow", BetaSounds.poolOf("random/bow.ogg"));
		assertEquals("random.door_close", BetaSounds.poolOf("random/door_close.ogg"));
		assertEquals("mob.ghast.affectionate scream", BetaSounds.poolOf("mob/ghast/affectionate scream.ogg"));
		assertEquals("step.grass", BetaSounds.poolOf("step/grass4.ogg"));
		assertNull(BetaSounds.poolOf("nodot"));
		assertNull(BetaSounds.poolOf("123.ogg"));
		assertEquals("beta/random/door_open.ogg", BetaSounds.idFor("random.door_open"));
	}

	/** The real pins: the four pools beta plays differently, sources over HTTPS only, names that stay in their folder. */
	@Test
	void thePinsAreTheFourSoundsAndOnlyHttps() throws IOException {
		Set<String> pools = new TreeSet<>();
		for (BetaSounds.Pinned p : BetaSounds.PINNED) {
			pools.add(p.pool);
			assertEquals(64, p.sha256.length(), p.pool);
			LegacyAssets.resolveInside(tmp.toFile(), p.file); // throws for a name that would leave the cache folder
		}
		assertEquals(Set.of("random.bow", "random.door_close", "random.door_open", "random.explode"), pools);
		// Each pinned source is HTTPS: a fetch from anything else is refused without a request.
		FakeHttp http = new FakeHttp();
		BetaSounds beta = new BetaSounds(http, tmp.resolve("cache"),
				List.of(pin("random.bow", "bow.ogg", BOW, List.of(), "http://plain.example/bow.ogg")));
		beta.fetchMissing(resources(), () -> false, (pool, file) -> { });
		assertTrue(http.requests.isEmpty());
		assertTrue(beta.ready().isEmpty());
	}

	/** First start: the later recordings load, then beta's arrive and each pool becomes beta's alone, for good. */
	@Test
	void arrivingFilesReplaceTheLaterRecordings() throws IOException {
		FakeHttp http = new FakeHttp().on(MIRROR + "bow.ogg", 200, BOW).on(MIRROR + "door_open.ogg", 200, DOOR)
				.on(MIRROR + "explode.ogg", 200, BLAST);
		BetaSounds beta = new BetaSounds(http, tmp.resolve("cache"), PINS);
		Game game = new Game(beta);
		beta.findLocal(resources());
		for (String id : List.of("random/explode1.ogg", "random/explode2.ogg", "random/explode3.ogg", "random/explode4.ogg",
				"random/old_explode.ogg", "random/bow.ogg", "random/door_open.ogg", "random/door_close.ogg")) game.load(id);
		assertEquals(4, game.pools.get("random.explode").size());

		beta.fetchMissing(resources(), () -> false, game::install);
		assertTrue(beta.settled());
		assertEquals(List.of("beta/random/explode.ogg <- explode.ogg"), game.pools.get("random.explode"));
		assertEquals(List.of("beta/random/bow.ogg <- bow.ogg"), game.pools.get("random.bow"));
		assertEquals(List.of("beta/random/door_open.ogg <- door_open.ogg"), game.pools.get("random.door_open"));
		// A pool beta's files don't cover, and old_explode's own pool, which nothing plays, are left alone.
		assertEquals(List.of("random/door_close.ogg"), game.pools.get("random.door_close"));
		assertEquals(List.of("random/old_explode.ogg"), game.pools.get("random.old_explode"));

		// A later recording arriving afterwards (a launcher's file downloaded late) stays out.
		game.load("random/explode2.ogg");
		game.load("random/bow.ogg");
		assertEquals(1, game.pools.get("random.explode").size());
		assertEquals(1, game.pools.get("random.bow").size());
		assertTrue(Files.isRegularFile(tmp.resolve("cache/beta-sounds/bow.ogg")));
	}

	/** Later starts: beta's files from the cache before anything loads, with no request; a forced reload too. */
	@Test
	void cachedFilesAreInPlaceFromTheFirstLoad() {
		FakeHttp http = new FakeHttp().on(MIRROR + "bow.ogg", 200, BOW).on(MIRROR + "door_open.ogg", 200, DOOR)
				.on(MIRROR + "explode.ogg", 200, BLAST);
		new BetaSounds(http, tmp.resolve("cache"), PINS).fetchMissing(resources(), () -> false, (pool, file) -> { });
		http.requests.clear();

		BetaSounds beta = new BetaSounds(http, tmp.resolve("cache"), PINS);
		beta.findLocal(resources());
		assertTrue(beta.complete());
		for (int reload = 0; reload < 2; reload++) {
			Game game = new Game(beta); // the game forcing a reload starts from an empty sound manager
			for (String id : List.of("random/explode1.ogg", "random/explode4.ogg", "random/bow.ogg", "random/door_open.ogg")) game.load(id);
			game.installAll();
			assertEquals(List.of("beta/random/explode.ogg <- explode.ogg"), game.pools.get("random.explode"));
			assertEquals(List.of("beta/random/bow.ogg <- bow.ogg"), game.pools.get("random.bow"));
		}
		beta.fetchMissing(resources(), () -> false, (pool, file) -> { });
		assertTrue(http.requests.isEmpty(), "nothing fetched when all is here: " + http.requests);
	}

	/** The launcher's copy is used when it's beta's file (the explosion, as pre-1.6's old_explode.ogg), not otherwise. */
	@Test
	void theLaunchersCopyCountsOnlyIfItIsBetas() throws IOException {
		Path random = Files.createDirectories(tmp.resolve("resources/newsound/random"));
		Files.write(random.resolve("old_explode.ogg"), BLAST);
		Files.write(random.resolve("bow.ogg"), "a later bow, longer".getBytes(StandardCharsets.UTF_8));
		Files.write(random.resolve("door_open.ogg"), "beta's own dooR".getBytes(StandardCharsets.UTF_8)); // same size, other bytes
		FakeHttp http = new FakeHttp();
		BetaSounds beta = new BetaSounds(http, tmp.resolve("cache"), PINS);
		beta.findLocal(resources());
		assertEquals(Set.of("random.explode"), beta.ready().keySet());
		assertEquals(tmp.resolve("cache/beta-sounds/explode.ogg").toFile().getCanonicalFile(), beta.ready().get("random.explode"));
		assertTrue(beta.keepsOut("random/explode3.ogg"));
		assertFalse(beta.keepsOut("random/bow.ogg"));
	}

	/** Anything but the pinned bytes is refused and never written; a damaged cache file is replaced. */
	@Test
	void onlyThePinnedBytesAreUsed() throws IOException {
		byte[] tampered = "beta's own boW".getBytes(StandardCharsets.UTF_8); // same length
		FakeHttp http = new FakeHttp().on(MIRROR + "bow.ogg", 200, tampered).on(SECOND + "bow.ogg", 200, BOW)
				.on(MIRROR + "door_open.ogg", 200, "far too long for the door".getBytes(StandardCharsets.UTF_8))
				.on(MIRROR + "explode.ogg", 404, new byte[0]);
		BetaSounds beta = new BetaSounds(http, tmp.resolve("cache"), PINS);
		List<String> arrived = new ArrayList<>();
		beta.fetchMissing(resources(), () -> false, (pool, file) -> arrived.add(pool));
		assertEquals(List.of("random.bow"), arrived, "the bow from its second source, after the first's failed its hash");
		assertEquals(BetaSounds.sha256(BOW), BetaSounds.sha256(Files.readAllBytes(tmp.resolve("cache/beta-sounds/bow.ogg"))));
		assertFalse(Files.exists(tmp.resolve("cache/beta-sounds/door_open.ogg")));
		assertFalse(Files.exists(tmp.resolve("cache/beta-sounds/explode.ogg")));
		File[] leftovers = tmp.resolve("cache/beta-sounds").toFile().listFiles((d, n) -> n.endsWith(".tmp"));
		assertEquals(0, leftovers.length);

		// Damaged on disk: not taken at the next start, and fetched again.
		Files.write(tmp.resolve("cache/beta-sounds/bow.ogg"), tampered);
		http.on(MIRROR + "bow.ogg", 200, BOW);
		BetaSounds next = new BetaSounds(http, tmp.resolve("cache"), PINS);
		next.findLocal(resources());
		assertFalse(next.ready().containsKey("random.bow"));
		next.fetchMissing(resources(), () -> false, (pool, file) -> { });
		assertTrue(next.ready().containsKey("random.bow"));
		assertEquals(BetaSounds.sha256(BOW), BetaSounds.sha256(Files.readAllBytes(tmp.resolve("cache/beta-sounds/bow.ogg"))));
	}

	/** A host that doesn't answer is asked once, nothing throws, and the game keeps the later recordings. */
	@Test
	void anUnreachableHostIsAskedOnce() {
		FakeHttp http = new FakeHttp().unreachable(MIRROR);
		BetaSounds beta = new BetaSounds(http, tmp.resolve("cache"), PINS);
		Game game = new Game(beta);
		beta.findLocal(resources());
		game.load("random/bow.ogg");
		beta.fetchMissing(resources(), () -> false, game::install);
		assertTrue(beta.settled());
		assertTrue(beta.ready().isEmpty());
		// The mirror once, then the bow's second source; the doors and explosion don't try the mirror again.
		assertEquals(List.of(MIRROR + "explode.ogg", SECOND + "bow.ogg"), http.requests.stream().map(FakeHttp.Request::url).toList());
		assertEquals(List.of("random/bow.ogg"), game.pools.get("random.bow"));
	}
}
