package org.studioquire.oldfaithful.polish;

import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Squid Surfacing ({@code mobs.squidSurface}; docs/still-faithful.md > Squid at the surface). A squid in water sets its
 * speed every tick to its swim direction times its pulse; one out of water (Old Faithful's #77 test: its middle over the
 * water's real surface) falls, its sideways speed zeroed, its tentacles in their out-of-water pose. A squid swimming up
 * keeps that direction until its 1-in-50 a tick re-pick, about 2.5 s on average, so at the surface it bounced: a tick
 * or two out, a drop of 0.04 to 0.11, back in and up again, two to ten ticks a cycle, with a one-tick tentacle twitch
 * each time. Every later version does the same at the surface (26.3's Squid.aiStep is beta's, its water test looser).
 * <p>
 * Here, where the world runs, a squid's rise is limited, after its swim speed is set, to {@link #EASE} of the way to
 * {@link #DEPTH} below the surface (its middle's): it slows as it nears, never comes out, and cruises there with its back
 * at the waterline, its pulse and tilt beta's (rising less, it lies flatter). Above that height and still in water, it
 * sinks back the same way. Only within a block or so of the surface: deeper, a full swim (0.1 a tick) is under the
 * limit. The out-of-water branch is beta's for a squid that falls in or is knocked out.
 */
public final class Squids {
	/** How far below the surface the middle settles: the box's top 0.175 over it, a flat squid's drawn back about 0.1. */
	public static final double DEPTH = 0.3;
	/** The share of the remaining way a squid may rise in a tick. */
	public static final double EASE = 0.2;

	private Squids() {
	}

	/** The squid's vertical speed for its next move, given the one its swim set. */
	public static double ease(World world, Box box, double velocityY) {
		double middle = (box.minY + box.maxY) / 2.0;
		double surface = surface(world, box, middle);
		if (Double.isNaN(surface)) return velocityY;
		return Math.min(velocityY, (surface - DEPTH - middle) * EASE);
	}

	/**
	 * The highest water surface over the middle among the columns under the squid that have water at its middle's
	 * height (the columns #77's test asks about), or NaN if there's none, or water two blocks up, too far to matter.
	 */
	static double surface(World world, Box box, double middle) {
		int y = MathHelper.floor(middle);
		double highest = Double.NaN;
		for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
			for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
				if (world.getMaterial(x, y, z) != Material.WATER) continue;
				int top = world.getMaterial(x, y + 1, z) == Material.WATER ? y + 1 : y;
				if (top > y && world.getMaterial(x, y + 2, z) == Material.WATER) return Double.NaN;
				double surface = top + 1 - LiquidBlock.getFluidHeightFromMeta(world.getBlockMeta(x, top, z));
				if (!(surface <= highest)) highest = surface;
			}
		}
		return highest;
	}
}
