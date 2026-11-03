package org.studioquire.oldfaithful.fixes.mixin.dismount;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * #27: getting off a vehicle puts the rider's feet on the vehicle's top, but by way of the eye: the position is set to
 * the top plus the eye height, and the box from that minus the eye height. Where those two roundings come out one step
 * low (about half the time for a top between y 62.4 and 64, a boat on sea-level water), the rider's box overlaps the
 * boat's instead of resting on it, collision ignores what it already overlaps, and the rider drops through the boat.
 * The height asked for is nudged up by the least amount that puts the box on top.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Shadow
	public float standingEyeHeight;
	@Shadow
	public float cameraOffset;

	@ModifyArg(method = "setVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setPositionAndAnglesKeepPrevAngles(DDDFF)V"), index = 1)
	private double oldfaithful$onTop(double top) {
		double y = top;
		for (int i = 0; i < 8 && y + this.standingEyeHeight - this.standingEyeHeight + this.cameraOffset < top; i++) y = Math.nextUp(y);
		return y;
	}
}
