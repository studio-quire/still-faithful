package org.studioquire.oldfaithful.saves;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Test chunk payloads that check themselves: which entry and which version they are, and a CRC over everything, so a
 * reader can tell a complete record from a torn one or one that belongs to another chunk.
 */
final class Payloads {
	static final int MAGIC = 0x51554952;

	private Payloads() {
	}

	/** Uncompressed payload for (index, version), {@code size} bytes or so, half compressible. */
	static byte[] plain(int index, int version, int size) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(size + 32);
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(MAGIC);
			out.writeInt(index);
			out.writeInt(version);
			out.writeInt(size);
			Random r = new Random(index * 1_000_003L + version);
			byte[] body = new byte[size];
			for (int i = 0; i < size; i++) body[i] = (byte) (i % 3 == 0 ? r.nextInt(256) : (i >> 4));
			out.write(body);
			CRC32 crc = new CRC32();
			crc.update(bytes.toByteArray());
			out.writeLong(crc.getValue());
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	/** Like {@link #plain}, but random bytes all through, so it doesn't compress. */
	static byte[] plainRandom(int index, int version, int size) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(size + 32);
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(MAGIC);
			out.writeInt(index);
			out.writeInt(version);
			out.writeInt(size);
			byte[] body = new byte[size];
			new Random(index * 1_000_003L + version).nextBytes(body);
			out.write(body);
			CRC32 crc = new CRC32();
			crc.update(bytes.toByteArray());
			out.writeLong(crc.getValue());
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	static byte[] deflate(byte[] plain) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
				out.write(plain);
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	/** Checks an inflated stream: returns {index, version} or throws with what's wrong. */
	static int[] check(InputStream inflated) throws IOException {
		byte[] all = inflated.readAllBytes();
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(all));
		if (in.readInt() != MAGIC) throw new IOException("bad magic");
		int index = in.readInt(), version = in.readInt(), size = in.readInt();
		if (all.length != 16 + size + 8) throw new IOException("length " + all.length + " for size " + size);
		CRC32 crc = new CRC32();
		crc.update(all, 0, 16 + size);
		long stored = new DataInputStream(new ByteArrayInputStream(all, 16 + size, 8)).readLong();
		if (stored != crc.getValue()) throw new IOException("CRC mismatch");
		return new int[] {index, version};
	}

	static int[] checkCompressed(byte[] compressed) throws IOException {
		return check(new InflaterInputStream(new ByteArrayInputStream(compressed)));
	}
}
