package org.studioquire.oldfaithful.lwjgl;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLKeycode;

/**
 * Between SDL's scancodes (USB HID usages: physical key positions) and LWJGL 2's key codes (DirectInput's, also
 * physical positions on a US layout). Keys bind by position, so W/A/S/D are the same keys on any layout; names come
 * from the active layout where the key types a letter (an AZERTY player sees "Z" for the forward key), and from
 * LWJGL 2's own names otherwise, unique on every layout so mods that save keys by name read them back ({@link #names}).
 */
public final class KeyMap {
	private static final int[] TO_LWJGL = new int[512];
	private static final int[] TO_SDL = new int[256];
	/** LWJGL 2's key names (its KEY_ constants without the prefix), by code. */
	private static final String[] NAMES = new String[256];

	private KeyMap() {
	}

	private static void map(int sdl, int lwjgl, String name) {
		TO_LWJGL[sdl] = lwjgl;
		if (TO_SDL[lwjgl] == 0) TO_SDL[lwjgl] = sdl;
		if (name != null && NAMES[lwjgl] == null) NAMES[lwjgl] = name;
	}

	static {
		// Letters, by their place on a US keyboard (SDL A..Z are 4..29).
		String qwerty = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		int[] letterCodes = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
		for (int i = 0; i < 26; i++) map(4 + i, letterCodes[i], String.valueOf(qwerty.charAt(i)));
		// 1..9, 0 (SDL 30..39; LWJGL 2..11).
		for (int i = 0; i < 10; i++) map(30 + i, 2 + i, String.valueOf((i + 1) % 10));
		map(40, 28, "RETURN");
		map(41, 1, "ESCAPE");
		map(42, 14, "BACK");
		map(43, 15, "TAB");
		map(44, 57, "SPACE");
		map(45, 12, "MINUS");
		map(46, 13, "EQUALS");
		map(47, 26, "LBRACKET");
		map(48, 27, "RBRACKET");
		map(49, 43, "BACKSLASH");
		map(50, 43, null); // non-US # (ISO, left of Enter): Windows reports it as the backslash key
		map(51, 39, "SEMICOLON");
		map(52, 40, "APOSTROPHE");
		map(53, 41, "GRAVE");
		map(54, 51, "COMMA");
		map(55, 52, "PERIOD");
		map(56, 53, "SLASH");
		map(57, 58, "CAPITAL");
		for (int i = 0; i < 10; i++) map(58 + i, 59 + i, "F" + (i + 1)); // F1..F10
		map(68, 87, "F11");
		map(69, 88, "F12");
		map(70, 183, "SYSRQ");
		map(71, 70, "SCROLL");
		map(72, 197, "PAUSE");
		map(73, 210, "INSERT");
		map(74, 199, "HOME");
		map(75, 201, "PRIOR");
		map(76, 211, "DELETE");
		map(77, 207, "END");
		map(78, 209, "NEXT");
		map(79, 205, "RIGHT");
		map(80, 203, "LEFT");
		map(81, 208, "DOWN");
		map(82, 200, "UP");
		map(83, 69, "NUMLOCK");
		map(84, 181, "DIVIDE");
		map(85, 55, "MULTIPLY");
		map(86, 74, "SUBTRACT");
		map(87, 78, "ADD");
		map(88, 156, "NUMPADENTER");
		int[] numpad = {79, 80, 81, 75, 76, 77, 71, 72, 73, 82}; // KP_1..KP_9, KP_0
		for (int i = 0; i < 10; i++) map(89 + i, numpad[i], "NUMPAD" + ((i + 1) % 10));
		map(99, 83, "DECIMAL");
		map(100, 86, null); // the extra ISO key left of Z (DirectInput's OEM_102; LWJGL 2 has no name for it)
		map(101, 221, "APPS");
		map(102, 222, "POWER");
		map(103, 141, "NUMPADEQUALS");
		int[] f13 = {100, 101, 102, 103, 104, 105, 113}; // F13..F19
		for (int i = 0; i < f13.length; i++) map(104 + i, f13[i], "F" + (13 + i));
		map(133, 179, "NUMPADCOMMA");
		map(135, 115, null); // International1 (ABNT C1 / Ro)
		map(136, 112, "KANA");
		map(137, 125, "YEN");
		map(138, 121, "CONVERT");
		map(139, 123, "NOCONVERT");
		map(156, 218, "CLEAR");
		map(224, 29, "LCONTROL");
		map(225, 42, "LSHIFT");
		map(226, 56, "LMENU");
		map(227, 219, "LMETA");
		map(228, 157, "RCONTROL");
		map(229, 54, "RSHIFT");
		map(230, 184, "RMENU");
		map(231, 220, "RMETA");
		map(258, 223, "SLEEP");
		// LWJGL 2 names without an SDL key of their own.
		String[][] extra = {{"144", "CIRCUMFLEX"}, {"145", "AT"}, {"146", "COLON"}, {"147", "UNDERLINE"}, {"148", "KANJI"},
				{"149", "STOP"}, {"150", "AX"}, {"151", "UNLABELED"}, {"167", "SECTION"}, {"196", "FUNCTION"}};
		for (String[] e : extra) if (NAMES[Integer.parseInt(e[0])] == null) NAMES[Integer.parseInt(e[0])] = e[1];
		NAMES[0] = "NONE";
	}

	/** LWJGL 2's code for an SDL scancode; 0 (KEY_NONE) for keys LWJGL 2 never had. */
	public static int toLwjgl(int scancode) {
		return scancode >= 0 && scancode < TO_LWJGL.length ? TO_LWJGL[scancode] : 0;
	}

	public static int toScancode(int lwjgl) {
		return lwjgl >= 0 && lwjgl < TO_SDL.length ? TO_SDL[lwjgl] : 0;
	}

	/** LWJGL 2's name for a code, as its KEY_ constants spell it; null if it has none. */
	public static String legacyName(int lwjgl) {
		return lwjgl >= 0 && lwjgl < NAMES.length ? NAMES[lwjgl] : null;
	}

	/** The code for an LWJGL 2 name (its KEY_ constants without the prefix, "NONE" too), or 0. */
	public static int fromLegacyName(String name) {
		for (int i = 0; i < NAMES.length; i++) if (name.equals(NAMES[i])) return i;
		return 0;
	}

	/** Names on the current layout; null until built, and again when SDL reports a layout change. */
	private static volatile String[] current;
	private static volatile boolean currentHasLayout;

	/** The layout changed (SDL_EVENT_KEYMAP_CHANGED): names are rebuilt when next asked for. */
	public static void layoutChanged() {
		current = null;
	}

	private static String[] current() {
		boolean layout = Sdl.videoReady();
		String[] names = current;
		if (names == null || currentHasLayout != layout) {
			names = names(layout ? sc -> SDLKeyboard.SDL_GetKeyFromScancode(sc, (short) SDLKeycode.SDL_KMOD_NONE, false) : null);
			currentHasLayout = layout;
			current = names;
		}
		return names;
	}

	/** The name shown for a key on the current layout (see {@link #names}); null for codes no key has. */
	public static String displayName(int lwjgl) {
		return lwjgl >= 0 && lwjgl < NAMES.length ? current()[lwjgl] : null;
	}

	/** The code for a name {@link #displayName} gives, or else for one of LWJGL 2's names; 0 if neither. */
	public static int fromName(String name) {
		return index(current(), name);
	}

	/** {@link #fromName} against a given table of names. */
	public static int index(String[] names, String name) {
		if (name == null) return 0;
		for (int i = 0; i < names.length; i++) if (name.equals(names[i])) return i;
		return fromLegacyName(name);
	}

	/**
	 * Every code's name on a layout ({@code layout}: an SDL scancode to the keycode it types unshifted; null before
	 * SDL's video is up, giving LWJGL 2's names), each name unique, so a name always leads back to its key: keys that
	 * type a letter are named by it (an AZERTY player sees "Z" on the forward key); other keys keep LWJGL 2's name
	 * unless the layout gave it to a letter key (AZERTY's "M" is beside L, so the key LWJGL 2 calls M is named by the
	 * ',' it types); keys still without a name are named by what they type, or "KEY" and their code.
	 */
	public static String[] names(IntUnaryOperator layout) {
		String[] names = new String[NAMES.length];
		Set<String> taken = new HashSet<>();
		if (layout != null) {
			for (int code = 1; code < names.length; code++) {
				String letter = TO_SDL[code] != 0 ? letter(layout.applyAsInt(TO_SDL[code])) : null;
				if (letter != null && taken.add(letter)) names[code] = letter;
			}
		}
		for (int code = 0; code < names.length; code++) {
			if (names[code] == null && NAMES[code] != null && taken.add(NAMES[code])) names[code] = NAMES[code];
		}
		for (int code = 1; code < names.length; code++) {
			if (names[code] != null || (TO_SDL[code] == 0 && NAMES[code] == null)) continue;
			String typed = layout != null && TO_SDL[code] != 0 ? printable(layout.applyAsInt(TO_SDL[code])) : null;
			names[code] = typed != null && taken.add(typed) ? typed : "KEY" + code;
			taken.add(names[code]);
		}
		return names;
	}

	private static String letter(int key) {
		if (key <= 0 || key >= SDLKeycode.SDLK_SCANCODE_MASK || !Character.isLetter(key)) return null;
		return new String(Character.toChars(key)).toUpperCase(Locale.ROOT);
	}

	private static String printable(int key) {
		if (key <= ' ' || key >= SDLKeycode.SDLK_SCANCODE_MASK || !Character.isDefined(key) || Character.isISOControl(key)
				|| Character.isWhitespace(key)) {
			return null;
		}
		return new String(Character.toChars(key)).toUpperCase(Locale.ROOT);
	}

	/** Keypad keys type these whatever the layout (digits and '.' only with Num Lock on). */
	static char keypadChar(int keycode, boolean numLock) {
		switch (keycode) {
			case SDLKeycode.SDLK_KP_DIVIDE: return '/';
			case SDLKeycode.SDLK_KP_MULTIPLY: return '*';
			case SDLKeycode.SDLK_KP_MINUS: return '-';
			case SDLKeycode.SDLK_KP_PLUS: return '+';
			case SDLKeycode.SDLK_KP_ENTER: return '\r';
			case SDLKeycode.SDLK_KP_EQUALS: return '=';
			case SDLKeycode.SDLK_KP_PERIOD: return numLock ? '.' : 0;
			case SDLKeycode.SDLK_KP_0: return numLock ? '0' : 0;
			default:
				if (keycode >= SDLKeycode.SDLK_KP_1 && keycode <= SDLKeycode.SDLK_KP_9) return numLock ? (char) ('1' + keycode - SDLKeycode.SDLK_KP_1) : 0;
				return 0;
		}
	}
}
