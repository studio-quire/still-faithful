package org.studioquire.oldfaithful.hardening;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.studioquire.oldfaithful.hardening.packet.PacketLimits;
import org.studioquire.oldfaithful.hardening.util.Json;

class JsonAndLimitsTest {
	@Test
	void parsesAndQuotes() {
		Object v = Json.parse(" {\"a\": [1, 2.5, -3e2, true, false, null], \"s\": \"q\\\"\\u00e9\\n\"} ");
		Map<String, Object> m = Json.object(v);
		assertEquals(List.of(1.0, 2.5, -300.0, true, false), ((List<?>) m.get("a")).subList(0, 5));
		assertEquals("q\"é\n", m.get("s"));
		assertEquals("\"a\\\"b\\\\c\\u0001\"", Json.quote("a\"b\\c\u0001"));
		assertEquals("x\"y", Json.parse(Json.quote("x\"y")));
	}

	@Test
	void rejectsMalformedAndAbusiveInput() {
		for (String bad : new String[] {"", "{", "{\"a\":}", "[1,]", "{\"a\":1} x", "\"\\x\"", "nul", "{1:2}"}) {
			assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
		}
		assertThrows(IllegalArgumentException.class, () -> Json.parse("[".repeat(1000) + "]".repeat(1000)));
	}

	@Test
	void chunkLengthsAreBoundedByTheRegion() {
		assertDoesNotThrow(() -> PacketLimits.checkChunkData(0, 1, 1, 1));
		assertDoesNotThrow(() -> PacketLimits.checkChunkData(81958, 16, 128, 16)); // zlib's compressBound(81920)
		assertThrows(IOException.class, () -> PacketLimits.checkChunkData(81959, 16, 128, 16));
		assertThrows(IOException.class, () -> PacketLimits.checkChunkData(-1, 16, 128, 16));
		assertThrows(IOException.class, () -> PacketLimits.checkChunkData(Integer.MAX_VALUE, 256, 256, 256));
	}

	@Test
	void explosionCountsAreBounded() {
		assertDoesNotThrow(() -> PacketLimits.checkExplosion(0));
		assertDoesNotThrow(() -> PacketLimits.checkExplosion(PacketLimits.MAX_EXPLOSION_BLOCKS));
		assertThrows(IOException.class, () -> PacketLimits.checkExplosion(PacketLimits.MAX_EXPLOSION_BLOCKS + 1));
		assertThrows(IOException.class, () -> PacketLimits.checkExplosion(-5));
	}
}
