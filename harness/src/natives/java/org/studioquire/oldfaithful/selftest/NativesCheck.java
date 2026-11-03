package org.studioquire.oldfaithful.selftest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.Sys;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

/**
 * Loads our LWJGL and OpenAL natives the way the game does and exercises them without a visible window:
 * {@code core} (the LWJGL library, display modes, an offscreen GL context through a pbuffer), {@code al-devices}
 * (OpenAL loaded, the system's output devices listed, none opened) and {@code al-play} (run with ALSOFT_DRIVERS=null:
 * OpenAL Soft's silent output, mixing a static buffer and a streaming queue as the game's sound library does).
 * Exits non-zero on any failure.
 */
public final class NativesCheck {
	private static int failures;

	public static void main(String[] args) throws Exception {
		String mode = args.length > 0 ? args[0] : "";
		System.out.println("[natives] java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + "), mode " + mode);
		if (mode.equals("core")) core();
		else if (mode.equals("al-devices")) alDevices();
		else if (mode.equals("al-play")) alPlay();
		else throw new IllegalArgumentException("mode: core | al-devices | al-play");
		System.out.println("[natives] DONE fail=" + failures);
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void check(boolean ok, String what) {
		System.out.println("[natives] " + (ok ? "PASS " : "FAIL ") + what);
		if (!ok) failures++;
	}

	private static void core() throws Exception {
		check(Sys.getVersion() != null && Sys.is64Bit(), "LWJGL native loaded: " + Sys.getVersion() + ", 64-bit " + Sys.is64Bit());
		DisplayMode desktop = Display.getDesktopDisplayMode();
		DisplayMode[] modes = Display.getAvailableDisplayModes();
		check(desktop.getWidth() > 0 && modes.length > 0, "display modes: desktop " + desktop + ", " + modes.length + " available");

		// A pbuffer gives a GL context with a hidden dummy window: nothing shows, nothing takes focus.
		Pbuffer pbuffer = new Pbuffer(16, 16, new PixelFormat(8, 24, 0), null);
		try {
			pbuffer.makeCurrent();
			String version = GL11.glGetString(GL11.GL_VERSION);
			check(version != null, "GL context: " + version + " / " + GL11.glGetString(GL11.GL_RENDERER));
			// Loads every generated capability flag and function pointer the game might use.
			check(GLContext.getCapabilities().OpenGL11, "GL capabilities loaded (OpenGL 1.1 present, 2.0 " + GLContext.getCapabilities().OpenGL20 + ")");
			GL11.glClearColor(0.25f, 0.5f, 1f, 1f);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			ByteBuffer pixel = BufferUtils.createByteBuffer(4);
			GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
			int r = pixel.get(0) & 0xff, g = pixel.get(1) & 0xff, b = pixel.get(2) & 0xff;
			check(Math.abs(r - 64) <= 2 && Math.abs(g - 128) <= 2 && b == 255 && GL11.glGetError() == GL11.GL_NO_ERROR,
					"GL clear and read back: " + r + "," + g + "," + b);
		} finally {
			pbuffer.destroy();
		}
	}

	private static void alDevices() throws Exception {
		AL.create(null, 44100, 60, false, false); // load the library, open nothing
		try {
			// ALC_DEFAULT_ALL_DEVICES_SPECIFIER: the system's own name for the default endpoint, so a backend
			// (WASAPI on Windows) really enumerated the hardware.
			String def = ALC10.alcGetString(null, 0x1012);
			check(def != null && !def.isEmpty() && !def.equals("No Output"), "OpenAL loaded, default output device: " + def);
		} finally {
			AL.destroy();
		}
	}

	private static void alPlay() throws Exception {
		check("null".equals(System.getenv("ALSOFT_DRIVERS")), "running on OpenAL Soft's silent null output");
		AL.create();
		try {
			String version = AL10.alGetString(AL10.AL_VERSION);
			check(version != null && version.contains("ALSOFT 1.25.2"), "our OpenAL Soft: " + version + " / " + AL10.alGetString(AL10.AL_RENDERER)
					+ " on " + ALC10.alcGetString(AL.getDevice(), ALC10.ALC_DEVICE_SPECIFIER));

			// A static buffer, played to the end.
			int rate = 22050;
			int source = AL10.alGenSources();
			int buffer = AL10.alGenBuffers();
			AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, tone(rate, rate / 4, 440), rate);
			AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
			AL10.alSourcePlay(source);
			check(waitFor(source, AL10.AL_STOPPED, 3000), "static buffer played to the end");

			// A streaming queue (how music plays): three buffers queued, all processed.
			AL10.alSourcei(source, AL10.AL_BUFFER, 0);
			IntBuffer queue = BufferUtils.createIntBuffer(3);
			AL10.alGenBuffers(queue);
			for (int i = 0; i < 3; i++) AL10.alBufferData(queue.get(i), AL10.AL_FORMAT_MONO16, tone(rate, rate / 10, 220 * (i + 1)), rate);
			AL10.alSourceQueueBuffers(source, queue);
			AL10.alSourcePlay(source);
			check(waitFor(source, AL10.AL_STOPPED, 3000) && AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED) == 3,
					"streamed queue processed: " + AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED) + " of 3");
			AL10.alSourceUnqueueBuffers(source, queue);
			AL10.alDeleteSources(source);
			AL10.alDeleteBuffers(buffer);
			AL10.alDeleteBuffers(queue);
			check(AL10.alGetError() == AL10.AL_NO_ERROR, "no AL errors");
		} finally {
			AL.destroy();
		}
	}

	private static boolean waitFor(int source, int state, long timeoutMs) throws InterruptedException {
		long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == state) return true;
			Thread.sleep(20);
		}
		return false;
	}

	private static ByteBuffer tone(int rate, int samples, int hz) {
		ByteBuffer data = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.nativeOrder());
		for (int i = 0; i < samples; i++) data.putShort((short) (Math.sin(2 * Math.PI * hz * i / rate) * 8000));
		data.flip();
		return data;
	}
}
