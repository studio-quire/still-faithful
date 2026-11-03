package org.studioquire.oldfaithful.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.MultiplayerClientPlayerEntity;
import net.minecraft.client.network.OtherPlayerEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ClientWorld;
import org.studioquire.oldfaithful.selftest.mixin.LivingEntityLerpAccessor;

/**
 * The hidden client's half of `:harness:fixesMultiplayerTest`: joined to a test server (whose {@link MultiplayerScenes}
 * built it a scene), it checks the client-side multiplayer fixes against what the run says this client and server
 * are ({@code oldfaithful.selftest.mp.fixedClient}, {@code .fixedServer}): where a fix is missing on the side that
 * matters, it checks the bug still shows, so the check is known to see it. In order:
 * <ul>
 * <li>#15: two pigs penned together; whenever one has finished moving to the server's position, how far the client's
 * own physics has put it from there (the fix: nowhere);</li>
 * <li>#36: three dirt blocks mined by hand, the way the game's mouse handling calls the interaction manager: the first,
 * then one after letting go, then one after holding on another block; the ticks each took (beta: 5 more for the later
 * two), and none of them coming back (the server accepted each break);</li>
 * <li>#50: another player ("SkyTurner", a raw client of the task) turned by the server in steps of 45 degrees; where
 * this client has them facing once the turn is done (beta: often a third short);</li>
 * <li>#49: this player dies, a pig is removed while they're dead, they respawn: the pig must be gone from this
 * client's world (beta: frozen there), the other one sent again;</li>
 * <li>#62, on joining: a cow spawner 20 blocks west shows a cow where both sides have the fix (beta: a pig).</li>
 * </ul>
 */
public final class MultiplayerCheck {
	private static final int Y = MultiplayerScenes.FLOOR + 1;
	private static final int[] TURNS = {45, 90, 135, 180};

	private final SelfTest results;
	private final boolean fixedClient = Boolean.getBoolean("oldfaithful.selftest.mp.fixedClient");
	private final boolean fixedServer = Boolean.getBoolean("oldfaithful.selftest.mp.fixedServer");
	private final String label;
	private int stage;
	private int ticks;
	private int px, pz;
	private int settled;

	// #15
	private double pigDeviation;
	private static final int PIG_SAMPLES = 20;
	private int pigSamples, pigExtraTicks;
	// #36
	private int mined;
	private final int[] mineTicks = new int[3];
	private int mineCount;
	private int pause;
	/** While mining, the game's own per-tick mouse handling stays out of it (MinecraftMouseDriveMixin). */
	public static volatile boolean drivingMouse;
	// #50
	private int turn;
	private final List<Double> turnErrors = new ArrayList<>();
	// #49
	private int stalePig = -1, keptPig = -1;
	private boolean frozenWhileDead;

	MultiplayerCheck(SelfTest results) {
		this.results = results;
		label = (fixedClient ? "fixed client" : "client without the fixes") + ", " + (fixedServer ? "fixed server" : "server without the fixes");
	}

	/** One game tick; true when done. */
	boolean tick(Minecraft mc) {
		ticks++;
		if (mc.currentScreen instanceof DisconnectedScreen) {
			results.fail("multiplayer (" + label + "): disconnected in stage " + stage, new AssertionError());
			return true;
		}
		switch (stage) {
			case 0: // joined, the scene arrived
				if (mc.world == null || mc.player == null || mc.currentScreen != null) return timeout(mc, 1200, "joining");
				px = MathHelper.floor(mc.player.x);
				pz = MathHelper.floor(mc.player.z);
				if (mc.world.getBlockId(px - 2, Y, pz) != 3 || pigs(mc).size() < 2) return timeout(mc, 1200, "the scene arriving");
				if (mc.world.getBlockId(px - 20, MultiplayerScenes.FLOOR, pz) != 52) return timeout(mc, 1200, "the spawner arriving");
				// Standing (mining in the air is 5 times slower), and settled.
				if (!mc.player.onGround || ++settled < 20) return timeout(mc, 1200, "standing on the scene");
				results.pass("multiplayer (" + label + "): joined, scene in view at " + px + " " + Y + " " + pz);
				spawnerResult(mc);
				System.out.println("[oldfaithful-selftest] mp ready");
				next();
				return false;
			case 1: // #36 mining, with #15 sampled meanwhile
				samplePigs(mc);
				drivingMouse = true;
				if (mine(mc)) {
					drivingMouse = false;
					next();
				}
				return ticks > 1200 && fail("mining didn't finish");
			case 2: // let the server answer the breaks, keep sampling pigs
				samplePigs(mc);
				if (ticks < 100) return false;
				miningResults(mc);
				next();
				return false;
			case 3: { // #50, with #15 sampled meanwhile
				samplePigs(mc);
				if (turn == TURNS.length) {
					// Turns done. A pig counts only on a tick it has reached the server's position, and how many those are
					// varies a lot with the pigs' wandering (14 to 45 over the same stages), so sample on until there are
					// enough to judge, up to a minute more.
					if (pigSamples <= PIG_SAMPLES && ++pigExtraTicks < 1200) return false;
					pigResults();
					next();
					return false;
				}
				OtherPlayerEntity turner = turner(mc);
				if (turner == null) return timeout(mc, 200, "SkyTurner in view");
				if (ticks == 1) mc.player.sendChatMessage("!oftest turn SkyTurner " + TURNS[turn]);
				if (ticks < 40) return false;
				turnErrors.add(degreesOff(turner.yaw, TURNS[turn]));
				ticks = 0;
				if (++turn == TURNS.length) turnResults();
				return false;
			}
			case 4: { // #49: die
				if (ticks == 1) {
					List<PigEntity> pigs = pigs(mc);
					stalePig = pigs.get(0).id;
					keptPig = pigs.get(1).id;
					mc.player.sendChatMessage("!oftest die");
				}
				if (mc.player.health > 0) return timeout(mc, 200, "dying");
				// The body is removed 20 ticks after death; then a pig goes away.
				if (ticks == 80) mc.player.sendChatMessage("!oftest unpig " + stalePig);
				if (ticks < 130) return false;
				frozenWhileDead = ((ClientWorld) mc.world).getEntity(stalePig) != null;
				((MultiplayerClientPlayerEntity) mc.player).respawn();
				mc.setScreen(null);
				next();
				return false;
			}
			case 5: // respawned
				if (mc.player.health <= 0 || mc.currentScreen != null) return timeout(mc, 400, "respawning");
				if (ticks < 80) return false;
				respawnResults(mc);
				return true;
			default:
				return true;
		}
	}

	private void next() {
		stage++;
		ticks = 0;
	}

	private boolean timeout(Minecraft mc, int limit, String what) {
		if (ticks < limit) return false;
		String state = mc.world == null ? "no world" : "screen " + mc.currentScreen + ", player " + (mc.player == null ? "none"
				: String.format(Locale.ROOT, "at %.2f %.2f %.2f, health %d", mc.player.x, mc.player.y, mc.player.z, mc.player.health))
				+ ", " + pigs(mc).size() + " pigs, " + mc.world.entities.size() + " entities";
		return fail("timed out waiting for " + what + " (" + state + ")");
	}

	private boolean fail(String what) {
		results.fail("multiplayer (" + label + "), stage " + stage + ": " + what, new AssertionError());
		return true;
	}

	/** The scene's two pigs, in their pen 3 blocks east (the world has others on the ground). */
	private List<PigEntity> pigs(Minecraft mc) {
		List<PigEntity> pigs = new ArrayList<>();
		for (Object o : mc.world.entities) {
			if (!(o instanceof PigEntity)) continue;
			PigEntity pig = (PigEntity) o;
			if (Math.abs(pig.x - (px + 3.5)) < 1.5 && Math.abs(pig.z - (pz + 0.5)) < 1.5 && pig.y > Y - 1) pigs.add(pig);
		}
		pigs.sort((a, b) -> Integer.compare(a.id, b.id));
		return pigs;
	}

	private static OtherPlayerEntity turner(Minecraft mc) {
		for (Object o : mc.world.entities) {
			if (o instanceof OtherPlayerEntity && "SkyTurner".equals(((OtherPlayerEntity) o).name)) return (OtherPlayerEntity) o;
		}
		return null;
	}

	/** #15: a pig that has reached the server's last position should be there, not where its own physics took it. */
	private void samplePigs(Minecraft mc) {
		for (PigEntity pig : pigs(mc)) {
			if (!pig.interpolateOnly || ((LivingEntityLerpAccessor) pig).oldfaithful$lerpSteps() != 0) continue;
			double d = Math.max(Math.abs(pig.x - pig.trackedPosX / 32.0), Math.abs(pig.z - pig.trackedPosZ / 32.0));
			pigDeviation = Math.max(pigDeviation, d);
			pigSamples++;
		}
	}

	private void pigResults() {
		String seen = String.format(Locale.ROOT, "%d samples, farthest %.4f blocks from the server's position", pigSamples, pigDeviation);
		if (fixedClient) {
			results.check("#15 (" + label + "): penned pigs stay where the server puts them (" + seen + ")", pigSamples > PIG_SAMPLES && pigDeviation < 1e-6);
		} else {
			results.check("#15 control (" + label + "): beta's own physics moves them off the server's position (" + seen + ")",
					pigSamples > PIG_SAMPLES && pigDeviation > 0.01);
		}
	}

	/**
	 * #36: as the game does each tick: a click calls attackBlock, then the held button processBlockBreakingAction on
	 * what's aimed at, and no button cancelBlockBreaking. Returns true when all three blocks are mined.
	 */
	private boolean mine(Minecraft mc) {
		int x = px - 2, z = pz - 1 + mined;
		if (pause > 0) {
			// Between blocks: after the first, the button let go; after the second, held on the next block.
			pause--;
			if (mined == 1) mc.interactionManager.cancelBlockBreaking();
			else mc.interactionManager.processBlockBreakingAction(x, Y, z, 5);
			return false;
		}
		if (mineCount == 0) mc.interactionManager.attackBlock(x, Y, z, 5);
		mc.interactionManager.processBlockBreakingAction(x, Y, z, 5);
		mineCount++;
		if (mc.world.getBlockId(x, Y, z) == 0) {
			mineTicks[mined++] = mineCount;
			mineCount = 0;
			pause = 10;
		} else if (mineCount > 200) {
			mineTicks[mined++] = -1;
			mineCount = 0;
			pause = 10;
		}
		return mined == 3;
	}

	private void miningResults(Minecraft mc) {
		boolean kept = true;
		for (int dz = -1; dz <= 1; dz++) kept &= mc.world.getBlockId(px - 2, Y, pz + dz) == 0;
		results.check("#36 (" + label + "): all three blocks still mined 5 s later (the server took each break)", kept);
		int first = mineTicks[0];
		String seen = "ticks: " + first + " first, " + mineTicks[1] + " after letting go, " + mineTicks[2] + " after holding on";
		if (fixedClient) {
			results.check("#36 (" + label + "): no leftover pause before later blocks (" + seen + ")",
					first > 0 && mineTicks[1] == first && mineTicks[2] == first);
		} else {
			results.check("#36 control (" + label + "): beta's 5 dead ticks before every later block (" + seen + ")",
					first > 0 && mineTicks[1] == first + 5 && mineTicks[2] == first + 5);
		}
	}

	private void turnResults() {
		double worst = 0;
		for (double e : turnErrors) worst = Math.max(worst, e);
		String seen = "off by " + turnErrors.stream().map(e -> String.format(Locale.ROOT, "%.1f", e)).reduce((a, b) -> a + ", " + b).orElse("") + " degrees";
		if (fixedClient) {
			results.check("#50 (" + label + "): another player finishes each 45-degree turn (" + seen + ")", worst < 1.0);
		} else {
			results.check("#50 control (" + label + "): beta leaves turns short (" + seen + ")", worst > 5.0);
		}
	}

	/** #62: the cow spawner the scene put 20 blocks west: what this client shows in it. */
	private void spawnerResult(Minecraft mc) {
		BlockEntity be = mc.world.getBlockEntity(px - 20, MultiplayerScenes.FLOOR, pz);
		String shown = be instanceof MobSpawnerBlockEntity ? ((MobSpawnerBlockEntity) be).getSpawnedEntityId() : "no spawner";
		if (fixedClient && fixedServer) {
			results.check("#62 (" + label + "): a cow spawner shows a cow (" + shown + ")", "Cow".equals(shown));
		} else {
			results.check("#62 control (" + label + "): beta shows every spawner as a pig (" + shown + ")", "Pig".equals(shown));
		}
	}

	private void respawnResults(Minecraft mc) {
		ClientWorld world = (ClientWorld) mc.world;
		boolean staleGone = world.getEntity(stalePig) == null;
		boolean keptBack = world.getEntity(keptPig) != null;
		String seen = "removed while dead: " + (frozenWhileDead ? "still shown during death" : "already gone during death")
				+ ", after respawn " + (staleGone ? "gone" : "still there") + "; the other " + (keptBack ? "sent again" : "missing");
		results.check("#49 (" + label + "): the other pig is back after the respawn (" + seen + ")", keptBack);
		if (fixedClient || fixedServer) {
			results.check("#49 (" + label + "): the pig removed while dead is gone after the respawn (" + seen + ")", staleGone);
		} else {
			results.check("#49 control (" + label + "): beta keeps the pig removed while dead (" + seen + ")", !staleGone);
		}
	}

	private static double degreesOff(float yaw, int target) {
		double d = (yaw - target) % 360.0;
		if (d < -180) d += 360;
		if (d >= 180) d -= 360;
		return Math.abs(d);
	}
}
