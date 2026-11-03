package org.studioquire.oldfaithful.polish.mixin.dusk;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.DuskLight;

/**
 * #6: a chunk's mesh is built with each vertex's light pairs in it instead of the darkness baked in (DuskLight): the
 * rebuild's region is the one whose light readings DuskLight answers, and each block goes through DuskLight's passes.
 * The chunk remembers whether any of its vertices had to be lit as beta lit them (then it's rebuilt at each step).
 */
@Mixin(ChunkBuilder.class)
abstract class ChunkBuilderMixin implements DuskLight.Section {
	@Shadow
	public World world;
	@Shadow
	public int x;
	@Shadow
	public int y;
	@Shadow
	public int z;
	@Shadow
	public int sizeX;
	@Shadow
	public int sizeY;
	@Shadow
	public int sizeZ;

	@Unique
	private boolean oldfaithful$stepped;

	@Override
	public boolean oldfaithful$stepped() {
		return oldfaithful$stepped;
	}

	@Override
	public void oldfaithful$stepped(boolean stepped) {
		oldfaithful$stepped = stepped;
	}

	@ModifyExpressionValue(method = "rebuild", at = @At(value = "NEW", target = "Lnet/minecraft/world/WorldRegion;"))
	private WorldRegion oldfaithful$lightPairs(WorldRegion region) {
		// The region the rebuild asks for: its blocks plus one all round.
		DuskLight.beginRebuild(this, region, world, x - 1, y - 1, z - 1, x + sizeX, y + sizeY, z + sizeZ);
		return region;
	}

	@WrapOperation(method = "rebuild", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;render(Lnet/minecraft/block/Block;III)Z"))
	private boolean oldfaithful$lightAtDraw(BlockRenderManager renderer, Block block, int x, int y, int z, Operation<Boolean> original) {
		return DuskLight.render(renderer, block, x, y, z, original);
	}

	@Inject(method = "rebuild", at = @At("RETURN"))
	private void oldfaithful$done(CallbackInfo ci) {
		DuskLight.endRebuild();
	}
}
