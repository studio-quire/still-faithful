package org.studioquire.oldfaithful.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SliderStepsTest {
	@Test
	void everyStepIsLabelledAsItself() {
		for (int steps : new int[] {100, 200}) {
			for (int k = 0; k <= steps; k++) {
				float v = SliderSteps.value(k, steps);
				assertEquals(k, (int) (v * (float) steps), "label of step " + k + "/" + steps);
				assertTrue(v >= 0.0F && v <= 1.0F);
				assertEquals(v, SliderSteps.snap(v, steps), "a step's value snaps to itself");
			}
		}
		assertEquals(0.0F, SliderSteps.value(0, 200));
		assertEquals(1.0F, SliderSteps.value(200, 200));
	}

	/** The labels the mouse can reach at a GUI scale: every pixel along the track, as the mixin reads it. */
	private static Set<Integer> reachable(int steps, int scale) {
		int trackX = 103; // any whole-unit position: the slider sits on whole units
		Set<Integer> labels = new HashSet<>();
		for (int px = (trackX - 2) * scale; px <= (trackX + SliderSteps.TRACK + 2) * scale; px++) {
			float x = SliderSteps.pointerX(px, scale, px / scale);
			labels.add(SliderSteps.step(SliderSteps.snap(SliderSteps.position(x, trackX), steps), steps));
		}
		return labels;
	}

	@Test
	void everyLabelReachableFromNormalScaleUp() {
		for (int scale = 1; scale <= 6; scale++) {
			assertEquals(101, reachable(100, scale).size(), "volume at scale " + scale);
		}
		// Small: one position per GUI unit, 143 along the track, each its own label.
		assertEquals(143, reachable(200, 1).size());
		for (int scale = 2; scale <= 6; scale++) {
			assertEquals(201, reachable(200, scale).size(), "sensitivity at scale " + scale);
		}
	}

	@Test
	void pointerKeepsTheScreensPositionWhenTheyDisagree() {
		assertEquals(50.5F, SliderSteps.pointerX(101, 2, 50));
		assertEquals(51.0F, SliderSteps.pointerX(102, 2, 50)); // the screen's rounding, near the right edge
		assertEquals(80.0F, SliderSteps.pointerX(101, 2, 80));
	}

	@Test
	void knobSitsOnItsPixel() {
		for (int k = 0; k <= 200; k++) {
			float v = SliderSteps.value(k, 200);
			assertEquals(0.0F, SliderSteps.knobOffset(v, 1), "beta's place at scale 1");
			for (int scale = 2; scale <= 4; scale++) {
				float off = SliderSteps.knobOffset(v, scale);
				assertTrue(off >= 0.0F && off < 1.0F);
				float px = ((int) (v * SliderSteps.TRACK) + off) * scale;
				assertEquals(Math.round(px), px, 1e-3F, "a whole pixel");
				assertTrue(px <= v * SliderSteps.TRACK * scale + 1e-3F && px > v * SliderSteps.TRACK * scale - 1.0F);
			}
		}
	}
}
