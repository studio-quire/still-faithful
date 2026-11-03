package org.studioquire.oldfaithful.lighting.mixin;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.lighting.LightingWorld;
import org.studioquire.oldfaithful.lighting.Stats;
import org.studioquire.oldfaithful.lighting.Verify;
import org.studioquire.oldfaithful.lighting.Relight;

@Mixin(World.class)
abstract class WorldMixin implements LightingWorld {
	@Shadow
	private List lightingQueue;

	@Shadow
	protected ChunkSource chunkSource;

	@Shadow
	public abstract boolean doLightingUpdates();

	@Unique
	private final Relight.Entered oldfaithful$entered = new Relight.Entered();

	@Override
	public Relight.Entered oldfaithful$entered() {
		return oldfaithful$entered;
	}

	/** Chunks that entered since the last pass are checked first, so what they need joins this pass's queue. */
	@Inject(method = "doLightingUpdates", at = @At("HEAD"))
	private void oldfaithful$checkEntered(CallbackInfoReturnable<Boolean> cir) {
		Relight.checkEntered((World) (Object) this);
		if (Stats.ENABLED) Stats.pass((World) (Object) this, lightingQueue.size());
		if (Verify.EVERY_S > 0) Verify.periodic((World) (Object) this, chunkSource, lightingQueue.size());
	}

	/**
	 * A full save (leaving the world, save-all, stopping a server) first finishes the light still queued, so the world
	 * on disk is lit as it should be. Without it the light was saved half done; the mod's check would finish it at
	 * the next load, but other programs reading the files would see it unfinished. Autosaves don't wait.
	 */
	@Inject(method = "saveWithLoadingDisplay", at = @At("HEAD"))
	private void oldfaithful$finishLight(boolean saveEntities, LoadingDisplay display, CallbackInfo ci) {
		if (!saveEntities || ((World) (Object) this).isRemote) return;
		long start = System.nanoTime();
		int queued = lightingQueue.size();
		while (doLightingUpdates()) {
			// Each call works through up to 500 updates.
		}
		if (Verify.ENABLED) Verify.all((World) (Object) this, chunkSource);
		if (Stats.ENABLED) Stats.log("finished " + queued + " queued updates before a full save in " + (System.nanoTime() - start) / 1000000 + " ms");
	}

	/** Requests made while a generator decorates are also checked at the next pass (Relight.decorating). */
	@Inject(method = "queueLightUpdate(Lnet/minecraft/world/LightType;IIIIIIZ)V", at = @At("HEAD"))
	private void oldfaithful$noteDecoration(LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean merge,
			CallbackInfo ci) {
		Relight.requested((World) (Object) this, type, minX, minY, minZ, maxX, maxY, maxZ);
	}

	/**
	 * Vanilla throws the whole queue away once it passes a million entries ("aborting lighting updates"), losing the
	 * light of everything waiting in it. The queue is kept, whatever its size; it's worked through as before.
	 */
	@ModifyConstant(method = "queueLightUpdate(Lnet/minecraft/world/LightType;IIIIIIZ)V", constant = @Constant(intValue = 1000000))
	private int oldfaithful$neverAbort(int limit) {
		return Integer.MAX_VALUE;
	}

	/**
	 * A cell whose light changed asks each neighbour to update unless the neighbour already holds the changed light
	 * less one. That's safe for a neighbour that costs 1 to pass through, but water and ice cost 3 (and an opaque
	 * block lit by mistake costs more), so a neighbour lit through one of those can be left too bright when the light
	 * behind it falls: placing a block over water leaves the water below it lit. Such a neighbour always updates.
	 */
	@Inject(method = "updateLight", at = @At("HEAD"))
	private void oldfaithful$alwaysThroughDenseBlocks(LightType type, int x, int y, int z, int light, CallbackInfo ci) {
		World world = (World) (Object) this;
		if (type == LightType.SKY && world.dimension.hasCeiling) return;
		if (!world.isPosLoaded(x, y, z)) return;
		if (Block.BLOCKS_LIGHT_OPACITY[world.getBlockId(x, y, z)] < 2) return;
		if (world.getBrightness(type, x, y, z) != 0) world.queueLightUpdate(type, x, y, z, x, y, z);
	}
}
