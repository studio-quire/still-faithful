package org.studioquire.oldfaithful.polish.selftest;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.FenceJoins;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.selftest.FenceScene;
import org.studioquire.oldfaithful.selftest.FenceWalk;
import org.studioquire.oldfaithful.selftest.ScreenCapture;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.StartupCheck;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.MinecraftPickInvoker;

/**
 * #30 in single player, where the world runs on this client, so the shape is the switch's: the walks of
 * {@link FenceWalk} (multiplayer's mixes are `:harness:fencesMultiplayerTest`'s); what the crosshair meets past a post
 * (the post, the gap beside it, a block and a pig behind the gap), placing against a post and picking it, as the game
 * does them from the crosshair; a pig led along a path round the end of a row of fences; and pictures of the outline on
 * a lone post, a line and a corner in local/run/fences/ ("-thin", or "-beta" with the switch off). Then Fence Joins
 * ({@link #joins}).
 */
public final class FenceCheck implements WorldCheck {
	private static final String KEY = "blocks.fenceShape", JOINS_KEY = "blocks.fenceJoins";
	private static final int FLOOR = 118;
	private static final File OUT = new File("../fences");
	/** Fence Joins' scene: three rows of things with fences running into them from the west, this far south of the origin. */
	private static final int JOINS_Z = 44;

	private interface Step {
		boolean tick(Minecraft mc);
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private SelfTest results;
	private World world;
	private boolean thin, joined;
	private int ox, oz, feet;
	private FenceWalk walk;
	private PigEntity pig;
	private double[] hold;
	private Object[] saved;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		thin = PolishConfig.applied(KEY);
		joined = PolishConfig.applied(JOINS_KEY);
		ox = MathHelper.floor(mc.player.x) + 8;
		oz = MathHelper.floor(mc.player.z) + 64;
		feet = FLOOR + 1;
		saved = new Object[] {mc.options.hideHud, mc.player.x, mc.player.y - mc.player.standingEyeHeight, mc.player.z};
		for (int x = ox - 16; x <= ox + 32; x += 16) {
			for (int z = oz - 16; z <= oz + 48; z += 16) world.getBlockId(x, FLOOR, z);
		}
		FenceScene.build(world, ox, FLOOR, oz);
		walk = new FenceWalk(results, "single player, " + (thin ? "thin" : "beta's"), thin, (c, x, y, z, yaw) -> {
			c.player.velocityX = c.player.velocityY = c.player.velocityZ = 0;
			c.player.setPositionAndAnglesKeepPrevAngles(x, y, z, yaw, 0);
		}, ox, FLOOR, oz);
		plan.add(c -> walk.tick(c));
		plan.add(c -> {
			aiming(c);
			return true;
		});
		pathing();
		outline("post", ox, oz);
		outline("line", ox + FenceScene.LINE + 1, oz);
		outline("corner", ox + FenceScene.CORNER, oz);
		joins();
	}

	@Override
	public boolean tick(Minecraft mc) {
		if (hold != null) {
			PlayerEntity p = mc.player;
			p.noClip = true;
			p.velocityX = p.velocityY = p.velocityZ = 0;
			p.setPositionAndAnglesKeepPrevAngles(hold[0], hold[1], hold[2], (float) hold[3], (float) hold[4]);
			p.prevYaw = p.yaw;
			p.prevPitch = p.pitch;
			world.setTime(6000);
		}
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
		}
		mc.options.hideHud = (Boolean) saved[0];
		mc.player.noClip = false;
		mc.player.setPositionAndAnglesKeepPrevAngles((Double) saved[1], (Double) saved[2], (Double) saved[3], 0, 0);
		return true;
	}

	private String label() {
		return thin ? "thin" : "beta";
	}

	/** The player standing at x feet z looking along yaw and pitch, and what the crosshair meets, as each frame works it out. */
	private HitResult aim(Minecraft mc, double x, double z, float yaw, float pitch) {
		PlayerEntity p = mc.player;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		p.setPositionAndAnglesKeepPrevAngles(x, feet, z, yaw, pitch);
		p.prevYaw = yaw;
		p.prevPitch = pitch;
		mc.gameRenderer.updateTargetedEntity(1.0F);
		return mc.crosshairTarget;
	}

	private static float pitch(double drop, double across) {
		return (float) Math.toDegrees(Math.atan2(drop, across));
	}

	private String at(HitResult h) {
		if (h == null) return "nothing";
		if (h.type == HitResultType.ENTITY) return "entity " + h.entity.getClass().getSimpleName();
		return String.format(java.util.Locale.ROOT, "block %d %d %d (%s), side %d at x %+.3f z %+.3f", h.blockX - ox, h.blockY - feet, h.blockZ - oz,
				world.getBlockId(h.blockX, h.blockY, h.blockZ) == Block.FENCE.id ? "fence" : "id " + world.getBlockId(h.blockX, h.blockY, h.blockZ),
				h.side, h.pos.x - ox, h.pos.z - oz);
	}

	private boolean block(HitResult h, int dx, int dz) {
		return h != null && h.type == HitResultType.BLOCK && h.blockX == ox + dx && h.blockY == feet && h.blockZ == oz + dz;
	}

	/**
	 * From the east, at the lone post's east face half up: the post (thin, its face 0.625 across) or the block (1.0);
	 * a stone placed from it lands east of it either way; pick block takes the fence. Then 0.3 beside the post's middle
	 * (in the block, clear of the post), at a stone wall 2 west: the wall (thin) or the fence; and from the south, the same
	 * way, a pig 2 north: the pig or the fence.
	 */
	private void aiming(Minecraft mc) {
		PlayerEntity p = mc.player;
		for (int dz = -1; dz <= 1; dz++) world.setBlock(ox - 2, feet, oz + dz, Block.STONE.id);
		for (int i = 0; i < p.inventory.main.length; i++) p.inventory.main[i] = null;
		p.inventory.main[0] = new ItemStack(Block.COBBLESTONE, 8);
		p.inventory.main[1] = new ItemStack(Block.FENCE, 1);
		p.inventory.selectedSlot = 0;

		String who = "fences (single player, " + label() + "): ";
		HitResult h = aim(mc, ox + 2.5, oz + 0.5, 90, pitch(1.12, 1.875));
		double face = thin ? 0.625 : 1.0;
		results.check(who + "aimed at a lone post's east face: " + at(h) + " (" + label() + ": its face at x " + face + ")",
				block(h, 0, 0) && h.side == 5 && Math.abs(h.pos.x - ox - face) < 0.001);
		if (block(h, 0, 0)) {
			mc.interactionManager.interactBlock(p, world, p.inventory.getSelectedItem(), h.blockX, h.blockY, h.blockZ, h.side);
			results.check(who + "cobblestone placed against it went east of it (" + world.getBlockId(ox + 1, feet, oz) + ")",
					world.getBlockId(ox + 1, feet, oz) == Block.COBBLESTONE.id);
			world.setBlock(ox + 1, feet, oz, 0);
			h = aim(mc, ox + 2.5, oz + 0.5, 90, pitch(1.12, 1.875));
			((MinecraftPickInvoker) mc).oldfaithful$pickBlock();
			results.check(who + "pick block on it took slot " + p.inventory.selectedSlot + " (the fence's, 1)", p.inventory.selectedSlot == 1);
		}

		p.inventory.selectedSlot = 0;
		h = aim(mc, ox + 1.6, oz + 0.8, 90, pitch(1.32, 2.6));
		results.check(who + "aimed 0.3 beside the post's middle at a wall 2 west: " + at(h) + " (" + (thin ? "thin: the wall" : "beta: the fence") + ")",
				thin ? block(h, -2, 0) && h.side == 5 : block(h, 0, 0));
		((MinecraftPickInvoker) mc).oldfaithful$pickBlock();
		results.check(who + "pick block there took slot " + p.inventory.selectedSlot + " (" + (thin ? "the wall isn't held: stays 0" : "the fence's, 1") + ")",
				p.inventory.selectedSlot == (thin ? 0 : 1));
		for (int dz = -1; dz <= 1; dz++) world.setBlock(ox - 2, feet, oz + dz, 0);

		pig = new PigEntity(world);
		pig.setPositionAndAngles(ox + 0.5, feet, oz - 1.5, 0, 0);
		world.spawnEntity(pig);
		h = aim(mc, ox + 0.8, oz + 1.6, 180, pitch(1.22, 2.65));
		results.check(who + "aimed 0.3 beside the post's middle at a pig 2 north: " + at(h) + " (" + (thin ? "thin: the pig" : "beta: the fence") + ")",
				thin ? h != null && h.type == HitResultType.ENTITY && h.entity == pig : block(h, 0, 0));
		world.remove(pig);
		p.inventory.main[0] = p.inventory.main[1] = null;
	}

	/**
	 * A pig led round the east end of a row of five fences, from north of its middle to south-east of its end: given a
	 * path there every tick (beta's own path finding, which goes round fence blocks whole either way), so only how it
	 * follows the path differs. Beta's mobs cut toward the next point early, so at the end of the row they meet its block.
	 */
	private void pathing() {
		int mx = ox, mz = oz + 24;
		int[] ticks = {0}, collided = {0};
		double[] closest = {99};
		plan.add(c -> {
			for (int x = mx - 3; x <= mx + 9; x++) {
				for (int z = mz - 4; z <= mz + 4; z++) {
					world.setBlock(x, FLOOR, z, Block.STONE.id);
					for (int y = FLOOR + 1; y <= FLOOR + 4; y++) world.setBlock(x, y, z, 0);
				}
			}
			for (int dx = 0; dx < 5; dx++) world.setBlock(mx + dx, feet, mz, Block.FENCE.id);
			pig = new PigEntity(world);
			pig.setPositionAndAngles(mx + 2.5, feet, mz - 1.5, 0, 0);
			world.spawnEntity(pig);
			return true;
		});
		plan.add(c -> {
			double tx = mx + 6.5, tz = mz + 1.5;
			double d = Math.hypot(pig.x - tx, pig.z - tz);
			closest[0] = Math.min(closest[0], d);
			if (pig.horizontalCollision) collided[0]++;
			if (d > 0.6 && ++ticks[0] < 300) {
				pig.setPath(world.findPath(pig, mx + 6, feet, mz + 1, 16.0F));
				return false;
			}
			boolean reached = d <= 0.6;
			String seen = "a pig led round the end of a row of fences " + (reached ? "got there in " + ticks[0] + " ticks" : "didn't get there in 300 ticks (closest "
					+ String.format(java.util.Locale.ROOT, "%.2f", closest[0]) + ")") + ", against something on " + collided[0] + " of them";
			results.check("fences (single player, " + label() + "): " + seen + (thin ? " (thin: never)" : " (beta: meets the end's block)"),
					reached && (thin ? collided[0] == 0 : collided[0] > 0));
			world.remove(pig);
			return true;
		});
	}

	/** A fence running into something from the west (or from the south), and whether later versions join it there. */
	private static final class Joint {
		final String name;
		final int x, z;
		final boolean fromSouth, joins;

		Joint(String name, int x, int z, boolean fromSouth, boolean joins) {
			this.name = name;
			this.x = x;
			this.z = z;
			this.fromSouth = fromSouth;
			this.joins = joins;
		}
	}

	/** Two fences running into what's at x z from the west (or from the south), the nearer one checked. */
	private Joint joint(List<Joint> joints, String name, int x, int z, boolean fromSouth, boolean joins) {
		Joint j = fromSouth ? new Joint(name, x, z + 1, true, joins) : new Joint(name, x - 1, z, false, joins);
		world.setBlock(j.x, feet, j.z, Block.FENCE.id);
		world.setBlock(fromSouth ? j.x : j.x - 1, feet, fromSouth ? j.z + 1 : j.z, Block.FENCE.id);
		joints.add(j);
		return j;
	}

	/**
	 * Fence Joins: things with fences running into them, in three rows south of the other scenes. Each nearer fence's
	 * collision boxes as movement reads them, which reach the thing where it's joined (thin with both switches), stop at
	 * the post (thin, Fence Joins off) or fill the block (beta's), and whether it's drawn joined; the crosshair at the arm
	 * against the wall, where it meets the arm, the wall past a post, or the whole block; a player walking along a
	 * chest's top onto the fence beside it, who steps up onto an arm (or beta's whole block) at the chest's edge and onto
	 * a lone post further on; and pictures in local/run/fences/joins/ ("-polished", or "-beta" with Fence Joins off).
	 */
	private void joins() {
		int bz = oz + JOINS_Z, fz = bz + 5, cz = bz + 10;
		List<Joint> joints = new ArrayList<>();
		String who = "fences (single player, " + label() + ", Fence Joins " + (joined ? "on" : "off") + "): ";
		plan.add(c -> {
			for (int x = ox - 16; x <= ox + 48; x += 16) {
				for (int z = bz - 16; z <= cz + 16; z += 16) world.getBlockId(x, FLOOR, z);
			}
			for (int x = ox - 3; x <= ox + 26; x++) {
				for (int z = bz - 6; z <= cz + 3; z++) {
					world.setBlock(x, FLOOR, z, Block.STONE.id);
					for (int y = feet; y <= feet + 5; y++) world.setBlock(x, y, z, 0);
				}
			}
			// The back row: a stone wall two high, and a corner of a house of planks three high, with fences into it from
			// the west and the south.
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = feet; y <= feet + 1; y++) world.setBlock(ox + 2, y, bz + dz, Block.STONE.id);
			}
			joint(joints, "a stone wall", ox + 2, bz, false, true);
			for (int y = feet; y <= feet + 2; y++) {
				for (int x = ox + 9; x <= ox + 13; x++) world.setBlock(x, y, bz, Block.PLANKS.id);
				for (int z = bz - 4; z < bz; z++) world.setBlock(ox + 9, y, z, Block.PLANKS.id);
			}
			joint(joints, "a house's corner, from the west", ox + 9, bz, false, true);
			joint(joints, "a house's corner, from the south", ox + 9, bz, true, true);
			// The front row: glass, stairs with their back to the fence (data 1, high to the west) and with their side
			// (data 2, high to the south), leaves, a chest.
			world.setBlock(ox + 2, feet, fz, Block.GLASS.id);
			joint(joints, "glass", ox + 2, fz, false, true);
			world.setBlock(ox + 7, feet, fz, Block.COBBLESTONE_STAIRS.id, 1);
			joint(joints, "stairs' back", ox + 7, fz, false, true);
			world.setBlock(ox + 12, feet, fz, Block.COBBLESTONE_STAIRS.id, 2);
			joint(joints, "stairs' side", ox + 12, fz, false, false);
			world.setBlock(ox + 17, feet, fz, Block.LEAVES.id);
			joint(joints, "leaves", ox + 17, fz, false, false);
			world.setBlock(ox + 22, feet, fz, Block.CHEST.id);
			joint(joints, "a chest", ox + 22, fz, false, true);
			// The third row: a door shut with its flat side west (data 0: beta's door lies along the west edge then), a
			// slab, farmland (under ripe wheat, or with no water near it turns to dirt), soul sand, a pumpkin, a cobweb.
			world.setBlock(ox + 1, feet, cz, Block.DOOR.id, 0);
			world.setBlock(ox + 1, feet + 1, cz, Block.DOOR.id, 8);
			joint(joints, "a door's flat side", ox + 1, cz, false, true);
			world.setBlock(ox + 5, feet, cz, Block.SLAB.id);
			joint(joints, "a slab", ox + 5, cz, false, false);
			world.setBlock(ox + 9, feet, cz, Block.FARMLAND.id);
			world.setBlock(ox + 9, feet + 1, cz, Block.WHEAT.id, 7);
			joint(joints, "farmland", ox + 9, cz, false, false);
			world.setBlock(ox + 13, feet, cz, Block.SOUL_SAND.id);
			joint(joints, "soul sand", ox + 13, cz, false, true);
			world.setBlock(ox + 17, feet, cz, Block.PUMPKIN.id);
			joint(joints, "a pumpkin", ox + 17, cz, false, false);
			world.setBlock(ox + 21, feet, cz, Block.COBWEB.id);
			joint(joints, "a cobweb", ox + 21, cz, false, false);
			return true;
		});
		plan.add(c -> {
			for (Joint j : joints) {
				ArrayList<Box> boxes = new ArrayList<>();
				Block.FENCE.addIntersectingBoundingBox(world, j.x, feet, j.z, Box.create(j.x, feet, j.z, j.x + 1, feet + 2, j.z + 1), boxes);
				double reach = 0, top = 0;
				for (Box b : boxes) {
					reach = Math.max(reach, j.fromSouth ? j.z + 1 - b.minZ : b.maxX - j.x);
					top = Math.max(top, b.maxY - feet);
				}
				boolean arm = thin && joined && j.joins;
				double expected = !thin || arm ? 1.0 : 0.625;
				results.check(who + "the fence beside " + j.name + " reaches " + f(reach) + " toward it, " + f(top) + " high (" + (!thin ? "beta: the whole block"
						: arm ? "an arm to it" : "the post") + ", " + f(expected) + ")", Math.abs(reach - expected) < 0.001 && Math.abs(top - 1.5) < 0.001);
				boolean drawn = FenceJoins.joins(world, Block.FENCE, j.x, feet, j.z, j.fromSouth ? 0 : 1, j.fromSouth ? -1 : 0);
				results.check(who + "the fence beside " + j.name + " is drawn " + (drawn ? "joined to it" : "apart from it") + " (" + (joined && j.joins ? "joined" : "apart") + ")",
						drawn == (joined && j.joins));
			}
			return true;
		});
		// The crosshair from the north-west at the wall's fence 0.82 across and 0.375 south, where its arm's north face is.
		int wx = ox + 1;
		double eyeX = wx + 0.82 - 1.0, eyeZ = bz + 0.375 - 1.8;
		float yaw = (float) Math.toDegrees(Math.atan2(-1.0, 1.8)), aimPitch = pitch(1.12, Math.hypot(1.0, 1.8));
		plan.add(c -> {
			HitResult h = aim(c, eyeX, eyeZ, yaw, aimPitch);
			String expected = !thin ? "beta: the fence's block at z 0" : joined ? "the arm at z 0.375" : "the wall past the post";
			boolean ok = !thin ? block(h, 1, JOINS_Z) && h.side == 2 && Math.abs(h.pos.z - bz) < 0.001
					: joined ? block(h, 1, JOINS_Z) && h.side == 2 && Math.abs(h.pos.z - bz - 0.375) < 0.001
					: block(h, 2, JOINS_Z) && h.side == 4;
			results.check(who + "aimed from the north-west at where the wall's fence's arm would be: " + at(h) + " (" + expected + ")", ok);
			hold = new double[] {eyeX, feet, eyeZ, yaw, aimPitch};
			return true;
		});
		picture("wall-outline", -1, 0, 0, 0, 0, 0);
		// Walking west along a chest's top onto the fence beside it, a twentieth of a block a move, with the game's own
		// movement (Entity.move, which steps a player up 0.5 onto what it walks into while on the ground: so each move
		// falls a tick's gravity too, which the chest's top stops, as a walking player's moves do).
		plan.add(c -> {
			PlayerEntity p = c.player;
			int cx = ox + 22;
			p.noClip = false;
			p.velocityX = p.velocityY = p.velocityZ = 0;
			p.setPositionAndAnglesKeepPrevAngles(cx + 0.5, feet + 1, fz + 0.5, 90, 0);
			p.move(0, -0.08, 0);
			double stepped = Double.NaN;
			for (int i = 0; i < 30 && Double.isNaN(stepped); i++) {
				p.move(-0.05, -0.08, 0);
				if (p.boundingBox.minY > feet + 1.01) stepped = p.x - cx;
			}
			boolean early = !thin || joined;
			results.check(who + "walking west along a chest's top, stepped up onto the fence beside it at x " + f(stepped) + " (" + (early
					? (thin ? "onto its arm" : "onto beta's whole block") + " at the chest's edge, +0.30" : "onto the post, -0.10") + ")",
					early ? stepped > 0.2 && stepped < 0.31 : stepped > -0.13 && stepped < -0.07);
			p.setPositionAndAnglesKeepPrevAngles((Double) saved[1], (Double) saved[2], (Double) saved[3], 0, 0);
			return true;
		});
		// The test's Tiny view distance fogs everything past a few blocks: Normal while these are taken.
		int[] view = {0};
		plan.add(c -> {
			view[0] = c.options.viewDistance;
			if (c.options.viewDistance > 1) c.options.viewDistance = 1;
			return true;
		});
		picture("all", ox + 11.5, feet + 10, cz + 7, ox + 11.5, feet, fz + 0.5);
		closeUp("wall", ox + 2, bz);
		picture("corner", ox + 12.2, feet + 3.4, bz + 5.2, ox + 9.0, feet + 0.8, bz + 1.0);
		closeUp("glass", ox + 2, fz);
		picture("stairs", ox + 8, feet + 3.8, fz + 6.4, ox + 9, feet + 0.4, fz + 0.5);
		closeUp("leaves", ox + 17, fz);
		closeUp("chest", ox + 22, fz);
		closeUp("door", ox + 1, cz);
		picture("third-row", ox + 11.5, feet + 6, cz + 8, ox + 11.5, feet + 0.4, cz + 0.5);
		plan.add(c -> {
			c.options.viewDistance = view[0];
			return true;
		});
	}

	/** A picture of the thing at x z and the fences west of it, from the south a little west and above, beyond the crosshair's reach. */
	private void closeUp(String name, int x, int z) {
		picture(name, x - 1.6, feet + 2.7, z + 4.6, x - 0.6, feet + 0.4, z + 0.5);
	}

	/**
	 * A picture into local/run/fences/joins/, the camera's eye at ex ey ez looking at tx ty tz (or where it's held
	 * already, ex -1), once beta's light queue is empty and no render chunk waits.
	 */
	private void picture(String name, double ex, double ey, double ez, double tx, double ty, double tz) {
		File file = new File(new File(OUT, "joins"), name + "-" + (joined ? "polished" : "beta") + ".png");
		int[] wait = {0};
		ScreenCapture[] capture = {null};
		plan.add(c -> {
			c.options.hideHud = true;
			if (ex == -1) return true;
			double dx = tx - ex, dy = ty - ey, dz = tz - ez;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			hold = new double[] {ex, ey - c.player.standingEyeHeight, ez, yaw, pitch};
			return true;
		});
		plan.add(c -> {
			if (capture[0] == null) {
				long until = System.nanoTime() + 20_000_000L;
				boolean more;
				do {
					more = c.world.doLightingUpdates();
				} while (more && System.nanoTime() < until);
				if (++wait[0] < 20 || (more || StartupCheck.chunksWaiting(c)) && wait[0] < 400) return false;
				file.getParentFile().mkdirs();
				capture[0] = ScreenCapture.request(file, -1, -1);
			}
			if (!capture[0].done()) return false;
			hold = null;
			return true;
		});
	}

	private static String f(double v) {
		return String.format(java.util.Locale.ROOT, "%+.3f", v);
	}

	/**
	 * A picture of the outline on the fence at x z, looked at from the south-east, 1.2 above its top. Not along the
	 * diagonal: a ray exactly into a vertical edge of the post misses both faces by rounding (as beta's own block test
	 * would), and the post's middle seen from 45 degrees is such an edge.
	 */
	private void outline(String name, int x, int z) {
		File file = new File(OUT, name + "-" + label() + ".png");
		int[] wait = {0};
		ScreenCapture[] capture = {null};
		plan.add(c -> {
			c.options.hideHud = true;
			double ex = x + 0.5 + 1.9, ez = z + 0.5 + 1.2, eye = feet + 2.2;
			double dx = x + 0.5 - ex, dz = z + 0.5 - ez, dy = feet + 0.6 - eye;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			hold = new double[] {ex, eye - c.player.standingEyeHeight, ez, yaw, pitch};
			return true;
		});
		plan.add(c -> {
			if (++wait[0] < 40) return false;
			HitResult h = c.crosshairTarget;
			boolean onIt = h != null && h.type == HitResultType.BLOCK && h.blockX == x && h.blockY == feet && h.blockZ == z;
			if (capture[0] == null) {
				results.check("fences (single player, " + label() + "): the " + name + "'s picture aims at it (" + at(h) + ")", onIt);
				capture[0] = ScreenCapture.request(file, -1, -1);
			}
			if (!capture[0].done()) return false;
			hold = null;
			return true;
		});
	}
}
