package org.studioquire.oldfaithful.polish.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.ServerWorld;

/**
 * Test-only (ServerCartsTest): #31 on the real server, with {@code -Doldfaithful.polish.scenario=carts} and
 * {@code -Doldfaithful.polish.scenario.polished} saying whether the polish mod is in. On the first overworld tick it
 * builds, high in the sky (above any hill animals could walk from) well east of the spawn, {@link CartScenes} on an east-west and a north-south axis, and on each
 * axis two long boosters: a pair of overlapping empty carts and a pair carrying pigs, rolling together at full pace
 * down {@link #LENGTH} blocks of track. Prints one line per check, "[polish-scenario] ...", then follows the boosters
 * for {@link #RUN} ticks: a pair that keeps meeting gains speed without limit, until the speed it stores overflows.
 * Needs spawn-animals on (beta's server removes animals otherwise, the riding pigs included).
 */
public final class CartScenario {
	static final int LENGTH = 960, Y = 120;
	static final int RUN = Integer.getInteger("oldfaithful.polish.scenario.run", 2200);

	private static int ticks = -1;
	private static final List<CartScenes> scenes = new ArrayList<>();
	private static final List<Booster> boosters = new ArrayList<>();
	private static boolean scenesTold;

	private CartScenario() {
	}

	public static void tick(ServerWorld world) {
		if (!"carts".equals(System.getProperty("oldfaithful.polish.scenario")) || world.dimension.id != 0 || ticks >= RUN) return;
		boolean polished = Boolean.getBoolean("oldfaithful.polish.scenario.polished");
		if (ticks < 0) {
			Vec3i spawn = world.getSpawnPos();
			int ex = Tracks.awayFromZero(spawn.x + 300, LENGTH + 2), ez = spawn.z + 100;
			int nx = Tracks.awayFromZero(spawn.x + 300, 40), nz = spawn.z + 160;
			// A server ticks an entity only with every chunk within 32 blocks of it loaded.
			load(world, ex - 48, ez - 48, ex + LENGTH + 48, ez + 8 + CartScenes.WIDTH + 48);
			load(world, nx - 48, nz - 48, nx + 8 + CartScenes.WIDTH + 48, nz + LENGTH + 48);
			for (boolean alongZ : new boolean[] {false, true}) {
				int ox = alongZ ? nx : ex, oz = alongZ ? nz : ez;
				Tracks t = new Tracks(world, ox, Y, oz, alongZ);
				t.lay(LENGTH, 0, 3);
				boosters.add(new Booster(t, 0, false));
				boosters.add(new Booster(t, 3, true));
				scenes.add(new CartScenes(world, alongZ ? ox + 8 : ox, Y, alongZ ? oz : oz + 8, alongZ));
			}
			say("built: east-west at " + ex + " " + ez + ", north-south at " + nx + " " + nz);
			ticks = 0;
			return;
		}
		ticks++;
		for (CartScenes s : scenes) s.tick();
		for (Booster b : boosters) b.tick(ticks);
		if (!scenesTold && scenes.get(0).done()) {
			scenesTold = true;
			for (CartScenes s : scenes) {
				for (CartScenes.Check c : s.checks(polished)) say((c.ok ? "ok: " : "WRONG: ") + c.text);
			}
		}
		if (ticks % 200 == 0 || ticks == RUN) {
			for (Booster b : boosters) say(b.report(ticks));
		}
		if (ticks == RUN) say("done");
	}

	/** Two overlapping carts rolling together at full pace on line w, and what became of their stored speed. */
	private static final class Booster {
		final Tracks t;
		final String name;
		final MinecartEntity a, b;
		final Entity riderA, riderB;
		int firstHuge = -1, firstInfinite = -1, firstNaN = -1, firstNaNPlace = -1;
		double most;

		Booster(Tracks t, int w, boolean ridden) {
			this.t = t;
			this.name = t.axis() + (ridden ? " with pigs" : " empty");
			a = t.cart(w, 1.5, 0, 0.4);
			b = t.cart(w, 1.8, 0, 0.4);
			riderA = ridden ? ride(a) : null;
			riderB = ridden ? ride(b) : null;
		}

		static Entity ride(MinecartEntity cart) {
			PigEntity pig = new PigEntity(cart.world);
			pig.setPositionAndAnglesKeepPrevAngles(cart.x, cart.y, cart.z, 0.0F, 0.0F);
			cart.world.spawnEntity(pig);
			pig.setVehicle(cart);
			return pig;
		}

		void tick(int now) {
			for (MinecartEntity c : new MinecartEntity[] {a, b}) {
				double vx = c.velocityX, vz = c.velocityZ;
				if (Double.isNaN(vx) || Double.isNaN(vz)) {
					if (firstNaN < 0) firstNaN = now;
				} else if (Double.isInfinite(vx) || Double.isInfinite(vz)) {
					if (firstInfinite < 0) firstInfinite = now;
				} else {
					double s = Math.max(Math.abs(vx), Math.abs(vz));
					most = Math.max(most, s);
					if (s >= 1.0E99 && firstHuge < 0) firstHuge = now;
				}
				if ((Double.isNaN(c.x) || Double.isNaN(c.z)) && firstNaNPlace < 0) firstNaNPlace = now;
			}
		}

		String report(int now) {
			return String.format(Locale.ROOT, "booster %s at tick %d: speed %s and %s (most finite %.3g); 1e99 first %s, Infinity %s, NaN %s,"
					+ " NaN place %s; carts at %s and %s along, %s; riders %s",
					name, now, v(a), v(b), most, when(firstHuge), when(firstInfinite), when(firstNaN), when(firstNaNPlace),
					place(a), place(b), (a.dead || b.dead ? "a cart dead" : "alive") + ", in the world: " + inWorld(a) + " " + inWorld(b),
					riderA == null ? "none" : place(riderA) + " and " + place(riderB) + (riderA.vehicle == a && riderB.vehicle == b ? " riding" : " not riding"));
		}

		String v(Entity e) {
			return String.format(Locale.ROOT, "%.3g,%.3g", e.velocityX, e.velocityZ);
		}

		String place(Entity e) {
			return String.format(Locale.ROOT, "%.1f (%.1f %.1f %.1f)", t.along(e), e.x, e.y, e.z);
		}

		String inWorld(Entity e) {
			return e.world.entities.contains(e) ? "yes" : "no";
		}

		static String when(int tick) {
			return tick < 0 ? "never" : "at " + tick;
		}
	}

	private static void load(ServerWorld world, int x0, int z0, int x1, int z1) {
		for (int x = x0 >> 4; x <= x1 >> 4; x++) {
			for (int z = z0 >> 4; z <= z1 >> 4; z++) world.chunkCache.loadChunk(x, z);
		}
	}

	private static void say(String s) {
		System.out.println("[polish-scenario] " + s);
	}
}
