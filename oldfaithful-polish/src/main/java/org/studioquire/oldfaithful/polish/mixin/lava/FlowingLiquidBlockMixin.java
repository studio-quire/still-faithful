package org.studioquire.oldfaithful.polish.mixin.lava;

import java.util.Random;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.block.FlowingLiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #20, lava: when flowing lava should thin out, beta lets it with one chance in four; the other three times it keeps
 * its level and schedules nothing, so lava cut off from its source can sit there for good, until something beside it
 * changes. Later versions thin it out every time, waiting four times its usual delay before the next step on those
 * three in four. Here the same: the draw is made as beta makes it (one {@code nextInt(4)}, so the world's random runs
 * as before), and where beta would refuse, the lava thins and its next tick comes four times later.
 */
@Mixin(FlowingLiquidBlock.class)
abstract class FlowingLiquidBlockMixin {
	@WrapOperation(method = "onTick", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I"))
	private int oldfaithful$thinSlowly(Random random, int bound, Operation<Integer> original, @Share("slow") LocalBooleanRef slow) {
		int draw = original.call(random, bound);
		// Only the lava check draws in onTick; it refuses on anything but 0.
		if (draw != 0) slow.set(true);
		return 0;
	}

	@WrapOperation(method = "onTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;scheduleBlockUpdate(IIIII)V"))
	private void oldfaithful$laterNextStep(World world, int x, int y, int z, int id, int delay, Operation<Void> original,
			@Share("slow") LocalBooleanRef slow) {
		original.call(world, x, y, z, id, slow.get() ? delay * 4 : delay);
	}
}
