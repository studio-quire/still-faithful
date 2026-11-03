package org.studioquire.oldfaithful.hardening.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * logs/purefaithful/latest.log: what the game printed this run, each line stamped with the time, in UTF-8. At
 * start the previous run's file is kept under its date and only the newest {@link #KEEP} of those survive. One run
 * writes at most {@link #MAX_BYTES}; past that the file says so and stops growing (the console still gets
 * everything).
 */
final class LogFile {
	static final String LATEST = "latest.log";
	static final int KEEP = 10;
	static final long MAX_BYTES = 32L << 20;

	private final Writer out;
	private final long maxBytes;
	private long written;
	private boolean full;
	private boolean lineStart = true;
	private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS");

	LogFile(File dir, long maxBytes) throws IOException {
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't create " + dir);
		rotate(dir, KEEP);
		this.out = new OutputStreamWriter(new FileOutputStream(new File(dir, LATEST)), StandardCharsets.UTF_8);
		this.maxBytes = maxBytes;
	}

	/** Renames latest.log to its last-modified time and deletes all but the newest {@code keep} older logs. */
	static void rotate(File dir, int keep) {
		File latest = new File(dir, LATEST);
		if (latest.isFile()) {
			String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(latest.lastModified()));
			File target = new File(dir, stamp + ".log");
			for (int n = 2; target.exists(); n++) target = new File(dir, stamp + "-" + n + ".log");
			if (!latest.renameTo(target)) latest.delete();
		}
		File[] files = dir.listFiles();
		if (files == null) return;
		List<String> old = new ArrayList<String>();
		for (File f : files) {
			// Our own names only: yyyy-MM-dd_HH-mm-ss[-n].log.
			if (f.isFile() && f.getName().matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(-\\d+)?\\.log")) old.add(f.getName());
		}
		Collections.sort(old, Collections.reverseOrder());
		for (int i = keep; i < old.size(); i++) new File(dir, old.get(i)).delete();
	}

	/** Appends text as printed (possibly part of a line); {@code tag} marks lines from stderr. */
	synchronized void write(String text, String tag) {
		if (full) return;
		try {
			StringBuilder b = new StringBuilder(text.length() + 32);
			int start = 0;
			while (start < text.length()) {
				if (lineStart) {
					b.append('[').append(time.format(new Date())).append("] ");
					if (tag != null) b.append(tag).append(' ');
					lineStart = false;
				}
				int nl = text.indexOf('\n', start);
				int end = nl < 0 ? text.length() : nl + 1;
				b.append(text, start, end);
				if (nl >= 0) lineStart = true;
				start = end;
			}
			String s = b.toString();
			written += s.length();
			if (written > maxBytes) {
				full = true;
				out.write((lineStart ? "" : "\n") + "[log] This run's log reached " + (maxBytes >> 20) + " MB; nothing more is written to the file.\n");
			} else {
				out.write(s);
			}
			out.flush();
		} catch (IOException e) {
			full = true;
		}
	}

	synchronized void close() throws IOException {
		full = true;
		out.close();
	}
}
