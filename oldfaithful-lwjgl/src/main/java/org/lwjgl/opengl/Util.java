package org.lwjgl.opengl;

/** LWJGL 2's Util: GL error checks. */
public final class Util {
	private Util() {
	}

	/** Throws an {@link OpenGLException} if the GL has an error flagged. */
	public static void checkGLError() throws OpenGLException {
		int error = GL11.glGetError();
		if (error != GL11.GL_NO_ERROR) throw new OpenGLException(error);
	}

	public static String translateGLErrorString(int error) {
		switch (error) {
			case GL11.GL_NO_ERROR: return "No error";
			case GL11.GL_INVALID_ENUM: return "Invalid enum";
			case GL11.GL_INVALID_VALUE: return "Invalid value";
			case GL11.GL_INVALID_OPERATION: return "Invalid operation";
			case GL11.GL_STACK_OVERFLOW: return "Stack overflow";
			case GL11.GL_STACK_UNDERFLOW: return "Stack underflow";
			case GL11.GL_OUT_OF_MEMORY: return "Out of memory";
			case 0x506: return "Invalid framebuffer operation";
			default: return null;
		}
	}
}
