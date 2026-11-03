package org.studioquire.oldfaithful.saves.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AlphaChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.saves.ChunkLoad;

/**
 * Inside a careful chunk load, an entity or block entity whose data throws is left out (and reported) rather than
 * losing the whole chunk. Outside one, failures propagate as in vanilla.
 */
@Mixin(AlphaChunkStorage.class)
abstract class AlphaChunkStorageMixin {
	@WrapOperation(method = "loadChunkFromNbt", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/EntityRegistry;getEntityFromNbt(Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"))
	private static Entity oldfaithful$entity(NbtCompound nbt, World world, Operation<Entity> original) {
		try {
			return original.call(nbt, world);
		} catch (RuntimeException | StackOverflowError e) {
			if (ChunkLoad.recordDropped("an entity (" + safeId(nbt) + ")", e)) return null;
			throw e;
		}
	}

	@WrapOperation(method = "loadChunkFromNbt", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/entity/BlockEntity;createFromNbt(Lnet/minecraft/nbt/NbtCompound;)Lnet/minecraft/block/entity/BlockEntity;"))
	private static BlockEntity oldfaithful$blockEntity(NbtCompound nbt, Operation<BlockEntity> original) {
		try {
			return original.call(nbt);
		} catch (RuntimeException | StackOverflowError e) {
			if (ChunkLoad.recordDropped("a block entity (" + safeId(nbt) + ")", e)) return null;
			throw e;
		}
	}

	@WrapOperation(method = "loadChunkFromNbt", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/chunk/Chunk;addBlockEntity(Lnet/minecraft/block/entity/BlockEntity;)V"))
	private static void oldfaithful$addBlockEntity(Chunk chunk, BlockEntity blockEntity, Operation<Void> original) {
		try {
			original.call(chunk, blockEntity);
		} catch (RuntimeException e) {
			if (!ChunkLoad.recordDropped("a block entity at " + blockEntity.x + ", " + blockEntity.y + ", " + blockEntity.z, e)) throw e;
		}
	}

	private static String safeId(NbtCompound nbt) {
		try {
			return nbt.getString("id");
		} catch (RuntimeException e) {
			return "unknown id";
		}
	}
}
