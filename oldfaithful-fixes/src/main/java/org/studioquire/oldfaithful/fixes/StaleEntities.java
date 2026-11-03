package org.studioquire.oldfaithful.fixes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityDestroyS2CPacket;

/**
 * #49 on a server: when a dead player's body is removed (after the death animation), vanilla takes them off every
 * entity's viewer list without telling their client, which keeps its world through a respawn in the same dimension.
 * Whatever then died, was picked up or went away near the death spot stayed frozen on that client until it
 * reconnected. The entities they were seeing are noted at that removal, and on respawn their client is told to drop
 * them before anything else; whatever is still in range is sent again by the respawn itself, as vanilla does.
 * Nothing changes while the death screen is up: the world behind it looks as it did.
 * <p>
 * Kept per player object (the respawn makes a new one), weakly, so a player who leaves while dead leaves nothing.
 * The server's own thread only.
 */
public final class StaleEntities {
	private static final Map<ServerPlayerEntity, List<Integer>> NOTED = new WeakHashMap<>();

	private StaleEntities() {
	}

	public static void note(ServerPlayerEntity player, int entityId) {
		NOTED.computeIfAbsent(player, p -> new ArrayList<>()).add(entityId);
	}

	public static void clear(ServerPlayerEntity player) {
		List<Integer> ids = NOTED.remove(player);
		if (ids == null) return;
		for (int id : ids) player.networkHandler.sendPacket(new EntityDestroyS2CPacket(id));
	}
}
