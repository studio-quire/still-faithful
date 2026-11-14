package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.world.gen.feature.LargeOakTreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A big oak's plan after it grew: its leaf clusters (each one's base and where its branch leaves the trunk), its height. */
@Mixin(LargeOakTreeFeature.class)
public interface LargeOakAccess {
	@Accessor("branches")
	int[][] oldfaithful$branches();

	@Accessor("height")
	int oldfaithful$height();

	@Accessor("foliageClusterHeight")
	int oldfaithful$clusterHeight();
}
