package org.studioquire.oldfaithful.polish.mixin.animals;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.KeptAnimals;

/** #4: whether an animal is kept ({@link KeptAnimals}), saved with it. */
@Mixin(AnimalEntity.class)
abstract class AnimalEntityMixin implements KeptAnimals.Keepable {
	@Unique
	private boolean oldfaithful$kept;

	@Override
	public boolean oldfaithful$kept() {
		return oldfaithful$kept;
	}

	@Override
	public void oldfaithful$keep() {
		oldfaithful$kept = true;
	}

	@Inject(method = "writeNbt", at = @At("TAIL"))
	private void oldfaithful$writeKept(NbtCompound nbt, CallbackInfo ci) {
		if (oldfaithful$kept) nbt.putBoolean(KeptAnimals.TAG, true);
	}

	@Inject(method = "readNbt", at = @At("TAIL"))
	private void oldfaithful$readKept(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.getBoolean(KeptAnimals.TAG)) oldfaithful$kept = true;
	}
}
