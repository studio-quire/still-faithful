package org.studioquire.oldfaithful.hardening.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameLogTest {
	private static final String ACCESS = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlc2lnbmF0dXJl";
	private static final String SESSION = "token:" + ACCESS + ":853c80ef3c3749fdaa49938b674adae6";

	@Test
	void redactsTheSessionFromLaunchArguments() {
		Redactor r = Redactor.fromLaunchArguments(new String[] {"--username", "jeb_", "--session", SESSION, "--gameDir", "x"});
		assertEquals("Setting user: jeb_, <redacted>", r.redact("Setting user: jeb_, " + SESSION));
		assertEquals("access <redacted> alone", r.redact("access " + ACCESS + " alone"));
		assertEquals("jeb_ stays", r.redact("jeb_ stays"));
	}

	@Test
	void redactsTokenShapesWithoutArguments() {
		Redactor r = Redactor.fromLaunchArguments(new String[0]);
		assertEquals("user x, token:<redacted>", r.redact("user x, token:abcdefgh12345:0123456789abcdef0123456789abcdef"));
		assertEquals("bearer <redacted>", r.redact("bearer " + ACCESS));
		// Short or ordinary values are left alone.
		assertEquals("token:abc and eyJ", r.redact("token:abc and eyJ"));
	}

	@Test
	void offlineSessionsAreNotSecrets() {
		Redactor r = Redactor.fromLaunchArguments(new String[] {"--session", "-", "--username", "Player"});
		assertEquals("Player - ok", r.redact("Player - ok"));
	}

	@Test
	void streamRedactsConsoleAndFile(@TempDir Path dir) throws IOException {
		ByteArrayOutputStream console = new ByteArrayOutputStream();
		Charset cs = StandardCharsets.UTF_8;
		LogFile file = new LogFile(dir.toFile(), 1 << 20);
		Redactor r = Redactor.fromLaunchArguments(new String[] {"--session", SESSION});
		PrintStream out = new PrintStream(new RedactingStream(new PrintStream(console, true, "UTF-8"), cs, file, null, r), true, "UTF-8");
		PrintStream err = new PrintStream(new RedactingStream(new PrintStream(console, true, "UTF-8"), cs, file, "[stderr]", r), true, "UTF-8");
		out.println("Setting user: jeb_, " + SESSION);
		out.print("half ");
		out.println("a line, ünïcode");
		err.println("oops");
		file.close();
		String shown = new String(console.toByteArray(), cs);
		assertFalse(shown.contains(ACCESS), shown);
		assertTrue(shown.contains("Setting user: jeb_, <redacted>"), shown);
		assertTrue(shown.contains("half a line, ünïcode"), shown);

		List<String> lines = Files.readAllLines(dir.resolve(LogFile.LATEST), StandardCharsets.UTF_8);
		assertEquals(3, lines.size(), lines.toString());
		assertTrue(lines.get(0).matches("\\[\\d\\d:\\d\\d:\\d\\d\\.\\d{3}] Setting user: jeb_, <redacted>"), lines.get(0));
		assertTrue(lines.get(1).endsWith("] half a line, ünïcode"), lines.get(1));
		assertTrue(lines.get(2).endsWith("] [stderr] oops"), lines.get(2));
	}

	/** A token printed in pieces, with flushes between (the streams flush after every print), is still redacted. */
	@Test
	void tokenSplitAcrossPrintsIsRedacted() throws IOException {
		ByteArrayOutputStream console = new ByteArrayOutputStream();
		Charset cs = StandardCharsets.UTF_8;
		Redactor r = Redactor.fromLaunchArguments(new String[] {"--session", SESSION});
		RedactingStream stream = new RedactingStream(new PrintStream(console, true, "UTF-8"), cs, null, null, r);
		PrintStream out = new PrintStream(stream, true, "UTF-8");
		int half = SESSION.length() / 2;
		out.print("Setting user: jeb_, " + SESSION.substring(0, half));
		out.flush();
		out.write(SESSION.substring(half, half + 5).getBytes(cs)); // write(byte[]) flushes too
		out.print(SESSION.substring(half + 5));
		out.println(" and on");
		out.print("an unfinished last line " + ACCESS);
		stream.drain();
		String shown = new String(console.toByteArray(), cs);
		assertFalse(shown.contains(SESSION.substring(0, half)), shown);
		assertFalse(shown.contains(ACCESS), shown);
		assertTrue(shown.contains("Setting user: jeb_, <redacted> and on"), shown);
		assertTrue(shown.contains("an unfinished last line <redacted>"), shown);
	}

	@Test
	void fileStopsAtItsCap(@TempDir Path dir) throws IOException {
		LogFile file = new LogFile(dir.toFile(), 100);
		for (int i = 0; i < 10; i++) file.write("0123456789012345678901234567890123456789\n", null);
		file.close();
		String text = new String(Files.readAllBytes(dir.resolve(LogFile.LATEST)), StandardCharsets.UTF_8);
		assertTrue(text.length() < 300 && text.contains("nothing more is written"), text);
	}

	@Test
	void rotationKeepsTheNewest(@TempDir Path dir) throws IOException {
		File d = dir.toFile();
		for (int i = 0; i < 12; i++) Files.write(dir.resolve(String.format("2026-10-%02d_12-00-00.log", i + 1)), new byte[0]);
		Files.write(dir.resolve("notes.txt"), new byte[0]);
		Path latest = Files.write(dir.resolve(LogFile.LATEST), "last run".getBytes(StandardCharsets.UTF_8));
		latest.toFile().setLastModified(java.sql.Timestamp.valueOf("2026-11-01 09:30:00").getTime());

		LogFile.rotate(d, LogFile.KEEP);
		List<String> names = Arrays.asList(d.list());
		assertFalse(names.contains(LogFile.LATEST));
		assertTrue(names.contains("2026-11-01_09-30-00.log"), names.toString());
		assertTrue(names.contains("notes.txt"), "others' files are left alone");
		assertEquals(LogFile.KEEP + 1, names.size(), names.toString());
		assertFalse(names.contains("2026-10-03_12-00-00.log"), names.toString());
		assertTrue(names.contains("2026-10-04_12-00-00.log"), names.toString());
	}
}
