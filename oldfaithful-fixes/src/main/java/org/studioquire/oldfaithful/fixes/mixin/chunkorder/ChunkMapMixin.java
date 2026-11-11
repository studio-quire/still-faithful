package org.studioquire.oldfaithful.fixes.mixin.chunkorder;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.ChunkMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #16b: when a player's view moves (a /tp, or any move past 8 blocks into a new chunk), the chunks newly in view are
 * queued row by row from the west edge, and the server sends them one at a time in that order; at view distance 10 the
 * chunk under the player is about the 220th, so a teleported player stands over air for seconds. Logins, respawns and
 * portals already queue nearest first (a spiral); this does the same here, ordering what's waiting by distance from
 * the player's chunk. Only the order changes: the same chunks are sent at the same pace.
 * <p>
 * The player's chunk is the one underfoot, from the position rounded down. Beta's own view square here goes by the
 * position cut toward zero ({@code (int) x >> 4}), a chunk east (or south) of the one underfoot when the player stands
 * in the eastmost block of a chunk west of 0 (the southmost north of 0); the one underfoot is still in view, and this
 * sent it second there until it took the rounded-down chunk too (found 2026-11-10: fixesMultiplayerTest's #16b check failing on such a spot).
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
	@Inject(method = "updatePlayerChunks", at = @At(value = "FIELD",
			target = "Lnet/minecraft/entity/player/ServerPlayerEntity;lastX:D", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void oldfaithful$nearestFirst(ServerPlayerEntity player, CallbackInfo ci) {
		int cx = MathHelper.floor(player.x) >> 4;
		int cz = MathHelper.floor(player.z) >> 4;
		@SuppressWarnings("unchecked")
		List<ChunkPos> pending = player.pendingChunkUpdates;
		// Stable: chunks at the same distance keep vanilla's order.
		Collections.sort(pending, Comparator.comparingInt(p -> p == null ? Integer.MAX_VALUE : (p.x - cx) * (p.x - cx) + (p.z - cz) * (p.z - cz)));
	}
}
