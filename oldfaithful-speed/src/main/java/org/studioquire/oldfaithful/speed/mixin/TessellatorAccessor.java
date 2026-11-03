package org.studioquire.oldfaithful.speed.mixin;

import net.minecraft.client.render.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Tessellator.class)
public interface TessellatorAccessor {
	@Accessor("drawing")
	boolean oldfaithful$drawing();
}
