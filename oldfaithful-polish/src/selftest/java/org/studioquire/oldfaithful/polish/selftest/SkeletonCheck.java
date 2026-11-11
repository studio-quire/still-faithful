package org.studioquire.oldfaithful.polish.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.SkeletonAim;
import org.studioquire.oldfaithful.polish.selftest.mixin.SkeletonEntityInvoker;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;

/**
 * Skeleton Arrows ({@code mobs.skeletonArrows}): where a skeleton's arrow starts against its eyes, then how often
 * skeletons hit, shooting through the game's own attack and arrows at targets with a player's box and the point a
 * skeleton aims at on a player in single player, in five scenes of 15 lanes side by side, 20 volleys each: level ground
 * 6 blocks across, a player 4 up a cliff and 4 down one 5 across, a tunnel one wide and two high 5 across, and spider
 * jockeys 4 above a player 5 across. Each share of hits is checked against what a re-run of beta's arrow code
 * gives for the same scene ({@code tools/skeleton_arrows.py scenes}), within four standard deviations of 300 shots and
 * a point: with the switch, beta's own share in the open, on the cliffs and from the jockeys (just moving the start
 * would give about 58% up the cliff, not 74%, and the jockeys' arrows would nearly all hit their spiders), and hits in
 * the tunnel; without it, beta's shares, and none in the tunnel, where beta's arrow starts in the ceiling. Hardly any
 * arrow hits a jockey's own spider, either way (beta's do, about 1 in 2,200 there).
 */
public final class SkeletonCheck implements WorldCheck {
	private static final String KEY = "mobs.skeletonArrows";
	private static final int Y = 100, LANES = 15, VOLLEYS = 20, LANE_GAP = 4, SCENE_GAP = 22, MOST_TICKS = 60;

	private enum Kind { LEVEL, CLIFF, TUNNEL }

	/** A scene, and the share of hits the re-run gives for it, with the switch and without. */
	private static final class Scene {
		final String name;
		final Kind kind;
		final int across, rise;
		final double polished, beta;
		final boolean jockey;
		final List<Lane> lanes = new ArrayList<>();

		Scene(String name, Kind kind, int across, int rise, double polished, double beta) {
			this(name, kind, across, rise, polished, beta, false);
		}

		Scene(String name, Kind kind, int across, int rise, double polished, double beta, boolean jockey) {
			this.name = name;
			this.kind = kind;
			this.across = across;
			this.rise = rise;
			this.polished = polished;
			this.beta = beta;
			this.jockey = jockey;
		}
	}

	private static final class Lane {
		final SkeletonEntity skeleton;
		final Target target;
		final Mount mount;
		final double farX;

		Lane(SkeletonEntity skeleton, Target target, Mount mount, double farX) {
			this.skeleton = skeleton;
			this.target = target;
			this.mount = mount;
			this.farX = farX;
		}
	}

	/** A jockey's spider: a spider's box where the game seats its rider, standing still, counting its rider's hits. */
	private static final class Mount extends SpiderEntity {
		final SkeletonEntity rider;
		int hits;

		Mount(World world, SkeletonEntity rider) {
			super(world);
			this.rider = rider;
		}

		@Override
		public void tick() {
		}

		@Override
		public boolean damage(Entity source, int amount) {
			if (source == rider) hits++;
			return true;
		}
	}

	/**
	 * A player's box (0.6 by 1.8 from its feet) and the point a skeleton aims at on one: beta's player in single player
	 * keeps its y 1.62 over its feet and answers 0.12 for its eyes, so y + getEyeHeight() is 1.74 over the feet. Counts its
	 * own skeleton's hits; arrows die on it as on a player.
	 */
	private static final class Target extends Entity {
		final SkeletonEntity shooter;
		int hits;

		Target(World world, SkeletonEntity shooter) {
			super(world);
			this.shooter = shooter;
			setBoundingBoxSpacing(0.6F, 1.8F);
		}

		@Override
		public float getEyeHeight() {
			return 1.74F;
		}

		@Override
		public boolean isCollidable() {
			return !dead;
		}

		@Override
		public boolean damage(Entity source, int amount) {
			if (source == shooter) hits++;
			return true;
		}

		@Override
		protected void initDataTracker() {
		}

		@Override
		protected void readNbt(NbtCompound nbt) {
		}

		@Override
		protected void writeNbt(NbtCompound nbt) {
		}
	}

	private final Scene[] scenes = {
			new Scene("level ground, 6 across", Kind.LEVEL, 6, 0, 71.8, 72.1),
			new Scene("a player 4 up a cliff, 5 across", Kind.CLIFF, 5, 4, 73.5, 73.6),
			new Scene("a player 4 down a cliff, 5 across", Kind.CLIFF, 5, -4, 67.1, 65.9),
			new Scene("a tunnel one wide and two high, 5 across", Kind.TUNNEL, 5, 0, 57.3, 0.0),
			new Scene("spider jockeys 4 above a player, 5 across", Kind.CLIFF, 5, -4, 65.7, 64.5, true),
	};
	private final List<ArrowEntity> flying = new ArrayList<>();
	private SelfTest results;
	private World world;
	private boolean on;
	private int volleys, ticks;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(KEY);
		int bx = MathHelper.floor(mc.player.x), bz = MathHelper.floor(mc.player.z);
		int x0 = bx - 40, z0 = bz - 110;
		for (int x = x0 - 16; x <= x0 + SCENE_GAP * scenes.length + 16; x += 16) {
			for (int z = z0 - 16; z <= z0 + LANES * LANE_GAP + 16; z += 16) world.getBlockId(x, Y, z);
		}
		for (int s = 0; s < scenes.length; s++) {
			Scene scene = scenes[s];
			int sx = x0 + s * SCENE_GAP;
			for (int x = sx - 2; x <= sx + scene.across + 4; x++) {
				for (int y = Y + Math.min(0, scene.rise) - 2; y <= Y + Math.max(0, scene.rise) + 7; y++) {
					for (int z = z0 - 2; z <= z0 + (LANES - 1) * LANE_GAP + 2; z++) {
						if (world.getBlockId(x, y, z) != 0) world.setBlock(x, y, z, 0);
					}
				}
			}
			for (int i = 0; i < LANES; i++) build(scene, sx, z0 + i * LANE_GAP);
		}
		startsAt(scenes[0].lanes.get(0));
		if (on) {
			for (Scene scene : scenes) {
				Lane lane = scene.lanes.get(0);
				double across = scene.across, dy = lane.target.y + 1.74F - 0.2F - (lane.skeleton.y + lane.skeleton.getEyeHeight() - 0.1F);
				double[] aim = SkeletonAim.aim(across, dy + (float) across * 0.2F, 0.6F, 12.0F);
				System.out.printf(Locale.ROOT, "[skeleton check] %s: beta's aim from the eyes %.4f, aimed %.4f, spread %.3f%n", scene.name,
						dy + (float) across * 0.2F, aim[0], aim[1]);
			}
		}
	}

	@Override
	public boolean tick(Minecraft mc) {
		ticks++;
		if (volleys > 0 && ticks < MOST_TICKS && !landed()) return false;
		for (ArrowEntity arrow : flying) arrow.markDead();
		flying.clear();
		if (volleys == VOLLEYS) {
			report();
			for (Scene scene : scenes) {
				for (Lane lane : scene.lanes) {
					lane.target.markDead();
					if (lane.mount != null) lane.mount.markDead();
				}
			}
			return true;
		}
		for (Scene scene : scenes) {
			for (Lane lane : scene.lanes) {
				lane.skeleton.attackCooldown = 0;
				((SkeletonEntityInvoker) lane.skeleton).oldfaithful$attack(lane.target, 5.0F);
			}
		}
		for (Object e : world.entities) {
			if (e instanceof ArrowEntity && ours(((ArrowEntity) e).owner)) flying.add((ArrowEntity) e);
		}
		volleys++;
		ticks = 0;
		return false;
	}

	/** Every arrow of the volley has hit, stuck or gone past the scene. */
	private boolean landed() {
		if (ticks < 2) return false;
		for (ArrowEntity arrow : flying) {
			if (arrow.dead || arrow.y < Y - 12) continue;
			if (arrow.x == arrow.prevX && arrow.y == arrow.prevY && arrow.z == arrow.prevZ) continue;
			if (arrow.x > laneOf(arrow).farX) continue;
			return false;
		}
		return true;
	}

	private Lane laneOf(ArrowEntity arrow) {
		for (Scene scene : scenes) {
			for (Lane lane : scene.lanes) {
				if (lane.skeleton == arrow.owner) return lane;
			}
		}
		throw new IllegalStateException("an arrow of no lane's");
	}

	private boolean ours(Object owner) {
		for (Scene scene : scenes) {
			for (Lane lane : scene.lanes) {
				if (lane.skeleton == owner) return true;
			}
		}
		return false;
	}

	private void report() {
		for (Scene scene : scenes) {
			int hits = 0, shots = scene.lanes.size() * VOLLEYS;
			for (Lane lane : scene.lanes) hits += lane.target.hits;
			double share = 100.0 * hits / shots, want = on ? scene.polished : scene.beta;
			double sd = 100.0 * Math.sqrt(want / 100 * (1 - want / 100) / shots);
			boolean ok = want == 0 ? hits == 0 : Math.abs(share - want) <= 4 * sd + 1;
			results.check(String.format(Locale.ROOT, "still skeleton arrows: %s, %d of %d hit (%.1f%%; the re-run of beta's code gives %.1f%% %s)",
					scene.name, hits, shots, share, want, on ? "for these arrows" : "for beta's"), ok);
			if (scene.jockey) {
				int own = 0;
				for (Lane lane : scene.lanes) own += lane.mount.hits;
				// The re-run: 0.045% of beta's arrows hit their spider here, 0.002% with the switch; without the mount spared, all of them.
				results.check(String.format(Locale.ROOT, "still skeleton arrows: %s, %d of %d hit their own spider (a stray one at most%s)", scene.name, own, shots,
						on ? "" : ", or three for beta's"), own <= (on ? 1 : 3));
			}
		}
	}

	/** The arrow's start against the skeleton's eyes: less 0.1, at them, with the switch; 1.3 over them, beta's. */
	private void startsAt(Lane lane) {
		SkeletonEntity skeleton = lane.skeleton;
		skeleton.attackCooldown = 0;
		((SkeletonEntityInvoker) skeleton).oldfaithful$attack(lane.target, 5.0F);
		ArrowEntity arrow = null;
		for (Object e : world.entities) {
			if (e instanceof ArrowEntity && ((ArrowEntity) e).owner == skeleton) arrow = (ArrowEntity) e;
		}
		double above = arrow == null ? Double.NaN : arrow.y - (skeleton.y + skeleton.getEyeHeight());
		results.check(String.format(Locale.ROOT, "still skeleton arrows: the arrow started %.2f above the skeleton's eyes (%s)", above,
				on ? "-0.1, at them" : "beta: 1.3, over its head"), arrow != null && Math.abs(above - (on ? -0.1 : 1.3)) < 0.01);
		if (arrow != null) arrow.markDead();
	}

	/**
	 * One lane: the skeleton's block at (sx, Y, z), shooting along +x at a target `across` blocks on and `rise` up, each in
	 * the middle of its block; ground three wide under them, a wall past the target to stop what misses.
	 */
	private void build(Scene scene, int sx, int z) {
		int tx = sx + scene.across, back = tx + 3, low = Math.min(0, scene.rise), stone = Block.STONE.id;
		switch (scene.kind) {
			case LEVEL:
				fill(sx - 1, Y - 1, z - 1, back, Y - 1, z + 1, stone);
				break;
			case CLIFF:
				if (scene.rise > 0) {
					fill(sx - 1, Y - 1, z - 1, back, Y - 1, z + 1, stone);
					fill(tx, Y, z - 1, back, Y + scene.rise - 1, z + 1, stone);
				} else {
					// The skeleton at the edge of its ledge, the target on the ground below.
					fill(sx - 1, Y + scene.rise - 1, z - 1, back, Y + scene.rise - 1, z + 1, stone);
					fill(sx - 1, Y + scene.rise, z - 1, sx, Y - 1, z + 1, stone);
				}
				break;
			case TUNNEL:
				back = tx + 2;
				fill(sx - 1, Y - 1, z - 1, back, Y + 2, z + 1, stone);
				fill(sx, Y, z, back - 1, Y + 1, z, 0);
				break;
		}
		fill(back, Y + low, z - 2, back, Y + Math.max(0, scene.rise) + 5, z + 2, stone);
		SkeletonEntity skeleton = new SkeletonEntity(world);
		skeleton.setPositionAndAnglesKeepPrevAngles(sx + 0.5, Y, z + 0.5, -90.0F, 0.0F);
		Mount mount = null;
		if (scene.jockey) {
			mount = new Mount(world, skeleton);
			mount.setPositionAndAnglesKeepPrevAngles(sx + 0.5, Y, z + 0.5, -90.0F, 0.0F);
			world.spawnEntity(mount);
			skeleton.setVehicle(mount);
			mount.updatePassengerPosition();
			// Seated by the game; the world would tick a mount's passenger, and these skeletons only shoot when told.
			mount.passenger = null;
		}
		Target target = new Target(world, skeleton);
		target.setPositionAndAnglesKeepPrevAngles(tx + 0.5, Y + scene.rise, z + 0.5, 0.0F, 0.0F);
		world.spawnEntity(target);
		scene.lanes.add(new Lane(skeleton, target, mount, back + 1.0));
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) world.setBlock(x, y, z, id);
			}
		}
	}
}
