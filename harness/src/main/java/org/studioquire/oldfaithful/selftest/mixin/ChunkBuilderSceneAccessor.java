package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkBuilder.class)
public interface ChunkBuilderSceneAccessor {
	/** False from the renderer moving it (reset) until its next rebuild: until then it draws nothing. */
	@Accessor("built")
	boolean oldfaithful$sceneBuilt();
}
