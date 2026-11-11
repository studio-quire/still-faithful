package org.studioquire.oldfaithful.polish.selftest;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.studioquire.oldfaithful.lwjgl.MouseState;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.selftest.mixin.HeldAttackTiming;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;
import org.studioquire.oldfaithful.selftest.mixin.MinecraftTimerAccessor;

/**
 * Held Attack ({@code combat.heldAttack}) with the button held as a player holds it: the press and the release go into
 * our LWJGL component's queue and held state, and the game's own tick reads them, so beta's press, its repeat four
 * times a second and its mining run as in play. In single player, on a floor high over the world, the player's hand
 * empty: glass mined at eye height with a pig behind it; a hold begun on that pig, turned onto a second pig beside and
 * back; a click on the second. The pigs stand still and count the player's hits (calls of the game's attack, which is
 * what beta's repeat makes) without taking them. With the switch, a hold hits no pig but the one it began on; without
 * it, both pigs it passes are hit, beta's way. Either way the glass breaks, the pig a hold began on is hit again while
 * held, and a click hits.
 */
public final class HeldAttackCheck implements WorldCheck {
	private static final String KEY = "combat.heldAttack";
	private static final int FLOOR = 116, SETTLE = 20;
	/** SDL's left button. */
	private static final int LEFT = 1;
	/** Yaw looking south (+z) at the glass and the pig behind it, and west (-x) at the pig beside. */
	private static final float AHEAD = 0.0F, BESIDE = 90.0F;

	/** A pig that stays where it's put and counts the player's hits, taking none. */
	private static final class Dummy extends PigEntity {
		private final PlayerEntity player;
		int hits;

		Dummy(World world, PlayerEntity player) {
			super(world);
			this.player = player;
		}

		@Override
		public void tick() {
		}

		@Override
		public boolean damage(Entity source, int amount) {
			if (source == player) hits++;
			return false;
		}
	}

	private interface Step {
		boolean tick(Minecraft mc);
	}

	private final Deque<Step> plan = new ArrayDeque<>();
	private SelfTest results;
	private World world;
	private boolean on;
	private int ox, oz, feet;
	private float yaw = AHEAD;
	private Object[] saved;
	private Dummy behind, beside;
	private int mined = -1;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(KEY);
		// Ticks between beta's repeats, a quarter of a second's (20 at the tests' faster game time). A hold: the press and
		// three repeats before the next step looks. Glass takes about 10 ticks by hand.
		int every = (int) Math.ceil(((MinecraftTimerAccessor) mc).oldfaithful$timer().tps / 4.0F);
		int hold = 4 * every, mine = 15 + 2 * every;
		PlayerEntity p = mc.player;
		ox = MathHelper.floor(p.x) - 16;
		oz = MathHelper.floor(p.z) + 36;
		feet = FLOOR + 1;
		int slot = p.inventory.selectedSlot;
		saved = new Object[] {p.x, p.y - p.standingEyeHeight, p.z, p.yaw, p.pitch, p.inventory.main[slot], mc.focused};
		p.inventory.main[slot] = null;
		for (int x = ox - 16; x <= ox + 16; x += 16) {
			for (int z = oz - 16; z <= oz + 16; z += 16) world.getBlockId(x, FLOOR, z);
		}
		for (int x = ox - 4; x <= ox + 4; x++) {
			for (int y = FLOOR; y <= FLOOR + 4; y++) {
				for (int z = oz - 4; z <= oz + 5; z++) world.setBlock(x, y, z, y == FLOOR ? Block.STONE.id : 0);
			}
		}
		// Glass at eye height 2 south; a pig on a stone behind it, its middle at eye height 3 south; another 3 west.
		world.setBlock(ox, feet + 1, oz + 2, Block.GLASS.id);
		world.setBlock(ox, feet, oz + 3, Block.STONE.id);
		world.setBlock(ox - 3, feet, oz, Block.STONE.id);
		behind = pig(p, ox + 0.5, oz + 3.5);
		beside = pig(p, ox - 2.5, oz + 0.5);

		pause(SETTLE);
		press();
		int[] held = {0};
		plan.add(c -> {
			held[0]++;
			if (mined < 0 && world.getBlockId(ox, feet + 1, oz + 2) == 0) mined = held[0];
			return held[0] >= mine;
		});
		release();
		pause(10);
		then(c -> {
			results.check("still held attack: glass held on broke " + (mined < 0 ? "never" : mined + " ticks after the press") + " (by hand, about 10)",
					mined > 0);
			results.check("still held attack: the pig behind the glass, held on once it broke, hit " + behind.hits + " times ("
					+ (on ? "none" : "beta: some") + ")", on ? behind.hits == 0 : behind.hits > 0);
			behind.hits = 0;
		});

		press();
		pause(hold);
		int[] first = new int[1];
		then(c -> {
			first[0] = behind.hits;
			results.check("still held attack: the pig a hold began on, hit " + first[0] + " times in " + hold + " ticks (the press and the repeat)",
					first[0] >= 2);
			look(c, BESIDE);
		});
		pause(hold);
		then(c -> {
			results.check("still held attack: a pig the hold turned onto, hit " + beside.hits + " times in " + hold + " ticks ("
					+ (on ? "none" : "beta: some") + ")", on ? beside.hits == 0 : beside.hits > 0);
			results.check("still held attack: meanwhile the pig the hold began on, out of the crosshair, hit " + (behind.hits - first[0])
					+ " times (none)", behind.hits == first[0]);
			look(c, AHEAD);
		});
		pause(hold);
		then(c -> results.check("still held attack: the pig the hold began on, turned back to, hit " + (behind.hits - first[0]) + " times more (some)",
				behind.hits > first[0]));
		release();
		pause(10);
		then(c -> {
			look(c, BESIDE);
			beside.hits = 0;
		});
		pause(2);
		press();
		pause(2);
		release();
		pause(5);
		then(c -> results.check("still held attack: a click on the pig beside, hit " + beside.hits + " times (once)", beside.hits == 1));
	}

	@Override
	public boolean tick(Minecraft mc) {
		look(mc, yaw);
		while (!plan.isEmpty()) {
			if (!plan.peek().tick(mc)) return false;
			plan.remove();
		}
		behind.markDead();
		beside.markDead();
		PlayerEntity p = mc.player;
		mc.focused = (Boolean) saved[6];
		p.inventory.main[p.inventory.selectedSlot] = (ItemStack) saved[5];
		p.setPositionAndAnglesKeepPrevAngles((Double) saved[0], (Double) saved[1], (Double) saved[2], (Float) saved[3], (Float) saved[4]);
		return true;
	}

	private Dummy pig(PlayerEntity player, double x, double z) {
		Dummy pig = new Dummy(world, player);
		pig.setPositionAndAnglesKeepPrevAngles(x, feet + 1, z, 0.0F, 0.0F);
		world.spawnEntity(pig);
		return pig;
	}

	/**
	 * The player held standing where the scene has them, looking along yaw, level. Called at the tick's start, before
	 * the game works out the crosshair, so a turn takes effect in the tick it's made.
	 */
	private void look(Minecraft mc, float yaw) {
		this.yaw = yaw;
		PlayerEntity p = mc.player;
		p.velocityX = p.velocityZ = 0;
		p.setPositionAndAnglesKeepPrevAngles(ox + 0.5, feet, oz + 0.5, yaw, 0.0F);
		p.prevYaw = yaw;
		p.prevPitch = 0.0F;
	}

	/** An action, then straight on to the next step. */
	private void then(java.util.function.Consumer<Minecraft> action) {
		plan.add(c -> {
			action.accept(c);
			return true;
		});
	}

	/** The next step comes this many ticks later. */
	private void pause(int ticks) {
		int[] left = {ticks};
		plan.add(c -> left[0]-- <= 0);
	}

	/**
	 * Pressed as the system would tell the window, read by the game later in this same tick (the check runs at its
	 * start). The game takes a press only with its window focused, which the hidden window never is.
	 */
	private void press() {
		then(c -> {
			if (c.currentScreen != null) results.check("still held attack: no screen open to take the press (" + c.currentScreen + ")", false);
			c.focused = true;
			((HeldAttackTiming) c).oldfaithful$setLastTickTime(System.currentTimeMillis());
			MouseState.button(LEFT, true, System.nanoTime());
		});
	}

	private void release() {
		then(c -> MouseState.button(LEFT, false, System.nanoTime()));
	}
}
