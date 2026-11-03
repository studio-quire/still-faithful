package org.studioquire.oldfaithful.fixes.mixin.spawnbox;

import net.minecraft.block.Block;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * #64: the test for any water or lava in a box took one off its lowest x, y and z where they're negative, though
 * MathHelper.floor already rounds negative numbers down. So west and north of 0, 0 it also tested the blocks beyond
 * the box's west and north sides: no mob spawned beside water or lava on those sides there (riverbanks, spawning floors
 * beside channels), while east and south of 0, 0 they did; a swimmer's lift out of water read it too. It now tests the
 * box's own blocks everywhere. (The server's test for any block in a box, used by its flying check, has the same slip
 * and is left as beta: fixing it would only make that check stricter for honest players.)
 */
@Mixin(World.class)
abstract class WorldMixin {
	@Shadow
	public abstract int getBlockId(int x, int y, int z);

	@Inject(method = "isBoxSubmergedInFluid", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$ownBlocksOnly(Box box, CallbackInfoReturnable<Boolean> cir) {
		int maxX = MathHelper.floor(box.maxX + 1.0), maxY = MathHelper.floor(box.maxY + 1.0), maxZ = MathHelper.floor(box.maxZ + 1.0);
		for (int x = MathHelper.floor(box.minX); x < maxX; x++) {
			for (int y = MathHelper.floor(box.minY); y < maxY; y++) {
				for (int z = MathHelper.floor(box.minZ); z < maxZ; z++) {
					Block block = Block.BLOCKS[this.getBlockId(x, y, z)];
					if (block != null && block.material.isFluid()) {
						cir.setReturnValue(true);
						return;
					}
				}
			}
		}
		cir.setReturnValue(false);
	}
}
