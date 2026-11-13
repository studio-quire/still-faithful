package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolItem;

/**
 * Obsidian mined as in 1.0 (#10's note). Beta's pickaxe list leaves it out, so a diamond
 * pickaxe mined it at hand speed, 15 s at hardness 10. Beta 1.9 put it on the list and 1.0.0-RC1 raised its hardness to
 * 50 to keep it slow: 9.4 s with a diamond pickaxe, and 250 s instead of 50 for anything that can't harvest it (beta's
 * and 1.0's rule gives such a tool no speed). The block's hardness field stays 10; the mining time and
 * {@code getHardness()} answer 50, so anything that times a break from them (Faithful Core's tool timing) agrees.
 * <p>
 * The switch's second choice, diamond, keeps beta's 50 s without a diamond pickaxe. That's how a game without this
 * change times it, and in multiplayer the client times a break and the server checks it: on a server with 1.0's 250 s,
 * a vanilla player who mines obsidian by hand sees it gone at 50 s, back 4 s later (the server refused the early
 * break), and gone at 250 s. The default is 1.0's all the same (such players are next to
 * none, and agreeing with vanilla players is a bonus, not a goal).
 * <p>
 * A client with this change mines obsidian as the server it's on says, whatever its own choice: the login packet's
 * marker is full, so the server says it in a sign packet ({@link ServerMarker#sign}), 'o' for 1.0's or 'd' for
 * diamond; a server that says neither gets beta's timing.
 */
public final class Obsidian {
	public static final float HARDNESS = 50.0F;
	/** What a server says it has. */
	public static final int BETA = 0, DIAMOND = 1, FULL = 2;

	/** In multiplayer, what the server has: {@link #BETA} until it says otherwise. */
	public static volatile int serverMode;
	/** Set while a client on a server without this change asks how fast obsidian mines. */
	public static int asBeta;

	private Obsidian() {
	}

	/** This game's own choice, for single player and the server it runs: {@link #FULL} or {@link #DIAMOND}. */
	public static int ownMode() {
		return "diamond".equals(PolishConfig.obsidianAtStart()) ? DIAMOND : FULL;
	}

	private static int mode(PlayerEntity player) {
		return player != null && player.world != null && player.world.isRemote ? serverMode : ownMode();
	}

	/** Whether this player's break of obsidian is timed as beta: on a client, on a server without this change. */
	public static boolean timedAsBeta(PlayerEntity player) {
		return mode(player) == BETA;
	}

	/** Whether obsidian is as hard as 1.0's for this player's break: with a pickaxe that harvests it, or 1.0's in full. */
	public static boolean hard(PlayerEntity player, boolean harvests) {
		int mode = mode(player);
		return mode == FULL || mode == DIAMOND && harvests;
	}

	/** Whether the tool should mine the block at its own speed though its list leaves it out. */
	public static boolean fastFor(ToolItem tool, Block block) {
		return asBeta == 0 && block == Block.OBSIDIAN && tool instanceof PickaxeItem;
	}
}
