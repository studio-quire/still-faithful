package org.studioquire.oldfaithful.selftest;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.studioquire.oldfaithful.selftest.mixin.ScreenAccessor;

/**
 * Old Faithful's options mod (oldfaithful-options), from the start-up test when it's loaded: Quit Game on the title
 * screen, GUI Scale Normal on a first start, and in the running world the FOV setting: the world drawn at it, the hand
 * at beta's view, kept in options.txt, and its slider in the Options screen. The mod's own classes are reached by
 * name, so the harness builds without it (Pure Faithful's public snapshot).
 */
public final class OptionsCheck {
	public static final String MOD = "oldfaithful-options";
	private static final int SETTING = 100;
	private static final int MAX_TICKS = 60;

	public static volatile boolean probing;
	private static volatile float worldFov = Float.NaN;
	private static volatile float handFov = Float.NaN;

	private final SelfTest results;
	private int ticks;

	OptionsCheck(SelfTest results) {
		this.results = results;
	}

	static boolean loaded() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(MOD);
	}

	private static float projectedFov() {
		FloatBuffer m = BufferUtils.createFloatBuffer(16);
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, m);
		return (float) Math.toDegrees(2.0 * Math.atan(1.0 / m.get(5)));
	}

	public static void worldProjected() {
		worldFov = projectedFov();
	}

	public static void handProjected() {
		handFov = projectedFov();
	}

	private static ButtonWidget button(Screen screen, int id) {
		for (ButtonWidget b : ((ScreenAccessor) screen).oldfaithful$buttons()) {
			if (b.id == id) return b;
		}
		return null;
	}

	/** On the title screen: beta's own Quit Game (id 4) beside Options, as beta lays them out on its own. */
	static void titleScreen(SelfTest results, Screen title) {
		ButtonWidget quit = button(title, 4), options = button(title, 0);
		results.check("title screen: Quit Game beside Options (" + (quit == null ? "no button 4" : "\"" + quit.text + "\" at " + quit.x + "," + quit.y) + ")",
				quit != null && options != null && "Quit Game".equals(quit.text) && quit.y == options.y && quit.x == title.width / 2 + 2
						&& options.x == title.width / 2 - 100);
	}

	/** A first start (the task launched it without options.txt): GUI Scale at Normal, and saved so if saved yet. */
	static void firstStart(SelfTest results, Minecraft mc) throws java.io.IOException {
		File file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("options.txt").toFile();
		boolean saved = !file.exists() || Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1).contains("guiScale:2");
		results.check("first start: GUI Scale Normal (" + mc.options.guiScale + ")" + (file.exists() ? ", and so in options.txt" : ""),
				mc.options.guiScale == 2 && saved);
	}

	/** Presses Quit Game as a click on it does; false if the title screen has none. */
	static boolean quit(Minecraft mc) {
		ButtonWidget quit = mc.currentScreen == null ? null : button(mc.currentScreen, 4);
		if (quit == null) return false;
		System.out.println("[oldfaithful-selftest] stopping by the title screen's Quit Game");
		((ScreenAccessor) mc.currentScreen).oldfaithful$press(quit);
		return true;
	}

	private static Field degrees() throws ReflectiveOperationException {
		return Class.forName("org.studioquire.oldfaithful.options.Fov").getField("degrees");
	}

	void start(Minecraft mc) throws ReflectiveOperationException {
		degrees().setInt(null, SETTING);
		worldFov = Float.NaN;
		handFov = Float.NaN;
		probing = true;
	}

	/** True when done. */
	boolean tick(Minecraft mc) throws Exception {
		ticks++;
		if ((Float.isNaN(worldFov) || Float.isNaN(handFov)) && ticks < MAX_TICKS) return false;
		probing = false;
		// The world at the setting, the hand at beta's view: 70 on land, 60 under water, in proportion.
		float world = worldFov, hand = handFov;
		results.check("FOV " + SETTING + ": the world drawn at " + world + " degrees, the hand at beta's " + hand,
				Math.abs(world / hand - SETTING / 70.0F) < 1e-3F && (Math.abs(hand - 70.0F) < 0.01F || Math.abs(hand - 60.0F) < 0.01F));

		File file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("options.txt").toFile();
		mc.options.save();
		boolean saved = Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1).contains("fov:" + SETTING);
		degrees().setInt(null, 70);
		mc.options.load();
		results.check("FOV kept in options.txt as fov:" + SETTING + " and read back", saved && degrees().getInt(null) == SETTING);

		degrees().setInt(null, 70);
		mc.options.save();
		OptionsScreen options = new OptionsScreen(null, mc.options);
		mc.setScreen(options);
		ButtonWidget slider = button(options, 7070);
		results.check("Options screen: the FOV slider beside Difficulty (" + (slider == null ? "none" : "\"" + slider.text + "\" at " + slider.x + "," + slider.y) + ")",
				slider != null && slider.getClass().getSimpleName().equals("FovSlider") && "FOV: Normal".equals(slider.text)
						&& slider.x == options.width / 2 + 5 && slider.y == options.height / 6 + 48);
		mc.setScreen(null);
		return true;
	}
}
