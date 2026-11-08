package org.studioquire.oldfaithful.selftest;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.block.RailBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Rides a Buried Tower's cart run in the real game, as a player would: {@code -Doldfaithful.selftest.cartRide="sx sy sz ex ey ez lx ly lz times;..."} through SceneShot's world. For each run, its
 * chunks are made and settled with the player held over the top station, then a cart goes on the station's brake (s), the player gets in, and the lever beside it (l) is pulled; every tick the cart's
 * place is followed until it has stood still a while. A ride passes if the cart ends on the bottom station's brake (e) with the rider still in it and unhurt, having never left the rails. Peaceful,
 * so only the track is tried. Each ride's path goes into {@code rides.txt}.
 */
final class CartRide {
	static final String RIDES = given("oldfaithful.selftest.cartRide");
	static final boolean ENABLED = RIDES != null;
	private static final int SETTLE = 200, STILL = 40, LONGEST = 3000;

	private final File out;
	private final SelfTest results;
	private final String[] runs;
	private final StringBuilder report = new StringBuilder();
	private int run = -1, times, time, phase, ticks, still, offRail, lowest;
	private int sx, sy, sz, ex, ey, ez, lx, ly, lz;
	private MinecartEntity cart;
	private double fastest, traveled;
	private final StringBuilder path = new StringBuilder();

	CartRide(String out, SelfTest results) {
		this.out = new File(out);
		this.results = results;
		runs = RIDES.split(";");
	}

	private static String given(String property) {
		String v = System.getProperty(property);
		return v == null || v.trim().isEmpty() ? null : v;
	}

	/** One game tick; true when every ride's done. */
	boolean tick(Minecraft mc) {
		mc.options.hideHud = true;
		mc.options.difficulty = 0;
		ClientPlayerEntity p = mc.player;
		p.health = Math.max(p.health, 1);
		World w = mc.world;
		if (run < 0 || phase == 3) {
			if (run >= 0 && ++time < times) {
				phase = 1;
				ticks = 0;
			} else {
				run++;
				if (run >= runs.length) {
					write();
					return true;
				}
				begin(mc, runs[run].trim().split("\\s+"));
			}
		}
		ticks++;
		if (phase == 0) {
			hold(p);
			if (ticks >= SETTLE) {
				phase = 1;
				ticks = 0;
			}
		} else if (phase == 1) {
			hold(p);
			if (ticks < 5) return false;
			if (cart != null) cart.markDead();
			// The last ride left the lever pulled, which would set a cart off as soon as it's put down.
			if ((w.getBlockMeta(lx, ly, lz) & 8) != 0) Block.LEVER.onUse(w, lx, ly, lz, p);
			p.noClip = false;
			p.health = 20;
			cart = new MinecartEntity(w, sx + 0.5, sy + 0.5, sz + 0.5, 0);
			w.spawnEntity(cart);
			p.setVehicle(cart);
			still = offRail = 0;
			fastest = traveled = 0;
			lowest = 20;
			path.setLength(0);
			phase = 2;
			ticks = 0;
		} else if (phase == 2) {
			if (ticks == 3) {
				results.check(String.format(Locale.ROOT, "cart ride %d.%d: a brake at the top (%d) and a lever by it (%d)", run, time, w.getBlockId(sx, sy, sz), w.getBlockId(lx, ly, lz)),
						w.getBlockId(sx, sy, sz) == Block.POWERED_RAIL.id && w.getBlockId(lx, ly, lz) == Block.LEVER.id);
				Block.LEVER.onUse(w, lx, ly, lz, p);
			}
			follow(p, w);
		}
		return false;
	}

	private void begin(Minecraft mc, String[] v) {
		sx = Integer.parseInt(v[0]);
		sy = Integer.parseInt(v[1]);
		sz = Integer.parseInt(v[2]);
		ex = Integer.parseInt(v[3]);
		ey = Integer.parseInt(v[4]);
		ez = Integer.parseInt(v[5]);
		lx = Integer.parseInt(v[6]);
		ly = Integer.parseInt(v[7]);
		lz = Integer.parseInt(v[8]);
		times = v.length > 9 ? Integer.parseInt(v[9]) : 1;
		time = 0;
		phase = 0;
		ticks = 0;
		for (int x = sx - 48 >> 4; x <= sx + 48 >> 4; x++) {
			for (int z = sz - 48 >> 4; z <= sz + 48 >> 4; z++) mc.world.getChunk(x, z);
		}
	}

	private void hold(ClientPlayerEntity p) {
		if (p.vehicle != null) p.setVehicle(null);
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.health = 20;
		p.air = 300;
		p.setPositionAndAngles(sx + 0.5, sy + 3 + 1.62, sz + 0.5, 0, 90);
	}

	private void follow(ClientPlayerEntity p, World w) {
		int bx = MathHelper.floor(cart.x), by = MathHelper.floor(cart.y), bz = MathHelper.floor(cart.z);
		boolean onRail = RailBlock.isRail(w, bx, by, bz) || RailBlock.isRail(w, bx, by - 1, bz);
		if (!onRail) offRail++;
		double speed = Math.sqrt((cart.x - cart.prevX) * (cart.x - cart.prevX) + (cart.y - cart.prevY) * (cart.y - cart.prevY) + (cart.z - cart.prevZ) * (cart.z - cart.prevZ));
		fastest = Math.max(fastest, speed);
		traveled += speed;
		lowest = Math.min(lowest, p.health);
		if (ticks % 5 == 0) path.append(String.format(Locale.ROOT, " %d:%.1f,%.1f,%.1f", ticks, cart.x, cart.y, cart.z));
		still = speed < 0.002 && ticks > 20 ? still + 1 : 0;
		boolean riding = p.vehicle == cart && !cart.dead;
		if (still < STILL && ticks < LONGEST && riding) return;
		boolean there = bx == ex && bz == ez && (by == ey || by - 1 == ey);
		String line = String.format(Locale.ROOT, "cart ride %d.%d from %d,%d,%d: stopped at %d,%d,%d after %d ticks, %.0f blocks, fastest %.2f a tick; off the rails %d ticks; rider %s, health at least %d; wanted %d,%d,%d",
				run, time, sx, sy, sz, bx, by, bz, ticks, traveled, fastest, offRail, riding ? "in" : "out", lowest, ex, ey, ez);
		report.append(line).append('\n').append("   ").append(path).append('\n');
		results.check(line, there && riding && offRail == 0 && lowest == 20);
		p.setVehicle(null);
		cart.markDead();
		cart = null;
		phase = 3;
	}

	private void write() {
		try {
			out.mkdirs();
			Files.write(new File(out, "rides.txt").toPath(), ("seed " + SceneShot.SEED + "\n" + report).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
