package org.studioquire.oldfaithful.hardening.mixin.client;

import java.io.PrintStream;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MinecraftApplet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * At startup the game printed "Setting user: <name>, <session id>", and with a modern launcher the session id holds
 * the account's access token, so it ended up in launcher logs and anywhere stdout was copied. The name is kept.
 */
@Mixin(MinecraftApplet.class)
abstract class MinecraftAppletMixin {
	@Shadow private Minecraft minecraft;

	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Ljava/io/PrintStream;println(Ljava/lang/String;)V", remap = false))
	private void oldfaithful$noToken(PrintStream out, String message, Operation<Void> original) {
		original.call(out, "Setting user: " + minecraft.session.username);
	}
}
