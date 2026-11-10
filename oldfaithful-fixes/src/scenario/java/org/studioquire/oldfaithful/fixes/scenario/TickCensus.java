package org.studioquire.oldfaithful.fixes.scenario;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.InflaterInputStream;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockEvent;
import net.minecraft.world.ServerWorld;
import org.studioquire.oldfaithful.fixes.PendingTicks;

/**
 * Test-only (ServerTicksTest, #42): every pending tick of every chunk, through an unload, a save and a restart, on a
 * queue as long as land made with nobody near leaves it.
 * <p>
 * {@code -Doldfaithful.fixes.scenario=bulk} loads a square of new chunks away from the spawn, eight a tick, which queues
 * ticks faster than the world runs them. Then, within one tick, so nothing runs in between: it works out each chunk's
 * ticks for itself, in one pass over the world's queue (in due order, then the parked ones); asks the mod for each
 * loaded chunk's; unloads some chunks, those with parked ticks first, and reads their records back from the region
 * files; saves the world and reads every other chunk's record back. Each must hold exactly the ticks worked out, in
 * that order, and the queue must have lost exactly the unloaded chunks' ticks. It writes what it worked out to
 * {@code ticks.txt} and turns saving off, so the files stay as they were at that tick.
 * <p>
 * {@code bulk-check}, on that world after a restart, loads the square's chunks in its first tick: the queue must gain
 * exactly the ticks in {@code ticks.txt}, each in its own chunk and due after the delay saved (one tick for a block
 * saved twice).
 */
public final class TickCensus {
	private static final int SIZE = Integer.getInteger("oldfaithful.fixes.bulk", 24);
	private static final int PER_TICK = 8;
	/** Chunks unloaded before the save: with parked ticks first, then the square's first rows. */
	private static final int UNLOAD = 48;
	private static final File FILE = new File("ticks.txt");

	private static int originX = Integer.MIN_VALUE;
	private static int originZ;
	private static int next;
	private static boolean done;
	private static boolean saidSet;
	private static int failures;

	private TickCensus() {
	}

	public static void tick(ServerWorld world) {
		if (done || world.dimension.id != 0) return;
		if (!saidSet) {
			// The world's own set, in a new world's first tick: the mod's counting one goes round it at the first save
			// or unload, and neither has happened yet. The test knows which it expects (the speed mod's).
			saidSet = true;
			say("pending ticks in " + ((PendingTicks.Queue) world).oldfaithful$pending().getClass().getName());
		}
		try {
			if ("bulk-check".equals(System.getProperty("oldfaithful.fixes.scenario"))) {
				check(world);
				done = true;
			} else if (load(world)) {
				census(world);
				done = true;
			}
		} catch (Throwable t) {
			done = true;
			fail("stopped by " + t);
			t.printStackTrace();
		}
		if (done) say(failures == 0 ? "PASS" : "FAILED: " + failures);
	}

	/** Loads the square a few chunks a tick; true once it's whole. */
	private static boolean load(ServerWorld world) {
		if (originX == Integer.MIN_VALUE) {
			Vec3i spawn = world.getSpawnPos();
			// Away from the spawn and from 0, 0, where the spawn is after a restart (docs/bugs.md #52).
			originX = spawn.x >= 0 ? (spawn.x >> 4) + 40 : (spawn.x >> 4) - 40 - SIZE;
			originZ = (spawn.z >> 4) - SIZE / 2;
		}
		for (int i = 0; i < PER_TICK && next < SIZE * SIZE; i++, next++) world.chunkCache.loadChunk(originX + next / SIZE, originZ + next % SIZE);
		return next == SIZE * SIZE;
	}

	private static void census(ServerWorld world) throws IOException {
		PendingTicks.Queue queue = (PendingTicks.Queue) world;
		long now = world.getTime();
		Map<Long, List<int[]>> expected = expected(queue, now);
		int queued = queue.oldfaithful$ordered().size();
		int parked = 0;
		for (List<BlockEvent> list : queue.oldfaithful$parked().values()) parked += list.size();

		// Every chunk that could hold ticks: the square, the spawn area, and wherever the queue says.
		Set<Long> chunks = new LinkedHashSet<>();
		for (int i = 0; i < SIZE * SIZE; i++) chunks.add(PendingTicks.key(originX + i / SIZE, originZ + i % SIZE));
		Vec3i spawn = world.getSpawnPos();
		for (int x = (spawn.x >> 4) - 14; x <= (spawn.x >> 4) + 14; x++) {
			for (int z = (spawn.z >> 4) - 14; z <= (spawn.z >> 4) + 14; z++) {
				if (world.chunkCache.isChunkLoaded(x, z)) chunks.add(PendingTicks.key(x, z));
			}
		}
		for (long key : expected.keySet()) {
			if (!world.chunkCache.isChunkLoaded(x(key), z(key))) fail("ticks held for chunk " + name(key) + ", which isn't loaded");
			chunks.add(key);
		}
		say("time " + now + ": " + chunks.size() + " chunks loaded, " + queued + " ticks queued and " + parked + " parked, in "
				+ expected.size() + " chunks");

		// What the mod would write for each chunk, as its save asks.
		long t0 = System.nanoTime();
		Map<Long, NbtList> collected = new LinkedHashMap<>();
		for (long key : chunks) collected.put(key, PendingTicks.collect(world, x(key), z(key), false));
		say("collected for " + chunks.size() + " chunks in " + ms(t0) + " ms");
		for (long key : chunks) same("collected", key, expected.get(key), read(collected.get(key)));

		// Unloads: chunks with parked ticks first (the spawn area's rim; within 128 blocks of the spawn nothing unloads),
		// then the square's first rows.
		List<Long> unload = new ArrayList<>();
		int unloadParked = 0;
		int unloadQueued = 0;
		for (long key : queue.oldfaithful$parked().keySet()) {
			if (unload.size() == UNLOAD / 2) break;
			if (Math.abs(x(key) * 16 + 8 - spawn.x) > 128 || Math.abs(z(key) * 16 + 8 - spawn.z) > 128) unload.add(key);
		}
		for (int i = 0; unload.size() < UNLOAD && i < SIZE * SIZE; i++) {
			long key = PendingTicks.key(originX + i / SIZE, originZ + i % SIZE);
			if (!unload.contains(key)) unload.add(key);
		}
		int leaving = 0;
		for (long key : unload) {
			List<int[]> ticks = expected.get(key);
			if (ticks != null) leaving += ticks.size();
			List<BlockEvent> waiting = queue.oldfaithful$parked().get(key);
			if (waiting != null) unloadParked += waiting.size();
			world.chunkCache.isLoaded(x(key), z(key)); // Biny's name for "unload this one when you next can"
		}
		unloadQueued = leaving - unloadParked;
		t0 = System.nanoTime();
		for (int i = 0; i < 4; i++) world.chunkCache.tick();
		say("unloaded " + unload.size() + " chunks in " + ms(t0) + " ms: " + unloadQueued + " ticks queued and " + unloadParked + " parked left with them");
		if (unloadParked == 0) fail("no chunk with parked ticks to unload: the unload of parked ticks went unchecked");
		if (unloadQueued == 0) fail("no chunk with queued ticks to unload: the unload of queued ticks went unchecked");
		for (long key : unload) {
			if (world.chunkCache.isChunkLoaded(x(key), z(key))) fail("chunk " + name(key) + " didn't unload");
			same("unloaded, on disk", key, expected.get(key), onDisk(key));
		}
		Map<Long, List<int[]>> left = expected(queue, now);
		for (long key : unload) {
			if (left.containsKey(key)) fail("chunk " + name(key) + " unloaded and the world still holds " + left.get(key).size() + " of its ticks");
		}
		int leftCount = 0;
		for (List<int[]> list : left.values()) leftCount += list.size();
		if (leftCount != queued + parked - leaving) fail("after the unloads the world holds " + leftCount + " ticks, not " + (queued + parked - leaving));
		if (queue.oldfaithful$ordered().size() != queue.oldfaithful$pending().size()) fail("the queue and its set differ in size");
		chunks.removeAll(unload);
		for (long key : chunks) {
			same("after the unloads", key, expected.get(key), left.get(key));
			same("collected after the unloads", key, expected.get(key), read(PendingTicks.collect(world, x(key), z(key), false)));
		}

		// The save a stop makes, with every chunk changed, as after land is made.
		for (long key : chunks) world.getChunk(x(key), z(key)).markDirty();
		t0 = System.nanoTime();
		world.saveWithLoadingDisplay(true, null);
		say("saved " + chunks.size() + " chunks in " + ms(t0) + " ms");
		for (long key : chunks) same("saved, on disk", key, expected.get(key), onDisk(key));
		if (queue.oldfaithful$ordered().size() != queued - unloadQueued) fail("the save changed the queue");
		same("the queue after the save", left, expected(queue, now));

		// For the restart: the square's chunks as they are on disk now, and nothing saved after this tick.
		StringBuilder out = new StringBuilder();
		out.append(originX).append(' ').append(originZ).append(' ').append(SIZE).append('\n');
		for (int i = 0; i < SIZE * SIZE; i++) {
			long key = PendingTicks.key(originX + i / SIZE, originZ + i % SIZE);
			List<int[]> ticks = expected.get(key);
			if (ticks == null) continue;
			for (int[] t : ticks) out.append(t[0]).append(' ').append(t[1]).append(' ').append(t[2]).append(' ').append(t[3]).append(' ').append(t[4]).append('\n');
		}
		Files.write(FILE.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
		world.savingDisabled = true;
	}

	private static void check(ServerWorld world) throws IOException {
		PendingTicks.Queue queue = (PendingTicks.Queue) world;
		List<String> lines = Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8);
		String[] head = lines.get(0).split(" ");
		originX = Integer.parseInt(head[0]);
		originZ = Integer.parseInt(head[1]);
		int size = Integer.parseInt(head[2]);
		Map<Long, List<int[]>> saved = new LinkedHashMap<>();
		for (String line : lines.subList(1, lines.size())) {
			String[] p = line.split(" ");
			int[] t = new int[5];
			for (int i = 0; i < 5; i++) t[i] = Integer.parseInt(p[i]);
			saved.computeIfAbsent(PendingTicks.key(t[0] >> 4, t[2] >> 4), k -> new ArrayList<>()).add(t);
		}

		// A chunk the server loaded as it started (the spawn can move onto the square, docs/bugs.md #52) has run a tick
		// already: only chunks loaded here, with nothing run in between, are compared.
		Set<Long> loaded = new LinkedHashSet<>();
		int before = queue.oldfaithful$ordered().size();
		long t0 = System.nanoTime();
		for (int i = 0; i < size * size; i++) {
			int x = originX + i / size;
			int z = originZ + i % size;
			if (world.chunkCache.isChunkLoaded(x, z)) continue;
			world.chunkCache.loadChunk(x, z);
			loaded.add(PendingTicks.key(x, z));
		}
		say("loaded " + loaded.size() + " of the square's " + size * size + " chunks in " + ms(t0) + " ms");
		if (loaded.size() < size * size / 2) fail("too few of the square's chunks were still to load");

		// Back on the queue: each in its chunk, due after its saved delay; those saved overdue (parked) come first now,
		// and ticks due together keep the order they were saved in. A block can be saved twice, parked and queued again
		// since (gravel at the loaded edge): the queue holds one tick a block, so the first saved is the one back.
		Map<Long, List<int[]>> now = expected(queue, world.getTime());
		int restored = 0;
		int twice = 0;
		for (long key : loaded) {
			List<int[]> want = saved.get(key);
			if (want != null) {
				Set<List<Integer>> blocks = new HashSet<>();
				List<int[]> once = new ArrayList<>();
				for (int[] t : want) {
					if (blocks.add(Arrays.asList(t[0], t[1], t[2], t[3]))) once.add(t);
				}
				twice += want.size() - once.size();
				want = once;
				want.sort(Comparator.comparingInt((int[] t) -> t[4]));
				restored += want.size();
			}
			same("restored", key, want, now.get(key));
			if (queue.oldfaithful$parked().containsKey(key)) fail("chunk " + name(key) + " came back with parked ticks");
		}
		int gained = queue.oldfaithful$ordered().size() - before;
		if (gained != restored) fail("the queue gained " + gained + " ticks, and " + restored + " were saved");
		if (restored == 0) fail("no ticks were saved in the chunks loaded: nothing was checked");
		say("restored " + restored + " ticks in " + loaded.size() + " chunks (" + twice + " more were a block's second)");
	}

	/**
	 * Each chunk's ticks as its record should list them, from one pass over the queue in due order and then the parked
	 * ones: x, y, z, the block id and the delay left.
	 */
	private static Map<Long, List<int[]>> expected(PendingTicks.Queue queue, long now) {
		Map<Long, List<int[]>> out = new LinkedHashMap<>();
		TreeSet<BlockEvent> ordered = queue.oldfaithful$ordered();
		for (BlockEvent e : ordered) out.computeIfAbsent(PendingTicks.key(e.x >> 4, e.z >> 4), k -> new ArrayList<>()).add(tick(e, now));
		Map<Long, List<int[]>> parked = new LinkedHashMap<>();
		for (Map.Entry<Long, List<BlockEvent>> p : queue.oldfaithful$parked().entrySet()) {
			List<int[]> list = new ArrayList<>();
			for (BlockEvent e : p.getValue()) {
				if (PendingTicks.key(e.x >> 4, e.z >> 4) != p.getKey()) fail("a tick parked under another chunk than its own");
				list.add(tick(e, now));
			}
			if (!list.isEmpty()) parked.put(p.getKey(), list);
		}
		for (Map.Entry<Long, List<int[]>> p : parked.entrySet()) out.computeIfAbsent(p.getKey(), k -> new ArrayList<>()).addAll(p.getValue());
		return out;
	}

	private static int[] tick(BlockEvent e, long now) {
		return new int[] {e.x, e.y, e.z, e.blockId, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, e.ticks - now))};
	}

	private static List<int[]> read(NbtList ticks) {
		if (ticks == null || ticks.size() == 0) return null;
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i < ticks.size(); i++) {
			NbtCompound t = (NbtCompound) ticks.get(i);
			out.add(new int[] {t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("i"), t.getInt("t")});
		}
		return out;
	}

	/** A chunk's TileTicks straight from its region file, by this class's own reading of the format. */
	private static List<int[]> onDisk(long key) throws IOException {
		int x = x(key);
		int z = z(key);
		File file = new File("world/region/r." + (x >> 5) + "." + (z >> 5) + ".mcr");
		try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
			in.seek(4 * ((x & 31) + (z & 31) * 32));
			int where = in.readInt();
			if (where == 0) throw new IOException("chunk " + name(key) + " isn't in " + file);
			in.seek((long) (where >> 8) * 4096);
			byte[] data = new byte[in.readInt() - 1];
			if (in.readByte() != 2) throw new IOException("chunk " + name(key) + " isn't deflated");
			in.readFully(data);
			NbtCompound level = NbtIo.read(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))).getCompound("Level");
			if (level.getInt("xPos") != x || level.getInt("zPos") != z) throw new IOException("chunk " + name(key) + "'s record is another chunk's");
			return level.contains(PendingTicks.TAG) ? read(level.getList(PendingTicks.TAG)) : null;
		}
	}

	private static void same(String what, Map<Long, List<int[]>> want, Map<Long, List<int[]>> got) {
		Set<Long> keys = new LinkedHashSet<>(want.keySet());
		keys.addAll(got.keySet());
		for (long key : keys) same(what, key, want.get(key), got.get(key));
	}

	private static void same(String what, long key, List<int[]> want, List<int[]> got) {
		int wants = want == null ? 0 : want.size();
		int gots = got == null ? 0 : got.size();
		if (wants != gots) {
			fail(what + ": chunk " + name(key) + " has " + gots + " ticks, not " + wants);
			return;
		}
		for (int i = 0; i < wants; i++) {
			int[] t = want.get(i);
			if ((t[0] >> 4) != x(key) || (t[2] >> 4) != z(key)) fail(what + ": chunk " + name(key) + " expects a tick of another chunk");
			if (!Arrays.equals(t, got.get(i))) {
				fail(what + ": chunk " + name(key) + " tick " + i + " is " + Arrays.toString(got.get(i)) + ", not " + Arrays.toString(t));
				return;
			}
		}
	}

	private static int x(long key) {
		return (int) (key >> 32);
	}

	private static int z(long key) {
		return (int) key;
	}

	private static String name(long key) {
		return x(key) + "," + z(key);
	}

	private static long ms(long since) {
		return (System.nanoTime() - since) / 1_000_000;
	}

	private static void fail(String what) {
		if (++failures <= 20) say("FAIL " + what);
	}

	private static void say(String s) {
		System.out.println("[fixes-scenario] bulk: " + s);
	}
}
