package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SkeletonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SkeletonEntity.class)
public interface SkeletonEntityInvoker {
	@Invoker("attack")
	void oldfaithful$attack(Entity other, float distance);
}
