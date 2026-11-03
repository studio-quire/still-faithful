package org.studioquire.oldfaithful.polish.mixin.animals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.KeptAnimals;

/**
 * #4 ({@link KeptAnimals}): a right-click on an animal that did something keeps it: the animal's own answer (ridden),
 * a change to what the player holds (milking swaps the bucket, shearing wears the shears), or an item it took (a saddle
 * or a dye is used up when it takes).
 */
@Mixin(PlayerEntity.class)
abstract class PlayerEntityMixin {
	@WrapOperation(method = "interact(Lnet/minecraft/entity/Entity;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;interact(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
	private boolean oldfaithful$tended(Entity entity, PlayerEntity player, Operation<Boolean> original) {
		// Shearing answers false in beta, but wears the shears; milking swaps the bucket.
		ItemStack hand = player.getHand();
		int count = hand == null ? 0 : hand.count, damage = hand == null ? 0 : hand.getDamage();
		boolean done = original.call(entity, player);
		ItemStack now = player.getHand();
		if (done || now != hand || now != null && (now.count != count || now.getDamage() != damage)) KeptAnimals.keep(entity);
		return done;
	}

	@WrapOperation(method = "interact(Lnet/minecraft/entity/Entity;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;useOnEntity(Lnet/minecraft/entity/LivingEntity;)V"))
	private void oldfaithful$itemTaken(ItemStack stack, LivingEntity entity, Operation<Void> original) {
		int before = stack.count;
		original.call(stack, entity);
		if (stack.count < before) KeptAnimals.keep(entity);
	}
}
