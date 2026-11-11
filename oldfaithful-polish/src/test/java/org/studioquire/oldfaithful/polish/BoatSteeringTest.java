package org.studioquire.oldfaithful.polish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import net.minecraft.util.math.MathHelper;
import org.junit.jupiter.api.Test;

/**
 * Boat Steering's reading of a riding client's keys on a server: the motion a client sends, worked out as beta's client
 * works it out (`LivingEntity.travel`, `Entity.moveNonSolid`, in floats, with the sine table), reads back as the keys
 * behind it at every look; and a push on top of it (another entity shoving the rider) is almost never read as keys.
 */
class BoatSteeringTest {
	private static final float[] FRICTION = {0.8F, 0.91F, 0.5F};

	@Test
	void keysReadBackAtEveryLook() {
		Random random = new Random(83);
		int cases = 0;
		for (int forward = -1; forward <= 1; forward++) {
			for (int sideways = -1; sideways <= 1; sideways++) {
				for (boolean sneak : new boolean[] {false, true}) {
					for (float friction : FRICTION) {
						for (int i = 0; i < 4000; i++) {
							float yaw = i < 3600 ? i * 0.1F - 180.0F : (random.nextFloat() - 0.5F) * 20000.0F;
							double[] v = motion(forward, sideways, sneak, friction, yaw);
							assertEquals(keys(forward, sideways), BoatSteering.decode(v[0], v[1], yaw),
									"forward " + forward + ", sideways " + sideways + ", sneak " + sneak + ", friction " + friction + ", yaw " + yaw);
							cases++;
						}
					}
				}
			}
		}
		assertTrue(cases > 200_000);
	}

	@Test
	void pushesAreNotReadAsKeys() {
		Random random = new Random(12);
		int pushes = 200_000, misread = 0;
		for (int i = 0; i < pushes; i++) {
			int forward = random.nextInt(3) - 1, sideways = random.nextInt(3) - 1;
			float yaw = (random.nextFloat() - 0.5F) * 720.0F;
			double[] v = motion(forward, sideways, false, 0.8F, yaw);
			double angle = random.nextDouble() * 2 * Math.PI, size = 0.002 + random.nextDouble() * 0.05;
			int read = BoatSteering.decode(v[0] + Math.cos(angle) * size, v[1] + Math.sin(angle) * size, yaw);
			if (read != BoatSteering.UNCLEAR && read != keys(forward, sideways)) misread++;
		}
		assertTrue(misread < pushes / 1000, misread + " of " + pushes + " pushes read as other keys");
	}

	private static int keys(int forward, int sideways) {
		return (forward > 0 ? BoatSteering.FORWARD : forward < 0 ? BoatSteering.BACK : 0)
				| (sideways > 0 ? BoatSteering.LEFT : sideways < 0 ? BoatSteering.RIGHT : 0);
	}

	/** A riding client's motion this tick, as beta works it out: the keys, sneaking's 0.3, the 0.98 decay, then travel. */
	private static double[] motion(int forward, int sideways, boolean sneak, float friction, float yaw) {
		float f = sideways, g = forward;
		if (sneak) {
			f = (float) (f * 0.3);
			g = (float) (g * 0.3);
		}
		f *= 0.98F;
		g *= 0.98F;
		double vx = 0, vz = 0;
		float length = MathHelper.sqrt(f * f + g * g);
		if (!(length < 0.01F)) {
			if (length < 1.0F) length = 1.0F;
			length = 0.02F / length;
			f *= length;
			g *= length;
			float s = MathHelper.sin(yaw * (float) Math.PI / 180.0F), c = MathHelper.cos(yaw * (float) Math.PI / 180.0F);
			vx += f * c - g * s;
			vz += g * c + f * s;
		}
		return new double[] {vx * friction, vz * friction};
	}
}
