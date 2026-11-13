package org.studioquire.oldfaithful.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerMarkerTest {
	@Test
	void marksComposeInAnyOrderAndVanillaHasNone() {
		String a = ServerMarker.add(ServerMarker.add("", ServerMarker.TOOL_SPEEDS), ServerMarker.WOODEN_SLABS);
		String b = ServerMarker.add(ServerMarker.add("", ServerMarker.WOODEN_SLABS), ServerMarker.TOOL_SPEEDS);
		for (String s : new String[] {a, b}) {
			assertTrue(ServerMarker.has(s, ServerMarker.TOOL_SPEEDS));
			assertTrue(ServerMarker.has(s, ServerMarker.WOODEN_SLABS));
			assertTrue(s.length() <= 16, "the login packet reads at most 16 characters");
		}
		assertEquals(a, ServerMarker.add(a, ServerMarker.TOOL_SPEEDS));
		assertFalse(ServerMarker.has("", ServerMarker.TOOL_SPEEDS));
		assertFalse(ServerMarker.has(null, ServerMarker.TOOL_SPEEDS));
		assertFalse(ServerMarker.has(ServerMarker.add("", ServerMarker.WOODEN_SLABS), ServerMarker.TOOL_SPEEDS));
		// A name before the prefix's letters can't be mistaken for a mark.
		assertFalse(ServerMarker.has("Ms", ServerMarker.TOOL_SPEEDS));
	}

	@Test
	void toolSpeedsBeforeRedstoneOreIsAnotherLetter() {
		String old = "oldfaithful:ms";
		assertFalse(ServerMarker.has(old, ServerMarker.TOOL_SPEEDS));
		assertTrue(ServerMarker.has(old, ServerMarker.TOOL_SPEEDS_WITHOUT_REDSTONE_ORE));
		String now = ServerMarker.add("", ServerMarker.TOOL_SPEEDS);
		assertFalse(ServerMarker.has(now, ServerMarker.TOOL_SPEEDS_WITHOUT_REDSTONE_ORE));
		// Still Faithful's two letters with Old Faithful's two fill the field exactly.
		String full = ServerMarker.add(ServerMarker.add(now, ServerMarker.WOODEN_SLABS) + "gf", ServerMarker.TOOL_SPEEDS);
		assertEquals(16, full.length());
	}
}
