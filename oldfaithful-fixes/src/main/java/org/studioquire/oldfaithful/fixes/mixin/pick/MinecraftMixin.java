package org.studioquire.oldfaithful.fixes.mixin.pick;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.PickBlock;

/** #46: pick block selects the block's item, of the same kind where items have kinds, and ignores mobs (PickBlock). */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Shadow
	public HitResult crosshairTarget;
	@Shadow
	public World world;
	@Shadow
	public ClientPlayerEntity player;

	@Inject(method = "handlePickBlock", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$pickItem(CallbackInfo ci) {
		HitResult target = this.crosshairTarget;
		if (target == null) return;
		ci.cancel();
		if (target.type != HitResultType.BLOCK) return;
		int id = this.world.getBlockId(target.blockX, target.blockY, target.blockZ);
		int meta = this.world.getBlockMeta(target.blockX, target.blockY, target.blockZ);
		int item = PickBlock.itemFor(id, meta);
		if (item == 0) return;
		int slot = PickBlock.hotbarSlot(this.player.inventory, item, ((BlockAccessor) Block.BLOCKS[id]).oldfaithful$itemKind(meta));
		if (slot >= 0) this.player.inventory.selectedSlot = slot;
	}
}
