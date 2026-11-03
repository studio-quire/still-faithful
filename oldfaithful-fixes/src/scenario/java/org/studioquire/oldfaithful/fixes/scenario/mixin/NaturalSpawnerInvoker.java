package org.studioquire.oldfaithful.fixes.scenario.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.world.NaturalSpawner;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-only (#66): the spawner's test of a place for a mob of a group. */
@Mixin(NaturalSpawner.class)
public interface NaturalSpawnerInvoker {
	@Invoker("isValidSpawnPos")
	static boolean oldfaithful$isValidSpawnPos(SpawnGroup group, World world, int x, int y, int z) {
		throw new AssertionError();
	}
}
