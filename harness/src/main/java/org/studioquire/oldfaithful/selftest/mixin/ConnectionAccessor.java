package org.studioquire.oldfaithful.selftest.mixin;

import java.util.List;

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ConnectionAccessor {
	/** Packets read and not yet handled. */
	@Accessor("readQueue")
	List<?> oldfaithful$readQueue();
}
