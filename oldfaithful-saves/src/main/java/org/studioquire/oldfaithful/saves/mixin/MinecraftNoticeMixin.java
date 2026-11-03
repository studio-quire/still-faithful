package org.studioquire.oldfaithful.saves.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.saves.Notices;

/** Save problems the player should know about appear in chat once they're in a world. */
@Mixin(Minecraft.class)
abstract class MinecraftNoticeMixin {
	@Shadow
	public InGameHud inGameHud;
	@Shadow
	public World world;

	@Inject(method = "tick", at = @At("HEAD"))
	private void oldfaithful$notices(CallbackInfo ci) {
		if (inGameHud != null && world != null) Notices.drain(inGameHud::addChatMessage);
	}
}
