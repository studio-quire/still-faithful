package org.studioquire.oldfaithful.selftest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.studioquire.oldfaithful.selftest.mixin.ScreenAccessor;

/**
 * Screens the conflict harness has to see on a real client, read as a player would: what was actually drawn, and buttons by their labels. It knows no class of
 * the core's, so a vanilla client can run it too. {@code TextRendererRecordMixin} fills {@link #drawn}.
 */
public final class LibraryScreens {
	public static final boolean RECORD = Boolean.getBoolean("oldfaithful.selftest.recordText");
	/** Text drawn since the last {@link #clear}. */
	public static final List<String> drawn = Collections.synchronizedList(new ArrayList<>());

	private LibraryScreens() {
	}

	public static void clear() {
		drawn.clear();
	}

	public static String text() {
		synchronized (drawn) {
			return String.join(" | ", new java.util.LinkedHashSet<>(drawn));
		}
	}

	public static void shot(String name) {
		ScreenCapture.request(new File("shots/" + name + ".png"), -1, -1);
	}

	/** Clicks a button by its label, as a mouse would; false if there's no such button. */
	public static boolean click(Screen screen, String label) {
		for (Object o : ((ScreenAccessor) screen).oldfaithful$buttons()) {
			ButtonWidget button = (ButtonWidget) o;
			if (label.equals(button.text)) {
				((ScreenAccessor) screen).oldfaithful$press(button);
				return true;
			}
		}
		return false;
	}
}
