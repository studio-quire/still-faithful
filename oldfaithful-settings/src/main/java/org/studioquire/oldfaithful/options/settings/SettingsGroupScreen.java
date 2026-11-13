package org.studioquire.oldfaithful.options.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Switches, as beta's Video Settings lays out its options: two columns of 150-wide buttons, each reading "Name: Value" and stepping to the next value on a
 * click, saved at once. A menu's sections follow one another on pages turned with arrows beside Done, each titled with its section; a section too long for a
 * page shares itself evenly over as many as it needs. A mod's page has what turns the mod on or off in a row of its own at the top, on each of its pages, with
 * the page's own button beside it when it has one ({@link PageButton}).
 * <p>
 * A change that waits for a restart marks its button with an asterisk and says so under the buttons. In multiplayer a
 * switch decided where the world runs reads "Server" and can't be pressed: the server's own file decides there, and
 * this game's setting is for single player.
 */
public class SettingsGroupScreen extends SettingsScreen {
	static final int PER_PAGE = 10;
	/** The page's own button's id: clear of the switches' and of Done's. */
	static final int PAGE_BUTTON = 100;

	/** One page's worth of a group. */
	private static final class Page {
		final SettingsRegistry.Group group;
		final List<Setting> settings;

		Page(SettingsRegistry.Group group, List<Setting> settings) {
			this.group = group;
			this.settings = settings;
		}
	}

	private final List<Page> pages = new ArrayList<>();
	/** The menu these groups are sections of; null for a mod's page. */
	private final String menu;
	private final List<ButtonWidget> switches = new ArrayList<>();
	/** Said under the buttons when a click couldn't be kept, or did more than was clicked. */
	private String said;

	SettingsGroupScreen(Screen parent, String menu, List<SettingsRegistry.Group> groups) {
		super(parent);
		this.menu = menu;
		for (SettingsRegistry.Group g : groups) {
			int room = g.hasTopRow() ? PER_PAGE - 2 : PER_PAGE, size = g.settings.size();
			int count = Math.max(1, (size + room - 1) / room), each = Math.max(1, (size + count - 1) / count);
			for (int from = 0; from == 0 || from < size; from += each) pages.add(new Page(g, g.settings.subList(from, Math.min(size, from + each))));
		}
	}

	/** A page of settings from wherever they're registered, coming back to {@code parent}: Create World's World Rules, every mod's on one list. */
	public static Screen of(Screen parent, String title, Supplier<String> note, List<Setting> settings) {
		SettingsRegistry.Group all = new SettingsRegistry.Group("", title, 0, note);
		all.settings.addAll(settings);
		return new SettingsGroupScreen(parent, null, Collections.singletonList(all));
	}

	private Page current() {
		return pages.get(page);
	}

	/** What's on this page, in the order of {@link #switches}. */
	private List<Setting> shown() {
		List<Setting> shown = new ArrayList<>();
		if (current().group.modSwitch != null) shown.add(current().group.modSwitch);
		shown.addAll(current().settings);
		return shown;
	}

	@Override
	public void init() {
		SettingsRegistry registry = SettingsRegistry.get();
		registry.reload();
		addArrows(pages.size());
		switches.clear();
		boolean server = minecraft.isWorldRemote();
		Setting modSwitch = current().group.modSwitch;
		PageButton pageButton = current().group.pageButton;
		String pageLabel = pageButton == null ? null : pageButton.label();
		// The top row: the switch alone in the middle, as it always was, or the switch and the page's button as a pair.
		int top = (modSwitch == null ? 0 : 1) + (pageLabel == null ? 0 : 1);
		if (modSwitch != null) add(registry, modSwitch, top == 1 ? width / 2 - SHORT / 2 : column(0), rowY(0), server);
		if (pageLabel != null) {
			ButtonWidget b = new ButtonWidget(PAGE_BUTTON, top == 1 ? width / 2 - SHORT / 2 : column(1), rowY(0), SHORT, 20, pageLabel);
			b.active = pageButton.lockedReason() == null;
			buttons.add(b);
		}
		List<Setting> settings = current().settings;
		for (int i = 0; i < settings.size(); i++) {
			add(registry, settings.get(i), column(i), rowY((i >> 1) + (top == 0 ? 0 : 1)), server);
		}
		addDone();
	}

	private void add(SettingsRegistry registry, Setting s, int x, int y, boolean server) {
		ButtonWidget b = new ButtonWidget(switches.size(), x, y, SHORT, 20, text(registry, s, server));
		b.active = !(server && s.serverDecides) && s.lockedReason() == null;
		buttons.add(b);
		switches.add(b);
	}

	static String text(SettingsRegistry registry, Setting s, boolean server) {
		if (server && s.serverDecides) return s.label + ": Server";
		if (s.store != null && s.store.read() == null) return s.label + ": -";
		String text = s.label + ": " + s.word(registry.value(s));
		return registry.waitingForRestart(s) ? text + WAITING : text;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) return;
		if (button.id == DONE) {
			minecraft.setScreen(parent);
		} else if (button.id == PREVIOUS || button.id == NEXT) {
			page += button.id == NEXT ? 1 : -1;
			said = null;
			init(minecraft, width, height);
		} else if (button.id == PAGE_BUTTON) {
			said = null;
			Screen next = current().group.pageButton.click.apply(this);
			if (next != null) minecraft.setScreen(next);
			else init(minecraft, width, height);
		} else if (button.id < switches.size()) {
			Setting s = shown().get(button.id);
			SettingsRegistry registry = SettingsRegistry.get();
			said = !registry.step(s) ? "Couldn't save that: " + s.label + " is as it was. The log says why." : s.store == null ? null : s.store.said();
			boolean remote = minecraft.isWorldRemote();
			List<Setting> shown = shown();
			for (int i = 0; i < shown.size(); i++) switches.get(i).text = text(registry, shown.get(i), remote);
		}
	}

	private String pageTitle() {
		String section = current().group.title;
		return paged(menu == null ? section : menu + ": " + section, pages.size());
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		renderBackground();
		drawCenteredTextWithShadow(textRenderer, pageTitle(), width / 2, 20, 0xFFFFFF);
		for (Object o : buttons) ((ButtonWidget) o).render(minecraft, mouseX, mouseY);
		ButtonWidget pointed = pointedAt(mouseX, mouseY, SHORT);
		boolean server = minecraft.isWorldRemote();
		List<Setting> shown = shown();
		SettingsRegistry.Group group = current().group;
		if (pointed != null && pointed.id == PAGE_BUTTON) {
			String locked = group.pageButton.lockedReason();
			hint(locked != null ? locked : group.pageButton.hint());
		} else if (pointed != null && pointed.id < shown.size()) {
			Setting s = shown.get(pointed.id);
			String locked = s.lockedReason();
			hint(locked != null ? locked : server && s.serverDecides ? "The server you're playing on decides this. Yours is for single player." : s.hint);
		} else if (group.note != null) {
			hint(group.note.get());
		} else if (group.modSwitch != null) {
			hint(group.modSwitch.hint);
		}
		SettingsRegistry registry = SettingsRegistry.get();
		if (said != null) {
			footer(said);
			return;
		}
		for (Setting s : shown) {
			if (!(server && s.serverDecides) && registry.waitingForRestart(s)) {
				footer(WAITING + " Takes effect after a restart");
				break;
			}
		}
	}
}
