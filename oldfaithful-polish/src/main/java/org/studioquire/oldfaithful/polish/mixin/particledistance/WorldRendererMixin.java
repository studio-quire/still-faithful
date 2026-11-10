package org.studioquire.oldfaithful.polish.mixin.particledistance;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Beta makes a particle only within 16 blocks of the camera, whatever made it, so a blast further off makes no cloud and
 * a mob dying there no puff. Here every particle is made within 32, later versions' distance (26.3's, read), and the puff
 * ("explode": a blast's, a mob's dying or appearing) within 64, where later versions make it at any distance: 64 is as
 * far as a blast is heard (beta's sound reach for its volume of 4), past the bow's reach (about 52 blocks on level
 * ground), and as far as a server sends blasts, so single player and multiplayer agree. A blast's dark smoke is an
 * ordinary particle: past 32 its white puffs are the cloud, as in later versions, and at that distance smoke is a pixel
 * or two. Display ticks (torches, fire, lava, portals) only run within 16 blocks anyway, so they add nothing; the cap
 * of 4000 particles a layer is beta's. Nothing in the world goes by particles: they draw on their own random.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	private static final double PARTICLES = 32.0, PUFFS = 64.0;

	@ModifyExpressionValue(method = "addParticle", at = @At(value = "CONSTANT", args = "doubleValue=16.0"))
	private double oldfaithful$farther(double beta, @Local(argsOnly = true) String particle) {
		return "explode".equals(particle) ? PUFFS : PARTICLES;
	}
}
