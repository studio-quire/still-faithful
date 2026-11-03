package org.lwjgl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/** LWJGL 2's MemoryUtil: buffer addresses, through LWJGL 3's. */
public final class MemoryUtil {
	private MemoryUtil() {
	}

	public static long getAddress0(Buffer buffer) {
		return org.lwjgl.system.MemoryUtil.memAddress0(buffer);
	}

	public static long getAddress0Safe(Buffer buffer) {
		return buffer == null ? 0L : getAddress0(buffer);
	}

	public static long getAddress(ByteBuffer b) {
		return org.lwjgl.system.MemoryUtil.memAddress(b);
	}

	public static long getAddress(ByteBuffer b, int position) {
		return org.lwjgl.system.MemoryUtil.memAddress(b, position);
	}

	public static long getAddress(ShortBuffer b) {
		return org.lwjgl.system.MemoryUtil.memAddress(b);
	}

	public static long getAddress(CharBuffer b) {
		return org.lwjgl.system.MemoryUtil.memAddress(b);
	}

	public static long getAddress(IntBuffer b) {
		return org.lwjgl.system.MemoryUtil.memAddress(b);
	}

	public static long getAddress(FloatBuffer b) {
		return org.lwjgl.system.MemoryUtil.memAddress(b);
	}

	public static long getAddress(LongBuffer b) {
		return org.lwjgl.system.MemoryUtil.memAddress(b);
	}

	public static long getAddress(DoubleBuffer b) {
		return org.lwjgl.system.MemoryUtil.memAddress(b);
	}

	public static long getAddressSafe(ByteBuffer b) {
		return b == null ? 0L : getAddress(b);
	}

	public static long getAddressSafe(IntBuffer b) {
		return b == null ? 0L : getAddress(b);
	}

	public static long getAddressSafe(FloatBuffer b) {
		return b == null ? 0L : getAddress(b);
	}
}
