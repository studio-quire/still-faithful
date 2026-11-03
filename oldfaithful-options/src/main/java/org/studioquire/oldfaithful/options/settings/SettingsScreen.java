package org.studioquire.oldfaithful.options.settings;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Old Faithful's settings, reached from Options: a button for each group, laid out as beta lays out its options (two
 * columns of 150-wide buttons from a sixth of the way down, Done at the bottom), each opening a
 * {@link SettingsGroupScreen}. Below the buttons, the screen's few words: a hint for the button pointed at, and a line
 * when a change waits for a restart.
 */
public class SettingsScreen extends Screen {
	static final int DONE = 200;
	/** Hint lines start here (below the fifth row of buttons) and run two lines at most; the restart line is below. */
	static final int HINT_Y = 124, FOOTER_Y = 150, HINT_WIDTH = 300;
	static final int HINT_COLOUR = 0xA0A0A0, FOOTER_COLOUR = 0xE0E0E0;

	protected final Screen parent;
	protected String title = "Old Faithful";
	private List<SettingsRegistry.Group> groups;

	public SettingsScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		SettingsRegistry registry = SettingsRegistry.get();
		registry.reload();
		groups = registry.groups();
		for (int i = 0; i < groups.size(); i++) {
			buttons.add(new ButtonWidget(i, column(i), row(i), 150, 20, groups.get(i).title + "..."));
		}
		buttons.add(new ButtonWidget(DONE, width / 2 - 100, height / 6 + 168, "Done"));
	}

	int column(int i) {
		return width / 2 - 155 + i % 2 * 160;
	}

	int row(int i) {
		return height / 6 + 24 * (i >> 1);
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) return;
		if (button.id == DONE) minecraft.setScreen(parent);
		else if (button.id < groups.size()) minecraft.setScreen(new SettingsGroupScreen(this, groups.get(button.id)));
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		renderBackground();
		drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
		super.render(mouseX, mouseY, delta);
		if (SettingsRegistry.get().anyWaitingForRestart()) footer("Some changes take effect after a restart");
	}

	/** The grid button under the mouse (all 150 wide), active or not: an inactive one still has a hint. */
	ButtonWidget pointedAt(int mouseX, int mouseY) {
		for (Object o : buttons) {
			ButtonWidget b = (ButtonWidget) o;
			if (b.visible && mouseX >= b.x && mouseY >= b.y && mouseX < b.x + 150 && mouseY < b.y + 20 && b.id < DONE) return b;
		}
		return null;
	}

	void hint(String text) {
		int y = height / 6 + HINT_Y;
		for (String line : wrap(text, HINT_WIDTH)) {
			drawCenteredTextWithShadow(textRenderer, line, width / 2, y, HINT_COLOUR);
			y += 10;
		}
	}

	void footer(String text) {
		drawCenteredTextWithShadow(textRenderer, text, width / 2, height / 6 + FOOTER_Y, FOOTER_COLOUR);
	}

	/** Word-wrapped to {@code width} in the game's font; a word wider than that stands on its own line. */
	List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() > 0 && textRenderer.getWidth(line + " " + word) > width) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0) line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) lines.add(line.toString());
		return lines;
	}
}
