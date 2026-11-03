package org.studioquire.oldfaithful.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FixesSettingsTest {
	/** A new fix needs a row on the settings screen, or a place among the server-only ones. */
	@Test
	void everyFixHasAPlaceOnTheScreen() {
		List<String> missing = new ArrayList<>();
		for (String key : FixesConfig.keys()) {
			if (!FixesSettings.described(key) && !FixesSettings.SERVER_ONLY.contains(key)) missing.add(key);
		}
		assertEquals(List.of(), missing, "fixes without a row in FixesSettings.ROWS (or in SERVER_ONLY)");
	}

	@Test
	void rowsNameRealFixesOnceEach() {
		Set<String> keys = new HashSet<>(), labels = new HashSet<>();
		for (String[] row : FixesSettings.ROWS) {
			assertTrue(FixesConfig.keys().contains(row[0]), "no such fix: " + row[0]);
			assertTrue(keys.add(row[0]), "twice: " + row[0]);
			assertTrue(labels.add(row[3]), "two buttons named " + row[3]);
			assertTrue(!FixesSettings.SERVER_ONLY.contains(row[0]), "server-only and on the screen: " + row[0]);
		}
		for (String key : FixesSettings.SERVER_ONLY) assertTrue(FixesConfig.keys().contains(key), "no such fix: " + key);
	}
}
