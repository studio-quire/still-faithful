package org.studioquire.oldfaithful.fixes.mixin.mobphysics;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #15, on a multiplayer client: a mob the server sends (vanilla marks it {@code interpolateOnly}) skips its AI there,
 * but still runs its own physics every tick on top of the positions the server sends: gravity, velocity, friction,
 * collisions and the pushes of other mobs. Penned mobs shove each other apart and get pulled back, a jumping mob is
 * dragged down, knockback lands twice. The server's positions are the truth, so the client's own move no longer
 * displaces such a mob: it still runs (whether it's on the ground, what it touches, footsteps and fire are found out as
 * before), but the mob is put back where the server's path has it. Its velocity only ever feeds that probe, so
 * knockback shows through the server's positions, a few ticks later than the client's guess used to.
 * <p>
 * Remote mobs only move through {@code move} in their travel step (riding sets the position directly), so this is the
 * one place. Players from the server are another class, which never had this physics.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Shadow public double x;
	@Shadow public double y;
	@Shadow public double z;
	@Shadow public float cameraOffset;
	@Shadow @Final public Box boundingBox;

	@Unique private boolean oldfaithful$probing;
	@Unique private double oldfaithful$x, oldfaithful$y, oldfaithful$z;
	@Unique private double oldfaithful$minX, oldfaithful$minY, oldfaithful$minZ, oldfaithful$maxX, oldfaithful$maxY, oldfaithful$maxZ;
	@Unique private float oldfaithful$cameraOffset;

	@Inject(method = "move", at = @At("HEAD"))
	private void oldfaithful$keepServerPosition(double dx, double dy, double dz, CallbackInfo ci) {
		Object self = this;
		oldfaithful$probing = self instanceof LivingEntity && ((LivingEntity) self).interpolateOnly;
		if (!oldfaithful$probing) return;
		oldfaithful$x = x;
		oldfaithful$y = y;
		oldfaithful$z = z;
		oldfaithful$minX = boundingBox.minX;
		oldfaithful$minY = boundingBox.minY;
		oldfaithful$minZ = boundingBox.minZ;
		oldfaithful$maxX = boundingBox.maxX;
		oldfaithful$maxY = boundingBox.maxY;
		oldfaithful$maxZ = boundingBox.maxZ;
		oldfaithful$cameraOffset = cameraOffset;
	}

	@Inject(method = "move", at = @At("RETURN"))
	private void oldfaithful$putBack(double dx, double dy, double dz, CallbackInfo ci) {
		if (!oldfaithful$probing) return;
		oldfaithful$probing = false;
		x = oldfaithful$x;
		y = oldfaithful$y;
		z = oldfaithful$z;
		boundingBox.set(oldfaithful$minX, oldfaithful$minY, oldfaithful$minZ, oldfaithful$maxX, oldfaithful$maxY, oldfaithful$maxZ);
		cameraOffset = oldfaithful$cameraOffset;
	}
}
