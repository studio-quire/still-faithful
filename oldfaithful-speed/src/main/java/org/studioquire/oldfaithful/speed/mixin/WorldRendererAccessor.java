package org.studioquire.oldfaithful.speed.mixin;

import java.util.List;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
	@Accessor("dirtyChunks")
	List<?> oldfaithful$dirtyChunks();
}
