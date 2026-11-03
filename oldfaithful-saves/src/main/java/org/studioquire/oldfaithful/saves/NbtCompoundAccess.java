package org.studioquire.oldfaithful.saves;

import java.util.Map;

import net.minecraft.nbt.NbtElement;

/** NbtCompound's entries (b1.7.3 has no remove), through our accessor mixin. */
public interface NbtCompoundAccess {
	Map<String, NbtElement> oldfaithful$entries();
}
