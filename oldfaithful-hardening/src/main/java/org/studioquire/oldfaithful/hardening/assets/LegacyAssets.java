package org.studioquire.oldfaithful.hardening.assets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.studioquire.oldfaithful.hardening.util.Https;
import org.studioquire.oldfaithful.hardening.util.Json;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * Replaces the game's resource downloader. The original fetched an XML listing over plain HTTP from a dead S3
 * bucket (or, with some fix mods, a third-party mirror), parsed it with external entities enabled (XXE), and wrote
 * each listed key to {@code resources/<key>} without checking where that path led (path traversal). Anyone able to
 * alter that response could write any file the player can.
 *
 * <p>Here, the list of sounds is Mojang's own legacy asset index, fetched over HTTPS and accepted only if its SHA-1
 * matches the pinned one, so it can't be altered. Every file is fetched from Mojang over HTTPS and accepted only if
 * it matches the index's hash and size, and every path must stay inside the resources folder. Files the launcher
 * already put there are left alone; with nothing missing, nothing is downloaded.
 */
public final class LegacyAssets {
	/** Mojang's "pre-1.6" asset index, the one b1.7.3's launcher profile names. */
	public static final String INDEX_URL =
			"https://piston-meta.mojang.com/v1/packages/3d8e55480977e32acd9844e545177e69a52f594b/pre-1.6.json";
	public static final String INDEX_SHA1 = "3d8e55480977e32acd9844e545177e69a52f594b";
	public static final String OBJECT_URL = "https://resources.download.minecraft.net/";

	private static final int MAX_INDEX_BYTES = 1 << 20;
	private static final long MAX_OBJECT_BYTES = 16 << 20;
	/** The folders the game loads sounds and music from; the index also lists files later versions use. */
	private static final String[] GAME_FOLDERS = {"sound/", "newsound/", "music/", "newmusic/", "streaming/"};
	/**
	 * Conservative on purpose: every name the game uses fits it (some have spaces), and nothing that could escape a
	 * folder does: no "." or ".." segments, no drive letters or backslashes, no leading or trailing dots or spaces.
	 */
	private static final String SEGMENT = "[A-Za-z0-9_\\-](?:[A-Za-z0-9_\\- .]*[A-Za-z0-9_\\-])?";
	private static final Pattern SAFE_NAME = Pattern.compile(SEGMENT + "(?:/" + SEGMENT + ")*");
	/** Windows device names open a device rather than a file, whatever the extension or folder. */
	private static final Pattern DEVICE_NAME =
			Pattern.compile("(?i)(?:^|/)(?:con|prn|aux|nul|com[0-9]|lpt[0-9])(?:\\.[^/]*)?(?:/|$)");
	private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

	public interface Cancellation {
		boolean cancelled();
	}

	public static final class Entry {
		public final String hash;
		public final long size;

		Entry(String hash, long size) {
			this.hash = hash;
			this.size = size;
		}
	}

	private final Https.Client http;
	private final Path cacheDir;

	public LegacyAssets(Https.Client http, Path cacheDir) {
		this.http = http;
		this.cacheDir = cacheDir;
	}

	/**
	 * Makes sure every sound the game uses is present in {@code resourcesDir}, downloading what's missing.
	 *
	 * @return how many files were downloaded
	 */
	public int sync(File resourcesDir, Cancellation cancellation) throws IOException {
		return sync(parseIndex(loadIndex()), resourcesDir, cancellation, (path, file) -> { });
	}

	/** As {@link #sync(File, Cancellation)}, telling {@code downloaded} about each file as it arrives. */
	public int sync(File resourcesDir, Cancellation cancellation, Downloaded downloaded) throws IOException {
		return sync(parseIndex(loadIndex()), resourcesDir, cancellation, downloaded);
	}

	public int sync(Map<String, Entry> index, File resourcesDir, Cancellation cancellation) throws IOException {
		return sync(index, resourcesDir, cancellation, (path, file) -> { });
	}

	/** Called for each file downloaded, as soon as it's in place, so the game can load it at once. */
	public interface Downloaded {
		void file(String path, File file);
	}

	/** Failures in a row after which the rest is left for next time: Mojang isn't answering. */
	public static final int GIVE_UP_AFTER = 3;

	/** As {@link #sync(File, Cancellation, Downloaded)}, for an index already loaded and checked. */
	public int sync(Map<String, Entry> index, File resourcesDir, Cancellation cancellation, Downloaded listener) throws IOException {
		int downloaded = 0;
		int failed = 0;
		int inARow = 0;
		for (Map.Entry<String, Entry> e : index.entrySet()) {
			if (cancellation.cancelled()) break;
			File target = resolveInside(resourcesDir, e.getKey());
			if (target.isFile() && target.length() == e.getValue().size) continue; // same test the game used

			try {
				fetchObject(e.getValue(), target);
				downloaded++;
				inARow = 0;
				listener.file(e.getKey(), target);
			} catch (IOException ex) {
				failed++;
				Log.warn("Could not fetch sound " + e.getKey() + ": " + ex.getMessage());
				if (++inARow >= GIVE_UP_AFTER) {
					Log.warn("Sounds: " + GIVE_UP_AFTER + " downloads failed in a row; the rest wait for the next start");
					break;
				}
			}
		}
		if (downloaded > 0 || failed > 0) Log.info("Sounds: downloaded " + downloaded + ", failed " + failed);
		return downloaded;
	}

	/** The index, from the local cache if it's intact, else from Mojang. Either way its SHA-1 must match. */
	byte[] loadIndex() throws IOException {
		Path cached = cacheDir.resolve("pre-1.6.json");
		if (Files.isRegularFile(cached)) {
			byte[] bytes = Files.readAllBytes(cached);
			if (INDEX_SHA1.equals(sha1(bytes))) return bytes;
		}

		Https.Response response = http.request("GET", INDEX_URL, null, null, MAX_INDEX_BYTES);
		if (response.status != 200) throw new IOException("Asset index: HTTP " + response.status);
		if (!INDEX_SHA1.equals(sha1(response.body))) throw new IOException("Asset index failed its hash check");

		Files.createDirectories(cacheDir);
		Path tmp = Files.createTempFile(cacheDir, "index", ".tmp");
		Files.write(tmp, response.body);
		Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
		return response.body;
	}

	/** The index entries the game uses, rejecting any entry that isn't a plain relative path with a SHA-1. */
	public static Map<String, Entry> parseIndex(byte[] json) throws IOException {
		Map<String, Entry> result = new TreeMap<String, Entry>();
		Map<String, Object> objects;
		try {
			objects = Json.object(Json.object(Json.parse(new String(json, StandardCharsets.UTF_8))).get("objects"));
		} catch (IllegalArgumentException e) {
			throw new IOException("Asset index is malformed", e);
		}

		for (Map.Entry<String, Object> e : objects.entrySet()) {
			String name = e.getKey();
			if (!usedByGame(name)) continue;
			Map<String, Object> obj = Json.object(e.getValue());
			String hash = Json.string(obj, "hash");
			Object size = obj.get("size");
			if (!isSafeName(name) || hash == null || !SHA1.matcher(hash).matches() || !(size instanceof Double)
					|| (Double) size < 0 || (Double) size > MAX_OBJECT_BYTES) {
				throw new IOException("Asset index has an unexpected entry: " + name);
			}
			result.put(name, new Entry(hash, ((Double) size).longValue()));
		}
		return result;
	}

	static boolean usedByGame(String name) {
		for (String folder : GAME_FOLDERS) {
			if (name.startsWith(folder)) return true;
		}
		return false;
	}

	static boolean isSafeName(String name) {
		return SAFE_NAME.matcher(name).matches() && !DEVICE_NAME.matcher(name).find();
	}

	/**
	 * {@code dir/name}, only if it stays inside {@code dir} once both are made canonical (which also resolves any
	 * symbolic links on the way).
	 */
	public static File resolveInside(File dir, String name) throws IOException {
		if (!isSafeName(name)) throw new IOException("Unsafe resource name: " + name);
		File base = dir.getCanonicalFile();
		File target = new File(base, name).getCanonicalFile();
		if (!target.toPath().startsWith(base.toPath()) || target.equals(base)) {
			throw new IOException("Resource path leaves the resources folder: " + name);
		}
		return target;
	}

	private void fetchObject(Entry entry, File target) throws IOException {
		String url = OBJECT_URL + entry.hash.substring(0, 2) + "/" + entry.hash;
		Https.Response response = http.request("GET", url, null, null, (int) Math.min(entry.size + 1, MAX_OBJECT_BYTES));
		if (response.status != 200) throw new IOException("HTTP " + response.status);
		if (response.body.length != entry.size || !entry.hash.equals(sha1(response.body))) {
			throw new IOException("failed its hash check");
		}

		File dir = target.getParentFile();
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
		File tmp = File.createTempFile("download", ".tmp", dir);
		try {
			OutputStream out = new FileOutputStream(tmp);
			try {
				out.write(response.body);
			} finally {
				out.close();
			}
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			if (tmp.exists() && !tmp.delete()) tmp.deleteOnExit();
		}
	}

	public static String sha1(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] hash = digest.digest(bytes);
			StringBuilder sb = new StringBuilder(40);
			for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
