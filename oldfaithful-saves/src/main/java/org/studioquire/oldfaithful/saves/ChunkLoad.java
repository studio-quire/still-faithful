package org.studioquire.oldfaithful.saves;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.NoteBlockBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.storage.AlphaChunkStorage;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionIo;
import org.studioquire.oldfaithful.saves.mixin.EntityRegistryAccessor;

/**
 * Loading a chunk from a region file without ever throwing away what's on disk (docs/bugs.md #1).
 *
 * <p>Vanilla treats every failure (a damaged record, unreadable data, one entity whose data throws, even a read error
 * from a file another program has locked) like a chunk that was never generated: it generates new terrain and saves it
 * over the record at the next save. Here:
 * <ul>
 * <li>What can be read is kept: an entity or block entity whose data fails is left out, and arrays of the wrong size
 * are rebuilt the way vanilla rebuilds missing ones; the rest of the chunk loads. A block left without its block
 * entity gets a fresh one.</li>
 * <li>What can't be read at all is copied to {@code corrupt/} beside the region folder before anything else happens,
 * and only then regenerated, as vanilla would, so the world goes on and the original bytes are kept for repair.</li>
 * <li>A read error (the file couldn't be read, so there's nothing to copy) is retried; if it persists, the chunk is
 * shown as generated terrain but never saved this session, so the record on disk stays as it is.</li>
 * <li>Each of these is logged and told to the player in chat.</li>
 * </ul>
 * A partly damaged chunk is saved again, repaired, at the next save, once its original is kept. A healthy chunk loads
 * exactly as vanilla loads it.
 */
public final class ChunkLoad {
	/** Decompressed chunk data larger than this isn't parsed (no real chunk comes near it). */
	private static final int MAX_NBT = 64 << 20;

	private static final Set<String> protectedChunks = Collections.synchronizedSet(new HashSet<>());
	private static final ThreadLocal<List<String>> salvage = new ThreadLocal<>();

	private ChunkLoad() {
	}

	/** The careful load; {@code handled} false means our layer isn't on this region file and vanilla should load it. */
	public static final class Result {
		public final boolean handled;
		public final Chunk chunk;

		Result(boolean handled, Chunk chunk) {
			this.handled = handled;
			this.chunk = chunk;
		}
	}

	private static final Result VANILLA = new Result(false, null);

	public static Result load(File dir, World world, int chunkX, int chunkZ) {
		RegionFile region = RegionIo.getRegionFile(dir, chunkX, chunkZ);
		RegionWrites writes = region instanceof RegionFileAccess ? ((RegionFileAccess) region).oldfaithful$writes() : null;
		if (writes == null) return VANILLA;
		int index = (chunkX & 31) + (chunkZ & 31) * 32;
		RegionWrites.Raw raw = writes.read(index);
		for (int attempt = 0; raw.status == RegionWrites.Raw.Status.IO_ERROR && attempt < 3; attempt++) {
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			raw = writes.read(index);
		}
		String key = key(dir, chunkX, chunkZ);
		switch (raw.status) {
			case ABSENT:
				return new Result(true, null);
			case IO_ERROR:
				protectedChunks.add(key);
				Notices.chunk(chunkX, chunkZ, "couldn't be read from " + writes.file.getName() + " (" + raw.detail
						+ "). It shows as new terrain for now, which won't be saved, so the original stays on disk.");
				return new Result(true, null);
			case OK:
			case OVERSIZED:
				protectedChunks.remove(key);
				return new Result(true, parse(dir, world, chunkX, chunkZ, raw, writes.file));
			default:
				lost(dir, chunkX, chunkZ, raw.bytes, describe(raw), writes.file);
				return new Result(true, null);
		}
	}

	private static String describe(RegionWrites.Raw raw) {
		switch (raw.status) {
			case BAD_POINTER: return "the region's index points outside the file: " + raw.detail;
			case BAD_LENGTH: return "the record's length is damaged: " + raw.detail;
			case BAD_VERSION: return "the record's compression type is unknown: " + raw.detail;
			case TRUNCATED: return "the file ends partway through the record";
			default: return raw.status + ": " + raw.detail;
		}
	}

	/** A chunk whose original couldn't be read, or couldn't be copied aside, is never saved over this session. */
	public static boolean isProtected(File dir, int chunkX, int chunkZ) {
		return protectedChunks.contains(key(dir, chunkX, chunkZ));
	}

	/**
	 * For the chunk caches' decorate: a protected chunk stands in for terrain that's really still on disk, so it isn't
	 * populated, which would spill trees and ores from made-up terrain into its real neighbours (and they are saved).
	 */
	public static boolean isProtectedIn(Object storage, int chunkX, int chunkZ) {
		if (protectedChunks.isEmpty() || !(storage instanceof RegionStorageAccess)) return false;
		return isProtected(((RegionStorageAccess) storage).oldfaithful$dir(), chunkX, chunkZ);
	}

	private static String key(File dir, int chunkX, int chunkZ) {
		return dir.getAbsolutePath() + "|" + chunkX + "," + chunkZ;
	}

	private static Chunk parse(File dir, World world, int chunkX, int chunkZ, RegionWrites.Raw raw, File regionFile) {
		byte[] record = rawRecord(raw);
		byte[] nbtBytes;
		try {
			nbtBytes = inflate(raw.bytes, raw.version);
		} catch (IOException e) {
			lost(dir, chunkX, chunkZ, record, "the compressed data is damaged: " + e.getMessage(), regionFile);
			return null;
		}
		String bad = NbtCheck.check(nbtBytes);
		if (bad != null) {
			lost(dir, chunkX, chunkZ, record, "the chunk's data is damaged: " + bad, regionFile);
			return null;
		}
		NbtCompound root;
		try {
			root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbtBytes)));
		} catch (Exception e) {
			lost(dir, chunkX, chunkZ, record, "the chunk's data can't be parsed: " + e, regionFile);
			return null;
		}
		NbtCompound level;
		try {
			if (!root.contains("Level")) {
				lost(dir, chunkX, chunkZ, record, "the record has no Level data", regionFile);
				return null;
			}
			level = root.getCompound("Level");
		} catch (ClassCastException e) {
			lost(dir, chunkX, chunkZ, record, "the record's Level data is the wrong type", regionFile);
			return null;
		}

		List<String> repairs = new ArrayList<>();
		Map<String, NbtElement> entries = ((NbtCompoundAccess) level).oldfaithful$entries();
		if (!isByteArray(entries.get("Blocks"), 32768)) {
			lost(dir, chunkX, chunkZ, record, "the record's block array is missing or the wrong size", regionFile);
			return null;
		}
		if (!(entries.get("xPos") instanceof NbtInt)) level.putInt("xPos", chunkX);
		if (!(entries.get("zPos") instanceof NbtInt)) level.putInt("zPos", chunkZ);
		if (!isByteArray(entries.get("Data"), 16384)) {
			level.putByteArray("Data", new byte[16384]);
			repairs.add("block data (reset)");
		}
		if (!isByteArray(entries.get("BlockLight"), 16384)) {
			level.putByteArray("BlockLight", new byte[16384]);
			repairs.add("block light (reset)");
		}
		boolean relight = false;
		if (!isByteArray(entries.get("SkyLight"), 16384) || !isByteArray(entries.get("HeightMap"), 256)) {
			level.putByteArray("SkyLight", new byte[16384]);
			level.putByteArray("HeightMap", new byte[256]);
			relight = true;
			repairs.add("sky light and height map (recomputed)");
		}
		for (String list : new String[] {"Entities", "TileEntities"}) {
			NbtElement e = entries.get(list);
			if (e != null && !(e instanceof NbtList)) {
				entries.remove(list);
				repairs.add(list + " (not a list; left out)");
			}
		}
		NbtElement populated = entries.get("TerrainPopulated");
		if (populated != null && populated.getType() != 1) entries.remove("TerrainPopulated");

		Chunk chunk;
		List<String> dropped = new ArrayList<>();
		salvage.set(dropped);
		try {
			chunk = AlphaChunkStorage.loadChunkFromNbt(world, level);
			if (!chunk.chunkPosEquals(chunkX, chunkZ)) {
				// Vanilla's own handling of a record stored under the wrong position.
				System.out.println("Chunk file at " + chunkX + "," + chunkZ + " is in the wrong location; relocating. (Expected "
						+ chunkX + ", " + chunkZ + ", got " + chunk.x + ", " + chunk.z + ")");
				level.putInt("xPos", chunkX);
				level.putInt("zPos", chunkZ);
				dropped.clear();
				chunk = AlphaChunkStorage.loadChunkFromNbt(world, level);
			}
		} catch (Throwable t) {
			salvage.remove();
			lost(dir, chunkX, chunkZ, record, "the chunk can't be built from its data: " + t, regionFile);
			return null;
		}
		salvage.remove();
		if (relight) {
			chunk.heightmap = new byte[256];
			chunk.skyLight = new ChunkNibbleArray(chunk.blocks.length);
			chunk.populateHeightMap();
		}
		chunk.fill();
		repairs.addAll(dropped);
		restoreBlockEntities(chunk, repairs);
		repairSpawners(chunk, repairs);
		if (raw.status == RegionWrites.Raw.Status.OVERSIZED) {
			Log.info("Chunk " + chunkX + ", " + chunkZ + " loaded from its oversized-chunk file " + raw.detail);
		}
		if (!repairs.isEmpty()) {
			File copy = Backups.chunk(dir, chunkX, chunkZ, record, "loaded with parts left out or rebuilt: " + String.join("; ", repairs));
			// Saved again at the next save once the original is kept, so the repair sticks and isn't reported every
			// time; without a copy, never saved over this session.
			if (copy != null) chunk.markDirty();
			else protectedChunks.add(key(dir, chunkX, chunkZ));
			Notices.chunk(chunkX, chunkZ, "had damaged parts (" + String.join("; ", repairs) + "). The rest loaded; the original is kept"
					+ (copy != null ? " in " + Notices.shortPath(copy) : "") + ".");
		}
		return chunk;
	}

	/**
	 * Each of vanilla's blocks with a block entity that has none (its data was unreadable and left out above, the whole
	 * list was, or Pure Faithful 0.1.0 saved it that way) gets a fresh one, as if just placed: an empty chest, an idle
	 * furnace, a blank sign. Without one, breaking a chest, furnace, dispenser or jukebox throws (their onBreak reads it
	 * after Chunk.setBlock has already changed the block, too late for getBlockEntity's lazy creation), which stops a
	 * server, and a sign is invisible. Made here rather than through that lazy path, whose onPlaced would turn a furnace
	 * or dispenser to face away from its neighbours. Vanilla's own play never leaves one of these blocks without its
	 * block entity, so in a healthy world this finds nothing. Other mods' blocks, and the piston's moving block (which
	 * has no default, and whose code expects it may have none), are left as vanilla has them.
	 */
	private static void restoreBlockEntities(Chunk chunk, List<String> repairs) {
		List<String> made = null;
		byte[] blocks = chunk.blocks;
		for (int i = 0; i < blocks.length; i++) {
			int id = blocks[i] & 0xFF;
			if (!Block.BLOCKS_WITH_ENTITY[id]) continue;
			int x = i >> 11, z = i >> 7 & 15, y = i & 127;
			if (chunk.blockEntities.containsKey(new BlockPos(x, y, z))) continue;
			Block block = Block.BLOCKS[id];
			BlockEntity fresh;
			String what;
			if (block == Block.CHEST) {
				fresh = new ChestBlockEntity();
				what = "an empty chest";
			} else if (block == Block.FURNACE || block == Block.LIT_FURNACE) {
				// A lit one stays lit until it next burns something, as with vanilla's own lazy creation.
				fresh = new FurnaceBlockEntity();
				what = "an empty furnace";
			} else if (block == Block.DISPENSER) {
				fresh = new DispenserBlockEntity();
				what = "an empty dispenser";
			} else if (block == Block.JUKEBOX) {
				fresh = new JukeboxBlockEntity();
				// Meta 1 says it holds a record; the fresh one doesn't, and with meta 1 no record could go in.
				chunk.meta.set(x, y, z, 0);
				what = "an empty jukebox";
			} else if (block == Block.NOTE_BLOCK) {
				fresh = new NoteBlockBlockEntity();
				what = "a note block on its lowest note";
			} else if (block == Block.SIGN || block == Block.WALL_SIGN) {
				fresh = new SignBlockEntity();
				what = "a blank sign";
			} else if (block == Block.SPAWNER) {
				fresh = new MobSpawnerBlockEntity();
				what = "a pig spawner (the block's default)";
			} else {
				continue;
			}
			chunk.setBlockEntity(x, y, z, fresh);
			what += " at " + (chunk.x * 16 + x) + ", " + y + ", " + (chunk.z * 16 + z);
			Log.warn("Put " + what + " where the block had no block entity");
			if (made == null) made = new ArrayList<>();
			made.add(what);
		}
		if (made == null) return;
		int shown = Math.min(made.size(), 5);
		repairs.add("put in their place: " + String.join(", ", made.subList(0, shown))
				+ (made.size() > shown ? " and " + (made.size() - shown) + " more" : ""));
	}

	/**
	 * docs/bugs.md #65: a spawner's tick casts what its entity id makes to a mob, so a spawner set to something else
	 * (primed TNT, an item, a minecart: world editors allow it) throws every tick a player is within 16 blocks, which
	 * crashes single player and stops a server, every time the world is opened. Such a spawner is set back to the block's
	 * default, a pig, and listed with the repairs. An id nothing is registered under is left alone: the game already
	 * spawns nothing for it, and it may be another mod's mob that comes back with that mod.
	 */
	private static void repairSpawners(Chunk chunk, List<String> repairs) {
		Map<String, Class<?>> classes = EntityRegistryAccessor.oldfaithful$idToClass();
		List<String> reset = null;
		for (Object o : chunk.blockEntities.values()) {
			if (!(o instanceof MobSpawnerBlockEntity)) continue;
			MobSpawnerBlockEntity spawner = (MobSpawnerBlockEntity) o;
			NbtCompound nbt = new NbtCompound();
			spawner.writeNbt(nbt);
			String id = nbt.getString("EntityId");
			Class<?> type = classes.get(id);
			if (type == null || LivingEntity.class.isAssignableFrom(type)) continue;
			spawner.setSpawnedEntityId("Pig");
			String what = "a pig spawner in place of one of " + id + " (not a mob) at " + spawner.x + ", " + spawner.y + ", " + spawner.z;
			Log.warn("Put " + what);
			if (reset == null) reset = new ArrayList<>();
			reset.add(what);
		}
		if (reset != null) repairs.add(String.join(", ", reset));
	}

	private static boolean isByteArray(NbtElement e, int length) {
		return e instanceof NbtByteArray && ((NbtByteArray) e).value != null && ((NbtByteArray) e).value.length == length;
	}

	/** Called from the entity and block-entity reads inside a careful load: true if the failure was recorded. */
	public static boolean recordDropped(String what, Throwable t) {
		List<String> list = salvage.get();
		if (list == null) return false;
		list.add(what + " (unreadable: " + t.getClass().getSimpleName() + "; left out)");
		Log.warn("Leaving out " + what + ": " + t);
		return true;
	}

	/**
	 * A record that can't be read: copied aside, then regenerated as vanilla would. If the copy can't be made (a full
	 * disk, no permission), the record is the only copy there is: the chunk is regenerated for this session but never
	 * saved over it, as after a read error.
	 */
	static void lost(File dir, int chunkX, int chunkZ, byte[] bytes, String why, File regionFile) {
		File copy = Backups.chunk(dir, chunkX, chunkZ, bytes, why);
		if (copy == null) {
			protectedChunks.add(key(dir, chunkX, chunkZ));
			Notices.chunk(chunkX, chunkZ, "in " + regionFile.getName() + " couldn't be read (" + why + "), and a copy couldn't be"
					+ " kept (see the log). It shows as new terrain for now, which won't be saved, so the original stays on disk.");
			return;
		}
		Notices.chunk(chunkX, chunkZ, "in " + regionFile.getName() + " couldn't be read (" + why + "), so it was generated anew."
				+ " The original is kept in " + Notices.shortPath(copy) + ".");
	}

	/** The record as it sits in the region (length, compression type, payload), for a backup that can be put back. */
	private static byte[] rawRecord(RegionWrites.Raw raw) {
		byte[] out = new byte[5 + raw.bytes.length];
		int length = raw.bytes.length + 1;
		out[0] = (byte) (length >>> 24);
		out[1] = (byte) (length >>> 16);
		out[2] = (byte) (length >>> 8);
		out[3] = (byte) length;
		out[4] = (byte) raw.version;
		System.arraycopy(raw.bytes, 0, out, 5, raw.bytes.length);
		return out;
	}

	private static byte[] inflate(byte[] compressed, int version) throws IOException {
		InputStream in = version == 1 ? new GZIPInputStream(new ByteArrayInputStream(compressed))
				: new InflaterInputStream(new ByteArrayInputStream(compressed));
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64 << 10, compressed.length * 4));
		byte[] buf = new byte[64 << 10];
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
			if (out.size() > MAX_NBT) throw new IOException("decompresses to more than " + (MAX_NBT >> 20) + " MB");
		}
		return out.toByteArray();
	}
}
