package org.studioquire.oldfaithful.saves.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.PistonBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * docs/bugs.md #53: a piston never extends into a chunk that isn't loaded. Every cell the
 * push reads is checked where it's read, in both the check before extending and the push itself; one in a chunk that
 * isn't there reads as obsidian, which a piston can't move, so the piston stays retracted, as it does in front of
 * anything immovable. In a loaded world nothing changes. Both sides.
 */
@Mixin(PistonBlock.class)
abstract class PistonBlockMixin {
	@WrapOperation(method = "canExtend", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockId(III)I"))
	private static int oldfaithful$checkEdge(World world, int x, int y, int z, Operation<Integer> original) {
		return edge(world, x, y, z) ? Block.OBSIDIAN.id : original.call(world, x, y, z);
	}

	@WrapOperation(method = "push", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockId(III)I"))
	private int oldfaithful$pushEdge(World world, int x, int y, int z, Operation<Integer> original) {
		return edge(world, x, y, z) ? Block.OBSIDIAN.id : original.call(world, x, y, z);
	}

	private static boolean edge(World world, int x, int y, int z) {
		return y >= 0 && y < 128 && world.getChunk(x >> 4, z >> 4) instanceof EmptyChunk;
	}
}
