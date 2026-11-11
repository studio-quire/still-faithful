package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For Culling: the planes beta's culler tests boxes against, and the camera it measures from. */
@Mixin(FrustumCuller.class)
public interface FrustumCullerAccessor {
	@Accessor("frustum")
	FrustumData oldfaithful$frustum();

	@Accessor("offsetX")
	double oldfaithful$offsetX();

	@Accessor("offsetY")
	double oldfaithful$offsetY();

	@Accessor("offsetZ")
	double oldfaithful$offsetZ();
}
