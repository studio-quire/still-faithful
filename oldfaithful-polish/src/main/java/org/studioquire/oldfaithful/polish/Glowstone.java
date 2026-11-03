package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Glowstone (#10's note): stone material since Beta 1.6.6, so it needs a pickaxe to drop and mines at the speed of a
 * block you can't harvest; 1.0 made it glass again ("a pickaxe is no longer needed"). Here it counts as harvestable by
 * anything, which gives both 1.0's drop and 1.0's mining speed (the game's "can harvest" decides each). Glowstone stays
 * stone material, so what can be placed on it is unchanged.
 * <p>
 * In multiplayer the client times the break and the server checks it: a vanilla server refuses a break reported much
 * sooner than its own timing (the block vanishes on the client and comes back 4 s later), so a client mines faster
 * only on a server whose login packet says it has this change ({@link ServerMarker}).
 */
public final class Glowstone {
	/** In multiplayer, whether the server has this change. */
	public static volatile boolean serverAgrees;

	private Glowstone() {
	}

	public static boolean harvestable(PlayerEntity player, Block block) {
		if (block == null || block.id != Block.GLOWSTONE.id) return false;
		return player == null || player.world == null || !player.world.isRemote || serverAgrees;
	}
}
