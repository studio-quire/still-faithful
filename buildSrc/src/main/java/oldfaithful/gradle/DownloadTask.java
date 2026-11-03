package oldfaithful.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Fetches one of the game's jars over HTTPS and keeps it only if its SHA-1 matches the pinned hash of Mojang's
 * original. A file already in place with the right hash is kept, so the jar can also be supplied by hand.
 */
public abstract class DownloadTask extends DefaultTask {
	private static final long MAX_BYTES = 64L << 20;

	@Input
	public abstract Property<String> getUrl();

	@Input
	public abstract Property<String> getSha1();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@TaskAction
	public void download() throws IOException, InterruptedException {
		Path out = getOutput().get().getAsFile().toPath();
		String sha1 = getSha1().get();
		if (Hashes.matches(out, sha1)) return;

		URI uri = URI.create(getUrl().get());
		if (!"https".equals(uri.getScheme())) throw new GradleException("Refusing a non-HTTPS download: " + uri);

		Files.createDirectories(out.getParent());
		Path tmp = out.resolveSibling(out.getFileName() + ".part");
		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL) // never follows HTTPS -> HTTP
				.connectTimeout(Duration.ofSeconds(30))
				.build();
		HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(uri).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) throw new GradleException("HTTP " + response.statusCode() + " from " + uri);

		try (InputStream in = response.body(); OutputStream os = Files.newOutputStream(tmp)) {
			byte[] buf = new byte[1 << 16];
			long total = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				total += n;
				if (total > MAX_BYTES) throw new GradleException("Download larger than expected: " + uri);
				os.write(buf, 0, n);
			}
		}

		String actual = Hashes.hash(tmp, "SHA-1");
		if (!actual.equalsIgnoreCase(sha1)) {
			Files.delete(tmp);
			throw new GradleException("SHA-1 mismatch for " + uri + ": expected " + sha1 + ", got " + actual);
		}

		Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
