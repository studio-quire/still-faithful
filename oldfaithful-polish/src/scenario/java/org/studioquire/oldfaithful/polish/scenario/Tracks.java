package org.studioquire.oldfaithful.polish.scenario;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.world.World;

/**
 * Test-only: straight tracks along one axis from an origin, on lines w blocks across it, and carts on them. Tracks lie
 * on a stone floor with a stone block at each end.
 */
public final class Tracks {
	final World world;
	final boolean alongZ;
	final int ox, y, oz;

	public Tracks(World world, int ox, int y, int oz, boolean alongZ) {
		this.world = world;
		this.ox = ox;
		this.y = y;
		this.oz = oz;
		this.alongZ = alongZ;
	}

	public String axis() {
		return alongZ ? "north-south" : "east-west";
	}

	/** Lays the tracks, each from 0 to length - 1 along the axis. */
	public void lay(int length, int... lines) {
		for (int w : lines) {
			for (int u = -1; u <= length; u++) {
				set(u, -1, w, Block.STONE.id, 0);
				set(u, 0, w, u < 0 || u == length ? Block.STONE.id : Block.RAIL.id, 0);
				set(u, 1, w, 0, 0);
				set(u, 2, w, 0, 0);
			}
		}
		// Placing a rail turns its neighbours (the next track's too) to join it: once all are down, set them straight,
		// without neighbour updates.
		for (int w : lines) {
			for (int u = 0; u < length; u++) world.setBlockMetaWithoutNotifyingNeighbors(x(u, w), y, z(u, w), alongZ ? 0 : 1);
		}
	}

	/**
	 * A track along the axis on line w from 0 to turn - 1, a curve at turn, and then {@code across} blocks running
	 * across the axis (towards greater w); the end blocks stone.
	 */
	public void curve(int w, int turn, int across) {
		for (int u = -1; u <= turn; u++) {
			set(u, -1, w, Block.STONE.id, 0);
			set(u, 0, w, u < 0 ? Block.STONE.id : Block.RAIL.id, 0);
			set(u, 1, w, 0, 0);
			set(u, 2, w, 0, 0);
		}
		for (int a = 1; a <= across + 1; a++) {
			set(turn, -1, w + a, Block.STONE.id, 0);
			set(turn, 0, w + a, a > across ? Block.STONE.id : Block.RAIL.id, 0);
			set(turn, 1, w + a, 0, 0);
			set(turn, 2, w + a, 0, 0);
		}
		for (int u = 0; u < turn; u++) world.setBlockMetaWithoutNotifyingNeighbors(x(u, w), y, z(u, w), alongZ ? 0 : 1);
		// The curve joins the side before it along the axis to the side across: -x and +z (7), or -z and +x (9).
		world.setBlockMetaWithoutNotifyingNeighbors(x(turn, w), y, z(turn, w), alongZ ? 9 : 7);
		for (int a = 1; a <= across; a++) world.setBlockMetaWithoutNotifyingNeighbors(x(turn, w + a), y, z(turn, w + a), alongZ ? 1 : 0);
	}

	/** The block-centre distance a cart has come, across the axis. */
	public double across(Entity e) {
		return alongZ ? e.x - ox : e.z - oz;
	}

	/** A cart of the type on line w at u blocks along it, moving at v along the axis. */
	public MinecartEntity cart(int w, double u, int type, double v) {
		double cx = alongZ ? ox + w + 0.5 : ox + u, cz = alongZ ? oz + u : oz + w + 0.5;
		MinecartEntity cart = new MinecartEntity(world, cx, y + 0.5, cz, type);
		cart.velocityX = alongZ ? 0 : v;
		cart.velocityZ = alongZ ? v : 0;
		world.spawnEntity(cart);
		return cart;
	}

	/** How far along the axis from the origin. */
	public double along(Entity e) {
		return alongZ ? e.z - oz : e.x - ox;
	}

	public static double speed(Entity e) {
		return Math.sqrt(e.velocityX * e.velocityX + e.velocityZ * e.velocityZ);
	}

	private int x(int u, int w) {
		return alongZ ? ox + w : ox + u;
	}

	private int z(int u, int w) {
		return alongZ ? oz + u : oz + w;
	}

	private void set(int u, int dy, int w, int id, int meta) {
		world.setBlockWithoutNotifyingNeighbors(x(u, w), y + dy, z(u, w), id, meta);
	}

	/** An x for tracks running north-south across {@code width} blocks, kept more than 40 from 0 (beta's slip spares |x| under about 2). */
	public static int awayFromZero(int x, int width) {
		if (x + width < -40 || x > 40) return x;
		return x >= 0 ? 41 : -41 - width;
	}
}
