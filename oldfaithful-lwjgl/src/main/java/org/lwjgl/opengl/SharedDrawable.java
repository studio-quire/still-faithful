package org.lwjgl.opengl;

import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLVideo;
import org.studioquire.oldfaithful.lwjgl.Window;

/**
 * LWJGL 2's SharedDrawable: a second context sharing objects (textures, buffers) with the Display's, to be made
 * current on another thread. Created where the Display's context is current, as LWJGL 2 required.
 */
public final class SharedDrawable implements Drawable {
	private long context;
	private GLCapabilities capabilities;

	public SharedDrawable(Drawable drawable) throws LWJGLException {
		if (!Window.created()) throw new IllegalStateException("Display not created");
		long previous = SDLVideo.SDL_GL_GetCurrentContext();
		GLCapabilities previousCaps = GLContext.currentCapabilities();
		if (previous != Window.context()) drawable.makeCurrent();
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 1);
		context = SDLVideo.SDL_GL_CreateContext(Window.handle());
		SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 0);
		if (context == 0) throw new LWJGLException("SDL_GL_CreateContext (shared) failed: " + org.lwjgl.sdl.SDLError.SDL_GetError());
		// SDL makes a new context current; take its capabilities, then put back what was current.
		capabilities = GL.createCapabilities();
		SDLVideo.SDL_GL_MakeCurrent(Window.handle(), previous);
		GL.setCapabilities(previous == 0 ? null : previous == Window.context() ? Window.capabilities() : previousCaps);
	}

	@Override
	public boolean isCurrent() throws LWJGLException {
		return context != 0 && SDLVideo.SDL_GL_GetCurrentContext() == context;
	}

	@Override
	public void makeCurrent() throws LWJGLException {
		if (context == 0) throw new IllegalStateException("SharedDrawable destroyed");
		if (!SDLVideo.SDL_GL_MakeCurrent(Window.handle(), context)) throw new LWJGLException("SDL_GL_MakeCurrent failed");
		GL.setCapabilities(capabilities);
	}

	@Override
	public void releaseContext() throws LWJGLException {
		if (isCurrent()) {
			SDLVideo.SDL_GL_MakeCurrent(Window.handle(), 0);
			GL.setCapabilities(null);
		}
	}

	@Override
	public void destroy() {
		if (context == 0) return;
		SDLVideo.SDL_GL_DestroyContext(context);
		context = 0;
		capabilities = null;
	}

	@Override
	public void setCLSharingProperties(PointerBuffer properties) throws LWJGLException {
		throw new UnsupportedOperationException("OpenCL sharing isn't offered");
	}
}
