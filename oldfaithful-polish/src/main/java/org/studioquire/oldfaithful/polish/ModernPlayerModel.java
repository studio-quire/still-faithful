package org.studioquire.oldfaithful.polish;

import java.nio.FloatBuffer;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.Quad;
import net.minecraft.client.model.Vertex;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.GlAllocationUtils;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.opengl.GL11;
import org.studioquire.oldfaithful.polish.mixin.skins.ModelPartAccessor;

/**
 * The player's model for a {@link ModernSkin}, a model of its own so beta's, which zombies, skeletons and armour are
 * built on, stays as it is. Beta's boxes with later versions' left limbs, layers, slim arms and leg stance. The cape
 * keeps beta's mapping: it has a 64x32 texture of its own.
 *
 * Three things beta's ModelPart can't do are done to its faces here. It divides V by 32, so each is halved for a texture
 * 64 high; it stops each face a tenth of a pixel short of its edges, and later versions go right to them; and later
 * versions turned every box's bottom face over (its front edge at the bottom of its texture, not the top), which skins
 * have been painted for since.
 *
 * It's drawn as a modern client draws it (seen in 26.3's own code and pictures; docs/still-faithful.md > Modern skins):
 * blended, depth written, and every face of every part sorted, farthest first, by its middle; and with a face seen from
 * behind lit as its other side would be, which fixed-function GL calls two-sided lighting. With GL's own calls, not
 * the tessellator: its normal() packs signed bytes with an OR, so a negative x wipes out y and z (and +1 overflows to
 * -1). Beta's boxes get away with that, being axis-aligned where it's used and lit the same from opposite sides.
 */
public final class ModernPlayerModel extends BipedEntityModel {
	/** Later versions' distance of a layer from the limb under it; the hat's is 0.5. */
	private static final float LAYER = 0.25F;
	private static final int BOTTOM_FACE = 3;
	/** Twelve parts of six faces. */
	private static final int MOST_FACES = 72;

	/** {@code PlayerEntityRenderer}, which holds the models and chooses for each player drawn. */
	public interface Wearer {
		void oldfaithful$wear(PlayerEntity player, TextureManager textures);

		void oldfaithful$wearBeta();
	}

	public final boolean slim;
	private final ModelPart jacket, rightSleeve, leftSleeve, rightTrouser, leftTrouser;
	private final ModelPart[] parts;

	// One drawing's faces, ready to draw: all this runs on the render thread, for every player, every frame.
	private final float[] corners = new float[MOST_FACES * 4 * 3], uvs = new float[MOST_FACES * 4 * 2], normals = new float[MOST_FACES * 3];
	private final float[] distance = new float[MOST_FACES];
	private final int[] order = new int[MOST_FACES];
	private final FloatBuffer matrix = GlAllocationUtils.allocateFloatBuffer(16);
	private final float[] modelview = new float[16];
	private int faceCount;
	private boolean handCulled, handTwoSided, handHidden;

	public ModernPlayerModel(boolean slim) {
		super(0.0F);
		this.slim = slim;
		int arm = slim ? 3 : 4;
		float rightArmX = slim ? -2.0F : -3.0F;
		rightArm = limb(40, 16, rightArmX, -2, arm, 0, -5, 2);
		leftArm = limb(32, 48, -1, -2, arm, 0, 5, 2);
		rightLeg.pivotX = -1.9F;
		leftLeg = limb(16, 48, -2, 0, 4, 0, 1.9F, 12);
		rightSleeve = limb(40, 32, rightArmX, -2, arm, LAYER, -5, 2);
		leftSleeve = limb(48, 48, -1, -2, arm, LAYER, 5, 2);
		rightTrouser = limb(0, 32, -2, 0, 4, LAYER, -1.9F, 12);
		leftTrouser = limb(0, 48, -2, 0, 4, LAYER, 1.9F, 12);
		jacket = new ModelPart(16, 32);
		jacket.addCuboid(-4, 0, -2, 8, 12, 4, LAYER);
		for (ModelPart part : new ModelPart[] {head, hat, body, rightLeg, ears, jacket}) fit(part);
		parts = new ModelPart[] {head, body, rightArm, leftArm, rightLeg, leftLeg, hat, jacket, rightSleeve, leftSleeve, rightTrouser, leftTrouser};
	}

	private static ModelPart limb(int u, int v, float x, float y, int width, float dilation, float pivotX, float pivotY) {
		ModelPart part = new ModelPart(u, v);
		part.addCuboid(x, y, -2, width, 12, 4, dilation);
		part.setPivot(pivotX, pivotY, 0);
		fit(part);
		return part;
	}

	private static void fit(ModelPart part) {
		Quad[] faces = ((ModelPartAccessor) part).oldfaithful$faces();
		for (Quad face : faces) {
			for (Vertex vertex : face.vertices) {
				vertex.u = onThePixelGrid(vertex.u);
				vertex.v = onThePixelGrid(vertex.v / 2);
			}
		}
		Vertex[] bottom = faces[BOTTOM_FACE].vertices;
		float first = bottom[0].v;
		bottom[0].v = bottom[1].v = bottom[2].v;
		bottom[2].v = bottom[3].v = first;
	}

	private static float onThePixelGrid(float uv) {
		return Math.round(uv * 64) / 64.0F;
	}

	/**
	 * Also the hurt flash's passes, with blending on and the texture off: the faces are the same, and sorted or not they
	 * only draw where the first pass left their exact depth.
	 */
	@Override
	public void render(float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, float scale) {
		setAngles(limbAngle, limbDistance, animationProgress, headYaw, headPitch, scale);
		boolean twoSided = GL11.glGetBoolean(GL11.GL_LIGHT_MODEL_TWO_SIDE);
		GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, GL11.GL_TRUE);
		draw(scale, parts);
		GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, twoSided ? GL11.GL_TRUE : GL11.GL_FALSE);
	}

	/** Each layer on its limb, after everything that poses one (Faithful Core's listeners run inside the super call). */
	@Override
	public void setAngles(float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, float scale) {
		super.setAngles(limbAngle, limbDistance, animationProgress, headYaw, headPitch, scale);
		follow(jacket, body);
		follow(rightSleeve, rightArm);
		follow(leftSleeve, leftArm);
		follow(rightTrouser, rightLeg);
		follow(leftTrouser, leftLeg);
	}

	private static void follow(ModelPart layer, ModelPart limb) {
		layer.setPivot(limb.pivotX, limb.pivotY, limb.pivotZ);
		layer.pitch = limb.pitch;
		layer.yaw = limb.yaw;
		layer.roll = limb.roll;
		layer.visible = limb.visible;
		layer.hidden = limb.hidden;
	}

	/** The first-person hand's drawing is ours too, after beta's renderHand has posed the arm and drawn nothing. */
	public void beginHand() {
		handHidden = rightArm.hidden;
		rightArm.hidden = true;
	}

	/** Beta's frame has face culling on here, and neither the alpha test nor two-sided lighting. */
	public void finishHand(float scale) {
		rightArm.hidden = rightSleeve.hidden = handHidden;
		boolean culled = GL11.glIsEnabled(GL11.GL_CULL_FACE), alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
		boolean twoSided = GL11.glGetBoolean(GL11.GL_LIGHT_MODEL_TWO_SIDE);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, GL11.GL_TRUE);
		draw(scale, rightArm, rightSleeve);
		GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, twoSided ? GL11.GL_TRUE : GL11.GL_FALSE);
		if (!alphaTest) GL11.glDisable(GL11.GL_ALPHA_TEST);
		if (culled) GL11.glEnable(GL11.GL_CULL_FACE);
	}

	private void draw(float scale, ModelPart... drawn) {
		((java.nio.Buffer) matrix).clear();
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrix);
		// A perspective projection's last element is 0, an orthographic one's (the inventory's figure) 1.
		boolean orthographic = matrix.get(15) != 0;
		((java.nio.Buffer) matrix).clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
		matrix.get(modelview);

		faceCount = 0;
		for (ModelPart part : drawn) {
			if (part.visible && !part.hidden) collect(part, scale, orthographic);
		}
		sort();

		boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
		int source = GL11.glGetInteger(GL11.GL_BLEND_SRC), destination = GL11.glGetInteger(GL11.GL_BLEND_DST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glBegin(GL11.GL_QUADS);
		for (int i = 0; i < faceCount; i++) {
			int face = order[i];
			GL11.glNormal3f(normals[face * 3], normals[face * 3 + 1], normals[face * 3 + 2]);
			for (int corner = face * 4; corner < face * 4 + 4; corner++) {
				GL11.glTexCoord2f(uvs[corner * 2], uvs[corner * 2 + 1]);
				GL11.glVertex3f(corners[corner * 3], corners[corner * 3 + 1], corners[corner * 3 + 2]);
			}
		}
		GL11.glEnd();
		GL11.glBlendFunc(source, destination);
		if (!blend) GL11.glDisable(GL11.GL_BLEND);
	}

	/** A part's faces, where its pivot and rotation (in ModelPart.render's order) put them. */
	private void collect(ModelPart part, float scale, boolean orthographic) {
		float sx = (float) Math.sin(part.pitch), cx = (float) Math.cos(part.pitch);
		float sy = (float) Math.sin(part.yaw), cy = (float) Math.cos(part.yaw);
		float sz = (float) Math.sin(part.roll), cz = (float) Math.cos(part.roll);
		float m00 = cz * cy, m01 = cz * sy * sx - sz * cx, m02 = cz * sy * cx + sz * sx;
		float m10 = sz * cy, m11 = sz * sy * sx + cz * cx, m12 = sz * sy * cx - cz * sx;
		float m20 = -sy, m21 = cy * sx, m22 = cy * cx;
		float px = part.pivotX * scale, py = part.pivotY * scale, pz = part.pivotZ * scale;
		for (Quad quad : ((ModelPartAccessor) part).oldfaithful$faces()) {
			int face = faceCount++;
			float middleX = 0, middleY = 0, middleZ = 0;
			for (int i = 0; i < 4; i++) {
				Vertex vertex = quad.vertices[i];
				float x = (float) vertex.pos.x * scale, y = (float) vertex.pos.y * scale, z = (float) vertex.pos.z * scale;
				float ox = m00 * x + m01 * y + m02 * z + px, oy = m10 * x + m11 * y + m12 * z + py, oz = m20 * x + m21 * y + m22 * z + pz;
				int corner = face * 4 + i;
				corners[corner * 3] = ox;
				corners[corner * 3 + 1] = oy;
				corners[corner * 3 + 2] = oz;
				uvs[corner * 2] = vertex.u;
				uvs[corner * 2 + 1] = vertex.v;
				middleX += ox / 4;
				middleY += oy / 4;
				middleZ += oz / 4;
			}
			// As Quad.render finds its normal, turned with the part.
			int c = face * 12;
			float ax = corners[c + 6] - corners[c + 3], ay = corners[c + 7] - corners[c + 4], az = corners[c + 8] - corners[c + 5];
			float bx = corners[c] - corners[c + 3], by = corners[c + 1] - corners[c + 4], bz = corners[c + 2] - corners[c + 5];
			float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
			float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			normals[face * 3] = nx / length;
			normals[face * 3 + 1] = ny / length;
			normals[face * 3 + 2] = nz / length;

			float[] m = modelview;
			float eyeX = m[0] * middleX + m[4] * middleY + m[8] * middleZ + m[12];
			float eyeY = m[1] * middleX + m[5] * middleY + m[9] * middleZ + m[13];
			float eyeZ = m[2] * middleX + m[6] * middleY + m[10] * middleZ + m[14];
			distance[face] = orthographic ? -eyeZ : eyeX * eyeX + eyeY * eyeY + eyeZ * eyeZ;
		}
	}

	/** Farthest first and stable, like the merge sort it follows; an insertion sort, for a list this short. */
	private void sort() {
		for (int i = 0; i < faceCount; i++) {
			float d = distance[i];
			int at = i;
			while (at > 0 && distance[order[at - 1]] < d) {
				order[at] = order[at - 1];
				at--;
			}
			order[at] = i;
		}
	}
}
