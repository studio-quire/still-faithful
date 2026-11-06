package org.studioquire.oldfaithful.fixes.mixin.wallrounding;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #75: an entity's box moves face by face, but its position is the middle of the box, and setPosition rebuilds the box
 * from that middle. Opening a saved world, reading a mob from its chunk or taking a server's position packet, the
 * rebuilt box could land a side face a rounding step past a wall it had been touching. Collision ignores a block already
 * overlapped, so the next step walked in, and a player reached suffocation before the push-out's probes found the block.
 * <p>
 * Every block's sides lie on sixteenths, so a side face just past a sixteenth is moved back onto it. {@link #PAST} is well
 * above the rounding (a few steps of the coordinate, about 4e-9 at the world's edge) and far below anything a move can
 * do. The box only ever narrows, by no more than that; the position itself isn't changed, so nothing saved or sent is,
 * and the world isn't read, so this is safe while a chunk loads. Height stays as beta: floors have few enough bits that
 * the rebuild lands on them exactly.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	private static final double PAST = 1.0E-7;

	@Shadow
	@Final
	public Box boundingBox;

	@Inject(method = "setPosition", at = @At("TAIL"))
	private void oldfaithful$sidesNotPastAWall(double x, double y, double z, CallbackInfo ci) {
		Box box = this.boundingBox;
		box.minX = lowFace(box.minX);
		box.minZ = lowFace(box.minZ);
		box.maxX = highFace(box.maxX);
		box.maxZ = highFace(box.maxZ);
	}

	private static double lowFace(double face) {
		double sixteenth = Math.ceil(face * 16.0) / 16.0;
		return sixteenth - face <= PAST ? sixteenth : face;
	}

	private static double highFace(double face) {
		double sixteenth = Math.floor(face * 16.0) / 16.0;
		return face - sixteenth <= PAST ? sixteenth : face;
	}
}
