package org.studioquire.oldfaithful.fixes.mixin.cursor;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #39: a container screen draws the stack held on the cursor in the middle of its frame, 32 units forward with depth
 * testing on, then the labels ("Crafting", "Inventory") with depth testing off, while the inventory's player figure
 * stands at 50. So the held item passed behind the figure and under the labels. It's now drawn last, after the
 * labels and buttons, far enough forward to clear the figure, with the same lighting the screen gave it.
 */
@Mixin(HandledScreen.class)
abstract class HandledScreenMixin extends Screen {
	/** In front of the player figure (at 50, about 30 deep) and of the slot items (near 0). */
	private static final float CURSOR_Z = 232.0F;

	@Shadow
	private static ItemRenderer itemRenderer;

	// In render, these two calls draw only the cursor's stack (the slots are drawn in drawSlot).
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/ItemRenderer;renderGuiItem(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/client/texture/TextureManager;Lnet/minecraft/item/ItemStack;II)V"))
	private boolean oldfaithful$notMidFrame(ItemRenderer renderer, TextRenderer text, TextureManager textures, ItemStack stack, int x, int y) {
		return false;
	}

	@WrapWithCondition(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/ItemRenderer;renderGuiItemDecoration(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/client/texture/TextureManager;Lnet/minecraft/item/ItemStack;II)V"))
	private boolean oldfaithful$countNotMidFrame(ItemRenderer renderer, TextRenderer text, TextureManager textures, ItemStack stack, int x, int y) {
		return false;
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void oldfaithful$cursorOnTop(int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ItemStack stack = this.minecraft.player.inventory.getCursorStack();
		if (stack == null) return;
		GL11.glPushMatrix();
		GL11.glRotatef(120.0F, 1.0F, 0.0F, 0.0F);
		Lighting.turnOn();
		GL11.glPopMatrix();
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0F, 0.0F, CURSOR_Z);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		itemRenderer.renderGuiItem(this.textRenderer, this.minecraft.textureManager, stack, mouseX - 8, mouseY - 8);
		itemRenderer.renderGuiItemDecoration(this.textRenderer, this.minecraft.textureManager, stack, mouseX - 8, mouseY - 8);
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		GL11.glPopMatrix();
		// As the screen leaves it: lighting enabled without its lights, depth testing on.
		Lighting.turnOff();
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}
}
