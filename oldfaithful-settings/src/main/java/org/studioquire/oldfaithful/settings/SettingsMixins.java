package org.studioquire.oldfaithful.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Leaves the settings button out when oldfaithful-options' {@code options.screen} says so: the switch lived there before
 * the screen moved here, and players' files still have it. The library's notice isn't part of that.
 */
public final class SettingsMixins implements IMixinConfigPlugin {
	private boolean screen = true;

	@Override
	public void onLoad(String mixinPackage) {
		File file = FabricLoader.getInstance().getConfigDir().resolve("oldfaithful-options.properties").toFile();
		if (!file.isFile()) return;
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(file)) {
			p.load(in);
		} catch (IOException e) {
			return;
		}
		screen = Boolean.parseBoolean(p.getProperty("options.screen", "true").trim());
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return screen || !mixinClassName.endsWith("OptionsScreenMixin");
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
