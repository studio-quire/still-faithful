package org.studioquire.oldfaithful.polish.mixin.skins;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.Quad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("faces")
	Quad[] oldfaithful$faces();
}
