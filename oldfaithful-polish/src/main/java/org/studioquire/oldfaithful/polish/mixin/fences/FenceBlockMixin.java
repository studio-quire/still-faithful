package org.studioquire.oldfaithful.polish.mixin.fences;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.material.Material;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.studioquire.oldfaithful.polish.Fences;

/**
 * #30: a fence collides as a post with arms where the world runs (and on a client only where the server agrees), and
 * is aimed at as one ({@link Fences}). FenceBlock overrides neither, so these are added to it as overrides of Block's.
 */
@Mixin(FenceBlock.class)
abstract class FenceBlockMixin extends Block {
	FenceBlockMixin(int id, Material material) {
		super(id, material);
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void addIntersectingBoundingBox(World world, int x, int y, int z, Box box, ArrayList boxes) {
		if (Fences.thinCollision(world)) Fences.addCollisions(world, x, y, z, this.id, box, boxes);
		else super.addIntersectingBoundingBox(world, x, y, z, box, boxes);
	}

	@Override
	public HitResult raycast(World world, int x, int y, int z, Vec3d startPos, Vec3d endPos) {
		return Fences.raycast(world, x, y, z, this.id, startPos, endPos);
	}
}
