package org.studioquire.oldfaithful.polish.mixin.dusk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.studioquire.oldfaithful.polish.DuskLight;

/**
 * #6: while entities, block entities, particles, the hand or the weather are drawn, the luminance they read is the
 * game's own at the two whole darknesses around this frame's, mixed by its fraction: beta's value at a whole darkness,
 * following the terrain in between. The world's darkness is set for each reading and put straight back; the game's
 * logic doesn't run in between (these are draws), and outside them nothing changes.
 */
@Mixin(World.class)
abstract class WorldMixin {
	@Shadow
	public int ambientDarkness;

	@WrapMethod(method = "method_1782")
	private float oldfaithful$smooth(int x, int y, int z, Operation<Float> original) {
		if (DuskLight.entityScope <= 0 || !DuskLight.active()) return original.call(x, y, z);
		float d = DuskLight.darkness;
		int whole = (int) d;
		int saved = ambientDarkness;
		try {
			ambientDarkness = whole;
			float a = original.call(x, y, z);
			if (d == whole) return a;
			ambientDarkness = whole + 1;
			return a + (original.call(x, y, z) - a) * (d - whole);
		} finally {
			ambientDarkness = saved;
		}
	}

	@WrapMethod(method = "getNaturalBrightness")
	private float oldfaithful$smooth(int x, int y, int z, int blockLight, Operation<Float> original) {
		if (DuskLight.entityScope <= 0 || !DuskLight.active()) return original.call(x, y, z, blockLight);
		float d = DuskLight.darkness;
		int whole = (int) d;
		int saved = ambientDarkness;
		try {
			ambientDarkness = whole;
			float a = original.call(x, y, z, blockLight);
			if (d == whole) return a;
			ambientDarkness = whole + 1;
			return a + (original.call(x, y, z, blockLight) - a) * (d - whole);
		} finally {
			ambientDarkness = saved;
		}
	}
}
