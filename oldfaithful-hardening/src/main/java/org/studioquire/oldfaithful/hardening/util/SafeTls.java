package org.studioquire.oldfaithful.hardening.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * TLS for {@link Https} that no other code in the game can loosen. HttpsURLConnection's defaults (its socket factory,
 * its hostname verifier) are JVM-wide and any mod can replace them, with "trust everything" as the usual reason;
 * here the trust comes from the JRE's own certificate store through a context of ours, and every socket checks the
 * server's name against its certificate during the handshake itself (endpoint identification), whatever verifier
 * is installed.
 */
public final class SafeTls {
	public static final SSLSocketFactory FACTORY = build();

	private SafeTls() {
	}

	private static SSLSocketFactory build() {
		try {
			TrustManagerFactory trust = TrustManagerFactory.getInstance("PKIX");
			trust.init((KeyStore) null); // the JRE's cacerts
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, trust.getTrustManagers(), null);
			return new Checked(context.getSocketFactory());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No TLS available", e);
		}
	}

	/** Turns on hostname checking in the handshake for every socket it makes. */
	private static final class Checked extends SSLSocketFactory {
		private final SSLSocketFactory delegate;

		Checked(SSLSocketFactory delegate) {
			this.delegate = delegate;
		}

		private static Socket check(Socket socket) {
			if (socket instanceof SSLSocket) {
				SSLSocket ssl = (SSLSocket) socket;
				SSLParameters params = ssl.getSSLParameters();
				params.setEndpointIdentificationAlgorithm("HTTPS");
				ssl.setSSLParameters(params);
			}
			return socket;
		}

		@Override
		public String[] getDefaultCipherSuites() {
			return delegate.getDefaultCipherSuites();
		}

		@Override
		public String[] getSupportedCipherSuites() {
			return delegate.getSupportedCipherSuites();
		}

		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
			return check(delegate.createSocket(s, host, port, autoClose));
		}

		@Override
		public Socket createSocket() throws IOException {
			return check(delegate.createSocket());
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException {
			return check(delegate.createSocket(host, port));
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
			return check(delegate.createSocket(host, port, localHost, localPort));
		}

		@Override
		public Socket createSocket(InetAddress host, int port) throws IOException {
			return check(delegate.createSocket(host, port));
		}

		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
			return check(delegate.createSocket(address, port, localAddress, localPort));
		}
	}
}
