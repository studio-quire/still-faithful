package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * For HeldAttackCheck: beta drops a mouse event when the tick reading it starts more than 200 ms after the last one
 * ended (a stall), and a dropped press would leave the check holding a button the game never saw pressed.
 */
@Mixin(Minecraft.class)
public interface HeldAttackTiming {
	@Accessor("lastTickTime")
	void oldfaithful$setLastTickTime(long millis);
}
