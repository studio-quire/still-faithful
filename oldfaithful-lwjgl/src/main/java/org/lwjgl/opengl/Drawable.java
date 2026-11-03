package org.lwjgl.opengl;

import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;

/** LWJGL 2's Drawable: something with a GL context that can be made current on a thread. */
public interface Drawable {
	boolean isCurrent() throws LWJGLException;

	void makeCurrent() throws LWJGLException;

	void releaseContext() throws LWJGLException;

	void destroy();

	/** OpenCL sharing isn't offered. */
	void setCLSharingProperties(PointerBuffer properties) throws LWJGLException;
}
