package org.studioquire.oldfaithful.fixes.mixin.respawn;

import java.util.Set;

import net.minecraft.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** #49: the entities the server gave this world. */
@Mixin(ClientWorld.class)
public interface ClientWorldAccessor {
	@Accessor("forcedEntities")
	Set oldfaithful$forcedEntities();
}
