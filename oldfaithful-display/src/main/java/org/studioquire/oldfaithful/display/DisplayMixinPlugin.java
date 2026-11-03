package org.studioquire.oldfaithful.display;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies this mod's mixins only on Old Faithful's LWJGL: with LWJGL 2 the game's AWT canvas is how it gets a window
 * at all, so taking it away would leave none. Says so once in the log.
 */
public final class DisplayMixinPlugin implements IMixinConfigPlugin {
	private static Boolean present;

	public static boolean layerPresent() {
		if (present == null) {
			present = DisplayMixinPlugin.class.getClassLoader().getResource("org/studioquire/oldfaithful/lwjgl/Window.class") != null;
			if (!present) {
				System.out.println("[purefaithful-display] WARN Pure Faithful's LWJGL component isn't installed (LWJGL 2 is), so this mod does nothing."
						+ " See Pure Faithful's guide for replacing the LWJGL component.");
			}
		}
		return present;
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return layerPresent();
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
