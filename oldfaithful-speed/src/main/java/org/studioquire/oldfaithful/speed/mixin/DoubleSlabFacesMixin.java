package org.studioquire.oldfaithful.speed.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.material.Material;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The double slab's faces are meant to follow the ordinary rule for a full block (shown unless an opaque block covers
 * them), but the slab's face check computes that answer for it and drops it (a missing return), then applies the half
 * slab's rule, which always shows the top. So a double slab under an opaque block meshes a top face nobody can see: it
 * lies between two opaque blocks, whose other faces enclose it, and the block above draws no face there (it is culled
 * against the opaque double slab), so depth hides it from every outside view. Answering with the ordinary rule drops
 * those faces from the mesh; every face anyone can see is the same, and so is every pixel. The half slab, and any slab
 * a mod adds, keeps its own rule.
 */
@Mixin(SlabBlock.class)
abstract class DoubleSlabFacesMixin extends Block {
	@Shadow
	private boolean doubleSlab;

	DoubleSlabFacesMixin(int id, Material material) {
		super(id, material);
	}

	@Inject(method = "isSideVisible", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$fullBlockRule(BlockView view, int x, int y, int z, int side, CallbackInfoReturnable<Boolean> cir) {
		if (this.doubleSlab) cir.setReturnValue(super.isSideVisible(view, x, y, z, side));
	}
}
