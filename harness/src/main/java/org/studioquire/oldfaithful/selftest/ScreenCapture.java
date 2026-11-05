package org.studioquire.oldfaithful.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * One screen's pixels, read back from the hidden window right after the screen is drawn (in the frame, before it's
 * shown), with the mouse at a chosen place in the screen's units. For looking at a screen's layout, or with no screen open
 * the world, without a window anyone can see.
 */
public final class ScreenCapture {
	public static volatile ScreenCapture pending;

	public final int mouseX;
	public final int mouseY;
	private final File file;
	private volatile boolean done;

	private ScreenCapture(File file, int mouseX, int mouseY) {
		this.file = file;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}

	/** Asks for the next frame's screen; {@link #done} once saved. Mouse -1,-1: pointing at nothing. */
	public static ScreenCapture request(File file, int mouseX, int mouseY) {
		ScreenCapture c = new ScreenCapture(file, mouseX, mouseY);
		pending = c;
		return c;
	}

	public boolean done() {
		return done;
	}

	public void take() {
		pending = null;
		int w = Display.getWidth(), h = Display.getHeight();
		ByteBuffer px = BufferUtils.createByteBuffer(w * h * 4);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = ((h - 1 - y) * w + x) * 4;
				img.setRGB(x, y, (px.get(i) & 0xFF) << 16 | (px.get(i + 1) & 0xFF) << 8 | px.get(i + 2) & 0xFF);
			}
		}
		try {
			file.getAbsoluteFile().getParentFile().mkdirs();
			ImageIO.write(img, "png", file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		done = true;
	}
}
