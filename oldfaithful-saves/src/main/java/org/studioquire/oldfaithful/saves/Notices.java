package org.studioquire.oldfaithful.saves;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** What a player should hear about: logged, and on the client also put in chat once a world is open. */
public final class Notices {
	private static final ConcurrentLinkedQueue<String> chat = new ConcurrentLinkedQueue<>();

	private Notices() {
	}

	static void chunk(int chunkX, int chunkZ, String what) {
		say("The chunk at blocks " + chunkX * 16 + ", " + chunkZ * 16 + " " + what);
	}

	static void say(String message) {
		Log.warn(message);
		if (chat.size() < 20) chat.add(message);
	}

	/** The client hands queued messages to its chat (MinecraftNoticeMixin). */
	public static void drain(Consumer<String> sink) {
		String m;
		while ((m = chat.poll()) != null) sink.accept("§ePure Faithful: §f" + m);
	}

	/** A path from the saves folder down, which reads better in chat than a full one. */
	static String shortPath(File f) {
		String path = f.getPath().replace('\\', '/');
		int saves = path.lastIndexOf("/saves/");
		return saves >= 0 ? path.substring(saves + 1) : path;
	}
}
