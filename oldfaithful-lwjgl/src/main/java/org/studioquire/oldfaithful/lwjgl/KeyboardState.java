package org.studioquire.oldfaithful.lwjgl;

import java.util.ArrayDeque;

import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLKeycode;

/**
 * The state behind {@link org.lwjgl.input.Keyboard}: key states and LWJGL 2's event queue, fed from SDL's events
 * by {@link Window#pump()} on the game's thread.
 * <p>
 * LWJGL 2 gave each key press the character it typed, in the same event. SDL reports the key, then (when text input
 * is on) the text separately; the two are joined here when they arrive together, and text without a key press (an
 * IME's composition, a dead key's result) becomes its own event with key 0, as LWJGL 2 delivered WM_CHAR alone.
 * Text input (and with it the system's IME) is on only while repeat events are, which is what b1.7.3's text fields
 * (chat, signs, world and server names) switch on; elsewhere characters come from the layout's key map, without an
 * IME in the way of movement keys.
 */
public final class KeyboardState {
	public static final class Event {
		public int key;
		public char character;
		public boolean down;
		public boolean repeat;
		public long nanos;
	}

	/**
	 * Unread events kept at most, as LWJGL 2 kept them: its Keyboard's read buffer (50 events) behind its window's
	 * event queue (200, EventQueue), each refusing new events when full. Key states follow every event regardless.
	 */
	public static final int CAPACITY = 50 + 200;

	public static boolean created;
	private static boolean repeatEnabled;
	private static final boolean[] keyDown = new boolean[256];
	private static final ArrayDeque<Event> queue = new ArrayDeque<>();
	private static Event current = new Event();
	/** A key press from this pump still waiting for the text it typed. */
	private static Event awaitingText;

	private KeyboardState() {
	}

	public static void key(int scancode, short mod, boolean down, boolean repeat, long nanos, boolean textInput) {
		int code = KeyMap.toLwjgl(scancode);
		if (repeat && !repeatEnabled) return;
		if (code != 0) keyDown[code] = down;
		Event e = new Event();
		e.key = code;
		e.down = down;
		e.repeat = repeat;
		e.nanos = nanos;
		if (down) e.character = character(scancode, mod, textInput);
		awaitingText = add(e) && down && e.character == 0 && textInput ? e : null;
	}

	public static void text(String text, long nanos) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (awaitingText != null) {
				awaitingText.character = c;
				awaitingText = null;
				continue;
			}
			Event e = new Event();
			e.character = c;
			e.down = true;
			e.nanos = nanos;
			add(e);
		}
	}

	/** Text belongs to the key press it follows only within one pump of events. */
	public static void endOfPump() {
		awaitingText = null;
	}

	/** Focus lost: any key still down is released, with events, so nothing stays held while away. */
	public static void releaseAll(long nanos) {
		for (int code = 1; code < keyDown.length; code++) {
			if (!keyDown[code]) continue;
			keyDown[code] = false;
			Event e = new Event();
			e.key = code;
			e.nanos = nanos;
			add(e);
		}
	}

	/** Queues an event unless the queue is full, in which case it's dropped, as LWJGL 2 dropped it. */
	private static boolean add(Event e) {
		if (queue.size() >= CAPACITY) return false;
		queue.add(e);
		return true;
	}

	/**
	 * What LWJGL 2 put in getEventCharacter for a key press, as Windows' WM_CHAR gave it: Enter '\r' (with Ctrl a line
	 * feed), Backspace '\b' (with Ctrl DEL), Tab, Escape; Ctrl with a letter or one of @ [ \ ] ^ _ its control code
	 * (Ctrl+V is 22, which b1.7.3's text fields paste on), with text input on or off, since SDL never sends control
	 * characters as text; the keypad's digits with Num Lock on; other printable keys their layout's character unless
	 * text input is on, in which case the character comes with SDL's text event.
	 */
	static char character(int scancode, short mod, boolean textInput) {
		int key = SDLKeyboard.SDL_GetKeyFromScancode(scancode, mod, false);
		// Ctrl+Alt is AltGr on Windows, which types the layout's third-level characters, never control codes.
		boolean ctrl = (mod & SDLKeycode.SDL_KMOD_CTRL) != 0 && (mod & (SDLKeycode.SDL_KMOD_ALT | SDLKeycode.SDL_KMOD_MODE)) == 0;
		char keypad = KeyMap.keypadChar(key, (mod & SDLKeycode.SDL_KMOD_NUM) != 0);
		if (key == '\r' || keypad == '\r') return ctrl ? '\n' : '\r';
		if (key == '\b') return ctrl ? (char) 127 : '\b';
		if (key == '\t' || key == 27) return (char) key;
		if (ctrl) return controlCode(key);
		if (textInput) return 0;
		if (keypad != 0) return keypad;
		if (key < 32 || key == 127 || key >= SDLKeycode.SDLK_SCANCODE_MASK || key > 0xFFFF) return 0;
		return (char) key;
	}

	/** Ctrl+A..Z are 1..26; Ctrl+[ \ ] ^ _ are 27..31 (Ctrl+@ is 0: none); nothing else has one. */
	static char controlCode(int key) {
		if (key >= 'a' && key <= 'z') return (char) (key - 'a' + 1);
		if (key >= '@' && key <= '_') return (char) (key - '@');
		return 0;
	}

	public static boolean isKeyDown(int code) {
		return code > 0 && code < keyDown.length && keyDown[code];
	}

	public static boolean next() {
		Event e = queue.poll();
		if (e == null) return false;
		current = e;
		return true;
	}

	public static Event current() {
		return current;
	}

	public static int pending() {
		return queue.size();
	}

	public static void setRepeat(boolean enabled) {
		repeatEnabled = enabled;
		Window.textInput(enabled);
	}

	public static boolean repeatEnabled() {
		return repeatEnabled;
	}

	public static void reset() {
		queue.clear();
		java.util.Arrays.fill(keyDown, false);
		current = new Event();
		awaitingText = null;
	}
}
