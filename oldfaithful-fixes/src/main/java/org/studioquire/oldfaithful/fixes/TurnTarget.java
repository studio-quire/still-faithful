package org.studioquire.oldfaithful.fixes;

/** #50: living entities on a multiplayer client keep the last rotation the server sent (mixin.turns). */
public interface TurnTarget {
	void oldfaithful$setTurnTarget(float yaw, float pitch);

	boolean oldfaithful$hasTurnTarget();

	float oldfaithful$turnYaw();

	float oldfaithful$turnPitch();
}
