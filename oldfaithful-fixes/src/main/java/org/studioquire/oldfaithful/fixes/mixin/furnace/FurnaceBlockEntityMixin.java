package org.studioquire.oldfaithful.fixes.mixin.furnace;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #11: a furnace takes fuel by counting the stack down and emptying the slot, so burning a lava bucket used up the
 * bucket too. Lava buckets already name the empty bucket as what they leave behind (crafting uses it), so when the last
 * of a fuel that leaves something behind burns, that's put in the fuel slot. Only lava buckets do in beta.
 */
@Mixin(FurnaceBlockEntity.class)
abstract class FurnaceBlockEntityMixin extends BlockEntity {
	@Shadow
	private ItemStack[] inventory;

	@Inject(method = "tick", at = @At("HEAD"))
	private void oldfaithful$leavesBehind(CallbackInfo ci, @Share("leaves") LocalRef<Item> leaves) {
		ItemStack fuel = this.inventory[1];
		Item item = fuel == null || fuel.count != 1 ? null : fuel.getItem();
		leaves.set(item != null && item.hasCraftingReturnItem() ? item.getCraftingReturnItem() : null);
	}

	// Ticking empties the fuel slot only by burning its last item.
	@Inject(method = "tick", at = @At("TAIL"))
	private void oldfaithful$putBack(CallbackInfo ci, @Share("leaves") LocalRef<Item> leaves) {
		if (leaves.get() != null && this.inventory[1] == null) {
			this.inventory[1] = new ItemStack(leaves.get());
			this.markDirty();
		}
	}
}
