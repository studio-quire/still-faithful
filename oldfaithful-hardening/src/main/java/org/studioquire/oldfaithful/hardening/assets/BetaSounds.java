package org.studioquire.oldfaithful.hardening.assets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * Beta's own recordings of four sounds, in place of the later ones launchers give today.
 *
 * <p>Launchers fill resources/ from Mojang's "pre-1.6" asset index, which holds the sounds as Mojang last had them
 * before 1.6, not as b1.7.3 had them (July 2011). Four that beta plays differ: {@code random.explode} is 1.2's
 * explode1-4 where beta had one explode.ogg, and {@code random.bow}, {@code random.door_open} and
 * {@code random.door_close} are later recordings under beta's names. Beta's explosion is still in that index, byte
 * for byte, as newsound/random/old_explode.ogg; beta's bow and doors are in no Mojang index, only in community
 * archives of Mojang's old resource bucket (two independent copies agreed byte for byte, 2026-11-11).
 *
 * <p>Each of beta's files is pinned here by size and SHA-256. It's taken from resources/ if the launcher's copy is
 * that file, otherwise fetched over HTTPS ({@link Https}, no redirects, size capped) and accepted only if it matches;
 * either way it's kept in our own cache and checked again at every start. Once a pool's file is here, the game's
 * pool holds it alone: later recordings are kept out as the game loads them ({@link #keepsOut}), and a pool that
 * already holds them is replaced ({@link #replace}). Until then, or if it never comes, the later recording plays, as
 * it did before.
 */
public final class BetaSounds {
	/** Modification Station's copy of the bucket beta's game downloaded its sounds from (s3.amazonaws.com/MinecraftResources). */
	public static final String ARCHIVE = "https://mcresources.modification-station.net/MinecraftResources/";

	public static final class Pinned {
		/** The game's name for the pool, as {@code SoundEntry} makes it from a file's path. */
		public final String pool;
		/** Its name in our cache: beta's own file name. */
		public final String file;
		public final long size;
		public final String sha256;
		/** Paths under resources/ where a launcher may already have put these exact bytes. */
		final List<String> local;
		/** Where to fetch it, in order; HTTPS only. */
		final List<String> urls;

		public Pinned(String pool, String file, long size, String sha256, List<String> local, List<String> urls) {
			this.pool = pool;
			this.file = file;
			this.size = size;
			this.sha256 = sha256;
			this.local = local;
			this.urls = urls;
		}
	}

	public static final List<Pinned> PINNED = Collections.unmodifiableList(Arrays.asList(
			new Pinned("random.explode", "explode.ogg", 12808, "5d996493c8829e5126cc25e4c16f6c06f1a0cff88f79abdff213e7179a111481",
					Arrays.asList("newsound/random/old_explode.ogg", "newsound/random/explode.ogg"),
					// Mojang's own object (SHA-1 9e22b5ae...), the one the pre-1.6 index lists as old_explode.ogg.
					Arrays.asList(LegacyAssets.OBJECT_URL + "9e/9e22b5aeec31de99410b682cc161a6096a1cd00a", ARCHIVE + "newsound/random/explode.ogg")),
			new Pinned("random.bow", "bow.ogg", 8589, "19ed7cc7a5af71dffe247df4024b51ad305f118780e1685783778fdf5b948b14",
					Arrays.asList("newsound/random/bow.ogg"), Arrays.asList(ARCHIVE + "newsound/random/bow.ogg")),
			new Pinned("random.door_open", "door_open.ogg", 9987, "98f55537af8c616f5f9de4472596e9f972b776adedcd59dbe660c6d4aaedb2c0",
					Arrays.asList("newsound/random/door_open.ogg"), Arrays.asList(ARCHIVE + "newsound/random/door_open.ogg")),
			new Pinned("random.door_close", "door_close.ogg", 9796, "8bff296afc952e4c66a15c5b3693dec0f26365a1a40396f262e7f4326446588b",
					Arrays.asList("newsound/random/door_close.ogg"), Arrays.asList(ARCHIVE + "newsound/random/door_close.ogg"))));

	/** The game's sound manager, made one by a mixin: puts a file in a pool as the pool's only sound. */
	public interface Pools {
		void oldfaithful$useBeta(String pool, String id, File file) throws IOException;
	}

	/** Told of each of beta's files as it becomes ready. */
	public interface Arrived {
		void ready(String pool, File file);
	}

	private final Https.Client http;
	private final Path dir;
	private final List<Pinned> pinned;
	/** Pool name to beta's file for it, once checked. */
	private final Map<String, File> ready = new ConcurrentHashMap<String, File>();
	private volatile boolean settled;

	public BetaSounds(Https.Client http, Path cacheDir) {
		this(http, cacheDir, PINNED);
	}

	public BetaSounds(Https.Client http, Path cacheDir, List<Pinned> pinned) {
		this.http = http;
		this.dir = cacheDir.resolve("beta-sounds");
		this.pinned = pinned;
	}

	/** Beta's files already on this machine, in our cache or as the launcher's copy. No network. Never throws. */
	public void findLocal(File resourcesDir) {
		for (Pinned p : pinned) {
			if (!ready.containsKey(p.pool)) findLocal(p, resourcesDir);
		}
	}

	private boolean findLocal(Pinned p, File resourcesDir) {
		try {
			File cached = cacheFile(p);
			if (matches(p, read(cached, p.size))) {
				ready.put(p.pool, cached);
				return true;
			}
			for (String name : p.local) {
				byte[] bytes = read(LegacyAssets.resolveInside(resourcesDir, name), p.size);
				if (matches(p, bytes)) {
					ready.put(p.pool, store(p, bytes));
					return true;
				}
			}
		} catch (IOException e) {
			Log.warn("Beta's " + p.file + " not read from disk: " + e);
		}
		return false;
	}

	/**
	 * What {@link #findLocal} didn't find: looked for on disk again (a launcher or our own download may have put it
	 * there since), then fetched, telling {@code arrived} about each one. A host that doesn't answer isn't asked again
	 * this run. Never throws: anything missing waits for the next start.
	 */
	public void fetchMissing(File resourcesDir, LegacyAssets.Cancellation cancellation, Arrived arrived) {
		Set<String> unreachable = new HashSet<String>();
		try {
			for (Pinned p : pinned) {
				if (cancellation.cancelled()) return;
				if (ready.containsKey(p.pool)) continue;
				if (findLocal(p, resourcesDir)) {
					arrived.ready(p.pool, ready.get(p.pool));
					continue;
				}
				for (String url : p.urls) {
					String host = new URL(url).getHost();
					if (unreachable.contains(host) || cancellation.cancelled()) continue;
					try {
						File file = store(p, fetch(p, url));
						ready.put(p.pool, file);
						Log.info("Beta's own " + p.file + " fetched from " + host + " and checked");
						arrived.ready(p.pool, file);
						break;
					} catch (Refused e) {
						Log.warn("Beta's " + p.file + " from " + host + " refused: " + e.getMessage());
					} catch (IOException e) {
						unreachable.add(host);
						Log.warn("Beta's " + p.file + " not fetched from " + host + ": " + e);
					}
				}
			}
		} catch (IOException e) {
			Log.warn("Beta's sounds not fetched: " + e);
		} finally {
			settled = true;
		}
	}

	/** For a start that fetches nothing: what's on disk is all there is. */
	public void settle() {
		settled = true;
	}

	/** True once this run has found or fetched all it's going to. */
	public boolean settled() {
		return settled;
	}

	public boolean complete() {
		return ready.size() == pinned.size();
	}

	/** The pools whose beta file is here, and the file. */
	public Map<String, File> ready() {
		return Collections.unmodifiableMap(ready);
	}

	/**
	 * True for a sound the game is about to add to a pool whose beta file is here: a later recording (or any other
	 * file) that stays out, so the pool holds beta's alone. {@code id} is the path under sound/ or newsound/.
	 */
	public boolean keepsOut(String id) {
		if (ready.isEmpty()) return false;
		String pool = poolOf(id);
		return pool != null && ready.containsKey(pool);
	}

	/** Puts every pool whose file is here into the game's sound manager. */
	public void installAll(Object soundManager) throws IOException {
		for (Map.Entry<String, File> e : ready.entrySet()) install(soundManager, e.getKey(), e.getValue());
	}

	public static void install(Object soundManager, String pool, File file) throws IOException {
		((Pools) soundManager).oldfaithful$useBeta(pool, idFor(pool), file);
	}

	/**
	 * Its own name for paulscode, which keeps each decoded sound by name for the session: under the later
	 * recording's name ("random/bow.ogg"), a bow already heard would go on playing the later one. Ends in .ogg, which
	 * picks the decoder.
	 */
	public static String idFor(String pool) {
		return "beta/" + pool.replace('.', '/') + ".ogg";
	}

	/**
	 * The pool's list becomes one holding {@code sound} alone. A new list rather than the old one emptied: the game
	 * thread picks from a pool at random by its size, and an emptied list would be seen empty for a moment.
	 */
	public static <S> void replace(Map<String, List<S>> pools, String pool, S sound) {
		List<S> only = new ArrayList<S>(1);
		only.add(sound);
		pools.put(pool, only);
	}

	/**
	 * The pool a file joins, named as {@code SoundEntry.loadStatic} names it: the path to its first dot, trailing
	 * digits off, slashes to dots. Null where the game itself would fail to name it.
	 */
	public static String poolOf(String id) {
		int dot = id.indexOf('.');
		if (dot < 0) return null;
		String name = id.substring(0, dot);
		while (!name.isEmpty() && Character.isDigit(name.charAt(name.length() - 1))) name = name.substring(0, name.length() - 1);
		return name.isEmpty() ? null : name.replace('/', '.');
	}

	/** A response that came but isn't beta's file: the host is up, so the next file may still come from it. */
	private static final class Refused extends IOException {
		Refused(String message) {
			super(message);
		}
	}

	private byte[] fetch(Pinned p, String url) throws IOException {
		if (!url.startsWith("https://")) throw new Refused("not an HTTPS address");
		// Exactly the pinned size at most: anything bigger stops at the cap.
		Https.Response response = http.request("GET", url, null, null, (int) p.size);
		if (response.status != 200) throw new Refused("HTTP " + response.status);
		if (!matches(p, response.body)) throw new Refused("failed its hash check");
		return response.body;
	}

	private File cacheFile(Pinned p) throws IOException {
		return LegacyAssets.resolveInside(dir.toFile(), p.file);
	}

	/** Written beside its place and moved there, so a half-written file is never taken for it. */
	private File store(Pinned p, byte[] bytes) throws IOException {
		File target = cacheFile(p);
		Files.createDirectories(dir);
		File tmp = File.createTempFile("beta", ".tmp", dir.toFile());
		try {
			OutputStream out = new FileOutputStream(tmp);
			try {
				out.write(bytes);
			} finally {
				out.close();
			}
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			if (tmp.exists() && !tmp.delete()) tmp.deleteOnExit();
		}
		return target;
	}

	/** The file's bytes if it's a file of exactly {@code size} bytes, else null. */
	private static byte[] read(File file, long size) throws IOException {
		if (!file.isFile() || file.length() != size) return null;
		return Files.readAllBytes(file.toPath());
	}

	static boolean matches(Pinned p, byte[] bytes) {
		return bytes != null && bytes.length == p.size && p.sha256.equals(sha256(bytes));
	}

	public static String sha256(byte[] bytes) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder sb = new StringBuilder(64);
			for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
