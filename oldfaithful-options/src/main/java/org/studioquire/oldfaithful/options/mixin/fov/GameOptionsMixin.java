package org.studioquire.oldfaithful.options.mixin.fov;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.options.Fov;

/**
 * The FOV setting kept in options.txt beside beta's own ({@code fov:<degrees>}): beta skips lines it doesn't know, so
 * the file still reads in a game without this mod (which drops the line the next time it saves).
 */
@Mixin(GameOptions.class)
abstract class GameOptionsMixin {
	@Shadow
	private File file;

	// Every return: beta's load returns early when there's no file, and then the setting stays beta's.
	@Inject(method = "load", at = @At("RETURN"))
	private void oldfaithful$loadFov(CallbackInfo ci) {
		if (this.file == null || !this.file.isFile()) return;
		try (BufferedReader in = Files.newBufferedReader(this.file.toPath(), StandardCharsets.ISO_8859_1)) {
			for (String line; (line = in.readLine()) != null; ) {
				if (!line.startsWith(Fov.KEY + ":")) continue;
				Integer d = Fov.parse(line.substring(Fov.KEY.length() + 1));
				if (d != null) Fov.degrees = d;
				else System.out.println("Skipping bad option: " + line);
			}
		} catch (IOException e) {
			System.out.println("[oldfaithful-options] Could not read the FOV from " + this.file.getName() + ": " + e);
		}
	}

	@WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Ljava/io/PrintWriter;close()V"))
	private void oldfaithful$saveFov(PrintWriter out, Operation<Void> close) {
		out.println(Fov.KEY + ":" + Fov.degrees);
		close.call(out);
	}
}
