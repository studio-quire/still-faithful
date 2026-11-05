package oldfaithful.gradle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

/** A world's chunks as they lie in its {@code .mcr} files: the bytes of each chunk's NBT, unpacked, by {@code region/index}. */
final class RegionFiles {
	private RegionFiles() {
	}

	static Map<String, byte[]> read(Path world) throws IOException {
		Map<String, byte[]> chunks = new TreeMap<>();
		Path dir = world.resolve("region");
		if (!Files.isDirectory(dir)) return chunks;
		List<Path> files;
		try (Stream<Path> list = Files.list(dir)) {
			files = list.filter(p -> p.getFileName().toString().endsWith(".mcr")).toList();
		}
		for (Path file : files) {
			byte[] all = Files.readAllBytes(file);
			ByteBuffer buffer = ByteBuffer.wrap(all);
			for (int i = 0; i < 1024; i++) {
				int location = buffer.getInt(i * 4);
				if (location == 0) continue;
				int at = (location >>> 8) * 4096;
				int length = buffer.getInt(at);
				if (all[at + 4] != 2) throw new IOException(file + ": chunk " + i + " isn't zlib");
				try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(all, at + 5, length - 1))) {
					chunks.put(file.getFileName() + "/" + i, in.readAllBytes());
				}
			}
		}
		return chunks;
	}
}
