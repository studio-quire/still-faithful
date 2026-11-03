package org.studioquire.oldfaithful.polish.mixin.dusk;

import java.nio.ByteBuffer;

import net.minecraft.client.render.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.DuskLight;
import org.studioquire.oldfaithful.polish.DuskShader;

/**
 * #6: what DuskLight needs of the tessellator while a chunk is built: the colours as the renderers give them (floats;
 * it keeps bytes), where each vertex lands in its buffer, vertices not written in a pass that only reads colours, and
 * the light arrays added to a chunk mesh's draw. Outside a chunk build only the checks run.
 */
@Mixin(Tessellator.class)
abstract class TessellatorMixin implements DuskLight.Mesh {
	@Shadow
	private int[] buffer;
	@Shadow
	private int bufferPosition;
	@Shadow
	private int bufferSize;
	@Shadow
	private int addedVertexCount;
	@Shadow
	private boolean drawing;
	@Shadow
	private ByteBuffer byteBuffer;
	@Shadow
	private boolean useVbo;
	@Shadow
	private double u;
	@Shadow
	private double v;
	@Shadow
	private int color;
	@Shadow
	private boolean hasColor;
	@Shadow
	private boolean hasTexture;
	@Shadow
	private boolean hasNormals;
	@Shadow
	private boolean colorDisabled;
	@Shadow
	private int normal;
	@Shadow
	private double xOffset;
	@Shadow
	private double yOffset;
	@Shadow
	private double zOffset;

	@Shadow
	public abstract void draw();

	@Unique
	private double oldfaithful$u, oldfaithful$v, oldfaithful$x, oldfaithful$y, oldfaithful$z;
	@Unique
	private int oldfaithful$color, oldfaithful$normal;
	@Unique
	private boolean oldfaithful$hasColor, oldfaithful$hasTexture, oldfaithful$hasNormals;

	@Override
	public int[] oldfaithful$buffer() {
		return buffer;
	}

	@Override
	public int oldfaithful$position() {
		return bufferPosition;
	}

	@Override
	public int oldfaithful$size() {
		return bufferSize;
	}

	@Override
	public boolean oldfaithful$wholeQuads() {
		return drawing && addedVertexCount % 4 == 0;
	}

	@Override
	public void oldfaithful$flush() {
		draw();
		drawing = true;
	}

	@Override
	public void oldfaithful$saveState() {
		oldfaithful$u = u;
		oldfaithful$v = v;
		oldfaithful$x = xOffset;
		oldfaithful$y = yOffset;
		oldfaithful$z = zOffset;
		oldfaithful$color = color;
		oldfaithful$normal = normal;
		oldfaithful$hasColor = hasColor;
		oldfaithful$hasTexture = hasTexture;
		oldfaithful$hasNormals = hasNormals;
	}

	@Override
	public void oldfaithful$restoreState() {
		u = oldfaithful$u;
		v = oldfaithful$v;
		xOffset = oldfaithful$x;
		yOffset = oldfaithful$y;
		zOffset = oldfaithful$z;
		color = oldfaithful$color;
		normal = oldfaithful$normal;
		hasColor = oldfaithful$hasColor;
		hasTexture = oldfaithful$hasTexture;
		hasNormals = oldfaithful$hasNormals;
	}

	@Inject(method = "color(FFF)V", at = @At("HEAD"))
	private void oldfaithful$floats(float r, float g, float b, CallbackInfo ci) {
		if (DuskLight.building && !colorDisabled) DuskLight.colorFloats(r, g, b);
	}

	@Inject(method = "color(FFFF)V", at = @At("HEAD"))
	private void oldfaithful$floats(float r, float g, float b, float a, CallbackInfo ci) {
		if (DuskLight.building && !colorDisabled) DuskLight.colorFloats(r, g, b);
	}

	@Inject(method = "color(IIII)V", at = @At("HEAD"))
	private void oldfaithful$ints(int r, int g, int b, int a, CallbackInfo ci) {
		if (DuskLight.building && !colorDisabled) DuskLight.colorInts(r, g, b);
	}

	@Inject(method = "vertex(DDD)V", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$vertex(double x, double y, double z, CallbackInfo ci) {
		if (DuskLight.dry) {
			DuskLight.dryVertex();
			ci.cancel();
		} else if (DuskLight.real) {
			DuskLight.vertexStart(bufferPosition);
		}
	}

	@Inject(method = "vertex(DDD)V", at = @At("RETURN"))
	private void oldfaithful$vertexDone(double x, double y, double z, CallbackInfo ci) {
		if (DuskLight.real) DuskLight.vertexEnd(bufferPosition);
	}

	@Inject(method = "draw", at = @At("HEAD"))
	private void oldfaithful$drawing(CallbackInfo ci) {
		DuskLight.drawn();
	}

	@Inject(method = "draw", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDrawArrays(III)V"))
	private void oldfaithful$lightArrays(CallbackInfo ci) {
		if (DuskLight.building) DuskShader.attach(byteBuffer, useVbo);
	}

	@Inject(method = "draw", at = @At("RETURN"))
	private void oldfaithful$drawn(CallbackInfo ci) {
		DuskShader.detach();
	}
}
