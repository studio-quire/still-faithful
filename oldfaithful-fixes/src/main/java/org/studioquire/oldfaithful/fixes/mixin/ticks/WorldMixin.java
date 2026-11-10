package org.studioquire.oldfaithful.fixes.mixin.ticks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.BlockEvent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.fixes.PendingTicks;

/**
 * #42, the queue: vanilla dropped a tick near the edge of the loaded world twice over: not queued at all when blocks
 * within 8 of it weren't loaded, and thrown away when it came due while they weren't. Now a tick is queued whenever its
 * own chunk is loaded (it has somewhere to be saved), and one that comes due without its surroundings is parked until
 * they load (PendingTicks). It still runs only with its surroundings loaded, as vanilla's did, so ticks never load
 * chunks. A chunk's ticks leave the world when the chunk unloads (ChunkMixin), so every tick held here belongs to a
 * chunk in memory.
 * Running or queueing a tick marks its chunk changed, so the next save writes the queue as it is. Remote (multiplayer
 * client) worlds keep vanilla's queue.
 */
@Mixin(World.class)
abstract class WorldMixin implements PendingTicks.Queue {
	@Shadow
	private TreeSet<BlockEvent> scheduledUpdates;
	@Shadow
	private Set<BlockEvent> scheduledUpdateSet;
	@Shadow
	public boolean isRemote;

	@Shadow
	public abstract boolean isPosLoaded(int x, int y, int z);

	@Shadow
	public abstract Chunk getChunkFromPos(int x, int z);

	@Shadow
	public abstract long getTime();

	@Unique
	private final Map<Long, List<BlockEvent>> oldfaithful$parked = new HashMap<>();

	@Override
	public Set<BlockEvent> oldfaithful$pending() {
		return this.scheduledUpdateSet;
	}

	@Override
	public void oldfaithful$setPending(Set<BlockEvent> pending) {
		this.scheduledUpdateSet = pending;
	}

	@Override
	public TreeSet<BlockEvent> oldfaithful$ordered() {
		return this.scheduledUpdates;
	}

	@Override
	public Map<Long, List<BlockEvent>> oldfaithful$parked() {
		return this.oldfaithful$parked;
	}

	@Inject(method = "processScheduledTicks", at = @At("HEAD"))
	private void oldfaithful$unpark(boolean flush, CallbackInfoReturnable<Boolean> cir) {
		if (!this.isRemote && this.getTime() % PendingTicks.SWEEP_TICKS == 0) PendingTicks.sweep((World) (Object) this);
	}

	// The second region check is the queueing one; the first belongs to the instant updates world generation uses.
	@WrapOperation(method = "scheduleBlockUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isRegionLoaded(IIIIII)Z", ordinal = 1))
	private boolean oldfaithful$queueNearEdge(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Operation<Boolean> original,
			@Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y, @Local(argsOnly = true, ordinal = 2) int z) {
		boolean loaded = original.call(world, minX, minY, minZ, maxX, maxY, maxZ);
		if (this.isRemote) return loaded;
		if (!loaded && !this.isPosLoaded(x, y, z)) return false;
		this.getChunkFromPos(x, z).markDirty();
		return true;
	}

	@WrapOperation(method = "processScheduledTicks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isRegionLoaded(IIIIII)Z"))
	private boolean oldfaithful$waitForSurroundings(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Operation<Boolean> original,
			@Local BlockEvent tick) {
		boolean loaded = original.call(world, minX, minY, minZ, maxX, maxY, maxZ);
		if (this.isRemote) return loaded;
		if (loaded) {
			this.getChunkFromPos(tick.x, tick.z).markDirty();
		} else {
			PendingTicks.park(world, tick);
		}
		return loaded;
	}
}
