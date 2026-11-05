package org.studioquire.oldfaithful.options.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * The settings, reached from Options: a wide button for each menu there is (Fixes, Changes, Mods), laid out as beta lays out its options (from a sixth of the
 * way down, Done at the bottom). With only one menu, {@link #open} goes straight to it. Below the buttons, the screen's few words: a hint for the button
 * pointed at, and a line when a change waits for a restart. Wide buttons lead somewhere; a switch, on {@link SettingsGroupScreen}, is shorter.
 */
public class SettingsScreen extends Screen {
	static final int DONE = 200, PREVIOUS = 201, NEXT = 202;
	/** Beta's two widths, Video Settings' and an option's. */
	static final int WIDE = 200, SHORT = 150;
	/** The rows above the hint. */
	static final int ROWS = 5;
	/** Hint lines start here (below the fifth row of buttons) and run two lines at most; the restart line is below. */
	static final int HINT_Y = 124, FOOTER_Y = 150, HINT_WIDTH = 300;
	static final int HINT_COLOUR = 0xA0A0A0, FOOTER_COLOUR = 0xE0E0E0;
	static final String WAITING = "*";

	/** A wide button: its label, its hint or null, what's said beside it or null, and the screen it opens. */
	static final class Entry {
		final String label, hint, state;
		final Function<Screen, Screen> opens;

		Entry(String label, String hint, String state, Function<Screen, Screen> opens) {
			this.label = label;
			this.hint = hint;
			this.state = state;
			this.opens = opens;
		}
	}

	protected final Screen parent;
	protected String title = defaultTitle();
	/** Kept through a visit further in, so Done comes back to the same page. */
	protected int page;
	private List<Entry> entries;

	/** "Old Faithful" with Old Faithful's options mod, "Mods" on any other edition. */
	public static String defaultTitle() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("oldfaithful-options") ? "Old Faithful" : "Mods";
	}

	/** What Options opens: the top page, or the one menu there is. */
	public static Screen open(Screen parent) {
		List<Entry> menus = menus(SettingsRegistry.get());
		return menus.size() == 1 ? menus.get(0).opens.apply(parent) : new SettingsScreen(parent);
	}

	public SettingsScreen(Screen parent) {
		this.parent = parent;
	}

	private static List<Entry> menus(SettingsRegistry registry) {
		List<Entry> menus = new ArrayList<>();
		if (!registry.fixes().isEmpty()) menus.add(menu("Fixes", registry.fixes()));
		if (!registry.changes().isEmpty()) menus.add(menu("Changes", registry.changes()));
		if (!registry.mods().isEmpty()) menus.add(new Entry("Mods...", "Turn mods on or off, and change their settings.", null, SettingsModsScreen::new));
		return menus;
	}

	private static Entry menu(String title, List<SettingsRegistry.Group> sections) {
		List<String> titles = new ArrayList<>();
		for (SettingsRegistry.Group g : sections) titles.add(g.title);
		return new Entry(title + "...", String.join(", ", titles) + ".", null, parent -> new SettingsGroupScreen(parent, title, sections));
	}

	/** The wide buttons, over every page. */
	List<Entry> entries() {
		return menus(SettingsRegistry.get());
	}

	/** The line under the hint, or null. */
	String waitingLine() {
		return SettingsRegistry.get().anyWaitingForRestart() ? "Some changes take effect after a restart" : null;
	}

	private int entryPages() {
		return Math.max(1, (entries.size() + ROWS - 1) / ROWS);
	}

	private List<Entry> shownEntries() {
		return entries.subList(page * ROWS, Math.min(entries.size(), (page + 1) * ROWS));
	}

	@Override
	public void init() {
		SettingsRegistry.get().reload();
		entries = entries();
		page = Math.min(page, entryPages() - 1);
		addArrows(entryPages());
		List<Entry> shown = shownEntries();
		for (int i = 0; i < shown.size(); i++) {
			buttons.add(new ButtonWidget(i, width / 2 - WIDE / 2, rowY(i), WIDE, 20, shown.get(i).label));
		}
		addDone();
	}

	/** Turning a page rebuilds the buttons while beta's click loop is still going through them: with the arrows first, it can't reach one a second time. */
	void addArrows(int pages) {
		if (pages > 1) {
			ButtonWidget previous = new ButtonWidget(PREVIOUS, width / 2 - 124, height / 6 + 168, 20, 20, "<");
			ButtonWidget next = new ButtonWidget(NEXT, width / 2 + 104, height / 6 + 168, 20, 20, ">");
			previous.active = page > 0;
			next.active = page < pages - 1;
			buttons.add(previous);
			buttons.add(next);
		}
	}

	void addDone() {
		buttons.add(new ButtonWidget(DONE, width / 2 - 100, height / 6 + 168, "Done"));
	}

	int column(int i) {
		return width / 2 - 155 + i % 2 * 160;
	}

	int rowY(int row) {
		return height / 6 + 24 * row;
	}

	String paged(String text, int pages) {
		return pages > 1 ? text + " (" + (page + 1) + "/" + pages + ")" : text;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) return;
		if (button.id == DONE) {
			minecraft.setScreen(parent);
		} else if (button.id == PREVIOUS || button.id == NEXT) {
			page += button.id == NEXT ? 1 : -1;
			init(minecraft, width, height);
		} else if (button.id < shownEntries().size()) {
			minecraft.setScreen(shownEntries().get(button.id).opens.apply(this));
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		renderBackground();
		drawCenteredTextWithShadow(textRenderer, paged(title, entryPages()), width / 2, 20, 0xFFFFFF);
		super.render(mouseX, mouseY, delta);
		List<Entry> shown = shownEntries();
		for (int i = 0; i < shown.size(); i++) {
			if (shown.get(i).state != null) drawTextWithShadow(textRenderer, shown.get(i).state, width / 2 + WIDE / 2 + 5, rowY(i) + 6, HINT_COLOUR);
		}
		ButtonWidget pointed = pointedAt(mouseX, mouseY, WIDE);
		if (pointed != null && pointed.id < shown.size() && shown.get(pointed.id).hint != null) hint(shown.get(pointed.id).hint);
		String waiting = waitingLine();
		if (waiting != null) footer(waiting);
	}

	/** The button of this width under the mouse, active or not: an inactive one still has a hint. */
	ButtonWidget pointedAt(int mouseX, int mouseY, int buttonWidth) {
		for (Object o : buttons) {
			ButtonWidget b = (ButtonWidget) o;
			if (b.visible && mouseX >= b.x && mouseY >= b.y && mouseX < b.x + buttonWidth && mouseY < b.y + 20 && b.id < DONE) return b;
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
