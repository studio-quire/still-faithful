package org.lwjgl.opengl;

import java.util.Map;
import java.util.WeakHashMap;

import org.lwjgl.LWJGLException;
import org.studioquire.oldfaithful.lwjgl.Window;

/** LWJGL 2's GLContext: the current thread's GL capabilities, as LWJGL 2's flags. */
public final class GLContext {
	private static final Map<GLCapabilities, ContextCapabilities> LEGACY = new WeakHashMap<>();

	private GLContext() {
	}

	public static ContextCapabilities getCapabilities() {
		GLCapabilities caps = GLContext.currentCapabilities();
		if (caps == null) throw new RuntimeException("No OpenGL context found in the current thread.");
		if (caps == Window.capabilities()) return Window.legacyCapabilities();
		synchronized (LEGACY) {
			ContextCapabilities legacy = LEGACY.get(caps);
			if (legacy == null) LEGACY.put(caps, legacy = new ContextCapabilities(caps));
			return legacy;
		}
	}

	/** LWJGL 3's capabilities for this thread's context, or null if none is current. */
	static GLCapabilities currentCapabilities() {
		try {
			return GL.getCapabilities();
		} catch (IllegalStateException e) {
			return null;
		}
	}

	/** The address of a GL function in the current context, or 0. */
	public static long getFunctionAddress(String name) {
		return GL.getFunctionProvider() == null ? 0 : GL.getFunctionProvider().getFunctionAddress(name);
	}

	/** LWJGL 2 tracked a context per thread here; with LWJGL 3 the current context is SDL's and GL's business. */
	public static void useContext(Object context) throws LWJGLException {
		if (context == null) GL.setCapabilities(null);
	}

	public static void useContext(Object context, boolean forwardCompatible) throws LWJGLException {
		useContext(context);
	}

	public static void loadOpenGLLibrary() throws LWJGLException {
	}

	public static void unloadOpenGLLibrary() {
	}
}
