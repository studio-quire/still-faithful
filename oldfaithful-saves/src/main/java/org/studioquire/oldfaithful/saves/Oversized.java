package org.studioquire.oldfaithful.saves;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Chunks too big for the region format (a record of 256 sectors or more, 1 MB compressed: thousands of dropped items,
 * say). Vanilla silently doesn't save them, so everything since the chunk last fit is lost. Here the record goes into
 * {@code region/oldfaithful-oversized/r.X.Z.<lx>.<lz>.dat}, stamped with the header entry and time the region held for
 * the chunk when it was written; the region keeps that previous record, which plain b1.7.3 reads. The side file counts
 * only while the region still holds exactly that entry: a later save of the chunk, here or by plain b1.7.3, supersedes
 * it.
 *
 * <p>Format: "OFOV", int format (1), int stamp entry, int stamp time, int length, byte compression (2: zlib), payload.
 */
final class Oversized {
	private static final int MAGIC = 0x4F464F56;
	static final String DIR = "purefaithful-oversized";

	static final class Record {
		final int stampEntry, stampTime, version;
		final byte[] data;

		Record(int stampEntry, int stampTime, int version, byte[] data) {
			this.stampEntry = stampEntry;
			this.stampTime = stampTime;
			this.version = version;
			this.data = data;
		}
	}

	private Oversized() {
	}

	static File sideFile(File region, int index) {
		return new File(new File(region.getParentFile(), DIR), region.getName().replace(".mcr", "") + "." + (index & 31) + "." + (index >> 5) + ".dat");
	}

	static boolean anyFor(File region) {
		File dir = new File(region.getParentFile(), DIR);
		String prefix = region.getName().replace(".mcr", "") + ".";
		String[] names = dir.list();
		if (names == null) return false;
		for (String n : names) if (n.startsWith(prefix) && n.endsWith(".dat")) return true;
		return false;
	}

	static void write(File side, int stampEntry, int stampTime, byte[] data, int size) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(size + 32);
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(MAGIC);
		out.writeInt(1);
		out.writeInt(stampEntry);
		out.writeInt(stampTime);
		out.writeInt(size + 1);
		out.writeByte(2);
		out.write(data, 0, size);
		out.flush();
		SafeFiles.replaceNow(side, bytes.toByteArray());
	}

	static Record read(File side) {
		try {
			byte[] all = RegionWrites.readAll(side);
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(all));
			if (in.readInt() != MAGIC || in.readInt() != 1) return null;
			int entry = in.readInt(), time = in.readInt(), length = in.readInt();
			int version = in.readByte();
			if (length < 1 || length - 1 > all.length - 21) return null;
			byte[] data = new byte[length - 1];
			in.readFully(data);
			return new Record(entry, time, version, data);
		} catch (IOException e) {
			return null;
		}
	}
}
