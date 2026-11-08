package org.studioquire.oldfaithful.saves.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.util.IntHashMap;
import net.minecraft.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * docs/bugs.md #78: a new world's spawn search runs inside World's constructor, before this class's fields exist, and
 * can decorate chunks. A spring's water can then break a plant, and the drop finds the entity map still null: the
 * server stops at "Preparing level", about once in 900 new worlds. Nothing could be tracking such an entity (the
 * server adds its tracker's listener after the constructor), so it's marked dead and goes at the world's first tick,
 * unsaved. In any other case nothing changes. Server only.
 *
 * Faithful Core carries the same wrap, for the library on a server without this mod. With both, the wraps chain:
 * whichever is outer marks the drop dead and doesn't call through, so the other never sees the null map.
 */
@Mixin(ServerWorld.class)
abstract class ServerWorldMixin {
	@WrapOperation(method = "notifyEntityAdded", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IntHashMap;put(ILjava/lang/Object;)V"))
	private void oldfaithful$whileBeingBuilt(IntHashMap entities, int id, Object entity, Operation<Void> put) {
		if (entities == null) ((Entity) entity).markDead();
		else put.call(entities, id, entity);
	}
}
