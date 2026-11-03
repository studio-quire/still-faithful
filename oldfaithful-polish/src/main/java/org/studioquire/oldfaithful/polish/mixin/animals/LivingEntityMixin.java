package org.studioquire.oldfaithful.polish.mixin.animals;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.KeptAnimals;

/**
 * #4 ({@link KeptAnimals}): a kept animal can't despawn; one riding or ridden is kept. All of it runs where the world does (despawning and riding are the server's in multiplayer).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin extends Entity {
	LivingEntityMixin(World world) {
		super(world);
	}

	@Inject(method = "canDespawn", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$keptStay(CallbackInfoReturnable<Boolean> cir) {
		if (KeptAnimals.kept(this)) cir.setReturnValue(false);
	}

	@Inject(method = "tryDespawn", at = @At("HEAD"))
	private void oldfaithful$riding(CallbackInfo ci) {
		if (this.vehicle != null || this.passenger != null) KeptAnimals.keep(this);
	}
}
