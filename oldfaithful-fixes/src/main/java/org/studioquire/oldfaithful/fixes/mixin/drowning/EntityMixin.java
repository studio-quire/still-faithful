package org.studioquire.oldfaithful.fixes.mixin.drowning;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #61 (server): whether a player's head is under water (drowning, and mining five times slower) is measured from the
 * eyes. The player's own client, which shows the air bubbles, and single player measure 1.74 above the feet (beta's
 * player keeps its eyes in y and adds 0.12); the server measured 1.62. With the surface between the two (water three
 * levels down from full, on a block one down) the server drowned a player whose screen showed full air, and timed
 * their mining slower than the client did. The server now measures where the client does. Vanilla clients are
 * unaffected: they already measure there.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@ModifyExpressionValue(method = "isInFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getEyeHeight()F"))
	private float oldfaithful$asTheClientMeasures(float eyes) {
		return (Object) this instanceof ServerPlayerEntity ? eyes + 0.12F : eyes;
	}
}
