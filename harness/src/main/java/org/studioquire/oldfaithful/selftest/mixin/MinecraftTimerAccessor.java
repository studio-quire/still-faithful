package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftTimerAccessor {
	@Accessor("timer")
	Timer oldfaithful$timer();
}
