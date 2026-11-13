package org.studioquire.oldfaithful.options.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * The Mods menu: a wide button for each mod's page, five to a page, with whether it's on said beside it. Where something has put a button at the top of
 * the menu (More Faithful's preset), it sits alone in the first row of every page, as Enabled does on a mod's page, and four mods follow.
 */
public class SettingsModsScreen extends SettingsScreen {
	/** The menu's own button's id: clear of the wide buttons' and of Done's. */
	static final int MENU_BUTTON = 100;

	public SettingsModsScreen(Screen parent) {
		super(parent);
		this.title = "Mods";
	}

	/** Asked every frame, so its label (which reads files) is asked only as the page is built. */
	private static PageButton menuButton() {
		return SettingsRegistry.get().modsButton();
	}

	@Override
	int rows() {
		return menuButton() == null ? ROWS : ROWS - 1;
	}

	@Override
	int firstRow() {
		return menuButton() == null ? 0 : 1;
	}

	@Override
	public void init() {
		super.init();
		PageButton top = menuButton();
		if (top == null) return;
		ButtonWidget b = new ButtonWidget(MENU_BUTTON, width / 2 - SHORT / 2, rowY(0), SHORT, 20, top.label());
		b.active = top.lockedReason() == null;
		buttons.add(b);
	}

	@Override
	List<Entry> entries() {
		SettingsRegistry registry = SettingsRegistry.get();
		List<Entry> mods = new ArrayList<>();
		for (SettingsRegistry.Group g : registry.mods()) {
			String on = g.modSwitch == null ? "" : g.modSwitch.word(registry.value(g.modSwitch));
			String state = on + (registry.waitingForRestart(g) ? WAITING : "");
			mods.add(new Entry(g.title + "...", g.modSwitch == null ? null : g.modSwitch.hint, state.isEmpty() ? null : state,
					parent -> new SettingsGroupScreen(parent, null, Collections.singletonList(g))));
		}
		return mods;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		PageButton top = menuButton();
		if (button.id != MENU_BUTTON || top == null) {
			super.buttonClicked(button);
			return;
		}
		if (!button.active) return;
		Screen next = top.click.apply(this);
		if (next != null) minecraft.setScreen(next);
		else init(minecraft, width, height);
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		super.render(mouseX, mouseY, delta);
		PageButton top = menuButton();
		if (top == null) return;
		for (Object o : buttons) {
			ButtonWidget b = (ButtonWidget) o;
			if (b.id == MENU_BUTTON && mouseX >= b.x && mouseY >= b.y && mouseX < b.x + SHORT && mouseY < b.y + 20) {
				String locked = top.lockedReason();
				hint(locked != null ? locked : top.hint());
			}
		}
	}

	@Override
	String waitingLine() {
		SettingsRegistry registry = SettingsRegistry.get();
		for (SettingsRegistry.Group g : registry.mods()) {
			if (registry.waitingForRestart(g)) return WAITING + " Takes effect after a restart";
		}
		return null;
	}
}
