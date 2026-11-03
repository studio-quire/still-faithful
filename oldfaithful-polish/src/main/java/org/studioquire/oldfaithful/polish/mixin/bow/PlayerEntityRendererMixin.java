package org.studioquire.oldfaithful.polish.mixin.bow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.BowPose;

/** #34: a player's bow held like a bow, in third person ({@link BowPose}). */
@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixin {
	@WrapOperation(method = "renderMore(Lnet/minecraft/entity/player/PlayerEntity;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/Item;isHandheld()Z"))
	private boolean oldfaithful$bowPose(Item item, Operation<Boolean> original, PlayerEntity player, float tickDelta) {
		// With a fishing line out beta draws a stick, so only a bow actually drawn takes the pose.
		if (player.fishHook == null && BowPose.isBow(player.inventory.getSelectedItem())) {
			BowPose.apply();
			return true;
		}
		return original.call(item);
	}
}
