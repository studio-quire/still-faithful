package org.studioquire.oldfaithful.fixes.mixin.steps;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #38: moving adds the distance walked to a counter and plays a footstep each time it passes the next whole unit,
 * one step a tick at most, but only while there's a block underfoot. In the air (down a staircase, over a jump) the
 * distance kept adding up, and back on the ground the backlog played one step every tick: a rattle at the foot of the
 * stairs. The next step is now kept within one of the distance walked, so landing owes at most the one step a
 * landing would make.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Shadow
	public float horizontalSpeed;
	@Shadow
	private int nextStepSoundDistance;

	@Inject(method = "move", at = @At("RETURN"))
	private void oldfaithful$noStepBacklog(double dx, double dy, double dz, CallbackInfo ci) {
		if (this.horizontalSpeed - this.nextStepSoundDistance >= 1.0F) this.nextStepSoundDistance = (int) this.horizontalSpeed;
	}
}
