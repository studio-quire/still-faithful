package org.studioquire.oldfaithful.hardening.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * The only way this mod talks to the network: HTTPS only, certificate and name checked by our own TLS ({@link
 * SafeTls}), no redirects followed,
 * timeouts set, and every response size capped. The game's own code used plain HTTP with none of these.
 */
public final class Https {
	public static final int CONNECT_TIMEOUT_MS = 10_000;
	public static final int READ_TIMEOUT_MS = 20_000;

	/** Swappable so the logic around it can be tested without a network. */
	public interface Client {
		Response request(String method, String url, String contentType, byte[] body, int maxBytes) throws IOException;
	}

	public static final class Response {
		public final int status;
		public final byte[] body;

		public Response(int status, byte[] body) {
			this.status = status;
			this.body = body;
		}

		public String text() {
			return new String(body, StandardCharsets.UTF_8);
		}
	}

	public static final Client REAL = Https::send;

	private Https() {
	}

	private static Response send(String method, String url, String contentType, byte[] body, int maxBytes) throws IOException {
		URL u = new URL(url);
		if (!"https".equals(u.getProtocol())) throw new IOException("Refusing a non-HTTPS request");

		HttpsURLConnection connection = (HttpsURLConnection) u.openConnection();
		try {
			connection.setSSLSocketFactory(SafeTls.FACTORY);
			connection.setInstanceFollowRedirects(false);
			connection.setUseCaches(false);
			connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
			connection.setReadTimeout(READ_TIMEOUT_MS);
			connection.setRequestMethod(method);
			connection.setRequestProperty("User-Agent", "purefaithful-hardening");
			if (body != null) {
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", contentType);
				connection.setFixedLengthStreamingMode(body.length);
				OutputStream out = connection.getOutputStream();
				try {
					out.write(body);
				} finally {
					out.close();
				}
			}

			int status = connection.getResponseCode();
			InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
			return new Response(status, in == null ? new byte[0] : readCapped(in, maxBytes));
		} finally {
			connection.disconnect();
		}
	}

	/** Reads a stream fully, failing rather than buffering more than {@code maxBytes}. */
	public static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				if (out.size() + n > maxBytes) throw new IOException("Response larger than " + maxBytes + " bytes");
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		} finally {
			in.close();
		}
	}

	/** True for statuses where the server accepted the request. */
	public static boolean ok(int status) {
		return status / 100 == 2;
	}
}
