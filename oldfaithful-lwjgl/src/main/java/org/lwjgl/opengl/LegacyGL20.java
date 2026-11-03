package org.lwjgl.opengl;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.studioquire.oldfaithful.lwjgl.Into;

/** LWJGL 2's GL20 methods without a same-shaped LWJGL 3 method, added to GL20 at build time. */
@Into("org.lwjgl.opengl.GL20")
final class LegacyGL20 {
	private LegacyGL20() {
	}

	/** The shader's source as bytes from the buffer's position to its limit (SmoothBeta passes it this way). */
	public static void glShaderSource(int shader, ByteBuffer string) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			GL20.glShaderSource(shader, stack.pointers(MemoryUtil.memAddress(string)), stack.ints(string.remaining()));
		}
	}
}
