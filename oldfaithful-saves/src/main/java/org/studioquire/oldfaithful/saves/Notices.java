package org.studioquire.oldfaithful.saves;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import net.fabricmc.loader.api.FabricLoader;

/**
 * What a player should hear about: logged, and on the client also put in chat once a world is open. Chat gets what
 * happened, where, and where the original is kept, in the player's words; the log gets that with the file-format detail.
 */
public final class Notices {
	private static final ConcurrentLinkedQueue<String> chat = new ConcurrentLinkedQueue<>();

	private Notices() {
	}

	/** {@code said} goes to chat; {@code logged}, the same with the detail, to the log. Both follow "The chunk at blocks X, Z ". */
	static void chunk(int chunkX, int chunkZ, String said, String logged) {
		String at = "The chunk at blocks " + chunkX * 16 + ", " + chunkZ * 16 + " ";
		say(at + said, at + logged);
	}

	static void say(String message) {
		say(message, message);
	}

	static void say(String said, String logged) {
		Log.warn(logged);
		if (chat.size() < 20) chat.add(said);
	}

	/** The client hands queued messages to its chat (MinecraftNoticeMixin), under the name of the edition running. */
	public static void drain(Consumer<String> sink) {
		String m;
		while ((m = chat.poll()) != null) sink.accept("§e" + edition() + ": §f" + m);
	}

	private static String edition() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("stillfaithful-polish")) return "Still Faithful";
		if (loader.isModLoaded("oldfaithful-fixes")) return "Old Faithful";
		return "Pure Faithful";
	}

	/** A path from the saves folder down, which reads better in chat than a full one. */
	static String shortPath(File f) {
		String path = f.getPath().replace('\\', '/');
		int saves = path.lastIndexOf("/saves/");
		return saves >= 0 ? path.substring(saves + 1) : path;
	}
}
