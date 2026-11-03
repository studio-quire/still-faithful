package org.studioquire.oldfaithful.fixes.mixin.fishing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #34, the fish that flies behind you: reeling in throws the catch (or a hooked mob) towards the player's position. A
 * multiplayer server keeps a player's position at the feet; single player keeps it at the eyes, 1.62 higher, so in
 * single player the catch was thrown high and sailed over your head from a dozen blocks out. It's aimed at the feet
 * now, as a server always aimed it; in multiplayer nothing changes.
 */
@Mixin(FishingBobberEntity.class)
abstract class FishingBobberEntityMixin {
	@Shadow
	public PlayerEntity owner;

	@ModifyExpressionValue(method = "use", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;y:D"))
	private double oldfaithful$feet(double y) {
		return y - this.owner.standingEyeHeight;
	}
}
