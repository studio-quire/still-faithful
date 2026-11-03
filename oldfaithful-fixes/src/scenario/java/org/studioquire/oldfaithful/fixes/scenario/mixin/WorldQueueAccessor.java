package org.studioquire.oldfaithful.fixes.scenario.mixin;

import java.util.TreeSet;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface WorldQueueAccessor {
	@Accessor("scheduledUpdates")
	TreeSet<?> oldfaithful$queue();
}
