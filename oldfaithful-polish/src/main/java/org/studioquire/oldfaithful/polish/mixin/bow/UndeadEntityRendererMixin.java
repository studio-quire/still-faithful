package org.studioquire.oldfaithful.polish.mixin.bow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.UndeadEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.BowPose;

/** #34: a skeleton's bow held like a bow ({@link BowPose}). */
@Mixin(UndeadEntityRenderer.class)
abstract class UndeadEntityRendererMixin {
	@WrapOperation(method = "renderMore(Lnet/minecraft/entity/LivingEntity;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/Item;isHandheld()Z"))
	private boolean oldfaithful$bowPose(Item item, Operation<Boolean> original, LivingEntity entity, float tickDelta) {
		if (BowPose.isBow(entity.getHeldItem())) {
			BowPose.apply();
			return true;
		}
		return original.call(item);
	}
}
