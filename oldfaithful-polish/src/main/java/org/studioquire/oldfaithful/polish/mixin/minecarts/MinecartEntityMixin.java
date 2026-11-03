package org.studioquire.oldfaithful.polish.mixin.minecarts;

import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.studioquire.oldfaithful.polish.Minecarts;

/**
 * #31, carts meeting carts ({@link Minecarts}). Beta's gate is {@code (dx * other.velocityZ + dz * other.prevX)^2 > 5}:
 * its limit becomes one the square never passes when the carts are lined up along this cart's rail, and one it always
 * passes (it's never negative) when they aren't, so the rail alone decides. A mod that zeroes the gate's value instead
 * (UniTweaks' and RetroTweaks' "Minecart Boosters") then changes nothing.
 */
@Mixin(MinecartEntity.class)
abstract class MinecartEntityMixin extends Entity {
	@Shadow
	@Final
	private static int[][][] ADJACENT_RAIL_POSITIONS_BY_SHAPE;

	MinecartEntityMixin(World world) {
		super(world);
	}

	@ModifyConstant(method = "onCollision", constant = @Constant(doubleValue = 5.0))
	private double oldfaithful$linedUp(double limit, Entity other) {
		return Minecarts.linedUp(this, other, ADJACENT_RAIL_POSITIONS_BY_SHAPE) ? Double.POSITIVE_INFINITY : -1.0;
	}
}
