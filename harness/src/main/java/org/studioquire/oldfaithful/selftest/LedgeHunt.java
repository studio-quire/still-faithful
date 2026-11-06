package org.studioquire.oldfaithful.selftest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * The ledge hunt (`:harness:ledgeHunt`): looks for the player moving into a block and being pushed out, on the real
 * client's physics. The real {@link ClientPlayerEntity} runs through {@code World.updateEntity} with a scripted
 * {@link Input} in place of the keyboard; nothing of the movement is reimplemented. Every tick (the game's own and the
 * extra ones run here to go faster) is observed: the box against every solid block, the push-out acting, the eye against
 * block faces, position set from outside.
 *
 * <p>Families ({@code ledgeRuns}, e.g. "A=200000,W=500"), each deterministic from the seed, cell by cell:
 * <ul>
 * <li>A: short approaches to a built ledge (enclosed scene at chosen coordinates), each started by placing the player.
 * <li>W: long wanders across a scene with no placing after the first tick.
 * <li>S: pressed against something, then the player written to NBT and read back (what a load does), then pressing on.
 * <li>R: the same with a real save, quit and reopen of the world, the ticks after it the game's own at its own pace.
 * <li>D: straight walks at full speed: across a one-wide trench under a roof 2 above the floor, off a ledge with a roof
 * 2 above its top and back again, with their open-air and 3-high controls.
 * <li>E: square-on to a ledge, forward n ticks, nothing for a gap, then back, with the start swept in small steps; roof 2
 * above the ledge top, 3 above, none (cell index mod 3), every (n, gap) from 1..14 by 0..3.
 * <li>F: the same taps as E under less roof: one block 2 above the ledge's top over the low side beside the edge, a row of
 * them along the edge on the low side, a row on the ledge's own side.
 * <li>Q: walk square-on across a power-of-two coordinate (which leaves the box's width a rounding step off) to a ledge's face
 * a block past it, on until touching, then write and read the player as S does. P: the same with a real save, quit and reopen.
 * <li>U: as Q, against a wall 4 high, so the eye can reach it. T: the same with a real save, quit and reopen (what #68 reported).
 * V: real rejoins against a 4-high wall at arbitrary places, half near the world spawn and half far from it.
 * <li>H: straight at something low, with and without a roof low enough to clip the rise: can the step still be made, and is it the same?
 * <li>N: real terrain, nothing edited, the game's own ticks at its own pace.
 * </ul>
 * {@code ledgeOnly=F:cell:run} replays a single run and writes every tick to {@code trace.txt}.
 */
public final class LedgeHunt {
	public static final boolean ENABLED = System.getProperty("oldfaithful.selftest.ledge") != null;
	static final long SEED = Long.getLong("oldfaithful.selftest.ledgeSeed", 1903L);
	private static final String RUNS = System.getProperty("oldfaithful.selftest.ledgeRuns", "A=20000");
	private static final String ONLY = System.getProperty("oldfaithful.selftest.ledgeOnly");
	/** Only scenes with nothing overhead, for the open-air case (A, W, S and R). */
	private static final boolean OPEN_ONLY = Boolean.getBoolean("oldfaithful.selftest.ledgeOpen");
	/** How many places a family cycles through: each new chunk leaves about 1,600 scheduled ticks, which only real ticks drain, so a long run must keep this low. */
	private static final int BASES = Integer.getInteger("oldfaithful.selftest.ledgeBases", 64);
	private static final int VIEW = Integer.getInteger("oldfaithful.selftest.ledgeView", -1);
	private static final String WORLD = "oldfaithful-startup";
	private static final long BUDGET_NS = 250_000_000L;
	private static final int RING = 24;
	private static final int QUOTA = 6;
	private static final int R = 7, TOP = 7, DOWN = -2;
	private static final int STONE = 1, SLAB = 44, STAIRS = 67, SNOW = 78, ICE = 79, SOUL_SAND = 88, GLASS = 20, CHEST = 54, FENCE = 85, PLANKS = 5;

	private static LedgeHunt instance;
	/** The first GENERATED are what A, W, S and R draw from; the rest are only for their own families. */
	private static final String[] TYPES = {"ledge", "cornerOut", "cornerIn", "pillar", "stairs", "trench", "hole", "wall", "lowstep"};
	private static final int GENERATED = 7, WALL = 7, LOWSTEP = 8;
	private static final int REPEATER = 93, TRAPDOOR = 96;
	/** H's scenes: name, block under the feet (0 for none: the floor is the stone below), block ahead, roof's height above the scene's floor, the top the step should end on. */
	private static final Object[][] LOW_STEPS = {
			{"repeater|open", 0, REPEATER, 0, 0.125},
			{"repeater|roof2", 0, REPEATER, 2, 0.125},
			{"trapdoor|open", 0, TRAPDOOR, 0, 0.1875},
			{"trapdoor|roof2", 0, TRAPDOOR, 2, 0.1875},
			{"slab|open", 0, SLAB, 0, 0.5},
			{"slab|roof2", 0, SLAB, 2, 0.5},
			{"slab|roof3", 0, SLAB, 3, 0.5},
			{"stairs|open", 0, STAIRS, 0, 0.5},
			{"stairs|roof2", 0, STAIRS, 2, 0.5},
			{"stairs|roof3", 0, STAIRS, 3, 0.5},
			{"soulsandToStone|open", SOUL_SAND, STONE, 0, 1.0},
			{"soulsandToStone|roof3", SOUL_SAND, STONE, 3, 1.0},
			{"slabToStone|open", SLAB, STONE, 0, 1.0},
			{"slabToStone|roof3", SLAB, STONE, 3, 1.0},
	};
	private static final String[] CEILS = {"open", "flat3", "flat4", "flat2", "stepped2", "overhang", "speck", "lowBlock", "lowRow", "highRow"};
	private static final String[] MATS = {"stone", "slab", "stairs", "soulsand", "ice", "glass", "chest", "fence", "snowcap"};

	private final SelfTest results;
	private final Path out;
	private BufferedWriter events, trace, digests, episodes;
	private final List<String> families = new ArrayList<>();
	private final Map<String, Integer> quotas = new TreeMap<>();
	private int familyIndex = -1;
	private String family;
	private int cellIndex = -1, runIndex, runsDone, runsWanted;
	private Cell cell;
	private Cell built;
	private long loadedBaseKey = Long.MIN_VALUE;
	private Run run;
	private final ScriptInput scriptInput = new ScriptInput();
	private int spawnX, spawnZ;
	private boolean started;
	private long startedAt, lastReport;
	private int idleLoops;
	private String onlyFamily;
	private int onlyCell, onlyRun;

	// Observations.
	private final Map<String, long[]> counters = new TreeMap<>();
	private final Map<String, Integer> written = new TreeMap<>();
	private long digest = 0xcbf29ce484222325L, cellDigest = 0xcbf29ce484222325L;
	private final double[][] ring = new double[RING][32];
	private final String[] ringNote = new String[RING];
	private int ringAt, ringSize;
	private Episode episode;
	private long ticksTotal;
	private long episodeLines;
	private double deepest;

	// Set by the push-out's hook during the tick; read at its end.
	private int pushEntered, pushChanged;
	private double vxHead, vzHead;
	private String pushNote = "";
	private int outsidePlacements;
	private int groundlessRun;
	private String placementNote = "";
	private boolean placing;
	private double headMinY;
	private int lastHealth = -1;
	private long rejoinedAt = -1;
	private final long[] rejoinWait = new long[3];

	public LedgeHunt(SelfTest results) {
		this.results = results;
		this.out = Paths.get(System.getProperty("oldfaithful.selftest.ledge"));
		instance = this;
		if (ONLY != null) {
			String[] p = ONLY.split(":");
			onlyFamily = p[0];
			onlyCell = Integer.parseInt(p[1]);
			onlyRun = Integer.parseInt(p[2]);
			families.add(onlyFamily);
			quotas.put(onlyFamily, 1);
		} else {
			for (String part : RUNS.split(",")) {
				String[] kv = part.split("=");
				families.add(kv[0]);
				quotas.put(kv[0], Integer.parseInt(kv[1]));
			}
		}
	}

	// ---- hooks ----

	/** The script's keys, as KeyboardInput would set them. */
	private final class ScriptInput extends Input {
		@Override
		public void update(PlayerEntity player) {
			Run r = run;
			movementSideways = 0.0F;
			movementForward = 0.0F;
			jumping = false;
			sneaking = false;
			if (r != null && !r.finished) r.keys(this, player);
			if (sneaking) {
				movementSideways = (float) (movementSideways * 0.3);
				movementForward = (float) (movementForward * 0.3);
			}
		}
	}

	public static void tickHead(ClientPlayerEntity player) {
		if (instance != null) instance.head(player);
	}

	public static void tickEnd(ClientPlayerEntity player) {
		if (instance != null) instance.observe(player);
	}

	public static void pushHead(ClientPlayerEntity player, double x, double y, double z) {
		if (instance == null) return;
		instance.vxHead = player.velocityX;
		instance.vzHead = player.velocityZ;
		int cx = MathHelper.floor(x), cy = MathHelper.floor(y), cz = MathHelper.floor(z);
		if (player.world.isPosLoaded(cx, 64, cz) && (player.world.shouldSuffocate(cx, cy, cz) || player.world.shouldSuffocate(cx, cy + 1, cz))) {
			instance.pushEntered++;
			instance.pushNote += " in[" + x + "," + y + "," + z + "]";
		}
	}

	public static void pushEnd(ClientPlayerEntity player) {
		if (instance == null) return;
		if (player.velocityX != instance.vxHead || player.velocityZ != instance.vzHead) {
			instance.pushChanged++;
			instance.pushNote += " v[" + instance.vxHead + "," + instance.vzHead + "->" + player.velocityX + "," + player.velocityZ + "]";
		}
	}

	/** Position set from anywhere but this hunt's own placing of the player. */
	public static void placed(Entity entity, double x, double y, double z) {
		if (instance == null || instance.placing || !(entity instanceof ClientPlayerEntity)) return;
		instance.outsidePlacements++;
		StackTraceElement[] st = new Throwable().getStackTrace();
		StringBuilder b = new StringBuilder();
		for (int i = 2; i < Math.min(7, st.length); i++) b.append(st[i].getMethodName()).append('<');
		instance.placementNote = b.toString();
	}

	// ---- the driver ----

	public boolean tick(Minecraft mc) {
		try {
			if (!started) start(mc);
			mc.world.difficulty = 0;
			mc.options.difficulty = 0;
			long end = System.nanoTime() + BUDGET_NS;
			report(mc, false);
			do {
				if (run == null || run.finished) {
					if (!nextRun(mc)) {
						finish(mc);
						return true;
					}
				}
				if (run.reloadWanted) {
					reload(mc);
					return false;
				}
				if (run.realTicksOnly) return false;
				long before = run.tick;
				mc.world.updateEntity(mc.player);
				Box.resetCacheCount();
				Vec3d.resetCacheCount();
				if (run.tick == before) {
					if (++idleLoops > 10000) {
						count(key("noTick"), 0);
						note("a run stopped ticking: " + run.describe() + " at " + mc.player.x + " " + mc.player.y + " " + mc.player.z);
						run.finished = true;
						idleLoops = 0;
					}
				} else {
					idleLoops = 0;
				}
			} while (System.nanoTime() < end);
			return false;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void start(Minecraft mc) throws IOException {
		started = true;
		startedAt = lastReport = System.currentTimeMillis();
		Files.createDirectories(out);
		events = Files.newBufferedWriter(out.resolve("events.txt"), StandardCharsets.UTF_8);
		digests = Files.newBufferedWriter(out.resolve("digests.txt"), StandardCharsets.UTF_8);
		episodes = Files.newBufferedWriter(out.resolve("episodes.tsv"), StandardCharsets.UTF_8);
		episodes.write("id\tscene\ttier\tmaxDepth\tfirstTick\tticks\teyeBefore\teyeLowest\teyeDrop\tworstOneTickEyeDrop\tpushEntered\tpushChanged\tminEyeClearance\tunloaded\n");
		if (ONLY != null) trace = Files.newBufferedWriter(out.resolve("trace.txt"), StandardCharsets.UTF_8);
		mc.options.soundVolume = 0.0F;
		mc.options.musicVolume = 0.0F;
		mc.options.fpsLimit = 2;
		if (VIEW >= 0) {
			mc.options.viewDistance = VIEW;
			mc.worldRenderer.reload();
		}
		spawnX = mc.world.getSpawnPos().x;
		spawnZ = mc.world.getSpawnPos().z;
		quiet(mc);
		swapIntervalIsOff("at the start");
	}

	/** Hidden clients waiting on the refresh have stalled the display; a long hunt must not. */
	private void swapIntervalIsOff(String when) {
		java.nio.IntBuffer interval = org.lwjgl.BufferUtils.createIntBuffer(1);
		boolean readable = org.lwjgl.sdl.SDLVideo.SDL_GL_GetSwapInterval(interval);
		results.check("ledge hunt: swap interval " + (readable ? interval.get(0) : "unreadable") + " " + when, readable && interval.get(0) == 0);
	}

	private void quiet(Minecraft mc) {
		World w = mc.world;
		w.allowSpawning(false, false);
		w.getProperties().setRaining(false);
		w.getProperties().setThundering(false);
		w.getProperties().setRainTime(Integer.MAX_VALUE / 2);
		w.getProperties().setThunderTime(Integer.MAX_VALUE / 2);
		mc.player.input = scriptInput;
	}

	private boolean nextRun(Minecraft mc) throws IOException {
		if (run != null) endRun();
		if (family == null || runsDone >= runsWanted) {
			if (family != null) closeFamily();
			if (++familyIndex >= families.size()) return false;
			family = families.get(familyIndex);
			runsWanted = quotas.get(family);
			runsDone = 0;
			cellIndex = -1;
			runIndex = Integer.MAX_VALUE;
			digest = 0xcbf29ce484222325L;
		}
		if (family.equals("N")) {
			cell = null;
			run = new Run(new Random(mix(SEED, 'N', runsDone, 0)), mc, 0);
			runsDone++;
			return true;
		}
		int per = perCell(family);
		if (ONLY != null) {
			cellIndex = onlyCell;
			runIndex = onlyRun;
		} else if (runIndex >= per - 1) {
			if (cell != null) writeCellDigest();
			cellIndex++;
			runIndex = 0;
		} else {
			runIndex++;
		}
		cell = new Cell(family, cellIndex, spawnX, spawnZ);
		prepare(mc, cell);
		run = new Run(new Random(mix(SEED, family.charAt(0), cellIndex, runIndex)), mc, runIndex);
		runsDone++;
		return true;
	}

	private void writeCellDigest() throws IOException {
		digests.write(family + " " + cellIndex + " " + Long.toHexString(cellDigest) + " " + cell.describe() + "\n");
		cellDigest = 0xcbf29ce484222325L;
	}

	private static int perCell(String family) {
		switch (family) {
			case "A": return 150;
			case "S": return 100;
			case "D": return 150;
			case "E": case "F": return 400;
			case "Q": case "U": return 100;
			case "H": return 50;
			default: return 1;
		}
	}

	private static long mix(long a, long b, long c, long d) {
		long h = a * 0x9E3779B97F4A7C15L + b;
		h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L + c;
		h = (h ^ (h >>> 32)) * 0x94D049BB133111EBL + d;
		return h ^ (h >>> 31);
	}

	/** Loads the base's surroundings once, and writes the cell's blocks where they differ. Never touches light or neighbours. */
	private void prepare(Minecraft mc, Cell c) {
		World w = mc.world;
		long baseKey = ((long) c.bx << 32) ^ (c.bz & 0xffffffffL);
		if (baseKey != loadedBaseKey) {
			loadedBaseKey = baseKey;
			built = null;
			int cx = c.bx >> 4, cz = c.bz >> 4;
			for (int i = -4; i <= 4; i++) {
				for (int j = -4; j <= 4; j++) w.getChunk(cx + i, cz + j);
			}
		}
		// Generation adds animals as neighbouring chunks load, and one walking in would make runs differ between launches.
		for (Iterator<?> it = w.entities.iterator(); it.hasNext(); ) {
			Entity e = (Entity) it.next();
			if (!(e instanceof PlayerEntity)) e.markDead();
		}
		if (built != null && built.sameBlocks(c)) return;
		for (int wx = -R; wx <= R; wx++) {
			for (int wz = -R; wz <= R; wz++) {
				int x = c.bx + wx, z = c.bz + wz;
				Chunk chunk = w.getChunk(x >> 4, z >> 4);
				for (int ry = DOWN - 1; ry <= TOP + 1; ry++) {
					int y = c.by + ry;
					if (y < 0 || y >= 128) continue;
					int id = c.block(wx, ry, wz);
					int index = (x & 15) << 11 | (z & 15) << 7 | y;
					if ((chunk.blocks[index] & 255) != id) {
						chunk.blocks[index] = (byte) id;
						chunk.dirty = true;
					}
					int meta = c.meta(wx, ry, wz);
					if (chunk.meta.get(x & 15, y, z & 15) != meta) {
						chunk.meta.set(x & 15, y, z & 15, meta);
						chunk.dirty = true;
					}
				}
			}
		}
		built = c;
	}

	private void place(ClientPlayerEntity p, double x, double y, double z, float yaw, float pitch) {
		// Death shrinks the box and the eye; a run that ended that way mustn't hand them on.
		p.standingEyeHeight = 1.62F;
		((org.studioquire.oldfaithful.selftest.mixin.LedgeHuntEntityAccessor) p).oldfaithful$setSize(0.6F, 1.8F);
		p.deathTime = 0;
		placing = true;
		p.setPosition(x, y, z);
		placing = false;
		p.prevX = p.lastTickX = x;
		p.prevY = p.lastTickY = y;
		p.prevZ = p.lastTickZ = z;
		p.velocityX = p.velocityY = p.velocityZ = 0.0;
		p.yaw = p.prevYaw = yaw;
		p.pitch = p.prevPitch = pitch;
		((org.studioquire.oldfaithful.selftest.mixin.LedgeHuntEntityAccessor) p).oldfaithful$setFallDistance(0.0F);
		p.cameraOffset = 0.0F;
		p.onGround = false;
		p.health = 20;
		p.fireTicks = 0;
		p.air = 300;
		p.hurtTime = 0;
		p.dead = false;
		p.horizontalCollision = false;
		p.verticalCollision = false;
	}

	private void reload(Minecraft mc) throws IOException {
		count(key("rejoins"), 0);
		String id = run.describe();
		run.reloadWanted = false;
		run.note = "REJOIN";
		long t0 = System.currentTimeMillis();
		placing = false;
		mc.setWorld(null);
		mc.startGame(WORLD, "Old Faithful start-up test", 0L);
		mc.setScreen(null);
		quiet(mc);
		run.realTicksOnly = true;
		loadedBaseKey = Long.MIN_VALUE;
		rejoinedAt = mc.world.getTime();
		lastHealth = -1;
		note("rejoined for " + id + " in " + (System.currentTimeMillis() - t0) + " ms; player " + mc.player.x + " " + mc.player.y + " " + mc.player.z);
	}

	// ---- one run ----

	private final class Run {
		final Random r;
		final int index;
		long tick;
		boolean finished, reloadWanted, realTicksOnly;
		String note = "";
		int settle;
		int length;
		float yaw, pitch;
		float forward, strafe;
		boolean sneak, jump;
		int segmentEnd;
		// Plan of a segment.
		int turnAt, stopAt, stopFor, jumpMode, jumpAt, jumpFor, sneakMode, sneakFrom, sneakTo, jumpDelay;
		float sigma, sweep;
		boolean turnByMouse;
		int contact;
		int pressedAt = -1;
		boolean hit;
		int stuckTicks;

		Run(Random r, Minecraft mc, int index) {
			this.r = r;
			this.index = index;
			if (family.equals("N")) {
				length = Integer.MAX_VALUE;
				settle = 0;
				realTicksOnly = true;
				segmentNatural();
				mc.player.input = scriptInput;
				return;
			}
			length = family.equals("W") ? 6000 : 30 + r.nextInt(60);
			if (family.equals("S") || family.equals("R") || family.equals("Q") || family.equals("P") || family.equals("U") || family.equals("T") || family.equals("V")) length = 200;
			settle = 4;
			placeAtStart(mc.player);
			if (family.equals("W")) segment();
			else if (family.equals("A")) plan(0, length);
			else if (family.equals("D")) directed(mc.player);
			else if (family.equals("E") || family.equals("F")) tap(mc.player);
			else if (family.equals("Q") || family.equals("P") || family.equals("U") || family.equals("T") || family.equals("V")) wallApproach(mc.player);
			else if (family.equals("H")) lowStep(mc.player);
			else {
				// Pressing: aimed at the middle of the scene, walking on.
				forward = 1.0F;
				sigma = r.nextInt(3) == 0 ? 1.0F : 0.0F;
				jumpMode = r.nextInt(4) == 0 ? 3 : 0;
				jumpDelay = 0;
			}
		}

		/** Straight on at full walking speed: across the scene's trench or pit, or off its ledge and, sometimes, straight back. */
		void directed(ClientPlayerEntity p) {
			int o = cell.orient;
			double ux = o == 0 ? 1 : o == 2 ? -1 : 0, uz = o == 1 ? 1 : o == 3 ? -1 : 0;
			boolean ledge = cell.type == 0;
			double d = ledge ? 1.5 + r.nextDouble() * 4.0 : 2.5 + r.nextDouble() * 3.0;
			// A ledge is walked from its top down (against the scene's +x), so that the walk starts high.
			double side = ledge ? 1.0 : -1.0;
			double along = (r.nextDouble() * 2 - 1) * (cell.type == 6 || cell.ceil == 6 ? 0.5 : 4.5);
			double x = cell.bx + 0.5 + ux * d * side + uz * along;
			double z = cell.bz + 0.5 + uz * d * side + ux * along;
			float yawBase = o == 0 ? -90.0F : o == 1 ? 0.0F : o == 2 ? 90.0F : 180.0F;
			if (ledge) yawBase += 180.0F;
			int kind = r.nextInt(10);
			float j = kind < 5 ? 0.0F : kind < 8 ? (float) (r.nextGaussian() * 0.5) : (float) (r.nextGaussian() * 5.0);
			yaw = yawBase + j;
			pitch = 0.0F;
			for (int ry = DOWN; ry <= 5; ry++) {
				double y = cell.by + ry + 0.02 + 1.62F;
				place(p, x, y, z, yaw, pitch);
				if (p.world.getEntityCollisions(p, p.boundingBox).isEmpty()) break;
			}
			forward = 1.0F;
			sigma = 0.0F;
			length = settle + 45;
			turnAt = Integer.MAX_VALUE;
			if (ledge && r.nextBoolean()) {
				// Back again: at about the ticks that take the box over the edge, give or take.
				turnAt = settle + (int) (d / 0.216) + r.nextInt(9) - 2;
				turnByMouse = false;
			}
		}

		/** From the far side of a power of two, straight at the ledge's face a block past it (Cell's Q and P place it so). */
		void wallApproach(ClientPlayerEntity p) {
			int o = cell.orient;
			double ux = o == 0 ? 1 : o == 2 ? -1 : 0, uz = o == 1 ? 1 : o == 3 ? -1 : 0;
			double back = 3.5 + r.nextDouble() * 1.5;
			double along = (r.nextDouble() * 2 - 1) * 4.0;
			double x = cell.bx + 0.5 - ux * (0.5 + back) + uz * along;
			double z = cell.bz + 0.5 - uz * (0.5 + back) + ux * along;
			yaw = o == 0 ? -90.0F : o == 1 ? 0.0F : o == 2 ? 90.0F : 180.0F;
			pitch = 0.0F;
			place(p, x, cell.by + 0.02 + 1.62F, z, yaw, pitch);
			forward = 1.0F;
			sigma = 0.0F;
			jumpMode = 0;
			length = 200;
		}

		/** Straight at the low thing from its low side, a little off square some of the time. */
		void lowStep(ClientPlayerEntity p) {
			wallApproach(p);
			int under = (Integer) LOW_STEPS[cell.lowStep][1];
			float floorTop = under == SOUL_SAND ? 0.875F : under == SLAB ? 0.5F : 0.0F;
			if (index % 2 == 1) yaw += (float) (r.nextGaussian() * 3.0);
			place(p, p.x, cell.by + floorTop + 0.02 + 1.62F, p.z, yaw, pitch);
			length = 50;
		}

		/** Off the ledge's edge square-on and back: forward n ticks, nothing for a gap, then back; the start swept in steps of the walk's own scale. */
		void tap(ClientPlayerEntity p) {
			int o = cell.orient;
			double ux = o == 0 ? 1 : o == 2 ? -1 : 0, uz = o == 1 ? 1 : o == 3 ? -1 : 0;
			// The edge is half a block behind the scene's centre along the ledge's +x: the start is by distance from it.
			double fromEdge = 0.8 + 0.0025 * index + r.nextDouble() * 0.0025 + 0.25 * (cell.index / 3 % 10);
			double d = fromEdge - 0.5;
			double along = cell.ceil == 7 ? (r.nextDouble() - 0.5) * 0.7 : 0.5 + r.nextDouble() * 3.5;
			double x = cell.bx + 0.5 + ux * d + uz * along;
			double z = cell.bz + 0.5 + uz * d + ux * along;
			float yawBase = o == 0 ? 90.0F : o == 1 ? 180.0F : o == 2 ? -90.0F : 0.0F;
			yaw = yawBase;
			pitch = 0.0F;
			for (int ry = DOWN; ry <= 5; ry++) {
				double y = cell.by + ry + 0.02 + 1.62F;
				place(p, x, y, z, yaw, pitch);
				if (p.world.getEntityCollisions(p, p.boundingBox).isEmpty()) break;
			}
			boolean exact = r.nextBoolean();
			int n = exact ? 11 : 1 + r.nextInt(14), gap = exact ? 2 : r.nextInt(4);
			forward = 1.0F;
			sigma = 0.0F;
			stopAt = settle + n;
			stopFor = gap;
			turnAt = settle + n + gap;
			turnByMouse = false;
			length = settle + n + gap + 25;
		}

		void placeAtStart(ClientPlayerEntity p) {
			boolean near = family.equals("S") || family.equals("R");
			for (int attempt = 0; attempt < 200; attempt++) {
				double range = near ? 2.6 : 4.5;
				double x = cell.bx + 0.5 + (r.nextDouble() * 2 - 1) * range;
				double z = cell.bz + 0.5 + (r.nextDouble() * 2 - 1) * range;
				for (int ry = DOWN; ry <= 5; ry++) {
					double y = cell.by + ry + 0.05 + r.nextDouble() * 0.2 + 1.62F;
					place(p, x, y, z, 0.0F, 0.0F);
					if (p.world.getEntityCollisions(p, p.boundingBox).isEmpty()) {
						float yaw = aim(p.x, p.z);
						place(p, x, y, z, yaw, r.nextFloat() * 180.0F - 90.0F);
						this.yaw = yaw;
						this.pitch = p.pitch;
						return;
					}
				}
			}
			throw new IllegalStateException("no start place in " + cell.describe());
		}

		float aim(double x, double z) {
			int kind = r.nextInt(10);
			if (kind < 5 || family.equals("S") || family.equals("R")) {
				double tx = cell.bx + 0.5 + (r.nextDouble() * 2 - 1) * 2.0;
				double tz = cell.bz + 0.5 + (r.nextDouble() * 2 - 1) * 2.0;
				return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
			}
			if (kind < 8) {
				float j = r.nextBoolean() ? 0.0F : (float) (r.nextGaussian() * Math.pow(10, -r.nextInt(4)));
				return r.nextInt(8) * 45.0F + j;
			}
			return r.nextFloat() * 360.0F;
		}

		/** One stretch of keys, from tick {@code from} for {@code n} ticks. */
		void plan(int from, int n) {
			int k = r.nextInt(100);
			forward = k < 85 ? 1.0F : k < 90 ? -1.0F : 0.0F;
			k = r.nextInt(100);
			strafe = k < 70 ? 0.0F : k < 85 ? 1.0F : -1.0F;
			turnAt = r.nextInt(2) == 0 ? from + r.nextInt(Math.max(1, n)) : Integer.MAX_VALUE;
			turnByMouse = r.nextBoolean();
			stopAt = r.nextInt(3) == 0 ? from + r.nextInt(Math.max(1, n)) : Integer.MAX_VALUE;
			stopFor = 1 + r.nextInt(6);
			k = r.nextInt(100);
			jumpMode = k < 20 ? 0 : k < 40 ? 1 : k < 80 ? 2 : 3;
			jumpAt = from + r.nextInt(Math.max(1, Math.min(n, 30)));
			jumpFor = 1 + r.nextInt(3);
			jumpDelay = r.nextInt(4);
			k = r.nextInt(100);
			sneakMode = k < 75 ? 0 : k < 85 ? 1 : 2;
			sneakFrom = from + r.nextInt(Math.max(1, n));
			sneakTo = sneakFrom + r.nextInt(Math.max(1, n));
			k = r.nextInt(100);
			sigma = k < 50 ? 0.0F : k < 75 ? 1.0F : k < 90 ? 6.0F : 0.0F;
			sweep = k >= 90 ? (r.nextFloat() - 0.5F) * 30.0F : 0.0F;
			contact = 0;
		}

		void segment() {
			int n = 3 + r.nextInt(28);
			segmentEnd = (int) tick + n;
			if (r.nextInt(3) != 0) yaw = aim(mcPlayer().x, mcPlayer().z);
			plan((int) tick, n);
		}

		void segmentNatural() {
			int n = 20 + r.nextInt(100);
			segmentEnd = (int) tick + n;
			yaw += (r.nextFloat() - 0.5F) * (r.nextInt(5) == 0 ? 300.0F : 60.0F);
			forward = r.nextInt(12) == 0 ? 0.0F : 1.0F;
			strafe = r.nextInt(6) == 0 ? (r.nextBoolean() ? 1.0F : -1.0F) : 0.0F;
			sneakMode = r.nextInt(12) == 0 ? 1 : 0;
			jumpMode = 3;
			jumpDelay = r.nextInt(3);
			contact = 0;
			sigma = 0.5F;
		}

		ClientPlayerEntity mcPlayer() {
			return Minecraft.class.cast(net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance()).player;
		}

		void keys(Input in, PlayerEntity p) {
			if (tick < settle) return;
			if (family.equals("N")) {
				if (tick >= segmentEnd) segmentNatural();
				if (p.isSubmergedInWater() || p.isTouchingLava()) in.jumping = true;
			} else if (family.equals("W") && tick >= segmentEnd) {
				segment();
			}
			int t = (int) tick;
			float f = forward, s = strafe;
			if (t == turnAt) {
				if (turnByMouse) yaw += 180.0F;
				else forward = -forward;
				f = forward;
			}
			if (t >= stopAt && t < stopAt + stopFor) f = 0.0F;
			yaw += sweep;
			if (sigma > 0) yaw += (float) (r.nextGaussian() * sigma);
			p.yaw = yaw;
			p.pitch = pitch;
			in.movementForward = f;
			in.movementSideways = s;
			if (p.horizontalCollision) contact++;
			switch (jumpMode) {
				case 1: in.jumping = true; break;
				case 2: in.jumping = t >= jumpAt && t < jumpAt + jumpFor; break;
				case 3: in.jumping |= contact > jumpDelay && contact <= jumpDelay + 3; break;
				default: break;
			}
			switch (sneakMode) {
				case 1: in.sneaking = true; break;
				case 2: in.sneaking = t >= sneakFrom && t < sneakTo; break;
				default: break;
			}
		}

		void afterTick(ClientPlayerEntity p, double depth) {
			tick++;
			if (cell != null && (p.health <= 0 || Math.abs(p.x - cell.bx) > R + 1 || Math.abs(p.z - cell.bz) > R + 1 || p.y < cell.by - 8)) {
				count(key(p.health <= 0 ? "diedInScene" : "leftScene"), 0);
				note((p.health <= 0 ? "died " : "left the scene ") + describe() + " at tick " + tick + ": " + p.x + " " + p.y + " " + p.z);
				finished = true;
				return;
			}
			if (family.equals("H") && tick == length - 1) {
				double top = (Double) LOW_STEPS[cell.lowStep][4];
				count(key(cell.key() + "|" + (p.boundingBox.minY == cell.by + top ? "onTop" : p.boundingBox.minY < cell.by + top ? "below" : "above")), 0);
			}
			if (family.equals("S") || family.equals("R") || family.equals("Q") || family.equals("P") || family.equals("U") || family.equals("T") || family.equals("V")) {
				if (pressedAt < 0) {
					boolean pressed = p.horizontalCollision && p.onGround && tick >= 8;
					if (pressed || tick >= 45) {
						pressedAt = (int) tick;
						if (family.equals("S") || family.equals("Q") || family.equals("U")) {
							Box b = p.boundingBox;
							double a0 = b.minX, a1 = b.minY, a2 = b.minZ, a3 = b.maxX, a4 = b.maxY, a5 = b.maxZ;
							NbtCompound nbt = new NbtCompound();
							p.write(nbt);
							// What a load starts from: a new player, whose camera offset is 0.
							p.cameraOffset = 0.0F;
							placing = true;
							p.read(nbt);
							placing = false;
							count(key("reads"), 0);
							if (pressed) count(key("readsTouching"), 0);
							boolean inside = !p.world.getEntityCollisions(p, p.boundingBox).isEmpty();
							if (inside) count(key(pressed ? "readsTouchingNowInside" : "readsNowInside"), 0);
							note = "READ box moved by " + (b.minX - a0) + "," + (b.minY - a1) + "," + (b.minZ - a2) + " " + (b.maxX - a3) + "," + (b.maxY - a4) + "," + (b.maxZ - a5) + (inside ? " INSIDE" : "");
							if (trace != null) writeTraceLine("READ box " + boxText(p));
							length = pressedAt + 20;
						} else {
							reloadWanted = true;
							length = pressedAt + 60;
						}
					}
				}
			}
			if (tick >= length) finished = true;
			if (family.equals("N")) {
				if (p.health <= 0 || p.dead) {
					count(key("deaths"), 0);
					Minecraft mc = Minecraft.class.cast(net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance());
					mc.respawnPlayer(false, 0);
					mc.player.input = scriptInput;
				}
			}
		}

		String describe() {
			return family + ":" + cellIndex + ":" + index + (cell == null ? "" : " " + cell.describe());
		}
	}

	// ---- observation ----

	private void head(ClientPlayerEntity p) {
		pushEntered = 0;
		pushChanged = 0;
		pushNote = "";
		headMinY = p.boundingBox.minY;
	}

	private final ArrayList<Box> shapes = new ArrayList<>();

	private void observe(ClientPlayerEntity p) {
		Run r = run;
		if (r == null || r.finished) return;
		ticksTotal++;
		World w = p.world;
		Box b = p.boundingBox;
		double depth = 0.0;
		char axis = '-';
		int hitId = 0, hx = 0, hy = 0, hz = 0;
		int unloaded = 0;
		int x0 = MathHelper.floor(b.minX), x1 = MathHelper.floor(b.maxX), y0 = MathHelper.floor(b.minY) - 1, y1 = MathHelper.floor(b.maxY), z0 = MathHelper.floor(b.minZ), z1 = MathHelper.floor(b.maxZ);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!w.isPosLoaded(x, 64, z)) {
					unloaded++;
					continue;
				}
				for (int y = y0; y <= y1; y++) {
					int id = w.getBlockId(x, y, z);
					if (id == 0) continue;
					Block block = Block.BLOCKS[id];
					if (block == null) continue;
					shapes.clear();
					block.addIntersectingBoundingBox(w, x, y, z, b, shapes);
					for (int i = 0; i < shapes.size(); i++) {
						Box s = shapes.get(i);
						double ox = Math.min(b.maxX, s.maxX) - Math.max(b.minX, s.minX);
						double oy = Math.min(b.maxY, s.maxY) - Math.max(b.minY, s.minY);
						double oz = Math.min(b.maxZ, s.maxZ) - Math.max(b.minZ, s.minZ);
						double d = Math.min(ox, Math.min(oy, oz));
						if (d > depth) {
							depth = d;
							axis = d == ox ? 'x' : d == oy ? 'y' : 'z';
							hitId = id;
							hx = x;
							hy = y;
							hz = z;
						}
					}
				}
			}
		}
		double eye = eyeClearance(w, p);
		boolean groundless = p.onGround && !supported(w, p);
		groundlessRun = groundless ? groundlessRun + 1 : 0;

		// Digest of what physics did, independent of who ticked it.
		digestDouble(p.x);
		digestDouble(p.y);
		digestDouble(p.z);
		digestDouble(b.minY);
		digestDouble(p.velocityX);
		digestDouble(p.velocityY);
		digestDouble(p.velocityZ);

		String k = cell == null ? family + "|natural" : family + "|" + cell.key();
		long[] c = counters.computeIfAbsent(k, x -> new long[C_SIZE]);
		c[C_TICKS]++;
		if (depth > 0.0) {
			c[C_OVERLAP_TICKS]++;
			if (depth > deepest) deepest = depth;
			if (depth > c[C_DEEPEST_BITS] / 1e12) c[C_DEEPEST_BITS] = (long) (depth * 1e12);
		}
		if (pushEntered > 0) c[C_PUSH_ENTERED]++;
		if (pushChanged > 0) c[C_PUSH_CHANGED]++;
		if (eye < 0.1) c[C_EYE_NEAR]++;
		if (eye <= 0.0) c[C_EYE_IN]++;
		if (groundless) c[C_GROUNDLESS]++;
		if (unloaded > 0) c[C_UNLOADED]++;
		if (lastHealth >= 0 && p.health < lastHealth) c[C_HURT]++;
		lastHealth = p.health;
		if (rejoinedAt >= 0) {
			long wait = w.getTime() - rejoinedAt;
			rejoinedAt = -1;
			note("first player tick " + wait + " world ticks after the rejoin, " + unloaded + " unloaded columns under the box");
			rejoinWait[0]++;
			rejoinWait[1] += wait;
			rejoinWait[2] = Math.max(rejoinWait[2], wait);
			count(key(unloaded > 0 ? "firstTickOverUnloaded" : "firstTickAllLoaded"), 0);
		}
		if (outsidePlacements > 0) c[C_PLACED]++;

		// Ring of the ticks before.
		double[] row = ring[ringAt];
		row[0] = r.tick;
		row[1] = p.x;
		row[2] = p.y;
		row[3] = p.z;
		row[4] = b.minX;
		row[5] = b.minY;
		row[6] = b.minZ;
		row[7] = b.maxX;
		row[8] = b.maxY;
		row[9] = b.maxZ;
		row[10] = p.velocityX;
		row[11] = p.velocityY;
		row[12] = p.velocityZ;
		row[13] = p.yaw;
		row[14] = p.pitch;
		row[15] = p.cameraOffset;
		row[16] = scriptInput.movementForward;
		row[17] = scriptInput.movementSideways;
		row[18] = scriptInput.jumping ? 1 : 0;
		row[19] = scriptInput.sneaking ? 1 : 0;
		row[20] = p.onGround ? 1 : 0;
		row[21] = p.horizontalCollision ? 1 : 0;
		row[22] = p.verticalCollision ? 1 : 0;
		row[23] = depth;
		row[24] = axis;
		row[25] = hitId;
		row[26] = hx;
		row[27] = hy;
		row[28] = hz;
		row[29] = pushEntered;
		row[30] = pushChanged;
		row[31] = eye;
		String noteNow = (r.note.isEmpty() ? "" : r.note + " ") + pushNote + (outsidePlacements > 0 ? " PLACED<" + placementNote : "") + (unloaded > 0 ? " unloaded=" + unloaded : "") + (groundless ? " groundless" : "");
		ringNote[ringAt] = noteNow;
		r.note = "";
		outsidePlacements = 0;
		ringAt = (ringAt + 1) % RING;
		if (ringSize < RING) ringSize++;

		if (trace != null) writeTraceLine(rowText(row, noteNow));

		boolean interesting = depth > 0.0 || pushEntered > 0 || pushChanged > 0;
		if (interesting || (eye <= 0.05 && episode == null) || unloaded > 0 && episode == null || groundlessRun >= 2 && episode == null) {
			if (episode == null) {
				episode = new Episode(r.describe(), cell == null ? "natural" : cell.key(), c);
				episode.firstTick = r.tick;
				episode.eyeBefore = ringSize >= 2 ? ring[(ringAt - 2 + RING) % RING][2] : p.y;
				episode.eyeLowest = episode.eyeBefore;
				episode.lastEye = episode.eyeBefore;
				for (int i = 0; i < ringSize; i++) {
					int at = (ringAt - ringSize + i + RING) % RING;
					episode.add(rowText(ring[at], ringNote[at]));
				}
			} else {
				episode.add(rowText(row, noteNow));
			}
			if (interesting) episode.quiet = 0;
			episode.update(depth, pushEntered, pushChanged, eye, unloaded, groundless);
			episode.eye(p.y);
		} else if (episode != null) {
			episode.add(rowText(row, noteNow));
			episode.eye(p.y);
			if (++episode.quiet >= 10) { try { closeEpisode(); } catch (IOException e) { throw new UncheckedIOException(e); } }
		}
		r.afterTick(p, depth);
	}

	private static String boxText(ClientPlayerEntity p) {
		Box b = p.boundingBox;
		return "[" + b.minX + "," + b.minY + "," + b.minZ + " " + b.maxX + "," + b.maxY + "," + b.maxZ + "] pos " + p.x + "," + p.y + "," + p.z;
	}

	private void writeTraceLine(String s) {
		try {
			trace.write(s);
			trace.write('\n');
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String rowText(double[] v, String note) {
		StringBuilder s = new StringBuilder(420);
		s.append("t=").append((long) v[0])
				.append(" keys f=").append((float) v[16]).append(" s=").append((float) v[17]).append(v[18] != 0 ? " J" : "").append(v[19] != 0 ? " N" : "")
				.append(" yaw=").append((float) v[13])
				.append(" onG=").append((int) v[20]).append(" hc=").append((int) v[21]).append(" vc=").append((int) v[22])
				.append(" pos=").append(v[1]).append(',').append(v[2]).append(',').append(v[3])
				.append(" box=").append(v[4]).append(',').append(v[5]).append(',').append(v[6]).append(' ').append(v[7]).append(',').append(v[8]).append(',').append(v[9])
				.append(" v=").append(v[10]).append(',').append(v[11]).append(',').append(v[12])
				.append(" cam=").append((float) v[15])
				.append(" depth=").append(v[23]);
		if (v[23] > 0) s.append(" axis=").append((char) v[24]).append(" block=").append((int) v[25]).append('@').append((int) v[26]).append(',').append((int) v[27]).append(',').append((int) v[28]);
		if (v[29] > 0 || v[30] > 0) s.append(" push=").append((int) v[29]).append('/').append((int) v[30]);
		s.append(" eye=").append((float) v[31]);
		if (!note.isEmpty()) s.append(" |").append(note);
		return s.toString();
	}

	/** Distance from the eye to the nearest suffocating cube, up to about 0.25; 0 if inside one. */
	private static double eyeClearance(World w, ClientPlayerEntity p) {
		double ex = p.x, ey = p.y, ez = p.z;
		int cx = MathHelper.floor(ex), cy = MathHelper.floor(ey), cz = MathHelper.floor(ez);
		double fx = ex - cx, fy = ey - cy, fz = ez - cz;
		double best = 1.0;
		int ax = fx < 0.25 ? -1 : 0, bx = fx > 0.75 ? 1 : 0;
		int ay = fy < 0.25 ? -1 : 0, by = fy > 0.75 ? 1 : 0;
		int az = fz < 0.25 ? -1 : 0, bz = fz > 0.75 ? 1 : 0;
		for (int dx = ax; dx <= bx; dx++) {
			for (int dz = az; dz <= bz; dz++) {
				if (!w.isPosLoaded(cx + dx, 64, cz + dz)) continue;
				for (int dy = ay; dy <= by; dy++) {
					if (!w.shouldSuffocate(cx + dx, cy + dy, cz + dz)) continue;
					double gx = dx < 0 ? fx : dx > 0 ? 1 - fx : 0;
					double gy = dy < 0 ? fy : dy > 0 ? 1 - fy : 0;
					double gz = dz < 0 ? fz : dz > 0 ? 1 - fz : 0;
					double d = Math.sqrt(gx * gx + gy * gy + gz * gz);
					if (d < best) best = d;
				}
			}
		}
		return best;
	}

	/** Anything solid right under the box, within a thousandth? */
	private final ArrayList<Box> underShapes = new ArrayList<>();

	private boolean supported(World w, ClientPlayerEntity p) {
		Box b = p.boundingBox;
		Box probe = Box.create(b.minX, b.minY - 0.002, b.minZ, b.maxX, b.minY + 0.002, b.maxZ);
		int x0 = MathHelper.floor(probe.minX), x1 = MathHelper.floor(probe.maxX), z0 = MathHelper.floor(probe.minZ), z1 = MathHelper.floor(probe.maxZ);
		int y0 = MathHelper.floor(probe.minY) - 1, y1 = MathHelper.floor(probe.maxY);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!w.isPosLoaded(x, 64, z)) return true;
				for (int y = y0; y <= y1; y++) {
					int id = w.getBlockId(x, y, z);
					if (id == 0 || Block.BLOCKS[id] == null) continue;
					underShapes.clear();
					Block.BLOCKS[id].addIntersectingBoundingBox(w, x, y, z, probe, underShapes);
					if (!underShapes.isEmpty()) return true;
				}
			}
		}
		return false;
	}

	private void digestDouble(double d) {
		long v = Double.doubleToRawLongBits(d);
		digest = (digest ^ v) * 0x100000001b3L;
		cellDigest = (cellDigest ^ v) * 0x100000001b3L;
	}

	private void endRun() throws IOException {
		closeEpisode();
		// -Pledge.runDigests: which run of a cell differs between two sets of mods, to replay it.
		if (Boolean.getBoolean("oldfaithful.selftest.ledgeRunDigests") && run != null) digests.write("run " + family + ":" + cellIndex + ":" + run.index + " " + Long.toHexString(cellDigest) + "\n");
		ringSize = 0;
		ringAt = 0;
		if (events != null) events.flush();
		if (trace != null) trace.flush();
	}

	private static final int C_TICKS = 0, C_OVERLAP_TICKS = 1, C_PUSH_ENTERED = 2, C_PUSH_CHANGED = 3, C_EYE_NEAR = 4, C_EYE_IN = 5, C_GROUNDLESS = 6, C_UNLOADED = 7, C_PLACED = 8,
			C_EP_ULP = 9, C_EP_SMALL = 10, C_EP_MID = 11, C_EP_DEEP = 12, C_EP_PUSHED = 13, C_RUNS = 14, C_DEEPEST_BITS = 15, C_HURT = 16, C_SIZE = 17;

	private final class Episode {
		final String id, key;
		final long[] c;
		final StringBuilder text = new StringBuilder();
		int rows;
		int quiet;
		double maxDepth;
		int entered, changed, unloaded, groundless;
		double minEye = 1;
		long firstTick;
		double eyeBefore, eyeLowest, lastEye, worstStep;

		void eye(double y) {
			eyeLowest = Math.min(eyeLowest, y);
			worstStep = Math.max(worstStep, lastEye - y);
			lastEye = y;
		}

		Episode(String id, String key, long[] c) {
			this.id = id;
			this.key = key;
			this.c = c;
		}

		void add(String row) {
			if (rows++ < 200) text.append("  ").append(row).append('\n');
		}

		void update(double depth, int pe, int pc, double eye, int unl, boolean gl) {
			maxDepth = Math.max(maxDepth, depth);
			entered += pe;
			changed += pc;
			minEye = Math.min(minEye, eye);
			unloaded += unl;
			if (gl) groundless++;
		}
	}

	private void closeEpisode() throws IOException {
		Episode e = episode;
		episode = null;
		if (e == null) return;
		String tier;
		if (e.maxDepth >= 0.09) {
			tier = "deep";
			e.c[C_EP_DEEP]++;
		} else if (e.maxDepth >= 1e-3) {
			tier = "mid";
			e.c[C_EP_MID]++;
		} else if (e.maxDepth >= 1e-9) {
			tier = "small";
			e.c[C_EP_SMALL]++;
		} else if (e.maxDepth > 0) {
			tier = "ulp";
			e.c[C_EP_ULP]++;
		} else {
			tier = e.unloaded > 0 ? "unloaded" : e.groundless > 0 ? "groundless" : e.minEye <= 0.05 ? "eye" : "other";
		}
		if (e.changed > 0) e.c[C_EP_PUSHED]++;
		if (episodeLines++ < 500_000) {
			episodes.write(e.id + "\t" + e.key + "\t" + tier + "\t" + e.maxDepth + "\t" + e.firstTick + "\t" + e.rows + "\t" + e.eyeBefore + "\t" + e.eyeLowest + "\t" + (e.eyeBefore - e.eyeLowest)
					+ "\t" + e.worstStep + "\t" + e.entered + "\t" + e.changed + "\t" + e.minEye + "\t" + e.unloaded + "\n");
		}
		String quotaKey = family + "|" + e.key + "|" + tier + (e.changed > 0 ? "|pushed" : "");
		int done = written.merge(quotaKey, 1, Integer::sum);
		if (done <= QUOTA) {
			events.write("EPISODE " + e.id + " tier=" + tier + " maxDepth=" + e.maxDepth + " pushEntered=" + e.entered + " pushChanged=" + e.changed + " minEye=" + e.minEye + " unloaded=" + e.unloaded + " groundless=" + e.groundless + "\n");
			events.write(e.text.toString());
		}
	}

	private String key(String name) {
		return family + "|!" + name;
	}

	private void count(String k, int unused) {
		counters.computeIfAbsent(k, x -> new long[C_SIZE])[C_RUNS]++;
	}

	private void note(String s) {
		System.out.println("[ledge-hunt] " + s);
	}

	private void closeFamily() throws IOException {
		if (run != null && cell != null) writeCellDigest();
		digests.write(family + " TOTAL " + Long.toHexString(digest) + "\n");
		digests.flush();
		note("family " + family + " done: " + runsDone + " runs, digest " + Long.toHexString(digest));
		cell = null;
		built = null;
		run = null;
	}

	private void report(Minecraft mc, boolean force) throws IOException {
		long now = System.currentTimeMillis();
		if (!force && now - lastReport < 30_000L) return;
		lastReport = now;
		note("family " + family + ": " + runsDone + "/" + runsWanted + " runs, " + ticksTotal + " ticks, " + (ticksTotal * 1000 / Math.max(1, now - startedAt)) + " ticks/s, deepest " + deepest);
		writeSummary();
	}

	private void writeSummary() throws IOException {
		long[] grand = new long[C_SIZE];
		StringBuilder s = new StringBuilder();
		s.append("family|type|ceil|material\tticks\toverlapTicks\tepUlp\tepSmall\tepMid\tepDeep\tepPushed\tpushEntered\tpushChanged\teyeNear\teyeIn\tgroundless\tunloaded\tplaced\thurt\tdeepest\n");
		for (Map.Entry<String, long[]> e : counters.entrySet()) {
			long[] c = e.getValue();
			s.append(e.getKey());
			for (int i : new int[] {C_TICKS, C_OVERLAP_TICKS, C_EP_ULP, C_EP_SMALL, C_EP_MID, C_EP_DEEP, C_EP_PUSHED, C_PUSH_ENTERED, C_PUSH_CHANGED, C_EYE_NEAR, C_EYE_IN, C_GROUNDLESS, C_UNLOADED, C_PLACED, C_HURT}) {
				s.append('\t').append(c[i]);
			}
			s.append('\t').append(c[C_DEEPEST_BITS] / 1e12).append(e.getKey().contains("|!") ? "\tcount=" + c[C_RUNS] : "").append('\n');
			for (int i = 0; i < C_SIZE; i++) grand[i] += c[i];
		}
		if (rejoinWait[0] > 0) s.append("rejoins: first player tick after, world ticks: mean ").append(rejoinWait[1] / (double) rejoinWait[0]).append(", most ").append(rejoinWait[2]).append('\n');
		s.append("TOTAL");
		for (int i : new int[] {C_TICKS, C_OVERLAP_TICKS, C_EP_ULP, C_EP_SMALL, C_EP_MID, C_EP_DEEP, C_EP_PUSHED, C_PUSH_ENTERED, C_PUSH_CHANGED, C_EYE_NEAR, C_EYE_IN, C_GROUNDLESS, C_UNLOADED, C_PLACED, C_HURT}) {
			s.append('\t').append(grand[i]);
		}
		s.append('\t').append(deepest).append('\n');
		Files.write(out.resolve("summary.txt"), s.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void finish(Minecraft mc) throws IOException {
		if (family != null && run != null) closeFamily();
		report(mc, true);
		swapIntervalIsOff("at the end");
		events.close();
		digests.close();
		episodes.close();
		if (trace != null) trace.close();
		instance = null;
		results.pass("ledge hunt: " + ticksTotal + " ticks; results in " + out);
	}

	/** Natural-terrain runs end by wall clock, set in seconds as their count. */
	static long wallLimitSeconds(int wanted) {
		return wanted;
	}

	public boolean wallDone() {
		return run != null && family != null && family.equals("N") && System.currentTimeMillis() - startedAt > quotas.get("N") * 1000L;
	}

	/** Ends a natural run from outside the tick loop. */
	public void endNatural() {
		if (run != null) run.finished = true;
		runsDone = runsWanted;
	}

	// ---- scenes ----

	private static final int[] SPECIAL = {0, 1, 2, 3, 4, 7, 8, 15, 16, 17, 31, 32, 33, 64, 100, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 2097152};
	private static final int[] HEIGHTS = {64, 64, 64, 64, 63, 63, 65, 62, 66, 70, 32, 31, 33, 16, 8, 4, 100, 118, 3, 119};

	/** One enclosed scene: where, and what's in it; every field follows from the seed, family and index. */
	private static final class Cell {
		final String family;
		final int index;
		final int bx, by, bz, type, orient, ceil, mat;
		int lowStep;

		Cell(String family, int index, int spawnX, int spawnZ) {
			this.family = family;
			this.index = index;
			// Several cells to a base: loading a base is the slow part, and chunks are never unloaded in single player.
			int group = family.equals("R") ? index : (index / 64) % BASES;
			Random rb = new Random(mix(SEED, 'B', group, family.equals("R") ? 1 : family.equals("D") ? 2 : family.equals("E") ? 3 : family.equals("F") ? 4 : 0));
			if (family.equals("H")) {
				Random rh = new Random(mix(SEED, 'H', index, 0));
				bx = rh.nextInt(4000) - 2000;
				bz = rh.nextInt(4000) - 2000;
				by = 64;
				orient = rh.nextInt(4);
				mat = 0;
				ceil = 0;
				type = LOWSTEP;
				lowStep = index % LOW_STEPS.length;
				return;
			}
			if (family.equals("V")) {
				Random rv = new Random(mix(SEED, 'V', index, 0));
				int reach = index % 2 == 0 ? 40 + rv.nextInt(60) : 230 + rv.nextInt(400);
				double a = rv.nextDouble() * 6.283185;
				bx = spawnX + (int) (Math.cos(a) * reach);
				bz = spawnZ + (int) (Math.sin(a) * reach);
				by = 64;
				orient = rv.nextInt(4);
				mat = 0;
				ceil = 0;
				type = WALL;
				return;
			}
			if (family.equals("Q") || family.equals("P") || family.equals("U") || family.equals("T")) {
				Random rq = new Random(mix(SEED, 'Q', family.charAt(0), index));
				int power = 1 << (5 + rq.nextInt(7));
				int boundary = rq.nextBoolean() ? power : -power;
				int o = rq.nextInt(4);
				int other = rq.nextInt(1200) - 600;
				// The face is a block past the boundary, in the walking direction: o 0 and 1 walk toward +, 2 and 3 toward -.
				int base = o < 2 ? boundary + 1 : boundary - 2;
				if (o == 0 || o == 2) {
					bx = base;
					bz = other;
				} else {
					bx = other;
					bz = base;
				}
				by = 64;
				orient = o;
				mat = 0;
				type = family.equals("U") || family.equals("T") ? WALL : 0;
				ceil = 0;
				return;
			}
			if (family.equals("R")) {
				// Half near the world spawn, half far (#68 was about 230 blocks out).
				int reach = index % 2 == 0 ? 40 + rb.nextInt(60) : 260 + rb.nextInt(400);
				double a = rb.nextDouble() * 6.283185;
				bx = spawnX + (int) (Math.cos(a) * reach);
				bz = spawnZ + (int) (Math.sin(a) * reach);
			} else {
				bx = coordinate(rb);
				bz = coordinate(rb);
			}
			Random r = new Random(mix(SEED, 'C', family.charAt(0), index));
			if (family.equals("F")) {
				by = 64;
				orient = r.nextInt(4);
				mat = 0;
				type = 0;
				ceil = 7 + index % 3;
				return;
			}
			if (family.equals("E")) {
				by = 64;
				orient = r.nextInt(4);
				mat = 0;
				type = 0;
				int[] ceils = {1, 2, 0};
				ceil = ceils[index % 3];
				return;
			}
			if (family.equals("D")) {
				by = r.nextInt(5) != 0 ? 64 : HEIGHTS[r.nextInt(HEIGHTS.length)];
				orient = r.nextInt(4);
				mat = 0;
				// Trench or pit under a roof 2 above the floor; a ledge with a roof 2 above its top; then the controls.
				int[][] kinds = {{5, 3}, {5, 3}, {5, 0}, {5, 1}, {6, 3}, {0, 1}, {0, 1}, {0, 0}, {0, 2}, {0, 6}};
				int[] kind = kinds[index % kinds.length];
				type = kind[0];
				ceil = kind[1];
				return;
			}
			by = HEIGHTS[r.nextInt(HEIGHTS.length)];
			type = r.nextInt(10) < 5 ? 0 : r.nextInt(GENERATED);
			orient = r.nextInt(4);
			int c = r.nextInt(100);
			ceil = OPEN_ONLY || c < 55 ? 0 : c < 66 ? 1 : c < 77 ? 2 : c < 85 ? 3 : c < 90 ? 4 : c < 95 ? 5 : 6;
			int m = r.nextInt(100);
			mat = m < 75 ? 0 : 1 + r.nextInt(MATS.length - 1);
		}

		private static int coordinate(Random r) {
			int k = r.nextInt(10);
			if (k < 6) return r.nextInt(6000) - 3000;
			int v = SPECIAL[r.nextInt(SPECIAL.length)];
			return r.nextBoolean() ? v : -v;
		}

		String key() {
			return type == LOWSTEP ? TYPES[type] + "|" + LOW_STEPS[lowStep][0] : TYPES[type] + "|" + CEILS[ceil] + "|" + MATS[mat];
		}

		String describe() {
			return key() + " at " + bx + "," + by + "," + bz + " orient " + orient;
		}

		boolean sameBlocks(Cell o) {
			return bx == o.bx && by == o.by && bz == o.bz && type == o.type && orient == o.orient && ceil == o.ceil && mat == o.mat && lowStep == o.lowStep;
		}

		int rotX(int wx, int wz) {
			switch (orient) {
				case 1: return wz;
				case 2: return -wx;
				case 3: return -wz;
				default: return wx;
			}
		}

		int rotZ(int wx, int wz) {
			switch (orient) {
				case 1: return -wx;
				case 2: return -wz;
				case 3: return wx;
				default: return wz;
			}
		}

		/** Height of the ground's top relative to by at this column, as whole cells of ledge (0 the floor, -1 a dip). */
		int level(int rx, int rz) {
			switch (type) {
				case 0: return rx >= 0 ? 1 : 0;
				case 1: return rx >= 0 && rz >= 0 ? 1 : 0;
				case 2: return rx >= 0 || rz >= 0 ? 1 : 0;
				case 3: return rx == 0 && rz == 0 ? 1 : 0;
				case 4: return rx < 0 ? 0 : rx < 2 ? 1 : rx < 4 ? 2 : 3;
				case 5: return rx == 0 ? -1 : 0;
				case 6: return rx == 0 && rz == 0 ? -1 : 0;
				case WALL: return rx >= 0 ? 4 : 0;
				default: return 0;
			}
		}

		int block(int wx, int ry, int wz) {
			if (Math.abs(wx) == R || Math.abs(wz) == R) return ry <= TOP + 1 ? STONE : 0;
			int rx = rotX(wx, wz), rz = rotZ(wx, wz);
			if (ry < DOWN) return STONE;
			if (type == LOWSTEP) {
				if (ry < 0) return STONE;
				Object[] step = LOW_STEPS[lowStep];
				int roof = (Integer) step[3];
				if (roof != 0 && ry == roof) return STONE;
				if (ry != 0) return 0;
				return rx >= 0 ? (Integer) step[2] : (Integer) step[1];
			}
			int level = level(rx, rz);
			int ceiling = ceiling(rx, rz, level);
			if (ceiling != Integer.MAX_VALUE && ry >= ceiling) return ry == ceiling ? STONE : 0;
			if (ry >= level) {
				if (mat == 8 && ry == level) return SNOW;
				return 0;
			}
			// Solid under the ground's top; what its top layer is depends on the material.
			boolean ledgeCell = ry >= 0;
			switch (mat) {
				case 1: return ledgeCell ? SLAB : STONE;
				case 2: return ledgeCell ? STAIRS : STONE;
				case 3: return SOUL_SAND;
				case 4: return ry == level - 1 ? ICE : STONE;
				case 5: return ledgeCell ? GLASS : STONE;
				case 6: return ledgeCell ? CHEST : STONE;
				case 7: return ledgeCell ? FENCE : STONE;
				default: return STONE;
			}
		}

		int ceiling(int rx, int rz, int level) {
			switch (ceil) {
				case 1: return 3;
				case 2: return 4;
				case 3: return 2;
				case 4: return Math.max(level, 0) + 2;
				case 5: return rx >= 1 ? Math.max(level, 0) + 2 : Integer.MAX_VALUE;
				case 6: return rx >= 0 && rx <= 1 && rz == 0 ? Math.max(level, 0) + 2 : Integer.MAX_VALUE;
				case 7: return rx == -1 && rz == 0 ? 3 : Integer.MAX_VALUE;
				case 8: return rx == -1 ? 3 : Integer.MAX_VALUE;
				case 9: return rx == 0 ? 3 : Integer.MAX_VALUE;
				default: return Integer.MAX_VALUE;
			}
		}

		int meta(int wx, int ry, int wz) {
			if (type == LOWSTEP) {
				// Stairs rising away from the walker; a repeater and a closed trapdoor look the same any way round.
				if (block(wx, ry, wz) == STAIRS) return orient == 0 ? 0 : orient == 1 ? 2 : orient == 2 ? 1 : 3;
				return 0;
			}
			if (mat == 2 && block(wx, ry, wz) == STAIRS) return orient;
			return 0;
		}
	}
}
