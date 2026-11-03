package org.studioquire.oldfaithful.saves.mixin;

import java.util.Map;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.studioquire.oldfaithful.saves.NbtCompoundAccess;

@Mixin(NbtCompound.class)
abstract class NbtCompoundAccessor implements NbtCompoundAccess {
	@Shadow
	private Map entries;

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, NbtElement> oldfaithful$entries() {
		return entries;
	}
}
