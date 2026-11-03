package org.studioquire.oldfaithful.fixes.mixin.pick;

import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** #46: which kind of item (wool colour, slab, log, sapling) a block's data stands for. */
@Mixin(Block.class)
public interface BlockAccessor {
	@Invoker("getDroppedItemMeta")
	int oldfaithful$itemKind(int meta);
}
