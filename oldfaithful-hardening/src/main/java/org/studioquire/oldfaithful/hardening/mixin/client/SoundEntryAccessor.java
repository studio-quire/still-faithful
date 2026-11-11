package org.studioquire.oldfaithful.hardening.mixin.client;

import java.util.Map;

import net.minecraft.client.sound.SoundEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sound table's pools, by name: what {@code get} picks from. */
@Mixin(SoundEntry.class)
public interface SoundEntryAccessor {
	@Accessor("weightedSoundSet")
	Map oldfaithful$pools();
}
