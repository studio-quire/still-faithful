package org.studioquire.oldfaithful.polish.selftest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.LargeOakTreeFeature;
import org.studioquire.oldfaithful.polish.LeafReach;
import org.studioquire.oldfaithful.polish.PlacedLeaves;
import org.studioquire.oldfaithful.polish.PolishConfig;
import org.studioquire.oldfaithful.polish.selftest.mixin.LargeOakAccess;
import org.studioquire.oldfaithful.selftest.SelfTest;
import org.studioquire.oldfaithful.selftest.WorldCheck;

/**
 * Leaf Reach ({@code leaves.reach}): big oaks grown by beta's own generator on a block of dirt in the air, {@link #TREES}
 * of each kind: the world's (its generator prepares them, and Wild Saplings grows them so: leaf clusters five layers tall)
 * and beta's sapling's (unprepared: four). Each tree is grown, then left to beta's own decay tick, called on every leaf
 * marked to look for a log until a pass changes nothing (what random ticks come to): first those its growing marked (a
 * log replacing a leaf marks the leaves round it, and the tree's trunk and branches grow through its clusters), what falls
 * from a tree nobody touched; then with the trunk's foot cut, as a player starts on a tree (a broken log marks the leaves
 * within the reach of it); then every leaf left, as broken logs and leaves all round would mark them. Grown again for each
 * reach, beta's 4 (as {@link LeafReach#betaForTest}, or with the switch off, beta's code itself) and 6, the same tree.
 * <p>
 * The check: after every leaf has looked, the leaves left are exactly those within the reach of a log, counted in steps
 * through leaves (a search over the tree as it grew), on every tree, so the window and the flood's steps both changed.
 * What falls is told: from untouched trees and with every leaf looking, by how far each leaf is from wood (no wood joined
 * at all is a cluster whose branch beta never places, low on the trunk, with nothing joining it to another), and which of
 * the generator's clusters fall whole. Every tree in {@code local/run/leaf-reach/trees-<polished|beta>.csv}.
 * <p>
 * Then a broken log: leaves 5 and 6 from it are marked too (with only beta's 4 marked, a leaf 6 from it, now 7 from the
 * nearest log, hangs on); placed leaves on the way and past the end stay where Placed Leaves is on.
 */
public final class LeafReachCheck implements WorldCheck {
	private static final String KEY = "leaves.reach";
	private static final File OUT = new File("../leaf-reach");
	private static final int TREES = 100;
	/** The trunk's foot, on a block of dirt; the box round it every tree stays inside, and the empty margin round that. */
	private static final int Y = 92, SIDE = 10, UP = 18, MARGIN = 8;
	private static final int NX = 2 * SIDE + 1, NY = UP + 2, CELLS = NX * NX * NY;
	/** How long a tick may work beta's light queue before the next tree. */
	private static final long LIGHT_NS = 40_000_000L;
	/** Distances kept apart: 1 to 6, then 7 and more; none (no wood joined) at 0. */
	private static final int FAR = 7;

	private SelfTest results;
	private World world;
	private boolean on;
	/** The reaches measured: beta's 4, and 6 where the switch is on. */
	private int[] reaches;
	private int x0, z0;
	private int next;
	private final Tree[] trees = new Tree[2 * TREES];
	private final long[] tickNs = new long[2];
	private final long[] tickCalls = new long[2];
	private final int[] notExact = new int[2];
	private int differed, outsideClusters, outsideBox, failedToGrow;
	private String firstNotExact;

	private static final class Tree {
		final boolean prepared;
		final int seed;
		int height, clusters, branchless, leaves, logs, grownMarked;
		/** Leaves 7 or more from wood (or with none joined) in no cluster but those whose branch beta never places. */
		int farBranchless;
		/** Leaves by their distance from wood through leaves: [0] none joined, [1..6], [7] 7 or more. */
		final int[] distances = new int[FAR + 1];
		final int[] fellUntouched = new int[2], fellFoot = new int[2], fellAll = new int[2], wholeClusters = new int[2], wholeBranchless = new int[2], saplings = new int[2];

		Tree(boolean prepared, int seed) {
			this.prepared = prepared;
			this.seed = seed;
		}
	}

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		this.world = mc.world;
		on = PolishConfig.applied(KEY);
		reaches = on ? new int[] {LeafReach.BETA, LeafReach.REACH} : new int[] {LeafReach.BETA};
		world.getProperties().setRaining(false);
		world.getProperties().setThundering(false);
		world.getProperties().setRainTime(1_000_000);
		world.getProperties().setThunderTime(1_000_000);
		int px = MathHelper.floor(mc.player.x), pz = MathHelper.floor(mc.player.z);
		boolean found = false;
		for (int i = 0; i < 49 && !found; i++) {
			x0 = px - 40 + (i % 7 - 3) * 48;
			z0 = pz - 40 + (i / 7 - 3) * 48;
			found = empty();
		}
		results.check("still leaf reach: an empty place in the air for the trees, at " + x0 + "," + z0 + " (loaded: "
				+ world.isRegionLoaded(x0 - SIDE - MARGIN, Y - 1 - MARGIN, z0 - SIDE - MARGIN, x0 + SIDE + MARGIN, Y + UP + MARGIN, z0 + SIDE + MARGIN) + ")",
				found && world.isRegionLoaded(x0 - SIDE - MARGIN, Y - 1 - MARGIN, z0 - SIDE - MARGIN, x0 + SIDE + MARGIN, Y + UP + MARGIN, z0 + SIDE + MARGIN));
		if (found) brokenLog();
		for (int i = 0; i < TREES; i++) {
			trees[2 * i] = new Tree(true, 1 + i);
			trees[2 * i + 1] = new Tree(false, 1 + i);
		}
		if (!found) next = trees.length;
	}

	@Override
	public boolean tick(Minecraft mc) {
		if (!lightDone()) return false;
		if (next < trees.length) {
			measure(trees[next++]);
			return false;
		}
		LeafReach.betaForTest = false;
		report();
		return true;
	}

	private boolean empty() {
		for (int x = x0 - SIDE - MARGIN; x <= x0 + SIDE + MARGIN; x++) {
			for (int z = z0 - SIDE - MARGIN; z <= z0 + SIDE + MARGIN; z++) {
				for (int y = Y - 1 - MARGIN; y <= Y + UP + MARGIN; y++) {
					if (world.getBlockId(x, y, z) != 0) return false;
				}
			}
		}
		return true;
	}

	/** Beta's light queue worked for a while: true once it's empty. Every tree grown and cleared is a few thousand changes. */
	private boolean lightDone() {
		long until = System.nanoTime() + LIGHT_NS;
		boolean more;
		do {
			more = world.doLightingUpdates();
		} while (more && System.nanoTime() < until);
		return !more;
	}

	private int index(int x, int y, int z) {
		return ((x - x0 + SIDE) * NX + (z - z0 + SIDE)) * NY + (y - Y + 1);
	}

	/** One tree, grown afresh for each reach. */
	private void measure(Tree tree) {
		int[] grownIds = null;
		int[] dist = null, footless = null;
		int[][] clusterCells = null;
		boolean[] branchless = null;
		for (int side = 0; side < reaches.length; side++) {
			int reach = reaches[side];
			LeafReach.betaForTest = reach == LeafReach.BETA;
			clear();
			world.setBlock(x0, Y - 1, z0, Block.DIRT.id);
			LargeOakTreeFeature feature = new LargeOakTreeFeature();
			if (tree.prepared) feature.prepare(1.0, 1.0, 1.0);
			if (!feature.generate(world, new Random(tree.seed), x0, Y, z0)) {
				failedToGrow++;
				clear();
				return;
			}
			LargeOakAccess plan = (LargeOakAccess) (Object) feature;
			int[] ids = new int[CELLS];
			int marked = 0;
			for (int x = x0 - SIDE; x <= x0 + SIDE; x++) {
				for (int z = z0 - SIDE; z <= z0 + SIDE; z++) {
					for (int y = Y - 1; y <= Y + UP; y++) {
						int id = world.getBlockId(x, y, z);
						ids[index(x, y, z)] = id;
						if (id != 0 && (Math.abs(x - x0) == SIDE || Math.abs(z - z0) == SIDE || y == Y + UP)) outsideBox++;
						if (id == Block.LEAVES.id && (world.getBlockMeta(x, y, z) & 8) != 0) marked++;
					}
				}
			}
			if (grownIds == null) {
				grownIds = ids;
				tree.grownMarked = marked;
				tree.height = plan.oldfaithful$height();
				for (int id : ids) {
					if (id == Block.LEAVES.id) tree.leaves++;
					else if (id == Block.LOG.id) tree.logs++;
				}
				dist = distances(ids);
				for (int i = 0; i < CELLS; i++) {
					if (ids[i] == Block.LEAVES.id) tree.distances[dist[i] < 0 ? 0 : Math.min(dist[i], FAR)]++;
				}
				int[][] branches = plan.oldfaithful$branches();
				int height = plan.oldfaithful$height(), layers = plan.oldfaithful$clusterHeight();
				tree.clusters = branches.length;
				clusterCells = new int[branches.length][];
				branchless = new boolean[branches.length];
				boolean[] inCluster = new boolean[CELLS];
				for (int c = 0; c < branches.length; c++) {
					// LargeOakTreeFeature.placeBranches: no branch where it would leave the trunk under a fifth of the height.
					branchless[c] = branches[c][3] - Y < height * 0.2;
					if (branchless[c]) tree.branchless++;
					clusterCells[c] = cluster(branches[c], layers, ids, inCluster);
				}
				boolean[] inBranched = new boolean[CELLS];
				for (int c = 0; c < branches.length; c++) {
					if (!branchless[c]) {
						for (int i : clusterCells[c]) inBranched[i] = true;
					}
				}
				for (int i = 0; i < CELLS; i++) {
					if (ids[i] != Block.LEAVES.id) continue;
					if (!inCluster[i]) outsideClusters++;
					if ((dist[i] < 0 || dist[i] >= FAR) && !inBranched[i]) tree.farBranchless++;
				}
				// The distances once the trunk's foot is cut.
				int[] cut = ids.clone();
				cut[index(x0, Y, z0)] = 0;
				footless = distances(cut);
			} else if (!Arrays.equals(ids, grownIds) || marked != tree.grownMarked) {
				differed++;
			}
			// Untouched: only what growing marked.
			decay(x0 - SIDE, Y - 1, z0 - SIDE, x0 + SIDE, Y + UP, z0 + SIDE, side);
			int left = countLeaves();
			tree.fellUntouched[side] = tree.leaves - left;
			tree.saplings[side] = saplingsDropped();
			// The trunk's foot cut, as a player starts on a tree: the leaves within the reach of it look, and what falls
			// marks what's round it.
			world.setBlock(x0, Y, z0, 0);
			decay(x0 - SIDE, Y - 1, z0 - SIDE, x0 + SIDE, Y + UP, z0 + SIDE, side);
			tree.fellFoot[side] = left - countLeaves();
			saplingsDropped();
			// Every leaf looks.
			for (int x = x0 - SIDE; x <= x0 + SIDE; x++) {
				for (int z = z0 - SIDE; z <= z0 + SIDE; z++) {
					for (int y = Y - 1; y <= Y + UP; y++) {
						if (world.getBlockId(x, y, z) == Block.LEAVES.id) world.setBlockMetaWithoutNotifyingNeighbors(x, y, z, world.getBlockMeta(x, y, z) | 8);
					}
				}
			}
			decay(x0 - SIDE, Y - 1, z0 - SIDE, x0 + SIDE, Y + UP, z0 + SIDE, side);
			tree.fellAll[side] = tree.leaves - countLeaves();
			// Exactly those within the reach stay.
			boolean exact = true;
			for (int x = x0 - SIDE; x <= x0 + SIDE && exact; x++) {
				for (int z = z0 - SIDE; z <= z0 + SIDE && exact; z++) {
					for (int y = Y - 1; y <= Y + UP && exact; y++) {
						int i = index(x, y, z);
						if (grownIds[i] != Block.LEAVES.id) continue;
						boolean stays = footless[i] > 0 && footless[i] <= reach;
						exact = (world.getBlockId(x, y, z) == Block.LEAVES.id) == stays;
					}
				}
			}
			if (!exact) {
				notExact[side]++;
				if (firstNotExact == null) firstNotExact = (tree.prepared ? "world's" : "sapling's") + " seed " + tree.seed + " at reach " + reach;
			}
			for (int c = 0; c < clusterCells.length; c++) {
				boolean any = false, gone = true;
				for (int i : clusterCells[c]) {
					if (grownIds[i] != Block.LEAVES.id) continue;
					any = true;
					int x = i / NY / NX + x0 - SIDE, z = i / NY % NX + z0 - SIDE, y = i % NY + Y - 1;
					if (world.getBlockId(x, y, z) == Block.LEAVES.id) gone = false;
				}
				if (any && gone) {
					tree.wholeClusters[side]++;
					if (branchless[c]) tree.wholeBranchless[side]++;
				}
			}
			saplingsDropped();
			clear();
		}
	}

	/** The cells of one of the generator's clusters, as LargeOakTreeFeature.placeFoliageCluster lays them: two wide at its ends, three between. */
	private int[] cluster(int[] base, int layers, int[] ids, boolean[] inCluster) {
		int[] cells = new int[layers * 49];
		int n = 0;
		for (int layer = 0; layer < layers; layer++) {
			float shape = layer == 0 || layer == layers - 1 ? 2.0F : 3.0F;
			int r = (int) (shape + 0.618);
			for (int a = -r; a <= r; a++) {
				for (int b = -r; b <= r; b++) {
					if (Math.sqrt(Math.pow(Math.abs(a) + 0.5, 2.0) + Math.pow(Math.abs(b) + 0.5, 2.0)) > shape) continue;
					int x = base[0] + a, y = base[1] + layer, z = base[2] + b;
					if (Math.abs(x - x0) > SIDE || Math.abs(z - z0) > SIDE || y < Y - 1 || y > Y + UP) continue;
					int i = index(x, y, z);
					cells[n++] = i;
					if (ids[i] == Block.LEAVES.id) inCluster[i] = true;
				}
			}
		}
		return Arrays.copyOf(cells, n);
	}

	/** Each leaf's distance from a log in steps through leaves (face to face, as beta's flood goes); -1 for none joined. */
	private int[] distances(int[] ids) {
		int[] dist = new int[CELLS];
		Arrays.fill(dist, -1);
		ArrayDeque<Integer> open = new ArrayDeque<>();
		for (int i = 0; i < CELLS; i++) {
			if (ids[i] == Block.LOG.id) {
				dist[i] = 0;
				open.add(i);
			}
		}
		int[] steps = {NX * NY, -NX * NY, NY, -NY, 1, -1};
		while (!open.isEmpty()) {
			int i = open.poll();
			int x = i / NY / NX, z = i / NY % NX, y = i % NY;
			for (int s = 0; s < 6; s++) {
				if (s == 0 && x == NX - 1 || s == 1 && x == 0 || s == 2 && z == NX - 1 || s == 3 && z == 0 || s == 4 && y == NY - 1 || s == 5 && y == 0) continue;
				int j = i + steps[s];
				if (ids[j] == Block.LEAVES.id && dist[j] < 0) {
					dist[j] = dist[i] + 1;
					open.add(j);
				}
			}
		}
		return dist;
	}

	/**
	 * Beta's own tick for every leaf in the box marked to look for a log, pass after pass until one changes nothing (a
	 * placed leaf keeps its mark and stays: Placed Leaves): what random ticks would come to.
	 */
	private void decay(int xa, int ya, int za, int xb, int yb, int zb, int side) {
		for (int pass = 0; pass < 500; pass++) {
			boolean changed = false;
			for (int x = xa; x <= xb; x++) {
				for (int z = za; z <= zb; z++) {
					for (int y = ya; y <= yb; y++) {
						if (world.getBlockId(x, y, z) != Block.LEAVES.id || (world.getBlockMeta(x, y, z) & 8) == 0) continue;
						long t = System.nanoTime();
						Block.LEAVES.onTick(world, x, y, z, world.random);
						if (side >= 0) {
							tickNs[side] += System.nanoTime() - t;
							tickCalls[side]++;
						}
						if (world.getBlockId(x, y, z) != Block.LEAVES.id || (world.getBlockMeta(x, y, z) & 8) == 0) changed = true;
					}
				}
			}
			if (!changed) return;
		}
		results.check("still leaf reach: decay came to rest within 500 passes", false);
	}

	private int countLeaves() {
		int n = 0;
		for (int x = x0 - SIDE; x <= x0 + SIDE; x++) {
			for (int z = z0 - SIDE; z <= z0 + SIDE; z++) {
				for (int y = Y - 1; y <= Y + UP; y++) {
					if (world.getBlockId(x, y, z) == Block.LEAVES.id) n++;
				}
			}
		}
		return n;
	}

	/** The saplings dropped since last asked, taken away (along with anything else dropped). */
	private int saplingsDropped() {
		int n = 0;
		List<?> items = world.collectEntitiesByClass(ItemEntity.class, Box.create(x0 - SIDE - 3, Y - 4, z0 - SIDE - 3, x0 + SIDE + 4, Y + UP + 4, z0 + SIDE + 4));
		for (Object o : items) {
			ItemEntity item = (ItemEntity) o;
			if (item.dead) continue;
			if (item.stack.itemId == Block.SAPLING.id) n += item.stack.count;
			item.markDead();
		}
		return n;
	}

	private void clear() {
		for (int x = x0 - SIDE; x <= x0 + SIDE; x++) {
			for (int z = z0 - SIDE; z <= z0 + SIDE; z++) {
				for (int y = Y - 1; y <= Y + UP; y++) {
					if (world.getBlockId(x, y, z) != 0) world.setBlock(x, y, z, 0);
				}
			}
		}
	}

	/**
	 * A log A with a row of six leaves out from it, and another log B two steps off the row's first leaf: with A broken,
	 * the row is 2 to 7 from B. The second and third are placed leaves, and a placed leaf ends the row. Polished: A marks
	 * all six, and only the sixth (7 from B) falls; beta's 4 marks four, and the fifth and sixth, 6 and 7 from B, fall as
	 * the decay spreads. The placed leaf past the end, marked by the sixth falling, stays where Placed Leaves is on.
	 */
	private void brokenLog() {
		boolean placedStay = PolishConfig.applied("leaves.placedStay");
		int sy = Y + 6, sx = x0 - 3, sz = z0;
		for (int side = 0; side < reaches.length; side++) {
			LeafReach.betaForTest = reaches[side] == LeafReach.BETA;
			clear();
			world.setBlock(sx, sy, sz, Block.LOG.id);
			world.setBlock(sx + 1, sy, sz - 2, Block.LOG.id);
			world.setBlock(sx + 1, sy, sz - 1, Block.LEAVES.id, 0);
			for (int k = 1; k <= 7; k++) world.setBlock(sx + k, sy, sz, Block.LEAVES.id, k == 2 || k == 3 || k == 7 ? PlacedLeaves.PLACED : 0);
			world.setBlock(sx, sy, sz, 0);
			decay(sx - 1, sy - 1, sz - 3, sx + 9, sy + 1, sz + 1, -1);
			StringBuilder fell = new StringBuilder();
			for (int k = 1; k <= 7; k++) {
				if (world.getBlockId(sx + k, sy, sz) != Block.LEAVES.id) fell.append(fell.length() == 0 ? "" : ", ").append(k);
			}
			boolean branchKept = world.getBlockId(sx + 1, sy, sz - 1) == Block.LEAVES.id;
			String want = (reaches[side] == LeafReach.BETA ? "4, 5, 6" : "6") + (placedStay ? "" : ", 7");
			results.check("still leaf reach: a log broken, reach " + reaches[side] + ": of the row out from it, leaves " + (fell.length() == 0 ? "none" : fell)
					+ " fell (expected " + want + "; the 2nd, 3rd and 7th placed" + (placedStay ? ", which stay" : ", Placed Leaves off") + ")", want.contentEquals(fell) && branchKept);
			saplingsDropped();
		}
		clear();
	}

	private void report() {
		results.check("still leaf reach: every tree grew (" + failedToGrow + " didn't) and stayed inside its box (" + outsideBox + " blocks outside)",
				failedToGrow == 0 && outsideBox == 0);
		results.check("still leaf reach: every leaf lies in one of the generator's clusters (" + outsideClusters + " outside)", outsideClusters == 0);
		if (reaches.length > 1) results.check("still leaf reach: each tree grew the same for both reaches (" + differed + " differed)", differed == 0);
		for (int side = 0; side < reaches.length; side++) {
			results.check("still leaf reach: at reach " + reaches[side] + (on ? "" : " (beta's code)") + ", once every leaf had looked, the leaves left were exactly those within "
					+ reaches[side] + " steps of a log through leaves, on " + (trees.length - notExact[side]) + " of " + trees.length + " trees"
					+ (firstNotExact == null ? "" : " (first not: " + firstNotExact + ")"), notExact[side] == 0);
		}
		for (int kind = 0; kind < 2; kind++) {
			boolean prepared = kind == 0;
			String name = prepared ? "the world's big oaks (clusters five tall, as Wild Saplings grows them too)" : "beta's sapling's big oaks (clusters four tall)";
			int n = 0, leaves = 0, clusters = 0, branchless = 0, marked = 0, farBranchless = 0;
			int[] distances = new int[FAR + 1];
			int[] untouched = new int[2], untouchedTrees = new int[2], foot = new int[2], footTrees = new int[2], all = new int[2], whole = new int[2],
					wholeBranchless = new int[2], saplings = new int[2];
			double[] worst = new double[2], worstFoot = new double[2];
			for (Tree t : trees) {
				if (t.prepared != prepared || t.leaves == 0) continue;
				n++;
				leaves += t.leaves;
				clusters += t.clusters;
				branchless += t.branchless;
				marked += t.grownMarked;
				farBranchless += t.farBranchless;
				for (int d = 0; d <= FAR; d++) distances[d] += t.distances[d];
				for (int side = 0; side < reaches.length; side++) {
					untouched[side] += t.fellUntouched[side];
					if (t.fellUntouched[side] > 0) untouchedTrees[side]++;
					foot[side] += t.fellFoot[side];
					if (t.fellFoot[side] > 0) footTrees[side]++;
					worstFoot[side] = Math.max(worstFoot[side], t.fellFoot[side] / (double) t.leaves);
					all[side] += t.fellAll[side];
					whole[side] += t.wholeClusters[side];
					wholeBranchless[side] += t.wholeBranchless[side];
					saplings[side] += t.saplings[side];
					worst[side] = Math.max(worst[side], t.fellAll[side] / (double) t.leaves);
				}
			}
			StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "still leaf reach: %s, %d trees, %d leaves (%.0f a tree), %d clusters (%d with no branch), %d leaves marked by growing; leaves by steps from wood:",
					name, n, leaves, leaves / (double) Math.max(1, n), clusters, branchless, marked));
			for (int d = 1; d < FAR; d++) s.append(String.format(Locale.ROOT, " %d: %.1f%%", d, pct(distances[d], leaves)));
			s.append(String.format(Locale.ROOT, ", 7 or more: %.2f%%, none joined: %.2f%% (of those two, %.2f%% only in clusters with no branch).", pct(distances[FAR], leaves),
					pct(distances[0], leaves), pct(farBranchless, leaves)));
			for (int side = 0; side < reaches.length; side++) {
				s.append(String.format(Locale.ROOT, " Reach %d: untouched, %.2f%% fell (from %d trees, %d saplings dropped); then the trunk's foot cut, %.2f%% more (from %d trees, worst %.1f%%);"
						+ " every leaf looking, %.2f%% in all (worst tree %.1f%%), %d clusters whole (%d of them with no branch).",
						reaches[side], pct(untouched[side], leaves), untouchedTrees[side], saplings[side], pct(foot[side], leaves), footTrees[side], 100 * worstFoot[side],
						pct(all[side], leaves), 100 * worst[side], whole[side], wholeBranchless[side]));
			}
			results.pass(s.toString());
		}
		for (int side = 0; side < reaches.length; side++) {
			results.pass(String.format(Locale.ROOT, "still leaf reach: a leaf's decay tick at reach %d took %.1f us on average (%d ticks)", reaches[side],
					tickNs[side] / 1000.0 / Math.max(1, tickCalls[side]), tickCalls[side]));
		}
		write();
	}

	private static double pct(int part, int whole) {
		return 100.0 * part / Math.max(1, whole);
	}

	private void write() {
		OUT.mkdirs();
		File file = new File(OUT, "trees-" + (on ? "polished" : "beta") + ".csv");
		try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
			StringBuilder head = new StringBuilder("kind,seed,height,clusters,branchless,leaves,logs,marked_by_growing,none_joined");
			for (int d = 1; d < FAR; d++) head.append(",at_").append(d);
			head.append(",at_7_or_more,far_in_branchless_only");
			for (int reach : reaches) {
				head.append(",fell_untouched_").append(reach).append(",fell_foot_cut_").append(reach).append(",fell_all_").append(reach).append(",whole_clusters_").append(reach)
						.append(",whole_branchless_").append(reach).append(",saplings_").append(reach);
			}
			out.println(head);
			for (Tree t : trees) {
				StringBuilder row = new StringBuilder();
				row.append(t.prepared ? "world" : "sapling").append(',').append(t.seed).append(',').append(t.height).append(',').append(t.clusters).append(',')
						.append(t.branchless).append(',').append(t.leaves).append(',').append(t.logs).append(',').append(t.grownMarked);
				for (int d = 0; d <= FAR; d++) row.append(',').append(t.distances[d]);
				row.append(',').append(t.farBranchless);
				for (int side = 0; side < reaches.length; side++) {
					row.append(',').append(t.fellUntouched[side]).append(',').append(t.fellFoot[side]).append(',').append(t.fellAll[side]).append(',').append(t.wholeClusters[side])
							.append(',').append(t.wholeBranchless[side]).append(',').append(t.saplings[side]);
				}
				out.println(row);
			}
		} catch (IOException e) {
			results.fail("still leaf reach: writing " + file, e);
		}
	}
}
