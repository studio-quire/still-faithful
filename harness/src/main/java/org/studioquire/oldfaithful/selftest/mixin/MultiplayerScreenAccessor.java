package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiplayerScreen.class)
public interface MultiplayerScreenAccessor {
	@Accessor("serverField")
	TextFieldWidget oldfaithful$serverField();
}
