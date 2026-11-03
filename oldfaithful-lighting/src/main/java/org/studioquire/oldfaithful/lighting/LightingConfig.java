package org.studioquire.oldfaithful.lighting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * config/oldfaithful-lighting.properties, read once at start. Written with explanations if missing; a setting it lacks
 * (from an older version) is appended. One setting for now: the rest of the mod is one mechanism (every cell kept to
 * beta's own light rule), with no part a player would want back.
 */
public final class LightingConfig {
	private static final String HEADER = "# oldfaithful-lighting: beta 1.7.3's light kept to its own rule, so dark and bright patches heal.\n"
			+ "# Restart the game after editing this file (Options > Old Faithful... changes it at once). In multiplayer the\n"
			+ "# server's setting is the world's.\n";

	static final String FILE = "oldfaithful-lighting.properties";
	static final String LAVA_KEY = "lava.lightGenerated";
	private static final String LAVA_TEXT = "# Lava that comes with new terrain (lakes deep in caves, the Nether's lava seas) gave no light in beta until\n"
			+ "# something next to it changed. true lights it as it loads, as lava placed any other way always was: caves\n"
			+ "# by lava and the Nether's shores are brighter, and fewer monsters spawn right beside lava. false leaves it\n"
			+ "# dark as beta did, until light reaches it or it's disturbed; the rest of the fixes stay on either way.\n";

	/**
	 * Whether lava from terrain generation is lit as it loads (true), or left dark as beta left it (false). Old
	 * Faithful's settings screen changes it while the game runs ({@link LightingSettings}): it applies to land checked
	 * from then on (chunks as they load), and light already in the world stays as it is.
	 */
	private static volatile boolean lightGeneratedLava;

	static {
		Properties p = new Properties();
		File file = null;
		try {
			file = FabricLoader.getInstance().getConfigDir().resolve(FILE).toFile();
			StringBuilder add = new StringBuilder();
			if (file.isFile()) {
				try (InputStream in = new FileInputStream(file)) {
					p.load(in);
				}
			} else {
				file.getParentFile().mkdirs();
				add.append(HEADER);
			}
			if (!p.containsKey(LAVA_KEY)) add.append('\n').append(LAVA_TEXT).append(LAVA_KEY).append("=true\n");
			if (add.length() > 0) {
				try (OutputStream out = new FileOutputStream(file, true)) {
					out.write(add.toString().getBytes(StandardCharsets.ISO_8859_1));
				}
			}
		} catch (IOException | RuntimeException e) {
			System.out.println("[oldfaithful-lighting] WARN Could not read or write " + file + ", using the defaults: " + e);
		}
		// A system property overrides the file (the tests use it).
		String value = System.getProperty("oldfaithful.lighting." + LAVA_KEY, p.getProperty(LAVA_KEY, "true"));
		lightGeneratedLava = Boolean.parseBoolean(value.trim());
		if (!lightGeneratedLava) System.out.println("[oldfaithful-lighting] " + LAVA_KEY + "=false: lava from new terrain stays dark as in beta");
	}

	public static boolean lightGeneratedLava() {
		return lightGeneratedLava;
	}

	static void setLightGeneratedLava(boolean on) {
		lightGeneratedLava = on;
	}

	private LightingConfig() {
	}
}
