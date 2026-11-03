package org.studioquire.oldfaithful.fixes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.studioquire.oldfaithful.options.settings.Setting;
import org.studioquire.oldfaithful.options.settings.SettingsProvider;
import org.studioquire.oldfaithful.options.settings.SettingsRegistry;

/**
 * The fixes' switches on Old Faithful's settings screen (oldfaithful-options, docs/options.md > The settings screen):
 * a short name and a hint for each, in groups. Every fix waits for a restart, since a fix that's off has no mixins in
 * the game at all (FixesConfig). Only loaded by the options mod, on the client.
 * <p>
 * A new fix gets a row here (or a place in {@link #SERVER_ONLY}); until it has one it shows under "More" by its key.
 */
public final class FixesSettings implements SettingsProvider {
	static final String FILE = "oldfaithful-fixes.properties";

	/** Where the world runs: single player here, the server's own setting in multiplayer. */
	private static final String WORLD = "world";
	/** On this game, wherever it plays. */
	private static final String CLIENT = "client";

	/** Key, group, side, name on the button, hint (what beta did; at most two lines under the buttons). */
	static final String[][] ROWS = {
			{"tools.lastUse", "blocks", WORLD, "Tool's Last Use", "The block broken by a tool's last use dropped nothing."},
			{"slabs.wooden", "blocks", CLIENT, "Wooden Slabs",
					"Wooden slabs dropped only when mined with a pickaxe. On a server, fixed only where it has this fix too."},
			{"tools.speedByMaterial", "blocks", CLIENT, "Tool Speeds",
					"Axes were slow on tables and doors, pickaxes on furnaces and rails. On a server, only where it has this fix too."},
			{"furnace.lavaBucket", "blocks", WORLD, "Lava Bucket Fuel", "A furnace burning a lava bucket used up the bucket too."},
			{"liquids.dropsBelow", "blocks", WORLD, "Falling Water",
					"Water falling onto torches, rails, redstone or flowers destroyed them without dropping them."},
			{"crafting.shiftClickFits", "blocks", WORLD, "Shift-Click Crafting",
					"Shift-clicking a crafting result into room for only part of it lost the rest."},
			{"controls.pickBlock", "blocks", CLIENT, "Pick Block",
					"Middle click found nothing for redstone, doors, signs or beds, mixed up wool colours, and picked stone on a mob."},
			{"items.badDataNames", "blocks", CLIENT, "Odd Item Names",
					"A slab or dye with an impossible data value crashed the game when its name was shown."},
			{"blocks.fallingSandKeepsKind", "blocks", WORLD, "Falling Sand",
					"Falling sand or gravel deleted any block of its own kind it passed into."},

			{"slimes.splitOnOverkill", "mobs", WORLD, "Slimes Splitting", "A big slime killed by one strong hit didn't split."},
			{"pigs.saddleDrop", "mobs", WORLD, "Saddled Pigs", "A saddled pig lost its saddle when killed."},
			{"sleep.wakeThroughWalls", "mobs", WORLD, "Waking in Bed",
					"A monster could wake you from the far side of the wall behind your bed."},
			{"vehicles.dismountOnTop", "mobs", CLIENT, "Leaving Boats", "Getting out of a boat could drop you through it."},
			{"minecarts.passItems", "mobs", WORLD, "Minecarts", "A dropped item or an arrow on the track stopped a minecart dead."},
			{"fishing.catchToFeet", "mobs", WORLD, "Fishing Catch",
					"A catch flew at your eyes and over your head from far out, unlike on a server."},
			{"falls.waterLandings", "mobs", WORLD, "Water Landings",
					"Falling into water one block deep hurt as on stone from about half of all heights."},
			{"controls.sneakCorners", "mobs", CLIENT, "Sneaking at Corners",
					"Sneaking didn't stop a diagonal step off an inside corner."},
			{"mobs.headNod", "mobs", WORLD, "Mobs Nodding", "Mobs looking up or down at you nodded their heads every tick."},
			{"spawning.besideWater", "mobs", WORLD, "Spawning by Water",
					"West and north of 0, 0, mobs didn't spawn beside water or lava on their west or north side."},
			{"spawning.squidInWater", "mobs", WORLD, "Squid in Lava", "Squid could spawn in lava, and burn."},
			{"world.rejoinTerrain", "mobs", CLIENT, "Rejoining Worlds",
					"Rejoining far from spawn, the walls around you weren't solid for a moment: you could walk in and suffocate."},

			{"redstone.repeaterUpdates", "redstone", WORLD, "Repeaters",
					"Doors, note blocks, dispensers, TNT and rails ignored a repeater beside them. Beta keeps circuits built on that."},
			{"ticks.saved", "redstone", WORLD, "Saved Block Ticks",
					"Repeaters, falling sand and flowing water forgot their next step when unloaded: clocks stopped on reload."},
			{"redstone.torchBurnout", "redstone", WORLD, "Torch Burnout",
					"After the time was set back, redstone torches could burn out for good."},
			{"doors.loneTop", "redstone", WORLD, "Lone Door Tops",
					"A door's top half on its own deleted the block below when redstone changed beside it."},

			{"sky.afterContainer", "looks", CLIENT, "Sky Behind Chests", "The sky turned grey while a chest, furnace or inventory was open."},
			{"render.undersides", "looks", CLIENT, "Fence Undersides", "The undersides of fence bars drew black."},
			{"render.hotbarAfterCracks", "looks", CLIENT, "White Hotbar",
					"With Fast graphics in the Nether, aiming at a mob turned the hotbar white."},
			{"render.ridingPoses", "looks", CLIENT, "Riding Poses",
					"A seated player's leggings stood upright, and the empty hand could tilt as if riding."},
			{"gui.cursorItemOnTop", "looks", CLIENT, "Cursor Item",
					"The item on the cursor in an inventory was drawn behind the player figure and labels."},
			{"gui.deathScore", "looks", CLIENT, "Death Screen Score", "The death screen read \"Score: &e0\"."},
			{"gui.sliderSteps", "looks", CLIENT, "Slider Steps", "The volume and sensitivity sliders skipped some percentages."},
			{"sound.stepsAfterFalls", "looks", CLIENT, "Footsteps", "Footsteps owed in the air all played at once on landing."},

			{"multiplayer.mobPhysics", "multiplayer", CLIENT, "Mob Movement",
					"Mobs on a server jittered: your game ran its own physics on top of the server's positions."},
			{"multiplayer.turns", "multiplayer", CLIENT, "Mob Turning",
					"Players and mobs on a server often stopped short of a turn, facing up to 11 degrees off."},
			{"multiplayer.miningDelay", "multiplayer", CLIENT, "Mining Pause",
					"On a server, every block after the first started with a quarter-second pause."},
			{"multiplayer.respawnEntities", "multiplayer", CLIENT, "After Respawning",
					"Mobs and items gone while you were dead could stay frozen on screen. Fixed if either side has this fix."},
			{"multiplayer.spawnerMobs", "multiplayer", CLIENT, "Spawner Mobs",
					"Every spawner on a server showed a pig. Fixed where the server has this fix too."},
	};

	/** Fixes that run only on a server: a client's switch would change nothing, so they're left to the server's file. */
	static final Set<String> SERVER_ONLY = new HashSet<>(Arrays.asList(
			"multiplayer.refusedBreaks", "multiplayer.trackAfterTeleport", "multiplayer.chunkOrder", "multiplayer.signUpdates",
			"multiplayer.explosionPush", "multiplayer.tpFall", "multiplayer.drowningHeight"));

	@Override
	public void register(SettingsRegistry r) {
		r.group("blocks", "Blocks and Items", 10);
		r.group("mobs", "Mobs and Vehicles", 20);
		r.group("redstone", "Redstone and Lighting", 30);
		r.group("looks", "Looks and Sounds", 40);
		r.group("multiplayer", "Multiplayer", 50, "Fixes that run on a server are set in the server's own config/" + FILE + ".");
		List<String> keys = FixesConfig.keys();
		for (String[] row : ROWS) {
			if (!keys.contains(row[0])) continue;
			Setting s = Setting.fix(row[1], FILE, row[0], row[3], row[4]).running(() -> String.valueOf(FixesConfig.applied(row[0])));
			r.add(WORLD.equals(row[2]) ? s.serverDecides() : s);
		}
		for (String key : keys) {
			if (SERVER_ONLY.contains(key) || described(key)) continue;
			r.add(Setting.fix("more", FILE, key, key, "Described in config/" + FILE + ".")
					.running(() -> String.valueOf(FixesConfig.applied(key))));
		}
	}

	static boolean described(String key) {
		for (String[] row : ROWS) {
			if (row[0].equals(key)) return true;
		}
		return false;
	}
}
