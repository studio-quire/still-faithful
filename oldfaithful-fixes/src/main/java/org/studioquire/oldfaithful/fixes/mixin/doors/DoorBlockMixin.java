package org.studioquire.oldfaithful.fixes.mixin.doors;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #55: a door's top half passes a redstone neighbour's update on to its bottom half, without checking that the block
 * below is the door. A top half on its own (an edited or converted world; play never leaves one) passed it to whatever
 * stood below, which then acted as a door whose top was missing: it was removed and a door dropped in its place (a
 * chest, spilling what it held). The update now goes down only to this door's own bottom half.
 */
@Mixin(DoorBlock.class)
abstract class DoorBlockMixin extends Block {
	DoorBlockMixin(int id, Material material) {
		super(id, material);
	}

	@WrapOperation(method = "neighborUpdate", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/DoorBlock;neighborUpdate(Lnet/minecraft/world/World;IIII)V"))
	private void oldfaithful$onlyToOwnBottom(DoorBlock door, World world, int x, int y, int z, int id, Operation<Void> original) {
		if (world.getBlockId(x, y, z) == this.id) original.call(door, world, x, y, z, id);
	}
}
