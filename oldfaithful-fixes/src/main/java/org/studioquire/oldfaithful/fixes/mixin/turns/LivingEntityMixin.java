package org.studioquire.oldfaithful.fixes.mixin.turns;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.studioquire.oldfaithful.fixes.TurnTarget;

/** #50: the last rotation the server sent for this entity, on a multiplayer client. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements TurnTarget {
	@Unique private boolean oldfaithful$hasTurn;
	@Unique private float oldfaithful$yaw, oldfaithful$pitch;

	@Override
	public void oldfaithful$setTurnTarget(float yaw, float pitch) {
		oldfaithful$hasTurn = true;
		oldfaithful$yaw = yaw;
		oldfaithful$pitch = pitch;
	}

	@Override
	public boolean oldfaithful$hasTurnTarget() {
		return oldfaithful$hasTurn;
	}

	@Override
	public float oldfaithful$turnYaw() {
		return oldfaithful$yaw;
	}

	@Override
	public float oldfaithful$turnPitch() {
		return oldfaithful$pitch;
	}
}
