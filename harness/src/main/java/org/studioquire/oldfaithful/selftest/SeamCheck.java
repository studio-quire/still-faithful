package org.studioquire.oldfaithful.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Measures the sky showing through terrain (`:harness:seamCheck`, docs/fixes.md > #70): holes in the world's
 * surface, one pixel at a time, as the camera moves by fractions of a pixel. Run in the start-up test's hidden window
 * through SceneShot's world ({@code -Doldfaithful.selftest.seams}).
 *
 * <p>At each place in {@code seamsAt} ("x z;..."), a sealed room is built high in the air (walls two blocks thick,
 * interior {@code seamsSize} square, y 115 to 125 (above fancy clouds), lit by glowstone in the ceiling, pillars inside), so nothing in a
 * frame should lie outside it. Each view is held for {@code seamsFrames} frames, the camera nudged a little each
 * frame (about 1/80 block and 1/20 degree). A frame is read back before the hand is drawn: colour, depth and the
 * camera's matrices; a pixel whose depth puts it outside the room (or at the far plane: the sky) is a hole. Each
 * hole is placed on the room's wall the ray crossed and classed: on a chunk seam (a block edge at a multiple of 16),
 * on another block edge, or inside a face.
 *
 * <p>{@code seamsOpaque}: the terrain atlas's transparent texels made opaque first, so holes that come from a face
 * sampling a transparent texel next to its own tile drop out and only the geometry's remain.
 *
 * <p>{@code seamsCrack}: the block-breaking cracks instead. A stone block in the floor is looked at from above at
 * diagonal angles; each camera position is drawn twice, without and with the cracks at half progress (the crack
 * tiles made opaque so every pixel of them draws). The cracks are drawn with a polygon offset toward the camera and
 * write depth, so where they drew, the depth is nearer than without; a pixel of the block's top face whose depth is
 * unchanged is one the cracks lost to the block itself (z-fighting).
 */
public final class SeamCheck {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.selftest.seams");
	private static final String AT = System.getProperty("oldfaithful.selftest.seamsAt", "20 20;-76 -76;708 708");
	private static final int SIZE = Integer.getInteger("oldfaithful.selftest.seamsSize", 48);
	private static final int FRAMES = Integer.getInteger("oldfaithful.selftest.seamsFrames", 32);
	private static final boolean OPAQUE = Boolean.getBoolean("oldfaithful.selftest.seamsOpaque");
	private static final boolean CRACK = Boolean.getBoolean("oldfaithful.selftest.seamsCrack");
	private static final String LABEL = System.getProperty("oldfaithful.selftest.seamsLabel", "run");
	private static final int Y0 = 115, Y1 = 126;
	private static final int STONE = 1, GRASS = 2, DIRT = 3, COBBLE = 4, PLANKS = 5, GLOWSTONE = 89;

	/** One view: the eye relative to the room's corner (x0, Y0's floor, z0), yaw and pitch. */
	private static final double[][] VIEWS = {
			{20.3, 2.6, 20.3, 135, 35},   // down at the floor, diagonally
			{6.5, 6.2, 6.5, -45, 5},      // across the room to the far corner
			{24.2, 3.1, 24.7, 200, -40},  // up at the ceiling
			{3.3, 0.9, 3.3, -45, 8},      // along the floor, grazing
			{30.1, 8.4, 12.8, 70, 20},    // down past pillars at a wall
	};
	/** Crack views: the eye relative to the target block's top centre (dx, dy, dz); aimed at that centre. */
	private static final double[][] CRACK_VIEWS = {
			{-1.1, 1.9, -1.1}, {1.2, 2.2, -1.0}, {0.9, 1.6, 1.3}, {-1.5, 1.4, 1.4}, {-0.4, 2.6, -0.5}, {2.0, 1.2, 2.0},
			{0.35, 1.62, 0.3}, {-0.7, 1.62, 0.65}, {0.9, 1.62, -0.85},  // standing on or beside it, looking almost straight down
	};

	private final File out;
	private final SelfTest results;
	private final String[] places;
	private int place = -1;
	private int phase;
	private int ticks;
	private int view;
	private int frame;
	private int x0, z0;
	private final StringBuilder report = new StringBuilder();

	// Totals over a place's views.
	private long holes, seamHoles, edgeHoles, faceHoles, frames, framesWithHoles, maxHoles;
	private long crackPixels, crackLost, noCracks;
	private final java.util.TreeMap<String, Integer> kinds = new java.util.TreeMap<>();

	// Between the tick that asks and the frame that answers.
	private static volatile SeamCheck pending;
	private static volatile boolean taken;
	private static volatile float crackProgress = -1;
	private static final double[] modelView = new double[16], projection = new double[16];
	private int[] noCrackDepth;
	private int worstFrameHoles = -1;

	SeamCheck(String outDir, SelfTest results) {
		this.out = new File(outDir);
		this.results = results;
		this.places = AT.split(";");
	}

	/** One game tick; true when done. */
	boolean tick(Minecraft mc) {
		mc.options.hideHud = true;
		mc.options.difficulty = 0;
		mc.options.viewDistance = 0;
		mc.options.bobView = false;
		mc.world.setTime(6000);
		mc.world.getProperties().setRaining(false);
		mc.world.getProperties().setRainTime(Integer.MAX_VALUE / 2);
		ticks++;
		if (place < 0) nextPlace();
		if (place >= places.length) {
			write();
			return true;
		}
		switch (phase) {
			case 1: // generated around, then build
				hold(mc.player, x0 + SIZE / 2.0, Y0 + 5, z0 + SIZE / 2.0, 0, 0);
				if (ticks < 60) return false;
				build(mc.world);
				mc.worldRenderer.reload();
				if (OPAQUE) opaqueAtlas(mc, 0, 256);
				if (CRACK) opaqueAtlas(mc, 240, 250);
				phase = 2;
				ticks = 0;
				return false;
			case 2: // the room's chunks rebuilt
				hold(mc.player, x0 + SIZE / 2.0, Y0 + 5, z0 + SIZE / 2.0, 0, 0);
				if (ticks < 400 && !(ticks > 100 && ((org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().isEmpty())) return false;
				phase = 3;
				view = 0;
				frame = 0;
				return false;
			case 3:
				return CRACK ? crackTick(mc) : viewTick(mc);
			default:
				return false;
		}
	}

	private void nextPlace() {
		place++;
		if (place >= places.length) return;
		String[] xz = places[place].trim().split("\\s+");
		x0 = Integer.parseInt(xz[0]);
		z0 = Integer.parseInt(xz[1]);
		holes = seamHoles = edgeHoles = faceHoles = frames = framesWithHoles = maxHoles = 0;
		crackPixels = crackLost = noCracks = 0;
		kinds.clear();
		worstFrameHoles = -1;
		phase = 1;
		ticks = 0;
	}

	private boolean viewTick(Minecraft mc) {
		if (view >= VIEWS.length) {
			finishPlace();
			return false;
		}
		double[] v = VIEWS[view];
		if (frame > 0 && pending != null) {
			hold(mc.player, eyeX, eyeY, eyeZ, yaw, pitch);
			return false; // the frame asked for hasn't been drawn yet
		}
		if (frame >= FRAMES) {
			view++;
			frame = 0;
			worstFrameHoles = -1;
			return false;
		}
		int i = frame;
		if (i == 0 && !settled(mc, v[0], v[1], v[2], (float) v[3], (float) v[4])) return false;
		eyeX = x0 + v[0] + i * 0.0131;
		eyeY = Y0 + v[1] + i * 0.0071;
		eyeZ = z0 + v[2] + i * 0.0113;
		yaw = (float) (v[3] + i * 0.047);
		pitch = (float) (v[4] + i * 0.029);
		hold(mc.player, eyeX, eyeY, eyeZ, yaw, pitch);
		frame++;
		pending = this;
		return false;
	}

	private double eyeX, eyeY, eyeZ;
	private float yaw, pitch;
	private int crackStep;

	private boolean crackTick(Minecraft mc) {
		if (view >= CRACK_VIEWS.length) {
			finishPlace();
			return false;
		}
		if (pending != null) {
			hold(mc.player, eyeX, eyeY, eyeZ, yaw, pitch);
			return false;
		}
		if (frame >= FRAMES * 2) {
			view++;
			frame = 0;
			worstFrameHoles = -1;
			return false;
		}
		double[] v = CRACK_VIEWS[view];
		int i = frame / 2;
		if (frame == 0 && !settled(mc, 20.5 + v[0], v[1], 20.5 + v[2], 0, 89)) return false;
		double tx = x0 + 20.5, ty = Y0, tz = z0 + 20.5;
		eyeX = tx + v[0] + i * 0.0131;
		eyeY = ty + v[1] + i * 0.0071;
		eyeZ = tz + v[2] + i * 0.0113;
		double dx = tx - eyeX, dy = ty - eyeY, dz = tz - eyeZ;
		yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + i * 0.047f;
		pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))) + i * 0.029f;
		hold(mc.player, eyeX, eyeY, eyeZ, yaw, pitch);
		crackStep = frame % 2;
		crackProgress = crackStep == 0 ? 0f : 0.55f;
		frame++;
		pending = this;
		return false;
	}

	private int settle;

	/**
	 * Before a view's first frame: the camera held there until the render chunks moved by the jump are all rebuilt
	 * (beta resets a render chunk it moves, and draws nothing there until it's built again), at least 20 ticks.
	 */
	private boolean settled(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
		hold(mc.player, x0 + x, Y0 + y, z0 + z, yaw, pitch);
		settle++;
		boolean done = settle >= 20 && ((org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor) mc.worldRenderer).oldfaithful$dirtyChunks().isEmpty()
				|| settle >= 400;
		if (done) settle = 0;
		return done;
	}

	private void finishPlace() {
		String line;
		if (CRACK) {
			line = String.format(Locale.ROOT, "%s at %d %d: cracks on the top face %d pixels over %d frames, lost to the block %d (%.3f%%); %d pairs without cracks left out",
					LABEL, x0, z0, crackPixels, frames, crackLost, crackPixels == 0 ? 0 : 100.0 * crackLost / crackPixels, noCracks);
		} else {
			line = String.format(Locale.ROOT, "%s at %d %d: %d frames, %d hole pixels (%.2f a frame, worst %d; %d frames with any): "
							+ "%d on chunk seams, %d on other block edges, %d inside faces",
					LABEL, x0, z0, frames, holes, frames == 0 ? 0 : (double) holes / frames, maxHoles, framesWithHoles, seamHoles, edgeHoles, faceHoles)
					+ " " + kinds;
		}
		results.pass("seams: " + line);
		report.append(line).append('\n');
		phase = 0;
		view = 0;
		frame = 0;
		nextPlace();
	}

	private void write() {
		try {
			out.mkdirs();
			Files.write(new File(out, "seams.txt").toPath(), report.toString().getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void hold(ClientPlayerEntity p, double x, double y, double z, float yaw, float pitch) {
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.setPositionAndAngles(x, y, z, yaw, pitch);
		p.lastTickX = x;
		p.lastTickY = y;
		p.lastTickZ = z;
	}

	/** The sealed room: walls two thick of a mix of blocks, a grass floor, a stone ceiling with glowstone, pillars. */
	private void build(World w) {
		int xa = x0 - 2, xb = x0 + SIZE + 2, za = z0 - 2, zb = z0 + SIZE + 2;
		int[] wallMix = {STONE, DIRT, COBBLE, PLANKS};
		for (int x = xa; x < xb; x++) {
			for (int z = za; z < zb; z++) {
				for (int y = Y0 - 2; y < Y1 + 2; y++) {
					boolean inside = x >= x0 && x < x0 + SIZE && z >= z0 && z < z0 + SIZE && y >= Y0 && y < Y1;
					int id;
					if (inside) id = 0;
					else if (y < Y0) id = y == Y0 - 1 ? ((x + z) % 7 == 0 ? DIRT : GRASS) : DIRT;
					else if (y >= Y1) id = y == Y1 && Math.floorMod(x - x0, 6) == 3 && Math.floorMod(z - z0, 6) == 3 ? GLOWSTONE : STONE;
					else id = wallMix[Math.floorMod((x >> 1) + (y >> 1) + (z >> 1), wallMix.length)];
					w.setBlock(x, y, z, id);
				}
			}
		}
		// Pillars of different heights and kinds, for edges against the room behind them.
		for (int a = 0; a < 4; a++) {
			for (int b = 0; b < 4; b++) {
				int px = x0 + 6 + a * 11, pz = z0 + 7 + b * 11, h = 2 + (a * 3 + b) % 6;
				for (int y = Y0; y < Y0 + h; y++) w.setBlock(px, y, pz, wallMix[(a + b) % 4]);
			}
		}
		// The crack test's block: stone in the floor.
		w.setBlock(x0 + 20, Y0 - 1, z0 + 20, STONE);
	}

	/** Tiles from..to (exclusive) of the terrain atlas made fully opaque, colours kept. */
	private static void opaqueAtlas(Minecraft mc, int from, int to) {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, mc.textureManager.getTextureId("/terrain.png"));
		int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
		int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
		ByteBuffer px = BufferUtils.createByteBuffer(w * h * 4);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
		int tile = w / 16;
		for (int t = from; t < to; t++) {
			int tx = (t % 16) * tile, ty = (t / 16) * tile;
			for (int y = ty; y < ty + tile; y++) {
				for (int x = tx; x < tx + tile; x++) px.put((y * w + x) * 4 + 3, (byte) 255);
			}
		}
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
	}

	// ---- On the render thread (GameRendererSeamMixin) ----

	/** At the start of a frame: the crack test's progress, after the interaction manager has set its own. */
	public static void beforeFrame(Minecraft mc) {
		if (CRACK && crackProgress >= 0) mc.worldRenderer.miningProgress = crackProgress;
	}

	/** After the camera's transform is set up: the matrices the world is drawn with. */
	public static void matrices() {
		if (pending == null) return;
		FloatBuffer f = BufferUtils.createFloatBuffer(16);
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, f);
		for (int i = 0; i < 16; i++) modelView[i] = f.get(i);
		f.clear();
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, f);
		for (int i = 0; i < 16; i++) projection[i] = f.get(i);
	}

	/** Before the depth is cleared for the hand: the frame's colour and depth, measured. */
	public static void frame(Minecraft mc) {
		SeamCheck c = pending;
		if (c == null) return;
		int w = mc.displayWidth, h = mc.displayHeight;
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		IntBuffer depth = BufferUtils.createIntBuffer(w * h);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, depth);
		int[] d = new int[w * h];
		depth.get(d);
		ByteBuffer rgb = BufferUtils.createByteBuffer(w * h * 3);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, rgb);
		double[] inv = invert(multiply(projection, modelView));
		ClientPlayerEntity p = mc.player;
		if (CRACK) c.measureCrack(d, rgb, w, h, inv, p);
		else c.measureHoles(d, rgb, w, h, inv, p);
		pending = null;
	}

	private void measureHoles(int[] d, ByteBuffer rgb, int w, int h, double[] inv, ClientPlayerEntity p) {
		double m = 0.3;
		double bx0 = x0 - m, bx1 = x0 + SIZE + m, by0 = Y0 - m, by1 = Y1 + m, bz0 = z0 - m, bz1 = z0 + SIZE + m;
		double tanHalf = 1.0 / projection[5];
		int n = 0;
		boolean[] hole = new boolean[w * h];
		double[] q = new double[3], near = new double[3], far = new double[3];
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int i = py * w + px;
				long di = d[i] & 0xFFFFFFFFL;
				boolean isHole;
				if (di >= 0xFFFFFF00L) {
					isHole = true;
				} else {
					unproject(inv, px, py, w, h, di / 4294967295.0, q);
					double x = q[0] + p.x, y = q[1] + p.y, z = q[2] + p.z;
					isHole = x < bx0 || x > bx1 || y < by0 || y > by1 || z < bz0 || z > bz1;
				}
				if (!isHole) continue;
				hole[i] = true;
				n++;
				// Where the ray leaves the room: the wall it crossed, and how near a block edge or chunk seam.
				unproject(inv, px, py, w, h, 0, near);
				unproject(inv, px, py, w, h, 1, far);
				double ox = near[0] + p.x, oy = near[1] + p.y, oz = near[2] + p.z;
				double rx = far[0] - near[0], ry = far[1] - near[1], rz = far[2] - near[2];
				double t = Double.MAX_VALUE;
				int axis = -1;
				double tt;
				if (rx != 0 && (tt = ((rx > 0 ? x0 + SIZE : x0) - ox) / rx) < t) { t = tt; axis = 0; }
				if (ry != 0 && (tt = ((ry > 0 ? Y1 : Y0) - oy) / ry) < t) { t = tt; axis = 1; }
				if (rz != 0 && (tt = ((rz > 0 ? z0 + SIZE : z0) - oz) / rz) < t) { t = tt; axis = 2; }
				double sx = ox + rx * t, sy = oy + ry * t, sz = oz + rz * t;
				double dist = Math.sqrt((sx - p.x) * (sx - p.x) + (sy - p.y) * (sy - p.y) + (sz - p.z) * (sz - p.z));
				double tol = Math.max(0.01, 3 * dist * 2 * tanHalf / h);
				double[] in = axis == 0 ? new double[] {sy, sz} : axis == 1 ? new double[] {sx, sz} : new double[] {sx, sy};
				double e0 = Math.abs(in[0] - Math.rint(in[0])), e1 = Math.abs(in[1] - Math.rint(in[1]));
				boolean seam0 = e0 < tol && Math.floorMod((long) Math.rint(in[0]), 16) == 0;
				boolean seam1 = e1 < tol && Math.floorMod((long) Math.rint(in[1]), 16) == 0;
				// A ray meeting the room's own corner also meets the next wall's face edge: count those as edges too.
				boolean corner = Math.abs(sx - x0) < tol || Math.abs(sx - x0 - SIZE) < tol || Math.abs(sy - Y0) < tol || Math.abs(sy - Y1) < tol
						|| Math.abs(sz - z0) < tol || Math.abs(sz - z0 - SIZE) < tol;
				boolean cornerSeam = corner && (Math.abs(sx - x0) < tol && x0 % 16 == 0 || Math.abs(sx - x0 - SIZE) < tol && (x0 + SIZE) % 16 == 0
						|| Math.abs(sz - z0) < tol && z0 % 16 == 0 || Math.abs(sz - z0 - SIZE) < tol && (z0 + SIZE) % 16 == 0
						|| Math.abs(sy - Y1) < tol && Y1 % 16 == 0 || Math.abs(sy - Y0) < tol && Y0 % 16 == 0);
				// Which edge, by the surface and the coordinate along which it's whole: on a wall, x or z whole is an
				// upright edge, y whole a level one (a face's top edge, where its texture starts at the tile's edge).
				String where = axis == 1 ? (ry < 0 ? "floor" : "ceiling") : "wall";
				String along = e0 <= e1 ? (axis == 0 ? "y" : axis == 1 ? "x" : "x") : (axis == 0 ? "z" : axis == 1 ? "z" : "y");
				if (seam0 || seam1 || cornerSeam) {
					seamHoles++;
					kinds.merge("seam " + where + " " + along, 1, Integer::sum);
				} else if (e0 < tol || e1 < tol || corner) {
					edgeHoles++;
					kinds.merge("edge " + where + " " + along, 1, Integer::sum);
				} else {
					faceHoles++;
				}
			}
		}
		if (frame == 1) {
			// The view's first frame as drawn, for comparing runs pixel by pixel.
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					img.setRGB(x, h - 1 - y, (rgb.get(i * 3) & 0xff) << 16 | (rgb.get(i * 3 + 1) & 0xff) << 8 | (rgb.get(i * 3 + 2) & 0xff));
				}
			}
			try {
				out.mkdirs();
				ImageIO.write(img, "png", new File(out, String.format(Locale.ROOT, "%s-%d-%d-view%d-first.png", LABEL, x0, z0, view)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		holes += n;
		frames++;
		if (n > 0) framesWithHoles++;
		maxHoles = Math.max(maxHoles, n);
		if (n > worstFrameHoles) {
			worstFrameHoles = n;
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					int c = (rgb.get(i * 3) & 0xff) << 16 | (rgb.get(i * 3 + 1) & 0xff) << 8 | (rgb.get(i * 3 + 2) & 0xff);
					img.setRGB(x, h - 1 - y, hole[i] ? 0xFF00FF : c);
				}
			}
			try {
				out.mkdirs();
				ImageIO.write(img, "png", new File(out, String.format(Locale.ROOT, "%s-%d-%d-view%d.png", LABEL, x0, z0, view)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private void measureCrack(int[] d, ByteBuffer rgb, int w, int h, double[] inv, ClientPlayerEntity p) {
		if (crackStep == 0) {
			noCrackDepth = d;
			return;
		}
		if (noCrackDepth == null) return;
		int bx = x0 + 20, by = Y0, bz = z0 + 20;
		double[] q = new double[3];
		int face = 0, lost = 0;
		boolean[] lostAt = new boolean[w * h];
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int i = py * w + px;
				long di = noCrackDepth[i] & 0xFFFFFFFFL;
				unproject(inv, px, py, w, h, di / 4294967295.0, q);
				double x = q[0] + p.x, y = q[1] + p.y, z = q[2] + p.z;
				if (Math.abs(y - by) > 0.01 || x < bx + 0.03 || x > bx + 0.97 || z < bz + 0.03 || z > bz + 0.97) continue;
				face++;
				if ((d[i] & 0xFFFFFFFFL) >= di) {
					lost++;
					lostAt[i] = true;
				}
			}
		}
		if (lost == face) {
			// No cracks drawn at all: the aim hadn't reached the block yet after a jump. Not a measurement.
			noCracks++;
			noCrackDepth = null;
			return;
		}
		if (lost > 0) report.append(String.format(Locale.ROOT, "  %s at %d %d view %d pair %d: %d of %d lost%n", LABEL, x0, z0, view, frame / 2, lost, face));
		if (lost > worstFrameHoles) {
			worstFrameHoles = lost;
			// The view's worst pair: the frame with the cracks, lost pixels magenta.
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					img.setRGB(x, h - 1 - y, lostAt[i] ? 0xFF00FF : (rgb.get(i * 3) & 0xff) << 16 | (rgb.get(i * 3 + 1) & 0xff) << 8 | (rgb.get(i * 3 + 2) & 0xff));
				}
			}
			try {
				out.mkdirs();
				ImageIO.write(img, "png", new File(out, String.format(Locale.ROOT, "%s-%d-%d-view%d.png", LABEL, x0, z0, view)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		crackPixels += face;
		crackLost += lost;
		frames++;
		noCrackDepth = null;
	}

	private static void unproject(double[] inv, int px, int py, int w, int h, double depth, double[] out) {
		double x = (px + 0.5) / w * 2 - 1, y = (py + 0.5) / h * 2 - 1, z = depth * 2 - 1;
		double rx = inv[0] * x + inv[4] * y + inv[8] * z + inv[12];
		double ry = inv[1] * x + inv[5] * y + inv[9] * z + inv[13];
		double rz = inv[2] * x + inv[6] * y + inv[10] * z + inv[14];
		double rw = inv[3] * x + inv[7] * y + inv[11] * z + inv[15];
		out[0] = rx / rw;
		out[1] = ry / rw;
		out[2] = rz / rw;
	}

	/** Column-major a × b. */
	private static double[] multiply(double[] a, double[] b) {
		double[] r = new double[16];
		for (int c = 0; c < 4; c++) {
			for (int row = 0; row < 4; row++) {
				double s = 0;
				for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k];
				r[c * 4 + row] = s;
			}
		}
		return r;
	}

	/** A 4×4 inverse by Gauss-Jordan elimination (column-major in and out). */
	private static double[] invert(double[] m) {
		double[][] a = new double[4][8];
		for (int r = 0; r < 4; r++) {
			for (int c = 0; c < 4; c++) a[r][c] = m[c * 4 + r];
			a[r][4 + r] = 1;
		}
		for (int c = 0; c < 4; c++) {
			int piv = c;
			for (int r = c + 1; r < 4; r++) if (Math.abs(a[r][c]) > Math.abs(a[piv][c])) piv = r;
			double[] t = a[c]; a[c] = a[piv]; a[piv] = t;
			double div = a[c][c];
			for (int k = 0; k < 8; k++) a[c][k] /= div;
			for (int r = 0; r < 4; r++) {
				if (r == c) continue;
				double f = a[r][c];
				for (int k = 0; k < 8; k++) a[r][k] -= f * a[c][k];
			}
		}
		double[] r = new double[16];
		for (int row = 0; row < 4; row++) for (int c = 0; c < 4; c++) r[c * 4 + row] = a[row][4 + c];
		return r;
	}
}
