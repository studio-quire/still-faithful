package org.studioquire.oldfaithful.lwjgl;

import org.lwjgl.LWJGLException;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;

/** SDL's video subsystem, started once on first use, with the hints our LWJGL 2 behaviour depends on. */
public final class Sdl {
	private static boolean video;

	private Sdl() {
	}

	public static synchronized void initVideo() throws LWJGLException {
		if (video) return;
		// The JVM handles Ctrl+C and friends; SDL mustn't take the signal handlers.
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_NO_SIGNAL_HANDLERS, "1");
		// Leaving fullscreen or alt-tabbing never minimises the game (LWJGL 2's exclusive mode did, and mods existed to stop it).
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, "0");
		// The window's close button is a request the game answers (Display.isCloseRequested), not an exit.
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_QUIT_ON_LAST_WINDOW_CLOSE, "0");
		// Clicking an unfocused window focuses it without also clicking in the game, as LWJGL 2 behaved.
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, "0");
		// Grabbed-mouse motion moves as the desktop pointer would (the system's speed and acceleration, applied by SDL
		// to raw input), which is what LWJGL 2 read on Windows; raw if the player asks.
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE, Settings.rawMouse ? "0" : "1");
		if (Settings.hidden) SDLHints.SDL_SetHint(SDLHints.SDL_HINT_WINDOW_ACTIVATE_WHEN_SHOWN, "0");
		check(SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_VIDEO), "SDL_InitSubSystem(VIDEO)");
		video = true;
	}

	public static boolean videoReady() {
		return video;
	}

	public static synchronized void quitVideo() {
		if (!video) return;
		SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_VIDEO);
		video = false;
	}

	public static void check(boolean ok, String what) throws LWJGLException {
		if (!ok) throw new LWJGLException(what + " failed: " + SDLError.SDL_GetError());
	}

	public static String error() {
		return SDLError.SDL_GetError();
	}
}
