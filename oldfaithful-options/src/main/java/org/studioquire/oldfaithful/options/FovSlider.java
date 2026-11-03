package org.studioquire.oldfaithful.options;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenScaler;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The FOV slider, drawn as beta's own sliders are (the dark button and the knob from gui.png), on a button of its own
 * rather than beta's SliderWidget, which needs one of beta's options behind it. Like Old Faithful's fixed sliders
 * (#40), it follows the mouse to the pixel and lands on whole degrees.
 */
public final class FovSlider extends ButtonWidget {
	/** Beta's sliders: 150 wide, the knob 8, so the knob's left edge travels 142. */
	private static final int TRACK = 142;

	private final Minecraft minecraft;
	private boolean dragging;

	public FovSlider(Minecraft minecraft, int id, int x, int y) {
		super(id, x, y, 150, 20, Fov.label(Fov.degrees));
		this.minecraft = minecraft;
	}

	@Override
	protected int getYImage(boolean hovered) {
		return 0;
	}

	private int scale() {
		return new ScreenScaler(minecraft.options, minecraft.displayWidth, minecraft.displayHeight).scaleFactor;
	}

	private void set(int pixelX, int mouseX) {
		float x = Fov.pointerX(pixelX, scale(), mouseX);
		Fov.degrees = Fov.fromSlider(Math.max(0.0F, Math.min(1.0F, (x - (this.x + 4)) / TRACK)));
		this.text = Fov.label(Fov.degrees);
	}

	@Override
	protected void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
		if (!this.visible) return;
		if (this.dragging) set(Mouse.getX(), mouseX);
		float units = Fov.toSlider(Fov.degrees) * TRACK;
		int s = scale();
		float knob = this.x + (float) Math.floor(units * s) / s;
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glPushMatrix();
		GL11.glTranslatef(knob - (int) knob, 0.0F, 0.0F);
		this.drawTexture((int) knob, this.y, 0, 66, 4, 20);
		this.drawTexture((int) knob + 4, this.y, 196, 66, 4, 20);
		GL11.glPopMatrix();
	}

	@Override
	public boolean isMouseOver(Minecraft minecraft, int mouseX, int mouseY) {
		if (!this.active || !this.visible || mouseX < this.x || mouseY < this.y || mouseX >= this.x + this.width || mouseY >= this.y + this.height) {
			return false;
		}
		set(Mouse.getEventX(), mouseX);
		this.dragging = true;
		return true;
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY) {
		this.dragging = false;
	}
}
