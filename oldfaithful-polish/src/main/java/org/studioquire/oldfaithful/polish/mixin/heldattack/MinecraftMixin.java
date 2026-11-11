package org.studioquire.oldfaithful.polish.mixin.heldattack;

import java.lang.ref.WeakReference;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.InteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held Attack. While the attack button is held, beta repeats the whole click every 5 ticks ({@code tick}'s third
 * {@code handleMouseClick}, after the keyboard) on whatever the crosshair is on then. Mining doesn't need it (that's
 * {@code handleMouseDown}, every tick), but on a mob it's a hit: holding on through a block you've just mined hits the
 * cow behind it, and dragging the crosshair across a tame wolf hits the wolf. Here the repeat hits only the entity the
 * press landed on; any other it passes over, and every entity if the press was on a block or on air. The rest of the
 * repeated click is beta's (the swing, a block's attack, a miss's wait), and a press hits whatever is under the
 * crosshair, as ever. Client input only: a server just sees fewer hits.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Shadow
	public HitResult crosshairTarget;
	/** Weak, so a press held through leaving a world doesn't keep that world. */
	@Unique
	private WeakReference<Entity> oldfaithful$pressedOn = new WeakReference<>(null);
	@Unique
	private boolean oldfaithful$repeating;

	/** The press: the first call, in the mouse event loop (the second is the right button's). */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleMouseClick(I)V", ordinal = 0))
	private void oldfaithful$press(Minecraft mc, int button, Operation<Void> original) {
		HitResult target = crosshairTarget;
		oldfaithful$pressedOn = new WeakReference<>(target != null && target.type == HitResultType.ENTITY ? target.entity : null);
		original.call(mc, button);
	}

	/** The repeat while the attack button is held (the fourth call is the right button's). */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleMouseClick(I)V", ordinal = 2))
	private void oldfaithful$repeat(Minecraft mc, int button, Operation<Void> original) {
		oldfaithful$repeating = true;
		try {
			original.call(mc, button);
		} finally {
			oldfaithful$repeating = false;
		}
	}

	@WrapOperation(method = "handleMouseClick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/InteractionManager;attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V"))
	private void oldfaithful$onlyWhatWasPressed(InteractionManager manager, PlayerEntity player, Entity target, Operation<Void> original) {
		if (oldfaithful$repeating && target != oldfaithful$pressedOn.get()) return;
		original.call(manager, player, target);
	}

	/**
	 * Let go, a screen open or the window left: the next hold goes by its own press. (Beta's repeat can also run on a
	 * held button whose press another mod took, which then hits no entity.)
	 */
	@Inject(method = "handleMouseDown", at = @At("HEAD"))
	private void oldfaithful$letGo(int button, boolean holding, CallbackInfo ci) {
		if (!holding) oldfaithful$pressedOn = new WeakReference<>(null);
	}
}
