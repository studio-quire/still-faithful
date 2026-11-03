package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A button's size, for the settings check's layout checks. */
@Mixin(ButtonWidget.class)
public interface ButtonWidgetAccessor {
	@Accessor("width")
	int oldfaithful$width();

	@Accessor("height")
	int oldfaithful$height();
}
