package org.studioquire.oldfaithful.speed.mixin;

import net.minecraft.client.render.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Frustum.class)
public interface FrustumAccessor {
	@Accessor("INSTANCE")
	static Frustum oldfaithful$instance() {
		throw new AssertionError();
	}
}
