package oldfaithful.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Hashes {
	private Hashes() {
	}

	static String hash(Path file, String algorithm) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}

		try (InputStream in = Files.newInputStream(file)) {
			byte[] buf = new byte[1 << 16];
			int n;
			while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	static boolean matches(Path file, String sha1) throws IOException {
		return Files.isRegularFile(file) && hash(file, "SHA-1").equalsIgnoreCase(sha1);
	}
}
