package org.studioquire.oldfaithful.polish.mixin.boatsteering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.BoatSteering;

/**
 * Boat Steering ({@link BoatSteering}), where the world runs. A boat is steered when its rider is a player whose keys
 * are known (single player; on a server, a client that sends its look every riding tick), or when nobody rides it and
 * it's in water; otherwise it's beta's, paddled by the rider's motion. A steered boat, before beta's paddling would
 * run: in water keeps {@link BoatSteering#MOMENTUM} of its speed, turns by its turn rate and is pushed along its new
 * heading, at most {@link BoatSteering#TOP_SPEED} in any direction. Beta's paddling, its 0.4 limit on each axis, its
 * 0.99 drag after the move (in water: the momentum replaces it) and its turn toward where the boat moved are skipped;
 * the rest of beta's tick (floating, the ground's halving, the move, splashes, breaking, pushing other boats) stands.
 */
@Mixin(BoatEntity.class)
abstract class BoatEntityMixin extends Entity implements BoatSteering.Boat {
	@Unique private boolean oldfaithful$steered, oldfaithful$inWater;
	@Unique private float oldfaithful$turnRate, oldfaithful$heading;

	BoatEntityMixin(World world) {
		super(world);
	}

	@Override
	public boolean oldfaithful$steered() {
		return oldfaithful$steered;
	}

	@Override
	public float oldfaithful$turnRate() {
		return oldfaithful$turnRate;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void oldfaithful$notYet(CallbackInfo ci) {
		oldfaithful$steered = false;
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isFluidInBox(Lnet/minecraft/util/math/Box;Lnet/minecraft/block/material/Material;)Z"))
	private boolean oldfaithful$countWetSlices(World world, Box box, Material fluid, Operation<Boolean> original,
			@Share("wet") LocalIntRef wet) {
		boolean in = original.call(world, box, fluid);
		if (in) wet.set(wet.get() + 1);
		return in;
	}

	/** Where beta reads the rider to paddle: only on the side where the world runs. */
	@Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/BoatEntity;passenger:Lnet/minecraft/entity/Entity;",
			opcode = Opcodes.GETFIELD, ordinal = 0))
	private void oldfaithful$steer(CallbackInfo ci, @Share("wet") LocalIntRef wet) {
		boolean inWater = wet.get() > 0;
		int keys = oldfaithful$keys(this.passenger);
		if (this.world.isRemote || keys == BoatSteering.BETA || this.passenger == null && !inWater) {
			oldfaithful$turnRate = 0;
			return;
		}
		oldfaithful$steered = true;
		oldfaithful$inWater = inWater;
		if (inWater) {
			this.velocityX *= BoatSteering.MOMENTUM;
			this.velocityZ *= BoatSteering.MOMENTUM;
		}
		double turnMomentum = inWater || !this.onGround ? BoatSteering.MOMENTUM : BoatSteering.GROUND;
		oldfaithful$turnRate = BoatSteering.turnRate(oldfaithful$turnRate, keys, turnMomentum);
		oldfaithful$heading = this.yaw + oldfaithful$turnRate;
		double push = BoatSteering.push(keys);
		this.velocityX += BoatSteering.headingX(oldfaithful$heading) * push;
		this.velocityZ += BoatSteering.headingZ(oldfaithful$heading) * push;
		double speed = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
		if (speed > BoatSteering.TOP_SPEED) {
			this.velocityX *= BoatSteering.TOP_SPEED / speed;
			this.velocityZ *= BoatSteering.TOP_SPEED / speed;
		}
	}

	/** The rider's keys, none for an empty boat, or BETA for a rider whose keys aren't known. */
	@Unique
	private static int oldfaithful$keys(Entity rider) {
		if (rider == null) return 0;
		if (rider instanceof BoatSteering.Rider) return ((BoatSteering.Rider) rider).oldfaithful$boatKeys();
		if (!(rider instanceof PlayerEntity)) return BoatSteering.BETA;
		LivingEntityAccessor input = (LivingEntityAccessor) rider;
		return BoatSteering.keys(input.oldfaithful$forwardSpeed(), input.oldfaithful$sidewaysSpeed());
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/BoatEntity;passenger:Lnet/minecraft/entity/Entity;",
			opcode = Opcodes.GETFIELD, ordinal = 0))
	private Entity oldfaithful$noBetaPaddling(Entity passenger) {
		return oldfaithful$steered ? null : passenger;
	}

	@ModifyConstant(method = "tick", constant = @Constant(doubleValue = 0.4))
	private double oldfaithful$noLimitPerAxis(double limit) {
		return oldfaithful$steered ? Double.POSITIVE_INFINITY : limit;
	}

	@ModifyConstant(method = "tick", constant = @Constant(doubleValue = (double) 0.99F))
	private double oldfaithful$momentumInsteadOfDrag(double drag) {
		return oldfaithful$steered && oldfaithful$inWater ? 1.0 : drag;
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/BoatEntity;setRotation(FF)V"))
	private void oldfaithful$keepHeading(BoatEntity boat, float yaw, float pitch, Operation<Void> original) {
		original.call(boat, oldfaithful$steered ? oldfaithful$heading : yaw, pitch);
	}
}
