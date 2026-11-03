package org.studioquire.oldfaithful.fixes.mixin.sneak;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #57: sneaking on the ground, a move is trimmed so the feet keep a block under them, but the x step and the z step
 * were each tested alone. At an inside corner (an L of blocks) one block holds the x step and the other the z step,
 * so a diagonal step went over the gap between them and the player dropped. After beta's two trims, the step is now
 * also trimmed while the two together have nothing under them, as later versions do.
 * <p>
 * Client only: the player's own client moves the player, in single player and on a server, and a server accepts the
 * shorter step (it repeats the move with beta's two trims, which find support for each part already).
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Shadow public World world;
	@Shadow public Box boundingBox;
	@Shadow public boolean onGround;

	@Shadow
	public abstract boolean isSneaking();

	// Right after the sneak trims, where move collects the blocks along the whole step. That box is already taken from
	// the untrimmed step; it covers the trimmed one, and every collision is then worked out with the trimmed values.
	// Ordinals among the double locals (the arguments first): dx 0, dy 1, dz 2, then the x and z at the start, and the
	// step as asked (dx 5, dy 6, dz 7), which beta updates with each trim so a trim doesn't count as hitting something.
	@Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;stretch(DDD)Lnet/minecraft/util/math/Box;", ordinal = 0))
	private void oldfaithful$trimDiagonal(double dx0, double dy0, double dz0, CallbackInfo ci,
			@Local(ordinal = 0, argsOnly = true) LocalDoubleRef dx, @Local(ordinal = 2, argsOnly = true) LocalDoubleRef dz,
			@Local(ordinal = 5) LocalDoubleRef askedX, @Local(ordinal = 7) LocalDoubleRef askedZ) {
		// Beta's own condition for the trims; nothing it does before this point changes either.
		if (!this.onGround || !this.isSneaking()) return;
		double step = 0.05;
		double x = dx.get(), z = dz.get();
		while (x != 0.0 && z != 0.0 && this.world.getEntityCollisions((Entity) (Object) this, this.boundingBox.offset(x, -1.0, z)).size() == 0) {
			x = x < step && x >= -step ? 0.0 : x > 0.0 ? x - step : x + step;
			z = z < step && z >= -step ? 0.0 : z > 0.0 ? z - step : z + step;
		}
		if (x == dx.get() && z == dz.get()) return;
		dx.set(x);
		dz.set(z);
		askedX.set(x);
		askedZ.set(z);
	}
}
