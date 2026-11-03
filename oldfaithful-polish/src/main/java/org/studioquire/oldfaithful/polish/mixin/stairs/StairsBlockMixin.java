package org.studioquire.oldfaithful.polish.mixin.stairs;

import net.minecraft.block.Block;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #13: stairs hand their drops to the block they're made of (a plank, a cobblestone). Here they drop one of themselves,
 * with the same chance (an explosion's luck) and the same one draw of the world's random as beta's single drop.
 */
@Mixin(StairsBlock.class)
abstract class StairsBlockMixin extends Block {
	StairsBlockMixin(int id, Material material) {
		super(id, material);
	}

	@Inject(method = "dropStacks(Lnet/minecraft/world/World;IIIIF)V", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$dropItself(World world, int x, int y, int z, int meta, float luck, CallbackInfo ci) {
		ci.cancel();
		if (world.isRemote) return;
		if (!(world.random.nextFloat() > luck)) this.dropStack(world, x, y, z, new ItemStack(this.id, 1, 0));
	}
}
