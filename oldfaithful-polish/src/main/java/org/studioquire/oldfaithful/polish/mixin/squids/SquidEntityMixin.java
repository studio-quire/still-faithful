package org.studioquire.oldfaithful.polish.mixin.squids;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SquidEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Squids;

/**
 * Squid Surfacing ({@link Squids}): right after a squid in water sets its speed from its swim, its rise eases off below
 * the surface. Inside beta's {@code !interpolateOnly}, so only where the world runs; before the tilt is worked out from
 * the speed, so it lies flatter as it slows.
 */
@Mixin(SquidEntity.class)
abstract class SquidEntityMixin {
	@Inject(method = "tickMovement", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
			target = "Lnet/minecraft/entity/passive/SquidEntity;velocityZ:D", ordinal = 0, shift = At.Shift.AFTER))
	private void oldfaithful$easeBelowSurface(CallbackInfo ci) {
		Entity squid = (Entity) (Object) this;
		squid.velocityY = Squids.ease(squid.world, squid.boundingBox, squid.velocityY);
	}
}
