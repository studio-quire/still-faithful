package org.studioquire.oldfaithful.polish.selftest.mixin;

import java.util.List;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ChatCheck: the chat's lines as the HUD holds them, newest first. */
@Mixin(InGameHud.class)
public interface ChatMessages {
	@Accessor("messages")
	List<?> oldfaithful$messages();
}
