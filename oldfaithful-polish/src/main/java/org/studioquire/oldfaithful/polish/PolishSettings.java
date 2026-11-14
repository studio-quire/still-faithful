package org.studioquire.oldfaithful.polish;

import java.util.List;

import org.studioquire.oldfaithful.options.settings.Setting;
import org.studioquire.oldfaithful.options.settings.SettingsProvider;
import org.studioquire.oldfaithful.options.settings.SettingsRegistry;

/**
 * Still Faithful's switches on the settings screen (Old Faithful's options mod, docs/options.md > The settings screen):
 * a section of their own in Changes, "Polish", since they're the edition's deliberate departures from beta rather than
 * fixes of its mistakes. Each reads "Polished" or "Beta" (dusk: Stepped, Smooth or Beta; obsidian and smooth water have
 * three of their own). Every one waits for a restart, as the fixes do (a change that's off has no mixins in the game),
 * except dusk between Stepped and Smooth, and smooth water but from Beta. Only loaded by the options mod, on the client.
 */
public final class PolishSettings implements SettingsProvider {
	private static final String WORLD = "world";
	private static final String CLIENT = "client";
	static final String GROUP = "polish";

	/** Key, side, name on the button, hint (what beta did; at most two lines under the buttons). */
	static final String[][] ROWS = {
			{"blocks.stairsDropStairs", WORLD, "Stair Drops", "Stairs dropped a plank or a cobblestone instead of themselves."},
			{"leaves.placedStay", WORLD, "Placed Leaves", "Leaves you placed decayed unless a log was near."},
			{"leaves.reach", WORLD, "Leaf Reach", "Leaves decayed more than 4 blocks from a log, so big oaks lost the tops of their leaf clusters."},
			{"glowstone.anyTool", CLIENT, "Glowstone",
					"Glowstone dropped nothing unless mined with a pickaxe. On a server, only where it has this change too."},
			{PolishConfig.OBSIDIAN_KEY, WORLD, "Obsidian",
					"Obsidian took 15 s with a diamond pickaxe, not 1.0's 9.4. Diamond Only leaves anything else at beta's 50 s."},
			{"liquids.lavaThins", WORLD, "Lingering Lava", "Flowing lava cut off from its source could stay forever."},
			{"liquids.waterSources", WORLD, "Water Sources", "Water between two sources became a source only over solid ground."},
			{"boats.sturdy", WORLD, "Sturdy Boats", "Boats broke on brushing a shore or a floating item at speed."},
			{"boats.handling", WORLD, "Boat Handling",
					"A boat you stopped paddling coasted forty blocks and bobbed. Paddling stays beta's without Boat Steering."},
			{"boats.steering", WORLD, "Boat Steering",
					"Boats went where you looked and swung round to face where they moved. On a server, riders need Riding Look."},
			{"minecarts.cartCollisions", WORLD, "Cart Collisions", "On north-south and curved track, carts stopped dead against each other."},
			{"animals.keepTended", WORLD, "Kept Animals", "Animals you'd sheared, saddled or ridden despawned when you went away."},
			{"saves.backgroundAutosave", WORLD, "Autosave", "Every 2 seconds the game paused to write the chunks that had changed."},
			{"blocks.chestsFaceYou", WORLD, "Chest Facing", "A chest faced away from the block behind it, or south; never the way you chose."},
			{"blocks.chestPairs", WORLD, "Chest Pairs",
					"Any two chests side by side joined, and none could go beside a double chest. Needs Chest Facing."},
			{"furnace.woodenFuel", WORLD, "Wooden Fuel", "A furnace burned planks, logs, sticks and most wooden blocks, but not tools, doors, signs, ladders or boats."},
			{"mobs.skeletonArrows", WORLD, "Skeleton Arrows",
					"Skeletons shot from well above their heads, so under a ceiling two blocks high their arrows stuck in it."},
			{"mobs.squidSurface", WORLD, "Squid Surfacing",
					"A squid that swam up to the surface bounced against it, up and down a few times a second."},
			{"render.chestLids", CLIENT, "Chest Lids", "A double chest's lid had a border down the middle, one lid to each half."},
			{"blocks.fenceShape", CLIENT, "Fence Shape",
					"Fences were whole blocks to walk into and aim at. On a server, walking past posts needs this change there too."},
			{"blocks.fenceJoins", CLIENT, "Fence Joins",
					"Fences joined only other fences, standing apart from a wall, glass or any other block beside them."},
			{"inventory.shiftClick", CLIENT, "Shift-Click", "Shift-click did nothing in a dispenser, and never filled a furnace or put armour on."},
			{"combat.heldAttack", CLIENT, "Held Attack",
					"Holding the button to attack or mine hit whatever came under the crosshair, not just what you'd clicked."},
			{"render.chatBars", CLIENT, "Chat Bars", "Every line of chat had a dark bar the chat's full width behind it, however short the line."},
			{PolishConfig.SMOOTH_WATER_KEY, CLIENT, "Smooth Water",
					"With Smooth Lighting, water was still lit in squares. Shaded: walls darken it, as they do the ground. Even: they don't."},
			{"render.smoothCactus", CLIENT, "Smooth Cactus", "With Smooth Lighting on, cactus was still lit flat, a face at a time, against the smooth sand round it."},
			{"render.droppedItemSize", CLIENT, "Dropped Blocks", "Dropped stairs, fences and other part-blocks lay at twice the size."},
			{"render.entityDistance", CLIENT, "Entity Distance",
					"Small things vanished close by: a dropped item past 16 blocks, a chicken past 21, an arrow past 32."},
			{"render.particleDistance", CLIENT, "Particle Distance",
					"Particles showed only within 16 blocks, so a blast or a mob dying further off left no puff."},
			{"menu.texturePacks", CLIENT, "Texture Packs", "The title screen's Mods and Texture Packs button only ever held texture packs."},
			{"render.bowPose", CLIENT, "Bow Pose", "Players and skeletons held a bow out flat, like any item."},
			{"skins.modern", CLIENT, "Modern Skins", "Skins showed their top half only: no jacket, sleeves or trousers, one arm and leg for both, no slim arms."},
			{PolishConfig.DUSK_KEY, CLIENT, "Dusk",
					"Beta: dusk's eleven steps of light each swept across the view. Stepped: each step at once. Smooth: no steps."},
	};

	@Override
	public void register(SettingsRegistry r) {
		r.changes(GROUP, "Polish", 10, "What later versions improved. Beta has them the old way.");
		List<String> keys = PolishConfig.keys();
		for (String[] row : ROWS) {
			if (!keys.contains(row[0])) continue;
			if (row[0].equals(PolishConfig.DUSK_KEY)) {
				r.add(GROUP, dusk(row));
				continue;
			}
			if (row[0].equals(PolishConfig.OBSIDIAN_KEY)) {
				// Polished (1.0's), Diamond Only, Beta; after a restart, and the server's in multiplayer (Obsidian).
				r.add(GROUP, Setting.choice(PolishConfig.FILE, row[0], row[2], row[3], "true", "false", "Beta", "true", "Polished", "diamond", "Diamond Only")
						.running(() -> PolishConfig.applied(row[0]) ? PolishConfig.obsidianAtStart() : "false").serverDecides());
				continue;
			}
			if (row[0].equals(PolishConfig.SMOOTH_WATER_KEY)) {
				r.add(GROUP, smoothWater(row));
				continue;
			}
			Setting s = Setting.choice(PolishConfig.FILE, row[0], row[2], row[3], PolishConfig.defaultValue(row[0]), "false", "Beta", "true", "Polished")
					.running(() -> String.valueOf(PolishConfig.applied(row[0])));
			r.add(GROUP, WORLD.equals(row[1]) ? s.serverDecides() : s);
		}
		for (String key : keys) {
			if (described(key)) continue;
			r.add(GROUP, Setting.choice(PolishConfig.FILE, key, key, "Described in config/" + PolishConfig.FILE + ".", "true",
					"false", "Beta", "true", "Polished").running(() -> String.valueOf(PolishConfig.applied(key))));
		}
	}

	/**
	 * #6: Stepped, Smooth or Beta. Between Stepped and Smooth it changes at once (the same draw-time light, only the
	 * darkness it's drawn at differs); to or from Beta waits for a restart, since Beta has none of the dusk mixins in.
	 */
	private static Setting dusk(String[] row) {
		return Setting.choice(PolishConfig.FILE, row[0], row[2], row[3], "smooth", "stepped", "Stepped", "smooth", "Smooth", "beta", "Beta")
				.running(() -> PolishConfig.applied(row[0]) ? (DuskLight.smooth ? "smooth" : "stepped") : "beta")
				.applyNow(v -> {
					if (PolishConfig.applied(row[0]) && !v.equals("beta")) DuskLight.smooth = v.equals("smooth");
				});
	}

	/**
	 * Shaded, Even or Beta, at once while the change's mixins are in (Beta by their standing aside: SmoothWater.choose);
	 * from Beta, after a restart, since a game started at Beta has none of them in.
	 */
	private static Setting smoothWater(String[] row) {
		return Setting.choice(PolishConfig.FILE, row[0], row[2], row[3], SmoothWater.SHADED, SmoothWater.SHADED, "Shaded", SmoothWater.EVEN, "Even",
				SmoothWater.BETA, "Beta")
				.running(() -> PolishConfig.applied(row[0]) ? SmoothWater.look : SmoothWater.BETA)
				.applyNow(v -> {
					if (PolishConfig.applied(row[0])) SmoothWater.choose(v);
				});
	}

	static boolean described(String key) {
		for (String[] row : ROWS) {
			if (row[0].equals(key)) return true;
		}
		return false;
	}
}
