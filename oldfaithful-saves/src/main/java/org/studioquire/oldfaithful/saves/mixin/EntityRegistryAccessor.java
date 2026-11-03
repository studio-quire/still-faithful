package org.studioquire.oldfaithful.saves.mixin;

import java.util.Map;

import net.minecraft.entity.EntityRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** docs/bugs.md #65: which class an entity id names, without making one (ChunkLoad's spawner repair). */
@Mixin(EntityRegistry.class)
public interface EntityRegistryAccessor {
	@Accessor("idToClass")
	static Map<String, Class<?>> oldfaithful$idToClass() {
		throw new AssertionError();
	}
}
