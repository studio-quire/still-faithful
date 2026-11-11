package org.studioquire.oldfaithful.polish.mixin.boathandling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.BoatSteering;
import org.studioquire.oldfaithful.polish.Boats;

/**
 * #12, handling ({@link Boats}): where the world runs (the server in multiplayer), a boat in water that nobody is
 * paddling slows to a stop in a few blocks, and a boat partly in the water floats at a height of its own, its rise and
 * fall damped. How much of the boat is in water is read from beta's own five slice tests at the start of its tick. A
 * boat Boat Steering moved this tick has that switch's own momentum instead of the coasting; the float and the damping
 * apply to it too.
 */
@Mixin(BoatEntity.class)
abstract class BoatEntityMixin extends Entity {
	BoatEntityMixin(World world) {
		super(world);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isFluidInBox(Lnet/minecraft/util/math/Box;Lnet/minecraft/block/material/Material;)Z"))
	private boolean oldfaithful$countWetSlices(World world, Box box, Material fluid, Operation<Boolean> original,
			@Share("wet") LocalIntRef wet) {
		boolean in = original.call(world, box, fluid);
		if (in) wet.set(wet.get() + 1);
		return in;
	}

	/**
	 * Just after beta's push up by its count of wet slices, before the paddling (only reached where the world runs):
	 * partly in the water, the push is redone from the share worked out from the surface ({@link Boats#wetShare}).
	 */
	@Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/BoatEntity;passenger:Lnet/minecraft/entity/Entity;",
			opcode = Opcodes.GETFIELD, ordinal = 0))
	private void oldfaithful$float(CallbackInfo ci, @Share("wet") LocalIntRef wet) {
		int slices = wet.get();
		if (this.world.isRemote || slices == 0 || slices == 5) return;
		double surface = oldfaithful$surface();
		if (surface == Double.NEGATIVE_INFINITY) return;
		this.velocityY += Boats.PUSH * 2 * (Boats.wetShare(surface, this.y, slices) - slices / 5.0);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void oldfaithful$handling(CallbackInfo ci, @Share("wet") LocalIntRef wet) {
		if (this.world.isRemote || this.dead || wet.get() == 0) return;
		Entity rider = this.passenger;
		boolean paddling = rider != null
				&& rider.velocityX * rider.velocityX + rider.velocityZ * rider.velocityZ > Boats.PADDLING * Boats.PADDLING;
		boolean steered = (Object) this instanceof BoatSteering.Boat && ((BoatSteering.Boat) (Object) this).oldfaithful$steered();
		if (!paddling && !steered) {
			this.velocityX *= Boats.COAST;
			this.velocityZ *= Boats.COAST;
		}
		// Partly in the water: settling at the surface.
		if (wet.get() < 5) this.velocityY *= Boats.BOB;
	}

	/**
	 * The water's surface at the boat as beta's slice tests see it: the highest top of any water in the blocks they
	 * look at (under the box, from 0.125 below it), a source's or a falling column's at the top of its block, flowing
	 * water's lower by an eighth for each step from its source. Negative infinity with none.
	 */
	@Unique
	private double oldfaithful$surface() {
		Box box = this.boundingBox;
		double surface = Double.NEGATIVE_INFINITY;
		int x1 = MathHelper.floor(box.maxX), y1 = MathHelper.floor(box.maxY - 0.125), z1 = MathHelper.floor(box.maxZ);
		for (int x = MathHelper.floor(box.minX); x <= x1; x++) {
			for (int z = MathHelper.floor(box.minZ); z <= z1; z++) {
				for (int y = MathHelper.floor(box.minY - 0.125); y <= y1; y++) {
					Block block = Block.BLOCKS[this.world.getBlockId(x, y, z)];
					if (block == null || block.material != Material.WATER) continue;
					int meta = this.world.getBlockMeta(x, y, z);
					surface = Math.max(surface, meta < 8 ? y + 1 - meta / 8.0 : y + 1);
				}
			}
		}
		return surface;
	}
}
