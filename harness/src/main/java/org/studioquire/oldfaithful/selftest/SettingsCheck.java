package org.studioquire.oldfaithful.selftest;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.opengl.Display;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLVideo;
import org.studioquire.oldfaithful.lwjgl.Settings;
import org.studioquire.oldfaithful.lwjgl.Window;
import org.studioquire.oldfaithful.selftest.mixin.ButtonWidgetAccessor;
import org.studioquire.oldfaithful.selftest.mixin.ScreenAccessor;

/**
 * Old Faithful's settings screen (oldfaithful-options, docs/options.md > The settings screen), from the start-up test
 * in the running world: "Old Faithful..." in Options with beta's own buttons where they were; the groups the installed
 * mods give; every label fitting its button and every hint its two lines; clicks that write the config files (and
 * nothing else in them), apply at once where they should (vsync, sub-pixel look, the mouse, lava light) and mark a
 * restart where they can't; in multiplayer, the world's switches shown as the server's and not pressable. Then the
 * screens drawn at GUI scales Small to Large in a small, a middling and a large window, checked for overlaps and
 * saved as pictures (settings-shots/ in the run folder) for looking at. The options mod's classes are reached by name,
 * so the harness builds without it.
 */
final class SettingsCheck {
	/** The settings button's label: the edition's name (Old Faithful's, or another edition's run names its own). */
	private static final String BUTTON = (StartupCheck.EDITION != null ? StartupCheck.EDITION.split(":")[0] : "Old Faithful") + "...";

	private static final String PKG = "org.studioquire.oldfaithful.options.settings.";
	private static final int[][] WINDOWS = {{640, 480}, {854, 480}, {1920, 1080}};

	private interface Step {
		/** True when done. */
		boolean run(Minecraft mc) throws Exception;
	}

	private final SelfTest results;
	private final Deque<Step> steps = new ArrayDeque<>();
	private final Path config = FabricLoader.getInstance().getConfigDir();
	private int startWidth, startHeight, startScale;
	private final List<String> layoutProblems = new ArrayList<>();
	private int layoutsChecked;

	SettingsCheck(SelfTest results) {
		this.results = results;
	}

	static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded("oldfaithful-options");
	}

	// ---- helpers ------------------------------------------------------------------------------------------------

	private static List<ButtonWidget> buttons(Screen s) {
		return ((ScreenAccessor) s).oldfaithful$buttons();
	}

	private static ButtonWidget button(Screen s, String textStart) {
		for (ButtonWidget b : buttons(s)) {
			if (b.text.startsWith(textStart)) return b;
		}
		throw new IllegalStateException("no button \"" + textStart + "...\" on " + s.getClass().getSimpleName() + ": " + texts(s));
	}

	private static List<String> texts(Screen s) {
		List<String> t = new ArrayList<>();
		for (ButtonWidget b : buttons(s)) t.add(b.text);
		return t;
	}

	private static void press(Screen s, ButtonWidget b) {
		((ScreenAccessor) s).oldfaithful$press(b);
	}

	private String file(String name) throws Exception {
		return new String(Files.readAllBytes(config.resolve(name)), StandardCharsets.ISO_8859_1);
	}

	private static Object call(Object target, Class<?> type, String method, Object... args) throws ReflectiveOperationException {
		for (Method m : type.getDeclaredMethods()) {
			if (m.getName().equals(method) && m.getParameterCount() == args.length) {
				m.setAccessible(true);
				return m.invoke(target, args);
			}
		}
		throw new NoSuchMethodException(type.getName() + "." + method);
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(target);
	}

	/** The screen opened from Options: the top of the settings. */
	private Screen openSettings(Minecraft mc) {
		OptionsScreen options = new OptionsScreen(null, mc.options);
		mc.setScreen(options);
		press(options, button(options, BUTTON));
		return mc.currentScreen;
	}

	private Screen openGroup(Minecraft mc, String title) {
		Screen top = openSettings(mc);
		press(top, button(top, title + "..."));
		return mc.currentScreen;
	}

	/** Every button inside the screen, none over another, every label inside its button. */
	private void layout(Minecraft mc, Screen s, String where) {
		layoutsChecked++;
		List<ButtonWidget> bs = buttons(s);
		for (int i = 0; i < bs.size(); i++) {
			ButtonWidget a = bs.get(i);
			int aw = ((ButtonWidgetAccessor) a).oldfaithful$width(), ah = ((ButtonWidgetAccessor) a).oldfaithful$height();
			if (a.x < 0 || a.y < 0 || a.x + aw > s.width || a.y + ah > s.height) {
				layoutProblems.add(where + ": \"" + a.text + "\" outside the " + s.width + "x" + s.height + " screen at " + a.x + "," + a.y);
			}
			if (mc.textRenderer.getWidth(a.text) > aw - 6) layoutProblems.add(where + ": \"" + a.text + "\" wider than its button");
			for (int j = i + 1; j < bs.size(); j++) {
				ButtonWidget b = bs.get(j);
				int bw = ((ButtonWidgetAccessor) b).oldfaithful$width(), bh = ((ButtonWidgetAccessor) b).oldfaithful$height();
				if (a.x < b.x + bw && b.x < a.x + aw && a.y < b.y + bh && b.y < a.y + ah) {
					layoutProblems.add(where + ": \"" + a.text + "\" over \"" + b.text + "\"");
				}
			}
		}
	}

	// ---- the checks -----------------------------------------------------------------------------------------------

	void start(Minecraft mc) {
		startWidth = Display.getWidth();
		startHeight = Display.getHeight();
		startScale = mc.options.guiScale;
		steps.add(this::optionsScreen);
		steps.add(this::groupsAndWords);
		steps.add(this::restartSwitch);
		steps.add(this::liveSwitches);
		steps.add(this::multiplayer);
		for (int[] size : WINDOWS) {
			steps.add(resize(size[0], size[1]));
			steps.add(wait(5));
			for (int scale = 1; scale <= 3; scale++) {
				int guiScale = scale;
				String at = size[0] + "x" + size[1] + "-gui" + scale;
				steps.add(m -> {
					m.options.guiScale = guiScale;
					m.setScreen(new OptionsScreen(null, m.options));
					layout(m, m.currentScreen, at + " Options");
					return true;
				});
				steps.add(capture(at + "-options", null));
				steps.add(m -> {
					layout(m, openSettings(m), at + " Old Faithful");
					return true;
				});
				steps.add(capture(at + "-top", null));
				steps.add(m -> {
					layout(m, openGroup(m, "Window and Mouse"), at + " Window and Mouse");
					return true;
				});
				// Pointing at the first button, so its hint shows.
				steps.add(capture(at + "-window", "VSync"));
				steps.add(m -> {
					layout(m, openGroup(m, "Blocks and Items"), at + " Blocks and Items");
					return true;
				});
				steps.add(capture(at + "-blocks", null));
			}
		}
		// A change waiting for a restart, pointed at.
		steps.add(resize(854, 480));
		steps.add(wait(5));
		steps.add(m -> {
			m.options.guiScale = 2;
			Screen redstone = openGroup(m, "Redstone and Lighting");
			press(redstone, button(redstone, "Repeaters:"));
			return true;
		});
		steps.add(capture("854x480-gui2-redstone-waiting", "Repeaters"));
		steps.add(m -> {
			press(m.currentScreen, button(m.currentScreen, "Repeaters:"));
			results.check("layouts: no button outside its screen, over another, or with its label overflowing, in " + layoutsChecked
					+ " screens at GUI scales Small to Large in 640x480, 854x480 and 1920x1080 (pictures in settings-shots/)"
					+ (layoutProblems.isEmpty() ? "" : ": " + layoutProblems), layoutProblems.isEmpty());
			return true;
		});
		steps.add(resize(startWidth, startHeight));
		steps.add(wait(5));
		steps.add(m -> {
			m.options.guiScale = startScale;
			m.options.save();
			m.setScreen(null);
			return true;
		});
	}

	/** True when done. */
	boolean tick(Minecraft mc) throws Exception {
		while (!steps.isEmpty()) {
			if (!steps.peek().run(mc)) return false;
			steps.poll();
		}
		return true;
	}

	private static Step wait(int ticks) {
		int[] left = {ticks};
		return m -> --left[0] <= 0;
	}

	private static Step resize(int w, int h) {
		return m -> {
			SDLVideo.SDL_RestoreWindow(Window.handle());
			SDLVideo.SDL_SetWindowSize(Window.handle(), w, h);
			return true;
		};
	}

	/** The current screen's next frame saved, with the mouse on the button whose label starts so (or nowhere). */
	private static Step capture(String name, String pointAt) {
		ScreenCapture[] c = {null};
		return m -> {
			if (c[0] == null) {
				int x = -1, y = -1;
				if (pointAt != null) {
					ButtonWidget b = button(m.currentScreen, pointAt);
					x = b.x + 4;
					y = b.y + 4;
				}
				c[0] = ScreenCapture.request(new File("settings-shots/" + name + ".png"), x, y);
				return false;
			}
			return c[0].done();
		};
	}

	private boolean optionsScreen(Minecraft mc) {
		OptionsScreen options = new OptionsScreen(null, mc.options);
		mc.setScreen(options);
		int w = options.width, h = options.height;
		ButtonWidget ours = button(options, BUTTON);
		ButtonWidget video = null, controls = null, done = null;
		for (ButtonWidget b : buttons(options)) {
			if (b.id == 101) video = b;
			if (b.id == 100) controls = b;
			if (b.id == 200) done = b;
		}
		boolean betasInPlace = video != null && controls != null && done != null
				&& video.x == w / 2 - 100 && video.y == h / 6 + 108 && controls.x == w / 2 - 100 && controls.y == h / 6 + 132
				&& done.x == w / 2 - 100 && done.y == h / 6 + 168;
		for (int i = 0; i < 5; i++) {
			ButtonWidget b = buttons(options).get(i);
			betasInPlace &= b.x == w / 2 - 155 + i % 2 * 160 && b.y == h / 6 + 24 * (i >> 1);
		}
		results.check("Options: \"" + BUTTON + "\" above Video Settings on their pitch (" + ours.x + "," + ours.y + "), beta's buttons where beta put them",
				ours.x == w / 2 - 100 && ours.y == h / 6 + 84 && ((ButtonWidgetAccessor) ours).oldfaithful$width() == 200 && betasInPlace);
		press(options, ours);
		results.check("\"" + BUTTON + "\" opens the settings screen (" + mc.currentScreen.getClass().getSimpleName() + ")",
				mc.currentScreen.getClass().getName().equals(PKG + "SettingsScreen"));
		return true;
	}

	/** The groups the installed mods give, and every label and hint fitting its place. */
	private boolean groupsAndWords(Minecraft mc) throws Exception {
		FabricLoader loader = FabricLoader.getInstance();
		List<String> expected = new ArrayList<>();
		if (loader.isModLoaded("oldfaithful-fixes")) expected.addAll(java.util.Arrays.asList("Blocks and Items...", "Mobs and Vehicles..."));
		if (loader.isModLoaded("oldfaithful-fixes") || loader.isModLoaded("oldfaithful-lighting")) expected.add("Redstone and Lighting...");
		if (loader.isModLoaded("oldfaithful-fixes")) expected.add("Looks and Sounds...");
		if (loader.isModLoaded("stillfaithful-polish")) expected.add("Polish...");
		if (loader.isModLoaded("oldfaithful-fixes")) expected.add("Multiplayer...");
		expected.add(loader.isModLoaded("purefaithful-display") ? "Window and Mouse..." : "Game...");
		expected.add("Done");
		Screen top = openSettings(mc);
		results.check("settings: the groups of the installed mods, in order: " + texts(top), texts(top).equals(expected));

		Class<?> registryType = Class.forName(PKG + "SettingsRegistry"), screenType = Class.forName(PKG + "SettingsScreen");
		Object registry = registryType.getMethod("get").invoke(null);
		List<?> groups = (List<?>) call(registry, registryType, "groups");
		List<String> problems = new ArrayList<>();
		int settings = 0;
		for (Object g : groups) {
			Object note = field(g, "note");
			if (note != null && ((List<?>) call(top, screenType, "wrap", note, 300)).size() > 2) problems.add("note of " + field(g, "title"));
			for (Object s : (List<?>) field(g, "settings")) {
				settings++;
				String label = (String) field(s, "label"), hint = (String) field(s, "hint");
				int lines = ((List<?>) call(top, screenType, "wrap", hint, 300)).size();
				if (lines > 2) problems.add(label + "'s hint takes " + lines + " lines");
				List<String> texts = new ArrayList<>();
				for (String word : (String[]) field(s, "words")) texts.add(label + ": " + word + "*");
				if ((Boolean) field(s, "serverDecides")) texts.add(label + ": Server");
				for (String text : texts) {
					if (mc.textRenderer.getWidth(text) > 144) problems.add("\"" + text + "\" is " + mc.textRenderer.getWidth(text) + " wide");
				}
			}
		}
		results.check("settings: " + settings + " switches; every label fits its button with any value and the restart mark, every hint"
				+ " two lines" + (problems.isEmpty() ? "" : ": " + problems), problems.isEmpty() && settings > 0);

		// What's running is read from the mixin plugins' own record of what they skipped: that's only so if Mixin made
		// them in the game's class loader, where the screen's providers read them (else these would be other copies,
		// never read).
		boolean read = true;
		for (String plugin : new String[]{"org.studioquire.oldfaithful.fixes.FixesConfig", "org.studioquire.oldfaithful.options.OptionsConfig"}) {
			Field f = Class.forName(plugin).getDeclaredField("read");
			f.setAccessible(true);
			read &= f.getBoolean(null);
		}
		results.check("the switches' mixin plugins read their files in the classes the screen asks what's running", read);
		return true;
	}

	/** A fix: written to its file and nothing else changed there, marked as waiting for a restart, and back. */
	private boolean restartSwitch(Minecraft mc) throws Exception {
		String name = "oldfaithful-fixes.properties", key = "redstone.repeaterUpdates";
		String before = file(name);
		Screen redstone = openGroup(mc, "Redstone and Lighting");
		ButtonWidget b = button(redstone, "Repeaters:");
		String was = b.text;
		press(redstone, b);
		String changed = file(name);
		boolean onlyThat = changed.equals(before.replace(key + "=true", key + "=false"));
		String marked = b.text;
		press(redstone, b);
		results.check("Repeaters: \"" + was + "\" pressed reads \"" + marked + "\", " + name + " says " + key + "=false and nothing else"
				+ " changed in it; pressed again, \"" + b.text + "\" and the file exactly as it was",
				was.equals("Repeaters: Fixed") && marked.equals("Repeaters: Beta*") && onlyThat && b.text.equals(was) && file(name).equals(before));
		return true;
	}

	/** Switches that take effect at once: set where their mods read them. */
	private boolean liveSwitches(Minecraft mc) throws Exception {
		if (FabricLoader.getInstance().isModLoaded("oldfaithful-lighting")) {
			Method lava = Class.forName("org.studioquire.oldfaithful.lighting.LightingConfig").getMethod("lightGeneratedLava");
			Screen redstone = openGroup(mc, "Redstone and Lighting");
			ButtonWidget b = button(redstone, "Lava Light:");
			boolean on = (Boolean) lava.invoke(null);
			press(redstone, b);
			String text = b.text;
			boolean off = !(Boolean) lava.invoke(null) && file("oldfaithful-lighting.properties").contains("lava.lightGenerated=false");
			press(redstone, b);
			results.check("Lava Light: pressed, \"" + text + "\" (no restart mark), the lighting mod's switch off and the file so; pressed again, on",
					on && off && text.equals("Lava Light: Beta") && (Boolean) lava.invoke(null) && b.text.equals("Lava Light: Fixed"));
		}
		if (!FabricLoader.getInstance().isModLoaded("purefaithful-display")) return true;
		Screen window = openGroup(mc, "Window and Mouse");
		String display = "purefaithful-display.properties";

		ButtonWidget vsync = button(window, "VSync:");
		boolean wasOn = Settings.vsync && Window.swapInterval() == 1;
		press(window, vsync);
		boolean nowOff = !Settings.vsync && Window.swapInterval() == 0 && file(display).contains("vsync=false") && vsync.text.equals("VSync: OFF");
		press(window, vsync);
		results.check("VSync: pressed, off at once (swap interval " + (nowOff ? 0 : "not 0") + ") and in the file; pressed again, on (" + Window.swapInterval() + ")",
				wasOn && nowOff && Settings.vsync && Window.swapInterval() == 1 && vsync.text.equals("VSync: ON"));

		Field subpixel = Class.forName("org.studioquire.oldfaithful.display.DisplayConfig").getField("subpixelLook");
		ButtonWidget look = button(window, "Smooth Look:");
		boolean before = subpixel.getBoolean(null);
		press(window, look);
		boolean during = subpixel.getBoolean(null) && file(display).contains("mouse.subpixel=true") && look.text.equals("Smooth Look: ON");
		press(window, look);
		results.check("Smooth Look: pressed, the display mod's sub-pixel look on at once and in the file; pressed again, off",
				!before && during && !subpixel.getBoolean(null) && look.text.equals("Smooth Look: OFF"));

		ButtonWidget mouse = button(window, "Mouse:");
		press(window, mouse);
		boolean raw = Settings.rawMouse && "0".equals(SDLHints.SDL_GetHint(SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE))
				&& file(display).contains("mouse.input=raw") && mouse.text.equals("Mouse: Raw");
		press(window, mouse);
		results.check("Mouse: pressed, raw input at once (SDL's system scale off) and in the file; pressed again, the system's",
				raw && !Settings.rawMouse && "1".equals(SDLHints.SDL_GetHint(SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE)) && mouse.text.equals("Mouse: System"));

		ButtonWidget remember = button(window, "Remember Window:");
		press(window, remember);
		String waiting = remember.text;
		press(window, remember);
		results.check("Remember Window: pressed, \"" + waiting + "\" (waits for a restart); pressed again, \"" + remember.text + "\"",
				waiting.equals("Remember Window: OFF*") && remember.text.equals("Remember Window: ON"));
		return true;
	}

	/** On a server (the world marked remote while the screen is built): the world's switches are the server's. */
	private boolean multiplayer(Minecraft mc) throws Exception {
		String before = file("oldfaithful-fixes.properties");
		mc.world.isRemote = true;
		try {
			Screen redstone = openGroup(mc, "Redstone and Lighting");
			ButtonWidget repeaters = button(redstone, "Repeaters:"), lava = button(redstone, "Lava Light:");
			press(redstone, repeaters);
			Screen blocks = openGroup(mc, "Blocks and Items");
			ButtonWidget slabs = button(blocks, "Wooden Slabs:"), pick = button(blocks, "Pick Block:");
			results.check("multiplayer: \"" + repeaters.text + "\" and \"" + lava.text + "\" can't be pressed (a press changed nothing),"
					+ " \"" + slabs.text + "\" and \"" + pick.text + "\" (this game's own) can",
					repeaters.text.equals("Repeaters: Server") && !repeaters.active && lava.text.equals("Lava Light: Server") && !lava.active
							&& file("oldfaithful-fixes.properties").equals(before) && slabs.active && slabs.text.equals("Wooden Slabs: Fixed")
							&& pick.active);
		} finally {
			mc.world.isRemote = false;
		}
		mc.setScreen(null);
		return true;
	}
}
