package org.studioquire.oldfaithful.fixes.mixin.names;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.item.DyeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** #33, as for slabs ({@link SlabBlockItemMixin}): a dye with a data value outside the sixteen colours. */
@Mixin(DyeItem.class)
abstract class DyeItemMixin {
	@ModifyExpressionValue(method = "getTranslationKey(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getDamage()I"))
	private int oldfaithful$inRange(int damage) {
		return damage >= 0 && damage < DyeItem.names.length ? damage : 0;
	}
}
