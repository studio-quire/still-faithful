package org.studioquire.oldfaithful.fixes.mixin.seams;

import java.util.Arrays;

import net.minecraft.client.render.world.ChunkRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.ChunkCorners;
import org.studioquire.oldfaithful.fixes.ChunkTransform;

/**
 * #70: beta's chunk renderer draws all its chunks' lists under one translation by (region - camera), with the camera
 * kept in single precision; here each list is drawn under its own chunk's translation (ChunkTransform), in the same
 * order. Called again for the second pass of water, as beta's is.
 */
@Mixin(ChunkRenderer.class)
abstract class ChunkRendererMixin implements ChunkCorners {
	@Shadow
	private boolean initialized;

	/** List, then the chunk's corner x, y, z, for each chunk. */
	@Unique
	private int[] oldfaithful$lists = new int[256];
	@Unique
	private int oldfaithful$count;
	@Unique
	private double oldfaithful$camX, oldfaithful$camY, oldfaithful$camZ;

	@Inject(method = "init", at = @At("HEAD"))
	private void oldfaithful$init(int x, int y, int z, double camX, double camY, double camZ, CallbackInfo ci) {
		oldfaithful$count = 0;
		oldfaithful$camX = camX;
		oldfaithful$camY = camY;
		oldfaithful$camZ = camZ;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void oldfaithful$clear(CallbackInfo ci) {
		oldfaithful$count = 0;
	}

	@Override
	public void oldfaithful$add(int list, int x, int y, int z) {
		int i = oldfaithful$count++ * 4;
		if (i == oldfaithful$lists.length) oldfaithful$lists = Arrays.copyOf(oldfaithful$lists, i * 2);
		oldfaithful$lists[i] = list;
		oldfaithful$lists[i + 1] = x;
		oldfaithful$lists[i + 2] = y;
		oldfaithful$lists[i + 3] = z;
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$renderEach(CallbackInfo ci) {
		ci.cancel();
		if (!initialized) return;
		int[] l = oldfaithful$lists;
		for (int i = 0, n = oldfaithful$count * 4; i < n; i += 4) {
			GL11.glPushMatrix();
			ChunkTransform.chunk(l[i + 1], l[i + 2], l[i + 3], oldfaithful$camX, oldfaithful$camY, oldfaithful$camZ);
			GL11.glCallList(l[i]);
			GL11.glPopMatrix();
		}
	}
}
