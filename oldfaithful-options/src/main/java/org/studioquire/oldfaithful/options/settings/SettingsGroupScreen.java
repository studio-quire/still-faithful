package org.studioquire.oldfaithful.options.settings;

import java.util.List;

import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * One group's switches, as beta's Video Settings lays out its options: two columns of 150-wide buttons, each reading
 * "Name: Value" and stepping to the next value on a click, saved at once. A group longer than five rows goes over
 * pages, with arrows beside Done.
 * <p>
 * A change that waits for a restart marks its button with an asterisk and says so under the buttons. In multiplayer a
 * switch decided where the world runs reads "Server" and can't be pressed: the server's own file decides there, and
 * this game's setting is for single player.
 */
public class SettingsGroupScreen extends SettingsScreen {
	static final int PER_PAGE = 10, PREVIOUS = 201, NEXT = 202;
	static final String WAITING = "*";

	private final SettingsRegistry.Group group;
	private int page;

	public SettingsGroupScreen(SettingsScreen parent, SettingsRegistry.Group group) {
		super(parent);
		this.group = group;
		this.title = group.title;
	}

	private int pages() {
		return (group.settings.size() + PER_PAGE - 1) / PER_PAGE;
	}

	private List<Setting> shown() {
		return group.settings.subList(page * PER_PAGE, Math.min(group.settings.size(), (page + 1) * PER_PAGE));
	}

	@Override
	public void init() {
		SettingsRegistry registry = SettingsRegistry.get();
		registry.reload();
		List<Setting> shown = shown();
		boolean server = minecraft.isWorldRemote();
		for (int i = 0; i < shown.size(); i++) {
			Setting s = shown.get(i);
			ButtonWidget b = new ButtonWidget(i, column(i), row(i), 150, 20, text(registry, s, server));
			b.active = !(server && s.serverDecides);
			buttons.add(b);
		}
		buttons.add(new ButtonWidget(DONE, width / 2 - 100, height / 6 + 168, "Done"));
		if (pages() > 1) {
			ButtonWidget previous = new ButtonWidget(PREVIOUS, width / 2 - 124, height / 6 + 168, 20, 20, "<");
			ButtonWidget next = new ButtonWidget(NEXT, width / 2 + 104, height / 6 + 168, 20, 20, ">");
			previous.active = page > 0;
			next.active = page < pages() - 1;
			buttons.add(previous);
			buttons.add(next);
		}
	}

	static String text(SettingsRegistry registry, Setting s, boolean server) {
		if (server && s.serverDecides) return s.label + ": Server";
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
			init(minecraft, width, height);
		} else if (button.id < shown().size()) {
			Setting s = shown().get(button.id);
			SettingsRegistry registry = SettingsRegistry.get();
			registry.step(s);
			button.text = text(registry, s, minecraft.isWorldRemote());
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		renderBackground();
		drawCenteredTextWithShadow(textRenderer, pages() > 1 ? title + " (" + (page + 1) + "/" + pages() + ")" : title, width / 2, 20, 0xFFFFFF);
		for (Object o : buttons) ((ButtonWidget) o).render(minecraft, mouseX, mouseY);
		ButtonWidget pointed = pointedAt(mouseX, mouseY);
		boolean server = minecraft.isWorldRemote();
		if (pointed != null && pointed.id < shown().size()) {
			Setting s = shown().get(pointed.id);
			hint(server && s.serverDecides ? "The server you're playing on decides this. Yours is for single player." : s.hint);
		} else if (group.note != null) {
			hint(group.note);
		}
		SettingsRegistry registry = SettingsRegistry.get();
		for (Setting s : shown()) {
			if (!(server && s.serverDecides) && registry.waitingForRestart(s)) {
				footer(WAITING + " Takes effect after a restart");
				break;
			}
		}
	}
}
