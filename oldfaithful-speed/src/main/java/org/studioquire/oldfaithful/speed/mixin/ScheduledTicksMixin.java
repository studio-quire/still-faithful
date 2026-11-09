package org.studioquire.oldfaithful.speed.mixin;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.speed.TickSet;

/**
 * The server's set of scheduled block ticks through {@link TickSet}: the same members, hashed so they spread. The
 * constructor may have queued some already (a new world generates chunks to find its spawn), so any there move across.
 * Only if the set is still vanilla's HashSet. The due-time TreeSet beside it is untouched, so ticks run in the same
 * order.
 */
@Mixin(World.class)
abstract class ScheduledTicksMixin {
	@Shadow
	private Set scheduledUpdateSet;

	// The server's only constructor, named in full: the remapper turns a bare "<init>" into the first constructor of the
	// merged jar, a client one, which then matches nothing on the server.
	@Inject(method = "<init>(Lnet/minecraft/world/storage/WorldStorage;Ljava/lang/String;JLnet/minecraft/world/dimension/Dimension;)V", at = @At("RETURN"))
	@SuppressWarnings("unchecked")
	private void oldfaithful$tickSet(CallbackInfo ci) {
		if (scheduledUpdateSet != null && scheduledUpdateSet.getClass() == HashSet.class) {
			TickSet ticks = new TickSet();
			ticks.addAll(scheduledUpdateSet);
			scheduledUpdateSet = ticks;
		}
	}
}
