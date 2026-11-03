package org.studioquire.oldfaithful.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.studioquire.oldfaithful.hardening.util.Https;

/** Our HTTPS keeps checking certificates when something else in the JVM has switched the defaults to trust anything. */
class SafeTlsTest {
	@Test
	void untrustedServerIsRefusedEvenWithTrustAllDefaults(@TempDir Path dir) throws Exception {
		Path keystore = dir.resolve("server.p12");
		String keytool = new File(System.getProperty("java.home"), "bin/keytool").getPath();
		Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
				"-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "2", "-storetype", "PKCS12",
				"-keystore", keystore.toString(), "-storepass", "changeit", "-keypass", "changeit")
				.redirectErrorStream(true).start();
		p.getInputStream().readAllBytes();
		assertEquals(0, p.waitFor(), "keytool");

		KeyStore ks = KeyStore.getInstance("PKCS12");
		try (InputStream in = Files.newInputStream(keystore)) {
			ks.load(in, "changeit".toCharArray());
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, "changeit".toCharArray());
		SSLContext serverTls = SSLContext.getInstance("TLS");
		serverTls.init(kmf.getKeyManagers(), null, null);

		HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
		server.createContext("/", exchange -> {
			byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();

		SSLSocketFactory oldFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
		HostnameVerifier oldVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
		try {
			SSLContext trustAll = SSLContext.getInstance("TLS");
			trustAll.init(null, new TrustManager[] {new X509TrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) {
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			}}, null);
			HttpsURLConnection.setDefaultSSLSocketFactory(trustAll.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);

			String url = "https://localhost:" + server.getAddress().getPort() + "/";
			HttpsURLConnection plain = (HttpsURLConnection) new URL(url).openConnection();
			assertEquals(200, plain.getResponseCode(), "with the defaults switched, an ordinary connection trusts it");
			plain.disconnect();

			assertThrows(IOException.class, () -> Https.REAL.request("GET", url, null, null, 100),
					"ours must still refuse a certificate the JRE doesn't trust");
		} finally {
			HttpsURLConnection.setDefaultSSLSocketFactory(oldFactory);
			HttpsURLConnection.setDefaultHostnameVerifier(oldVerifier);
			server.stop(0);
		}
	}
}
