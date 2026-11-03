package org.studioquire.oldfaithful.lwjgl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.lwjgl.LWJGLUtil;

/**
 * LWJGL 2's argument checks (its BufferChecks and GLChecks' image sizes), with its messages. LWJGL 3 doesn't check
 * most buffer sizes, so a buffer too small for glReadPixels or glTexImage2D would be overrun in native memory; LWJGL
 * 2 threw instead. The bake (buildSrc's {@code Checks}) reads each LWJGL 2 binding method's checks from LWJGL 2's own
 * bytecode and puts the same calls, pointed here, at the top of the LWJGL 3 method or forwarder that stands in for it.
 * Like LWJGL 2's, they're off with {@code -Dorg.lwjgl.util.NoChecks=true}.
 * <p>
 * Null buffers pass: LWJGL 2 only checked those it was given (its nullable arguments were guarded), and LWJGL 3
 * rejects a null where one isn't allowed.
 */
public final class LegacyChecks {
	private LegacyChecks() {
	}

	public static void checkBuffer(Buffer buf, int size) {
		if (!LWJGLUtil.CHECKS || buf == null) return;
		if (buf.remaining() < size) {
			throw new IllegalArgumentException("Number of remaining buffer elements is " + buf.remaining() + ", must be at least " + size
					+ ". Because at most " + size + " elements can be returned, a buffer with at least " + size
					+ " elements is required, regardless of actual returned element count");
		}
		checkDirect(buf);
	}

	public static void checkDirect(Buffer buf) {
		if (LWJGLUtil.CHECKS && buf != null && !buf.isDirect()) throw new IllegalArgumentException(kind(buf) + " is not direct");
	}

	public static void checkNullTerminated(Buffer buf) {
		if (!LWJGLUtil.CHECKS || buf == null) return;
		int last = buf.limit() - 1;
		long value = buf instanceof ByteBuffer ? ((ByteBuffer) buf).get(last)
				: buf instanceof IntBuffer ? ((IntBuffer) buf).get(last)
				: buf instanceof LongBuffer ? ((LongBuffer) buf).get(last) : 0;
		if (value != 0) throw new IllegalArgumentException("Missing null termination");
	}

	public static void checkNullTerminated(ByteBuffer buf, int count) {
		if (!LWJGLUtil.CHECKS || buf == null) return;
		int found = 0;
		for (int i = buf.position(); i < buf.limit(); i++) if (buf.get(i) == 0) found++;
		if (found < count) throw new IllegalArgumentException("Missing null termination");
	}

	public static void checkArray(Object[] array) {
		if (LWJGLUtil.CHECKS && (array == null || array.length == 0)) throw new IllegalArgumentException("Invalid array");
	}

	public static void checkArray(Object[] array, int size) {
		if (LWJGLUtil.CHECKS && array.length < size) {
			throw new IllegalArgumentException("Number of array elements is " + array.length + ", must be at least " + size);
		}
	}

	public static void checkNotNull(Object o) {
		if (LWJGLUtil.CHECKS && o == null) throw new IllegalArgumentException("Null argument");
	}

	// GLChecks: the elements an image of this format and type takes in this buffer. As in LWJGL 2.9.4, pixel-store
	// state (row length, alignment, skips) isn't counted, and a format or type it doesn't know gives 0: no check.

	public static int calculateImageStorage(Buffer buf, int format, int type, int width, int height, int depth) {
		return elements(buf, bytesPerPixel(format, type) * width * height * depth);
	}

	public static int calculateTexImage1DStorage(Buffer buf, int format, int type, int width) {
		return elements(buf, bytesPerPixel(format, type) * width);
	}

	public static int calculateTexImage2DStorage(Buffer buf, int format, int type, int width, int height) {
		return elements(buf, bytesPerPixel(format, type) * width * height);
	}

	public static int calculateTexImage3DStorage(Buffer buf, int format, int type, int width, int height, int depth) {
		return elements(buf, bytesPerPixel(format, type) * width * height * depth);
	}

	private static int elements(Buffer buf, int bytes) {
		if (!LWJGLUtil.CHECKS || buf == null) return 0;
		return bytes >> sizeExponent(buf);
	}

	private static int sizeExponent(Buffer buf) {
		if (buf instanceof ByteBuffer) return 0;
		if (buf instanceof ShortBuffer) return 1;
		if (buf instanceof IntBuffer || buf instanceof FloatBuffer) return 2;
		if (buf instanceof LongBuffer || buf instanceof DoubleBuffer) return 3;
		throw new IllegalStateException("Unsupported buffer type: " + buf);
	}

	private static int bytesPerPixel(int format, int type) {
		int bytes;
		switch (type) {
			case 0x1401: // GL_UNSIGNED_BYTE
			case 0x1400: // GL_BYTE
				bytes = 1;
				break;
			case 0x1403: // GL_UNSIGNED_SHORT
			case 0x1402: // GL_SHORT
				bytes = 2;
				break;
			case 0x1405: // GL_UNSIGNED_INT
			case 0x1404: // GL_INT
			case 0x1406: // GL_FLOAT
				bytes = 4;
				break;
			default:
				return 0;
		}
		switch (format) {
			case 0x1909: // GL_LUMINANCE
			case 0x1906: // GL_ALPHA
				return bytes;
			case 0x190A: // GL_LUMINANCE_ALPHA
				return bytes * 2;
			case 0x1907: // GL_RGB
			case 0x80E0: // GL_BGR
				return bytes * 3;
			case 0x1908: // GL_RGBA
			case 0x8000: // GL_ABGR_EXT
			case 0x80E1: // GL_BGRA
				return bytes * 4;
			default:
				return 0;
		}
	}

	private static String kind(Buffer buf) {
		if (buf instanceof ByteBuffer) return "ByteBuffer";
		if (buf instanceof ShortBuffer) return "ShortBuffer";
		if (buf instanceof IntBuffer) return "IntBuffer";
		if (buf instanceof LongBuffer) return "LongBuffer";
		if (buf instanceof FloatBuffer) return "FloatBuffer";
		if (buf instanceof DoubleBuffer) return "DoubleBuffer";
		return buf.getClass().getSimpleName();
	}
}
