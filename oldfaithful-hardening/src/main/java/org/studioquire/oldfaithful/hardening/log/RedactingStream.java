package org.studioquire.oldfaithful.hardening.log;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * What System.out (or err) writes into once our log is installed: collects bytes up to each line end (or a flush),
 * redacts the text, and passes it on to the original console stream and to our log file.
 */
final class RedactingStream extends OutputStream {
	private final PrintStream console;
	private final Charset charset;
	private final LogFile file;
	private final String tag;
	private final Redactor redactor;
	private final ByteArrayOutputStream pending = new ByteArrayOutputStream(256);

	/** A line longer than this goes out unfinished; nothing the game prints comes near it. */
	static final int MAX_PENDING = 1 << 20;

	RedactingStream(PrintStream console, Charset charset, LogFile file, String tag, Redactor redactor) {
		this.console = console;
		this.charset = charset;
		this.file = file;
		this.tag = tag;
		this.redactor = redactor;
	}

	@Override
	public synchronized void write(int b) {
		pending.write(b);
		if (b == '\n' || pending.size() >= MAX_PENDING) emit();
	}

	@Override
	public synchronized void write(byte[] b, int off, int len) {
		int end = off + len;
		while (off < end) {
			int nl = -1;
			for (int i = off; i < end; i++) {
				if (b[i] == '\n') {
					nl = i;
					break;
				}
			}
			int stop = nl < 0 ? end : nl + 1;
			pending.write(b, off, stop - off);
			if (nl >= 0 || pending.size() >= MAX_PENDING) emit();
			off = stop;
		}
	}

	/**
	 * Passes the flush on, but keeps an unfinished line until it ends: redaction works on whole lines, and the
	 * streams flush after every print, so a token printed in two pieces would otherwise go out unredacted.
	 */
	@Override
	public synchronized void flush() {
		console.flush();
	}

	/** Whatever is left of an unfinished line, at exit (GameLog registers this). */
	synchronized void drain() {
		emit();
	}

	private void emit() {
		if (pending.size() == 0) return;
		String text = redactor.redact(new String(pending.toByteArray(), charset));
		pending.reset();
		console.print(text);
		console.flush();
		if (file != null) file.write(text, tag);
	}
}
