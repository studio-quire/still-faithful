package org.studioquire.oldfaithful.selftest.mixin;

import java.util.List;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
	/** Render chunks waiting to be (re)built. */
	@Accessor("dirtyChunks")
	List<?> oldfaithful$dirtyChunks();

	/** Every render chunk in view distance. */
	@Accessor("chunks")
	net.minecraft.client.render.chunk.ChunkBuilder[] oldfaithful$chunks();

	/** Frames left in which beta draws no entities, after the renderer reloads. */
	@Accessor("entityRenderCooldown")
	int oldfaithful$entityRenderCooldown();
}
