package org.studioquire.oldfaithful.selftest;

import net.minecraft.block.Block;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.play.UpdateSignPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ServerWorld;

/**
 * Server half of `:harness:fixesMultiplayerTest` (only with {@code -Doldfaithful.selftest.multiplayer=true}): the
 * scene the hidden client ("OldFaithfulTest") joins into, and test commands players send as chat starting with
 * "!oftest ", so a raw client or the hidden client can ask for what only the server can do (die, a sign edited in
 * the same tick as another block change, another player turned, an entity removed). Never shipped.
 */
public final class MultiplayerScenes {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.selftest.multiplayer");
	public static final String CLIENT = "OldFaithfulTest";
	/** The scenes' floor: high above most terrain, but inside the world (blocks end at y 127; "Sky" players fly at 130). */
	public static final int FLOOR = 120;

	private MultiplayerScenes() {
	}

	/**
	 * The hidden client's scene, built around where it joins (high up, standing on it): a stone floor, three
	 * dirt blocks in a row 2 blocks west (to mine), and 3 blocks east a one-block fence pen with two pigs in it, which
	 * push each other all the time.
	 */
	public static void buildClientScene(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.world;
		int px = MathHelper.floor(player.x), pz = MathHelper.floor(player.z);
		for (int x = (px - 4) >> 4; x <= (px + 5) >> 4; x++) {
			for (int z = (pz - 3) >> 4; z <= (pz + 3) >> 4; z++) world.chunkCache.loadChunk(x, z);
		}
		for (int x = px - 4; x <= px + 5; x++) {
			for (int z = pz - 3; z <= pz + 3; z++) world.setBlock(x, FLOOR, z, Block.STONE.id);
		}
		for (int dz = -1; dz <= 1; dz++) world.setBlock(px - 2, FLOOR + 1, pz + dz, Block.DIRT.id);
		int penX = px + 3;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) world.setBlock(penX + dx, FLOOR + 1, pz + dz, Block.FENCE.id);
			}
		}
		// #62: a cow spawner 20 blocks west, out of a player's 16-block reach (so it spawns nothing), in view.
		world.chunkCache.loadChunk((px - 20) >> 4, pz >> 4);
		world.setBlock(px - 20, FLOOR, pz, Block.SPAWNER.id);
		((MobSpawnerBlockEntity) world.getBlockEntity(px - 20, FLOOR, pz)).setSpawnedEntityId("Cow");
		for (int i = 0; i < 2; i++) {
			PigEntity pig = new PigEntity(world);
			pig.setPositionAndAngles(penX + 0.5 + i * 0.05, FLOOR + 1, pz + 0.5, 0, 0);
			world.spawnEntity(pig);
		}
		System.out.println("[oldfaithful-selftest] scene built for " + player.name + " at " + px + " " + (FLOOR + 1) + " " + pz);
	}

	/** Returns true if the chat message was a test command (and so isn't broadcast). */
	public static boolean command(MinecraftServer server, ServerPlayerEntity player, String message) {
		if (!message.startsWith("!oftest ")) return false;
		String[] a = message.substring("!oftest ".length()).trim().split(" ");
		ServerWorld world = (ServerWorld) player.world;
		int cx = MathHelper.floor(player.x) & ~15, cz = MathHelper.floor(player.z) & ~15;
		switch (a[0]) {
			case "die":
				player.damage(null, 1000);
				System.out.println("[oldfaithful-selftest] killed " + player.name + " (health " + player.health + ")");
				break;
			case "signplace":
				// A sign standing on stone in the sender's chunk; a dirt block's place, later, too.
				world.setBlock(cx + 4, FLOOR, cz + 4, Block.STONE.id);
				world.setBlock(cx + 10, FLOOR, cz + 10, Block.STONE.id);
				world.setBlock(cx + 4, FLOOR + 1, cz + 4, Block.SIGN.id, 0);
				System.out.println("[oldfaithful-selftest] sign placed at " + (cx + 4) + " " + (FLOOR + 1) + " " + (cz + 4));
				break;
			case "signedit":
				// In one tick, as a player's edit coinciding with any other change in the chunk: the dirt, then the edit
				// through the server's own handler (which is how an edit reaches other players).
				world.setBlock(cx + 10, FLOOR + 1, cz + 10, Block.DIRT.id);
				player.networkHandler.handleUpdateSign(new UpdateSignPacket(cx + 4, FLOOR + 1, cz + 4, new String[] {"Old", "Faithful", "sign", "test"}));
				System.out.println("[oldfaithful-selftest] sign edited with a block change beside it");
				break;
			case "turn": {
				ServerPlayerEntity other = server.playerManager.getPlayer(a[1]);
				other.yaw = Float.parseFloat(a[2]);
				other.pitch = 0;
				System.out.println("[oldfaithful-selftest] " + other.name + " turned to " + other.yaw);
				break;
			}
			case "hurt":
				// #58: a fresh hit, so the next half second's damage meets beta's protection.
				player.damage(null, 1);
				System.out.println("[oldfaithful-selftest] hurt " + player.name + " (health " + player.health + ")");
				break;
			case "boom":
				// #58: an explosion a block and a half east of the player.
				world.createExplosion(null, player.x + 1.5, player.y, player.z, 2.0F);
				System.out.println("[oldfaithful-selftest] explosion beside " + player.name);
				break;
			case "pool": {
				// #61: a pool to stand in, south of the spawn's scenes, under the scenes' floor: water at the feet and, in the
				// block above, water three levels down from full, whose surface lies between the eye heights the server
				// (1.62 above the feet) and clients (1.74) measured from. Placed without updates, so it doesn't flow.
				int x = MathHelper.floor(player.x), z = MathHelper.floor(player.z) - 8, feet = FLOOR - 5;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int y = feet - 1; y <= feet + 3; y++) {
							boolean inside = dx == 0 && dz == 0 && y >= feet;
							int id = !inside ? (y <= feet + 1 ? Block.STONE.id : 0) : y == feet ? Block.WATER.id : y == feet + 1 ? Block.WATER.id : 0;
							world.setBlockWithoutNotifyingNeighbors(x + dx, y, z + dz, id, inside && y == feet + 1 ? 3 : 0);
						}
					}
				}
				player.networkHandler.teleport(x + 0.5, feet, z + 0.5, player.yaw, player.pitch);
				System.out.println("[oldfaithful-selftest] " + player.name + " in a pool at " + x + " " + feet + " " + z);
				break;
			}
			case "unpig": {
				int id = Integer.parseInt(a[1]);
				for (Object o : world.entities) {
					Entity e = (Entity) o;
					if (e.id == id) {
						e.markDead();
						System.out.println("[oldfaithful-selftest] removed entity " + id);
					}
				}
				break;
			}
			default:
				System.out.println("[oldfaithful-selftest] unknown test command: " + message);
		}
		return true;
	}
}
