package org.studioquire.oldfaithful.selftest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.SharedDrawable;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.util.glu.GLU;
import org.studioquire.oldfaithful.lwjgl.KeyMap;
import org.studioquire.oldfaithful.lwjgl.KeyboardState;
import org.studioquire.oldfaithful.lwjgl.MouseState;
import org.studioquire.oldfaithful.lwjgl.Placement;
import org.studioquire.oldfaithful.lwjgl.Settings;
import org.studioquire.oldfaithful.lwjgl.Window;

/**
 * Our LWJGL component, exercised as b1.7.3 uses it, with no visible window (the window is created hidden and never
 * shown, so nothing takes focus). One mode per JVM:
 * <ul>
 * <li>{@code classes}: every LWJGL 3 class the bake changed loads and initialises, so the JVM's verifier has passed
 * every generated method.</li>
 * <li>{@code display}: Display on SDL3 with a GL context; the game's own GL calls through the generated forwarders,
 * drawn and read back (including the forwarders that supply a GL type, as the game's tessellator uses them); a
 * shared context on another thread; resizing.</li>
 * <li>{@code input}: synthetic SDL events, checked against LWJGL 2's keyboard and mouse behaviour.</li>
 * <li>{@code sound}: the game's own sound library (paulscode, from the official jar, read locally) plays a real game
 * sound through our AL into OpenAL Soft's wave writer; the file must hold sound.</li>
 * </ul>
 */
public final class LwjglCheck {
	private static int failures;

	public static void main(String[] args) throws Exception {
		String mode = args.length > 0 ? args[0] : "";
		System.out.println("[lwjgl] java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + "), mode " + mode);
		switch (mode) {
			case "classes": classes(); break;
			case "display": display(); break;
			case "input": input(); break;
			case "sound": sound(args[1], args[2]); break;
			default: throw new IllegalArgumentException("mode: classes | display | input | sound <wave file> <sounds dir>");
		}
		System.out.println("[lwjgl] DONE fail=" + failures);
		System.exit(failures == 0 ? 0 : 1);
	}

	static void check(boolean ok, String what) {
		System.out.println("[lwjgl] " + (ok ? "PASS " : "FAIL ") + what);
		if (!ok) failures++;
	}

	// ---- classes ----------------------------------------------------------------------------------------------------

	private static void classes() throws Exception {
		List<String> changed = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				LwjglCheck.class.getClassLoader().getResourceAsStream("META-INF/oldfaithful-lwjgl/changed-classes.txt"), StandardCharsets.UTF_8))) {
			for (String line; (line = r.readLine()) != null; ) if (!line.isEmpty()) changed.add(line.replace('/', '.'));
		}
		List<String> broken = new ArrayList<>();
		for (String c : changed) {
			try {
				// Initialising links the class, and linking runs the verifier over every method, generated ones too.
				// Nested holder classes only initialise through their outer class, so those are loaded through it.
				Class.forName(c.contains("$") ? c.substring(0, c.indexOf('$')) : c, true, LwjglCheck.class.getClassLoader());
				Class.forName(c, false, LwjglCheck.class.getClassLoader());
			} catch (Throwable t) {
				broken.add(c + ": " + t);
			}
		}
		for (String b : broken) System.out.println("[lwjgl]   " + b);
		check(!changed.isEmpty() && broken.isEmpty(), changed.size() + " changed classes load, verify and initialise (" + broken.size() + " didn't)");
	}

	// ---- display ----------------------------------------------------------------------------------------------------

	private static void display() throws Exception {
		check(Display.getDesktopDisplayMode().getWidth() > 0, "desktop mode " + Display.getDesktopDisplayMode());
		check(Display.getAvailableDisplayModes().length > 0, Display.getAvailableDisplayModes().length + " fullscreen modes");

		// What b1.7.3 does without a canvas, plus the resizable window our display mod asks for.
		Display.setDisplayMode(new DisplayMode(854, 480));
		Display.setTitle("Old Faithful LWJGL check");
		Display.setResizable(true);
		Display.create();
		try {
			check(Display.isCreated() && Display.getWidth() == 854 && Display.getHeight() == 480,
					"hidden window and GL context at 854x480: " + Display.getWidth() + "x" + Display.getHeight());
			String version = GL11.glGetString(GL11.GL_VERSION);
			check(version != null, "GL " + version + " / " + GL11.glGetString(GL11.GL_RENDERER));
			ContextCapabilities caps = GLContext.getCapabilities();
			check(caps.OpenGL11 && caps.OpenGL12 && caps.OpenGL14, "ContextCapabilities: OpenGL 1.1, 1.2, 1.4 (b1.7.3 checks 1.2 and 1.4)");
			System.out.println("[lwjgl]   occlusion query " + caps.GL_ARB_occlusion_query + ", VBO " + caps.GL_ARB_vertex_buffer_object
					+ ", NV fog distance " + caps.GL_NV_fog_distance);

			drawAndRead();

			// glGetFloat(int, FloatBuffer) is LWJGL 2's name for glGetFloatv; GLU (lwjgl_util) calls the matrix ones.
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			GLU.gluPerspective(70f, 854f / 480f, 0.05f, 256f);
			FloatBuffer m = BufferUtils.createFloatBuffer(16);
			GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, m);
			float f = (float) (1 / Math.tan(Math.toRadians(35)));
			check(Math.abs(m.get(5) - f) < 1e-4 && Math.abs(m.get(11) + 1) < 1e-6, "gluPerspective through glGetFloat: m[5]=" + m.get(5) + " (want " + f + ")");
			GL11.glLoadIdentity();
			GL11.glMatrixMode(GL11.GL_MODELVIEW);

			// Fog and light through LWJGL 2's buffer overloads (glFog -> glFogfv, glLight -> glLightfv).
			FloatBuffer colour = BufferUtils.createFloatBuffer(4);
			colour.put(new float[] {0.25f, 0.5f, 0.75f, 1f}).flip();
			GL11.glFog(GL11.GL_FOG_COLOR, colour);
			FloatBuffer read = BufferUtils.createFloatBuffer(16);
			GL11.glGetFloat(GL11.GL_FOG_COLOR, read);
			check(Math.abs(read.get(2) - 0.75f) < 1e-3, "glFog / glGetFloat round trip: " + read.get(0) + "," + read.get(1) + "," + read.get(2));
			GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, colour);
			check(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL errors so far");

			sharedContext();

			bufferChecks();
			legacyFlags(caps);

			Display.update();
			Display.update();
			check(!Display.wasResized(), "wasResized false while the size holds");
			Display.setDisplayMode(new DisplayMode(640, 400));
			check(Display.getWidth() == 640 && Display.getHeight() == 400, "setDisplayMode resizes the window: " + Display.getWidth() + "x" + Display.getHeight());
			Display.update();
			check(Display.wasResized() && Display.wasResized(), "wasResized true after the update that saw the resize, however often it's read (LWJGL 2)");
			check(!Display.isCloseRequested(), "update pumps events; no close request");
			Display.update();
			check(!Display.wasResized(), "wasResized false again after the next update");
		} finally {
			Display.destroy();
		}
		check(!Display.isCreated(), "destroyed");
		placement();
	}

	/**
	 * Settings.placement: a window kept somewhere that's no longer on any display (a monitor unplugged) comes back
	 * whole onto one, at its size; one whose display moved in the desktop follows it; Window.placement() reads back
	 * the windowed bounds and a maximise (pending, as the window is hidden).
	 */
	private static void placement() throws Exception {
		try {
			Placement gone = new Placement();
			gone.x = -100000;
			gone.y = 100000;
			gone.width = 700;
			gone.height = 400;
			Settings.placement = gone;
			Display.create();
			try {
				Placement p = Window.placement();
				check(p != null && p.width == 700 && p.height == 400 && onUsableArea(p), "a placement off every display opens whole on one: " + p);
			} finally {
				Display.destroy();
			}

			Placement now = null;
			Settings.placement = null;
			Display.create();
			try {
				now = Window.placement();
			} finally {
				Display.destroy();
			}
			// The same display, said to have been 50 px further left and up: the window keeps its offset from it.
			Placement moved = new Placement();
			moved.x = now.x - 50;
			moved.y = now.y - 50;
			moved.width = 640;
			moved.height = 360;
			moved.display = now.display;
			moved.displayX = now.displayX - 50;
			moved.displayY = now.displayY - 50;
			moved.maximized = true;
			Settings.placement = moved;
			Display.create();
			try {
				Placement p = Window.placement();
				check(p.x == now.x && p.y == now.y && p.width == 640 && p.height == 360 && p.maximized,
						"a placement follows its display (" + now.display + ") to where it is now, maximise pending: " + p);
			} finally {
				Display.destroy();
			}
		} finally {
			Settings.placement = null;
		}
	}

	private static boolean onUsableArea(Placement p) {
		java.nio.IntBuffer ids = SDLVideo.SDL_GetDisplays();
		try (SDL_Rect r = SDL_Rect.malloc()) {
			for (int i = 0; i < ids.remaining(); i++) {
				if (SDLVideo.SDL_GetDisplayUsableBounds(ids.get(i), r) && p.x >= r.x() && p.y >= r.y()
						&& p.x + p.width <= r.x() + r.w() && p.y + p.height <= r.y() + r.h()) return true;
			}
			return false;
		} finally {
			SDLStdinc.SDL_free(ids);
		}
	}

	/**
	 * Draws as b1.7.3's tessellator does: interleaved client arrays, colours as unsigned bytes through
	 * glColorPointer(int, boolean unsigned, int, ByteBuffer) and positions through glVertexPointer(int, int stride,
	 * FloatBuffer), two forwarders that supply the GL type. A wrong type or position shows in the pixels.
	 */
	private static void drawAndRead() {
		GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
		GL11.glClearColor(0f, 0f, 0f, 1f);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		// Two triangles covering the screen; vertex = x, y, z floats then RGBA bytes: 16 bytes, like a slim tessellator.
		float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, -1}, {1, 1}, {-1, 1}};
		ByteBuffer data = BufferUtils.createByteBuffer(corners.length * 16);
		for (float[] c : corners) {
			data.putFloat(c[0]).putFloat(c[1]).putFloat(0f);
			data.put((byte) 200).put((byte) 100).put((byte) 50).put((byte) 255);
		}
		data.flip();
		FloatBuffer positions = data.asFloatBuffer();
		data.position(12);
		ByteBuffer colours = data.slice().order(ByteOrder.nativeOrder());
		data.position(0);
		GL11.glVertexPointer(3, 16, positions);
		GL11.glColorPointer(4, true, 16, colours);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, corners.length);
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		ByteBuffer pixel = BufferUtils.createByteBuffer(4);
		GL11.glReadPixels(Display.getWidth() / 3, Display.getHeight() / 3, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
		int r = pixel.get(0) & 0xff, g = pixel.get(1) & 0xff, b = pixel.get(2) & 0xff;
		check(Math.abs(r - 200) <= 2 && Math.abs(g - 100) <= 2 && Math.abs(b - 50) <= 2,
				"tessellator-style draw (glVertexPointer/glColorPointer forwarders) reads back 200,100,50: " + r + "," + g + "," + b);
	}

	/** Runs a call LWJGL 2 rejected; true if it threw IllegalArgumentException, as LWJGL 2's checks did. */
	private static boolean rejects(String what, Runnable call) {
		try {
			call.run();
			System.out.println("[lwjgl]   " + what + ": not rejected");
			return false;
		} catch (IllegalArgumentException e) {
			System.out.println("[lwjgl]   " + what + ": " + e.getMessage());
			return true;
		}
	}

	/**
	 * LWJGL 2's argument checks, restored on LWJGL 3 by the bake: image calls check the buffer against the image's
	 * size, matrix reads against 16, and buffers must be direct. Each too-small buffer is a view of a big enough one,
	 * so if a check were missing the GL would write into memory we own, not past it.
	 */
	private static void bufferChecks() {
		int w = 4, h = 4;
		ByteBuffer big = BufferUtils.createByteBuffer(4096);
		ByteBuffer small = (ByteBuffer) big.duplicate().limit(w * h * 4 - 1);
		check(rejects("glReadPixels 4x4 RGBA into 63 bytes", () -> GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, small)),
				"glReadPixels: a buffer smaller than the image is rejected");
		ByteBuffer exact = (ByteBuffer) big.duplicate().limit(w * h * 4);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, exact);
		IntBuffer ints = big.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
		ints.limit(w * h);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, ints);
		check(GL11.glGetError() == GL11.GL_NO_ERROR, "glReadPixels: exactly the image's size passes, in bytes and in ints (16 for 4x4 RGBA)");
		ints.limit(w * h - 1);
		check(rejects("glReadPixels into 15 ints", () -> GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, ints)),
				"glReadPixels: sizes count the buffer's elements, as LWJGL 2's did");
		ByteBuffer heap = ByteBuffer.allocate(64);
		check(rejects("glReadPixels into a heap buffer", () -> GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, heap)),
				"glReadPixels: a buffer that isn't direct is rejected");

		IntBuffer tex = BufferUtils.createIntBuffer(1);
		GL11.glGenTextures(tex);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.get(0));
		ByteBuffer rgb = (ByteBuffer) big.duplicate().limit(w * h * 3 - 1);
		check(rejects("glTexImage2D 4x4 RGB from 47 bytes",
						() -> GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, rgb)),
				"glTexImage2D: a buffer smaller than the image is rejected");
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) big.duplicate().limit(w * h * 4));
		ByteBuffer sub = (ByteBuffer) big.duplicate().limit(2 * 2 * 4 - 1);
		check(rejects("glTexSubImage2D 2x2 RGBA from 15 bytes",
						() -> GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 2, 2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, sub)),
				"glTexSubImage2D (b1.7.3's texture animations): a buffer smaller than the region is rejected");
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 2, 2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) big.duplicate().limit(16));
		// LWJGL 2 can't know a texture's size, so it checked glGetTexImage for one pixel only; so do we.
		ByteBuffer read = (ByteBuffer) big.duplicate().limit(3);
		check(rejects("glGetTexImage RGBA into 3 bytes", () -> GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, read)),
				"glGetTexImage: a buffer smaller than one pixel is rejected (LWJGL 2's check)");
		GL11.glDeleteTextures(tex);

		FloatBuffer four = BufferUtils.createFloatBuffer(16);
		four.limit(4);
		check(rejects("glGetFloat(GL_MODELVIEW_MATRIX) into 4 floats", () -> GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, four)),
				"glGetFloat: fewer than 16 elements rejected, as LWJGL 2 required");
		check(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL errors from the checked calls");
	}

	/**
	 * ContextCapabilities flags LWJGL 3's GLCapabilities has no field for: those LWJGL 2 set from the extension string
	 * alone match the context's extensions (read here independently); those needing functions LWJGL 3 has no binding
	 * for are false. The bake report inside the jar lists both.
	 */
	private static void legacyFlags(ContextCapabilities caps) throws Exception {
		StringBuilder report = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				LwjglCheck.class.getClassLoader().getResourceAsStream("META-INF/oldfaithful-lwjgl/bake-report.txt"), StandardCharsets.UTF_8))) {
			for (String line; (line = r.readLine()) != null; ) report.append(line).append('\n');
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(
				"from the extension string, as LWJGL 2 set them: \\[(.*?)\\]; always false \\(functions LWJGL 3 has no binding for\\): \\[(.*?)\\]")
				.matcher(report);
		if (!m.find()) {
			check(false, "bake report lists the flags set from extension strings and those always false");
			return;
		}
		java.util.Set<String> exts = new java.util.HashSet<>();
		int n = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_NUM_EXTENSIONS);
		for (int i = 0; i < n; i++) exts.add(org.lwjgl.opengl.GL30.glGetStringi(GL11.GL_EXTENSIONS, i));
		List<String> wrong = new ArrayList<>(), on = new ArrayList<>();
		String[] fromStrings = m.group(1).split(", "), alwaysFalse = m.group(2).split(", ");
		for (String flag : fromStrings) {
			boolean value = ContextCapabilities.class.getField(flag).getBoolean(caps);
			if (value != exts.contains(flag)) wrong.add(flag);
			if (value) on.add(flag);
		}
		for (String flag : alwaysFalse) if (ContextCapabilities.class.getField(flag).getBoolean(caps)) wrong.add(flag);
		check(wrong.isEmpty() && !on.isEmpty() && fromStrings.length + alwaysFalse.length == 65,
				fromStrings.length + " flags LWJGL 3 doesn't know follow the extension string (" + on.size() + " true here: " + on + "), "
						+ alwaysFalse.length + " are false for want of a binding; wrong: " + wrong);
	}

	/** A SharedDrawable made current on another thread sees a texture made on the main context. */
	private static void sharedContext() throws Exception {
		SharedDrawable shared = new SharedDrawable(Display.getDrawable());
		IntBuffer tex = BufferUtils.createIntBuffer(1);
		GL11.glGenTextures(tex);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.get(0));
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL11.glFlush();
		AtomicBoolean seen = new AtomicBoolean();
		Thread t = new Thread(() -> {
			try {
				shared.makeCurrent();
				seen.set(GL11.glIsTexture(tex.get(0)));
				shared.releaseContext();
			} catch (Exception e) {
				e.printStackTrace(System.out);
			}
		});
		t.start();
		t.join(10_000);
		check(seen.get(), "SharedDrawable on another thread shares the main context's texture");
		check(Display.isCurrent(), "main context still current here");
		GL11.glDeleteTextures(tex);
		shared.destroy();
	}

	// ---- input ------------------------------------------------------------------------------------------------------

	private static SDL_Event ev;
	private static int win;

	private static void input() throws Exception {
		Display.setDisplayMode(new DisplayMode(400, 300));
		Display.create();
		Keyboard.create();
		Mouse.create();
		ev = SDL_Event.calloc();
		win = Window.windowId();
		try {
			keyboard();
			mouse();
		} finally {
			ev.free();
			Display.destroy();
		}
	}

	private static void key(int scancode, int mod, boolean down, boolean repeat) {
		ev.clear();
		ev.key().type(down ? SDLEvents.SDL_EVENT_KEY_DOWN : SDLEvents.SDL_EVENT_KEY_UP).timestamp(SDLTimer.SDL_GetTicksNS())
				.windowID(win).scancode(scancode).mod((short) mod).down(down).repeat(repeat);
		SDLEvents.SDL_PushEvent(ev);
	}

	private static void text(String s) {
		byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
		ByteBuffer mem = SDLStdinc.SDL_malloc(utf8.length + 1);
		mem.put(utf8).put((byte) 0).flip();
		ev.clear();
		ev.text().type(SDLEvents.SDL_EVENT_TEXT_INPUT).timestamp(SDLTimer.SDL_GetTicksNS()).windowID(win).text(mem);
		SDLEvents.SDL_PushEvent(ev);
	}

	private static final class Seen {
		int key;
		char ch;
		boolean down, repeat;

		@Override
		public String toString() {
			return "(key " + key + ", char " + (int) ch + ", " + (down ? "down" : "up") + (repeat ? ", repeat" : "") + ")";
		}
	}

	private static List<Seen> keys() {
		Display.processMessages();
		List<Seen> seen = new ArrayList<>();
		while (Keyboard.next()) {
			Seen s = new Seen();
			s.key = Keyboard.getEventKey();
			s.ch = Keyboard.getEventCharacter();
			s.down = Keyboard.getEventKeyState();
			s.repeat = Keyboard.isRepeatEvent();
			seen.add(s);
		}
		return seen;
	}

	private static void keyboard() throws ReflectiveOperationException {
		final int A = 4, Q = 20, RETURN = 40, BACKSPACE = 42, KP_1 = 89, KP_PLUS = 87, LSHIFT = 225, LCTRL = 224, RALT = 230;
		final int NUM = SDLKeycode.SDL_KMOD_NUM;
		keys();

		key(A, 0, true, false);
		List<Seen> s = keys();
		char a = (char) org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(A, (short) 0, false);
		check(s.size() == 1 && s.get(0).key == Keyboard.KEY_A && s.get(0).down && s.get(0).ch == a,
				"key A: KEY_A with its layout character '" + a + "' in the same event " + s);
		check(Keyboard.isKeyDown(Keyboard.KEY_A), "isKeyDown(KEY_A) while held");
		key(A, 0, false, false);
		s = keys();
		check(s.size() == 1 && !s.get(0).down && s.get(0).ch == 0 && !Keyboard.isKeyDown(Keyboard.KEY_A), "release: key up, no character " + s);

		key(RETURN, 0, true, false);
		key(BACKSPACE, 0, true, false);
		s = keys();
		check(s.size() == 2 && s.get(0).key == Keyboard.KEY_RETURN && s.get(0).ch == '\r' && s.get(1).key == Keyboard.KEY_BACK && s.get(1).ch == '\b',
				"Enter types '\\r' and Backspace '\\b', as LWJGL 2 on Windows " + s);
		key(RETURN, 0, false, false);
		key(BACKSPACE, 0, false, false);
		keys();

		key(KP_1, NUM, true, false);
		key(KP_1, NUM, false, false);
		key(KP_1, 0, true, false);
		key(KP_1, 0, false, false);
		key(KP_PLUS, 0, true, false);
		key(KP_PLUS, 0, false, false);
		s = keys();
		check(s.size() == 6 && s.get(0).key == Keyboard.KEY_NUMPAD1 && s.get(0).ch == '1' && s.get(2).ch == 0
						&& s.get(4).key == Keyboard.KEY_ADD && s.get(4).ch == '+',
				"keypad: '1' with Num Lock, nothing without, '+' either way " + s);

		key(LSHIFT, SDLKeycode.SDL_KMOD_LSHIFT, true, false);
		keys();
		check(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT), "held Shift: isKeyDown(KEY_LSHIFT)");
		key(A, SDLKeycode.SDL_KMOD_LSHIFT, true, false);
		s = keys();
		char shiftedA = (char) org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(A, (short) SDLKeycode.SDL_KMOD_LSHIFT, false);
		check(s.size() == 1 && s.get(0).ch == shiftedA && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT),
				"Shift+A types '" + shiftedA + "', Shift still down " + s);
		key(A, SDLKeycode.SDL_KMOD_LSHIFT, false, false);
		key(LSHIFT, 0, false, false);
		keys();
		check(!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT), "Shift released");

		key(LCTRL, SDLKeycode.SDL_KMOD_LCTRL, true, false);
		key(A, SDLKeycode.SDL_KMOD_LCTRL, true, false);
		s = keys();
		check(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && s.size() == 2 && s.get(1).ch == 1, "Ctrl held: isKeyDown(KEY_LCONTROL), Ctrl+A types control code 1 " + s);
		key(A, SDLKeycode.SDL_KMOD_LCTRL, false, false);
		key(LCTRL, 0, false, false);
		keys();

		// AltGr arrives as right Alt with (on Windows) left Ctrl: its characters must not become control codes.
		key(RALT, SDLKeycode.SDL_KMOD_RALT | SDLKeycode.SDL_KMOD_LCTRL, true, false);
		key(Q, SDLKeycode.SDL_KMOD_RALT | SDLKeycode.SDL_KMOD_LCTRL, true, false);
		s = keys();
		char altGrQ = (char) org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(Q, (short) (SDLKeycode.SDL_KMOD_RALT | SDLKeycode.SDL_KMOD_LCTRL), false);
		check(s.size() == 2 && s.get(0).key == Keyboard.KEY_RMENU && s.get(1).key == Keyboard.KEY_Q && (s.get(1).ch == altGrQ || altGrQ >= 0xFFFF) && s.get(1).ch >= 32,
				"AltGr+Q: KEY_RMENU then KEY_Q typing the layout's AltGr character '" + altGrQ + "', not a control code " + s);
		key(Q, 0, false, false);
		key(RALT, 0, false, false);
		keys();

		// Repeats: dropped unless enabled, then marked.
		key(A, 0, true, false);
		key(A, 0, true, true);
		key(A, 0, true, true);
		s = keys();
		check(s.size() == 1 && !s.get(0).repeat, "repeat events dropped while disabled " + s);
		Keyboard.enableRepeatEvents(true);
		key(A, 0, true, true);
		s = keys();
		check(s.size() == 1 && s.get(0).repeat && s.get(0).key == Keyboard.KEY_A, "repeat events delivered and marked when enabled " + s);
		key(A, 0, false, false);
		keys();

		// Text input (on with repeat events, as in b1.7.3's text fields): the key and its text join into one event;
		// text alone (an IME, a dead key) is an event with key 0.
		key(A, 0, true, false);
		text("a");
		s = keys();
		check(s.size() == 1 && s.get(0).key == Keyboard.KEY_A && s.get(0).ch == 'a', "text input: key and its text in one event " + s);
		key(A, 0, false, false);
		text("é漢");
		s = keys();
		check(s.size() == 3 && s.get(1).key == 0 && s.get(1).ch == 'é' && s.get(1).down && s.get(2).ch == '漢',
				"text without a key press: one event per character, key 0 " + s);
		key(KP_1, NUM, true, false);
		text("1");
		s = keys();
		check(s.size() == 1 && s.get(0).ch == '1' && s.get(0).key == Keyboard.KEY_NUMPAD1, "text input: keypad 1 types '1' " + s);
		key(KP_1, NUM, false, false);
		keys();
		// Control characters with text input on, where b1.7.3's text fields need them: Ctrl+V (22) pastes. SDL sends
		// no text for these, so each must come with its key press, as WM_CHAR gave it to LWJGL 2.
		final int V = 25, LBRACKET = 47;
		key(LCTRL, SDLKeycode.SDL_KMOD_LCTRL, true, false);
		key(V, SDLKeycode.SDL_KMOD_LCTRL, true, false);
		key(V, SDLKeycode.SDL_KMOD_LCTRL, false, false);
		key(V, SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_LSHIFT, true, false);
		key(LBRACKET, SDLKeycode.SDL_KMOD_LCTRL, true, false);
		key(RETURN, SDLKeycode.SDL_KMOD_LCTRL, true, false);
		key(BACKSPACE, SDLKeycode.SDL_KMOD_LCTRL, true, false);
		key(LCTRL, 0, false, false);
		s = keys();
		check(s.size() == 8 && s.get(1).key == Keyboard.KEY_V && s.get(1).ch == 22 && s.get(2).ch == 0 && s.get(3).ch == 22,
				"text input: Ctrl+V types 22 (paste) in its key event, with Shift too " + s);
		check(s.size() == 8 && s.get(4).ch == 27 && s.get(5).ch == '\n' && s.get(6).ch == 127,
				"text input: Ctrl+[ 27, Ctrl+Enter '\\n', Ctrl+Backspace 127, as WM_CHAR gave them " + s);
		for (int k : new int[] {V, LBRACKET, RETURN, BACKSPACE}) key(k, 0, false, false);
		keys();
		key(V, 0, true, false);
		text("v");
		s = keys();
		check(s.size() == 1 && s.get(0).ch == 'v', "text input: V without Ctrl still types its text " + s);
		key(V, 0, false, false);
		Keyboard.enableRepeatEvents(false);
		keys();
		check(org.lwjgl.Sys.getClipboard() != null, "Sys.getClipboard reads SDL's clipboard (contents not shown)");

		// Losing focus releases what's held.
		key(LSHIFT, SDLKeycode.SDL_KMOD_LSHIFT, true, false);
		keys();
		window(SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST);
		s = keys();
		check(!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && s.size() == 1 && s.get(0).key == Keyboard.KEY_LSHIFT && !s.get(0).down,
				"focus lost: held Shift released with an event " + s);
		check(Keyboard.getKeyName(Keyboard.KEY_LSHIFT).equals("LSHIFT") && Keyboard.getKeyName(Keyboard.KEY_SPACE).equals("SPACE")
				&& Keyboard.getKeyIndex("LSHIFT") == Keyboard.KEY_LSHIFT, "key names: LWJGL 2's for non-letters; letters by layout ("
				+ Keyboard.getKeyName(Keyboard.KEY_W) + Keyboard.getKeyName(Keyboard.KEY_A) + Keyboard.getKeyName(Keyboard.KEY_S) + Keyboard.getKeyName(Keyboard.KEY_D) + ")");
		keyNames();
		queueBound();
	}

	/** LWJGL 2's key names, as its Keyboard found them: every public KEY_ constant but the deprecated *WIN ones. */
	private static java.util.Map<String, Integer> legacyKeyNames() throws ReflectiveOperationException {
		java.util.Map<String, Integer> names = new java.util.TreeMap<>();
		for (java.lang.reflect.Field f : Keyboard.class.getFields()) {
			if (f.getType() == int.class && f.getName().startsWith("KEY_") && !f.getName().endsWith("WIN")) names.put(f.getName().substring(4), f.getInt(null));
		}
		return names;
	}

	/** US: SDL's keycodes are the characters keys type unshifted; keys that type none are their scancode with SDL's mask. */
	private static int us(int sc) {
		if (sc >= 4 && sc <= 29) return 'a' + sc - 4;
		if (sc >= 30 && sc <= 38) return '1' + sc - 30;
		String other = "0\r\u001b\b\t -=[]\\#;'`,./";
		if (sc >= 39 && sc <= 56) return other.charAt(sc - 39);
		if (sc == 100) return '\\';
		return SDLKeycode.SDLK_SCANCODE_MASK | sc;
	}

	/** US with the given keys typing the given characters instead. */
	private static java.util.function.IntUnaryOperator with(String chars, int... scancodes) {
		java.util.Map<Integer, Integer> o = new java.util.HashMap<>();
		for (int i = 0; i < scancodes.length; i++) o.put(scancodes[i], (int) chars.charAt(i));
		return sc -> o.getOrDefault(sc, us(sc));
	}

	/** One layout's naming problems: a name not leading back to its key, a key we deliver without one, a duplicate. */
	private static List<String> namesProblems(String[] names, java.util.Map<String, Integer> legacy) {
		List<String> problems = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (int k = 0; k < names.length; k++) {
			if (names[k] == null) continue;
			if (!seen.add(names[k])) problems.add("duplicate " + names[k]);
			int back = KeyMap.index(names, names[k]);
			if (back != k) problems.add(k + " '" + names[k] + "' -> " + back);
		}
		for (int sc = 0; sc < 512; sc++) {
			int k = KeyMap.toLwjgl(sc);
			if (k != 0 && names[k] == null) problems.add("unnamed " + k);
		}
		// LWJGL 2's own names lead to their keys, unless this layout shows that name on another key.
		for (java.util.Map.Entry<String, Integer> e : legacy.entrySet()) {
			int got = KeyMap.index(names, e.getKey());
			if (got != e.getValue() && !e.getKey().equals(names[got])) problems.add("LWJGL 2's " + e.getKey() + " -> " + got);
		}
		return problems;
	}

	/**
	 * getKeyIndex(getKeyName(k)) == k for every key on any layout (mods save keybinds by name), LWJGL 2's own names
	 * accepted too: this system's layout through the API, then synthetic ones through the same naming (KeyMap.names).
	 */
	private static void keyNames() throws ReflectiveOperationException {
		java.util.Map<String, Integer> legacy = legacyKeyNames();
		List<String> problems = new ArrayList<>();
		for (int k = 0; k < Keyboard.KEYBOARD_SIZE; k++) {
			String name = Keyboard.getKeyName(k);
			if (name != null && Keyboard.getKeyIndex(name) != k) problems.add(k + " '" + name + "' -> " + Keyboard.getKeyIndex(name));
		}
		for (java.util.Map.Entry<String, Integer> e : legacy.entrySet()) {
			int got = Keyboard.getKeyIndex(e.getKey());
			if (got != e.getValue() && !e.getKey().equals(Keyboard.getKeyName(got))) problems.add("LWJGL 2's " + e.getKey() + " -> " + got);
		}
		check(problems.isEmpty() && legacy.size() == 131 && "NONE".equals(Keyboard.getKeyName(0)) && Keyboard.getKeyIndex("NONE") == 0
						&& Keyboard.getKeyCount() == 131 && Keyboard.getKeyIndex("no such key") == 0,
				"this layout: every key's name leads back to it; LWJGL 2's 131 names accepted " + problems);

		java.util.function.IntUnaryOperator azerty = with("qazw,m;:!&é\"'(-è_çà)^$ù²*<",
				4, 20, 26, 29, 16, 51, 54, 55, 56, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 45, 47, 48, 52, 53, 49, 100);
		String[] a = KeyMap.names(azerty);
		List<String> ap = namesProblems(a, legacy);
		check(ap.isEmpty() && a[Keyboard.KEY_W].equals("Z") && a[Keyboard.KEY_Q].equals("A") && a[Keyboard.KEY_SEMICOLON].equals("M")
						&& a[Keyboard.KEY_M].equals(",") && a[Keyboard.KEY_2].equals("É") && a[Keyboard.KEY_1].equals("1")
						&& KeyMap.index(a, "Z") == Keyboard.KEY_W && KeyMap.index(a, "SEMICOLON") == Keyboard.KEY_SEMICOLON,
				"AZERTY: forward key 'Z', the key beside L 'M', the key LWJGL 2 calls M ',', all round-trip " + ap);
		String[] q = KeyMap.names(with("zyßü+öä^#-<", 28, 29, 45, 47, 48, 51, 52, 53, 49, 56, 100));
		List<String> qp = namesProblems(q, legacy);
		check(qp.isEmpty() && q[Keyboard.KEY_Y].equals("Z") && q[Keyboard.KEY_Z].equals("Y") && q[Keyboard.KEY_MINUS].equals("SS") && q[86].equals("<"),
				"QWERTZ: Y and Z swapped, ß, the ISO key '<', all round-trip " + qp);
		String usChars = "qwertyuiopasdfghjkl;zxcvbnm,./", dvorakChars = "',.pyfgcrlaoeuidhtns;qjkxbmwvz";
		int[] usScancodes = new int[usChars.length()];
		for (int i = 0; i < usChars.length(); i++) {
			char c = usChars.charAt(i);
			usScancodes[i] = c >= 'a' && c <= 'z' ? 4 + c - 'a' : c == ';' ? 51 : c == ',' ? 54 : c == '.' ? 55 : 56;
		}
		List<String> dp = namesProblems(KeyMap.names(with(dvorakChars, usScancodes)), legacy);
		List<String> legacyOnly = namesProblems(KeyMap.names(null), legacy);
		List<String> allA = namesProblems(KeyMap.names(sc -> 'a'), legacy);
		check(dp.isEmpty() && legacyOnly.isEmpty() && allA.isEmpty(),
				"Dvorak, LWJGL 2's names alone (before video), every key typing 'a': all round-trip " + dp + legacyOnly + allA);
		String pool = "abcdefghijklmnopqrstuvwxyzéßüçжα,.;-<'1234567890";
		int bad = 0;
		String firstBad = "";
		for (int seed = 0; seed < 300; seed++) {
			java.util.Random r = new java.util.Random(seed);
			int[] layout = new int[512];
			for (int sc = 0; sc < 512; sc++) layout[sc] = r.nextInt(4) == 0 ? (SDLKeycode.SDLK_SCANCODE_MASK | sc) : pool.charAt(r.nextInt(pool.length()));
			List<String> p = namesProblems(KeyMap.names(sc -> layout[sc]), legacy);
			if (!p.isEmpty() && bad++ == 0) firstBad = "seed " + seed + ": " + p;
		}
		check(bad == 0, "300 random layouts (letters, accents, Cyrillic, Greek, digits, punctuation, colliding freely): all round-trip " + firstBad);
	}

	/** LWJGL 2 kept at most 250 unread keyboard events (its read buffer of 50 behind a queue of 200) and dropped newer ones. */
	private static void queueBound() {
		final int A = 4, B = 5;
		keys();
		for (int i = 0; i < 150; i++) {
			key(A, 0, true, false);
			key(A, 0, false, false);
		}
		key(B, 0, true, false);
		List<Seen> s = keys();
		check(KeyboardState.CAPACITY == 250 && s.size() == 250 && s.get(0).key == Keyboard.KEY_A && s.get(0).down && !s.get(249).down
						&& Keyboard.isKeyDown(Keyboard.KEY_B) && s.stream().noneMatch(e -> e.key == Keyboard.KEY_B),
				"keyboard queue: of 301 unread events the first 250 kept, newer ones dropped as LWJGL 2 did, key states still followed (got " + s.size() + ")");
		key(B, 0, false, false);
		s = keys();
		check(s.size() == 1 && s.get(0).key == Keyboard.KEY_B && !s.get(0).down, "keyboard queue: events flow again once read " + s);
	}


	private static void window(int type) {
		ev.clear();
		ev.window().type(type).timestamp(SDLTimer.SDL_GetTicksNS()).windowID(win);
		SDLEvents.SDL_PushEvent(ev);
	}

	private static void motion(float x, float y, float xrel, float yrel) {
		motionAt(SDLTimer.SDL_GetTicksNS(), x, y, xrel, yrel);
	}

	private static void motionAt(long nanos, float x, float y, float xrel, float yrel) {
		ev.clear();
		ev.motion().type(SDLEvents.SDL_EVENT_MOUSE_MOTION).timestamp(nanos).windowID(win).x(x).y(y).xrel(xrel).yrel(yrel);
		SDLEvents.SDL_PushEvent(ev);
	}

	private static void button(int b, boolean down) {
		ev.clear();
		ev.button().type(down ? SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN : SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP).timestamp(SDLTimer.SDL_GetTicksNS())
				.windowID(win).button((byte) b).down(down);
		SDLEvents.SDL_PushEvent(ev);
	}

	private static void wheel(float y) {
		ev.clear();
		ev.wheel().type(SDLEvents.SDL_EVENT_MOUSE_WHEEL).timestamp(SDLTimer.SDL_GetTicksNS()).windowID(win).y(y);
		SDLEvents.SDL_PushEvent(ev);
	}

	private static List<int[]> mouseEvents() {
		Display.processMessages();
		List<int[]> seen = new ArrayList<>();
		while (Mouse.next()) {
			seen.add(new int[] {Mouse.getEventButton(), Mouse.getEventButtonState() ? 1 : 0, Mouse.getEventDX(), Mouse.getEventDY(),
					Mouse.getEventDWheel(), Mouse.getEventX(), Mouse.getEventY()});
		}
		return seen;
	}

	private static String str(List<int[]> l) {
		StringBuilder b = new StringBuilder("[");
		for (int[] e : l) b.append(java.util.Arrays.toString(e));
		return b.append("] (button, state, dx, dy, dwheel, x, y)").toString();
	}

	private static void mouse() {
		// Grabbed motion as LWJGL 2 saw it on Windows: the pointer's, after the system's speed and acceleration, which
		// SDL applies to raw input with this hint (tools/mouse_ballistics.py shows the difference it makes).
		check(!org.studioquire.oldfaithful.lwjgl.Settings.rawMouse
						&& "1".equals(org.lwjgl.sdl.SDLHints.SDL_GetHint(org.lwjgl.sdl.SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE)),
				"mouse: the system's pointer speed and acceleration by default (SDL's relative system scale on)");
		mouseEvents();
		Mouse.getDX();
		Mouse.getDY();
		int h = Display.getHeight();
		motion(100, 50, 0, 0);
		mouseEvents();
		check(Mouse.getX() == 100 && Mouse.getY() == h - 1 - 50, "position in pixels from the bottom-left: " + Mouse.getX() + "," + Mouse.getY());
		Mouse.getDX();
		Mouse.getDY();
		motion(110, 40, 10, -10);
		motion(130, 30, 20, -10);
		List<int[]> e = mouseEvents();
		check(e.size() == 1 && e.get(0)[2] == 30 && e.get(0)[3] == 20 && e.get(0)[5] == 130, "consecutive motion merges into one event: " + str(e));
		check(Mouse.getDX() == 30 && Mouse.getDY() == 20 && Mouse.getDX() == 0, "getDX/getDY accumulate, then reset when read");

		button(1, true);
		button(3, true);
		button(2, true);
		e = mouseEvents();
		check(e.size() == 3 && e.get(0)[0] == 0 && e.get(1)[0] == 1 && e.get(2)[0] == 2 && Mouse.isButtonDown(1),
				"buttons: left 0, right 1, middle 2 (LWJGL 2's numbering) " + str(e));
		button(1, false);
		button(3, false);
		button(2, false);
		mouseEvents();

		wheel(1f);
		wheel(0.5f);
		wheel(0.5f);
		wheel(-2f);
		e = mouseEvents();
		check(e.size() == 4 && e.get(0)[4] == 120 && e.get(1)[4] == 60 && e.get(3)[4] == -240 && Mouse.getDWheel() == 0,
				"wheel: 120 per notch, half notches kept, getDWheel sums " + str(e));

		Mouse.setGrabbed(true);
		check(Mouse.isGrabbed(), "grabbed");
		int x0 = Mouse.getX(), y0 = Mouse.getY();
		motion(0, 0, 5, 7);
		motion(0, 0, 3, -2);
		e = mouseEvents();
		check(Mouse.getDX() == 8 && Mouse.getDY() == -5 && Mouse.getX() == x0 + 8 && Mouse.getY() == y0 - 5,
				"grabbed: deltas from relative motion (y up), position follows them " + str(e));
		// The frame cut-off (MouseState.frameStart): grabbed motion stamped after the frame began, read by a later pump,
		// counts in the next frame; motion before it counts now. Unrounded deltas ride along (sub-pixel look).
		long early = SDLTimer.SDL_GetTicksNS();
		MouseState.frameStart();
		long late = SDLTimer.SDL_GetTicksNS() + 1;
		motionAt(early, 0, 0, 4.25f, 0);
		motionAt(late, 0, 0, 6, 1);
		mouseEvents();
		int first = Mouse.getDX(), firstY = Mouse.getDY();
		float firstF = MouseState.takenFX();
		MouseState.frameStart();
		int second = Mouse.getDX(), secondY = Mouse.getDY();
		check(first == 4 && firstY == 0 && Math.abs(firstF - 4.25f) < 1e-4 && second == 6 && secondY == -1,
				"frame cut-off: motion before it this frame (" + first + ", unrounded " + firstF + "), after it the next (" + second + "," + secondY + ")");
		motionAt(SDLTimer.SDL_GetTicksNS() + 1, 0, 0, 0.4f, 0);
		motionAt(SDLTimer.SDL_GetTicksNS() + 1, 0, 0, 0.4f, 0);
		mouseEvents();
		MouseState.frameStart();
		int whole = Mouse.getDX();
		Mouse.getDY();
		check(whole == 1 && Math.abs(MouseState.takenFX() - 0.8f) < 1e-4,
				"fractions: whole pixels " + whole + " (0.25 carried + 0.8), unrounded " + MouseState.takenFX());
		MouseState.clearCutoff();
		motion(0, 0, 100000, 0);
		mouseEvents();
		check(Mouse.getX() == Display.getWidth() - 1, "grabbed position stays in the window: x " + Mouse.getX());
		Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
		Mouse.setGrabbed(false);
		check(!Mouse.isGrabbed() && Mouse.getX() == Display.getWidth() / 2, "released at the position set while grabbed: " + Mouse.getX());
		button(1, true);
		mouseEvents();
		window(SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST);
		e = mouseEvents();
		check(!Mouse.isButtonDown(0) && e.size() == 1 && e.get(0)[0] == 0 && e.get(0)[1] == 0, "focus lost: held button released " + str(e));
	}

	// ---- sound ------------------------------------------------------------------------------------------------------

	/**
	 * paulscode as b1.7.3's SoundManager sets it up, OpenAL Soft writing to a wave file (ALSOFT_DRIVERS=wave and a
	 * config naming the file, set by the task). Plays a real game sound and a streamed music track, moves the device
	 * to the default output as on a device change, and checks the file has sound in it.
	 */
	private static void sound(String waveFile, String soundsDir) throws Exception {
		check("wave".equals(System.getenv("ALSOFT_DRIVERS")), "OpenAL Soft's wave writer selected");
		paulscode.sound.SoundSystemConfig.addLibrary(paulscode.sound.libraries.LibraryLWJGLOpenAL.class);
		paulscode.sound.SoundSystemConfig.setCodec("ogg", paulscode.sound.codecs.CodecJOrbis.class);
		paulscode.sound.SoundSystem system = new paulscode.sound.SoundSystem();
		try {
			check(AL.isCreated(), "AL.create through paulscode's LibraryLWJGLOpenAL");
			String alVersion = AL10.alGetString(AL10.AL_VERSION);
			check(alVersion != null && alVersion.contains("ALSOFT 1.25.2"), "our OpenAL Soft: " + alVersion);
			File bow = new File(soundsDir, "newsound/random/bow.ogg");
			File music = new File(soundsDir, "music/calm1.ogg");
			system.setListenerPosition(0f, 0f, 0f);
			system.setListenerOrientation(0f, 0f, -1f, 0f, 1f, 0f);
			system.newSource(false, "bow", bow.toURI().toURL(), "bow.ogg", false, 1f, 0f, 0f, 2, 16f);
			system.setVolume("bow", 1f);
			system.play("bow");
			system.backgroundMusic("music", music.toURI().toURL(), "calm1.ogg", false);
			Thread.sleep(1500);
			java.lang.reflect.Method reopen = Class.forName("org.lwjgl.openal.LegacyAL").getDeclaredMethod("reopen");
			reopen.setAccessible(true);
			check((Boolean) reopen.invoke(null), "device follows a default-output change (reopened on the fly)");
			// The wave writer starts its file afresh on a reopen, so what's checked below is played after it.
			system.play("bow");
			Thread.sleep(1500);
			check(system.playing("music"), "music still streaming after the reopen");
			check(AL10.alGetError() == AL10.AL_NO_ERROR, "no AL errors");
		} finally {
			system.cleanup();
		}
		check(!AL.isCreated(), "AL destroyed by paulscode's cleanup");
		byte[] wav = Files.readAllBytes(new File(waveFile).toPath());
		// The task's config asks for 16-bit samples; find the data chunk after whatever header the writer used.
		int data = -1;
		for (int i = 12; i + 8 <= wav.length; ) {
			int size = ByteBuffer.wrap(wav, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
			if (wav[i] == 'd' && wav[i + 1] == 'a' && wav[i + 2] == 't' && wav[i + 3] == 'a') {
				data = i + 8;
				break;
			}
			i += 8 + size + (size & 1);
		}
		check(data > 0 && wav.length - data > 44100, "wave file written: " + wav.length + " bytes, samples from byte " + data);
		long sum = 0;
		int peak = 0;
		ByteBuffer pcm = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
		int samples = 0;
		for (int i = Math.max(data, 44); i + 1 < wav.length; i += 2, samples++) {
			int s = pcm.getShort(i);
			sum += (long) s * s;
			peak = Math.max(peak, Math.abs(s));
		}
		double rms = Math.sqrt((double) sum / Math.max(1, samples));
		check(rms > 100 && peak > 2000, "the file holds sound: RMS " + Math.round(rms) + ", peak " + peak);
	}
}
