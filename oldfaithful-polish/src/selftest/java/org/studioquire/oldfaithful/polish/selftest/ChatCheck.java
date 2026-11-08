package org.studioquire.oldfaithful.polish.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.ScreenScaler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.selftest.mixin.ChatMessages;
import org.studioquire.oldfaithful.selftest.ScreenCapture;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;

/**
 * Chat's bars as long as each line ({@code render.chatBars}): messages put in the chat as the game shows them, in a
 * room of white wool open to the sky so every bar lies on the same plain wall, and each line's bar found in the picture
 * along its top row (the row above the text): where it ends,
 * against where it should (just past the line's text, or beta's 320 wide). One short line, lines of different lengths
 * with one long enough to wrap, and the same with the chat open. Pictures in local/run/fences/chat/ ("-polished",
 * "-beta").
 */
public final class ChatCheck implements WorldCheck {
	private static final String KEY = "render.chatBars";
	private static final File OUT = new File("../fences/chat");
	private static final int Y = 100;
	private static final String[] SEVERAL = {
			"Hello!",
			"Anyone seen the cave by the lake?",
			"It goes down past the lava, and there's a long tunnel with diamonds at the end of it, near the waterfall",
			"ok",
	};

	private interface Step {
		boolean tick(Minecraft mc);
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private SelfTest results;
	private World world;
	private boolean on;
	private double ox, oz;
	private Object[] saved;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(KEY);
		saved = new Object[] {mc.options.hideHud, mc.player.x, mc.player.y - mc.player.standingEyeHeight, mc.player.z};
		int bx = MathHelper.floor(mc.player.x) - 70, bz = MathHelper.floor(mc.player.z) - 70;
		ox = bx + 0.5;
		oz = bz + 0.5;
		for (int x = bx - 16; x <= bx + 16; x += 16) {
			for (int z = bz - 16; z <= bz + 16; z += 16) world.getBlockId(x, Y, z);
		}
		for (int x = bx - 6; x <= bx + 6; x++) {
			for (int z = bz - 4; z <= bz + 4; z++) {
				boolean wall = Math.abs(x - bx) == 6 || Math.abs(z - bz) == 4;
				world.setBlock(x, Y, z, Block.WOOL.id);
				for (int y = Y + 1; y <= Y + 7; y++) world.setBlock(x, y, z, wall ? Block.WOOL.id : 0);
			}
		}
		mc.options.hideHud = false;
		plan.add(wait(20));
		shot("one-short", false, "Hi there");
		shot("several", false, SEVERAL);
		shot("open", true, SEVERAL);
	}

	@Override
	public boolean tick(Minecraft mc) {
		PlayerEntity p = mc.player;
		p.noClip = true;
		p.velocityX = p.velocityY = p.velocityZ = 0;
		// Facing the north wall 4 away, 15 degrees up, so the chat's corner of the screen is all wall.
		p.setPositionAndAnglesKeepPrevAngles(ox, Y + 1, oz, 180, -15);
		p.prevYaw = p.yaw;
		p.prevPitch = p.pitch;
		world.setTime(6000);
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
		}
		mc.inGameHud.clearChat();
		mc.options.hideHud = (Boolean) saved[0];
		p.noClip = false;
		p.setPositionAndAnglesKeepPrevAngles((Double) saved[1], (Double) saved[2], (Double) saved[3], 0, 0);
		return true;
	}

	private Step wait(int ticks) {
		int[] left = {ticks};
		return c -> --left[0] <= 0;
	}

	private void shot(String name, boolean open, String... messages) {
		File file = new File(OUT, name + "-" + (on ? "polished" : "beta") + ".png");
		ScreenCapture[] capture = {null};
		plan.add(c -> {
			c.inGameHud.clearChat();
			for (String m : messages) c.inGameHud.addChatMessage(m);
			if (open) c.setScreen(new ChatScreen());
			return true;
		});
		plan.add(wait(5));
		plan.add(c -> {
			if (capture[0] == null) capture[0] = ScreenCapture.request(file, -1, -1);
			if (!capture[0].done()) return false;
			try {
				bars(c, name, ImageIO.read(file));
			} catch (IOException e) {
				results.fail("chat bars: reading " + file, e);
			}
			if (open) c.setScreen(null);
			return true;
		});
	}

	/** Each line's bar end in the picture against its text: beta's 2 + 320, or 2 + the text's width + 1. */
	private void bars(Minecraft mc, String name, BufferedImage img) {
		ScreenScaler scaler = new ScreenScaler(mc.options, mc.displayWidth, mc.displayHeight);
		int s = scaler.scaleFactor, h = scaler.getScaledHeight();
		// The lines as the chat holds them, newest first (a long message is split as addChatMessage splits it).
		List<String> shown = shownLines(mc);
		List<String> report = new ArrayList<>();
		boolean ok = !shown.isEmpty();
		for (int i = 0; i < shown.size(); i++) {
			int row = (h - 48 - 9 * i - 1) * s + s / 2;
			// The bar's right edge: the last place the wall brightens sharply from one pixel of the GUI to the next (the bar
			// halves it; the vignette darkens the bottom corner too, but smoothly), from a pixel that isn't a letter's
			// shadow (always 63): a comma or an apostrophe of the line above reaches 1 into this row.
			int last = -1;
			for (int x = 2 * s; x < 330 * s; x++) {
				int here = brightness(img, x, row);
				if (here > 63 && brightness(img, x + s, row) > here * 1.5) last = x;
			}
			int end = (last + 1) / s;
			int width = mc.textRenderer.getWidth(shown.get(i));
			int expected = on ? 2 + width + 1 : 322;
			report.add("\"" + shorten(shown.get(i)) + "\" " + end + " (" + expected + ")");
			ok &= Math.abs(end - expected) <= 1;
		}
		results.check("chat bars (" + (on ? "polished" : "beta") + "), " + name + ": each line's bar ends at " + String.join(", ", report), ok);
	}

	private static List<String> shownLines(Minecraft mc) {
		List<String> out = new ArrayList<>();
		for (Object o : ((ChatMessages) mc.inGameHud).oldfaithful$messages()) out.add(((net.minecraft.client.gui.hud.ChatHudLine) o).text);
		return out;
	}

	private static String shorten(String s) {
		return s.length() <= 16 ? s : s.substring(0, 13) + "...";
	}

	private static int brightness(BufferedImage img, int x, int y) {
		int c = img.getRGB(x, y);
		return ((c >> 16 & 0xFF) + (c >> 8 & 0xFF) + (c & 0xFF)) / 3;
	}
}
