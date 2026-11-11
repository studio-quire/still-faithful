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
 * in the running world: "Old Faithful..." in Options with beta's own buttons where they were; the menus the installed
 * mods give, their pages turned by real clicks; every label fitting its button and every hint its two lines; clicks that
 * write the config files (and nothing else in them), apply at once where they should (vsync, sub-pixel look, the mouse,
 * lava light) and mark a restart where they can't; in multiplayer, the world's switches shown as the server's and not
 * pressable. With a library folder (`clientStartupTestLibrary`), the Mods menu, mods' pages and a list long enough to page.
 * Then the screens drawn at GUI scales Small to Large in a small, a middling and a large window, checked for overlaps and
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
	private final Path list = FabricLoader.getInstance().getGameDir().resolve("mods-library").resolve("selection.txt");
	private final boolean library = Files.isDirectory(list.getParent());
	/** A release's content pack (`morePackTest`): {@code on}, everything on, as shipped; {@code off}, nothing on; {@code absent}, the same with no list at all. */
	private static final String PACK = System.getProperty("oldfaithful.selftest.libraryPack");
	/**
	 * The pack's mods as {@code id:Name,...}, from the build (its list, each mod's name in its fabric.mod.json), or from the pack's own jars for a session's
	 * test pack (`tools/pack_start.py`): the Mods menu should offer exactly these.
	 */
	private static final java.util.Map<String, String> PACK_MODS = packMods(System.getProperty("oldfaithful.selftest.packMods", ""));
	private static final List<String> PACK_NAMES = new ArrayList<>(PACK_MODS.values());
	private static final List<String> PACK_IDS = new ArrayList<>(PACK_MODS.keySet());
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

	private Screen openMenu(Minecraft mc, String menu) {
		Screen top = openSettings(mc);
		press(top, button(top, menu + "..."));
		return mc.currentScreen;
	}

	private static boolean has(Screen s, String textStart) {
		for (ButtonWidget b : buttons(s)) {
			if (b.text.startsWith(textStart)) return true;
		}
		return false;
	}

	/** The page of a menu with this button on it, reached as a player reaches it, over the arrows. */
	private Screen pageWith(Minecraft mc, String menu, String label) {
		Screen page = openMenu(mc, menu);
		while (!has(page, label)) press(page, button(page, ">"));
		return page;
	}

	/** A menu turned on by this many pages. */
	private Screen turned(Minecraft mc, String menu, int pages) {
		Screen page = openMenu(mc, menu);
		for (int i = 0; i < pages; i++) press(page, button(page, ">"));
		return page;
	}

	private Screen openMod(Minecraft mc, String name) {
		Screen mods = pageWith(mc, "Mods", name + "...");
		press(mods, button(mods, name + "..."));
		return mc.currentScreen;
	}

	/** As the mouse clicks, through beta's loop over the buttons, which turning a page rebuilds. */
	private static void click(Screen s, ButtonWidget b) {
		((ScreenAccessor) s).oldfaithful$click(b.x + 2, b.y + 2, 0);
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
		steps.add(this::menusAndWords);
		steps.add(this::menuPages);
		steps.add(this::restartSwitch);
		steps.add(this::liveSwitches);
		steps.add(this::multiplayer);
		if (PACK != null) steps.add(this::packMods);
		else if (library) steps.add(this::modPages);
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
					layout(m, pageWith(m, "Changes", "VSync:"), at + " Window and Mouse");
					return true;
				});
				// Pointing at the first button, so its hint shows.
				steps.add(capture(at + "-window", "VSync"));
				steps.add(m -> {
					layout(m, pageWith(m, "Fixes", "Wooden Slabs:"), at + " Blocks and Items");
					return true;
				});
				steps.add(capture(at + "-blocks", null));
				if (!library) continue;
				steps.add(m -> {
					layout(m, pageWith(m, "Mods", "World Rules..."), at + " Mods");
					return true;
				});
				steps.add(capture(at + "-mods", "World Rules"));
				steps.add(m -> {
					layout(m, openMod(m, "World Rules"), at + " World Rules");
					return true;
				});
				steps.add(capture(at + "-world-rules", "Enabled"));
			}
		}
		// A change waiting for a restart, pointed at.
		steps.add(resize(854, 480));
		steps.add(wait(5));
		steps.add(m -> {
			m.options.guiScale = 2;
			Screen redstone = pageWith(m, "Fixes", "Repeaters:");
			press(redstone, button(redstone, "Repeaters:"));
			return true;
		});
		steps.add(capture("854x480-gui2-redstone-waiting", "Repeaters"));
		steps.add(m -> {
			press(m.currentScreen, button(m.currentScreen, "Repeaters:"));
			return true;
		});
		everyLevel();
		steps.add(m -> {
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

	/** One picture of each level and page, at the GUI scale most players have: levels/ in settings-shots/. */
	private void everyLevel() {
		steps.add(m -> {
			openSettings(m);
			return true;
		});
		steps.add(capture("levels/1-top", "Changes"));
		steps.add(m -> {
			openSettings(m);
			return true;
		});
		steps.add(capture("levels/1-top-fixes", "Fixes"));
		for (int page = 0; page < 6; page++) {
			int turn = page;
			steps.add(m -> {
				turned(m, "Fixes", turn);
				return true;
			});
			steps.add(capture("levels/2-fixes-" + (page + 1), null));
		}
		boolean still = FabricLoader.getInstance().isModLoaded("stillfaithful-polish");
		for (int page = 0; page < (still ? 3 : 1); page++) {
			int turn = page;
			steps.add(m -> {
				turned(m, "Changes", turn);
				return true;
			});
			steps.add(capture("levels/2-changes-" + (page + 1), null));
		}
		if (!library || PACK != null) return;
		steps.add(m -> {
			pageWith(m, "Mods", "World Rules...");
			return true;
		});
		steps.add(capture("levels/2-mods", "World Rules"));
		steps.add(m -> {
			openMod(m, "World Rules");
			return true;
		});
		steps.add(capture("levels/3-world-rules", null));
		steps.add(m -> {
			openMod(m, "World Rules");
			return true;
		});
		steps.add(capture("levels/3-world-rules-pointed", "Weather"));
		steps.add(m -> {
			Screen rules = openMod(m, "World Rules");
			press(rules, button(rules, "Enabled:"));
			return true;
		});
		steps.add(capture("levels/3-world-rules-off", null));
		steps.add(m -> {
			press(m.currentScreen, button(m.currentScreen, "Done"));
			return true;
		});
		steps.add(capture("levels/2-mods-waiting", null));
		steps.add(m -> {
			Screen rules = openMod(m, "World Rules");
			press(rules, button(rules, "Enabled:"));
			openMod(m, "Plain F3");
			return true;
		});
		steps.add(capture("levels/3-plain-f3", null));
		steps.add(m -> {
			manyMods(true);
			turned(m, "Mods", 1);
			return true;
		});
		steps.add(capture("levels/2-mods-many", null));
		steps.add(m -> {
			manyMods(false);
			return true;
		});
	}

	/** A hundred and some mods for a moment, each reading as Plain F3's switch does; only the screen's lists know them. */
	@SuppressWarnings("unchecked")
	private void manyMods(boolean on) throws ReflectiveOperationException {
		Object registry = Class.forName(PKG + "SettingsRegistry").getMethod("get").invoke(null);
		java.util.Map<String, Object> mods = (java.util.Map<String, Object>) field(registry, "mods");
		if (!on) {
			mods.keySet().removeIf(id -> id.startsWith("many-"));
			return;
		}
		Object plain = mods.get("plain-f3");
		java.lang.reflect.Constructor<?> group = plain.getClass().getDeclaredConstructors()[0];
		group.setAccessible(true);
		Field modSwitch = plain.getClass().getDeclaredField("modSwitch");
		modSwitch.setAccessible(true);
		for (int i = 1; i <= 100; i++) {
			Object g = group.newInstance("many-" + i, String.format("Many Mod %03d", i), 0, null);
			modSwitch.set(g, modSwitch.get(plain));
			mods.put("many-" + i, g);
		}
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
		String title;
		try {
			title = (String) field(mc.currentScreen, "title");
		} catch (ReflectiveOperationException e) {
			title = e.toString();
		}
		results.check("the settings screen is titled \"" + title + "\", as its button names it", BUTTON.equals(title + "..."));
		return true;
	}

	/** The menus the installed mods give, and every label and hint fitting its place. */
	private boolean menusAndWords(Minecraft mc) throws Exception {
		FabricLoader loader = FabricLoader.getInstance();
		List<String> expected = new ArrayList<>();
		if (loader.isModLoaded("oldfaithful-fixes") || loader.isModLoaded("oldfaithful-lighting")) expected.add("Fixes...");
		expected.add("Changes...");
		if (library) expected.add("Mods...");
		expected.add("Done");
		Screen top = openSettings(mc);
		results.check("settings: a wide button for each menu of the installed mods: " + texts(top), texts(top).equals(expected));
		boolean wide = true;
		for (ButtonWidget b : buttons(top)) wide &= ((ButtonWidgetAccessor) b).oldfaithful$width() == 200;
		results.check("and they're all beta's wide button", wide);

		Class<?> registryType = Class.forName(PKG + "SettingsRegistry"), screenType = Class.forName(PKG + "SettingsScreen");
		Object registry = registryType.getMethod("get").invoke(null);
		List<Object> groups = new ArrayList<>();
		for (String menu : new String[]{"fixes", "changes", "mods"}) groups.addAll((List<?>) call(registry, registryType, menu));
		List<String> problems = new ArrayList<>();
		int settings = 0;
		for (Object g : groups) {
			Object noteSource = field(g, "note"), note = noteSource == null ? null : ((java.util.function.Supplier<?>) noteSource).get();
			if (note != null && ((List<?>) call(top, screenType, "wrap", note, 300)).size() > 2) problems.add("note of " + field(g, "title"));
			if (mc.textRenderer.getWidth(field(g, "title") + "...") > 194) problems.add("the mod name " + field(g, "title"));
			for (Object s : (List<?>) call(g, g.getClass(), "all")) {
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

	/** Over a menu's pages by real clicks, to the end and back, one page a click: the titles, and how many switches they hold. */
	private int walk(Minecraft mc, String menu, List<String> titles) throws Exception {
		Screen page = openMenu(mc, menu);
		int switches = 0;
		List<String> back = new ArrayList<>();
		while (true) {
			titles.add((String) call(page, page.getClass(), "pageTitle"));
			for (ButtonWidget b : buttons(page)) {
				if (b.text.contains(": ")) switches++;
			}
			if (!has(page, ">") || !button(page, ">").active) break;
			click(page, button(page, ">"));
		}
		while (has(page, "<") && button(page, "<").active) {
			click(page, button(page, "<"));
			back.add(0, (String) call(page, page.getClass(), "pageTitle"));
		}
		if (!back.equals(titles.subList(0, titles.size() - 1))) titles.add("back by other pages: " + back);
		return switches;
	}

	private boolean menuPages(Minecraft mc) throws Exception {
		Class<?> registryType = Class.forName(PKG + "SettingsRegistry");
		Object registry = registryType.getMethod("get").invoke(null);
		List<String> titles = new ArrayList<>();
		int shown = walk(mc, "Fixes", titles), registered = 0;
		for (Object g : (List<?>) call(registry, registryType, "fixes")) registered += ((List<?>) field(g, "settings")).size();
		results.check("Fixes: a page a section, there and back a click at a time, holding all " + registered + " fixes: " + titles + " " + shown,
				titles.equals(java.util.Arrays.asList("Fixes: Blocks and Items (1/7)", "Fixes: Mobs (2/7)", "Fixes: Players and Vehicles (3/7)",
						"Fixes: Redstone and Lighting (4/7)", "Fixes: Looks and Sounds (5/7)", "Fixes: Screens (6/7)", "Fixes: Multiplayer (7/7)")) && shown == registered);
		titles.clear();
		walk(mc, "Changes", titles);
		boolean still = FabricLoader.getInstance().isModLoaded("stillfaithful-polish");
		results.check("Changes: " + titles, titles.equals(still
				? java.util.Arrays.asList("Changes: Polish (1/4)", "Changes: Polish (2/4)", "Changes: Polish (3/4)", "Changes: Window and Mouse (4/4)")
				: java.util.Collections.singletonList("Changes: Window and Mouse")));
		return true;
	}

	/** The library's mods: a wide button each, leading to a page with the on and off first, then what the mod has. */
	private boolean modPages(Minecraft mc) throws Exception {
		Screen mods = openMenu(mc, "Mods");
		results.check("Mods: a wide button for each mod, by name: " + texts(mods),
				texts(mods).equals(java.util.Arrays.asList("No Toasts...", "Plain F3...", "World Rules...", "Done"))
						&& ((ButtonWidgetAccessor) button(mods, "World Rules")).oldfaithful$width() == 200);

		Screen rules = openMod(mc, "World Rules");
		ButtonWidget enabled = button(rules, "Enabled:"), keep = button(rules, "Keep Inventory:"), lock = button(rules, "Lock Rules");
		// In a world, the core's Lock Rules pairs with it (pressed in libraryRulesTest, not here: it can't be undone in game).
		results.check("World Rules' page: the on and off and Lock Rules in a row of their own at the top, short buttons, the rules below: " + texts(rules),
				enabled.text.equals("Enabled: ON") && enabled.x == rules.width / 2 - 155 && enabled.y == rules.height / 6
						&& lock.x == rules.width / 2 + 5 && lock.y == enabled.y && lock.active
						&& ((ButtonWidgetAccessor) enabled).oldfaithful$width() == 150 && ((ButtonWidgetAccessor) lock).oldfaithful$width() == 150
						&& button(rules, "Mobs Break Blocks:").y == rules.height / 6 + 24 && has(rules, "Weather: Normal"));
		java.util.Set<String> before = new java.util.HashSet<>(Files.readAllLines(list));
		press(rules, enabled);
		List<String> off = Files.readAllLines(list);
		String offText = enabled.text;
		press(rules, enabled);
		results.check("Enabled: pressed, \"" + offText + "\", the list without the mod; pressed again, as it was",
				offText.equals("Enabled: OFF*") && !off.contains("mods-library/world-rules.jar") && before.contains("mods-library/world-rules.jar")
						&& new java.util.HashSet<>(Files.readAllLines(list)).equals(before));
		press(rules, keep);
		String pressed = keep.text;
		press(rules, keep);
		results.check("a rule on the mod's page: pressed, \"" + pressed + "\" at once, no restart; pressed again, \"" + keep.text + "\"",
				pressed.equals("Keep Inventory: ON") && keep.text.equals("Keep Inventory: OFF"));
		press(rules, button(rules, "Done"));
		results.check("Done goes back one level, to the Mods menu: " + mc.currentScreen.getClass().getSimpleName(), mc.currentScreen.getClass().getName().equals(PKG + "SettingsModsScreen"));

		Screen plain = openMod(mc, "Plain F3");
		results.check("a mod with no settings has only its on and off: " + texts(plain), texts(plain).equals(java.util.Arrays.asList("Enabled: ON", "Done")));

		manyMods(true);
		Screen many = openMenu(mc, "Mods");
		String first = buttons(many).get(2).text;
		ButtonWidget next = button(many, ">");
		click(many, next);
		String second = buttons(many).get(2).text;
		press(many, buttons(many).get(3));
		press(mc.currentScreen, button(mc.currentScreen, "Done"));
		boolean samePage = mc.currentScreen == many && buttons(many).get(2).text.equals(second);
		int pages = 2;
		while (button(many, ">").active) {
			click(many, button(many, ">"));
			pages++;
		}
		results.check("a hundred mods more: " + pages + " pages of five from \"" + first + "\", on to \"" + second + "\", and Done from a mod's page comes back to the page it left",
				pages == 21 && first.equals("Many Mod 001...") && second.equals("Many Mod 006...") && samePage);
		manyMods(false);
		mc.setScreen(null);
		return true;
	}

	/** A mod's own settings, besides its rules, as its page shows them in a pack as it comes: the whole button, so each at its default. */
	private static final java.util.Map<String, List<String>> PACK_SETTINGS = java.util.Collections.singletonMap("Blue Flowers", java.util.Collections.singletonList("Yellow Flower: Dandelion"));

	private static java.util.Map<String, String> packMods(String given) {
		java.util.Map<String, String> mods = new java.util.TreeMap<>();
		for (String m : given.split(",")) {
			int colon = m.indexOf(':');
			if (colon > 0) mods.put(m.substring(0, colon).trim(), m.substring(colon + 1).trim());
		}
		return mods;
	}

	private java.util.Set<String> listed() throws Exception {
		java.util.Set<String> lines = new java.util.TreeSet<>();
		for (String line : Files.readAllLines(list)) {
			if (!line.trim().isEmpty()) lines.add(line.trim());
		}
		return lines;
	}

	/** Each loaded mod's world rules by the mod's name, as their buttons begin: what the core has declared, read where the core keeps them. */
	private static java.util.Map<String, List<String>> rulesByMod() throws Exception {
		java.util.Map<String, List<String>> rules = new java.util.HashMap<>();
		Method id = Class.forName("org.studioquire.oldfaithful.core.api.Rule").getMethod("id");
		for (Object rule : (java.util.Collection<?>) Class.forName("org.studioquire.oldfaithful.core.impl.Rules").getMethod("all").invoke(null)) {
			String ruleId = (String) id.invoke(rule), mod = ruleId.substring(0, ruleId.indexOf(':'));
			String name = FabricLoader.getInstance().getModContainer(mod).get().getMetadata().getName();
			String label = net.minecraft.client.resource.language.TranslationStorage.getInstance().get("rule." + ruleId.replace(':', '.') + ".name");
			rules.computeIfAbsent(name, k -> new ArrayList<>()).add(label + ":");
		}
		return rules;
	}

	/** Every button on a page and the pages after it, over the arrows. */
	private static List<String> allTexts(Screen page) {
		List<String> all = new ArrayList<>(texts(page));
		while (has(page, ">") && button(page, ">").active) {
			click(page, button(page, ">"));
			all.addAll(texts(page));
		}
		return all;
	}

	/**
	 * A library pack from a release, by what a player finds: with nothing on, none of the library's code is here and the Mods menu still offers all of it; ticking a mod
	 * brings the core onto the list and unticking the last takes it off, and the run leaves everything ticked for the next launch to find. With everything on, it all loaded.
	 */
	private boolean packMods(Minecraft mc) throws Exception {
		boolean on = PACK.equals("on");
		boolean anyLoaded = FabricLoader.getInstance().isModLoaded("faithful-core");
		boolean allLoaded = anyLoaded;
		for (String id : PACK_IDS) {
			anyLoaded |= FabricLoader.getInstance().isModLoaded(id);
			allLoaded &= FabricLoader.getInstance().isModLoaded(id);
		}
		if (on) {
			results.check("everything on: the core and all the mods are loaded", allLoaded);
		} else {
			boolean coreClass = true;
			try {
				Class.forName("org.studioquire.oldfaithful.core.impl.CoreNet", false, getClass().getClassLoader());
			} catch (ClassNotFoundException e) {
				coreClass = false;
			}
			results.check("nothing on: no library mod is loaded and the core's classes aren't there", !anyLoaded && !coreClass);
			results.check("nothing on: the list is " + (PACK.equals("absent") ? "absent" : "there and empty"), PACK.equals("absent") ? !Files.exists(list) : listed().isEmpty());
		}
		List<String> wanted = new ArrayList<>();
		results.check("the test was told the pack's mods", !PACK_MODS.isEmpty());
		List<String> names = new ArrayList<>(PACK_NAMES);
		java.util.Collections.sort(names);
		for (String name : names) wanted.add(name + "...");
		Screen mods = openMenu(mc, "Mods");
		List<String> offered = new ArrayList<>();
		while (true) {
			for (String text : texts(mods)) {
				if (text.endsWith("...")) offered.add(text);
			}
			if (!has(mods, ">") || !button(mods, ">").active) break;
			click(mods, button(mods, ">"));
		}
		results.check("Mods: a wide button for each mod, over its pages: " + offered, offered.equals(wanted));
		// With nothing on, a page is its on and off; with everything, its mod's rules and settings too, and Lock Rules where there are any rules (in a world: this check runs in one).
		java.util.Map<String, List<String>> rules = on ? rulesByMod() : java.util.Collections.emptyMap();
		for (String name : PACK_NAMES) {
			Screen page = openMod(mc, name);
			String enabled = button(page, "Enabled:").text;
			List<String> own = rules.getOrDefault(name, java.util.Collections.emptyList()), shown = new ArrayList<>(), stray = new ArrayList<>();
			List<String> settings = on ? PACK_SETTINGS.getOrDefault(name, java.util.Collections.emptyList()) : java.util.Collections.emptyList(), settingsShown = new ArrayList<>();
			boolean lock = false;
			for (String text : allTexts(page)) {
				String rule = own.stream().filter(text::startsWith).findFirst().orElse(null);
				if (rule != null) shown.add(rule);
				else if (settings.contains(text)) settingsShown.add(text);
				else if (text.equals("Lock Rules")) lock = true;
				else if (!text.startsWith("Enabled:") && !text.equals("Done") && !text.equals("<") && !text.equals(">")) stray.add(text);
			}
			results.check(name + "'s page: " + texts(page) + (own.isEmpty() ? "" : ", its rules " + own) + (settings.isEmpty() ? "" : ", its settings " + settings),
					enabled.equals(on ? "Enabled: ON" : "Enabled: OFF") && shown.equals(own) && settingsShown.equals(settings) && lock == !own.isEmpty() && stray.isEmpty());
		}
		if (on) {
			java.util.Set<String> before = listed();
			Screen page = openMod(mc, "Water Bottles");
			ButtonWidget enabled = button(page, "Enabled:");
			press(page, enabled);
			String offText = enabled.text;
			boolean gone = !listed().contains("mods-library/water-bottles.jar") && !listed().contains("mods-library/alchemy.jar") && listed().contains("mods-library/faithful-core.jar");
			press(page, enabled);
			Screen needing = openMod(mc, "Alchemy");
			press(needing, button(needing, "Enabled:"));
			results.check("Water Bottles unticked, \"" + offText + "\", off the list with Alchemy, which needs it, and the core still on it; both ticked, as it was", offText.equals("Enabled: OFF*") && gone && listed().equals(before));
			mc.setScreen(null);
			return true;
		}
		Screen page = openMod(mc, "Water Bottles");
		ButtonWidget enabled = button(page, "Enabled:");
		press(page, enabled);
		String onText = enabled.text;
		java.util.Set<String> one = listed();
		results.check("Water Bottles ticked, \"" + onText + "\": the list has it and the core: " + one, onText.equals("Enabled: ON*")
				&& one.equals(new java.util.TreeSet<>(java.util.Arrays.asList("mods-library/faithful-core.jar", "mods-library/water-bottles.jar"))));
		press(page, enabled);
		results.check("Water Bottles unticked, \"" + enabled.text + "\": the last mod off takes the core with it, the list empty: " + listed(), enabled.text.equals("Enabled: OFF") && listed().isEmpty());
		Screen needing = openMod(mc, "Alchemy");
		press(needing, button(needing, "Enabled:"));
		results.check("Alchemy ticked: it brings Water Bottles, which it needs, and the core: " + listed(),
				listed().equals(new java.util.TreeSet<>(java.util.Arrays.asList("mods-library/alchemy.jar", "mods-library/faithful-core.jar", "mods-library/water-bottles.jar"))));
		for (String name : PACK_NAMES) {
			Screen p = openMod(mc, name);
			if (button(p, "Enabled:").text.startsWith("Enabled: OFF")) press(p, button(p, "Enabled:"));
		}
		results.check("every mod ticked, for the next launch: " + listed(), listed().size() == PACK_NAMES.size() + 1);
		mc.setScreen(null);
		return true;
	}

	/** A fix: written to its file and nothing else changed there, marked as waiting for a restart, and back. */
	private boolean restartSwitch(Minecraft mc) throws Exception {
		String name = "oldfaithful-fixes.properties", key = "redstone.repeaterUpdates";
		String before = file(name);
		Screen redstone = pageWith(mc, "Fixes", "Repeaters:");
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
			Screen redstone = pageWith(mc, "Fixes", "Lava Light:");
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
		Screen window = pageWith(mc, "Changes", "VSync:");
		String display = "purefaithful-display.properties";

		ButtonWidget vsync = button(window, "VSync:");
		// StartupCheck keeps vsync off for the run, against display stalls; as shipped for this button alone.
		if (Settings.vsync) org.lwjgl.opengl.Display.setSwapInterval(1);
		boolean wasOn = Settings.vsync && Window.swapInterval() == 1;
		press(window, vsync);
		boolean nowOff = !Settings.vsync && Window.swapInterval() == 0 && file(display).contains("vsync=false") && vsync.text.equals("VSync: OFF");
		press(window, vsync);
		results.check("VSync: pressed, off at once (swap interval " + (nowOff ? 0 : "not 0") + ") and in the file; pressed again, on (" + Window.swapInterval() + ")",
				wasOn && nowOff && Settings.vsync && Window.swapInterval() == 1 && vsync.text.equals("VSync: ON"));
		org.lwjgl.opengl.Display.setSwapInterval(0);

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
			Screen redstone = pageWith(mc, "Fixes", "Repeaters:");
			ButtonWidget repeaters = button(redstone, "Repeaters:"), lava = button(redstone, "Lava Light:");
			press(redstone, repeaters);
			Screen blocks = pageWith(mc, "Fixes", "Wooden Slabs:");
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
