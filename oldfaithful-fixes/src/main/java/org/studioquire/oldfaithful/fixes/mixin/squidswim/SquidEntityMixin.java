package org.studioquire.oldfaithful.fixes.mixin.squidswim;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #77: a squid asks whether it's in water with its box shrunk by 0.6 at the top and at the bottom, but it's 0.95 tall,
 * so the box is inside out, and with its feet 0.40 to 0.65 up a block the world's test looks at no block at all. The
 * answer is no, gravity takes over, and a swim of at most 0.1 a tick never gets across: a squid only goes down, then
 * hops about the floor. It now asks about its middle: a flat box at half its height, for the world's test (so the flow
 * still pushes it as before), and under the water's real surface, where the world's test only looks at the block.
 * <p>
 * The real surface because baseTick's own water test goes by the block, at about the squid's middle, and passing it on the
 * way back in makes a splash and 40 particles. Going by the block too, a squid at the top kept splashing. By the real
 * surface it turns back short of that test (docs/bugs.md #77).
 */
@Mixin(SquidEntity.class)
abstract class SquidEntityMixin {
	@WrapOperation(method = "isSubmergedInWater", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;updateMovementInFluid(Lnet/minecraft/util/math/Box;Lnet/minecraft/block/material/Material;Lnet/minecraft/entity/Entity;)Z"))
	private boolean oldfaithful$middleInWater(World world, Box insideOut, Material water, Entity squid, Operation<Boolean> original) {
		Box box = squid.boundingBox;
		double middle = (box.minY + box.maxY) / 2.0;
		Box flat = Box.createCached(box.minX, middle, box.minZ, box.maxX, middle, box.maxZ);
		return original.call(world, flat, water, squid) && oldfaithful$underSurface(world, flat, middle);
	}

	/** Any column under the box has water at this height, its surface above it (water with water over it is full). */
	@Unique
	private static boolean oldfaithful$underSurface(World world, Box flat, double middle) {
		int y = MathHelper.floor(middle);
		for (int x = MathHelper.floor(flat.minX); x <= MathHelper.floor(flat.maxX); x++) {
			for (int z = MathHelper.floor(flat.minZ); z <= MathHelper.floor(flat.maxZ); z++) {
				if (world.getMaterial(x, y, z) != Material.WATER) continue;
				if (world.getMaterial(x, y + 1, z) == Material.WATER) return true;
				if (middle < y + 1 - LiquidBlock.getFluidHeightFromMeta(world.getBlockMeta(x, y, z))) return true;
			}
		}
		return false;
	}
}
