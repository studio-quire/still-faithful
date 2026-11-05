package org.studioquire.oldfaithful.selftest.mixin;

import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A screen's buttons, and pressing one as a click on it does (after the click sound). */
@Mixin(Screen.class)
public interface ScreenAccessor {
	@Accessor("buttons")
	List<ButtonWidget> oldfaithful$buttons();

	@Invoker("buttonClicked")
	void oldfaithful$press(ButtonWidget button);

	@Invoker("mouseClicked")
	void oldfaithful$click(int mouseX, int mouseY, int button);
}
