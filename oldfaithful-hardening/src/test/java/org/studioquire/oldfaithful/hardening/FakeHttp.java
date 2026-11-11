package org.studioquire.oldfaithful.hardening;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.studioquire.oldfaithful.hardening.util.Https;

/** Answers requests from a table; records what was asked. Unknown URLs get a 404. */
public final class FakeHttp implements Https.Client {
	public record Request(String method, String url, String body) {
	}

	private final Map<String, Https.Response> responses = new HashMap<>();
	public final List<Request> requests = new ArrayList<>();

	public FakeHttp on(String url, int status, byte[] body) {
		responses.put(url, new Https.Response(status, body));
		return this;
	}

	public FakeHttp on(String url, int status, String body) {
		return on(url, status, body.getBytes(StandardCharsets.UTF_8));
	}

	private final List<String> down = new ArrayList<>();

	/** Every URL starting with this fails as a host that doesn't answer does. */
	public FakeHttp unreachable(String prefix) {
		down.add(prefix);
		return this;
	}

	@Override
	public Https.Response request(String method, String url, String contentType, byte[] body, int maxBytes) throws IOException {
		requests.add(new Request(method, url, body == null ? null : new String(body, StandardCharsets.UTF_8)));
		for (String prefix : down) {
			if (url.startsWith(prefix)) throw new java.net.ConnectException("Connection timed out: " + url);
		}
		Https.Response r = responses.getOrDefault(url, new Https.Response(404, new byte[0]));
		if (r.body.length > maxBytes) throw new IOException("Response larger than " + maxBytes + " bytes");
		return r;
	}
}
