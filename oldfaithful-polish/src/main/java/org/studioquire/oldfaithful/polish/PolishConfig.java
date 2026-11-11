package org.studioquire.oldfaithful.polish;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * config/stillfaithful-polish.properties: one switch per change, all on by default (they're the edition's). A change
 * that's off isn't applied at all (its mixins are skipped), as Old Faithful's fixes are: turning one off also clears it
 * out of the way of another mod. Each change's mixins live in their own package under {@code mixin}, named in
 * {@link #CHANGES}; {@code mixin.edition} (the edition's name on screen) is always in. The file is written with
 * explanations if missing; a switch it lacks (written by an older version) is appended.
 */
public final class PolishConfig implements IMixinConfigPlugin {
	static final String FILE = "stillfaithful-polish.properties";
	private static final String ALWAYS = "edition";
	/** The background autosave relies on Pure Faithful's crash-safe region writes (BackgroundSaves). */
	private static final String SAVES_KEY = "saves.backgroundAutosave", SAVES_PACKAGE = "autosave";
	/** #6 is a choice rather than a switch: stepped or smooth (the dusk mixins in; DuskLight), or beta (off). */
	public static final String DUSK_KEY = "sky.dusk";
	/** Pairs go by which way chests face, so they need #69's facings. */
	private static final String PAIRS_KEY = "blocks.chestPairs", FACING_KEY = "blocks.chestsFaceYou";
	private static final String HEADER = "# stillfaithful-polish: Still Faithful's changes, what later versions improved while beta stayed beta.\n"
			+ "# false (for sky.dusk, beta) leaves that part of the game exactly as beta had it. Restart the game after changing\n"
			+ "# anything here (the settings screen, Options > Still Faithful..., writes this file too).\n"
			+ "# In multiplayer, changes to the world (drops, leaves, water, boats, animals, saving) take effect where the world\n"
			+ "# runs: on the server. Changes to what you see take effect on your client.\n";

	/** Key, mixin package, explanation (docs/bugs.md entry), and the default where it isn't true. */
	private static final String[][] CHANGES = {
			{"blocks.stairsDropStairs", "stairs",
					"# #13: stairs dropped their material (a plank, a cobblestone) instead of the stairs, from a recipe of six\n"
					+ "# blocks for four stairs. With this, they drop themselves.\n"},
			{"leaves.placedStay", "leaves",
					"# #24: leaves you placed decayed unless a log was near (they were placed already marked to decay). With this,\n"
					+ "# placed leaves never decay; leaves on trees still do. Placed leaves carry a mark in their data that\n"
					+ "# beta ignores, so the world still opens in beta.\n"},
			{"glowstone.anyTool", "glowstone",
					"# #10: glowstone dropped nothing unless mined with a pickaxe. With this, it drops by hand or with any tool,\n"
					+ "# as from 1.0 (and mines at the speed 1.0 had). In multiplayer, mining is faster only on a server that\n"
					+ "# has this change too; drops are the server's.\n"},
			{"liquids.lavaThins", "lava",
					"# #20: flowing lava cut off from its source could stay forever (three times in four it refused to thin\n"
					+ "# out, and then never looked again). With this, it thins out, waiting four times longer each of those times.\n"
					+ "# Lava already stuck in a world goes once something beside it changes.\n"},
			{"liquids.waterSources", "watersources",
					"# #20: water between two sources became a source only over solid ground, not over still water (the check\n"
					+ "# read the wrong block). With this, it becomes one over still water too.\n"},
			{"render.droppedItemSize", "itemsize",
					"# #34: dropped stairs, fences, cactus, pressure plates and other blocks that aren't full cubes lay on the\n"
					+ "# ground at twice the size of other dropped blocks.\n"},
			{"menu.texturePacks", "texturepacks",
					"# The title screen's Mods and Texture Packs button only ever held texture packs. With this, it says so.\n"},
			{"render.bowPose", "bow",
					"# #34: players and skeletons held a bow out flat, like any item. With this, it's held upright, like a bow.\n"},
			{DUSK_KEY, "dusk",
					"# #6: the light changed in eleven steps at dusk and at dawn, and each step rebuilt every chunk in view (a\n"
					+ "# wave of rebuilding sweeping across the landscape, and a hitch). stepped: beta's eleven steps, each\n"
					+ "# exactly as beta drew it, changing the whole view at once with nothing rebuilt. smooth: the light\n"
					+ "# follows the time of day without steps (beta's picture at each of its steps, gliding between them).\n"
					+ "# beta: as beta had it. stepped and smooth need OpenGL 2.0; without it, dusk is beta's. Switching between\n"
					+ "# stepped and smooth on the settings screen takes effect at once.\n",
					"smooth"},
			{"boats.sturdy", "boats",
					"# #12: a boat broke whenever it brushed a shore or a floating item at more than a slow speed. With this, only a\n"
					+ "# real crash breaks it (hitting a wall hard at an angle; head-on bumps never broke boats, nor do they now),\n"
					+ "# or hitting it. Floating items no longer stop it.\n"},
			{"boats.handling", "boathandling",
					"# #12: boats coasted on for about forty blocks after you stopped paddling and bobbed up and down at the\n"
					+ "# surface. With this, a boat you stop paddling slows to a stop in a few blocks and rides the water\n"
					+ "# steadily. Paddling stays beta's (top speed, acceleration, turning) unless boats.steering is on, which\n"
					+ "# has its own paddling and slowing.\n"},
			{"boats.steering", "boatsteering",
					"# Boats went the way you looked as you paddled, swung round to face wherever they were moving, and turned your\n"
					+ "# view with them. With this, they steer as in later versions: A and D turn the boat (your view turns with it),\n"
					+ "# W paddles it forward the way it faces and S backs it slowly. Its top speed is 6.5 blocks a second, a little\n"
					+ "# over beta's 5.8; a boat nobody paddles stops in a few blocks; and a boat you place faces the way you\n"
					+ "# look. In multiplayer, the server's: it steers for players whose game sends where they look every tick\n"
					+ "# while riding (Old Faithful's Riding Look fix, in Old Faithful and the editions after it), and other players'\n"
					+ "# boats paddle as in beta.\n"},
			{"minecarts.cartCollisions", "minecarts",
					"# #31: on north-south and curved track, minecarts were walls to each other (a slip in the check meant to\n"
					+ "# keep carts on parallel tracks apart): a cart running into another stopped dead, and a furnace cart\n"
					+ "# couldn't push a train. East-west track worked. With this, every track works as east-west track did: carts\n"
					+ "# on one track bump, push and boost each other, and carts side by side on parallel tracks don't.\n"},
			{"animals.keepTended", "animals",
					"# #4: every cow, pig, sheep and chicken could despawn once you were far enough away, penned ones included.\n"
					+ "# With this, animals a player has looked after (sheared, milked, saddled, dyed, ridden, carried in a boat\n"
					+ "# or minecart, or hatched from a thrown egg) stay for good; the rest despawn as in beta. Kept animals still\n"
					+ "# count towards how many animals the world spawns around you.\n"},
			{"saves.backgroundAutosave", "autosave",
					"# #14: every 2 seconds the game stopped to compress and write the chunks that had changed (and again for\n"
					+ "# chunks unloading), a hitch while building or exploring. With this, the same saves, of the same moment,\n"
					+ "# are compressed and written on a thread of their own, starting at once. Needs Pure Faithful's saves mod\n"
					+ "# (purefaithful-saves), whose crash-safe writes it relies on; without it, saves stay as beta's.\n"},
			{"blocks.chestsFaceYou", "chests",
					"# #69: a chest's front was worked out from the blocks around it: away from a block behind it, south when it\n"
					+ "# stood free, sideways in a corner; you couldn't choose. With this, a chest you place faces you (a second\n"
					+ "# chest beside it joins its facing). Chests placed before keep beta's rule, and a vanilla game draws every\n"
					+ "# chest by it. In multiplayer, placed chests face you only on a server that has this change too.\n"},
			{"blocks.fenceShape", "fences",
					"# #30: a fence was a whole block to walk into and to aim at, whatever it joined. With this, it's a post with arms\n"
					+ "# to the fences beside it, as in later versions, both to aim at and to bump into: you can stand against a post\n"
					+ "# and walk past its corner, and still can't jump over it. In multiplayer, walking past posts needs a server with\n"
					+ "# this change too (elsewhere fences stay whole to walk into); what you aim at is always yours.\n"},
			{"render.chatBars", "chat",
					"# Each line of chat had a dark bar behind it the full width of the chat, however short the line. With this,\n"
					+ "# a line's bar ends just past its own text, as long as the line is.\n"},
			{PAIRS_KEY, "chestpairs",
					"# A chest beside another always joined it into a double chest, and none could go beside a double chest. With\n"
					+ "# this, chests pair as in later versions: a chest you place joins one beside it only if that one faces the same\n"
					+ "# way, stands to its left or right and isn't joined already, and never while you sneak; and a chest can go\n"
					+ "# anywhere, beside a double chest too. Chests from before stay paired as they were. Needs blocks.chestsFaceYou\n"
					+ "# (pairs go by which way chests face). In multiplayer, the server's.\n"},
			{"render.chestLids", "chestlids",
					"# Each half of a double chest had the lid of a single chest, so a border ran down the middle of its top and\n"
					+ "# bottom. With this, a double chest has one lid, as in later versions, made from your texture pack's chest lid.\n"},
			{"mobs.skeletonArrows", "skeletons",
					"# Skeletons shot their arrows from well above their heads, so under a ceiling two blocks high (a tunnel, a low\n"
					+ "# cave, a doorway) the arrow stuck in the ceiling and never reached you. With this, an arrow leaves from the\n"
					+ "# skeleton's eyes, as in later versions, so skeletons there can hit you. Elsewhere they hit a player standing\n"
					+ "# about as often as before; on the move it can differ (more often walking straight at one). In multiplayer, the\n"
					+ "# server's.\n"},
			{"mobs.squidSurface", "squids",
					"# A squid that swam up to the water's surface bounced against it: each time it came halfway out it dropped back\n"
					+ "# and swam up again, a few times a second, its tentacles twitching. With this, a squid slows as it nears the\n"
					+ "# surface and swims on just under it, its back at the waterline, until it turns. In multiplayer, the server's.\n"},
			{"skins.modern", "skins",
					"# Beta read only the top half of a skin. With this, skins made for later versions show whole: jacket, sleeves\n"
					+ "# and trousers, a left arm and leg of their own, and slim arms where the player's profile asks for them.\n"
					+ "# Older skins look as they did in beta. Yours and other players', on any server: a server needs nothing.\n"},
			{"inventory.shiftClick", "shiftclick",
					"# Shift-click moved things between a chest and your inventory, or your inventory and hotbar, but it did nothing\n"
					+ "# in a dispenser, never put anything into a furnace and never put armour on. With this it does those too: a\n"
					+ "# dispenser both ways, like a chest; what smelts into a furnace's top slot and fuel into its bottom one (if that\n"
					+ "# slot holds something else, nothing moves); and armour on, from your inventory, where that place is empty.\n"
					+ "# It's made of the clicks you could make yourself, so it works on any server.\n"},
			{"furnace.woodenFuel", "fuel",
					"# A furnace burned planks, logs, sticks, saplings and most wooden blocks, but no wooden tools or swords, bows,\n"
					+ "# fishing rods, bowls, signs, wooden doors, ladders, boats or wooden slabs. With this, those burn too, for as long\n"
					+ "# as later versions burn them: a boat smelts six items; a bow, fishing rod or ladder one and a half, as a plank\n"
					+ "# does; a tool, sign or door one; a wooden slab three quarters; a bowl half. In multiplayer, the server's.\n"},
			{"render.particleDistance", "particledistance",
					"# Particles (smoke, splashes, the puffs of a blast or of a mob dying) showed only within 16 blocks of you, so a\n"
					+ "# blast further off made no cloud at all. With this, particles show within 32 blocks, as in later versions, and\n"
					+ "# the puffs of a blast or of a mob dying within 64, as far as a blast is heard. A server sends blasts within\n"
					+ "# 64 blocks too, so it needs nothing.\n"},
			{"render.entityDistance", "entitydistance",
					"# Things showed only within a distance that went by their size: a dropped item vanished past 16 blocks, a\n"
					+ "# chicken past 21, an arrow past 32, a zombie past 64. With this, everything shows within 64 blocks at least,\n"
					+ "# as far as a zombie, and bigger things as far as before. A server sends dropped items and arrows within 64\n"
					+ "# blocks, so it needs nothing.\n"},
			{"combat.heldAttack", "heldattack",
					"# Holding the button to attack or mine went on hitting whatever came under the crosshair, four times a second:\n"
					+ "# the animal behind a block you'd just mined, or a tame wolf the crosshair passed across. With this, holding it\n"
					+ "# goes on hitting only what you clicked; anything else it passes over, and mining is as before. A new click\n"
					+ "# hits whatever is under the crosshair. (Later versions hit only when you click.) Your own game's, on any\n"
					+ "# server: a server needs nothing.\n"},
	};

	private static final List<String> off = new ArrayList<>();
	private static volatile boolean read;
	private static String dusk = "smooth";

	@Override
	public void onLoad(String mixinPackage) {
		read = true;
		Properties p = new Properties();
		File file = FabricLoader.getInstance().getConfigDir().resolve(FILE).toFile();
		try {
			StringBuilder add = new StringBuilder();
			if (file.isFile()) {
				try (InputStream in = new FileInputStream(file)) {
					p.load(in);
				}
			} else {
				file.getParentFile().mkdirs();
				add.append(HEADER);
			}
			for (String[] c : CHANGES) {
				if (!p.containsKey(c[0])) add.append('\n').append(c[2]).append(c[0]).append('=').append(fallback(c)).append('\n');
			}
			if (add.length() > 0) {
				try (OutputStream out = new FileOutputStream(file, true)) {
					out.write(add.toString().getBytes(StandardCharsets.ISO_8859_1));
				}
			}
		} catch (IOException e) {
			System.out.println("[stillfaithful-polish] WARN Could not read or write " + file + ", every change on: " + e);
		}
		for (String[] c : CHANGES) {
			String v = p.getProperty(c[0], fallback(c)).trim();
			if (DUSK_KEY.equals(c[0])) {
				dusk = duskMode(v);
				if (dusk.equals("beta")) off.add(c[0]);
			} else if (!Boolean.parseBoolean(v)) {
				off.add(c[0]);
			}
		}
		if (off.contains(FACING_KEY) && !off.contains(PAIRS_KEY)) off.add(PAIRS_KEY);
		if (!off.isEmpty()) System.out.println("[stillfaithful-polish] Turned off in " + file.getName() + ": " + String.join(", ", off));
	}

	/** A switch's value when the file doesn't say: true for most. */
	public static String defaultValue(String key) {
		for (String[] c : CHANGES) {
			if (c[0].equals(key)) return fallback(c);
		}
		return "true";
	}

	private static String fallback(String[] change) {
		return change.length > 3 ? change[3] : "true";
	}

	/** The dusk choice as read: stepped, beta, or smooth (the default) for anything else (as the settings screen reads it). */
	static String duskMode(String value) {
		String v = value.trim().toLowerCase(java.util.Locale.ROOT);
		return v.equals("stepped") || v.equals("beta") ? v : "smooth";
	}

	/** The dusk choice the game started with: stepped, smooth or beta. */
	public static String duskAtStart() {
		return dusk;
	}

	/** Whether the change was on when the game started, so its mixins are in. */
	public static boolean applied(String key) {
		if (SAVES_KEY.equals(key) && !savesModLoaded()) return false;
		return !off.contains(key);
	}

	private static boolean savesModLoaded() {
		return FabricLoader.getInstance().isModLoaded("purefaithful-saves");
	}

	/** Every switch, in the file's order. */
	public static List<String> keys() {
		List<String> keys = new ArrayList<>();
		for (String[] c : CHANGES) keys.add(c[0]);
		return keys;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String pkg = mixinClassName.substring(0, mixinClassName.lastIndexOf('.'));
		pkg = pkg.substring(pkg.lastIndexOf('.') + 1);
		// What reaches into other mods goes in only where they're installed.
		if (targetClassName.startsWith("org.studioquire.oldfaithful.hardening.") && !FabricLoader.getInstance().isModLoaded("purefaithful-hardening")) return false;
		if (ALWAYS.equals(pkg)) {
			if (targetClassName.startsWith("org.studioquire.oldfaithful.options.")) return FabricLoader.getInstance().isModLoaded("oldfaithful-options");
			return true;
		}
		if (SAVES_PACKAGE.equals(pkg) && !savesModLoaded()) return false;
		for (String[] c : CHANGES) {
			if (c[1].equals(pkg)) return !off.contains(c[0]);
		}
		throw new IllegalStateException("A mixin outside any change's package: " + mixinClassName);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
