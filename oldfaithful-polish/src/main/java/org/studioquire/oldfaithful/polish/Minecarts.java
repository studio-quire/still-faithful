package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.block.RailBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * #31, carts meeting carts. In beta's {@code MinecartEntity.onCollision} the exchange between two carts (bumping,
 * pushing, a furnace cart driving a train, the booster) is skipped when a gate fires that was meant to keep carts on
 * parallel tracks apart: {@code (dx * other.velocityZ + dz * other.prevX)^2 > 5}, a position where a velocity belongs.
 * In line on east-west track dz is 0 and it never fires; side by side on east-west track it fires (dz is about 1, times
 * an x in the hundreds), as meant. On north-south and curved track it fires for carts in line too, so there carts were
 * walls to each other; only within a few blocks of x = 0 did they meet.
 * <p>
 * Here the exchange happens when the other cart lies along the line the cart's rail runs (within {@link #LINED_UP} of
 * it, about 37 degrees either way, as later versions judge it from the cart's facing). That is beta's east-west
 * behaviour on every axis: carts on one track meet, carts side by side on parallel tracks don't. The rail, not the
 * facing, because a cart that has never moved faces east whatever track it's on; on a curve the line is its chord,
 * which beta's carts run along. Off the rails the cart's facing is used, as later versions do. Which cart's rail: the
 * one whose onCollision runs; every touching pair runs it both ways each tick, so where two carts' rails differ (at a
 * curve's ends) one way still counts.
 * <p>
 * Boosters (overlapping carts gaining a fifth of their joint speed in each exchange) come with it, as on east-west
 * track. Beta's arithmetic is kept there too: two carts that stay overlapped and rolling together gain about 1.38 a
 * tick without limit, and after some 1,100 ticks the speed they store overflows; beta's own guards (Entity.move zeroes
 * a NaN speed, World.updateEntity puts back a NaN or infinite position) end it with the pair stopping. Seen on the
 * real server (ServerCartsTest): no NaN reaches a position, a rider, a save or a client.
 */
public final class Minecarts {
	/** The cosine of the widest angle off the rail's line at which another cart still counts as in line (later versions'). */
	public static final double LINED_UP = 0.8;

	private Minecarts() {
	}

	/** Whether the cart and the other cart exchange speed: the other lies along the cart's rail (or facing, off the rails). */
	public static boolean linedUp(Entity cart, Entity other, int[][][] shapes) {
		double dx = other.x - cart.x, dz = other.z - cart.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d == 0) return true;
		double hx, hz;
		int[][] shape = railShape(cart.world, cart.x, cart.y, cart.z, shapes);
		if (shape != null) {
			hx = shape[1][0] - shape[0][0];
			hz = shape[1][2] - shape[0][2];
		} else {
			hx = MathHelper.cos(cart.yaw * (float) Math.PI / 180.0F);
			hz = MathHelper.sin(cart.yaw * (float) Math.PI / 180.0F);
		}
		double h = Math.sqrt(hx * hx + hz * hz);
		return Math.abs((dx * hx + dz * hz) / (d * h)) >= LINED_UP;
	}

	/** The rail under a cart, found as the cart's tick finds it; null off the rails. */
	static int[][] railShape(World world, double x, double y, double z, int[][][] shapes) {
		int bx = MathHelper.floor(x), by = MathHelper.floor(y), bz = MathHelper.floor(z);
		if (RailBlock.isRail(world, bx, by - 1, bz)) by--;
		int id = world.getBlockId(bx, by, bz);
		if (!RailBlock.isRail(id)) return null;
		int meta = world.getBlockMeta(bx, by, bz);
		if (((RailBlock) Block.BLOCKS[id]).isAlwaysStraight()) meta &= 7;
		return meta < shapes.length ? shapes[meta] : null;
	}
}
