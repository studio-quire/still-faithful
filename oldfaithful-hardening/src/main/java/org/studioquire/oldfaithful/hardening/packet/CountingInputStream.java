package org.studioquire.oldfaithful.hardening.packet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Counts the bytes read through it: how many a connection's packets took on the wire ({@link ConnectionLimits}). */
public final class CountingInputStream extends FilterInputStream {
	private volatile long count;

	public CountingInputStream(InputStream in) {
		super(in);
	}

	public long count() {
		return count;
	}

	@Override
	public int read() throws IOException {
		int b = super.read();
		if (b >= 0) count++;
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int n = super.read(b, off, len);
		if (n > 0) count += n;
		return n;
	}

	@Override
	public long skip(long n) throws IOException {
		long s = super.skip(n);
		if (s > 0) count += s;
		return s;
	}

	@Override
	public boolean markSupported() {
		return false;
	}
}
