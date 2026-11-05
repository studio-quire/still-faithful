package org.studioquire.oldfaithful.options.settings;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.gui.screen.Screen;

/**
 * A button on a mod's page that does something rather than holding a value, in the top row beside Enabled: the core's Lock Rules.
 * Its label is asked each time the page is built, so it can read differently, or not be there, as things stand.
 */
public final class PageButton {
	final Supplier<String> label;
	final String hint;
	final Supplier<String> locked;
	final Function<Screen, Screen> click;

	/**
	 * @param label what it reads now, or null when it isn't shown
	 * @param hint said when it's pointed at
	 * @param locked why it can't be pressed now, said in place of the hint; null when it can
	 * @param click given the page, the screen to open over it (which comes back to it), or null to stay on the page
	 */
	public PageButton(Supplier<String> label, String hint, Supplier<String> locked, Function<Screen, Screen> click) {
		this.label = label;
		this.hint = hint;
		this.locked = locked;
		this.click = click;
	}

	String label() {
		return label.get();
	}

	String lockedReason() {
		return locked == null ? null : locked.get();
	}
}
