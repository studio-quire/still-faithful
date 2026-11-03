package org.studioquire.oldfaithful.fixes.mixin.names;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.SlabBlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #33: a slab item's name is looked up by its data value in the four slab kinds, so a slab with any other value (from
 * a server, an editor or another mod) crashed the game wherever its name was shown, such as hovering over it. Out of
 * range now reads as the first kind, stone, as later versions do.
 */
@Mixin(SlabBlockItem.class)
abstract class SlabBlockItemMixin {
	@ModifyExpressionValue(method = "getTranslationKey(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getDamage()I"))
	private int oldfaithful$inRange(int damage) {
		return damage >= 0 && damage < SlabBlock.names.length ? damage : 0;
	}
}
