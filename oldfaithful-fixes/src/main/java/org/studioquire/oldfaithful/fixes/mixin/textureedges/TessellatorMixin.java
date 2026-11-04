package org.studioquire.oldfaithful.fixes.mixin.textureedges;

import net.minecraft.client.render.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.TextureEdges;

/**
 * #72: while a chunk is built, each quad's texture coordinates pulled in from its tile's edges once its last vertex is
 * in (TextureEdges). Beta's tessellator writes a quad as two triangles, six entries, the last three written with the
 * fourth vertex; otherwise four.
 */
@Mixin(Tessellator.class)
abstract class TessellatorMixin {
	@Shadow
	private static boolean TRIANGLE_MODE;
	@Shadow
	private int[] buffer;
	@Shadow
	private int bufferPosition;
	@Shadow
	private int addedVertexCount;
	@Shadow
	private int mode;
	@Shadow
	private boolean hasTexture;

	@Inject(method = "vertex(DDD)V", at = @At("RETURN"))
	private void oldfaithful$insetQuad(double x, double y, double z, CallbackInfo ci) {
		if (!TextureEdges.building || !hasTexture || mode != 7 || addedVertexCount % 4 != 0) return;
		TextureEdges.inset(buffer, bufferPosition, TRIANGLE_MODE ? 6 : 4);
	}
}
