package org.lwjgl.opengl;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.studioquire.oldfaithful.lwjgl.Into;

/** LWJGL 2's ARBShaderObjects methods without a same-shaped LWJGL 3 method, added at build time. */
@Into("org.lwjgl.opengl.ARBShaderObjects")
final class LegacyARBShaderObjects {
	private LegacyARBShaderObjects() {
	}

	public static void glShaderSourceARB(int shader, ByteBuffer string) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ARBShaderObjects.glShaderSourceARB(shader, stack.pointers(MemoryUtil.memAddress(string)), stack.ints(string.remaining()));
		}
	}
}
