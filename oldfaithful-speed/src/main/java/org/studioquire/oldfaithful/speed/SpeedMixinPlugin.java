package org.studioquire.oldfaithful.speed;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * This mod only makes the game faster, so an injection that can't apply (another mod reshaped the method) must turn
 * its optimisation off, not stop the game: injectors are require 0, and every handler falls back to vanilla, or does
 * nothing, when its partners didn't apply (the batched clouds check the tessellator for themselves). After each mixin
 * applies, this says in the log which of its core Mixin handlers (@Inject and the like) the target never calls.
 * MixinExtras' injectors (@WrapOperation, @ModifyExpressionValue, ...) apply in a later pass, after postApply, so they
 * can't be checked here; the game tests run with mixin.debug.countInjections, which reports any injector of either
 * kind that found nothing.
 */
public final class SpeedMixinPlugin implements IMixinConfigPlugin {
	@Override
	public void postApply(String targetClassName, ClassNode target, String mixinClassName, IMixinInfo info) {
		String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		Set<String> absent = new HashSet<>();
		for (MethodNode m : info.getClassNode(0).methods) {
			if (isInjector(m) && !called(target, m.name)) absent.add(m.name);
		}
		for (String h : absent) {
			System.out.println("[purefaithful-speed] WARN " + simple + "." + h + " didn't apply to " + targetClassName
					+ " (another mod has probably changed it); that optimisation stays off, the game runs as vanilla there.");
		}
	}

	private static boolean isInjector(MethodNode m) {
		if (m.visibleAnnotations == null) return false;
		for (AnnotationNode a : m.visibleAnnotations) {
			if (a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !a.desc.contains("/At;")) return true;
		}
		return false;
	}

	/** Mixin merges a handler under a decorated name ending in "$" plus its own. */
	private static boolean called(ClassNode target, String handler) {
		String suffix = "$" + handler;
		for (MethodNode m : target.methods) {
			for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
				if (i instanceof MethodInsnNode) {
					MethodInsnNode c = (MethodInsnNode) i;
					if (c.owner.equals(target.name) && (c.name.equals(handler) || c.name.endsWith(suffix))) return true;
				}
			}
		}
		return false;
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
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode target, String mixinClassName, IMixinInfo info) {
	}
}
