package org.studioquire.oldfaithful.polish.selftest;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.selftest.FenceScene;
import org.studioquire.oldfaithful.selftest.FenceWalk;
import org.studioquire.oldfaithful.selftest.ScreenCapture;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.MinecraftPickInvoker;

/**
 * #30 in single player, where the world runs on this client, so the shape is the switch's: the walks of
 * {@link FenceWalk} (multiplayer's mixes are `:harness:fencesMultiplayerTest`'s); what the crosshair meets past a post
 * (the post, the gap beside it, a block and a pig behind the gap), placing against a post and picking it, as the game
 * does them from the crosshair; a pig led along a path round the end of a row of fences; and pictures of the outline on
 * a lone post, a line and a corner in local/run/fences/ ("-thin", or "-beta" with the switch off).
 */
public final class FenceCheck implements WorldCheck {
	private static final String KEY = "blocks.fenceShape";
	private static final int FLOOR = 118;
	private static final File OUT = new File("../fences");

	private interface Step {
		boolean tick(Minecraft mc);
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private SelfTest results;
	private World world;
	private boolean thin;
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
