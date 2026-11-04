package org.studioquire.oldfaithful.fixes;

import org.lwjgl.opengl.GL11;

/**
 * #70 (docs/bugs.md): where a render chunk (16 blocks a side) is drawn.
 *
 * <p>Beta compiles each chunk's vertices relative to the chunk's corner, exactly, and positions the chunk in two
 * steps: the chunk renderer translates by (the chunk's 1024-block region - the camera, the camera as a float), and the
 * chunk's display list then translates by the chunk's place in that region (0 to 1023 blocks) and scales by 1.000001
 * about the chunk's middle (an overlap against cracks: 8 millionths of a block at each side). The two translations
 * nearly cancel, and the graphics driver adds them in single precision, so each chunk ends up off by a rounding of up
 * to about 0.0001 blocks, independently of its neighbour: several times the overlap. Where two chunks meet, the faces
 * either side then miss each other by a hair, and pixels fall through to the sky.
 *
 * <p>With the fix, the display list keeps only the scale about the middle, and each chunk is translated once, by its
 * corner minus the camera, worked out in double precision and rounded once: the error is a rounding of the distance
 * to the chunk, a few millionths of a block nearby, inside the overlap.
 */
public final class ChunkTransform {
	private ChunkTransform() {
	}

	/** The chunk's translation: its corner minus the camera, rounded once. */
	public static void chunk(int x, int y, int z, double camX, double camY, double camZ) {
		GL11.glTranslatef((float) (x - camX), (float) (y - camY), (float) (z - camZ));
	}
}
