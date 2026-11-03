package org.studioquire.oldfaithful.fixes.mixin.repeaters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.TrapdoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #41: doors, trapdoors, note blocks, dispensers, TNT and plain rails' junctions look at their power only when the
 * neighbour that changed says it can emit power, and the repeater says it can't (wire uses that answer to decide what it
 * connects to, so it has to stay). A repeater switching on or off beside one of them went unnoticed until some other
 * source nearby changed. Here a repeater counts as a source for those checks alone.
 */
@Mixin({DoorBlock.class, TrapdoorBlock.class, NoteBlock.class, DispenserBlock.class, TntBlock.class, RailBlock.class})
abstract class PoweredBlocksMixin {
	@WrapOperation(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;canEmitRedstonePower()Z"))
	private boolean oldfaithful$repeaterCounts(Block neighbor, Operation<Boolean> original) {
		return original.call(neighbor) || neighbor instanceof RepeaterBlock;
	}
}
