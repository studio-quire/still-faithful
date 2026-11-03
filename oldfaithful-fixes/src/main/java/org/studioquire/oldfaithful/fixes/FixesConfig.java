package org.studioquire.oldfaithful.fixes;

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
 * config/oldfaithful-fixes.properties: one switch per fix, all on by default. A fix that's off isn't applied at all
 * (its mixins are skipped), so turning one off also clears it out of the way of another mod that changes the same
 * code. Each fix's mixins live in their own package under {@code mixin}, named in {@link #FIXES}. The file is written
 * with explanations if missing; a switch it lacks (written by an older version) is appended.
 */
public final class FixesConfig implements IMixinConfigPlugin {
	private static final String HEADER = "# oldfaithful-fixes: fixes of beta 1.7.3's own mistakes, each on its own switch.\n"
			+ "# false leaves that part of the game exactly as beta had it. Restart the game after changing anything here (the\n"
			+ "# settings screen, Options > Old Faithful..., writes this file too).\n"
			+ "# In multiplayer, fixes to the world (drops, mobs, water, furnaces) take effect where the world runs: on the\n"
			+ "# server. Fixes to what you see and hear take effect on your client.\n";

	/** Key, mixin package, explanation (docs/bugs.md entry). */
	private static final String[][] FIXES = {
			{"sky.afterContainer", "sky",
					"# #7: the sky turned grey while an inventory, chest or furnace was open.\n"},
			{"tools.lastUse", "lastuse",
					"# #8: the block broken with a tool's last use dropped nothing (in multiplayer: any block; in single player:\n"
					+ "# leaves cut with shears).\n"},
			{"slabs.wooden", "slabs",
					"# #9: wooden slabs only dropped when mined with a pickaxe. With this, they mine and drop like planks by\n"
					+ "# hand or axe (and still mine fast with a pickaxe).\n"},
			{"tools.speedByMaterial", "tools",
					"# #10: pickaxes, axes and shovels mined fast only the blocks on a short list, so an axe was slow on crafting\n"
					+ "# tables, doors and fences, a pickaxe on furnaces, bricks and rails, a shovel on soul sand. With this, each\n"
					+ "# mines fast everything of its material. Obsidian and redstone ore keep beta's slowness. In multiplayer,\n"
					+ "# only on a server that has this fix too.\n"},
			{"furnace.lavaBucket", "furnace",
					"# #11: a furnace burning a lava bucket used up the bucket too. With this, the empty bucket stays in the\n"
					+ "# fuel slot.\n"},
			{"liquids.dropsBelow", "liquids",
					"# #19: water falling onto torches, rails, redstone, flowers or crops destroyed them without dropping them\n"
					+ "# (flowing sideways it dropped them). With this, they drop, and lava falling on them fizzes as it does\n"
					+ "# sideways.\n"},
			{"slimes.splitOnOverkill", "slimes",
					"# #25: a big slime killed by a hit stronger than its remaining health died without splitting.\n"},
			{"sleep.wakeThroughWalls", "sleep",
					"# #26: a monster sent to wake a sleeping player could count as reaching the bed from the far side of the\n"
					+ "# wall behind the bed's head (the check measured from the corner of the block the monster stood on).\n"},
			{"pigs.saddleDrop", "saddle",
					"# #29: a saddled pig dropped only pork when killed, and the saddle was lost.\n"},
			{"sound.stepsAfterFalls", "steps",
					"# #38: footsteps owed while in the air (going down stairs, jumping) all played at once on landing.\n"},
			{"gui.cursorItemOnTop", "cursor",
					"# #39: the item held on the cursor in an inventory was drawn behind the player figure and under the\n"
					+ "# labels.\n"},
			{"gui.sliderSteps", "sliders",
					"# #40: the volume and sensitivity sliders couldn't reach every percentage. With this, they follow the\n"
					+ "# mouse to the pixel and land on whole percentages (every one from GUI scale Normal up).\n"},
			{"items.badDataNames", "names",
					"# #33: a slab or dye item with an impossible data value crashed the game when its name was shown.\n"},
			{"redstone.repeaterUpdates", "repeaters",
					"# #41: doors, trapdoors, note blocks, dispensers, TNT and rail junctions didn't react to a repeater beside\n"
					+ "# them switching, only to some other change nearby. With this, they react to repeaters as to any other source.\n"
					+ "# Turn it off to keep a circuit that counted on them ignoring a repeater.\n"},
			{"ticks.saved", "ticks",
					"# #42: a block's next step (a repeater's switch, falling sand, flowing water) was forgotten when its chunk\n"
					+ "# unloaded or the world closed, or when it lay near the edge of the loaded world: repeater clocks stopped\n"
					+ "# for good on reload. With this, the steps are saved with their chunk (worlds stay readable by vanilla).\n"},
			{"redstone.torchBurnout", "burnout",
					"# #44: after opening a world whose clock was behind the last one's, or setting the time back, redstone\n"
					+ "# torches that turned off eight times burnt out for good. Burnout itself is unchanged.\n"},
			{"crafting.shiftClickFits", "crafting",
					"# #45: shift-clicking a crafting result into an inventory with room for only part of it lost the rest,\n"
					+ "# and used the ingredients anyway. With this, the result is taken only when all of it fits.\n"},
			{"controls.pickBlock", "pick",
					"# #46: pick block (middle click) found nothing for redstone, repeaters, doors, signs, wheat, beds, cake or\n"
					+ "# sugar cane, didn't tell wool colours or slab kinds apart, and on a mob picked stone.\n"},
			{"vehicles.dismountOnTop", "dismount",
					"# #27: getting out of a boat could drop you through it (a rounding slip put your feet a hair inside it).\n"},
			{"minecarts.passItems", "minecarts",
					"# #31: a dropped item or an arrow lying on the track stopped a minecart dead.\n"},
			{"render.undersides", "undersides",
					"# #30: the undersides of fence bars drew black on a fence standing on the ground (lit from the block\n"
					+ "# below instead of the fence's own space).\n"},
			{"render.hotbarAfterCracks", "hotbar",
					"# #34: with Fast graphics in the Nether, aiming at a mob turned the hotbar white (blending left set by the\n"
					+ "# block-cracking overlay).\n"},
			{"render.ridingPoses", "ridingmodels",
					"# #34: a seated player's leggings stood upright, and the empty first-person hand could tilt as if riding.\n"},
			{"gui.deathScore", "deathscreen",
					"# #34: the death screen read \"Score: &e0\" (a broken colour code) instead of the score in yellow.\n"},
			{"fishing.catchToFeet", "fishing",
					"# #34: in single player a caught fish was thrown at your eyes' height and flew over your head from a dozen\n"
					+ "# blocks out; it's thrown at your feet, as a multiplayer server always threw it.\n"},
			{"multiplayer.refusedBreaks", "refusedbreaks",
					"# #5 (server): a block mined while the server had you more than 6 blocks away (lag) vanished on your screen\n"
					+ "# for 4 seconds although the server kept it. With this, the server sends it back at once.\n"},
			{"multiplayer.mobPhysics", "mobphysics",
					"# #15 (client): mobs on a server jittered: your game ran its own physics on them on top of the server's\n"
					+ "# positions (penned animals shoved apart and pulled back, knockback twice). With this, they follow the server.\n"},
			{"multiplayer.trackAfterTeleport", "tracking",
					"# #16a (server): a player who teleported or came through a portal stayed invisible to players already there\n"
					+ "# until one of them walked a few blocks.\n"},
			{"multiplayer.chunkOrder", "chunkorder",
					"# #16b (server): after a teleport or a fast move, chunks arrived row by row from the west edge, so you stood\n"
					+ "# over air for seconds. With this, the nearest come first.\n"},
			{"multiplayer.miningDelay", "miningdelay",
					"# #36 (client): on a server, every block after the first started with a quarter-second pause however long\n"
					+ "# ago the last one broke. With this, the pause after a break counts down and clears as in single player.\n"},
			{"multiplayer.signUpdates", "signs",
					"# #48 (server): a sign edited in the same moment as another block change in its chunk (water, crops,\n"
					+ "# redstone) stayed blank to everyone else.\n"},
			{"multiplayer.respawnEntities", "respawn",
					"# #49 (both): after respawning, mobs, items and players that went away while you were dead could stay\n"
					+ "# frozen on your screen. Fixed on either side: a server with this, or a client with this.\n"},
			{"multiplayer.turns", "turns",
					"# #50 (client): other players and mobs often stopped a third of the way short of a turn and stayed facing\n"
					+ "# up to about 11 degrees off.\n"},
			{"doors.loneTop", "doors",
					"# #55: a door's top half standing on its own (an edited or converted world) passed a redstone update to the\n"
					+ "# block below as if it were the door's bottom: that block was removed and a door dropped.\n"},
			{"falls.waterLandings", "landings",
					"# #56: falling into water one block deep hurt as if landing on stone from about half of all heights.\n"},
			{"controls.sneakCorners", "sneak",
					"# #57 (client): sneaking didn't stop a diagonal step off an inside corner (an L of blocks).\n"},
			{"multiplayer.explosionPush", "explosions",
					"# #58 (server): an explosion didn't push a player who had been hurt in the last half second (or had just\n"
					+ "# joined or respawned).\n"},
			{"mobs.headNod", "nod",
					"# #59: mobs looking up or down at you nodded their heads every tick.\n"},
			{"multiplayer.tpFall", "tpfall",
					"# #60 (server): a player moved by /tp in mid-fall took the fall as damage on landing wherever they arrived.\n"},
			{"multiplayer.drowningHeight", "drowning",
					"# #61 (server): the server measured drowning a little lower than the player's own game, so with the water's\n"
					+ "# surface between the two it drowned a player whose screen showed full air.\n"},
			{"multiplayer.spawnerMobs", "spawners",
					"# #62 (both): on a server every mob spawner showed a spinning pig. A server with this sends what each spawns;\n"
					+ "# a client with this shows it.\n"},
			{"spawning.besideWater", "spawnbox",
					"# #64: west and north of 0, 0, mobs didn't spawn with water or lava beside their west or north side.\n"},
			{"spawning.squidInWater", "squid",
					"# #66: squid could spawn in lava (and burn).\n"},
			{"blocks.fallingSandKeepsKind", "fallingsand",
					"# #67: falling sand or gravel deleted any block of its own kind it passed into (pushed by a piston into a\n"
					+ "# column, or sand placed in its path).\n"},
			{"world.rejoinTerrain", "rejoin",
					"# #68: rejoining a single-player world far from its spawn, the terrain around you wasn't loaded yet, so\n"
					+ "# for a moment the walls weren't solid: you could walk into one and suffocate until pushed out.\n"},
	};

	private static final List<String> off = new ArrayList<>();
	/** Set once the switches are read: the settings screen asks this class what's running (applied). */
	private static volatile boolean read;

	@Override
	public void onLoad(String mixinPackage) {
		read = true;
		Properties p = new Properties();
		File file = FabricLoader.getInstance().getConfigDir().resolve("oldfaithful-fixes.properties").toFile();
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
			for (String[] f : FIXES) {
				if (!p.containsKey(f[0])) add.append('\n').append(f[2]).append(f[0]).append("=true\n");
			}
			if (add.length() > 0) {
				try (OutputStream out = new FileOutputStream(file, true)) {
					out.write(add.toString().getBytes(StandardCharsets.ISO_8859_1));
				}
			}
		} catch (IOException e) {
			System.out.println("[oldfaithful-fixes] WARN Could not read or write " + file + ", every fix on: " + e);
		}
		for (String[] f : FIXES) {
			if (!Boolean.parseBoolean(p.getProperty(f[0], "true").trim())) off.add(f[0]);
		}
		if (!off.isEmpty()) System.out.println("[oldfaithful-fixes] Turned off in " + file.getName() + ": " + String.join(", ", off));
	}

	/** Whether the fix was on when the game started, so its mixins are in. */
	public static boolean applied(String key) {
		return !off.contains(key);
	}

	/** Every switch, in the file's order. */
	public static List<String> keys() {
		List<String> keys = new ArrayList<>();
		for (String[] f : FIXES) keys.add(f[0]);
		return keys;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String pkg = mixinClassName.substring(0, mixinClassName.lastIndexOf('.'));
		pkg = pkg.substring(pkg.lastIndexOf('.') + 1);
		for (String[] f : FIXES) {
			if (f[1].equals(pkg)) return !off.contains(f[0]);
		}
		throw new IllegalStateException("A mixin outside any fix's package: " + mixinClassName);
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
