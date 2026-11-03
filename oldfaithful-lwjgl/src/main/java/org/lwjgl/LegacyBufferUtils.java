package org.lwjgl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.studioquire.oldfaithful.lwjgl.Into;

/** LWJGL 2's BufferUtils methods that LWJGL 3's lacks (its create and zero methods are the same), added at build time. */
@Into("org.lwjgl.BufferUtils")
final class LegacyBufferUtils {
	private LegacyBufferUtils() {
	}

	public static int getElementSizeExponent(Buffer buf) {
		if (buf instanceof ByteBuffer) return 0;
		if (buf instanceof ShortBuffer || buf instanceof CharBuffer) return 1;
		if (buf instanceof FloatBuffer || buf instanceof IntBuffer) return 2;
		if (buf instanceof LongBuffer || buf instanceof DoubleBuffer) return 3;
		throw new IllegalStateException("Unsupported buffer type: " + buf);
	}

	public static int getOffset(Buffer buffer) {
		return buffer.position() << getElementSizeExponent(buffer);
	}
}
