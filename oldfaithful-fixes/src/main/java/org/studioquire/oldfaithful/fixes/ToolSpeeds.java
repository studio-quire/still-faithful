package org.studioquire.oldfaithful.fixes;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.AxeItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolItem;

/**
 * #10: a pickaxe, axe or shovel mines fast only the blocks on its hand-written list (PickaxeItem, AxeItem,
 * ShovelItem), and the lists missed many blocks of the tool's own material: crafting tables, doors, fences and
 * wooden stairs for the axe; furnaces, bricks, cobblestone stairs and redstone ore for the pickaxe; soul sand for the
 * shovel. The pickaxe's harvest check already goes by material (stone and metal), so a pickaxe was required for a
 * furnace or redstone ore yet mined it at hand speed. With this, a tool also mines fast every block of its material,
 * and a few blocks whose material says nothing of what they're made of (rails, stone buttons, ladders). Obsidian keeps
 * beta's speed (the user's call, 2026-11-02: its slowness is well known, and at beta's hardness it would take under 2
 * seconds with a diamond pickaxe). Redstone ore kept it too until the user's call of 2026-11-12. Mining speed only:
 * which blocks need a tool to drop is unchanged.
 */
public final class ToolSpeeds {
	/** In multiplayer, whether the server has this fix (so the client mines at the speed the server will accept). */
	public static volatile boolean serverAgrees;
	/** In multiplayer, whether the server's fix includes redstone ore (Old Faithful 0.2.6 and before left it out). */
	public static volatile boolean serverAgreesOnRedstoneOre;
	/** Set while the multiplayer client asks a server without this fix how fast a block mines. */
	public static int asBeta;
	/** Set while the multiplayer client asks a server whose fix leaves redstone ore out. */
	public static int redstoneOreAsBeta;

	private ToolSpeeds() {
	}

	/** Whether the tool should mine the block fast though its list leaves it out. */
	public static boolean addedFor(ToolItem tool, Block block) {
		if (asBeta > 0 || block == Block.OBSIDIAN) return false;
		if (redstoneOreAsBeta > 0 && (block == Block.REDSTONE_ORE || block == Block.LIT_REDSTONE_ORE)) return false;
		Material m = block.material;
		if (tool instanceof PickaxeItem) {
			return m == Material.STONE || m == Material.METAL || block == Block.RAIL || block == Block.POWERED_RAIL
					|| block == Block.DETECTOR_RAIL || block == Block.BUTTON;
		}
		if (tool instanceof AxeItem) return m == Material.WOOD || m == Material.PUMPKIN || block == Block.LADDER;
		if (tool instanceof ShovelItem) {
			return m == Material.SOIL || m == Material.SOLID_ORGANIC || m == Material.SAND || m == Material.SNOW_LAYER
					|| m == Material.SNOW_BLOCK || m == Material.CLAY;
		}
		return false;
	}
}
