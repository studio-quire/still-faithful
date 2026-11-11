package org.studioquire.oldfaithful.fixes.mixin.culling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.fixes.EntityCulling;

/** #84: the box an entity is culled by is where it's drawn this frame, grown by how far its model reaches past it. */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@WrapOperation(method = "renderEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;boundingBox:Lnet/minecraft/util/math/Box;", opcode = Opcodes.GETFIELD))
	private Box oldfaithful$drawnBox(Entity entity, Operation<Box> original, @Local(argsOnly = true) float tickDelta) {
		return EntityCulling.drawn(original.call(entity), entity, tickDelta);
	}
}
