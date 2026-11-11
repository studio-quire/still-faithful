package org.studioquire.oldfaithful.hardening;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

import org.studioquire.oldfaithful.hardening.util.Log;

/** Read once, from config/purefaithful-hardening.properties (written with defaults and explanations if missing). */
public final class Config {
	private static final String DEFAULTS =
			"# purefaithful-hardening. Restart the game after changing anything here.\n"
			+ "\n"
			+ "# Fetch any of the game's sounds missing from resources/, from Mojang over HTTPS, checked by hash.\n"
			+ "# Launchers normally provide them already. Beta's own bow and door sounds, which launchers now give in later\n"
			+ "# recordings, are fetched once from Modification Station's archive of beta's files (over HTTPS, checked by\n"
			+ "# hash) and kept in cache/. With this off nothing is downloaded, and the bow and doors are beta's only if\n"
			+ "# they were fetched before.\n"
			+ "resources.download=true\n"
			+ "\n"
			+ "# Show players' skins and capes, looked up from Mojang over HTTPS. Off: everyone has the default skin\n"
			+ "# and nothing is requested.\n"
			+ "skins=true\n"
			+ "\n"
			+ "# Set up game controllers at startup, as the game does though it never uses them. That loads jinput's\n"
			+ "# native libraries (it can stall startup). Only for a mod that reads controllers through LWJGL.\n"
			+ "controllers=false\n"
			+ "\n"
			+ "# Keep a copy of the game's output in logs/purefaithful/ (the last few runs), with session tokens taken out.\n"
			+ "log.file=true\n";

	public static final Config INSTANCE = load();

	public final boolean resourcesDownload;
	public final boolean skins;
	public final boolean controllers;
	public final boolean logFile;

	private Config(Properties p) {
		resourcesDownload = bool(p, "resources.download");
		skins = bool(p, "skins");
		controllers = bool(p, "controllers");
		logFile = bool(p, "log.file");
	}

	private static Config load() {
		Properties defaults = new Properties();
		try {
			defaults.load(new java.io.StringReader(DEFAULTS));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		Properties props = new Properties(defaults);
		File file = FabricLoader.getInstance().getConfigDir().resolve("purefaithful-hardening.properties").toFile();
		try {
			if (file.isFile()) {
				InputStream in = new FileInputStream(file);
				try {
					props.load(in);
				} finally {
					in.close();
				}
			} else {
				file.getParentFile().mkdirs();
				OutputStream out = new FileOutputStream(file);
				try {
					Writer w = new OutputStreamWriter(out, StandardCharsets.ISO_8859_1);
					w.write(DEFAULTS);
					w.flush();
				} finally {
					out.close();
				}
			}
		} catch (IOException e) {
			Log.warn("Could not read or write " + file + ", using defaults: " + e);
		}
		return new Config(props);
	}

	private static boolean bool(Properties p, String key) {
		return Boolean.parseBoolean(p.getProperty(key).trim());
	}
}
