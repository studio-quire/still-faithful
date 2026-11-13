package org.studioquire.oldfaithful.polish.mixin.obsidian;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.Obsidian;

/** Obsidian's hardness and the pickaxe's speed on it as in 1.0 ({@link Obsidian}), where the game times a break. */
@Mixin(Block.class)
abstract class BlockMixin {
	@ModifyExpressionValue(method = "getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F",
			at = @At(value = "FIELD", target = "Lnet/minecraft/block/Block;hardness:F"))
	private float oldfaithful$obsidianHardness(float hardness, PlayerEntity player) {
		if ((Object) this != Block.OBSIDIAN) return hardness;
		return Obsidian.hard(player, player.canHarvest((Block) (Object) this)) ? Obsidian.HARDNESS : hardness;
	}

	@WrapOperation(method = "getHardness(Lnet/minecraft/entity/player/PlayerEntity;)F",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getBlockBreakingSpeed(Lnet/minecraft/block/Block;)F"))
	private float oldfaithful$obsidianSpeed(PlayerEntity player, Block block, Operation<Float> original) {
		if ((Object) this != Block.OBSIDIAN || !Obsidian.timedAsBeta(player)) return original.call(player, block);
		Obsidian.asBeta++;
		try {
			return original.call(player, block);
		} finally {
			Obsidian.asBeta--;
		}
	}

	@ModifyReturnValue(method = "getHardness()F", at = @At("RETURN"))
	private float oldfaithful$obsidianHardnessAsked(float hardness) {
		return (Object) this == Block.OBSIDIAN ? Obsidian.HARDNESS : hardness;
	}
}
