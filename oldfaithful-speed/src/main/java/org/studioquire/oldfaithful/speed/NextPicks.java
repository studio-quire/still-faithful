package org.studioquire.oldfaithful.speed;

import java.util.List;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.world.DirtyChunkSorter;
import net.minecraft.entity.LivingEntity;
import org.studioquire.oldfaithful.speed.mixin.WorldAccessor;
import org.studioquire.oldfaithful.speed.mixin.WorldRendererAccessor;

/**
 * Which chunks WorldRenderer.compileChunks(camera, false) would rebuild next, worked out the way it chooses them,
 * without changing anything: every dirty chunk within 16 blocks of the camera, then the best distant one by
 * DirtyChunkSorter, and the second best if it's in the frustum. Used by FramePacing to leave to vanilla's own call any
 * rebuild that would make the game load or generate a chunk (a rebuild's WorldRegion asks the world for the 3x3
 * chunk columns around it, and a missing one is read from disk or generated), since such a call can take tens of
 * milliseconds, more than any frame has spare.
 */
public final class NextPicks {
	private static final ChunkBuilder[] best = new ChunkBuilder[2];

	private NextPicks() {
	}

	/** Whether the next compileChunks call would rebuild a chunk whose surroundings aren't all loaded. */
	public static boolean needLoading(WorldRenderer renderer, LivingEntity camera) {
		List<?> dirty = ((WorldRendererAccessor) renderer).oldfaithful$dirtyChunks();
		DirtyChunkSorter sorter = new DirtyChunkSorter(camera);
		best[0] = best[1] = null;
		for (int i = 0, n = dirty.size(); i < n; i++) {
			ChunkBuilder c = (ChunkBuilder) dirty.get(i);
			if (c == null) continue;
			if (c.squaredDistanceTo(camera) > 256.0F) {
				// compileChunks' insertion into its two-slot list, as written there.
				int j = 0;
				while (j < 2 && (best[j] == null || sorter.compare(best[j], c) <= 0)) j++;
				if (--j <= 0) continue;
				int k = j;
				while (--k != 0) best[k - 1] = best[k];
				best[j] = c;
				continue;
			}
			if (needsLoading(c)) return true;
		}
		// Rebuilt from the last slot down, stopping at a slot out of the frustum unless it's the last.
		for (int i = 1; i >= 0; i--) {
			ChunkBuilder c = best[i];
			if (c == null) continue;
			if (!c.inFrustum && i != 1) break;
			if (needsLoading(c)) return true;
		}
		return false;
	}

	private static boolean needsLoading(ChunkBuilder c) {
		if (!c.dirty || c.world == null) return false;
		WorldAccessor world = (WorldAccessor) c.world;
		int minX = (c.x - 1) >> 4, maxX = (c.x + c.sizeX + 1) >> 4;
		int minZ = (c.z - 1) >> 4, maxZ = (c.z + c.sizeZ + 1) >> 4;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (!world.oldfaithful$hasChunk(x, z)) return true;
			}
		}
		return false;
	}
}
