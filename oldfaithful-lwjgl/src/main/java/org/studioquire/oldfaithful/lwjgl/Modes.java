package org.studioquire.oldfaithful.lwjgl;

import java.lang.reflect.Constructor;

import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDL_DisplayMode;

/**
 * SDL display modes as LWJGL 2 DisplayModes, in pixels. A mode the system offers is "fullscreen capable" in LWJGL 2's
 * sense, which only its package-private constructor can say, so that constructor is used.
 */
final class Modes {
	private static final Constructor<DisplayMode> CAPABLE;

	static {
		try {
			CAPABLE = DisplayMode.class.getDeclaredConstructor(int.class, int.class, int.class, int.class);
			CAPABLE.setAccessible(true);
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private Modes() {
	}

	static DisplayMode of(SDL_DisplayMode m) {
		float d = m.pixel_density() > 0 ? m.pixel_density() : 1f;
		try {
			return CAPABLE.newInstance(Math.round(m.w() * d), Math.round(m.h() * d), SDLPixels.SDL_BITSPERPIXEL(m.format()),
					Math.round(m.refresh_rate()));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
