package org.studioquire.oldfaithful.selftest;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityRegistry;
import net.minecraft.entity.mob.MonsterEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * How much a Buried Tower's floor spawns, in the real game: {@code -Doldfaithful.selftest.spawnWatch="cx cz radius floorY floorH ticks;..."} through SceneShot's world. For each floor the player is
 * held inside the floor's own stone under the room (eye in solid stone, so nothing sees or reaches them, and every spawner on the floor is within its 16 blocks), at Normal, kept alive, for the
 * ticks given; every monster that appears in the tower's column is counted by kind and floor, and each spawner's share and the block light round it go into {@code spawns.txt}. Monsters in the
 * column are cleared between floors.
 */
final class SpawnWatch {
	static final String WATCH = given("oldfaithful.selftest.spawnWatch");
	static final boolean ENABLED = WATCH != null;
	private static final int SETTLE = 200;

	private final File out;
	private final SelfTest results;
	private final String[] floors;
	private final StringBuilder report = new StringBuilder();
	private int floor = -1, held;
	private int cx, cz, radius, fy, fh, ticks;
	private double px, pz;
	private final Map<Integer, String> seen = new HashMap<>();
	private final List<int[]> spawners = new ArrayList<>();
	private final List<String> spawnerKinds = new ArrayList<>();
	private int[] perSpawner;
	private final Map<String, Integer> here = new TreeMap<>(), elsewhere = new TreeMap<>();
	private int mostAlive, firstAt = -1;

	SpawnWatch(String out, SelfTest results) {
		this.out = new File(out);
		this.results = results;
		floors = WATCH.split(";");
	}

	private static String given(String property) {
		String v = System.getProperty(property);
		return v == null || v.trim().isEmpty() ? null : v;
	}

	/** One game tick; true when every floor's been watched. */
	boolean tick(Minecraft mc) {
		mc.options.hideHud = true;
		mc.options.difficulty = 2;
		World w = mc.world;
		if (floor < 0 || held >= SETTLE + ticks) {
			if (floor >= 0) finish(w);
			floor++;
			if (floor >= floors.length) {
				write();
				return true;
			}
			begin(mc, floors[floor].trim().split("\\s+"));
		}
		hold(mc.player);
		held++;
		if (held == SETTLE) {
			clear(w);
			survey(w);
			seen.clear();
			for (Object o : w.entities) seen.put(((Entity) o).id, "before");
		} else if (held > SETTLE) {
			count(w, held - SETTLE);
		}
		return false;
	}

	private void begin(Minecraft mc, String[] v) {
		cx = Integer.parseInt(v[0]);
		cz = Integer.parseInt(v[1]);
		radius = Integer.parseInt(v[2]);
		fy = Integer.parseInt(v[3]);
		fh = Integer.parseInt(v[4]);
		ticks = Integer.parseInt(v[5]);
		held = 0;
		px = cx + 0.5;
		pz = cz + 0.5;
		hold(mc.player);
		// The tower's spawners and chests go in as its chunks are decorated, which wants their neighbours: everything round it is made now, not left to the render distance.
		for (int x = cx - radius - 32 >> 4; x <= cx + radius + 32 >> 4; x++) {
			for (int z = cz - radius - 32 >> 4; z <= cz + radius + 32 >> 4; z++) mc.world.getChunk(x, z);
		}
	}

	private void hold(ClientPlayerEntity p) {
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.air = 300;
		// The client's player stands with y at the eye: feet two under the floor's air, eye in its top stone.
		p.setPositionAndAngles(px, fy - 2 + 1.62, pz, 0, 0);
	}

	private boolean inColumn(Entity e) {
		return Math.abs(e.x - (cx + 0.5)) <= radius + 1 && Math.abs(e.z - (cz + 0.5)) <= radius + 1;
	}

	private void clear(World w) {
		for (Object o : w.entities) {
			Entity e = (Entity) o;
			if (e instanceof MonsterEntity && inColumn(e)) e.markDead();
		}
	}

	/** The floor's spawners, and the column under the room nearest their middle with stone two deep, which is where the player goes. */
	private void survey(World w) {
		spawners.clear();
		spawnerKinds.clear();
		here.clear();
		elsewhere.clear();
		mostAlive = 0;
		firstAt = -1;
		double sx = 0, sz = 0;
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				for (int y = fy; y < fy + fh; y++) {
					if (w.getBlockId(x, y, z) != Block.SPAWNER.id) continue;
					spawners.add(new int[] {x, y, z});
					BlockEntity be = w.getBlockEntity(x, y, z);
					spawnerKinds.add(be instanceof MobSpawnerBlockEntity ? ((MobSpawnerBlockEntity) be).getSpawnedEntityId() : "?");
					sx += x;
					sz += z;
				}
			}
		}
		perSpawner = new int[spawners.size()];
		double tx = spawners.isEmpty() ? cx : sx / spawners.size(), tz = spawners.isEmpty() ? cz : sz / spawners.size();
		double best = Double.MAX_VALUE;
		for (int x = cx - radius + 3; x <= cx + radius - 3; x++) {
			for (int z = cz - radius + 3; z <= cz + radius - 3; z++) {
				if (!Block.BLOCKS_OPAQUE[w.getBlockId(x, fy - 1, z)] || !Block.BLOCKS_OPAQUE[w.getBlockId(x, fy - 2, z)]) continue;
				double d = (x - tx) * (x - tx) + (z - tz) * (z - tz);
				if (d < best) {
					best = d;
					px = x + 0.5;
					pz = z + 0.5;
				}
			}
		}
	}

	private int floorOf(double y) {
		for (int i = 0; i < floors.length; i++) {
			String[] v = floors[i].trim().split("\\s+");
			int y0 = Integer.parseInt(v[3]), h = Integer.parseInt(v[4]);
			if (y >= y0 - 2 && y < y0 + h) return i;
		}
		return -1;
	}

	private void count(World w, int tick) {
		int alive = 0;
		for (Object o : w.entities) {
			Entity e = (Entity) o;
			if (!(e instanceof MonsterEntity) || !inColumn(e)) continue;
			boolean onFloor = e.y >= fy - 0.5 && e.y < fy + fh;
			if (onFloor) alive++;
			if (seen.containsKey(e.id)) continue;
			String kind = EntityRegistry.getId(e);
			seen.put(e.id, kind);
			if (onFloor) {
				here.merge(kind, 1, Integer::sum);
				if (firstAt < 0) firstAt = tick;
				int nearest = -1;
				double nd = 36;
				for (int i = 0; i < spawners.size(); i++) {
					int[] s = spawners.get(i);
					double d = (e.x - s[0] - 0.5) * (e.x - s[0] - 0.5) + (e.z - s[2] - 0.5) * (e.z - s[2] - 0.5);
					if (d < nd && Math.abs(e.y - s[1]) < 2.5) {
						nd = d;
						nearest = i;
					}
				}
				if (nearest >= 0) perSpawner[nearest]++;
			} else {
				int f = floorOf(e.y);
				elsewhere.merge(kind + "@" + (f < 0 ? "out" : "f" + f), 1, Integer::sum);
			}
		}
		mostAlive = Math.max(mostAlive, alive);
	}

	private void finish(World w) {
		int total = 0;
		for (int n : here.values()) total += n;
		double minutes = ticks / 1200.0;
		StringBuilder line = new StringBuilder();
		line.append(String.format(Locale.ROOT, "floor y=%d h=%d (watch %d): %d spawners, %d monsters appeared on it in %.1f game minutes (%.1f a minute), most alive at once %d, first after %s ticks; %s",
				fy, fh, floor, spawners.size(), total, minutes, total / minutes, mostAlive, firstAt < 0 ? "-" : String.valueOf(firstAt), here));
		line.append("\n    player at ").append((int) Math.floor(px)).append(',').append(fy - 2).append(',').append((int) Math.floor(pz));
		for (int i = 0; i < spawners.size(); i++) {
			int[] s = spawners.get(i);
			int lowest = 99, highest = 0, cells = 0, sum = 0;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = 0; dy <= 1; dy++) {
						int x = s[0] + dx, y = s[1] + dy, z = s[2] + dz;
						if (w.getBlockId(x, y, z) != 0) continue;
						int l = w.getLightLevel(x, y, z);
						lowest = Math.min(lowest, l);
						highest = Math.max(highest, l);
						sum += l;
						cells++;
					}
				}
			}
			double far = Math.sqrt((s[0] + 0.5 - px) * (s[0] + 0.5 - px) + (s[1] + 0.5 - (fy - 0.38)) * (s[1] + 0.5 - (fy - 0.38)) + (s[2] + 0.5 - pz) * (s[2] + 0.5 - pz));
			line.append(String.format(Locale.ROOT, "\n    spawner %s at %d,%d,%d (%.1f from the player): %d of them; light round it %s",
					spawnerKinds.get(i), s[0], s[1], s[2], far, perSpawner[i], cells == 0 ? "no open cells" : String.format(Locale.ROOT, "%d-%d, mean %.1f over %d cells", lowest, highest, sum / (double) cells, cells)));
		}
		if (!elsewhere.isEmpty()) line.append("\n    elsewhere in the tower meanwhile: ").append(elsewhere);
		report.append(line).append('\n');
		results.pass("spawn watch: " + line.toString().split("\n")[0]);
		clear(w);
	}

	private void write() {
		try {
			out.mkdirs();
			Files.write(new File(out, "spawns.txt").toPath(), ("seed " + SceneShot.SEED + "\n" + report).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
