package org.studioquire.oldfaithful.hardening.packet;

import java.io.IOException;

/**
 * Bounds for lengths a server sends before the client allocates for them. Without these, one packet with a huge
 * length runs the game out of memory: an OutOfMemoryError isn't an Exception, so the game's network code doesn't
 * catch it and the connection's reader thread dies instead of disconnecting cleanly.
 */
public final class PacketLimits {
	/**
	 * Most blocks an explosion packet may list. The largest vanilla explosion touches a few hundred; this allows a
	 * radius of about 39 blocks for modded servers while stopping a stream of entries from growing without bound.
	 */
	public static final int MAX_EXPLOSION_BLOCKS = 1 << 18;

	private PacketLimits() {
	}

	/**
	 * Chunk data arrives deflated, followed by the region's size. Deflate never expands data by more than zlib's
	 * {@code compressBound}, so a compressed length above that bound, or below zero, can't be genuine.
	 */
	public static void checkChunkData(int compressedSize, int sizeX, int sizeY, int sizeZ) throws IOException {
		long raw = (long) sizeX * sizeY * sizeZ * 5 / 2;
		long bound = raw + (raw >> 12) + (raw >> 14) + (raw >> 25) + 13;
		if (compressedSize < 0 || compressedSize > bound) {
			throw new IOException("Chunk data length " + compressedSize + " is impossible for a "
					+ sizeX + "x" + sizeY + "x" + sizeZ + " region");
		}
	}

	public static void checkExplosion(int count) throws IOException {
		if (count < 0 || count > MAX_EXPLOSION_BLOCKS) {
			throw new IOException("Explosion lists " + count + " blocks");
		}
	}
}
