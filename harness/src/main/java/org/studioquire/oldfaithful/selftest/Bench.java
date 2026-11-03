package org.studioquire.oldfaithful.selftest;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SingleplayerInteractionManager;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.world.WorldProperties;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;
import org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor;

/**
 * The speed benchmark's in-game half (docs/speed.md), launched by tools/bench.py with
 * {@code -Doldfaithful.bench.out=<dir>}. Works on LWJGL 2 and on our component alike (nothing SDL-specific).
 * <p>
 * From the game's own tick it creates a world on a fixed seed and runs a fixed script: SETTLE (stand at spawn until
 * every render chunk is built), STAND, TURN (a full turn on the spot), FLY (straight along +X at altitude through new
 * terrain). The run loop's hooks (MinecraftBenchMixin and friends) time each frame and its parts; the results go to
 * {@code frames.csv}, {@code gc.csv} and {@code bench.json} in the out folder, and the game stops itself.
 * <p>
 * The script drives the player the way the game would: looking turns per frame as mouse look does, flying is a
 * velocity per tick with noClip, so the camera interpolates as in play. Weather is held clear so runs compare.
 */
public final class Bench {
	public static final String OUT = System.getProperty("oldfaithful.bench.out");
	public static final boolean ENABLED = OUT != null;

	public static final long SEED = Long.getLong("oldfaithful.bench.seed", 404L);
	private static final double SETTLE_TIMEOUT_S = dbl("oldfaithful.bench.settleTimeout", 120);
	private static final double SETTLE_HOLD_S = 2;
	private static final double STAND_S = dbl("oldfaithful.bench.stand", 10);
	private static final double TURN_S = dbl("oldfaithful.bench.turn", 10);
	private static final double FLY_S = dbl("oldfaithful.bench.fly", 30);
	private static final double FLY_SPEED = dbl("oldfaithful.bench.flySpeed", 10); // blocks per second
	private static final double FLY_Y = dbl("oldfaithful.bench.flyY", 110);
	private static final float FLY_PITCH = (float) dbl("oldfaithful.bench.flyPitch", 25); // looking down at the terrain
	/**
	 * "off": swap interval 0, so frame times measure the work, not the display's refresh; "on": 1, as players have it
	 * with the display mod's default; "driver": as the game and its mods leave it.
	 */
	private static final String VSYNC = System.getProperty("oldfaithful.bench.vsync", "off");

	public static final int MENU = 0, SETTLE = 1, STAND = 2, TURN = 3, FLY = 4, DONE = 5;
	private static final String[] PHASES = {"menu", "settle", "stand", "turn", "fly", "done"};

	// Per-frame columns, microseconds unless named otherwise. Preallocated so recording allocates nothing.
	private static final int CAP = 250_000;
	private static final int[] tUs = new int[CAP], frameUs = new int[CAP], tickUs = new int[CAP], lightUs = new int[CAP],
			renderUs = new int[CAP], compileUs = new int[CAP], genUs = new int[CAP], swapUs = new int[CAP], gpuUs = new int[CAP],
			dirty = new int[CAP], rebuilt = new int[CAP], pumpUs = new int[CAP], instantUs = new int[CAP];
	private static final byte[] phaseOf = new byte[CAP];
	private static int rows;
	private static boolean overflow;

	// Accumulators for the frame in progress (nanoseconds).
	public static long tickNs, lightNs, renderNs, compileNs, genNs, swapNs;
	/** When Display.update (swap, then the input events it reads) returned: the frame's input cut-off. */
	public static long pumpAt;
	/** The display mod's FrameClock instant for this frame, relative to the frame's start (-1 without it). */
	public static long instantNs = Long.MIN_VALUE;
	public static int rebuilds;
	private static int renderChunks;
	private static long frameStart, benchStart;

	// GPU time per frame from GL_TIME_ELAPSED queries, read a few frames later so nothing waits on the GPU.
	private static final int QUERIES = 8;
	private static int[] queries;
	private static final int[] queryRow = new int[QUERIES];
	private static int querySlot;
	private static boolean queryOpen;
	private static int gpuMissing;

	private static int phase = MENU;
	private static long phaseStart;
	private static long settledAt;
	private static int settleUnbuilt = -1;
	private static int settleDirtyLeft = -1;
	private static double worldgenMs;
	private static double yaw0, flyStartX;
	private static final StringBuilder phaseLog = new StringBuilder();
	private static final List<String> gcEvents = new ArrayList<>();
	private static long cpuAtPhase;
	private static String meta = "";
	private static boolean written;
	private static boolean facingFixed;
	private static String captureAs;
	private static Minecraft client;

	private Bench() {
	}

	private static double dbl(String key, double fallback) {
		String v = System.getProperty(key);
		return v == null ? fallback : Double.parseDouble(v);
	}

	private static double sinceS(long t) {
		return (System.nanoTime() - t) / 1e9;
	}

	/** Start of each pass of the game's run loop: closes the previous frame's row and opens the next. */
	public static void frameStart(Minecraft mc) {
		long now = System.nanoTime();
		if (frameStart != 0 && phase >= SETTLE && phase < DONE) record(now, mc);
		tickNs = lightNs = renderNs = compileNs = genNs = swapNs = 0;
		pumpAt = 0;
		instantNs = Long.MIN_VALUE;
		rebuilds = 0;
		frameStart = now;
		if (phase == TURN && mc.player != null) {
			// As mouse look turns the player: yaw and prevYaw together, once per frame.
			float yaw = (float) (yaw0 + 360.0 * sinceS(phaseStart) / TURN_S);
			mc.player.yaw = yaw;
			mc.player.prevYaw = yaw;
		}
		if (queries != null && phase >= SETTLE && phase < DONE) beginGpu();
	}

	private static java.lang.reflect.Method instant;
	private static boolean instantLooked;

	/** Reads the display mod's FrameClock instant (System.nanoTime's offset from the frame's start), if it's there. */
	public static void frameInstant() {
		if (!instantLooked) {
			instantLooked = true;
			try {
				instant = Class.forName("org.studioquire.oldfaithful.display.FrameClock").getMethod("seconds");
			} catch (Throwable t) {
				instant = null;
			}
		}
		if (instant == null) return;
		try {
			// FrameClock runs on SDL's clock; both it and nanoTime count from boot on Windows (QueryPerformanceCounter),
			// but only differences between frames are used, so a constant offset doesn't matter.
			long sdl = (long) ((double) instant.invoke(null) * 1e9);
			instantNs = sdl - org.lwjgl.sdl.SDLTimer.SDL_GetTicksNS() + System.nanoTime() - frameStart;
		} catch (Throwable t) {
			instant = null;
		}
	}

	private static void record(long now, Minecraft mc) {
		if (rows >= CAP) {
			overflow = true;
			return;
		}
		int r = rows++;
		tUs[r] = (int) ((frameStart - benchStart) / 1000);
		frameUs[r] = us(now - frameStart);
		tickUs[r] = us(tickNs);
		lightUs[r] = us(lightNs);
		renderUs[r] = us(renderNs);
		compileUs[r] = us(compileNs);
		genUs[r] = us(genNs);
		swapUs[r] = us(swapNs);
		gpuUs[r] = -1;
		dirty[r] = mc.worldRenderer == null ? -1 : ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().size();
		rebuilt[r] = rebuilds;
		pumpUs[r] = pumpAt == 0 ? -1 : us(pumpAt - frameStart);
		instantUs[r] = instantNs == Long.MIN_VALUE ? Integer.MIN_VALUE : (int) (instantNs / 1000);
		phaseOf[r] = (byte) phase;
	}

	private static int us(long ns) {
		return (int) Math.min(Integer.MAX_VALUE, ns / 1000);
	}

	private static void beginGpu() {
		int q = queries[querySlot];
		if (queryRow[querySlot] >= 0) {
			int row = queryRow[querySlot];
			if (GL15.glGetQueryObjecti(q, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
				if (row < CAP) gpuUs[row] = us(GL33.glGetQueryObjectui64(q, GL15.GL_QUERY_RESULT));
			} else {
				// Not ready QUERIES frames later: that frame's GPU time stays unknown (-1) rather than stall to read it.
				gpuMissing++;
			}
		}
		queryRow[querySlot] = rows; // the row this frame will be written to
		GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, q);
		queryOpen = true;
	}

	/** Just before the frame's buffer swap: the GPU query covers everything the frame submitted. */
	public static void beforeSwap() {
		if (captureAs != null) capture();
		if (queryOpen) {
			GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
			queryOpen = false;
			querySlot = (querySlot + 1) % QUERIES;
		}
	}

	public static void tick(Minecraft mc) {
		client = mc;
		try {
			step(mc);
		} catch (Throwable t) {
			System.out.println("[oldfaithful-bench] FAIL in phase " + PHASES[phase] + ": " + t);
			t.printStackTrace(System.out);
			phase = DONE;
			finish(mc);
		}
	}

	private static void step(Minecraft mc) {
		switch (phase) {
			case MENU: {
				if (!(mc.currentScreen instanceof TitleScreen)) return;
				startWorld(mc);
				return;
			}
			case SETTLE: {
				holdWeather(mc);
				if (!facingFixed && mc.player != null) {
					// A new player faces a random direction (LivingEntity's constructor, Math.random): face +Z, level.
					facingFixed = true;
					mc.player.yaw = mc.player.prevYaw = 0f;
					mc.player.pitch = mc.player.prevPitch = 0f;
				}
				// Loaded: every render chunk built once. b1.7.3 keeps re-dirtying some (water, leaves, light), so
				// an empty rebuild queue never comes.
				int unbuilt = unbuilt(mc);
				if (settledAt == 0 && unbuilt == 0) settledAt = System.nanoTime();
				boolean held = settledAt != 0 && sinceS(settledAt) >= SETTLE_HOLD_S;
				if (held || sinceS(phaseStart) >= SETTLE_TIMEOUT_S) {
					settleDirtyLeft = ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().size();
					settleUnbuilt = unbuilt;
					next(mc, STAND);
				}
				return;
			}
			case STAND:
				holdWeather(mc);
				if (sinceS(phaseStart) >= STAND_S) {
					captureAs = "stand.png";
					yaw0 = mc.player.yaw;
					next(mc, TURN);
				}
				return;
			case TURN:
				holdWeather(mc);
				if (sinceS(phaseStart) >= TURN_S) {
					ClientPlayerEntity p = mc.player;
					p.noClip = true;
					p.setPositionAndAngles(p.x, FLY_Y, p.z, 270f, FLY_PITCH); // yaw 270: facing +X
					flyStartX = p.x;
					next(mc, FLY);
				}
				return;
			case FLY: {
				holdWeather(mc);
				ClientPlayerEntity p = mc.player;
				p.noClip = true;
				p.velocityX = FLY_SPEED / 20.0;
				p.velocityY = 0;
				p.velocityZ = 0;
				p.yaw = 270f;
				p.pitch = FLY_PITCH;
				if (sinceS(phaseStart) >= FLY_S) {
					captureAs = "fly.png";
					phaseLog.append(String.format(Locale.ROOT, ",\n    \"flyDistance\": %.1f", p.x - flyStartX));
					next(mc, DONE);
					finish(mc);
				}
				return;
			}
			default:
		}
	}

	/**
	 * The frame about to be shown, read back from the back buffer into a PNG: the record of what the run drew, and the
	 * check that it drew at all (a window nobody can see might be skipped by the driver).
	 */
	private static void capture() {
		String name = captureAs;
		captureAs = null;
		try {
			int w = client.displayWidth, h = client.displayHeight;
			java.nio.ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(w * h * 3);
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
			GL11.glReadBuffer(GL11.GL_BACK);
			GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, px);
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = ((h - 1 - y) * w + x) * 3;
					img.setRGB(x, y, (px.get(i) & 0xff) << 16 | (px.get(i + 1) & 0xff) << 8 | (px.get(i + 2) & 0xff));
				}
			}
			Path out = Files.createDirectories(Paths.get(OUT));
			javax.imageio.ImageIO.write(img, "png", out.resolve(name).toFile());
		} catch (Throwable t) {
			System.out.println("[oldfaithful-bench] capture " + name + " failed: " + t);
		}
	}

	private static int unbuilt(Minecraft mc) {
		int n = 0;
		for (Object c : ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$chunks()) {
			if (!((BenchChunk) c).oldfaithful$built()) n++;
		}
		return n;
	}

	private static void startWorld(Minecraft mc) {
		String gl = GL11.glGetString(GL11.GL_RENDERER) + " | " + GL11.glGetString(GL11.GL_VERSION) + " | " + GL11.glGetString(GL11.GL_VENDOR);
		if (GLContext.getCapabilities().OpenGL33) {
			queries = new int[QUERIES];
			for (int i = 0; i < QUERIES; i++) {
				queries[i] = GL15.glGenQueries();
				queryRow[i] = -1;
			}
		}
		listenToGc();
		if ("off".equals(VSYNC)) Display.setVSyncEnabled(false);
		else if ("on".equals(VSYNC)) Display.setVSyncEnabled(true);
		meta = String.format(Locale.ROOT,
				"  \"gl\": %s,\n  \"lwjgl\": %s,\n  \"java\": %s,\n  \"display\": [%d, %d],\n  \"viewDistance\": %d,\n  \"fpsLimit\": %d,\n"
						+ "  \"fancyGraphics\": %b,\n  \"smoothLighting\": %b,\n  \"seed\": %d,\n  \"gpuQueries\": %b,\n  \"maxHeapMb\": %d,\n",
				json(gl), json(Sys.getVersion()), json(System.getProperty("java.vendor") + " " + System.getProperty("java.runtime.version")),
				mc.displayWidth, mc.displayHeight, mc.options.viewDistance, mc.options.fpsLimit, mc.options.fancyGraphics, mc.options.ao,
				SEED, queries != null, Runtime.getRuntime().maxMemory() >> 20, json(VSYNC));
		System.out.println("[oldfaithful-bench] HandsOff " + (HandsOff.CURSOR ? "active" : "INACTIVE") + "; LWJGL Mouse.isGrabbed() = "
				+ org.lwjgl.input.Mouse.isGrabbed());
		System.out.println("[oldfaithful-bench] GL: " + gl + "; display " + mc.displayWidth + "x" + mc.displayHeight + "; view distance "
				+ mc.options.viewDistance);
		long t = System.nanoTime();
		cpuAtPhase = cpuNs();
		// As the Create World screen does.
		mc.interactionManager = new SingleplayerInteractionManager(mc);
		mc.startGame("oldfaithful-bench", "Old Faithful benchmark", SEED);
		mc.setScreen(null);
		worldgenMs = (System.nanoTime() - t) / 1e6;
		holdWeather(mc);
		benchStart = System.nanoTime();
		frameStart = 0;
		writeQuietly("bench-start.txt", java.time.OffsetDateTime.now().toString()); // tools/jfr_profile.py --phase
		renderChunks = ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$chunks().length;
		phaseLog.append(String.format(Locale.ROOT, "    \"worldgenMs\": %.1f,\n    \"spawn\": [%.1f, %.1f, %.1f]", worldgenMs,
				mc.player.x, mc.player.y, mc.player.z));
		next(mc, SETTLE);
	}

	private static void next(Minecraft mc, int to) {
		long now = System.nanoTime();
		long cpu = cpuNs();
		if (phase >= SETTLE) {
			MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
			phaseLog.append(String.format(Locale.ROOT, ",\n    \"%sCpuMs\": %.1f,\n    \"%sHeapUsedMb\": %d", PHASES[phase],
					(cpu - cpuAtPhase) / 1e6, PHASES[phase], heap.getUsed() >> 20));
		}
		cpuAtPhase = cpu;
		System.out.println("[oldfaithful-bench] grabbed=" + org.lwjgl.input.Mouse.isGrabbed() + " " + PHASES[phase] + " -> " + PHASES[to] + " at " + String.format(Locale.ROOT, "%.1f s",
				benchStart == 0 ? 0 : (now - benchStart) / 1e9));
		phase = to;
		phaseStart = now;
	}

	private static long cpuNs() {
		java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		return os instanceof com.sun.management.OperatingSystemMXBean ? ((com.sun.management.OperatingSystemMXBean) os).getProcessCpuTime() : -1;
	}

	/** Clear weather, held: rain changes what's drawn, and b1.7.3's weather timer isn't seeded. */
	private static void holdWeather(Minecraft mc) {
		WorldProperties props = mc.world.getProperties();
		props.setRaining(false);
		props.setThundering(false);
		props.setRainTime(1_000_000);
		props.setThunderTime(1_000_000);
	}

	private static void listenToGc() {
		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (!(gc instanceof NotificationEmitter)) continue;
			((NotificationEmitter) gc).addNotificationListener((Notification n, Object handback) -> {
				if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) return;
				GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
				long uptimeAtBench = benchStart == 0 ? 0 : ManagementFactory.getRuntimeMXBean().getUptime() - (System.nanoTime() - benchStart) / 1_000_000;
				synchronized (gcEvents) {
					gcEvents.add(String.format(Locale.ROOT, "%d,%s,%s,%s,%d,%d", info.getGcInfo().getStartTime() - uptimeAtBench,
							csv(info.getGcName()), csv(info.getGcAction()), csv(info.getGcCause()), info.getGcInfo().getDuration(), phase));
				}
			}, null, null);
		}
	}

	private static void writeQuietly(String name, String text) {
		try {
			Files.write(Files.createDirectories(Paths.get(OUT)).resolve(name), text.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			System.out.println("[oldfaithful-bench] couldn't write " + name + ": " + e);
		}
	}

	private static String csv(String s) {
		return s.replace(',', ';');
	}

	private static String json(String s) {
		return "\"" + String.valueOf(s).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static void finish(Minecraft mc) {
		if (written) return;
		written = true;
		try {
			write();
			System.out.println("[oldfaithful-bench] DONE " + rows + " frames" + (overflow ? " (row cap reached)" : ""));
		} catch (Exception e) {
			System.out.println("[oldfaithful-bench] FAIL writing results: " + e);
		} finally {
			// As the window's close button does: the game saves and stops itself.
			mc.scheduleStop();
		}
	}

	private static void write() throws IOException {
		Path out = Files.createDirectories(Paths.get(OUT));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("frames.csv"), StandardCharsets.UTF_8))) {
			w.println("t_us,phase,frame_us,tick_us,light_us,render_us,compile_us,gen_us,swap_us,gpu_us,dirty,rebuilt,pump_us,instant_us");
			for (int r = 0; r < rows; r++) {
				w.print(tUs[r]); w.print(','); w.print(PHASES[phaseOf[r]]); w.print(','); w.print(frameUs[r]); w.print(',');
				w.print(tickUs[r]); w.print(','); w.print(lightUs[r]); w.print(','); w.print(renderUs[r]); w.print(',');
				w.print(compileUs[r]); w.print(','); w.print(genUs[r]); w.print(','); w.print(swapUs[r]); w.print(',');
				w.print(gpuUs[r]); w.print(','); w.print(dirty[r]); w.print(','); w.print(rebuilt[r]); w.print(','); w.print(pumpUs[r]); w.print(','); w.println(instantUs[r]);
			}
		}
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("gc.csv"), StandardCharsets.UTF_8))) {
			w.println("start_ms,name,action,cause,duration_ms,phase");
			synchronized (gcEvents) {
				for (String e : gcEvents) w.println(e);
			}
		}
		String settle = String.format(Locale.ROOT, ",\n    \"renderChunks\": %d,\n    \"settleUnbuilt\": %d,\n    \"settledMs\": %s,\n    \"settleDirtyLeft\": %d,\n    \"gpuResultsMissing\": %d,\n    \"overflow\": %b",
				renderChunks, settleUnbuilt, settledAt == 0 ? "null" : String.format(Locale.ROOT, "%.1f", (settledAt - benchStart) / 1e6), settleDirtyLeft, gpuMissing, overflow);
		Files.write(out.resolve("bench.json"), ("{\n" + meta + "  \"result\": {\n" + phaseLog + settle + "\n  }\n}\n").getBytes(StandardCharsets.UTF_8));
	}
}
