package org.studioquire.oldfaithful.polish.mixin.animals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.KeptAnimals;

/** #4 ({@link KeptAnimals}): chicks hatched from an egg a player threw are kept. */
@Mixin(EggEntity.class)
abstract class EggEntityMixin {
	@Shadow
	private LivingEntity owner;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
	private boolean oldfaithful$hatchedByPlayer(World world, Entity chick, Operation<Boolean> original) {
		if (this.owner instanceof PlayerEntity) KeptAnimals.keep(chick);
		return original.call(world, chick);
	}
}
