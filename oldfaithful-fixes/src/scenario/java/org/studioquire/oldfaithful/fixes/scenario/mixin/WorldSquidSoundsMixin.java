package org.studioquire.oldfaithful.fixes.scenario.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.scenario.SquidWatch;

@Mixin(World.class)
abstract class WorldSquidSoundsMixin {
	@Inject(method = "playSound(Lnet/minecraft/entity/Entity;Ljava/lang/String;FF)V", at = @At("HEAD"))
	private void oldfaithful$heard(Entity source, String sound, float volume, float pitch, CallbackInfo ci) {
		SquidWatch.heard(source, sound);
	}
}
