package org.studioquire.oldfaithful.hardening.mixin.client;

import java.io.IOException;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.DataTrackerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.studioquire.oldfaithful.hardening.packet.ServerContent;

/**
 * Tracked entity data from a server:
 * <ul>
 * <li>A packet's list of values ends at a marker byte, and the client reads values until it sees one; a server that
 * never sends it grows the list until the client runs out of memory. Bounded at {@link
 * ServerContent#MAX_TRACKED_ENTRIES}.</li>
 * <li>A value of another type than the entity's (an int where it keeps a byte) is stored as sent, and the entity's
 * next tick fails casting it, crashing the game. Refused when the packet is handled instead.</li>
 * </ul>
 */
@Mixin(DataTracker.class)
abstract class DataTrackerMixin {
	@WrapOperation(method = "readEntries", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"))
	private static boolean oldfaithful$boundEntries(java.util.ArrayList list, Object entry, Operation<Boolean> original) throws IOException {
		ServerContent.checkTrackedCount(list);
		return original.call(list, entry);
	}

	@WrapOperation(method = "writeUpdatedEntries", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/data/DataTrackerEntry;set(Ljava/lang/Object;)V"))
	private void oldfaithful$sameType(DataTrackerEntry existing, Object value, Operation<Void> original) {
		ServerContent.checkTrackedValue(existing.getId(), existing.get(), value);
		original.call(existing, value);
	}
}
