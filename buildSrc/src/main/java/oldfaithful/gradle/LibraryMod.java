package oldfaithful.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import groovy.json.JsonSlurper;

/** A library mod's jar and what its {@code fabric.mod.json} says. */
record LibraryMod(Path jar, String id, boolean content, boolean match, int network, List<String> depends, boolean tests, boolean worldTests, boolean client) {
	private static final List<String> NOT_LIBRARY = List.of("fabricloader", "minecraft", "java", "faithful-core", "oldfaithful-settings");

	@SuppressWarnings("unchecked")
	static LibraryMod read(Path jar) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) throw new IOException(jar + " has no fabric.mod.json");
			try (InputStream in = zip.getInputStream(entry)) {
				Map<String, Object> json = (Map<String, Object>) new JsonSlurper().parseText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				Map<String, Object> custom = (Map<String, Object>) json.getOrDefault("custom", Map.of());
				Map<String, Object> faithful = (Map<String, Object>) custom.getOrDefault("faithful", Map.of());
				List<String> depends = new ArrayList<>();
				for (String id : ((Map<String, Object>) json.getOrDefault("depends", Map.of())).keySet()) {
					if (!NOT_LIBRARY.contains(id)) depends.add(id);
				}
				Map<String, Object> entrypoints = (Map<String, Object>) json.getOrDefault("entrypoints", Map.of());
				return new LibraryMod(jar, (String) json.get("id"), Boolean.TRUE.equals(faithful.get("content")), Boolean.TRUE.equals(faithful.get("match")),
						faithful.get("network") instanceof Number n ? n.intValue() : 1, depends,
						entrypoints.containsKey("faithful:test") || entrypoints.containsKey("faithful:test-client"), entrypoints.containsKey("faithful:test"), "client".equals(json.get("environment")));
			}
		}
	}
}
