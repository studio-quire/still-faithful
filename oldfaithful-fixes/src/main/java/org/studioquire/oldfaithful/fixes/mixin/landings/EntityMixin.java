package org.studioquire.oldfaithful.fixes.mixin.landings;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #56: water cancels a fall only when an entity is in it at the start of a tick (baseTick's test: the box shrunk 0.4
 * at top and bottom). Falling into one-deep water, the tick that lands moves the entity from above that window to the
 * pool's floor, and the landing counted the whole fall with no look at the water it now stands in: about half of all
 * heights hurt exactly as on stone. The landing now makes baseTick's test once more (without its push), so landing
 * in water is safe from any height, as later versions have it. Nothing that landed safely before is hurt now.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Shadow public World world;
	@Shadow public Box boundingBox;
	@Shadow public float fallDistance;

	@Inject(method = "fall", at = @At("HEAD"))
	private void oldfaithful$landInWater(double heightDifference, boolean onGround, CallbackInfo ci) {
		if (!onGround || this.fallDistance <= 0.0F) return;
		Box box = this.boundingBox.expand(0.0, -0.4F, 0.0).contract(0.001, 0.001, 0.001);
		// As World.updateMovementInFluid decides it: any water block the box reaches, its region loaded.
		if (!this.world.isRegionLoaded(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
				MathHelper.floor(box.maxX + 1.0), MathHelper.floor(box.maxY + 1.0), MathHelper.floor(box.maxZ + 1.0))) return;
		if (this.world.isMaterialInBox(box, Material.WATER)) this.fallDistance = 0.0F;
	}
}
