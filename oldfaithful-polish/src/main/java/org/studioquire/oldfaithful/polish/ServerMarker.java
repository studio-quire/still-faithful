package org.studioquire.oldfaithful.polish;

import net.minecraft.network.packet.play.UpdateSignPacket;

/**
 * Tells a client which of Still Faithful's changes that both sides must agree on a server has (mining, collision), as
 * Old Faithful's fixes do (the same format, so both mods' marks share the field): the name field of the server's login
 * packet, which beta's server sends empty and its client never reads. Up to 16 characters: the prefix, then one letter
 * per change. Old Faithful's fix mod uses 'M' (before 2026-11-12, 'm') and 's'; this mod's letters are its own. With
 * 'g' and 'f' the field is full.
 * <p>
 * So later changes are marked in a sign packet each, sent straight after the login packet ({@link #sign}): at height
 * -1, where no client applies one (beta's applies a sign packet only at a loaded position, and nothing below 0 is), with
 * {@link #SIGN_MARK} on its first line (Old Faithful's spawner fix sends its own sign packets, marked otherwise) and
 * the change's letter on its second. A client forgets them at each login.
 */
public final class ServerMarker {
	static final String PREFIX = "oldfaithful:";
	public static final char GLOWSTONE = 'g';
	public static final char FENCES = 'f';

	/** 15 characters, all a sign line holds. */
	public static final String SIGN_MARK = "#oldfaithful:mk";
	public static final int SIGN_Y = -1;
	/** In a sign packet: obsidian as 1.0 has it, or as 1.0 with a diamond pickaxe only (Obsidian). */
	public static final char OBSIDIAN = 'o', OBSIDIAN_DIAMOND = 'd';

	private ServerMarker() {
	}

	public static UpdateSignPacket sign(char change) {
		return new UpdateSignPacket(0, SIGN_Y, 0, new String[] {SIGN_MARK, String.valueOf(change), "", ""});
	}

	public static boolean signHas(UpdateSignPacket packet, char change) {
		String[] text = packet.text;
		return packet.y == SIGN_Y && text != null && text.length == 4 && SIGN_MARK.equals(text[0]) && text[1] != null
				&& text[1].indexOf(change) >= 0;
	}

	public static String add(String name, char change) {
		String marked = name != null && name.startsWith(PREFIX) ? name : PREFIX;
		return marked.indexOf(change, PREFIX.length()) >= 0 || marked.length() >= 16 ? marked : marked + change;
	}

	public static boolean has(String name, char change) {
		return name != null && name.startsWith(PREFIX) && name.indexOf(change, PREFIX.length()) >= 0;
	}
}
