package org.lwjgl.opengl;

import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLVideo;
import org.studioquire.oldfaithful.lwjgl.Window;

/** The Display's own context as a Drawable. */
final class DisplayDrawable implements Drawable {
	static final DisplayDrawable INSTANCE = new DisplayDrawable();

	private DisplayDrawable() {
	}

	@Override
	public boolean isCurrent() throws LWJGLException {
		return Window.context() != 0 && SDLVideo.SDL_GL_GetCurrentContext() == Window.context();
	}

	@Override
	public void makeCurrent() throws LWJGLException {
		if (!Window.created()) throw new IllegalStateException("Display not created");
		if (!SDLVideo.SDL_GL_MakeCurrent(Window.handle(), Window.context())) throw new LWJGLException("SDL_GL_MakeCurrent failed");
		GL.setCapabilities(Window.capabilities());
	}

	@Override
	public void releaseContext() throws LWJGLException {
		if (!Window.created()) return;
		SDLVideo.SDL_GL_MakeCurrent(Window.handle(), 0);
		GL.setCapabilities(null);
	}

	@Override
	public void destroy() {
		Display.destroy();
	}

	@Override
	public void setCLSharingProperties(PointerBuffer properties) throws LWJGLException {
		throw new UnsupportedOperationException("OpenCL sharing isn't offered");
	}
}
