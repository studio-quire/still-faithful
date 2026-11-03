package oldfaithful.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import oldfaithful.gradle.lwjgl.LinkCheck;
import oldfaithful.gradle.lwjgl.References;

/**
 * Every org.lwjgl reference in the game, in the mods listed in {@code linkcheck-mods.json} (fetched by URL, kept only
 * if the SHA-512 matches) and in our own LWJGL jar's non-LWJGL-3 classes must link against that jar. Mods' expected
 * misses (a mixin into an LWJGL 2 internal that can't exist) are listed with a reason in the same file.
 */
public abstract class LinkCheckTask extends DefaultTask {
	/** The jar under test: our LWJGL component. */
	@Classpath
	public abstract ConfigurableFileCollection getLibrary();

	/** LWJGL 3's own jars: their classes are left out of the self-check (they link against themselves). */
	@Classpath
	public abstract ConfigurableFileCollection getLwjgl3();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getGameJar();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getModList();

	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getExtraSubjects();

	@Internal
	public abstract DirectoryProperty getCacheDir();

	@OutputFile
	public abstract RegularFileProperty getReport();

	private record Mod(String slug, String version, String file, String url, String sha512) {
	}

	@TaskAction
	public void check() throws Exception {
		String json = Files.readString(getModList().get().getAsFile().toPath());
		List<Mod> mods = parseMods(json);
		Map<String, String> expected = parseExpected(json);
		Path cache = Files.createDirectories(getCacheDir().get().getAsFile().toPath());

		Map<String, Path> subjects = new LinkedHashMap<>();
		subjects.put("minecraft b1.7.3", getGameJar().get().getAsFile().toPath());
		for (var f : getExtraSubjects().getFiles()) subjects.put(f.getName(), f.toPath());
		for (Mod m : mods) subjects.put(m.slug() + " " + m.version(), fetch(m, cache));

		List<Path> library = new ArrayList<>();
		for (var f : getLibrary().getFiles()) library.add(f.toPath());
		Map<String, LinkCheck.Result> results = LinkCheck.check(library, subjects);

		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		int total = 0;
		for (LinkCheck.Result r : results.values()) {
			total += r.references();
			report.append(r.subject()).append(": ").append(r.references()).append(" references\n");
			for (LinkCheck.Problem p : r.problems()) {
				String key = p.ref().toString();
				String reason = expected.get(key);
				report.append("  ").append(reason != null ? "expected: " : "UNRESOLVED: ").append(key).append(" [").append(p.why()).append("]")
						.append(reason != null ? " (" + reason + ")" : "").append('\n');
				if (reason == null) failures.add(r.subject() + ": " + key + " [" + p.why() + "] from " + p.from());
			}
		}
		total += selfCheck(library, report, failures);
		Files.writeString(getReport().get().getAsFile().toPath(), report.toString(), StandardCharsets.UTF_8);
		getLogger().lifecycle("Link check: {} references in {} jars and our own classes; {} unresolved. Report: {}",
				total, subjects.size(), failures.size(), getReport().get().getAsFile());
		if (!failures.isEmpty()) {
			throw new GradleException("Link check FAILED:\n  " + String.join("\n  ", failures.subList(0, Math.min(60, failures.size())))
					+ (failures.size() > 60 ? "\n  ... " + (failures.size() - 60) + " more" : ""));
		}
	}

	/** Our classes, the LWJGL 2 classes we took and the generated ones must link against the jar they're in. */
	private int selfCheck(List<Path> library, StringBuilder report, List<String> failures) throws IOException {
		Set<String> lwjgl3Classes = new java.util.HashSet<>();
		for (var f : getLwjgl3().getFiles()) {
			oldfaithful.gradle.lwjgl.ClassPool.forEachClass(f.toPath(), (where, bytes) ->
					lwjgl3Classes.add(where.substring(where.indexOf('!') + 1)));
		}
		Set<String> changed = new java.util.HashSet<>();
		try (var z = new java.util.zip.ZipFile(library.get(0).toFile())) {
			var e = z.getEntry("META-INF/oldfaithful-lwjgl/changed-classes.txt");
			if (e != null) for (String c : new String(z.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8).split("\n")) changed.add(c + ".class");
		}
		var pool = oldfaithful.gradle.lwjgl.ClassPool.of(library);
		int n = 0;
		int problems = 0;
		for (Map.Entry<References.Ref, Set<String>> e : References.scan(library.get(0), LinkCheck.PREFIX, true).all().entrySet()) {
			List<String> ours = new ArrayList<>();
			for (String where : e.getValue()) {
				String entry = where.substring(where.indexOf('!') + 1);
				if (!lwjgl3Classes.contains(entry) || changed.contains(entry)) ours.add(entry);
			}
			if (ours.isEmpty()) continue;
			n++;
			String why = LinkCheck.resolve(pool, e.getKey());
			if (why != null) {
				problems++;
				report.append("  UNRESOLVED (own classes): ").append(e.getKey()).append(" [").append(why).append("] from ").append(ours).append('\n');
				failures.add("own classes: " + e.getKey() + " [" + why + "] from " + ours);
			}
		}
		report.insert(0, "Our jar's own classes: " + n + " references, " + problems + " unresolved\n");
		return n;
	}

	private Path fetch(Mod m, Path cache) throws IOException, InterruptedException {
		Path file = cache.resolve(m.file());
		if (Files.isRegularFile(file) && sha512(Files.readAllBytes(file)).equals(m.sha512())) return file;
		getLogger().lifecycle("Fetching {} {} for the link check", m.slug(), m.version());
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(m.url())).build(), HttpResponse.BodyHandlers.ofInputStream());
		byte[] data;
		try (InputStream in = response.body()) {
			data = in.readAllBytes();
		}
		if (response.statusCode() != 200) throw new GradleException(m.url() + ": HTTP " + response.statusCode());
		if (!sha512(data).equals(m.sha512())) throw new GradleException(m.url() + ": SHA-512 doesn't match the pin");
		Files.write(file, data);
		return file;
	}

	private static String sha512(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final Pattern MOD = Pattern.compile("\\{[^{}]*\"slug\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"version\"\\s*:\\s*\"([^\"]+)\"[^{}]*"
			+ "\"file\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"url\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"sha512\"\\s*:\\s*\"([0-9a-f]+)\"[^{}]*}");
	private static final Pattern EXPECTED = Pattern.compile("\"ref\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"why\"\\s*:\\s*\"([^\"]+)\"");

	/** The file is ours and small; a pattern per object avoids a JSON library in the build. */
	private static List<Mod> parseMods(String json) {
		List<Mod> mods = new ArrayList<>();
		Matcher m = MOD.matcher(json.substring(json.indexOf("\"mods\""), json.indexOf("\"expected\"")));
		while (m.find()) mods.add(new Mod(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)));
		if (mods.isEmpty()) throw new GradleException("no mods in the link check list");
		return mods;
	}

	private static Map<String, String> parseExpected(String json) {
		Map<String, String> expected = new TreeMap<>();
		Matcher m = EXPECTED.matcher(json.substring(json.indexOf("\"expected\"")));
		while (m.find()) expected.put(m.group(1), m.group(2));
		return expected;
	}
}
