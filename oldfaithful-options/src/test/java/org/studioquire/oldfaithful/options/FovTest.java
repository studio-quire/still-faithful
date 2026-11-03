package org.studioquire.oldfaithful.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FovTest {
	@Test
	void sliderRoundTripsEveryDegree() {
		for (int d = Fov.MIN; d <= Fov.MAX; d++) assertEquals(d, Fov.fromSlider(Fov.toSlider(d)));
		assertEquals(Fov.MIN, Fov.fromSlider(0.0F));
		assertEquals(Fov.MAX, Fov.fromSlider(1.0F));
	}

	@Test
	void everyDegreeReachableEvenAtSmallScale() {
		// One mouse position per GUI unit at Small: 143 along the 142-unit track, for 81 degrees.
		Set<Integer> seen = new HashSet<>();
		for (int px = 0; px <= 142; px++) seen.add(Fov.fromSlider(Fov.pointerX(px, 1, px) / 142.0F));
		assertEquals(Fov.MAX - Fov.MIN + 1, seen.size());
	}

	@Test
	void labelsAndParsing() {
		assertEquals("FOV: Normal", Fov.label(70));
		assertEquals("FOV: 95", Fov.label(95));
		assertEquals(Integer.valueOf(90), Fov.parse("90"));
		assertEquals(Integer.valueOf(90), Fov.parse(" 90.4"));
		assertEquals(Integer.valueOf(110), Fov.parse("400"));
		assertEquals(Integer.valueOf(30), Fov.parse("-5"));
		assertNull(Fov.parse("wide"));
		assertNull(Fov.parse("NaN"));
	}

	@Test
	void scalesBetasViewInProportion() {
		int before = Fov.degrees;
		try {
			Fov.degrees = 70;
			assertEquals(60.0F, Fov.apply(60.0F));
			Fov.degrees = 105;
			assertEquals(105.0F, Fov.apply(70.0F), 1e-4F);
			assertEquals(90.0F, Fov.apply(60.0F), 1e-4F); // under water, as later versions scale it
		} finally {
			Fov.degrees = before;
		}
	}
}
