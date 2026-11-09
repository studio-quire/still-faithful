package org.studioquire.oldfaithful.polish.selftest;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.MathHelper;
import org.studioquire.oldfaithful.lwjgl.KeyboardState;
import org.studioquire.oldfaithful.lwjgl.MouseState;
import org.studioquire.oldfaithful.lwjgl.Window;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.selftest.mixin.HandledScreenFrame;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;

/**
 * {@code inventory.shiftClick} used as a player uses it: mouse and key events put into our LWJGL component's queues,
 * which the open screen reads in its next tick as it reads the real ones. Each shift-click expects the new move where
 * the switch was on at start, and beta's where it was off; and nothing is lost or made either way.
 */
public final class ShiftClickCheck implements WorldCheck {
	private static final String KEY = "inventory.shiftClick";
	private static final int DIRT = 3, PUMPKIN = 86;
	private static final int LEFT = 1, SHIFT = 225;
	/** Between events, and between clicks (far enough apart that nothing reads two as one). */
	private static final long STEP = 30_000_000L, APART = 600_000_000L;

	private interface Action {
		/** Called each tick until it's done. */
		boolean run();
	}

	private final ArrayDeque<Action> actions = new ArrayDeque<>();
	private SelfTest results;
	private Minecraft mc;
	private long clock;
	private int bx, by, bz;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.mc = mc;
		this.results = results;
		clock = System.nanoTime();
		bx = MathHelper.floor(mc.player.x);
		by = MathHelper.floor(mc.player.y) + 3;
		bz = MathHelper.floor(mc.player.z);
		inventory();
		furnace();
		dispenser();
		then(this::noItems);
	}

	@Override
	public boolean tick(Minecraft mc) {
		if (actions.isEmpty()) return true;
		if (actions.peek().run()) actions.poll();
		return actions.isEmpty();
	}

	// ---- the scenes

	private void inventory() {
		int helmet = Item.IRON_HELMET.id, boots = Item.GOLDEN_BOOTS.id;
		own("shift-click puts a helmet on", new Spec().at(9, helmet, 1),
				9, "5=" + helmet + "x1 9=- 36=- c=-", "5=- 9=- 36=" + helmet + "x1 c=-", 5, 9, 36);
		own("shift-click puts boots on from the hotbar", new Spec().at(40, boots, 1),
				40, "8=" + boots + "x1 40=- 9=- c=-", "8=- 40=- 9=" + boots + "x1 c=-", 8, 40, 9);
		own("shift-click with a helmet already on moves the other as beta does", new Spec().at(5, helmet, 1).at(9, helmet, 1),
				9, "5=" + helmet + "x1 9=- 36=" + helmet + "x1 c=-", "5=" + helmet + "x1 9=- 36=" + helmet + "x1 c=-", 5, 9, 36);
		own("shift-click doesn't put a pumpkin on", new Spec().at(10, PUMPKIN, 5),
				10, "5=- 10=- 36=86x5 c=-", "5=- 10=- 36=86x5 c=-", 5, 10, 36);
	}

	private void furnace() {
		int fx = bx - 2, coal = Item.COAL.id, iron = Block.IRON_ORE.id, gold = Block.GOLD_ORE.id, log = Block.LOG.id;
		then(() -> mc.world.setBlock(fx, by, bz, Block.FURNACE.id));
		// Furnace screen: 0 top, 1 fuel, 2 output, 3-29 the inventory (its slots 9-35), 30-38 the hotbar.
		set(new Spec().at(9, DIRT, 3).at(10, coal, 5).at(11, iron, 10).at(12, gold, 4));
		then(() -> mc.player.openFurnaceScreen(furnace(fx)));
		pause(2);
		for (int s = 3; s <= 6; s++) {
			int slot = s;
			then(() -> shiftClick(slot));
			pause(3);
		}
		releaseShift();
		expect("a furnace takes iron ore on top and coal below, nothing beside iron ore, and dirt goes to the hotbar",
				"0=" + iron + "x10 3=- 4=- 5=- 6=" + gold + "x4 30=3x3 31=- 32=- 33=- c=-",
				"0=- 3=- 4=- 5=- 6=- 30=3x3 31=" + coal + "x5 32=" + iron + "x10 33=" + gold + "x4 c=-",
				0, 3, 4, 5, 6, 30, 31, 32, 33);
		then(() -> {
			if (!PolishConfig.applied(KEY)) return;
			ItemStack fuel = screen().getSlot(1).getStack();
			results.check("still shift-click: the furnace's fuel: " + show(fuel) + " (5 coal, or 4 once lit)",
					fuel != null && fuel.itemId == coal && (fuel.count == 5 || fuel.count == 4));
		});
		then(() -> {
			for (int i = 0; i < 3; i++) furnace(fx).setStack(i, null);
		});
		set(new Spec().at(9, log, 6));
		pause(2);
		then(() -> shiftClick(3));
		pause(3);
		releaseShift();
		expect("a furnace takes logs on top, where they smelt", "0=" + log + "x6 3=- 30=- c=-", "0=- 3=- 30=" + log + "x6 c=-", 0, 3, 30);
		then(() -> {
			for (int i = 0; i < 3; i++) furnace(fx).setStack(i, null);
		});
		close();
		then(() -> mc.world.setBlock(fx, by, bz, 0));
	}

	private void dispenser() {
		int dz = bz + 2, arrow = Item.ARROW.id;
		then(() -> mc.world.setBlock(bx, by, dz, Block.DISPENSER.id));
		// Dispenser screen: 0-8 the dispenser, 9-35 the inventory, 36-44 the hotbar.
		set(new Spec().at(9, arrow, 16));
		then(() -> mc.player.openDispenserScreen((DispenserBlockEntity) mc.world.getBlockEntity(bx, by, dz)));
		pause(2);
		then(() -> shiftClick(9));
		pause(4);
		expect("a dispenser takes shift-clicked arrows", "0=" + arrow + "x16 9=- c=-", "0=- 9=" + arrow + "x16 c=-", 0, 9);
		then(() -> shiftClick(0));
		pause(4);
		releaseShift();
		expect("a dispenser gives shift-clicked arrows back, to the hotbar's end first",
				"0=- 9=- 44=" + arrow + "x16 c=-", "0=- 9=" + arrow + "x16 44=- c=-", 0, 9, 44);
		close();
		then(() -> mc.world.setBlock(bx, by, dz, 0));
	}

	private FurnaceBlockEntity furnace(int x) {
		return (FurnaceBlockEntity) mc.world.getBlockEntity(x, by, bz);
	}

	// ---- steps

	/** On the inventory screen: set up, one shift-click, and the slots compared with what's expected (on, beta). */
	private void own(String name, Spec spec, int slot, String on, String off, int... slots) {
		set(spec);
		then(() -> mc.setScreen(new InventoryScreen(mc.player)));
		pause(2);
		then(() -> shiftClick(slot));
		pause(5);
		releaseShift();
		expect(name, on, off, slots);
		close();
	}

	/** The slots of the open screen and the held stack, against on or beta by the switch. */
	private void expect(String name, String on, String off, int... slots) {
		then(() -> {
			boolean applied = PolishConfig.applied(KEY);
			String want = applied ? on : off, got = state(slots);
			results.check("still shift-click: " + name + ": " + got + " (expected " + want + ", " + (applied ? "on" : "beta") + ")",
					got.equals(want));
		});
	}

	/** The player's own screen as given (slot ids 1-44: grid, armour, inventory), nothing held. */
	private void set(Spec spec) {
		then(() -> {
			ScreenHandler h = mc.player.playerScreenHandler;
			for (int s = 1; s < 45; s++) h.getSlot(s).setStack(spec.stackAt(s));
			mc.player.inventory.setCursorStack(null);
		});
	}

	/** Closed with everything cleared, so nothing drops. */
	private void close() {
		then(() -> {
			mc.player.inventory.setCursorStack(null);
			ScreenHandler h = mc.player.playerScreenHandler;
			for (int s = 1; s < 45; s++) h.getSlot(s).setStack(null);
			if (mc.currentScreen != null) mc.player.closeHandledScreen();
		});
		pause(2);
		then(this::noItems);
	}

	private void noItems() {
		for (Object o : mc.world.entities) {
			if (o instanceof ItemEntity && ((Entity) o).getSquaredDistance(mc.player) < 32 * 32) ((Entity) o).markDead();
		}
	}

	// ---- input, as SDL's would arrive

	private void shiftClick(int slot) {
		key(SHIFT, true);
		point(slot);
		MouseState.button(LEFT, true, next());
		MouseState.button(LEFT, false, next());
		clock += APART;
	}

	private void point(int slot) {
		HandledScreen s = (HandledScreen) mc.currentScreen;
		HandledScreenFrame f = (HandledScreenFrame) s;
		Slot target = s.handler.getSlot(slot);
		int gx = (s.width - f.polish$frameWidth()) / 2 + target.x + 8, gy = (s.height - f.polish$frameHeight()) / 2 + target.y + 8;
		double fx = (gx + 0.5) * mc.displayWidth / s.width, fy = (s.height - 1 - gy + 0.5) * mc.displayHeight / s.height;
		float density = Window.pixelDensity();
		MouseState.motion((float) (fx / density), (float) ((Window.framebufferHeight() - 1 - fy) / density), 0, 0, next());
	}

	private void key(int scancode, boolean down) {
		KeyboardState.key(scancode, (short) 0, down, false, next(), false);
	}

	private void releaseShift() {
		then(() -> key(SHIFT, false));
		pause(1);
	}

	private long next() {
		return clock += STEP;
	}

	// ---- reading

	private ScreenHandler screen() {
		return ((HandledScreen) mc.currentScreen).handler;
	}

	/** "slot=IDxN" (or "slot=-") for each slot of the open screen (else the inventory's), then the held stack. */
	private String state(int... slots) {
		ScreenHandler h = mc.currentScreen instanceof HandledScreen ? screen() : mc.player.playerScreenHandler;
		StringBuilder b = new StringBuilder();
		for (int s : slots) b.append(s).append('=').append(show(h.getSlot(s).getStack())).append(' ');
		return b.append("c=").append(show(mc.player.inventory.getCursorStack())).toString();
	}

	private static String show(ItemStack s) {
		return s == null ? "-" : s.itemId + "x" + s.count + (s.getDamage() != 0 ? ":" + s.getDamage() : "");
	}

	private void then(Runnable r) {
		actions.add(() -> {
			r.run();
			return true;
		});
	}

	private void pause(int ticks) {
		int[] left = {ticks};
		actions.add(() -> --left[0] <= 0);
	}

	/** The player's own screen: slot ids 1-44 (grid, armour, inventory). */
	private static final class Spec {
		final Map<Integer, ItemStack> slots = new TreeMap<>();

		Spec at(int slot, int id, int count) {
			slots.put(slot, new ItemStack(id, count, 0));
			return this;
		}

		ItemStack stackAt(int slot) {
			ItemStack s = slots.get(slot);
			return s == null ? null : s.copy();
		}
	}
}
