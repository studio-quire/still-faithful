package org.studioquire.oldfaithful.fixes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.util.math.Box;

/**
 * #84: beta draws an entity only while its box is in view, testing the box where the entity is this tick. But it's drawn
 * where it's interpolated to between ticks, up to a tick's move behind, and most models reach past their boxes. So at the
 * screen's edges a mob whose box had just left the view vanished with its head, arms or tentacles still on screen.
 * The box tested here is the drawn one, grown on every side by how far the model can reach past it.
 * <p>
 * How far, from `:harness:cullingMeasure` (docs/bugs.md #84): nothing of beta's reaches more than 0.8 of a block past its
 * box (a cow's legs mid-stride; a sheep 0.78, a player's sword or a 16-letter name tag 0.75) but a squid's tentacles
 * (1.34) and a ghast, whose body stands 2.3 above its box and swells as it charges (2.94). A dying mob falls onto its side,
 * which lays its height out sideways; a sleeping player's box shrinks to a fifth of a block under a body 1.8 long.
 * <p>
 * Called for every entity in every frame, on the render thread: no allocation, one box reused (the culler reads it at once).
 */
public final class EntityCulling {
	private static final Box BOX = Box.create(0, 0, 0, 0, 0, 0);

	private EntityCulling() {
	}

	public static Box drawn(Box box, Entity e, float tickDelta) {
		double back = 1.0 - tickDelta;
		double dx = (e.lastTickX - e.x) * back, dy = (e.lastTickY - e.y) * back, dz = (e.lastTickZ - e.z) * back;
		double pad = reach(e);
		return BOX.set(box.minX + dx - pad, box.minY + dy - pad, box.minZ + dz - pad, box.maxX + dx + pad, box.maxY + dy + pad, box.maxZ + dz + pad);
	}

	static double reach(Entity e) {
		double pad = e instanceof SquidEntity ? 1.5 : e instanceof GhastEntity ? 3.0 : 1.0;
		if (e instanceof LivingEntity) {
			LivingEntity l = (LivingEntity) e;
			if (l.deathTime > 0) pad += e.height;
			if (l.isSleeping()) pad = Math.max(pad, 2.0);
		}
		return pad;
	}
}
