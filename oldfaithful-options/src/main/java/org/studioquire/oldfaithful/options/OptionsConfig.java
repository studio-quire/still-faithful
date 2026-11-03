package org.studioquire.oldfaithful.options;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * config/oldfaithful-options.properties: one switch per option, all on by default, as oldfaithful-fixes has them. One
 * that's off isn't applied at all (its mixins are skipped), so it can also clear a clash with another mod. Each lives
 * in its own package under {@code mixin}, named in {@link #SWITCHES}. The window's defaults aren't here: this mod asks
 * the display mod for them (fabric.mod.json), and they're set in its file.
 */
public final class OptionsConfig implements IMixinConfigPlugin {
	private static final String HEADER = "# oldfaithful-options: options and comforts beta 1.7.3 lacked, each on its own switch.\n"
			+ "# false leaves that part of the game as beta had it. Restart the game after changing anything here (the\n"
			+ "# settings screen, Options > Old Faithful..., writes this file too).\n"
			+ "# The window opening maximised the first time and where you left it after: window.startMaximized and\n"
			+ "# window.remember in purefaithful-display.properties.\n";

	/** Key, mixin package, explanation. */
	private static final String[][] SWITCHES = {
			{"options.fov", "fov",
					"# A field-of-view slider in Options, beside Difficulty: 30 to 110 degrees, beta's 70 by default (\"Normal\").\n"
					+ "# The hand and held item keep beta's view, so they look the same at any setting. Saved in options.txt as fov.\n"},
			{"gui.normalScale", "gui",
					"# A first start (no options.txt yet) has GUI Scale at Normal instead of Auto. Once options.txt exists,\n"
					+ "# it keeps whatever is chosen in Video Settings.\n"},
			{"options.screen", "screen",
					"# Old Faithful... in Options: a screen for these switches, the fixes', the lighting's and the window's, so\n"
					+ "# they needn't be edited here. Left out by itself if another mod has put something in its place.\n"},
			{"title.quitGame", "quit",
					"# Quit Game on the title screen, beside Options, as beta had it when run on its own rather than in the\n"
					+ "# launcher's frame. It stops the game as closing the window does.\n"},
	};

	private static final List<String> off = new ArrayList<>();
	/** Set once the switches are read: the settings screen asks this class what's running (applied). */
	private static volatile boolean read;

	@Override
	public void onLoad(String mixinPackage) {
		read = true;
		Properties p = new Properties();
		File file = FabricLoader.getInstance().getConfigDir().resolve("oldfaithful-options.properties").toFile();
		try {
			StringBuilder add = new StringBuilder();
			if (file.isFile()) {
				try (InputStream in = new FileInputStream(file)) {
					p.load(in);
				}
			} else {
				file.getParentFile().mkdirs();
				add.append(HEADER);
			}
			for (String[] s : SWITCHES) {
				if (!p.containsKey(s[0])) add.append('\n').append(s[2]).append(s[0]).append("=true\n");
			}
			if (add.length() > 0) {
				try (OutputStream out = new FileOutputStream(file, true)) {
					out.write(add.toString().getBytes(StandardCharsets.ISO_8859_1));
				}
			}
		} catch (IOException e) {
			System.out.println("[oldfaithful-options] WARN Could not read or write " + file + ", every option on: " + e);
		}
		for (String[] s : SWITCHES) {
			if (!Boolean.parseBoolean(p.getProperty(s[0], "true").trim())) off.add(s[0]);
		}
		if (!off.isEmpty()) System.out.println("[oldfaithful-options] Turned off in " + file.getName() + ": " + String.join(", ", off));
	}

	/** Whether the switch was on when the game started, so its mixins are in. */
	public static boolean applied(String key) {
		return !off.contains(key);
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String pkg = mixinClassName.substring(0, mixinClassName.lastIndexOf('.'));
		pkg = pkg.substring(pkg.lastIndexOf('.') + 1);
		for (String[] s : SWITCHES) {
			if (s[1].equals(pkg)) return !off.contains(s[0]);
		}
		throw new IllegalStateException("A mixin outside any switch's package: " + mixinClassName);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
