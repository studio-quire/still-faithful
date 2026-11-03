package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** FixesCheck (#46): pick block, as the middle button does. */
@Mixin(Minecraft.class)
public interface MinecraftPickInvoker {
	@Invoker("handlePickBlock")
	void oldfaithful$pickBlock();
}
