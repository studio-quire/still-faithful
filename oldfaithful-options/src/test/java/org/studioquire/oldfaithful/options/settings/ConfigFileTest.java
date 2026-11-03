package org.studioquire.oldfaithful.options.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFileTest {
	/** As the mods write their files: a header, then an explanation before each switch. */
	private static final String WRITTEN = "# oldfaithful-fixes: fixes of beta 1.7.3's own mistakes, each on its own switch.\n"
			+ "# false leaves that part of the game exactly as beta had it.\n"
			+ "\n# #7: the sky turned grey while an inventory, chest or furnace was open.\nsky.afterContainer=true\n"
			+ "\n# #41: doors ... redstone.repeaterUpdates=false is how you'd keep them ignoring a repeater.\n"
			+ "redstone.repeaterUpdates=true\n"
			+ "\n# #42: a block's next step.\nticks.saved=true\n";

	/** Every key the text sets, as Properties reads it, with {@code key} expected at {@code value}. */
	private static void assertOnlyChanged(String before, String after, String key, String value) {
		Properties b = ConfigFile.parse(before), a = ConfigFile.parse(after);
		b.setProperty(key, value);
		assertEquals(b, a);
	}

	@Test
	void changesTheValueInPlaceAndNothingElse() {
		String after = ConfigFile.set(WRITTEN, "redstone.repeaterUpdates", "false");
		assertEquals(WRITTEN.replace("redstone.repeaterUpdates=true", "redstone.repeaterUpdates=false"), after);
		assertOnlyChanged(WRITTEN, after, "redstone.repeaterUpdates", "false");
		// The comment that mentions the key isn't touched, and back again gives the very same text.
		assertEquals(WRITTEN, ConfigFile.set(after, "redstone.repeaterUpdates", "true"));
	}

	@Test
	void keepsWindowsLineEnds() {
		String crlf = WRITTEN.replace("\n", "\r\n");
		String after = ConfigFile.set(crlf, "ticks.saved", "false");
		assertEquals(crlf.replace("ticks.saved=true", "ticks.saved=false"), after);
		String added = ConfigFile.set(crlf, "new.key", "true");
		assertEquals(crlf + "new.key=true\r\n", added);
	}

	@Test
	void keepsEachLinesOwnSeparatorAndIndent() {
		String text = "  a = true\nb:true\nc true\nd\t=\ttrue\ne=\n";
		String after = text;
		for (String k : new String[]{"a", "b", "c", "d", "e"}) after = ConfigFile.set(after, k, "false");
		assertEquals("  a = false\nb:false\nc false\nd\t=\tfalse\ne=false\n", after);
		Properties p = ConfigFile.parse(after);
		for (String k : new String[]{"a", "b", "c", "d", "e"}) assertEquals("false", p.getProperty(k));
	}

	@Test
	void missingKeyIsAppended() {
		assertEquals(WRITTEN + "vsync=false\n", ConfigFile.set(WRITTEN, "vsync", "false"));
		assertEquals("a=true\nvsync=false\n", ConfigFile.set("a=true", "vsync", "false"));
		assertEquals("vsync=false\n", ConfigFile.set("", "vsync", "false"));
	}

	@Test
	void everyLineSettingTheKeyChanges() {
		// Properties takes the last; both are changed so whichever is read says the same.
		String text = "x=true\ny=1\nx=true\n";
		assertEquals("x=false\ny=1\nx=false\n", ConfigFile.set(text, "x", "false"));
	}

	@Test
	void continuationLinesAndEscapedKeys() {
		String text = "long=a,\\\n    b,\\\n    c\nafter=true\nweird\\=key=true\n# trailing\\\nnot.continued=true\n";
		String after = ConfigFile.set(text, "long", "z");
		assertEquals("long=z\nafter=true\nweird\\=key=true\n# trailing\\\nnot.continued=true\n", after);
		assertOnlyChanged(text, after, "long", "z");
		String escaped = ConfigFile.set(text, "weird=key", "false");
		assertOnlyChanged(text, escaped, "weird=key", "false");
		String plain = ConfigFile.set(text, "not.continued", "false");
		assertOnlyChanged(text, plain, "not.continued", "false");
	}

	@Test
	void commentsThatLookLikeTheKeyStayComments() {
		String text = "# vsync=true\n! vsync=true\nvsync=true\n";
		assertEquals("# vsync=true\n! vsync=true\nvsync=false\n", ConfigFile.set(text, "vsync", "false"));
	}

	@Test
	void writesWholeFilesInLatin1(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("config/x.properties");
		String text = "# café été\nkey=true\n";
		ConfigFile.write(file, ConfigFile.set(text, "key", "false"));
		assertEquals("# café été\nkey=false\n", new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
		assertEquals(ConfigFile.read(file), new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
		assertTrue(Files.list(file.getParent()).allMatch(p -> p.equals(file)), "no temporary file left");
		assertEquals("", ConfigFile.read(dir.resolve("missing.properties")));
	}
}
