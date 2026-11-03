package org.studioquire.oldfaithful.hardening.packet;

import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;

/**
 * How much a connection may have read but not yet handled. b1.7.3's reader thread reads packets as fast as they
 * arrive and queues them without limit, while the game handles at most 101 a tick; so whoever is on the other end
 * can fill the heap just by sending faster (a client: a few seconds of one-byte keep-alives; a server: chunk packets
 * that inflate to 42 MB each, or explosions and entity data that take ~25 times their wire size in memory). Over
 * these bounds the reader stops reading until the game catches up, which pushes back on the sender through TCP.
 * Nothing is dropped and no one is disconnected for it, and the rate packets are handled at doesn't change: the game
 * still handles 101 a tick while the queue holds that many.
 *
 * <p>A packet's weight is what it may hold in memory: the bytes it took on the wire, times 32 (the worst expansion
 * of any b1.7.3 packet: an explosion's 3 bytes per block become a ~70-byte set entry; a tracked byte's 2 bytes a
 * ~40-byte entry), plus a fixed overhead; for chunk data, its inflated size instead. The packet's own size() isn't
 * used: it counts entity data as 5 bytes whatever it holds.
 */
public final class ConnectionLimits {
	/** A server reading what a client sent. */
	public static final int SERVER_MAX_PACKETS = 4096;
	public static final long SERVER_MAX_BYTES = 1L << 20;
	/** A client reading what a server sent. */
	public static final int CLIENT_MAX_PACKETS = 16384;
	public static final long CLIENT_MAX_BYTES = 64L << 20;
	/** Memory per wire byte assumed for anything but chunk data. */
	public static final int EXPANSION = 32;
	public static final int OVERHEAD = 64;

	private ConnectionLimits() {
	}

	public static boolean full(boolean serverSide, int packets, long bytes) {
		return serverSide
				? packets >= SERVER_MAX_PACKETS || bytes >= SERVER_MAX_BYTES
				: packets >= CLIENT_MAX_PACKETS || bytes >= CLIENT_MAX_BYTES;
	}

	/** What a queued packet that took {@code wireBytes} to read may hold in memory. */
	public static long weight(Object packet, long wireBytes) {
		if (packet instanceof ChunkDataS2CPacket) {
			byte[] data = ((ChunkDataS2CPacket) packet).chunkData;
			return OVERHEAD + wireBytes + (data == null ? 0 : data.length); // the compressed copy is already garbage
		}
		return OVERHEAD + EXPANSION * wireBytes;
	}
}
