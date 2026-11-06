package org.studioquire.oldfaithful.fixes.mixin.lowroof;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #74: a step-up rises at most the step height, moves, then goes down the whole step height, whatever the rise was. A
 * block overhead can clip the rise (a roof 2 above the floor: 0.2), and the blocks collected for the step leave out the
 * floor being stood on (touching isn't intersecting), so the way down ended 0.3 below where it began: inside the floor
 * or the block being stepped towards. From there collision ignores that block, the entity falls or walks further in,
 * and a player is shoved back out by the push-out. The way down is now as long as the way up. With nothing overhead
 * the two are the same number, so ordinary steps don't change at all; a step onto something under a low roof still
 * lands on it.
 * <p>
 * Runs wherever an entity with a step height moves: a player's own client, mobs where the world runs. A server's
 * player has no step height, so nothing here depends on the other side.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	// The third read of stepHeight is beta's "dy = -stepHeight"; just before it, dy still holds what the rise came to.
	@Inject(method = "move", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/entity/Entity;stepHeight:F", ordinal = 2))
	private void oldfaithful$rise(double dx0, double dy0, double dz0, CallbackInfo ci,
			@Local(ordinal = 1, argsOnly = true) double dy, @Share("rise") LocalDoubleRef rise) {
		rise.set(dy);
	}

	@ModifyVariable(method = "move", ordinal = 1, argsOnly = true,
			slice = @Slice(from = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/entity/Entity;stepHeight:F", ordinal = 2)),
			at = @At(value = "STORE", ordinal = 0))
	private double oldfaithful$downAsFarAsUp(double down, @Share("rise") LocalDoubleRef rise) {
		return -rise.get();
	}
}
