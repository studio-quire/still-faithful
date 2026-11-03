package org.studioquire.oldfaithful.polish.mixin.dusk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.DuskLight;
import org.studioquire.oldfaithful.polish.DuskShader;

/**
 * #6: the chunks' display lists are called with DuskShader's program, which applies this frame's darkness; a step in
 * the darkness no longer marks every sky-lit chunk for rebuilding, only one holding vertices lit as beta lit them; and
 * entities and block entities are lit at the same darkness as the terrain (DuskLight.entityScope).
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@Shadow
	private World world;

	@Unique
	private boolean oldfaithful$shading;

	@Inject(method = "renderLastChunks", at = @At("HEAD"))
	private void oldfaithful$shade(int layer, double tickDelta, CallbackInfo ci) {
		oldfaithful$shading = world != null && DuskLight.active();
		if (oldfaithful$shading) DuskShader.begin(world);
	}

	@Inject(method = "renderLastChunks", at = @At("RETURN"))
	private void oldfaithful$shaded(int layer, double tickDelta, CallbackInfo ci) {
		if (!oldfaithful$shading) return;
		oldfaithful$shading = false;
		DuskShader.end();
	}

	@WrapOperation(method = "notifyAmbientDarknessChanged", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;hasSkyLight:Z"))
	private boolean oldfaithful$onlyStepped(ChunkBuilder chunk, Operation<Boolean> original) {
		boolean skyLit = original.call(chunk);
		return DuskLight.active() ? skyLit && ((DuskLight.Section) chunk).oldfaithful$stepped() : skyLit;
	}

	@Inject(method = "reload", at = @At("HEAD"))
	private void oldfaithful$reload(CallbackInfo ci) {
		DuskLight.reloaded();
	}

	@WrapMethod(method = "renderEntities")
	private void oldfaithful$entities(Vec3d cameraPos, Culler culler, float tickDelta, Operation<Void> original) {
		DuskLight.entityScope++;
		try {
			original.call(cameraPos, culler, tickDelta);
		} finally {
			DuskLight.entityScope--;
		}
	}
}
