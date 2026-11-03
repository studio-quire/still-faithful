package org.studioquire.oldfaithful.fixes;

/**
 * Tells a client which fixes that change mining speed the server has, so a client with them mines faster only where
 * the server agrees. In multiplayer the client times the break and the server checks it: a vanilla server refuses a
 * break reported much sooner than its own timing, finishes it later on its own, and while it waits drops any second
 * early report outright, so the client removes a block the server keeps (it comes back 4 s later, when the client
 * undoes the unconfirmed change).
 * <p>
 * The marker rides in the name field of the server's login packet, which beta's server sends empty and its client
 * never reads, so a vanilla client sees nothing. Up to 16 characters (the packet's limit): the prefix, then one letter
 * per fix.
 */
public final class ServerMarker {
	static final String PREFIX = "oldfaithful:";
	public static final char TOOL_SPEEDS = 'm';
	public static final char WOODEN_SLABS = 's';

	private ServerMarker() {
	}

	public static String add(String name, char fix) {
		String marked = name != null && name.startsWith(PREFIX) ? name : PREFIX;
		return marked.indexOf(fix, PREFIX.length()) >= 0 || marked.length() >= 16 ? marked : marked + fix;
	}

	public static boolean has(String name, char fix) {
		return name != null && name.startsWith(PREFIX) && name.indexOf(fix, PREFIX.length()) >= 0;
	}
}
