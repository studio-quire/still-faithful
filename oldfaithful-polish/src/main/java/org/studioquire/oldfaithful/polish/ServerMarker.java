package org.studioquire.oldfaithful.polish;

/**
 * Tells a client which of Still Faithful's changes to mining a server has, as Old Faithful's fixes do (the same
 * format, so both mods' marks share the field): the name field of the server's login packet, which beta's server
 * sends empty and its client never reads. Up to 16 characters: the prefix, then one letter per change. Old Faithful's
 * fix mod uses 'm' and 's'; this mod's letters are its own.
 */
public final class ServerMarker {
	static final String PREFIX = "oldfaithful:";
	public static final char GLOWSTONE = 'g';

	private ServerMarker() {
	}

	public static String add(String name, char change) {
		String marked = name != null && name.startsWith(PREFIX) ? name : PREFIX;
		return marked.indexOf(change, PREFIX.length()) >= 0 || marked.length() >= 16 ? marked : marked + change;
	}

	public static boolean has(String name, char change) {
		return name != null && name.startsWith(PREFIX) && name.indexOf(change, PREFIX.length()) >= 0;
	}
}
