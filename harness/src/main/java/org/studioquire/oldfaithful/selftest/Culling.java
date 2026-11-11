package org.studioquire.oldfaithful.selftest;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.studioquire.oldfaithful.selftest.mixin.FrustumCullerAccessor;
import org.studioquire.oldfaithful.selftest.mixin.WorldRendererAccessor;

/**
 * What entity culling drops at the screen's edges (docs/bugs.md #84): frame hooks for {@link CullingMeasure} and the
 * Fixed edition's culling scene in {@link FixesCheck}. A {@link Scene} places the camera at the start of each frame
 * (GameRendererCullingMixin), a probe entity at the start of drawing entities, with that frame's frustum in hand
 * (WorldRendererCullingMixin), and reads the frame once the world is drawn, before the hand clears the depth. The probe
 * is left unticked by the world ({@link #frozen}, WorldProbeTickMixin), so its pose is only ever what the scene sets.
 * <p>
 * Its pixels are the depth buffer's nearer than {@link #near} blocks from the eye, in a scene with nothing else that near
 * (the camera high above the world): exactly what of it was drawn, whatever its colours.
 */
public final class Culling {
	public interface Scene {
		/** renderFrame's start: the camera (the player) for this frame. */
		void frameStart(Minecraft mc);

		/** renderEntities' start: the probe for this frame, with this frame's frustum. Not called while beta skips drawing entities. */
		void entities(Minecraft mc, FrustumCuller culler, float tickDelta);

		/** The world drawn, for the frame whose {@link #entities} was called. */
		void drawn(Minecraft mc);
	}

	public static volatile Scene scene;
	/** Where every frame is drawn between the last tick and this one while a scene runs (MinecraftCullingMixin): at this one unless set. */
	public static volatile float frameDelta = 1.0F;
	/** Left unticked by the world (WorldProbeTickMixin). */
	public static volatile Entity frozen;
	private static boolean entitiesThisFrame;
	private static final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
	private static float p10, p14;
	/** The view's half-angles across and up, in degrees, from the last frame's projection (beta's 70-degree FOV at 16:10 until one is drawn). */
	static volatile double halfWide = 48.2, halfHigh = 35;

	private Culling() {
	}

	public static void frameStart(Minecraft mc) {
		entitiesThisFrame = false;
		Scene s = scene;
		if (s != null) s.frameStart(mc);
	}

	public static void entities(Minecraft mc, Culler culler, float tickDelta) {
		Scene s = scene;
		// Beta draws no entities for two frames after the renderer reloads.
		if (s == null || !(culler instanceof FrustumCuller) || ((WorldRendererAccessor) mc.worldRenderer).oldfaithful$entityRenderCooldown() > 0) return;
		matrix.clear();
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrix);
		p10 = matrix.get(10);
		p14 = matrix.get(14);
		halfWide = Math.toDegrees(Math.atan(1 / matrix.get(0)));
		halfHigh = Math.toDegrees(Math.atan(1 / matrix.get(5)));
		entitiesThisFrame = true;
		s.entities(mc, (FrustumCuller) culler, tickDelta);
	}

	public static void drawn(Minecraft mc) {
		Scene s = scene;
		if (s != null && entitiesThisFrame) s.drawn(mc);
	}

	// ---- The frustum, as beta's culler tests a box: each plane must have a corner of the box in front of it.

	/** Per plane, the furthest any corner of the box lies in front of it, in blocks; the box is drawn only if all six are above 0. */
	static double[] margins(FrustumCuller culler, Box b) {
		FrustumCullerAccessor c = (FrustumCullerAccessor) culler;
		float[][] f = c.oldfaithful$frustum().frustum;
		double ox = c.oldfaithful$offsetX(), oy = c.oldfaithful$offsetY(), oz = c.oldfaithful$offsetZ();
		double[] m = new double[6];
		for (int i = 0; i < 6; i++) {
			float[] p = f[i];
			m[i] = p[0] * (p[0] > 0 ? b.maxX - ox : b.minX - ox) + p[1] * (p[1] > 0 ? b.maxY - oy : b.minY - oy)
					+ p[2] * (p[2] > 0 ? b.maxZ - oz : b.minZ - oz) + p[3];
		}
		return m;
	}

	/** How far the box lies outside the frustum, in blocks, past its worst plane: 0 or less while beta draws it. */
	static double outside(FrustumCuller culler, Box b) {
		double worst = Double.MAX_VALUE;
		for (double m : margins(culler, b)) worst = Math.min(worst, m);
		return -worst;
	}

	/** The least growth on every side that brings the box back into the frustum (0 if it's in). */
	static double padNeeded(FrustumCuller culler, Box b) {
		float[][] f = ((FrustumCullerAccessor) culler).oldfaithful$frustum().frustum;
		double[] m = margins(culler, b);
		double pad = 0;
		for (int i = 0; i < 6; i++) {
			if (m[i] > 0) continue;
			double l1 = Math.abs(f[i][0]) + Math.abs(f[i][1]) + Math.abs(f[i][2]);
			pad = Math.max(pad, -m[i] / l1);
		}
		return pad;
	}

	static boolean visible(FrustumCuller culler, Box b) {
		return culler.intersectsFrustum(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
	}

	/** Which plane the box is furthest outside: right, left, bottom, top, far, near (beta's order), or "in". */
	static String edge(FrustumCuller culler, Box b) {
		double[] m = margins(culler, b);
		int worst = 0;
		for (int i = 1; i < 6; i++) if (m[i] < m[worst]) worst = i;
		return m[worst] > 0 ? "in" : new String[] {"right", "left", "bottom", "top", "far", "near"}[worst];
	}

	// ---- Pixels

	/** The depth buffer's pixels nearer than {@code near} blocks from the eye (along the view): what was drawn that close. */
	static int nearPixels(int w, int h, double near) {
		IntBuffer depth = BufferUtils.createIntBuffer(w * h);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, depth);
		double ndc = -p10 + p14 / near;
		long limit = (long) ((ndc + 1) / 2 * 0xFFFFFFFFL);
		int n = 0;
		for (int i = 0, end = w * h; i < end; i++) {
			if ((depth.get(i) & 0xFFFFFFFFL) < limit) n++;
		}
		return n;
	}

	/** The frame's colours as they stand. */
	static BufferedImage colour(int w, int h) {
		ByteBuffer px = BufferUtils.createByteBuffer(w * h * 4);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = ((h - 1 - y) * w + x) * 4;
				img.setRGB(x, y, (px.get(i) & 0xFF) << 16 | (px.get(i + 1) & 0xFF) << 8 | px.get(i + 2) & 0xFF);
			}
		}
		return img;
	}

	// ---- Places

	/**
	 * The middle of a box {@code distance} straight ahead of the camera, slid {@code slide} blocks towards an edge of the
	 * screen ('L' left, 'U' up, 'D' down). Worked out for a camera along +z (whose screen's left is +x), then turned by its yaw.
	 */
	static double[] ahead(double cx, double eye, double cz, float yaw, float pitch, char toward, double distance, double slide) {
		double p = Math.toRadians(pitch);
		double ux, uy, uz;
		switch (toward) {
			case 'U': ux = 0; uy = Math.cos(p); uz = Math.sin(p); break;
			case 'D': ux = 0; uy = -Math.cos(p); uz = -Math.sin(p); break;
			default: ux = 1; uy = 0; uz = 0;
		}
		double x = slide * ux, y = -Math.sin(p) * distance + slide * uy, z = Math.cos(p) * distance + slide * uz;
		double r = Math.toRadians(yaw);
		return new double[] {cx + x * Math.cos(r) - z * Math.sin(r), eye + y, cz + x * Math.sin(r) + z * Math.cos(r)};
	}

	// ---- Poses

	/** The camera, held still: the same pose at any point between ticks. */
	static void hold(ClientPlayerEntity p, double x, double eyeY, double z, float yaw, float pitch) {
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.fireTicks = 0;
		p.health = 20;
		p.hurtTime = 0;
		p.setPositionAndAngles(x, eyeY, z, yaw, pitch);
		p.lastTickX = x;
		p.lastTickY = eyeY;
		p.lastTickZ = z;
	}

	/** An entity with the middle of its box at x, y, z, facing yaw (body and head), standing still between ticks. */
	static void place(Entity e, double x, double y, double z, float yaw, float pitch) {
		e.setPosition(x, y, z);
		double up = y - (e.boundingBox.minY + e.boundingBox.maxY) / 2;
		e.setPosition(x, y + up, z);
		e.prevX = e.lastTickX = e.x;
		e.prevY = e.lastTickY = e.y;
		e.prevZ = e.lastTickZ = e.z;
		e.yaw = e.prevYaw = yaw;
		e.pitch = e.prevPitch = pitch;
		e.velocityX = e.velocityY = e.velocityZ = 0;
		if (e instanceof LivingEntity) {
			LivingEntity l = (LivingEntity) e;
			l.bodyYaw = l.lastBodyYaw = yaw;
		}
	}

	/** A living entity's walk: its limbs at {@code phase} (radians of beta's swing) with {@code speed} (0 standing to 1 striding), this frame. */
	static void walk(LivingEntity e, float phase, float speed, float tickDelta) {
		e.walkAnimationSpeed = e.lastWalkAnimationSpeed = speed;
		// The renderer reads progress - speed * (1 - tickDelta); beta's models swing by cos(progress * 0.6662).
		e.walkAnimationProgress = phase / 0.6662F + speed * (1 - tickDelta);
	}
}
