package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a screen's frame sits, so ShiftClickCheck can point at its slots. */
@Mixin(HandledScreen.class)
public interface HandledScreenFrame {
	@Accessor("backgroundWidth")
	int polish$frameWidth();

	@Accessor("backgroundHeight")
	int polish$frameHeight();
}
