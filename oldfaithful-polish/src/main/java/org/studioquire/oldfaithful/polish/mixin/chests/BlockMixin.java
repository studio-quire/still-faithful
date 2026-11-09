package org.studioquire.oldfaithful.polish.mixin.chests;

import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Chests;
import org.studioquire.oldfaithful.polish.PolishConfig;

/** #69: a chest placed by a player or mob gets a facing toward them, and with blocks.chestPairs pairs as later versions pair it ({@link Chests}). ChestBlock doesn't override this. */
@Mixin(Block.class)
abstract class BlockMixin {
	@Inject(method = "onPlaced(Lnet/minecraft/world/World;IIILnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"))
	private void oldfaithful$chestFacing(World world, int x, int y, int z, LivingEntity placer, CallbackInfo ci) {
		Object self = this;
		if (!(self instanceof ChestBlock) || placer == null) return;
		if (PolishConfig.applied("blocks.chestPairs")) Chests.placedPairing(world, x, y, z, placer, ((Block) self).id);
		else Chests.placed(world, x, y, z, placer, ((Block) self).id);
	}
}
