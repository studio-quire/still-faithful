package org.studioquire.oldfaithful.fixes;

/**
 * #62: how a server with the fix tells clients what a spawner spawns. Beta's server sends block entity data only for
 * signs, in the sign packet, so the server sends a spawner's entity id in a sign packet at the spawner's position: a
 * vanilla client applies a sign packet only where it has a sign, so it ignores this one and keeps its pig; a client
 * with the fix reads it where it has a spawner. Each line holds at most 15 characters (the client reads no more), so a
 * longer id (another mod's mob) isn't sent and shows as the pig, as before.
 */
public final class SpawnerMobs {
	/** The first line; the second is the entity id, the other two empty. */
	public static final String MARK = "#oldfaithful:sp";
	public static final int MAX_LINE = 15;

	private SpawnerMobs() {
	}

	public static String[] lines(String entityId) {
		if (entityId == null || entityId.isEmpty() || entityId.length() > MAX_LINE) return null;
		return new String[] {MARK, entityId, "", ""};
	}

	/** The entity id a sign packet's lines carry, or null if they aren't a spawner's. */
	public static String entityId(String[] lines) {
		if (lines == null || lines.length != 4 || !MARK.equals(lines[0]) || lines[1] == null || lines[1].isEmpty()) return null;
		return lines[1];
	}
}
