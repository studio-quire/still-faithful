package org.studioquire.oldfaithful.selftest;

import net.minecraft.block.Block;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.play.ChatMessagePacket;
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
	/** #77: the top of the floor of the squid's tank, under the scene, 3 blocks of water. */
	public static final int TANK_FLOOR = 101;

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
		// #76, #77: a tank of squid under the scene and east of it, water 3 deep, the squid starting on its floor.
		for (int x = (px + 7) >> 4; x <= (px + 17) >> 4; x++) {
			for (int z = (pz - 5) >> 4; z <= (pz + 5) >> 4; z++) world.chunkCache.loadChunk(x, z);
		}
		for (int x = px + 7; x <= px + 17; x++) {
			for (int z = pz - 5; z <= pz + 5; z++) {
				boolean wall = x == px + 7 || x == px + 17 || Math.abs(z - pz) == 5;
				world.setBlock(x, TANK_FLOOR - 1, z, Block.STONE.id);
				for (int y = TANK_FLOOR; y < TANK_FLOOR + 6; y++) {
					if (wall) world.setBlock(x, y, z, Block.STONE.id);
				}
			}
		}
		for (int x = px + 8; x <= px + 16; x++) {
			for (int z = pz - 4; z <= pz + 4; z++) {
				for (int y = TANK_FLOOR; y < TANK_FLOOR + 6; y++) world.setBlock(x, y, z, y < TANK_FLOOR + 3 ? Block.WATER.id : 0);
			}
		}
		for (int i = 0; i < 16; i++) {
			SquidEntity squid = new SquidEntity(world);
			squid.setPositionAndAngles(px + 9.5 + (i % 4) * 2, TANK_FLOOR, pz - 2.5 + (i / 4) * 2, 0, 0);
			world.spawnEntity(squid);
		}
		System.out.println("[oldfaithful-selftest] scene built for " + player.name + " at " + px + " " + (FLOOR + 1) + " " + pz);
	}

	/**
	 * A pool for a boat (BoatRide): still water at the scenes' floor, {@link BoatRide#POOL_LENGTH} long west of x and
	 * {@link BoatRide#POOL_WIDTH} wide south of z (x z its north-east inside corner), three of air over it and a stone roof
	 * (open water freezes in a cold biome), and a boat in it 3 from the east end, halfway across, facing west (yaw 0, as a
	 * client spawns any boat).
	 */
	private static BoatEntity pool(ServerWorld world, int x, int z) {
		int west = x - BoatRide.POOL_LENGTH, south = z + BoatRide.POOL_WIDTH;
		for (int cx = (west - 1) >> 4; cx <= (x + 1) >> 4; cx++) {
			for (int cz = (z - 1) >> 4; cz <= south >> 4; cz++) world.chunkCache.loadChunk(cx, cz);
		}
		for (int bx = west; bx <= x + 1; bx++) {
			for (int bz = z - 1; bz <= south; bz++) {
				boolean wall = bx == west || bx == x + 1 || bz == z - 1 || bz == south;
				world.setBlockWithoutNotifyingNeighbors(bx, FLOOR - 1, bz, Block.STONE.id, 0);
				world.setBlockWithoutNotifyingNeighbors(bx, FLOOR, bz, wall ? Block.STONE.id : Block.WATER.id, 0);
				for (int y = FLOOR + 1; y <= FLOOR + 3; y++) world.setBlockWithoutNotifyingNeighbors(bx, y, bz, wall ? Block.STONE.id : 0, 0);
				world.setBlockWithoutNotifyingNeighbors(bx, FLOOR + 4, bz, Block.STONE.id, 0);
			}
		}
		BoatEntity boat = new BoatEntity(world, x - 2.5, FLOOR + 0.7, z + BoatRide.POOL_WIDTH / 2.0);
		world.spawnEntity(boat);
		return boat;
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
			case "chest": {
				// #79: an empty inventory, a chest at x y z holding 8 cobblestone, and air 4 blocks south of it to place in.
				int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]);
				for (int i = 0; i < player.inventory.main.length; i++) player.inventory.main[i] = null;
				world.setBlock(x, y + 1, z, 0);
				world.setBlock(x, y, z + 4, 0);
				world.setBlock(x, y, z, Block.CHEST.id);
				((ChestBlockEntity) world.getBlockEntity(x, y, z)).setStack(0, new ItemStack(Block.COBBLESTONE, 8));
				player.currentScreenHandler.sendContentUpdates();
				System.out.println("[oldfaithful-selftest] chest of cobblestone at " + x + " " + y + " " + z);
				break;
			}
			case "give":
				// #79: 3 dirt in slot 9, sent at once on whatever screen is open (as a pickup is).
				player.inventory.main[9] = new ItemStack(Block.DIRT, 3);
				player.currentScreenHandler.sendContentUpdates();
				System.out.println("[oldfaithful-selftest] gave " + player.name + " 3 dirt");
				break;
			case "sync":
				// #79: the whole inventory as the server has it.
				player.onContentsUpdate(player.playerScreenHandler);
				System.out.println("[oldfaithful-selftest] sent " + player.name + " their whole inventory");
				break;
			case "fences": {
				// Still Faithful's #30: FenceScene at x z (FenceWalk asks for it 20 blocks north of where it joined).
				int x = Integer.parseInt(a[1]), z = Integer.parseInt(a[2]);
				for (int ch = (x - 4) >> 4; ch <= (x + FenceScene.CORNER + 6) >> 4; ch++) {
					for (int cz2 = (z - 4) >> 4; cz2 <= (z + 7) >> 4; cz2++) world.chunkCache.loadChunk(ch, cz2);
				}
				FenceScene.build(world, x, FLOOR, z);
				System.out.println("[oldfaithful-selftest] fence scene at " + x + " " + FLOOR + " " + z);
				break;
			}
			case "tp":
				// The player put at x feet z, facing yaw, as a teleport is sent (FenceWalk's starting places).
				player.networkHandler.teleport(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Float.parseFloat(a[4]), 0);
				break;
			case "boat": {
				// #83 and Still Faithful's Boat Steering: a boat in a pool (BoatRide).
				int x = Integer.parseInt(a[1]), z = Integer.parseInt(a[2]);
				BoatEntity boat = pool(world, x, z);
				System.out.println("[oldfaithful-selftest] boat " + boat.id + " in a pool, north-east inside corner " + x + " " + z);
				break;
			}
			case "ride": {
				// Into the nearest boat, by its own right-click (the client asks once it has the boat, or the seat it's
				// sent would name an entity it doesn't know).
				BoatEntity nearest = null;
				for (Object o : world.entities) {
					if (o instanceof BoatEntity && !((BoatEntity) o).dead
							&& (nearest == null || player.getSquaredDistance((Entity) o) < player.getSquaredDistance(nearest))) nearest = (BoatEntity) o;
				}
				if (nearest != null) nearest.interact(player);
				System.out.println("[oldfaithful-selftest] " + player.name + " rides " + (nearest == null ? "nothing" : "boat " + nearest.id));
				break;
			}
			case "look":
				// #83: where the server has the player looking, as a chat line the client's test reads (BoatRide.reply).
				player.networkHandler.sendPacket(new ChatMessagePacket("oftest look " + player.yaw + " " + player.pitch));
				break;
			case "unride": {
				// Out of the boat (it goes) and to x feet z.
				Entity vehicle = player.vehicle;
				if (vehicle != null) {
					player.setVehicle(vehicle);
					vehicle.markDead();
				}
				player.networkHandler.teleport(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]), player.yaw, 0);
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
