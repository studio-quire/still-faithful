package org.studioquire.oldfaithful.polish;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.Quad;
import net.minecraft.client.model.Vertex;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import org.studioquire.oldfaithful.polish.mixin.skins.ModelPartAccessor;

/**
 * The player's model for a {@link ModernSkin}, a model of its own so beta's, which zombies, skeletons and armour are
 * built on, stays as it is. Beta's boxes with 1.8's left limbs, layers and slim arms, at 1.8's places except that the
 * legs keep beta's stance. The cape keeps beta's mapping: it has a 64x32 texture of its own.
 *
 * Two things beta's ModelPart can't do are done to its faces here. It divides V by 32, so each is halved for a texture
 * 64 high. And later versions turned every box's bottom face over (its front edge at the bottom of its texture, not
 * the top), which skins have been painted for since.
 */
public final class ModernPlayerModel extends BipedEntityModel {
	/** Later versions' distance of a layer from the limb under it; the hat's is 0.5. */
	private static final float LAYER = 0.25F;
	private static final int BOTTOM_FACE = 3;

	/** {@code PlayerEntityRenderer}, which holds the models and chooses for each player drawn. */
	public interface Wearer {
		void oldfaithful$wear(PlayerEntity player, TextureManager textures);

		void oldfaithful$wearBeta();
	}

	public final boolean slim;
	private final ModelPart jacket, rightSleeve, leftSleeve, rightTrouser, leftTrouser;

	public ModernPlayerModel(boolean slim) {
		super(0.0F);
		this.slim = slim;
		int arm = slim ? 3 : 4;
		float rightArmX = slim ? -2.0F : -3.0F, armY = slim ? 2.5F : 2.0F;
		rightArm = limb(40, 16, rightArmX, -2, arm, 0, -5, armY);
		leftArm = limb(32, 48, -1, -2, arm, 0, 5, armY);
		leftLeg = limb(16, 48, -2, 0, 4, 0, 2, 12);
		rightSleeve = limb(40, 32, rightArmX, -2, arm, LAYER, -5, armY);
		leftSleeve = limb(48, 48, -1, -2, arm, LAYER, 5, armY);
		rightTrouser = limb(0, 32, -2, 0, 4, LAYER, -2, 12);
		leftTrouser = limb(0, 48, -2, 0, 4, LAYER, 2, 12);
		jacket = new ModelPart(16, 32);
		jacket.addCuboid(-4, 0, -2, 8, 12, 4, LAYER);
		for (ModelPart part : new ModelPart[] {head, hat, body, rightLeg, ears, jacket}) fit(part);
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
			for (Vertex vertex : face.vertices) vertex.v /= 2;
		}
		Vertex[] bottom = faces[BOTTOM_FACE].vertices;
		float first = bottom[0].v;
		bottom[0].v = bottom[1].v = bottom[2].v;
		bottom[2].v = bottom[3].v = first;
	}

	@Override
	public void render(float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, float scale) {
		super.render(limbAngle, limbDistance, animationProgress, headYaw, headPitch, scale);
		jacket.render(scale);
		rightSleeve.render(scale);
		leftSleeve.render(scale);
		rightTrouser.render(scale);
		leftTrouser.render(scale);
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

	/** The first-person hand's sleeve, just after the arm. */
	public void renderRightSleeve(float scale) {
		rightSleeve.render(scale);
	}
}
