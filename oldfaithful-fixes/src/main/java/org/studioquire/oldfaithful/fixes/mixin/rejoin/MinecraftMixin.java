package org.studioquire.oldfaithful.fixes.mixin.rejoin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * #68, single player: opening a saved world, {@code setWorld} prepares the terrain (loads the chunks 128 blocks around)
 * before the saved player is read, so {@code prepareWorld} finds no player and centres on the world spawn; its own
 * "around the player if there is one" never applies. Rejoining farther than that from spawn, the player stood in
 * chunks nobody had loaded, and collisions treat an unloaded column as empty: for the first moments the walls around
 * weren't solid, the player could walk into one, then suffocated until pushed out. Now the terrain is prepared around
 * the position the player was saved at. The world is already the saved player's dimension (World's constructor reads
 * it), so the position belongs to it. Respawns and portals prepare after the player is placed and are unchanged.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Shadow public ClientPlayerEntity player;

	@Redirect(method = "prepareWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getSpawnPos()Lnet/minecraft/util/math/Vec3i;"))
	private Vec3i oldfaithful$aroundSavedPlayer(World world) {
		Vec3i spawn = world.getSpawnPos();
		if (this.player != null || world.getProperties() == null) return spawn;
		NbtCompound saved = world.getProperties().getPlayerNbt();
		if (saved == null || !saved.contains("Pos")) return spawn;
		NbtList pos = saved.getList("Pos");
		if (pos.size() != 3 || !(pos.get(0) instanceof NbtDouble) || !(pos.get(2) instanceof NbtDouble)) return spawn;
		double x = ((NbtDouble) pos.get(0)).value, z = ((NbtDouble) pos.get(2)).value;
		if (Double.isNaN(x) || Double.isNaN(z) || Math.abs(x) > 3.2E7 || Math.abs(z) > 3.2E7) return spawn;
		return new Vec3i(MathHelper.floor(x), spawn.y, MathHelper.floor(z));
	}
}
