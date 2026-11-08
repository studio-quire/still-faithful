package org.studioquire.oldfaithful.fixes.mixin.squidsteps;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * #76: moving plays footsteps and calls the block's onSteppedOn for anything that doesn't say otherwise. Spiders, wolves,
 * items, boats, minecarts, TNT and falling blocks say otherwise; squid don't, so a squid on the sea floor walked: stone
 * and sand steps from under the water, redstone ore lit up. (The mapped name reads backwards: true means it walks.)
 */
@Mixin(SquidEntity.class)
abstract class SquidEntityMixin extends Entity {
	SquidEntityMixin(World world) {
		super(world);
	}

	@Override
	protected boolean bypassesSteppingEffects() {
		return false;
	}
}
