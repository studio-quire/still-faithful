package org.studioquire.oldfaithful.fixes.mixin.fallingsand;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #67: falling sand or gravel clears a block of its own kind at its position on every tick, where it's meant to clear
 * only the block it fell from, on its first (later versions check the tick). Pushed by a piston into the sand above
 * it, or with sand placed in its path, it deleted that block. It now clears only on its first tick.
 * <p>
 * A vanilla client on a server with this fix still clears such a block in its own copy of the world as the falling
 * block passes (it runs the same tick for the entity the server sent it), so the server sends each kept block again
 * when it's passed and once more when the falling block lands; without that, the client would show a hole where the
 * block still is.
 */
@Mixin(FallingBlockEntity.class)
abstract class FallingBlockEntityMixin {
	@Shadow public int timeFalling;

	@Unique private List<int[]> oldfaithful$kept;

	// The tick's first four-argument setBlock is the clearing; the second places the block on landing.
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlock(IIII)Z", ordinal = 0))
	private boolean oldfaithful$clearOnlyFirst(World world, int x, int y, int z, int id, Operation<Boolean> original) {
		if (this.timeFalling <= 1) return original.call(world, x, y, z, id);
		if (!world.isRemote) {
			world.blockUpdateEvent(x, y, z);
			if (oldfaithful$kept == null) oldfaithful$kept = new ArrayList<>();
			if (oldfaithful$kept.size() < 64) oldfaithful$kept.add(new int[] {x, y, z});
		}
		return false;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void oldfaithful$resendKept(CallbackInfo ci) {
		if (oldfaithful$kept == null || !((FallingBlockEntity) (Object) this).dead) return;
		World world = ((FallingBlockEntity) (Object) this).world;
		for (int[] p : oldfaithful$kept) world.blockUpdateEvent(p[0], p[1], p[2]);
		oldfaithful$kept = null;
	}
}
