package org.studioquire.oldfaithful.polish;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * #30: fences shaped as later versions shape them. In beta a fence is a whole block to aim at (its outline and what the
 * crosshair hits) and a whole 1 x 1.5 x 1 box to walk into, whatever it joins. Here it's what's drawn: a post a quarter
 * of a block wide in the middle (beta's own post, 6/16 to 10/16) with an arm as wide reaching to each fence beside it,
 * which is what a fence joins in beta (BlockRenderManager.renderFence), or with Fence Joins to what later versions join
 * ({@link FenceJoins}, which also draws them). As in later versions, what you aim at stands 1
 * high (1.13's outline: the post and arms, not one box around them) and what you bump into 1.5, so a fence still can't
 * be jumped. Covered as two boxes, as 1.8 to 1.12 did: one along z where it joins north or south, one along x where it
 * joins east or west or doesn't join north or south.
 * <p>
 * Collision in multiplayer: both sides work it out (the client moves its player, the server replays the move and
 * sends the player back where its own result is more than a quarter block off, or the place is inside something). A
 * client walking up to a post on a server whose fences are whole would be sent back again and again, so a client is thin
 * only on a server whose login packet says it has this change ({@link ServerMarker}). The other way round is safe: a
 * client with whole fences never goes where a thin server's fences are, the server's replay of its moves comes out the
 * same (fewer boxes to stop at, and the client's moves already stop short of all of them), and beta's floating check
 * counts blocks near the feet, not their shapes. So a server applies the thin shape to everyone, and a vanilla player
 * standing on the edge of a post's top just looks as if they float to others.
 * <p>
 * Aiming is the client's own: a server trusts which block and face a player names (it checks only the distance), so
 * the thin outline and crosshair apply everywhere. On a server without this change, a mob hit through the gap beside
 * a post may not be hurt: the server checks the line between the two pairs of eyes with whole fences.
 */
public final class Fences {
	/** In multiplayer, whether the server has this change. */
	public static volatile boolean serverAgrees;

	private Fences() {
	}

	/** Whether collision in this world is thin: wherever the world runs, and on a client only where the server agrees. */
	public static boolean thinCollision(World world) {
		return !world.isRemote || serverAgrees;
	}

	private static List<double[]> footprint(BlockView view, int x, int y, int z, Block fence) {
		return FenceShape.footprint(FenceJoins.joins(view, fence, x, y, z, -1, 0), FenceJoins.joins(view, fence, x, y, z, 1, 0),
				FenceJoins.joins(view, fence, x, y, z, 0, -1), FenceJoins.joins(view, fence, x, y, z, 0, 1));
	}

	/** The fence's collision boxes that meet {@code box}, as Block.addIntersectingBoundingBox adds its one. */
	public static void addCollisions(World world, int x, int y, int z, Block fence, Box box, List<Box> boxes) {
		for (double[] f : footprint(world, x, y, z, fence)) {
			Box b = Box.createCached(x + f[0], y, z + f[1], x + f[2], y + FenceShape.COLLISION_HEIGHT, z + f[3]);
			if (box.intersects(b)) boxes.add(b);
		}
	}

	/** Where a ray from start to end first meets the post or an arm, 1 high, and on which face; null if it passes. */
	public static HitResult raycast(World world, int x, int y, int z, Block fence, Vec3d start, Vec3d end) {
		HitResult nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (double[] f : footprint(world, x, y, z, fence)) {
			HitResult hit = Box.createCached(x + f[0], y, z + f[1], x + f[2], y + FenceShape.AIM_HEIGHT, z + f[3]).raycast(start, end);
			if (hit == null) continue;
			double sq = start.squaredDistanceTo(hit.pos);
			if (sq < nearestSq) {
				nearestSq = sq;
				nearest = hit;
			}
		}
		return nearest == null ? null : new HitResult(x, y, z, nearest.side, nearest.pos);
	}

	/** The outline's edges in world coordinates, x1 y1 z1 x2 y2 z2 each, grown by {@code grow} as beta grows its box. */
	public static double[] outline(BlockView view, int x, int y, int z, Block fence, double grow) {
		double[] local = FenceShape.outline(FenceJoins.joins(view, fence, x, y, z, -1, 0), FenceJoins.joins(view, fence, x, y, z, 1, 0),
				FenceJoins.joins(view, fence, x, y, z, 0, -1), FenceJoins.joins(view, fence, x, y, z, 0, 1));
		for (int i = 0; i < local.length; i += 3) {
			local[i] = x + FenceShape.grown(local[i], grow);
			local[i + 1] = y + FenceShape.grown(local[i + 1], grow);
			local[i + 2] = z + FenceShape.grown(local[i + 2], grow);
		}
		return local;
	}
}
